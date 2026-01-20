package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Record of a user's attempt at answering a question.
 *
 * @property id Unique attempt identifier
 * @property questionId ID of the question attempted
 * @property domain Domain for performance tracking
 * @property timestamp When the attempt was made (Unix millis)
 * @property userAnswer User's text answer (for oral rounds)
 * @property selectedChoice Index (0-3) of selected MCQ option (for written rounds)
 * @property responseTimeSeconds Time taken to answer
 * @property usedConference Whether team conference was used
 * @property conferenceTimeSeconds Time spent in conference (if used)
 * @property wasCorrect Whether the answer was correct
 * @property pointsEarned Points earned for this answer
 * @property roundType Type of round (written/oral)
 * @property wasRebound Whether this was a rebound (answered after opponent missed)
 * @property matchType How the answer was validated
 */
@Serializable
data class KBQuestionAttempt(
    val id: String,
    @SerialName("question_id")
    val questionId: String,
    val domain: KBDomain,
    val timestamp: Long = System.currentTimeMillis(),
    @SerialName("user_answer")
    val userAnswer: String? = null,
    @SerialName("selected_choice")
    val selectedChoice: Int? = null,
    @SerialName("response_time_seconds")
    val responseTimeSeconds: Float,
    @SerialName("used_conference")
    val usedConference: Boolean = false,
    @SerialName("conference_time_seconds")
    val conferenceTimeSeconds: Float? = null,
    @SerialName("was_correct")
    val wasCorrect: Boolean,
    @SerialName("points_earned")
    val pointsEarned: Int,
    @SerialName("round_type")
    val roundType: KBRoundType,
    @SerialName("was_rebound")
    val wasRebound: Boolean = false,
    @SerialName("match_type")
    val matchType: KBMatchType? = null,
    @SerialName("was_skipped")
    val wasSkipped: Boolean = false,
) {
    companion object {
        /**
         * Generate a unique ID for a new attempt.
         */
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }
}
