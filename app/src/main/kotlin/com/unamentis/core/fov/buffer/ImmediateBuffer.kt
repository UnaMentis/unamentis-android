package com.unamentis.core.fov.buffer

/**
 * Content for the immediate buffer.
 *
 * Contains verbatim recent conversation, current segment being played,
 * and any barge-in utterance from the user.
 */
data class ImmediateBuffer(
    /** Current TTS segment being played */
    var currentSegment: TranscriptSegmentContext? = null,
    /** Adjacent segments for context (typically 1-2 before and after) */
    var adjacentSegments: List<TranscriptSegmentContext> = emptyList(),
    /** Recent conversation turns (verbatim) */
    var recentTurns: List<ConversationTurn> = emptyList(),
    /** User's barge-in utterance (if applicable) */
    var bargeInUtterance: String? = null,
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

        // Always include barge-in utterance first (highest priority)
        bargeInUtterance?.takeIf { it.isNotEmpty() }?.let { bargeIn ->
            val bargeInText = "The user just interrupted with: \"$bargeIn\""
            parts.add(bargeInText)
            estimatedTokens += bargeInText.length / CHARS_PER_TOKEN
        }

        // Include current segment
        currentSegment?.let { segment ->
            val segmentText = "Currently teaching: ${segment.content}"
            if (estimatedTokens + segmentText.length / CHARS_PER_TOKEN <= tokenBudget) {
                parts.add(segmentText)
                estimatedTokens += segmentText.length / CHARS_PER_TOKEN
            }
        }

        // Include recent turns (newest first, within budget)
        for (turn in recentTurns.reversed()) {
            val turnText = "[${turn.role.name.lowercase().replaceFirstChar { it.uppercase() }}]: ${turn.content}"
            val turnTokens = turnText.length / CHARS_PER_TOKEN
            if (estimatedTokens + turnTokens <= tokenBudget) {
                // Insert after barge-in but before segment
                val insertIndex = if (parts.isNotEmpty()) 1 else 0
                parts.add(insertIndex, turnText)
                estimatedTokens += turnTokens
            } else {
                break
            }
        }

        return parts.joinToString("\n\n")
    }

    companion object {
        private const val CHARS_PER_TOKEN = 4
    }
}
