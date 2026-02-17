package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Complete rule configuration for a Knowledge Bowl region.
 *
 * @property region The region these rules apply to
 * @property teamsPerMatch Number of teams in each match
 * @property minTeamSize Minimum players per team
 * @property maxTeamSize Maximum players per team
 * @property activePlayersInOral Number of active players during oral rounds
 * @property writtenQuestionCount Number of questions in written round
 * @property writtenTimeLimitSeconds Time limit for written round in seconds
 * @property writtenPointsPerCorrect Points per correct answer in written round
 * @property oralQuestionCount Number of questions in oral round
 * @property oralPointsPerCorrect Points per correct answer in oral round
 * @property reboundEnabled Whether rebound questions are allowed
 * @property conferenceTimeSeconds Time allowed for team conference
 * @property verbalConferringAllowed Whether verbal discussion is allowed
 * @property handSignalsAllowed Whether hand signals are allowed
 * @property negativeScoring Whether wrong answers lose points
 * @property sosBonus "Speed of Sound" bonus for quick answers
 */
@Serializable
data class KBRegionalConfig(
    val region: KBRegion,
    @SerialName("teams_per_match")
    val teamsPerMatch: Int,
    @SerialName("min_team_size")
    val minTeamSize: Int,
    @SerialName("max_team_size")
    val maxTeamSize: Int,
    @SerialName("active_players_in_oral")
    val activePlayersInOral: Int,
    @SerialName("written_question_count")
    val writtenQuestionCount: Int,
    @SerialName("written_time_limit_seconds")
    val writtenTimeLimitSeconds: Int,
    @SerialName("written_points_per_correct")
    val writtenPointsPerCorrect: Int,
    @SerialName("oral_question_count")
    val oralQuestionCount: Int,
    @SerialName("oral_points_per_correct")
    val oralPointsPerCorrect: Int,
    @SerialName("rebound_enabled")
    val reboundEnabled: Boolean,
    @SerialName("conference_time_seconds")
    val conferenceTimeSeconds: Int,
    @SerialName("verbal_conferring_allowed")
    val verbalConferringAllowed: Boolean,
    @SerialName("hand_signals_allowed")
    val handSignalsAllowed: Boolean,
    @SerialName("negative_scoring")
    val negativeScoring: Boolean,
    @SerialName("sos_bonus")
    val sosBonus: Boolean,
) {
    /**
     * Written time limit in minutes.
     */
    val writtenTimeLimitMinutes: Int
        get() = writtenTimeLimitSeconds / 60

    /**
     * Conference time in seconds (convenience property).
     */
    val conferenceTime: Int
        get() = conferenceTimeSeconds

    /**
     * Whether this region has SOS bonus.
     */
    val hasSOS: Boolean
        get() = sosBonus

    /**
     * Validation strictness for this region.
     *
     * Colorado/Colorado Springs use strict matching (exact or near-exact).
     * Minnesota/Washington allow enhanced algorithmic matching.
     */
    val validationStrictness: KBValidationStrictness
        get() =
            when (region) {
                KBRegion.COLORADO, KBRegion.COLORADO_SPRINGS -> KBValidationStrictness.STRICT
                KBRegion.MINNESOTA, KBRegion.WASHINGTON -> KBValidationStrictness.STANDARD
            }

    /**
     * Written time limit formatted for display.
     */
    val writtenTimeLimitDisplay: String
        get() = "${writtenTimeLimitSeconds / 60} min"

    /**
     * Conference time formatted for display.
     */
    val conferenceTimeDisplay: String
        get() = "$conferenceTimeSeconds sec"

    /**
     * Points per written question formatted for display.
     */
    val writtenPointsDisplay: String
        get() = "$writtenPointsPerCorrect pt${if (writtenPointsPerCorrect == 1) "" else "s"}"

    /**
     * Points per oral question formatted for display.
     */
    val oralPointsDisplay: String
        get() = "$oralPointsPerCorrect pts"

    /**
     * Conferring rule description.
     */
    val conferringRuleDescription: String
        get() =
            when {
                verbalConferringAllowed -> "Verbal discussion allowed"
                handSignalsAllowed -> "Hand signals only (no verbal)"
                else -> "No conferring"
            }

    /**
     * Key differences from another regional configuration.
     */
    fun keyDifferences(from: KBRegionalConfig): List<String> {
        val differences = mutableListOf<String>()

        if (writtenQuestionCount != from.writtenQuestionCount) {
            differences.add(
                "Written: $writtenQuestionCount vs ${from.writtenQuestionCount} questions",
            )
        }

        if (writtenTimeLimitSeconds != from.writtenTimeLimitSeconds) {
            differences.add(
                "Written time: $writtenTimeLimitDisplay vs ${from.writtenTimeLimitDisplay}",
            )
        }

        if (writtenPointsPerCorrect != from.writtenPointsPerCorrect) {
            differences.add(
                "Written points: $writtenPointsPerCorrect vs " +
                    "${from.writtenPointsPerCorrect} per question",
            )
        }

        if (verbalConferringAllowed != from.verbalConferringAllowed) {
            val selfVerbal = if (verbalConferringAllowed) "verbal allowed" else "no verbal"
            val otherVerbal = if (from.verbalConferringAllowed) "verbal allowed" else "no verbal"
            differences.add("Conferring: $selfVerbal vs $otherVerbal")
        }

        if (sosBonus != from.sosBonus) {
            val selfSOS = if (sosBonus) "has SOS bonus" else "no SOS"
            val otherSOS = if (from.sosBonus) "has SOS bonus" else "no SOS"
            differences.add("SOS: $selfSOS vs $otherSOS")
        }

        return differences
    }

    companion object {
        /**
         * Get the configuration for a specific region.
         */
        @Suppress("LongMethod")
        fun forRegion(region: KBRegion): KBRegionalConfig {
            return when (region) {
                KBRegion.COLORADO ->
                    KBRegionalConfig(
                        region = KBRegion.COLORADO,
                        teamsPerMatch = 3,
                        minTeamSize = 1,
                        maxTeamSize = 4,
                        activePlayersInOral = 4,
                        writtenQuestionCount = 60,
                        // 15 minutes
                        writtenTimeLimitSeconds = 900,
                        writtenPointsPerCorrect = 1,
                        oralQuestionCount = 50,
                        oralPointsPerCorrect = 5,
                        reboundEnabled = true,
                        conferenceTimeSeconds = 15,
                        // CRITICAL: Colorado prohibits verbal conferring
                        verbalConferringAllowed = false,
                        handSignalsAllowed = true,
                        negativeScoring = false,
                        sosBonus = false,
                    )

                KBRegion.COLORADO_SPRINGS ->
                    KBRegionalConfig(
                        region = KBRegion.COLORADO_SPRINGS,
                        teamsPerMatch = 3,
                        minTeamSize = 1,
                        maxTeamSize = 4,
                        activePlayersInOral = 4,
                        writtenQuestionCount = 60,
                        writtenTimeLimitSeconds = 900,
                        writtenPointsPerCorrect = 1,
                        oralQuestionCount = 50,
                        oralPointsPerCorrect = 5,
                        reboundEnabled = true,
                        conferenceTimeSeconds = 15,
                        verbalConferringAllowed = false,
                        // Stricter hand signal rules in Colorado Springs
                        handSignalsAllowed = true,
                        negativeScoring = false,
                        sosBonus = false,
                    )

                KBRegion.MINNESOTA ->
                    KBRegionalConfig(
                        region = KBRegion.MINNESOTA,
                        teamsPerMatch = 3,
                        minTeamSize = 3,
                        maxTeamSize = 6,
                        activePlayersInOral = 4,
                        writtenQuestionCount = 60,
                        writtenTimeLimitSeconds = 900,
                        // 2 points per question in Minnesota
                        writtenPointsPerCorrect = 2,
                        oralQuestionCount = 50,
                        oralPointsPerCorrect = 5,
                        reboundEnabled = true,
                        conferenceTimeSeconds = 15,
                        // Verbal discussion allowed in Minnesota
                        verbalConferringAllowed = true,
                        handSignalsAllowed = true,
                        negativeScoring = false,
                        // Minnesota has SOS bonus
                        sosBonus = true,
                    )

                KBRegion.WASHINGTON ->
                    KBRegionalConfig(
                        region = KBRegion.WASHINGTON,
                        teamsPerMatch = 3,
                        minTeamSize = 3,
                        maxTeamSize = 5,
                        activePlayersInOral = 4,
                        // Washington has only 50 questions
                        writtenQuestionCount = 50,
                        // 45 minutes - much longer than other regions
                        writtenTimeLimitSeconds = 2700,
                        writtenPointsPerCorrect = 2,
                        oralQuestionCount = 50,
                        oralPointsPerCorrect = 5,
                        reboundEnabled = true,
                        conferenceTimeSeconds = 15,
                        verbalConferringAllowed = true,
                        handSignalsAllowed = true,
                        negativeScoring = false,
                        sosBonus = false,
                    )
            }
        }

        /**
         * Default configuration (Colorado).
         */
        val DEFAULT = forRegion(KBRegion.DEFAULT)
    }
}
