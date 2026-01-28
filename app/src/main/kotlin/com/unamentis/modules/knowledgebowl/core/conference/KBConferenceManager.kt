package com.unamentis.modules.knowledgebowl.core.conference

import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages conference training sessions for Knowledge Bowl.
 *
 * Conference training helps teams practice quick decision-making
 * within regional time limits. Features:
 * - Progressive difficulty (time limits decrease as you improve)
 * - Hand signal tracking
 * - Session statistics and recommendations
 */
@Singleton
class KBConferenceManager
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBConferenceManager"

            /** Correct answers needed to advance difficulty */
            private const val PROMOTION_THRESHOLD = 3

            /** Timeouts that cause difficulty demotion (reserved for future use) */
            @Suppress("UnusedPrivateProperty")
            private const val DEMOTION_THRESHOLD = 2

            /** Signal practice scenarios */
            private val SIGNAL_SCENARIOS =
                listOf(
                    KBHandSignal.CONFIDENT to "You know the answer is 'Paris' for certain",
                    KBHandSignal.UNSURE to "You think it might be 'Rome' but aren't sure",
                    KBHandSignal.PASS to "You have no idea and want to skip",
                    KBHandSignal.WAIT to "You're still processing the question",
                    KBHandSignal.ANSWER to "You want to be the one to buzz in",
                    KBHandSignal.AGREE to "Your teammate suggests 'London' and you agree",
                    KBHandSignal.DISAGREE to "Your teammate suggests 'Berlin' but you think it's wrong",
                )

            /**
             * Get a random hand signal prompt for training.
             */
            fun randomSignalPrompt(): Pair<KBHandSignal, String> {
                return SIGNAL_SCENARIOS.random()
            }

            /**
             * Validate a hand signal response.
             */
            fun validateSignal(
                expected: KBHandSignal,
                given: KBHandSignal,
            ): Boolean {
                return expected == given
            }
        }

        private val mutex = Mutex()

        // Session state
        private var config: KBConferenceConfig? = null
        private val attempts = mutableListOf<KBConferenceAttempt>()
        private var currentDifficultyLevel = 0
        private var consecutiveCorrect = 0
        private var sessionStartTime: Long = 0L
        private var isActive = false

        // Observable state
        private val _currentLevel = MutableStateFlow(0)
        val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

        private val _isSessionActive = MutableStateFlow(false)
        val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

        /**
         * Start a new conference training session.
         */
        suspend fun startSession(config: KBConferenceConfig) =
            mutex.withLock {
                this.config = config
                attempts.clear()
                currentDifficultyLevel = 0
                consecutiveCorrect = 0
                sessionStartTime = System.currentTimeMillis()
                isActive = true
                _currentLevel.value = 0
                _isSessionActive.value = true
                Log.i(TAG, "Conference training session started for ${config.region.displayName}")
            }

        /**
         * End the current session and return statistics.
         */
        suspend fun endSession(): KBConferenceStats =
            mutex.withLock {
                isActive = false
                _isSessionActive.value = false
                val stats = calculateStats()
                val accuracy = (stats.accuracy * 100).toInt()
                Log.i(TAG, "Conference session ended: ${stats.totalAttempts} attempts, $accuracy% accuracy")
                stats
            }

        /**
         * Check if session is active.
         */
        fun isSessionActive(): Boolean = isActive

        /**
         * Get current time limit for this round.
         */
        fun getCurrentTimeLimit(): Double {
            return config?.timeLimit(currentDifficultyLevel) ?: 15.0
        }

        /**
         * Get current difficulty level (0-indexed).
         */
        fun getCurrentDifficultyLevel(): Int = currentDifficultyLevel

        /**
         * Record a conference attempt.
         */
        suspend fun recordAttempt(
            questionId: String,
            domain: KBDomain,
            conferenceTime: Double,
            wasCorrect: Boolean,
            signalUsed: KBHandSignal? = null,
        ) = mutex.withLock {
            val attempt =
                KBConferenceAttempt(
                    questionId = questionId,
                    domain = domain,
                    conferenceTime = conferenceTime,
                    timeLimitUsed = getCurrentTimeLimit(),
                    wasCorrect = wasCorrect,
                    signalUsed = signalUsed,
                )

            attempts.add(attempt)
            updateDifficulty(wasCorrect = wasCorrect, timedOut = attempt.usedFullTime)

            val timeFormatted = String.format("%.1f", conferenceTime)
            Log.d(TAG, "Conference attempt: ${timeFormatted}s, correct: $wasCorrect, level: $currentDifficultyLevel")
        }

        /**
         * Update difficulty based on performance.
         */
        private fun updateDifficulty(
            wasCorrect: Boolean,
            timedOut: Boolean,
        ) {
            val cfg = config ?: return
            if (!cfg.progressiveDifficulty) return

            if (wasCorrect && !timedOut) {
                consecutiveCorrect++

                // Promote to harder difficulty
                if (consecutiveCorrect >= PROMOTION_THRESHOLD) {
                    val maxLevel = KBConferenceConfig.PROGRESSIVE_LEVELS.lastIndex
                    if (currentDifficultyLevel < maxLevel) {
                        currentDifficultyLevel++
                        _currentLevel.value = currentDifficultyLevel
                        consecutiveCorrect = 0
                        Log.i(TAG, "Advanced to difficulty level $currentDifficultyLevel")
                    }
                }
            } else if (timedOut) {
                // Demote on timeouts
                if (currentDifficultyLevel > 0) {
                    currentDifficultyLevel--
                    _currentLevel.value = currentDifficultyLevel
                    consecutiveCorrect = 0
                    Log.i(TAG, "Dropped to difficulty level $currentDifficultyLevel")
                }
            } else {
                // Wrong answer resets consecutive counter
                consecutiveCorrect = (consecutiveCorrect - 1).coerceAtLeast(0)
            }
        }

        /**
         * Calculate current session statistics.
         */
        private fun calculateStats(): KBConferenceStats {
            if (attempts.isEmpty()) {
                return KBConferenceStats.EMPTY.copy(currentDifficultyLevel = currentDifficultyLevel)
            }

            val times = attempts.map { it.conferenceTime }
            val avgTime = times.average()

            val signalDist =
                attempts
                    .mapNotNull { it.signalUsed }
                    .groupingBy { it }
                    .eachCount()

            return KBConferenceStats(
                totalAttempts = attempts.size,
                correctCount = attempts.count { it.wasCorrect },
                averageConferenceTime = avgTime,
                fastestTime = times.minOrNull() ?: 0.0,
                slowestTime = times.maxOrNull() ?: 0.0,
                timeoutsCount = attempts.count { it.usedFullTime },
                currentDifficultyLevel = currentDifficultyLevel,
                signalDistribution = signalDist,
            )
        }

        /**
         * Get attempts for a specific domain.
         */
        fun getAttempts(domain: KBDomain): List<KBConferenceAttempt> {
            return attempts.filter { it.domain == domain }
        }

        /**
         * Get all attempts.
         */
        fun getAllAttempts(): List<KBConferenceAttempt> = attempts.toList()

        /**
         * Reset the manager for a new session.
         */
        suspend fun reset() =
            mutex.withLock {
                config = null
                attempts.clear()
                currentDifficultyLevel = 0
                consecutiveCorrect = 0
                sessionStartTime = 0L
                isActive = false
                _currentLevel.value = 0
                _isSessionActive.value = false
            }
    }
