package com.unamentis.modules.knowledgebowl.core.match

import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import kotlin.random.Random

/**
 * Simulates an opponent team's behavior in a Knowledge Bowl match.
 *
 * AI opponents have different skill levels affecting their:
 * - Probability of buzzing on oral questions
 * - Speed of buzzing
 * - Accuracy of answers
 *
 * @property teamId The team this simulator controls
 * @property strength The skill level of this opponent
 */
class KBOpponentSimulator(
    val teamId: String,
    val strength: OpponentStrength,
) {
    /**
     * Attempt to buzz on an oral question.
     *
     * @param question The question being asked
     * @return Buzz time in seconds if the opponent would buzz, null otherwise
     */
    fun attemptBuzz(question: KBQuestion): Double? {
        // Higher strength = more likely to buzz
        val shouldBuzz = Random.nextDouble() < strength.buzzProbability

        if (!shouldBuzz) return null

        // Buzz time based on strength (faster for stronger teams)
        // Add some randomness to make it more realistic
        val variation = Random.nextDouble(-0.5, 0.5)
        return strength.baseBuzzTime + variation
    }

    /**
     * Answer a written question.
     *
     * @param question The question to answer
     * @return True if the answer is correct
     */
    fun answerWrittenQuestion(question: KBQuestion): Boolean {
        return Random.nextDouble() < strength.accuracy
    }

    /**
     * Answer an oral question after buzzing.
     *
     * @param question The question to answer
     * @return True if the answer is correct
     */
    fun answerOralQuestion(question: KBQuestion): Boolean {
        return Random.nextDouble() < strength.accuracy
    }

    /**
     * Get a simulated answer string for display purposes.
     * This is a placeholder that would normally come from a more
     * sophisticated AI system.
     */
    fun getSimulatedAnswer(question: KBQuestion, isCorrect: Boolean): String {
        return if (isCorrect) {
            question.answer.primary
        } else {
            // Return a plausible but incorrect answer
            generateIncorrectAnswer(question)
        }
    }

    private fun generateIncorrectAnswer(question: KBQuestion): String {
        // For MCQ questions, pick a wrong option
        if (!question.mcqOptions.isNullOrEmpty()) {
            val wrongOptions = question.mcqOptions.filter { it != question.answer.primary }
            return wrongOptions.randomOrNull() ?: "Unknown"
        }

        // For other questions, return a generic incorrect response
        return when (question.domain) {
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.SCIENCE -> "Newton"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.MATHEMATICS -> "42"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.LITERATURE -> "Shakespeare"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.HISTORY -> "1776"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.SOCIAL_STUDIES -> "Washington D.C."
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.ARTS -> "Picasso"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.CURRENT_EVENTS -> "2024"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.LANGUAGE -> "Latin"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.TECHNOLOGY -> "Binary"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.POP_CULTURE -> "The Beatles"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.RELIGION_PHILOSOPHY -> "Aristotle"
            com.unamentis.modules.knowledgebowl.data.model.KBDomain.MISCELLANEOUS -> "Unknown"
        }
    }

    companion object {
        /**
         * Team names for opponent teams.
         */
        val OPPONENT_TEAM_NAMES = listOf(
            "Alpha Academy",
            "Beta School",
            "Gamma Institute",
            "Delta High",
            "Epsilon Prep",
        )

        /**
         * Get a team name for the given index.
         */
        fun getTeamName(index: Int): String {
            return OPPONENT_TEAM_NAMES[index % OPPONENT_TEAM_NAMES.size]
        }
    }
}
