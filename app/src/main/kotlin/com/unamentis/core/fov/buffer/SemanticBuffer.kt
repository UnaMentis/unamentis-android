package com.unamentis.core.fov.buffer

/**
 * Content for the semantic buffer.
 *
 * Contains curriculum outline, current position, and topic dependencies.
 */
data class SemanticBuffer(
    /** Compressed curriculum outline (titles + brief objectives) */
    var curriculumOutline: String = "",
    /** Current position in curriculum */
    var currentPosition: CurriculumPosition = CurriculumPosition(),
    /** Topic dependency information */
    var topicDependencies: List<String> = emptyList(),
) {
    /**
     * Render to string within token budget.
     *
     * @param tokenBudget Maximum tokens to use
     * @return Rendered context string
     */
    fun render(tokenBudget: Int): String {
        val parts = mutableListOf<String>()

        // Current position (always included)
        parts.add(currentPosition.render())

        // Curriculum outline (compressed)
        if (curriculumOutline.isNotEmpty()) {
            // Calculate available tokens for outline
            val outlineTokens = curriculumOutline.length / CHARS_PER_TOKEN
            val availableTokens = tokenBudget - parts.joinToString("").length / CHARS_PER_TOKEN

            if (outlineTokens <= availableTokens) {
                parts.add("Course outline:\n$curriculumOutline")
            } else {
                // Truncate to fit
                val truncatedLength = availableTokens * CHARS_PER_TOKEN
                val truncated = curriculumOutline.take(truncatedLength) + "..."
                parts.add("Course outline:\n$truncated")
            }
        }

        return parts.joinToString("\n\n")
    }

    companion object {
        private const val CHARS_PER_TOKEN = 4
    }
}
