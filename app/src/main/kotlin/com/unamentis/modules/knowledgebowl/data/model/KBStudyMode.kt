package com.unamentis.modules.knowledgebowl.data.model

import androidx.annotation.StringRes
import com.unamentis.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feature flags that may restrict certain study modes.
 *
 * Modules can declare which features they support, and study modes
 * that require unavailable features will be filtered from the UI.
 */
enum class KBRequiredFeature {
    /** Always available - no special feature required. */
    NONE,

    /** Requires the module to support team mode. */
    TEAM_MODE,

    /** Requires the module to support speed training. */
    SPEED_TRAINING,

    /** Requires the module to support competition simulation. */
    COMPETITION_SIM,
}

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
     * String resource ID for the human-readable display name.
     *
     * Use with stringResource() for localized display in Compose UI.
     */
    @get:StringRes
    val displayNameResId: Int
        get() =
            when (this) {
                DIAGNOSTIC -> R.string.kb_mode_diagnostic
                TARGETED -> R.string.kb_mode_targeted
                BREADTH -> R.string.kb_mode_breadth
                SPEED -> R.string.kb_mode_speed
                COMPETITION -> R.string.kb_mode_competition
                TEAM -> R.string.kb_mode_team
            }

    /**
     * String resource ID for the description.
     *
     * Use with stringResource() for localized display in Compose UI.
     */
    @get:StringRes
    val descriptionResId: Int
        get() =
            when (this) {
                DIAGNOSTIC -> R.string.kb_mode_diagnostic_desc
                TARGETED -> R.string.kb_mode_targeted_desc
                BREADTH -> R.string.kb_mode_breadth_desc
                SPEED -> R.string.kb_mode_speed_desc
                COMPETITION -> R.string.kb_mode_competition_desc
                TEAM -> R.string.kb_mode_team_desc
            }

    /**
     * Human-readable display name (non-localized, for logging/debugging).
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
     * Description of the mode (non-localized, for logging/debugging).
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

    /**
     * Feature flag required for this mode to be available.
     *
     * Modes with [KBRequiredFeature.NONE] are always available.
     * Other modes may be restricted based on module capabilities.
     */
    val requiredFeature: KBRequiredFeature
        get() =
            when (this) {
                DIAGNOSTIC, TARGETED, BREADTH -> KBRequiredFeature.NONE
                SPEED -> KBRequiredFeature.SPEED_TRAINING
                COMPETITION -> KBRequiredFeature.COMPETITION_SIM
                TEAM -> KBRequiredFeature.TEAM_MODE
            }

    /**
     * SF Symbol / Material icon name for this mode.
     */
    val iconName: String
        get() =
            when (this) {
                DIAGNOSTIC -> "chart_pie"
                TARGETED -> "scope"
                BREADTH -> "grid_3x3"
                SPEED -> "bolt"
                COMPETITION -> "trophy"
                TEAM -> "groups"
            }
}
