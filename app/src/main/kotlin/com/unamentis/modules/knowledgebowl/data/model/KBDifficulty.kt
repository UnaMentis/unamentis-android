package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Difficulty levels aligned with Knowledge Bowl competition standards.
 *
 * Levels range from basic familiarity to expert-level research questions.
 */
@Serializable
enum class KBDifficulty(
    val displayName: String,
    val level: Int,
) {
    /** Basic familiarity with the topic */
    @SerialName("overview")
    OVERVIEW("Overview", 1),

    /** Core concepts and fundamental knowledge */
    @SerialName("foundational")
    FOUNDATIONAL("Foundational", 2),

    /** Deeper understanding required */
    @SerialName("intermediate")
    INTERMEDIATE("Intermediate", 3),

    /** Competition-ready difficulty */
    @SerialName("varsity")
    VARSITY("Varsity", 4),

    /** Top-tier difficulty for championships */
    @SerialName("championship")
    CHAMPIONSHIP("Championship", 5),

    /** Expert-level, research-quality questions */
    @SerialName("research")
    RESEARCH("Research", 6),
    ;

    /**
     * Check if this difficulty is harder than another.
     */
    fun isHarderThan(other: KBDifficulty): Boolean = level > other.level

    /**
     * Check if this difficulty is easier than another.
     */
    fun isEasierThan(other: KBDifficulty): Boolean = level < other.level

    /**
     * Compare difficulty levels by level value.
     */
    fun compareByLevel(other: KBDifficulty): Int = level.compareTo(other.level)

    companion object {
        /**
         * Get difficulty from level number.
         */
        fun fromLevel(level: Int): KBDifficulty? {
            return entries.find { it.level == level }
        }

        /**
         * Get the default difficulty (varsity).
         */
        val DEFAULT = VARSITY
    }
}
