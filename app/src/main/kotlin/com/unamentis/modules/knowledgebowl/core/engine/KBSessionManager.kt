package com.unamentis.modules.knowledgebowl.core.engine

import android.util.Log
import com.unamentis.modules.knowledgebowl.core.stats.KBSessionStore
import com.unamentis.modules.knowledgebowl.core.stats.KBStatistics
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionAttempt
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSession
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of Knowledge Bowl sessions with persistence.
 *
 * Coordinates between the in-memory practice engine and the persistent
 * session store. Handles:
 * - Starting new sessions with question selection
 * - Recording attempts and completing sessions
 * - Loading session history for analytics
 * - Cleaning up old session data
 *
 * Maps to iOS KBSessionManager actor.
 */
@Suppress("TooManyFunctions")
@Singleton
class KBSessionManager
    @Inject
    constructor(
        private val sessionStore: KBSessionStore,
    ) {
        companion object {
            private const val TAG = "KBSessionManager"
        }

        private val mutex = Mutex()
        private var currentSession: KBSession? = null
        private var currentQuestions: List<KBQuestion> = emptyList()

        // MARK: - Session Lifecycle

        /**
         * Start a new session with the given configuration.
         *
         * @param questions Questions to use for the session
         * @param config Session configuration
         * @return The newly created session
         */
        suspend fun startSession(
            questions: List<KBQuestion>,
            config: KBSessionConfig,
        ): KBSession =
            mutex.withLock {
                val session = KBSession.create(config)
                currentSession = session
                currentQuestions = questions
                Log.i(TAG, "Started session ${session.id} with ${questions.size} questions")
                session
            }

        /**
         * Get the current active session, if any.
         */
        suspend fun getCurrentSession(): KBSession? =
            mutex.withLock {
                currentSession
            }

        /**
         * Get the current question based on the session's question index.
         */
        suspend fun getCurrentQuestion(): KBQuestion? =
            mutex.withLock {
                val session = currentSession ?: return@withLock null
                currentQuestions.getOrNull(session.currentQuestionIndex)
            }

        /**
         * Advance to the next question.
         *
         * @return The next question, or null if the session is complete
         */
        suspend fun advanceToNextQuestion(): KBQuestion? =
            mutex.withLock {
                val session = currentSession ?: return@withLock null
                session.currentQuestionIndex++
                currentQuestions.getOrNull(session.currentQuestionIndex)
            }

        /**
         * Check if the current question is the last one.
         */
        suspend fun isLastQuestion(): Boolean =
            mutex.withLock {
                val session = currentSession ?: return@withLock true
                session.currentQuestionIndex >= currentQuestions.size - 1
            }

        /**
         * Get the current progress (0.0 to 1.0).
         */
        suspend fun getProgress(): Double =
            mutex.withLock {
                val session = currentSession ?: return@withLock 0.0
                if (currentQuestions.isEmpty()) return@withLock 0.0
                session.attempts.size.toDouble() / currentQuestions.size
            }

        /**
         * Record an attempt for the current question.
         */
        suspend fun recordAttempt(attempt: KBQuestionAttempt) {
            mutex.withLock {
                val session = currentSession ?: return@withLock
                session.attempts.add(attempt)
                Log.d(TAG, "Recorded attempt for question ${attempt.questionId}")
            }
        }

        /**
         * Update the current session via a transform function.
         */
        suspend fun updateSession(updater: (KBSession) -> Unit) {
            mutex.withLock {
                val session = currentSession ?: return@withLock
                updater(session)
            }
        }

        /**
         * Complete the current session and persist it.
         */
        suspend fun completeSession() {
            mutex.withLock {
                val session = currentSession ?: throw KBSessionError.NoActiveSession

                if (session.isComplete) throw KBSessionError.SessionAlreadyComplete

                session.isComplete = true
                session.endTimeMillis = System.currentTimeMillis()

                sessionStore.save(session)

                Log.i(
                    TAG,
                    "Completed session ${session.id}: " +
                        "${session.correctCount}/${session.attempts.size} correct",
                )
            }
        }

        /**
         * Cancel the current session without saving.
         */
        suspend fun cancelSession() {
            mutex.withLock {
                val sessionId = currentSession?.id
                currentSession = null
                currentQuestions = emptyList()
                Log.i(TAG, "Cancelled session $sessionId")
            }
        }

        // MARK: - Session Queries

        /**
         * Load recent sessions from the store.
         *
         * @param limit Maximum number of sessions to return
         */
        suspend fun loadRecentSessions(limit: Int = 10): List<KBSession> = sessionStore.loadRecent(limit)

        /**
         * Calculate aggregate statistics.
         */
        suspend fun calculateStatistics(): KBStatistics = sessionStore.calculateStatistics()

        /**
         * Load sessions filtered by region.
         */
        suspend fun loadSessions(region: KBRegion): List<KBSession> = sessionStore.loadSessions(region)

        /**
         * Load sessions filtered by round type.
         */
        suspend fun loadSessions(roundType: KBRoundType): List<KBSession> = sessionStore.loadSessions(roundType)

        // MARK: - Data Management

        /**
         * Delete sessions older than the given number of days.
         *
         * @return Number of sessions deleted
         */
        suspend fun deleteOldSessions(days: Int): Int = sessionStore.deleteOlderThan(days)

        /**
         * Delete all stored sessions.
         */
        suspend fun deleteAllSessions() {
            sessionStore.deleteAll()
        }
    }

// MARK: - Errors

/**
 * Errors from session management operations.
 */
sealed class KBSessionError : Exception() {
    /** No session is currently active. */
    data object NoActiveSession : KBSessionError() {
        override val message: String get() = "No active session"
    }

    /** The session has already been completed. */
    data object SessionAlreadyComplete : KBSessionError() {
        override val message: String get() = "Session is already complete"
    }

    /** Invalid question index. */
    data class InvalidQuestionIndex(val index: Int) : KBSessionError() {
        override val message: String get() = "Invalid question index: $index"
    }
}
