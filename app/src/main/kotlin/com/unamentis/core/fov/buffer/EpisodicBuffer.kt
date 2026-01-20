package com.unamentis.core.fov.buffer

/**
 * Content for the episodic buffer.
 *
 * Contains session history including topic summaries, user questions,
 * addressed misconceptions, and learner signals.
 */
data class EpisodicBuffer(
    /** Summaries of prior topics covered in this session */
    var topicSummaries: MutableList<FOVTopicSummary> = mutableListOf(),
    /** User's questions/confusions from earlier */
    var userQuestions: MutableList<UserQuestion> = mutableListOf(),
    /** Misconceptions that were triggered and addressed */
    var addressedMisconceptions: MutableList<AddressedMisconception> = mutableListOf(),
    /** Learning profile signals detected during session */
    var learnerSignals: LearnerSignals = LearnerSignals(),
) {
    /**
     * Render to string within token budget.
     *
     * @param tokenBudget Maximum tokens to use
     * @return Rendered context string
     */
    fun render(tokenBudget: Int): String {
        val parts = mutableListOf<String>()
        var estimatedTokens = 0

        // Learner signals (concise, high value)
        val signalsText = learnerSignals.render()
        if (signalsText.isNotEmpty()) {
            parts.add(signalsText)
            estimatedTokens += signalsText.length / CHARS_PER_TOKEN
        }

        // Topic summaries (most recent first)
        if (topicSummaries.isNotEmpty()) {
            val summariesText =
                "Topics covered:\n" +
                    topicSummaries.takeLast(MAX_SUMMARIES_TO_SHOW).joinToString("\n") {
                        "- ${it.title}: ${it.summary}"
                    }
            if (estimatedTokens + summariesText.length / CHARS_PER_TOKEN <= tokenBudget) {
                parts.add(summariesText)
                estimatedTokens += summariesText.length / CHARS_PER_TOKEN
            }
        }

        // Recent user questions
        if (userQuestions.isNotEmpty() && estimatedTokens < tokenBudget) {
            val questionsText =
                "Student's earlier questions:\n" +
                    userQuestions.takeLast(MAX_QUESTIONS_TO_SHOW).joinToString("\n") {
                        "- ${it.question}"
                    }
            if (estimatedTokens + questionsText.length / CHARS_PER_TOKEN <= tokenBudget) {
                parts.add(questionsText)
            }
        }

        return parts.joinToString("\n\n")
    }

    companion object {
        private const val CHARS_PER_TOKEN = 4
        private const val MAX_SUMMARIES_TO_SHOW = 5
        private const val MAX_QUESTIONS_TO_SHOW = 3
    }
}
