package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Validation strictness levels for answer matching.
 *
 * Controls how aggressively the answer validator attempts to match
 * user responses against correct answers.
 *
 * @property level Numeric level for comparison (lower = stricter)
 * @property displayName Human-readable name for logging/debugging
 */
@Serializable
enum class KBValidationStrictness(
    val level: Int,
    val displayName: String,
) : Comparable<KBValidationStrictness> {
    /** Exact match plus fuzzy (Levenshtein) only */
    @SerialName("strict")
    STRICT(1, "Strict"),

    /** Adds phonetic, n-gram, and token matching */
    @SerialName("standard")
    STANDARD(2, "Standard"),

    /** Adds semantic matching (embeddings, LLM) */
    @SerialName("lenient")
    LENIENT(3, "Lenient"),
    ;

    /**
     * Check if this strictness is stricter than another.
     */
    fun isStricterThan(other: KBValidationStrictness): Boolean = level < other.level

    /**
     * Check if this strictness is more lenient than another.
     */
    fun isMoreLenientThan(other: KBValidationStrictness): Boolean = level > other.level

    companion object {
        /**
         * Default validation strictness (standard).
         */
        val DEFAULT = STANDARD
    }
}
