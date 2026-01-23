package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Feature flags for the Knowledge Bowl module.
 *
 * These flags control which study modes and features are available
 * based on module capabilities. Used to filter the UI when certain
 * features are not supported by the currently loaded module content.
 *
 * @property supportsTeamMode Whether team practice mode is available.
 * @property supportsSpeedTraining Whether speed drill mode is available.
 * @property supportsCompetitionSim Whether competition simulation is available.
 */
@Serializable
data class KBModuleFeatures(
    @SerialName("supports_team_mode")
    val supportsTeamMode: Boolean = true,
    @SerialName("supports_speed_training")
    val supportsSpeedTraining: Boolean = true,
    @SerialName("supports_competition_sim")
    val supportsCompetitionSim: Boolean = true,
) {
    /**
     * Check if a specific feature requirement is satisfied.
     *
     * @param feature The feature requirement to check.
     * @return true if the feature is available, false otherwise.
     */
    fun isAvailable(feature: KBRequiredFeature): Boolean =
        when (feature) {
            KBRequiredFeature.NONE -> true
            KBRequiredFeature.TEAM_MODE -> supportsTeamMode
            KBRequiredFeature.SPEED_TRAINING -> supportsSpeedTraining
            KBRequiredFeature.COMPETITION_SIM -> supportsCompetitionSim
        }

    /**
     * Get all study modes that are available with these features.
     *
     * @return List of available study modes.
     */
    fun availableStudyModes(): List<KBStudyMode> = KBStudyMode.entries.filter { isAvailable(it.requiredFeature) }

    /**
     * Get study modes that are restricted (unavailable) with these features.
     *
     * @return List of restricted study modes.
     */
    fun restrictedStudyModes(): List<KBStudyMode> = KBStudyMode.entries.filter { !isAvailable(it.requiredFeature) }

    companion object {
        /**
         * Default features with all capabilities enabled.
         * Used when module info is unavailable or for local-only mode.
         */
        val DEFAULT_ENABLED =
            KBModuleFeatures(
                supportsTeamMode = true,
                supportsSpeedTraining = true,
                supportsCompetitionSim = true,
            )

        /**
         * Minimal features with only core study modes enabled.
         * Used for restricted/demo mode.
         */
        val MINIMAL =
            KBModuleFeatures(
                supportsTeamMode = false,
                supportsSpeedTraining = false,
                supportsCompetitionSim = false,
            )
    }
}
