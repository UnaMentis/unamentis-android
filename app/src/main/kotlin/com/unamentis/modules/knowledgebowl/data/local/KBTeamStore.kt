package com.unamentis.modules.knowledgebowl.data.local

import android.content.Context
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBMemberStats
import com.unamentis.modules.knowledgebowl.data.model.KBTeamProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON-based local persistence for Knowledge Bowl team data.
 *
 * Stores team profile and per-member statistics as JSON files in the
 * app's internal storage under `KnowledgeBowl/Team/`.
 *
 * All file operations run on [Dispatchers.IO] and are protected by a [Mutex]
 * for thread safety.
 *
 * @property context Application context for file access
 */
@Singleton
class KBTeamStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()
        private val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }

        private val teamDir: File
            get() = File(context.filesDir, TEAM_DIR_PATH)

        private val profileFile: File
            get() = File(teamDir, PROFILE_FILE_NAME)

        private val statsDir: File
            get() = File(teamDir, STATS_DIR_NAME)

        /**
         * Check whether a team profile exists on disk.
         *
         * @return true if a profile file exists
         */
        suspend fun hasTeamProfile(): Boolean =
            withContext(Dispatchers.IO) {
                profileFile.exists()
            }

        /**
         * Save a team profile to disk.
         *
         * Creates the team directory if it does not exist.
         *
         * @param profile The team profile to persist
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun saveProfile(profile: KBTeamProfile) {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        teamDir.mkdirs()
                        val data = json.encodeToString(KBTeamProfile.serializer(), profile)
                        profileFile.writeText(data)
                        Log.i(TAG, "Saved team profile '${profile.name}'")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save team profile: ${e.message}")
                    }
                }
            }
        }

        /**
         * Load the team profile from disk.
         *
         * @return The stored team profile, or null if none exists or decoding fails
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun loadProfile(): KBTeamProfile? =
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        if (!profileFile.exists()) return@withContext null
                        val data = profileFile.readText()
                        json.decodeFromString(KBTeamProfile.serializer(), data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load team profile: ${e.message}")
                        null
                    }
                }
            }

        /**
         * Delete the team profile from disk.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun deleteProfile() {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        if (profileFile.exists()) {
                            profileFile.delete()
                            Log.i(TAG, "Deleted team profile")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete team profile: ${e.message}")
                    }
                }
            }
        }

        /**
         * Save member statistics to disk.
         *
         * Creates the stats directory if it does not exist.
         *
         * @param stats The member statistics to persist
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun saveStats(stats: KBMemberStats) {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        statsDir.mkdirs()
                        val file = File(statsDir, "${stats.memberId}.json")
                        val data = json.encodeToString(KBMemberStats.serializer(), stats)
                        file.writeText(data)
                        Log.d(TAG, "Saved stats for member ${stats.memberId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save member stats: ${e.message}")
                    }
                }
            }
        }

        /**
         * Load statistics for a specific team member.
         *
         * @param memberId The member's unique identifier
         * @return The stored statistics, or null if none exist or decoding fails
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun loadStats(memberId: String): KBMemberStats? =
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(statsDir, "$memberId.json")
                        if (!file.exists()) return@withContext null
                        val data = file.readText()
                        json.decodeFromString(KBMemberStats.serializer(), data)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load stats for member $memberId: ${e.message}")
                        null
                    }
                }
            }

        /**
         * Load statistics for all team members.
         *
         * @return List of all stored member statistics
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun loadAllStats(): List<KBMemberStats> =
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        if (!statsDir.exists()) return@withContext emptyList()
                        statsDir
                            .listFiles { file -> file.extension == "json" }
                            ?.mapNotNull { file ->
                                try {
                                    val data = file.readText()
                                    json.decodeFromString(KBMemberStats.serializer(), data)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to load stats from ${file.name}: ${e.message}")
                                    null
                                }
                            } ?: emptyList()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load all member stats: ${e.message}")
                        emptyList()
                    }
                }
            }

        /**
         * Delete statistics for a specific team member.
         *
         * @param memberId The member's unique identifier
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun deleteStats(memberId: String) {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(statsDir, "$memberId.json")
                        if (file.exists()) {
                            file.delete()
                            Log.d(TAG, "Deleted stats for member $memberId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete stats for member $memberId: ${e.message}")
                    }
                }
            }
        }

        /**
         * Delete all team data including profile and all member statistics.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun deleteAllData() {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        if (teamDir.exists()) {
                            teamDir.deleteRecursively()
                            Log.i(TAG, "Deleted all team data")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete all team data: ${e.message}")
                    }
                }
            }
        }

        companion object {
            private const val TAG = "KBTeamStore"
            private const val TEAM_DIR_PATH = "KnowledgeBowl/Team"
            private const val PROFILE_FILE_NAME = "profile.json"
            private const val STATS_DIR_NAME = "stats"
        }
    }
