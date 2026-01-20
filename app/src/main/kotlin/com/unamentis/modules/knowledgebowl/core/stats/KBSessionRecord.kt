package com.unamentis.modules.knowledgebowl.core.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Record of a completed practice session for history tracking.
 *
 * @property id Unique session ID
 * @property timestamp When the session occurred (epoch millis)
 * @property studyMode The study mode used
 * @property questionsAnswered Total questions answered
 * @property correctAnswers Total correct answers
 * @property averageTimeSeconds Average response time in seconds
 * @property totalPoints Total points earned
 */
@Serializable
data class KBSessionRecord(
    val id: String,
    val timestamp: Long,
    @SerialName("study_mode")
    val studyMode: String,
    @SerialName("questions_answered")
    val questionsAnswered: Int,
    @SerialName("correct_answers")
    val correctAnswers: Int,
    @SerialName("average_time_seconds")
    val averageTimeSeconds: Float,
    @SerialName("total_points")
    val totalPoints: Int = 0,
) {
    /**
     * Accuracy for this session (0.0 to 1.0).
     */
    val accuracy: Float
        get() = if (questionsAnswered > 0) correctAnswers.toFloat() / questionsAnswered else 0f

    /**
     * Display-friendly date string.
     */
    val dateDisplay: String
        get() {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
            return sdf.format(java.util.Date(timestamp))
        }

    /**
     * Display-friendly time string.
     */
    val timeDisplay: String
        get() {
            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
            return sdf.format(java.util.Date(timestamp))
        }
}
