package com.unamentis.modules.knowledgebowl.core.conference

import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import kotlinx.serialization.Serializable

/**
 * Configuration for conference training sessions.
 *
 * Conference training helps teams practice quick decision-making
 * within regional time limits using hand signals or verbal communication.
 */
@Serializable
data class KBConferenceConfig(
    val region: KBRegion,
    val baseTimeLimit: Double,
    val progressiveDifficulty: Boolean,
    val handSignalsOnly: Boolean,
    val questionCount: Int,
) {
    companion object {
        /** Time limits for progressive difficulty levels (in seconds) */
        val PROGRESSIVE_LEVELS = listOf(15.0, 12.0, 10.0, 8.0)

        /** Create default config for a region */
        fun forRegion(region: KBRegion): KBConferenceConfig {
            val regionConfig = region.config
            return KBConferenceConfig(
                region = region,
                baseTimeLimit = regionConfig.conferenceTime.toDouble(),
                progressiveDifficulty = true,
                handSignalsOnly = !regionConfig.verbalConferringAllowed,
                questionCount = 15,
            )
        }
    }

    /**
     * Get the time limit for a specific difficulty level.
     *
     * @param level The difficulty level (0-indexed)
     * @return Time limit in seconds
     */
    fun timeLimit(forLevel: Int): Double {
        if (!progressiveDifficulty) return baseTimeLimit
        val clampedLevel = forLevel.coerceIn(0, PROGRESSIVE_LEVELS.lastIndex)
        return PROGRESSIVE_LEVELS[clampedLevel]
    }
}

/**
 * Standard Knowledge Bowl hand signals for non-verbal conferring.
 */
enum class KBHandSignal(
    val displayNameResId: Int,
    val gestureDescriptionResId: Int,
    val emoji: String,
) {
    /** Thumbs up - "I know this" */
    CONFIDENT(
        R.string.kb_signal_confident,
        R.string.kb_signal_confident_gesture,
        "👍",
    ),

    /** Flat hand wobble - "Not sure" */
    UNSURE(
        R.string.kb_signal_unsure,
        R.string.kb_signal_unsure_gesture,
        "🤔",
    ),

    /** Wave off - "Skip this" */
    PASS(
        R.string.kb_signal_pass,
        R.string.kb_signal_pass_gesture,
        "👋",
    ),

    /** Raised finger - "Wait, thinking" */
    WAIT(
        R.string.kb_signal_wait,
        R.string.kb_signal_wait_gesture,
        "☝️",
    ),

    /** Point - "I have the answer" */
    ANSWER(
        R.string.kb_signal_answer,
        R.string.kb_signal_answer_gesture,
        "👆",
    ),

    /** Nod/thumbs sideways - "Agree with that" */
    AGREE(
        R.string.kb_signal_agree,
        R.string.kb_signal_agree_gesture,
        "👌",
    ),

    /** Shake head - "Don't think so" */
    DISAGREE(
        R.string.kb_signal_disagree,
        R.string.kb_signal_disagree_gesture,
        "🙅",
    ),
}

/**
 * Records a single conference attempt during training.
 */
data class KBConferenceAttempt(
    val id: String = java.util.UUID.randomUUID().toString(),
    val questionId: String,
    val domain: com.unamentis.modules.knowledgebowl.data.model.KBDomain,
    val conferenceTime: Double,
    val timeLimitUsed: Double,
    val wasCorrect: Boolean,
    val signalUsed: KBHandSignal? = null,
    val timestamp: Long = System.currentTimeMillis(),
) {
    /** Whether the full time was used (timeout) */
    val usedFullTime: Boolean = conferenceTime >= timeLimitUsed * 0.95

    /**
     * Efficiency ratio (0-1, higher is better = faster decisions).
     */
    val efficiency: Double
        get() {
            if (timeLimitUsed <= 0) return 0.0
            return (1.0 - (conferenceTime / timeLimitUsed)).coerceAtLeast(0.0)
        }
}

/**
 * Statistics for a conference training session.
 */
data class KBConferenceStats(
    val totalAttempts: Int,
    val correctCount: Int,
    val averageConferenceTime: Double,
    val fastestTime: Double,
    val slowestTime: Double,
    val timeoutsCount: Int,
    val currentDifficultyLevel: Int,
    val signalDistribution: Map<KBHandSignal, Int>,
) {
    val accuracy: Double
        get() = if (totalAttempts > 0) correctCount.toDouble() / totalAttempts else 0.0

    val averageEfficiency: Double
        get() {
            if (totalAttempts <= 0 || averageConferenceTime <= 0) return 0.0
            val baseTime = KBConferenceConfig.PROGRESSIVE_LEVELS.getOrElse(currentDifficultyLevel) { 15.0 }
            return (1.0 - (averageConferenceTime / baseTime)).coerceAtLeast(0.0)
        }

    val timeoutRate: Double
        get() = if (totalAttempts > 0) timeoutsCount.toDouble() / totalAttempts else 0.0

    companion object {
        val EMPTY =
            KBConferenceStats(
                totalAttempts = 0,
                correctCount = 0,
                averageConferenceTime = 0.0,
                fastestTime = 0.0,
                slowestTime = 0.0,
                timeoutsCount = 0,
                currentDifficultyLevel = 0,
                signalDistribution = emptyMap(),
            )
    }
}

/**
 * Result of a complete conference training session.
 */
data class KBConferenceTrainingResult(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val region: KBRegion,
    val startTime: Long,
    val endTime: Long,
    val stats: KBConferenceStats,
    val finalDifficultyLevel: Int,
    val recommendation: String,
) {
    val durationMs: Long = endTime - startTime

    companion object {
        /**
         * Generate training recommendation based on results.
         */
        fun generateRecommendation(stats: KBConferenceStats): String {
            return when {
                stats.timeoutRate > 0.3 ->
                    "Focus on faster decision-making. Try to identify which teammate has domain expertise quickly."
                stats.accuracy < 0.6 ->
                    "Conference time is good, but accuracy needs work. Practice question analysis before conferring."
                stats.averageEfficiency > 0.7 && stats.accuracy > 0.8 ->
                    "Excellent conferring! Ready to practice at faster time limits."
                else ->
                    "Good progress! Continue practicing to build faster conference habits."
            }
        }
    }
}
