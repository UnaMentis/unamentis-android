package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary of a completed Knowledge Bowl session.
 *
 * @property sessionId ID of the completed session
 * @property roundType Type of round (written/oral)
 * @property region Regional rules used
 * @property totalQuestions Total questions attempted
 * @property totalCorrect Number of correct answers
 * @property totalPoints Total points earned
 * @property accuracy Accuracy as a fraction (0.0 to 1.0)
 * @property averageResponseTimeSeconds Average time per response
 * @property durationSeconds Total session duration
 * @property completedAtMillis When the session was completed
 */
@Serializable
data class KBSessionSummary(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("round_type")
    val roundType: KBRoundType,
    val region: KBRegion,
    @SerialName("total_questions")
    val totalQuestions: Int,
    @SerialName("total_correct")
    val totalCorrect: Int,
    @SerialName("total_points")
    val totalPoints: Int,
    val accuracy: Float,
    @SerialName("average_response_time_seconds")
    val averageResponseTimeSeconds: Float,
    @SerialName("duration_seconds")
    val durationSeconds: Float,
    @SerialName("completed_at_millis")
    val completedAtMillis: Long,
) {
    /**
     * Accuracy as a percentage string (e.g., "85%").
     */
    val accuracyPercent: String
        get() = "${(accuracy * 100).toInt()}%"

    /**
     * Duration formatted for display (e.g., "5:30").
     */
    val durationDisplay: String
        get() {
            val minutes = (durationSeconds / 60).toInt()
            val seconds = (durationSeconds % 60).toInt()
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }

    /**
     * Average response time formatted for display (e.g., "4.2s").
     */
    val averageResponseTimeDisplay: String
        get() = String.format(java.util.Locale.US, "%.1fs", averageResponseTimeSeconds)

    companion object {
        /**
         * Create a summary from a completed session.
         */
        fun from(session: KBSession): KBSessionSummary {
            return KBSessionSummary(
                sessionId = session.id,
                roundType = session.config.roundType,
                region = session.config.region,
                totalQuestions = session.attempts.size,
                totalCorrect = session.correctCount,
                totalPoints = session.totalPoints,
                accuracy = session.accuracy,
                averageResponseTimeSeconds = session.averageResponseTimeSeconds,
                durationSeconds = session.durationSeconds,
                completedAtMillis = session.endTimeMillis ?: System.currentTimeMillis(),
            )
        }
    }
}
