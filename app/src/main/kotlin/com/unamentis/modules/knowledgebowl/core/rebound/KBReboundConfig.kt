package com.unamentis.modules.knowledgebowl.core.rebound

import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Configuration for rebound training sessions.
 */
@Serializable
data class KBReboundConfig(
    val region: KBRegion,
    val reboundProbability: Double = 0.5,
    val opponentAccuracy: Double = 0.6,
    val questionCount: Int = 15,
    val showOpponentAnswer: Boolean = true,
    val useProgressiveDifficulty: Boolean = true,
) {
    init {
        require(reboundProbability in 0.3..0.8) { "Rebound probability must be between 0.3 and 0.8" }
        require(opponentAccuracy in 0.3..0.9) { "Opponent accuracy must be between 0.3 and 0.9" }
    }

    companion object {
        /** Default configuration for a region */
        fun forRegion(region: KBRegion) =
            KBReboundConfig(
                region = region,
                reboundProbability = 0.5,
                opponentAccuracy = 0.6,
                questionCount = 15,
                showOpponentAnswer = true,
                useProgressiveDifficulty = true,
            )
    }
}

/**
 * Represents a rebound scenario in training.
 */
data class KBReboundScenario(
    val id: String = UUID.randomUUID().toString(),
    val question: KBQuestion,
    val opponentBuzzed: Boolean,
    val opponentAnswer: String?,
    val opponentWasCorrect: Boolean,
    val isReboundOpportunity: Boolean,
    val timeAfterOpponentAnswer: Double = 0.0,
)

/**
 * Records a rebound attempt.
 */
data class KBReboundAttempt(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val questionId: String,
    val domain: KBDomain,
    val wasReboundOpportunity: Boolean,
    val userBuzzedOnRebound: Boolean,
    val userAnswer: String? = null,
    val wasCorrect: Boolean,
    val responseTime: Double,
    val pointsEarned: Int = 0,
    val strategicDecision: ReboundDecision,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Strategic decision on a rebound.
 */
enum class ReboundDecision(
    val displayNameResId: Int,
    val isPositive: Boolean,
) {
    BUZZED_CORRECTLY(R.string.kb_rebound_buzzed_correctly, true),
    BUZZED_INCORRECTLY(R.string.kb_rebound_buzzed_incorrectly, false),
    STRATEGIC_HOLD(R.string.kb_rebound_strategic_hold, true),
    MISSED_OPPORTUNITY(R.string.kb_rebound_missed_opportunity, false),
    CORRECTLY_IGNORED(R.string.kb_rebound_correctly_ignored, true),
}

/**
 * Statistics from a rebound training session.
 */
data class KBReboundStats(
    val totalScenarios: Int,
    val reboundOpportunities: Int,
    val reboundsTaken: Int,
    val reboundsCorrect: Int,
    val strategicHolds: Int,
    val missedOpportunities: Int,
    val averageResponseTime: Double,
    val totalPoints: Int,
    val reboundAccuracy: Double,
    val opportunityCapture: Double,
) {
    companion object {
        val EMPTY =
            KBReboundStats(
                totalScenarios = 0,
                reboundOpportunities = 0,
                reboundsTaken = 0,
                reboundsCorrect = 0,
                strategicHolds = 0,
                missedOpportunities = 0,
                averageResponseTime = 0.0,
                totalPoints = 0,
                reboundAccuracy = 0.0,
                opportunityCapture = 0.0,
            )

        fun fromAttempts(attempts: List<KBReboundAttempt>): KBReboundStats {
            if (attempts.isEmpty()) return EMPTY

            val reboundOpportunities = attempts.count { it.wasReboundOpportunity }
            val reboundsTaken = attempts.count { it.wasReboundOpportunity && it.userBuzzedOnRebound }
            val reboundsCorrect =
                attempts.count {
                    it.wasReboundOpportunity && it.userBuzzedOnRebound && it.wasCorrect
                }
            val strategicHolds = attempts.count { it.strategicDecision == ReboundDecision.STRATEGIC_HOLD }
            val missedOpportunities = attempts.count { it.strategicDecision == ReboundDecision.MISSED_OPPORTUNITY }

            val responseTimes = attempts.map { it.responseTime }
            val avgTime = if (responseTimes.isNotEmpty()) responseTimes.average() else 0.0

            val totalPoints = attempts.sumOf { it.pointsEarned }

            val reboundAccuracy =
                if (reboundsTaken > 0) {
                    reboundsCorrect.toDouble() / reboundsTaken.toDouble()
                } else {
                    0.0
                }

            val opportunityCapture =
                if (reboundOpportunities > 0) {
                    reboundsTaken.toDouble() / reboundOpportunities.toDouble()
                } else {
                    0.0
                }

            return KBReboundStats(
                totalScenarios = attempts.size,
                reboundOpportunities = reboundOpportunities,
                reboundsTaken = reboundsTaken,
                reboundsCorrect = reboundsCorrect,
                strategicHolds = strategicHolds,
                missedOpportunities = missedOpportunities,
                averageResponseTime = avgTime,
                totalPoints = totalPoints,
                reboundAccuracy = reboundAccuracy,
                opportunityCapture = opportunityCapture,
            )
        }
    }
}

/**
 * Result of a rebound training session.
 */
data class KBReboundTrainingResult(
    val sessionId: String = UUID.randomUUID().toString(),
    val region: KBRegion,
    val startTime: Long,
    val endTime: Long,
    val stats: KBReboundStats,
    val recommendation: String,
) {
    companion object {
        fun generateRecommendation(stats: KBReboundStats): String {
            return when {
                stats.reboundAccuracy >= 0.8 ->
                    "Excellent rebound instincts! You're capitalizing on opponent mistakes effectively."
                stats.missedOpportunities > stats.reboundsTaken ->
                    "Try to be more aggressive on rebounds. You're missing opportunities to score."
                stats.reboundAccuracy < 0.5 ->
                    "Focus on only buzzing when confident. A wrong rebound costs points."
                else ->
                    "Good balance between aggression and caution. Keep practicing!"
            }
        }
    }
}

/**
 * Feedback model for rebound attempts.
 */
data class ReboundFeedback(
    val title: String,
    val message: String,
    val isPositive: Boolean,
    val points: Int,
)
