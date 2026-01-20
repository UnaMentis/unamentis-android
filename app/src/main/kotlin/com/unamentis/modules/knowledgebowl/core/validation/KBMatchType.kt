package com.unamentis.modules.knowledgebowl.core.validation

/**
 * Type of match found during answer validation.
 */
enum class KBMatchType {
    /**
     * Exact match with primary or acceptable answer.
     */
    EXACT,

    /**
     * Match found in acceptable alternatives list.
     */
    ACCEPTABLE,

    /**
     * Fuzzy match within configured threshold.
     */
    FUZZY,

    /**
     * No match found.
     */
    NONE,
}
