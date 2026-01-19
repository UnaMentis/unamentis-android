package com.unamentis.core.featureflags

/**
 * Centralized feature flag keys used across the app.
 *
 * Flag names should match what's configured in Unleash.
 * Use semantic naming: {category}_{feature}_{variant}
 */
object FeatureFlagKeys {
    // MARK: - Operations

    /** Maintenance mode - blocks new sessions */
    const val MAINTENANCE_MODE = "ops_maintenance_mode"

    // MARK: - Features

    /** Specialized training modules (Knowledge Bowl, etc.) */
    const val SPECIALIZED_MODULES = "feature_specialized_modules"

    /** Team collaboration features within modules */
    const val TEAM_MODE = "feature_team_mode"

    /** Competition simulation features */
    const val COMPETITION_SIM = "feature_competition_sim"

    /** Knowledge Bowl module specifically */
    const val KNOWLEDGE_BOWL = "feature_knowledge_bowl"

    // MARK: - Experiments

    /** A/B test for new onboarding flow */
    const val NEW_ONBOARDING = "experiment_new_onboarding"

    // MARK: - Provider Features

    /** Enable Chatterbox TTS provider */
    const val CHATTERBOX_TTS = "feature_chatterbox_tts"

    /** Enable GLMASR STT provider */
    const val GLMASR_STT = "feature_glmasr_stt"

    // MARK: - FOV Context Features

    /** Enable FOV context management */
    const val FOV_CONTEXT = "feature_fov_context"

    /** Enable automatic context expansion */
    const val AUTO_CONTEXT_EXPANSION = "feature_auto_context_expansion"
}
