package com.unamentis.modules.knowledgebowl.core.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Aggregate statistics calculated from all completed sessions.
 *
 * @property totalSessions Number of completed sessions
 * @property totalQuestions Total questions answered across all sessions
 * @property totalCorrect Total correct answers
 * @property totalIncorrect Total incorrect answers
 * @property overallAccuracy Overall accuracy (0.0 to 1.0)
 * @property writtenAccuracy Accuracy for written rounds (0.0 to 1.0)
 * @property oralAccuracy Accuracy for oral rounds (0.0 to 1.0)
 * @property mostRecentSessionMillis Timestamp of most recent session
 * @property currentStreak Consecutive days with sessions
 */
@Serializable
data class KBStatistics(
    @SerialName("total_sessions")
    val totalSessions: Int = 0,
    @SerialName("total_questions")
    val totalQuestions: Int = 0,
    @SerialName("total_correct")
    val totalCorrect: Int = 0,
    @SerialName("total_incorrect")
    val totalIncorrect: Int = 0,
    @SerialName("overall_accuracy")
    val overallAccuracy: Double = 0.0,
    @SerialName("written_accuracy")
    val writtenAccuracy: Double = 0.0,
    @SerialName("oral_accuracy")
    val oralAccuracy: Double = 0.0,
    @SerialName("most_recent_session_millis")
    val mostRecentSessionMillis: Long? = null,
    @SerialName("current_streak")
    val currentStreak: Int = 0,
)
