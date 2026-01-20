package com.unamentis.modules.knowledgebowl.core.validation

/**
 * Result of answer validation.
 *
 * @property isCorrect Whether the answer was correct
 * @property confidence Confidence level of the match (0.0-1.0)
 * @property matchType How the answer was matched
 * @property matchedAnswer The answer that was matched against (if correct)
 */
data class KBValidationResult(
    val isCorrect: Boolean,
    val confidence: Float,
    val matchType: KBMatchType,
    val matchedAnswer: String?,
) {
    /**
     * Base points earned (can be modified by caller based on timing, rebound, etc.).
     */
    val pointsEarned: Int
        get() = if (isCorrect) 1 else 0

    companion object {
        /**
         * Create a result for a correct exact match.
         */
        fun exactMatch(answer: String): KBValidationResult =
            KBValidationResult(
                isCorrect = true,
                confidence = 1.0f,
                matchType = KBMatchType.EXACT,
                matchedAnswer = answer,
            )

        /**
         * Create a result for a correct acceptable match.
         */
        fun acceptableMatch(answer: String): KBValidationResult =
            KBValidationResult(
                isCorrect = true,
                confidence = 1.0f,
                matchType = KBMatchType.ACCEPTABLE,
                matchedAnswer = answer,
            )

        /**
         * Create a result for a fuzzy match.
         */
        fun fuzzyMatch(
            answer: String,
            confidence: Float,
        ): KBValidationResult =
            KBValidationResult(
                isCorrect = true,
                confidence = confidence,
                matchType = KBMatchType.FUZZY,
                matchedAnswer = answer,
            )

        /**
         * Create a result for no match.
         */
        fun noMatch(): KBValidationResult =
            KBValidationResult(
                isCorrect = false,
                confidence = 0f,
                matchType = KBMatchType.NONE,
                matchedAnswer = null,
            )
    }
}
