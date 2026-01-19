package com.unamentis.modules.knowledgebowl.core.voice

/**
 * Segment types for KB question audio.
 */
enum class KBSegmentType(val value: String) {
    QUESTION("question"),
    ANSWER("answer"),
    HINT("hint"),
    EXPLANATION("explanation"),
    ;

    companion object {
        fun fromValue(value: String): KBSegmentType? = entries.find { it.value == value }
    }
}
