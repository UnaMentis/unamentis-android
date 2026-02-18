package com.unamentis.modules.knowledgebowl.core.stats

import android.content.Context
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Persistent storage for Knowledge Bowl sessions.
 *
 * Stores complete session data as JSON files for rich analytics queries.
 * Each session is stored as a separate file: `{sessionsDir}/{sessionId}.json`.
 *
 * Maps to iOS KBSessionStore actor.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@Singleton
class KBSessionStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KBSessionStore"
            private const val SESSIONS_DIR = "KnowledgeBowl/Sessions"
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }

        private val mutex = Mutex()

        private val sessionsDir: File
            get() {
                val dir = File(context.filesDir, SESSIONS_DIR)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                return dir
            }

        // MARK: - Save Operations

        /**
         * Save a session to persistent storage.
         */
        suspend fun save(session: KBSession) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        val file = sessionFile(session.id)
                        file.writeText(json.encodeToString(session))
                        Log.d(TAG, "Saved session ${session.id}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save session ${session.id}: ${e.message}", e)
                    }
                }
            }
        }

        /**
         * Save multiple sessions in a batch.
         */
        suspend fun saveBatch(sessions: List<KBSession>) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    for (session in sessions) {
                        try {
                            val file = sessionFile(session.id)
                            file.writeText(json.encodeToString(session))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save session ${session.id}: ${e.message}", e)
                        }
                    }
                    Log.d(TAG, "Saved batch of ${sessions.size} sessions")
                }
            }
        }

        // MARK: - Load Operations

        /**
         * Load a single session by ID.
         */
        suspend fun load(id: String): KBSession? =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        val file = sessionFile(id)
                        if (!file.exists()) return@withContext null
                        json.decodeFromString<KBSession>(file.readText())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load session $id: ${e.message}", e)
                        null
                    }
                }
            }

        /**
         * Load all sessions sorted by start time (newest first).
         */
        suspend fun loadAll(): List<KBSession> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    loadAllInternal()
                }
            }

        /**
         * Load the most recent sessions.
         *
         * @param limit Maximum number of sessions to return
         */
        suspend fun loadRecent(limit: Int = 10): List<KBSession> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    loadAllInternal().take(limit)
                }
            }

        /**
         * Load sessions filtered by region.
         */
        suspend fun loadSessions(region: KBRegion): List<KBSession> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    loadAllInternal().filter { it.config.region == region }
                }
            }

        /**
         * Load sessions filtered by round type.
         */
        suspend fun loadSessions(roundType: KBRoundType): List<KBSession> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    loadAllInternal().filter { it.config.roundType == roundType }
                }
            }

        // MARK: - Delete Operations

        /**
         * Delete a session by ID.
         */
        suspend fun delete(id: String) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val file = sessionFile(id)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Deleted session $id")
                    }
                }
            }
        }

        /**
         * Delete sessions older than the specified number of days.
         *
         * @return Number of sessions deleted
         */
        suspend fun deleteOlderThan(days: Int): Int =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
                    val sessions = loadAllInternal()
                    var deleted = 0

                    for (session in sessions) {
                        if (session.startTimeMillis < cutoff) {
                            val file = sessionFile(session.id)
                            if (file.delete()) deleted++
                        }
                    }

                    Log.i(TAG, "Deleted $deleted sessions older than $days days")
                    deleted
                }
            }

        /**
         * Delete all stored sessions.
         */
        suspend fun deleteAll() {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val files = sessionsDir.listFiles { _, name -> name.endsWith(".json") }
                    val count = files?.count { it.delete() } ?: 0
                    Log.i(TAG, "Deleted all $count sessions")
                }
            }
        }

        // MARK: - Statistics

        /**
         * Calculate aggregate statistics from all completed sessions.
         */
        suspend fun calculateStatistics(): KBStatistics =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val sessions = loadAllInternal().filter { it.isComplete }
                    if (sessions.isEmpty()) return@withContext KBStatistics()

                    val totalQuestions = sessions.sumOf { it.attempts.size }
                    val totalCorrect = sessions.sumOf { it.correctCount }
                    val totalIncorrect = sessions.sumOf { it.incorrectCount }

                    val writtenSessions = sessions.filter { it.config.roundType == KBRoundType.WRITTEN }
                    val oralSessions = sessions.filter { it.config.roundType == KBRoundType.ORAL }

                    val writtenTotal = writtenSessions.sumOf { it.attempts.size }
                    val writtenCorrect = writtenSessions.sumOf { it.correctCount }
                    val oralTotal = oralSessions.sumOf { it.attempts.size }
                    val oralCorrect = oralSessions.sumOf { it.correctCount }

                    KBStatistics(
                        totalSessions = sessions.size,
                        totalQuestions = totalQuestions,
                        totalCorrect = totalCorrect,
                        totalIncorrect = totalIncorrect,
                        overallAccuracy = if (totalQuestions > 0) totalCorrect.toDouble() / totalQuestions else 0.0,
                        writtenAccuracy = if (writtenTotal > 0) writtenCorrect.toDouble() / writtenTotal else 0.0,
                        oralAccuracy = if (oralTotal > 0) oralCorrect.toDouble() / oralTotal else 0.0,
                        mostRecentSessionMillis = sessions.firstOrNull()?.startTimeMillis,
                        currentStreak = calculateStreak(sessions),
                    )
                }
            }

        // MARK: - Internal Helpers

        private fun sessionFile(id: String): File = File(sessionsDir, "$id.json")

        /**
         * Load all sessions from disk, sorted by start time (newest first).
         * Must be called within mutex lock.
         */
        private fun loadAllInternal(): List<KBSession> {
            val files = sessionsDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
            val sessions = mutableListOf<KBSession>()

            for (file in files) {
                try {
                    val session = json.decodeFromString<KBSession>(file.readText())
                    sessions.add(session)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt session file ${file.name}: ${e.message}")
                }
            }

            return sessions.sortedByDescending { it.startTimeMillis }
        }

        /**
         * Calculate consecutive days with at least one session.
         */
        private fun calculateStreak(sessions: List<KBSession>): Int {
            if (sessions.isEmpty()) return 0

            val calendar = Calendar.getInstance()
            val sessionDays =
                sessions
                    .map { session ->
                        calendar.timeInMillis = session.startTimeMillis
                        Triple(
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH),
                        )
                    }.distinct()
                    .sortedWith(
                        compareByDescending<Triple<Int, Int, Int>> { it.first }
                            .thenByDescending { it.second }
                            .thenByDescending { it.third },
                    )

            if (sessionDays.isEmpty()) return 0

            var streak = 1
            val dayCalendar = Calendar.getInstance()

            for (i in 0 until sessionDays.size - 1) {
                val current = sessionDays[i]
                val previous = sessionDays[i + 1]

                dayCalendar.set(current.first, current.second, current.third)
                val currentDayOfYear = dayCalendar.get(Calendar.DAY_OF_YEAR)
                val currentYear = dayCalendar.get(Calendar.YEAR)

                dayCalendar.set(previous.first, previous.second, previous.third)
                val previousDayOfYear = dayCalendar.get(Calendar.DAY_OF_YEAR)
                val previousYear = dayCalendar.get(Calendar.YEAR)

                val isConsecutive =
                    (currentYear == previousYear && currentDayOfYear - previousDayOfYear == 1) ||
                        (
                            currentYear == previousYear + 1 &&
                                currentDayOfYear == 1 &&
                                previousDayOfYear == dayCalendar.getActualMaximum(Calendar.DAY_OF_YEAR)
                        )

                if (isConsecutive) {
                    streak++
                } else {
                    break
                }
            }

            return streak
        }
    }
