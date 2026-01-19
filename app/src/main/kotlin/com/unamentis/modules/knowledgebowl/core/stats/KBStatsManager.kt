package com.unamentis.modules.knowledgebowl.core.stats

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent statistics for Knowledge Bowl practice sessions.
 *
 * Tracks domain mastery, session history, and aggregate statistics
 * using SharedPreferences for persistence.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@Singleton
class KBStatsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KBStatsManager"
            private const val PREFS_NAME = "kb_stats"
            private const val MAX_RECENT_SESSIONS = 20
            private const val COMPETITION_TARGET_QUESTIONS = 200
            private const val DOMAIN_COVERAGE_MIN_QUESTIONS = 5

            private object Keys {
                const val TOTAL_QUESTIONS = "total_questions"
                const val TOTAL_CORRECT = "total_correct"
                const val TOTAL_RESPONSE_TIME = "total_response_time"
                const val DOMAIN_STATS = "domain_stats"
                const val RECENT_SESSIONS = "recent_sessions"
            }
        }

        private val prefs: SharedPreferences by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private val json = Json { ignoreUnknownKeys = true }

        // State
        private val _totalQuestionsAnswered = MutableStateFlow(0)
        val totalQuestionsAnswered: StateFlow<Int> = _totalQuestionsAnswered.asStateFlow()

        private val _totalCorrectAnswers = MutableStateFlow(0)
        val totalCorrectAnswers: StateFlow<Int> = _totalCorrectAnswers.asStateFlow()

        private val _averageResponseTime = MutableStateFlow(0.0)
        val averageResponseTime: StateFlow<Double> = _averageResponseTime.asStateFlow()

        private val _domainStats = MutableStateFlow<Map<String, KBDomainStats>>(emptyMap())
        val domainStats: StateFlow<Map<String, KBDomainStats>> = _domainStats.asStateFlow()

        private val _recentSessions = MutableStateFlow<List<KBSessionRecord>>(emptyList())
        val recentSessions: StateFlow<List<KBSessionRecord>> = _recentSessions.asStateFlow()

        init {
            loadStats()
        }

        // Computed Properties

        /**
         * Overall accuracy across all questions (0.0 to 1.0).
         */
        val overallAccuracy: Float
            get() {
                val total = _totalQuestionsAnswered.value
                return if (total > 0) _totalCorrectAnswers.value.toFloat() / total else 0f
            }

        /**
         * Competition readiness score (0.0 to 1.0).
         *
         * Based on:
         * - 50% accuracy score
         * - 30% domain coverage (domains with 5+ questions)
         * - 20% volume score (toward 200 question target)
         */
        val competitionReadiness: Float
            get() {
                val total = _totalQuestionsAnswered.value
                if (total == 0) return 0f

                // Accuracy score
                val accuracyScore = overallAccuracy

                // Domain coverage score (number of domains with adequate practice)
                val coveredDomains =
                    _domainStats.value.count {
                        it.value.totalAnswered >= DOMAIN_COVERAGE_MIN_QUESTIONS
                    }
                val coverageScore = coveredDomains.toFloat() / KBDomain.entries.size

                // Volume score (practice volume toward target)
                val volumeScore = minOf(1f, total.toFloat() / COMPETITION_TARGET_QUESTIONS)

                // Weighted average
                return (accuracyScore * 0.5f + coverageScore * 0.3f + volumeScore * 0.2f)
            }

        // Public Methods

        /**
         * Record results from a completed practice session.
         *
         * @param summary Session summary with results
         * @param mode Study mode used
         */
        fun recordSession(
            summary: KBSessionSummary,
            mode: KBStudyMode,
        ) {
            // Update totals
            val prevTotal = _totalQuestionsAnswered.value
            _totalQuestionsAnswered.value += summary.totalQuestions
            _totalCorrectAnswers.value += summary.totalCorrect

            // Update running average response time
            val prevAvgTime = _averageResponseTime.value
            val prevTotalTime = prevAvgTime * prevTotal
            val newTotalTime = prevTotalTime + (summary.averageResponseTimeSeconds * summary.totalQuestions)
            val newTotal = _totalQuestionsAnswered.value
            if (newTotal > 0) {
                _averageResponseTime.value = newTotalTime / newTotal
            }

            // Update domain stats from session attempts would require attempt details
            // For now, we record aggregate stats from the summary

            // Create session record
            val record =
                KBSessionRecord(
                    id = summary.sessionId,
                    timestamp = summary.completedAtMillis,
                    studyMode = mode.name.lowercase(),
                    questionsAnswered = summary.totalQuestions,
                    correctAnswers = summary.totalCorrect,
                    averageTimeSeconds = summary.averageResponseTimeSeconds,
                    totalPoints = summary.totalPoints,
                )

            // Add to recent sessions (FIFO, max 20)
            val sessions = _recentSessions.value.toMutableList()
            sessions.add(0, record)
            if (sessions.size > MAX_RECENT_SESSIONS) {
                _recentSessions.value = sessions.take(MAX_RECENT_SESSIONS)
            } else {
                _recentSessions.value = sessions
            }

            // Persist
            saveStats()

            Log.i(TAG, "Recorded session: ${summary.totalCorrect}/${summary.totalQuestions} correct")
        }

        /**
         * Record a domain attempt.
         *
         * @param domain The domain of the question
         * @param wasCorrect Whether the answer was correct
         */
        fun recordDomainAttempt(
            domain: KBDomain,
            wasCorrect: Boolean,
        ) {
            val domainId = normalizeDomainId(domain.name)
            val currentStats = _domainStats.value.toMutableMap()
            val stats = currentStats[domainId] ?: KBDomainStats()
            currentStats[domainId] = stats.withAttempt(wasCorrect)
            _domainStats.value = currentStats

            // Persist domain stats
            saveDomainStats()
        }

        /**
         * Get mastery percentage for a specific domain (0.0 to 1.0).
         */
        fun mastery(domain: KBDomain): Float {
            val domainId = normalizeDomainId(domain.name)
            return _domainStats.value[domainId]?.accuracy ?: 0f
        }

        /**
         * Get domain stats for a specific domain.
         */
        fun getDomainStats(domain: KBDomain): KBDomainStats {
            val domainId = normalizeDomainId(domain.name)
            return _domainStats.value[domainId] ?: KBDomainStats()
        }

        /**
         * Get all domains sorted by mastery (lowest first) for targeted practice.
         */
        fun getWeakDomains(limit: Int = 5): List<Pair<KBDomain, Float>> =
            KBDomain.entries
                .map { it to mastery(it) }
                .filter { _domainStats.value[normalizeDomainId(it.first.name)]?.totalAnswered ?: 0 > 0 }
                .sortedBy { it.second }
                .take(limit)

        /**
         * Get all domains sorted by mastery (highest first) for review.
         */
        fun getStrongDomains(limit: Int = 5): List<Pair<KBDomain, Float>> =
            KBDomain.entries
                .map { it to mastery(it) }
                .filter { _domainStats.value[normalizeDomainId(it.first.name)]?.totalAnswered ?: 0 > 0 }
                .sortedByDescending { it.second }
                .take(limit)

        /**
         * Get domains that need more practice (< threshold questions).
         */
        fun getUnderPracticedDomains(threshold: Int = DOMAIN_COVERAGE_MIN_QUESTIONS): List<KBDomain> =
            KBDomain.entries.filter {
                (_domainStats.value[normalizeDomainId(it.name)]?.totalAnswered ?: 0) < threshold
            }

        /**
         * Reset all statistics.
         */
        fun resetStats() {
            _totalQuestionsAnswered.value = 0
            _totalCorrectAnswers.value = 0
            _averageResponseTime.value = 0.0
            _domainStats.value = emptyMap()
            _recentSessions.value = emptyList()

            prefs.edit().clear().apply()

            Log.i(TAG, "Stats reset")
        }

        // Private Helpers

        private fun normalizeDomainId(domainId: String): String =
            domainId.lowercase()
                .replace(" ", "-")
                .replace("&", "")
                .replace("--", "-")

        private fun loadStats() {
            _totalQuestionsAnswered.value = prefs.getInt(Keys.TOTAL_QUESTIONS, 0)
            _totalCorrectAnswers.value = prefs.getInt(Keys.TOTAL_CORRECT, 0)
            _averageResponseTime.value = prefs.getFloat(Keys.TOTAL_RESPONSE_TIME, 0f).toDouble()

            // Load domain stats
            prefs.getString(Keys.DOMAIN_STATS, null)?.let { jsonStr ->
                try {
                    _domainStats.value = json.decodeFromString(jsonStr)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load domain stats", e)
                }
            }

            // Load recent sessions
            prefs.getString(Keys.RECENT_SESSIONS, null)?.let { jsonStr ->
                try {
                    _recentSessions.value = json.decodeFromString(jsonStr)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load recent sessions", e)
                }
            }

            Log.i(
                TAG,
                "Loaded stats: ${_totalQuestionsAnswered.value} questions, " +
                    "${_totalCorrectAnswers.value} correct",
            )
        }

        private fun saveStats() {
            prefs.edit()
                .putInt(Keys.TOTAL_QUESTIONS, _totalQuestionsAnswered.value)
                .putInt(Keys.TOTAL_CORRECT, _totalCorrectAnswers.value)
                .putFloat(Keys.TOTAL_RESPONSE_TIME, _averageResponseTime.value.toFloat())
                .putString(Keys.DOMAIN_STATS, json.encodeToString(_domainStats.value))
                .putString(Keys.RECENT_SESSIONS, json.encodeToString(_recentSessions.value))
                .apply()
        }

        private fun saveDomainStats() {
            prefs.edit()
                .putString(Keys.DOMAIN_STATS, json.encodeToString(_domainStats.value))
                .apply()
        }
    }
