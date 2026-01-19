package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Types of Knowledge Bowl rounds.
 */
@Serializable
enum class KBRoundType(
    val displayName: String,
    val iconName: String,
) {
    /**
     * Written round - MCQ format, team works together, timed.
     */
    @SerialName("written")
    WRITTEN("Written Round", "edit"),

    /**
     * Oral round - Spoken questions, buzzer-based responses.
     */
    @SerialName("oral")
    ORAL("Oral Round", "mic"),
}
