package com.unamentis.modules.knowledgebowl.data.model

import java.util.UUID

/**
 * Result of answering a single question in a practice session.
 *
 * @property id Unique identifier for this result
 * @property question The question that was answered
 * @property userAnswer The user's response
 * @property isCorrect Whether the answer was correct
 * @property responseTimeSeconds Time taken to answer in seconds
 * @property wasWithinSpeedTarget Whether answered within the speed target
 * @property wasSkipped Whether the question was skipped
 */
data class KBQuestionResult(
    val id: String = UUID.randomUUID().toString(),
    val question: KBQuestion,
    val userAnswer: String,
    val isCorrect: Boolean,
    val responseTimeSeconds: Double,
    val wasWithinSpeedTarget: Boolean,
    val wasSkipped: Boolean = false,
) {
    companion object {
        /**
         * Default speed target for questions (seconds).
         */
        private const val DEFAULT_SPEED_TARGET = 10.0

        /**
         * Create a result from a question and user answer.
         *
         * @param question The question being answered
         * @param userAnswer The user's response
         * @param responseTimeSeconds Time taken to answer
         * @param speedTargetSeconds Optional speed target override
         * @param wasSkipped Whether the question was skipped
         */
        fun create(
            question: KBQuestion,
            userAnswer: String,
            responseTimeSeconds: Double,
            speedTargetSeconds: Double = DEFAULT_SPEED_TARGET,
            wasSkipped: Boolean = false,
        ): KBQuestionResult {
            val isCorrect = if (wasSkipped) false else checkCorrect(question, userAnswer)
            val withinTarget = if (wasSkipped) false else responseTimeSeconds <= speedTargetSeconds

            return KBQuestionResult(
                question = question,
                userAnswer = userAnswer,
                isCorrect = isCorrect,
                responseTimeSeconds = responseTimeSeconds,
                wasWithinSpeedTarget = withinTarget,
                wasSkipped = wasSkipped,
            )
        }

        /**
         * Check if an answer is correct for a question.
         */
        private fun checkCorrect(
            question: KBQuestion,
            userAnswer: String,
        ): Boolean {
            if (userAnswer.isBlank()) return false

            val normalizedAnswer = userAnswer.trim().lowercase()
            val primaryAnswer = question.answer.primary.trim().lowercase()

            // Check primary answer
            if (normalizedAnswer == primaryAnswer) return true

            // Check acceptable alternatives
            question.answer.acceptable?.forEach { alt ->
                if (normalizedAnswer == alt.trim().lowercase()) return true
            }

            return false
        }
    }
}

/**
 * Domain score breakdown for session summaries.
 *
 * @property total Total questions for this domain
 * @property correct Number of correct answers
 */
data class KBDomainScore(
    val total: Int,
    val correct: Int,
) {
    /**
     * Accuracy as a decimal (0.0 to 1.0).
     */
    val accuracy: Double
        get() = if (total > 0) correct.toDouble() / total else 0.0
}

/**
 * Summary of a practice session for the unified flow.
 *
 * This is a simpler summary used by the unified practice session,
 * distinct from [KBSessionSummary] which is used for persistent storage.
 */
data class KBPracticeSessionSummary(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val averageResponseTime: Double,
    val questionsWithinSpeedTarget: Int,
    val domainBreakdown: Map<String, KBDomainScore>,
    val durationSeconds: Double,
) {
    /**
     * Accuracy as a decimal (0.0 to 1.0).
     */
    val accuracy: Double
        get() = if (totalQuestions > 0) correctAnswers.toDouble() / totalQuestions else 0.0

    /**
     * Speed target hit rate as a decimal (0.0 to 1.0).
     */
    val speedTargetRate: Double
        get() = if (totalQuestions > 0) questionsWithinSpeedTarget.toDouble() / totalQuestions else 0.0
}
