package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.Serializable

/**
 * Answer with primary response and acceptable alternatives.
 *
 * @property primary The canonical correct answer
 * @property acceptable Alternative acceptable answers (e.g., "USA" for "United States")
 * @property answerType Type of answer for specialized matching logic
 */
@Serializable
data class KBAnswer(
    val primary: String,
    val acceptable: List<String>? = null,
    val answerType: KBAnswerType = KBAnswerType.TEXT,
) {
    /**
     * All valid answers including primary and alternatives.
     */
    val allValidAnswers: List<String>
        get() = listOf(primary) + (acceptable ?: emptyList())

    /**
     * Check if a given answer matches any valid answer (case-insensitive).
     */
    fun matchesExactly(answer: String): Boolean {
        val normalizedAnswer = answer.trim().lowercase()
        return allValidAnswers.any { it.trim().lowercase() == normalizedAnswer }
    }
}
