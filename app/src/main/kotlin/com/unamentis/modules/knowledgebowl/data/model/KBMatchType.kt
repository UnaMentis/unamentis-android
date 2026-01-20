package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How an answer was validated and matched.
 */
@Serializable
enum class KBMatchType {
    /** Exact match to primary answer */
    @SerialName("exact")
    EXACT,

    /** Matched an acceptable alternative answer */
    @SerialName("acceptable")
    ACCEPTABLE,

    /** Matched via fuzzy matching (handles typos) */
    @SerialName("fuzzy")
    FUZZY,

    /** Matched via AI/ML evaluation */
    @SerialName("ai")
    AI,

    /** Manually marked correct */
    @SerialName("manual")
    MANUAL,

    /** No match - answer is incorrect */
    @SerialName("none")
    NONE,
}
