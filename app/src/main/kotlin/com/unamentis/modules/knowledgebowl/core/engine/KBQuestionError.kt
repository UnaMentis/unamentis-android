package com.unamentis.modules.knowledgebowl.core.engine

/**
 * Errors that can occur during question loading and management.
 */
sealed class KBQuestionError : Exception() {
    /**
     * Question bundle not found in app resources.
     */
    data object BundleNotFound : KBQuestionError() {
        override val message: String = "Question bundle not found in app resources"
    }

    /**
     * Failed to decode questions from JSON.
     */
    data class DecodingFailed(val details: String) : KBQuestionError() {
        override val message: String = "Failed to decode questions: $details"
    }

    /**
     * Not enough questions available for the requested operation.
     */
    data class InsufficientQuestions(val needed: Int, val available: Int) : KBQuestionError() {
        override val message: String = "Not enough questions available: need $needed, have $available"
    }

    /**
     * Failed to load questions from URL.
     */
    data class LoadFailed(val url: String, val reason: String) : KBQuestionError() {
        override val message: String = "Failed to load questions from $url: $reason"
    }
}
