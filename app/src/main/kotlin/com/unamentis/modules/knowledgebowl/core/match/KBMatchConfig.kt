package com.unamentis.modules.knowledgebowl.core.match

import androidx.annotation.StringRes
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBRegion

/**
 * Configuration for a simulated Knowledge Bowl match.
 *
 * @property region The competition region (affects scoring rules)
 * @property matchFormat The format of the match (quick, half, full)
 * @property opponentStrengths Strength levels of opponent teams
 * @property enablePracticeMode Whether to show hints and explanations
 */
data class KBMatchConfig(
    val region: KBRegion,
    val matchFormat: MatchFormat,
    val opponentStrengths: List<OpponentStrength>,
    val enablePracticeMode: Boolean,
) {
    companion object {
        /**
         * Create default configuration for a region.
         */
        fun forRegion(region: KBRegion): KBMatchConfig = KBMatchConfig(
            region = region,
            matchFormat = MatchFormat.QUICK_MATCH,
            opponentStrengths = listOf(OpponentStrength.INTERMEDIATE, OpponentStrength.INTERMEDIATE),
            enablePracticeMode = true,
        )
    }
}

/**
 * Match format determining question counts and rounds.
 */
enum class MatchFormat(
    val writtenQuestions: Int,
    val oralRounds: Int,
    val questionsPerOralRound: Int,
    @StringRes val displayNameResId: Int,
) {
    QUICK_MATCH(
        writtenQuestions = 10,
        oralRounds = 2,
        questionsPerOralRound = 10,
        displayNameResId = R.string.kb_match_format_quick,
    ),
    HALF_MATCH(
        writtenQuestions = 20,
        oralRounds = 4,
        questionsPerOralRound = 10,
        displayNameResId = R.string.kb_match_format_half,
    ),
    FULL_MATCH(
        writtenQuestions = 40,
        oralRounds = 8,
        questionsPerOralRound = 10,
        displayNameResId = R.string.kb_match_format_full,
    ),
    ;

    val totalOralQuestions: Int
        get() = oralRounds * questionsPerOralRound

    val totalQuestions: Int
        get() = writtenQuestions + totalOralQuestions
}

/**
 * Opponent team strength level affecting buzz probability and accuracy.
 */
enum class OpponentStrength(
    val buzzProbability: Double,
    val accuracy: Double,
    val baseBuzzTime: Double,
    @StringRes val displayNameResId: Int,
) {
    BEGINNER(
        buzzProbability = 0.3,
        accuracy = 0.4,
        baseBuzzTime = 3.0,
        displayNameResId = R.string.kb_opponent_beginner,
    ),
    INTERMEDIATE(
        buzzProbability = 0.5,
        accuracy = 0.6,
        baseBuzzTime = 2.5,
        displayNameResId = R.string.kb_opponent_intermediate,
    ),
    ADVANCED(
        buzzProbability = 0.7,
        accuracy = 0.75,
        baseBuzzTime = 2.0,
        displayNameResId = R.string.kb_opponent_advanced,
    ),
    EXPERT(
        buzzProbability = 0.85,
        accuracy = 0.9,
        baseBuzzTime = 1.5,
        displayNameResId = R.string.kb_opponent_expert,
    ),
}
