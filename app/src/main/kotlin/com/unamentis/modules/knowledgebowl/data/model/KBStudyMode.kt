package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Study modes for Knowledge Bowl practice sessions.
 *
 * Each mode has different question counts and focuses.
 */
@Serializable
enum class KBStudyMode {
    /**
     * Diagnostic mode - assess overall knowledge with full domain coverage.
     * Uses 50 questions covering all domains.
     */
    @SerialName("diagnostic")
    DIAGNOSTIC,

    /**
     * Targeted practice - focus on weak areas.
     * Uses 25 questions from selected domains.
     */
    @SerialName("targeted")
    TARGETED,

    /**
     * Breadth practice - ensure coverage across all domains.
     * Uses 36 questions with balanced distribution.
     */
    @SerialName("breadth")
    BREADTH,

    /**
     * Speed drill - fast-paced timed practice.
     * Uses 20 questions with 5-minute time limit.
     */
    @SerialName("speed")
    SPEED,

    /**
     * Competition simulation - match competition format.
     * Uses 45 questions with competition rules.
     */
    @SerialName("competition")
    COMPETITION,

    /**
     * Team practice - collaborative team mode.
     * Uses 45 questions with conference time.
     */
    @SerialName("team")
    TEAM,
    ;

    /**
     * Human-readable display name.
     */
    val displayName: String
        get() =
            when (this) {
                DIAGNOSTIC -> "Diagnostic"
                TARGETED -> "Targeted Practice"
                BREADTH -> "Breadth Practice"
                SPEED -> "Speed Drill"
                COMPETITION -> "Competition Simulation"
                TEAM -> "Team Practice"
            }

    /**
     * Description of the mode.
     */
    val description: String
        get() =
            when (this) {
                DIAGNOSTIC -> "Assess your knowledge across all domains"
                TARGETED -> "Focus on specific domains or weak areas"
                BREADTH -> "Ensure balanced coverage of all topics"
                SPEED -> "Quick timed practice to improve speed"
                COMPETITION -> "Simulate a real competition round"
                TEAM -> "Practice with team conferring"
            }

    /**
     * Default question count for this mode.
     */
    val defaultQuestionCount: Int
        get() =
            when (this) {
                DIAGNOSTIC -> 50
                TARGETED -> 25
                BREADTH -> 36
                SPEED -> 20
                COMPETITION -> 45
                TEAM -> 45
            }

    /**
     * Time limit in seconds (null for no limit).
     */
    val timeLimitSeconds: Int?
        get() =
            when (this) {
                SPEED -> 300 // 5 minutes
                else -> null
            }
}
