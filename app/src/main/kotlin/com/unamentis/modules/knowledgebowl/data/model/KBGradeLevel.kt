package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Target grade levels for Knowledge Bowl questions.
 */
@Serializable
enum class KBGradeLevel(
    val displayName: String,
    val gradeRange: IntRange,
) {
    /** Middle school level (grades 6-8) */
    @SerialName("middleSchool")
    MIDDLE_SCHOOL("Middle School (6-8)", 6..8),

    /** High school level (grades 9-12) */
    @SerialName("highSchool")
    HIGH_SCHOOL("High School (9-12)", 9..12),

    /** Advanced/college-prep level */
    @SerialName("advanced")
    ADVANCED("Advanced", 11..14),
    ;

    /**
     * Check if a specific grade is within this level's range.
     */
    fun containsGrade(grade: Int): Boolean = grade in gradeRange

    companion object {
        /**
         * Get grade level for a specific grade.
         */
        fun forGrade(grade: Int): KBGradeLevel {
            return when {
                grade <= 8 -> MIDDLE_SCHOOL
                grade <= 12 -> HIGH_SCHOOL
                else -> ADVANCED
            }
        }

        /**
         * Default grade level.
         */
        val DEFAULT = HIGH_SCHOOL
    }
}
