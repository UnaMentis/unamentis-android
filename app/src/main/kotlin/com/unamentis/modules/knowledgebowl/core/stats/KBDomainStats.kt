package com.unamentis.modules.knowledgebowl.core.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Statistics for a specific knowledge domain.
 *
 * @property totalAnswered Total questions answered in this domain
 * @property totalCorrect Total correct answers in this domain
 */
@Serializable
data class KBDomainStats(
    @SerialName("total_answered")
    val totalAnswered: Int = 0,
    @SerialName("total_correct")
    val totalCorrect: Int = 0,
) {
    /**
     * Accuracy for this domain (0.0 to 1.0).
     */
    val accuracy: Float
        get() = if (totalAnswered > 0) totalCorrect.toFloat() / totalAnswered else 0f

    /**
     * Add an attempt to these stats.
     */
    fun withAttempt(wasCorrect: Boolean): KBDomainStats =
        copy(
            totalAnswered = totalAnswered + 1,
            totalCorrect = if (wasCorrect) totalCorrect + 1 else totalCorrect,
        )

    /**
     * Merge with another domain stats.
     */
    fun merge(other: KBDomainStats): KBDomainStats =
        copy(
            totalAnswered = totalAnswered + other.totalAnswered,
            totalCorrect = totalCorrect + other.totalCorrect,
        )
}
