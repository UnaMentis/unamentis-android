package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Knowledge Bowl practice session.
 *
 * @property id Unique session identifier
 * @property config Session configuration
 * @property startTimeMillis When the session started
 * @property endTimeMillis When the session ended (null if in progress)
 * @property attempts List of question attempts
 * @property currentQuestionIndex Current position in question list
 * @property isComplete Whether the session has completed
 */
@Serializable
data class KBSession(
    val id: String,
    val config: KBSessionConfig,
    @SerialName("start_time_millis")
    val startTimeMillis: Long = System.currentTimeMillis(),
    @SerialName("end_time_millis")
    var endTimeMillis: Long? = null,
    val attempts: MutableList<KBQuestionAttempt> = mutableListOf(),
    @SerialName("current_question_index")
    var currentQuestionIndex: Int = 0,
    @SerialName("is_complete")
    var isComplete: Boolean = false,
) {
    /**
     * Duration of the session in seconds.
     */
    val durationSeconds: Float
        get() {
            val end = endTimeMillis ?: System.currentTimeMillis()
            return (end - startTimeMillis) / 1000f
        }

    /**
     * Number of correct answers.
     */
    val correctCount: Int
        get() = attempts.count { it.wasCorrect }

    /**
     * Number of incorrect answers.
     */
    val incorrectCount: Int
        get() = attempts.count { !it.wasCorrect }

    /**
     * Total points earned.
     */
    val totalPoints: Int
        get() = attempts.sumOf { it.pointsEarned }

    /**
     * Accuracy as a fraction (0.0 to 1.0).
     */
    val accuracy: Float
        get() = if (attempts.isEmpty()) 0f else correctCount.toFloat() / attempts.size

    /**
     * Average response time in seconds.
     */
    val averageResponseTimeSeconds: Float
        get() = if (attempts.isEmpty()) 0f else attempts.map { it.responseTimeSeconds }.average().toFloat()

    /**
     * Progress through the session (0.0 to 1.0).
     */
    val progress: Float
        get() = if (config.questionCount == 0) 0f else attempts.size.toFloat() / config.questionCount

    /**
     * Time remaining in session (null if no time limit).
     */
    fun timeRemainingSeconds(currentTimeMillis: Long = System.currentTimeMillis()): Float? {
        val timeLimit = config.timeLimitSeconds ?: return null
        val elapsed = (currentTimeMillis - startTimeMillis) / 1000f
        return maxOf(0f, timeLimit - elapsed)
    }

    /**
     * Timer state based on remaining time.
     */
    @Suppress("ReturnCount")
    fun timerState(currentTimeMillis: Long = System.currentTimeMillis()): KBTimerState? {
        val timeLimit = config.timeLimitSeconds ?: return null
        if (timeLimit <= 0) return null
        val remaining = timeRemainingSeconds(currentTimeMillis) ?: return null
        val percent = remaining / timeLimit
        return KBTimerState.from(percent)
    }

    /**
     * Accuracy breakdown by domain.
     */
    val performanceByDomain: Map<KBDomain, DomainPerformance>
        get() {
            val stats = mutableMapOf<KBDomain, Triple<Int, Int, Float>>() // correct, total, time

            for (attempt in attempts) {
                val current = stats[attempt.domain] ?: Triple(0, 0, 0f)
                val newCorrect = current.first + if (attempt.wasCorrect) 1 else 0
                val newTotal = current.second + 1
                val newTime = current.third + attempt.responseTimeSeconds
                stats[attempt.domain] = Triple(newCorrect, newTotal, newTime)
            }

            return stats.mapValues { (domain, stat) ->
                val avgTime = if (stat.second > 0) stat.third / stat.second else 0f
                DomainPerformance(
                    domain = domain,
                    correct = stat.first,
                    total = stat.second,
                    averageTimeSeconds = avgTime,
                )
            }
        }

    /**
     * Generate a summary of this session.
     *
     * @param durationMillis Optional duration in milliseconds (defaults to calculated)
     */
    fun generateSummary(durationMillis: Long? = null): KBSessionSummary {
        val duration = durationMillis ?: (System.currentTimeMillis() - startTimeMillis)

        return KBSessionSummary(
            sessionId = id,
            roundType = config.roundType,
            region = config.region,
            totalQuestions = attempts.size,
            totalCorrect = correctCount,
            totalPoints = totalPoints,
            accuracy = accuracy,
            averageResponseTimeSeconds = averageResponseTimeSeconds,
            durationSeconds = duration / 1000f,
            completedAtMillis = endTimeMillis ?: System.currentTimeMillis(),
        )
    }

    companion object {
        /**
         * Generate a unique ID for a new session.
         */
        fun generateId(): String = java.util.UUID.randomUUID().toString()

        /**
         * Create a new session with the given configuration.
         */
        fun create(config: KBSessionConfig): KBSession {
            return KBSession(
                id = generateId(),
                config = config,
            )
        }
    }
}

/**
 * Performance metrics for a specific domain.
 */
@Serializable
data class DomainPerformance(
    val domain: KBDomain,
    val correct: Int,
    val total: Int,
    @SerialName("average_time_seconds")
    val averageTimeSeconds: Float,
) {
    /**
     * Accuracy for this domain (0.0 to 1.0).
     */
    val accuracy: Float
        get() = if (total == 0) 0f else correct.toFloat() / total
}

/**
 * Current state of an active session.
 */
sealed class KBSessionState {
    /** Session has not started yet */
    data object NotStarted : KBSessionState()

    /** Session is actively in progress */
    data class InProgress(val questionIndex: Int) : KBSessionState()

    /** Session is paused */
    data object Paused : KBSessionState()

    /** Reviewing a specific attempt */
    data class Reviewing(val attemptIndex: Int) : KBSessionState()

    /** Session completed successfully */
    data object Completed : KBSessionState()

    /** Session expired due to time limit */
    data object Expired : KBSessionState()
}

/**
 * Timer states for visual urgency feedback.
 */
enum class KBTimerState {
    /** > 60% remaining */
    NORMAL,

    /** 30-60% remaining */
    FOCUSED,

    /** 10-30% remaining - orange with pulse */
    URGENT,

    /** < 10% remaining - magenta with fast pulse */
    CRITICAL,
    ;

    /**
     * Pulse speed in seconds (null for no pulse).
     */
    val pulseSpeedSeconds: Float?
        get() =
            when (this) {
                NORMAL, FOCUSED -> null
                URGENT -> 1.0f
                CRITICAL -> 0.3f
            }

    companion object {
        /**
         * Determine timer state from remaining percentage.
         */
        fun from(remainingPercent: Float): KBTimerState {
            return when {
                remainingPercent >= 0.6f -> NORMAL
                remainingPercent >= 0.3f -> FOCUSED
                remainingPercent >= 0.1f -> URGENT
                else -> CRITICAL
            }
        }
    }
}
