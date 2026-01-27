package com.unamentis.modules.knowledgebowl.core.match

import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import java.util.Date
import java.util.UUID

/**
 * Current phase of the match.
 */
sealed class MatchPhase {
    data object NotStarted : MatchPhase()
    data object WrittenRound : MatchPhase()
    data object WrittenReview : MatchPhase()
    data class OralRound(val roundNumber: Int) : MatchPhase()
    data class OralReview(val roundNumber: Int) : MatchPhase()
    data object FinalResults : MatchPhase()

    val isWritten: Boolean
        get() = this is WrittenRound

    val isOral: Boolean
        get() = this is OralRound

    val isComplete: Boolean
        get() = this is FinalResults
}

/**
 * Represents a team in the match.
 *
 * @property id Unique identifier
 * @property name Team display name
 * @property isPlayer Whether this is the player's team
 * @property strength AI strength (null for player team)
 * @property writtenScore Score from written round
 * @property oralScore Score from oral rounds
 */
data class KBTeam(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPlayer: Boolean = false,
    val strength: OpponentStrength? = null,
    val writtenScore: Int = 0,
    val oralScore: Int = 0,
) {
    val totalScore: Int
        get() = writtenScore + oralScore

    fun withWrittenScore(score: Int): KBTeam = copy(writtenScore = score)

    fun withOralScore(score: Int): KBTeam = copy(oralScore = score)

    fun addWrittenPoints(points: Int): KBTeam = copy(writtenScore = writtenScore + points)

    fun addOralPoints(points: Int): KBTeam = copy(oralScore = oralScore + points)
}

/**
 * Result of a question in the match.
 *
 * @property id Unique identifier
 * @property question The question that was answered
 * @property phase Match phase when answered
 * @property answeringTeamId ID of the team that answered (null if no one answered)
 * @property wasCorrect Whether the answer was correct
 * @property pointsAwarded Points awarded (may be negative for penalties)
 * @property responseTime Time taken to answer in seconds
 * @property timestamp When the answer was submitted
 */
data class KBMatchQuestionResult(
    val id: String = UUID.randomUUID().toString(),
    val question: KBQuestion,
    val phase: MatchPhase,
    val answeringTeamId: String?,
    val wasCorrect: Boolean,
    val pointsAwarded: Int,
    val responseTime: Double,
    val timestamp: Date = Date(),
)

/**
 * Summary of a completed match.
 *
 * @property matchId Unique match identifier
 * @property config Match configuration used
 * @property teams Final team standings (sorted by score)
 * @property results All question results
 * @property startTime When the match started
 * @property endTime When the match ended
 * @property playerRank Player's final rank (1-based)
 * @property playerStats Detailed player statistics
 */
data class KBMatchSummary(
    val matchId: String = UUID.randomUUID().toString(),
    val config: KBMatchConfig,
    val teams: List<KBTeam>,
    val results: List<KBMatchQuestionResult>,
    val startTime: Date,
    val endTime: Date,
    val playerRank: Int,
    val playerStats: PlayerMatchStats,
) {
    val duration: Long
        get() = endTime.time - startTime.time

    val playerWon: Boolean
        get() = playerRank == 1
}

/**
 * Detailed statistics for the player's performance.
 *
 * @property writtenCorrect Number of correct written answers
 * @property writtenTotal Total written questions attempted
 * @property oralCorrect Number of correct oral answers
 * @property oralTotal Total oral questions attempted
 * @property averageResponseTime Average time to answer in seconds
 * @property domainsStrength Accuracy by domain (0.0 to 1.0)
 */
data class PlayerMatchStats(
    val writtenCorrect: Int,
    val writtenTotal: Int,
    val oralCorrect: Int,
    val oralTotal: Int,
    val averageResponseTime: Double,
    val domainsStrength: Map<KBDomain, Double>,
) {
    val writtenAccuracy: Double
        get() = if (writtenTotal > 0) writtenCorrect.toDouble() / writtenTotal else 0.0

    val oralAccuracy: Double
        get() = if (oralTotal > 0) oralCorrect.toDouble() / oralTotal else 0.0

    val overallAccuracy: Double
        get() {
            val total = writtenTotal + oralTotal
            val correct = writtenCorrect + oralCorrect
            return if (total > 0) correct.toDouble() / total else 0.0
        }

    val totalCorrect: Int
        get() = writtenCorrect + oralCorrect

    val totalAnswered: Int
        get() = writtenTotal + oralTotal
}

/**
 * Result of a buzz attempt in oral round.
 *
 * @property teamId ID of the team that buzzed
 * @property buzzTime Time to buzz in seconds
 */
data class BuzzResult(
    val teamId: String,
    val buzzTime: Double,
)

/**
 * Current state of a match for UI consumption.
 */
data class MatchUiState(
    val phase: MatchPhase = MatchPhase.NotStarted,
    val teams: List<KBTeam> = emptyList(),
    val currentQuestion: KBQuestion? = null,
    val writtenProgress: Pair<Int, Int> = 0 to 0,
    val oralProgress: OralProgress = OralProgress(),
    val lastResult: KBMatchQuestionResult? = null,
    val summary: KBMatchSummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Progress through oral rounds.
 *
 * @property currentRound Current round number (1-based)
 * @property totalRounds Total number of rounds
 * @property currentQuestion Current question in round (0-based)
 * @property questionsPerRound Questions per round
 */
data class OralProgress(
    val currentRound: Int = 1,
    val totalRounds: Int = 0,
    val currentQuestion: Int = 0,
    val questionsPerRound: Int = 0,
) {
    val roundProgress: Float
        get() = if (questionsPerRound > 0) currentQuestion.toFloat() / questionsPerRound else 0f

    val overallProgress: Float
        get() {
            if (totalRounds == 0 || questionsPerRound == 0) return 0f
            val total = totalRounds * questionsPerRound
            val completed = (currentRound - 1) * questionsPerRound + currentQuestion
            return completed.toFloat() / total
        }
}
