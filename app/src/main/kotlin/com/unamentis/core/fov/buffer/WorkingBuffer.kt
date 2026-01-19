package com.unamentis.core.fov.buffer

/**
 * Content for the working buffer.
 *
 * Contains current topic content, learning objectives, glossary terms,
 * and misconception triggers.
 */
data class WorkingBuffer(
    /** Current topic title */
    var topicTitle: String = "",
    /** Topic description/outline */
    var topicContent: String = "",
    /** Learning objectives for current topic */
    var learningObjectives: List<String> = emptyList(),
    /** Glossary terms relevant to current segment */
    var glossaryTerms: List<GlossaryTerm> = emptyList(),
    /** Alternative explanations available */
    var alternativeExplanations: List<AlternativeExplanation> = emptyList(),
    /** Misconception triggers for current topic */
    var misconceptionTriggers: List<MisconceptionTrigger> = emptyList(),
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

        // Topic title and content (highest priority)
        val titleSection = "Topic: $topicTitle\n$topicContent"
        parts.add(titleSection)
        estimatedTokens += titleSection.length / CHARS_PER_TOKEN

        // Learning objectives
        if (learningObjectives.isNotEmpty() && estimatedTokens < tokenBudget) {
            val objectivesText =
                "Learning Objectives:\n" +
                    learningObjectives.joinToString("\n") { "- $it" }
            if (estimatedTokens + objectivesText.length / CHARS_PER_TOKEN <= tokenBudget) {
                parts.add(objectivesText)
                estimatedTokens += objectivesText.length / CHARS_PER_TOKEN
            }
        }

        // Glossary terms
        if (glossaryTerms.isNotEmpty() && estimatedTokens < tokenBudget) {
            val glossaryText =
                "Key Terms:\n" +
                    glossaryTerms.joinToString("\n") { "- ${it.term}: ${it.definition}" }
            if (estimatedTokens + glossaryText.length / CHARS_PER_TOKEN <= tokenBudget) {
                parts.add(glossaryText)
                estimatedTokens += glossaryText.length / CHARS_PER_TOKEN
            }
        }

        // Misconception triggers (important for tutoring)
        if (misconceptionTriggers.isNotEmpty() && estimatedTokens < tokenBudget) {
            val triggerText =
                "Watch for these common misconceptions:\n" +
                    misconceptionTriggers.joinToString("\n") {
                        "- If student says '${it.triggerPhrase}': ${it.remediation}"
                    }
            if (estimatedTokens + triggerText.length / CHARS_PER_TOKEN <= tokenBudget) {
                parts.add(triggerText)
            }
        }

        return parts.joinToString("\n\n")
    }

    companion object {
        private const val CHARS_PER_TOKEN = 4
    }
}
