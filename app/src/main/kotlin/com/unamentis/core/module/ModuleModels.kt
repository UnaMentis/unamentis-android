package com.unamentis.core.module

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary information about an available module.
 *
 * Used in module browser lists and search results.
 */
@Serializable
data class ModuleSummary(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    @SerialName("icon_name")
    val iconName: String = "extension",
    @SerialName("theme_color_hex")
    val themeColorHex: String = "#6200EE",
    val enabled: Boolean = true,
    @SerialName("supports_team_mode")
    val supportsTeamMode: Boolean = false,
    @SerialName("supports_speed_training")
    val supportsSpeedTraining: Boolean = false,
    @SerialName("supports_competition_sim")
    val supportsCompetitionSim: Boolean = false,
    @SerialName("download_size_bytes")
    val downloadSizeBytes: Long? = null,
    @SerialName("is_installed")
    val isInstalled: Boolean = false,
)

/**
 * Detailed module information from server.
 *
 * Includes full content metadata and feature overrides.
 */
@Serializable
data class ModuleDetail(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("long_description")
    val longDescription: String = "",
    val version: String,
    @SerialName("icon_name")
    val iconName: String = "extension",
    @SerialName("theme_color_hex")
    val themeColorHex: String = "#6200EE",
    val enabled: Boolean = true,
    @SerialName("supports_team_mode")
    val supportsTeamMode: Boolean = false,
    @SerialName("supports_speed_training")
    val supportsSpeedTraining: Boolean = false,
    @SerialName("supports_competition_sim")
    val supportsCompetitionSim: Boolean = false,
    val domains: List<ModuleDomain> = emptyList(),
    @SerialName("study_modes")
    val studyModes: List<ModuleStudyMode> = emptyList(),
    @SerialName("total_questions")
    val totalQuestions: Int = 0,
    @SerialName("estimated_study_hours")
    val estimatedStudyHours: Float = 0f,
    val settings: ModuleSettings? = null,
)

/**
 * A content domain within a module.
 *
 * For Knowledge Bowl, domains are subject areas like Science, Math, etc.
 */
@Serializable
data class ModuleDomain(
    val id: String,
    val name: String,
    @SerialName("icon_name")
    val iconName: String = "category",
    val weight: Float = 1.0f,
    val subcategories: List<String> = emptyList(),
    @SerialName("question_count")
    val questionCount: Int = 0,
)

/**
 * A study/practice mode available in the module.
 */
@Serializable
data class ModuleStudyMode(
    val id: String,
    val name: String,
    val description: String = "",
    @SerialName("icon_name")
    val iconName: String = "play_arrow",
    @SerialName("question_count")
    val questionCount: Int? = null,
    @SerialName("time_limit_seconds")
    val timeLimitSeconds: Int? = null,
    @SerialName("allow_hints")
    val allowHints: Boolean = true,
    @SerialName("shuffle_questions")
    val shuffleQuestions: Boolean = true,
)

/**
 * Module content container for serialized module data.
 *
 * This is a flexible container that holds the actual content
 * of a module (questions, configuration, etc.) as JSON.
 */
@Serializable
data class ModuleContent(
    /** Raw JSON content - specific structure depends on module type */
    @SerialName("questions_json")
    val questionsJson: String? = null,
    /** Module-specific configuration */
    @SerialName("config_json")
    val configJson: String? = null,
    /** Domain/category metadata */
    val domains: List<ModuleDomain> = emptyList(),
    /** Available study modes */
    @SerialName("study_modes")
    val studyModes: List<ModuleStudyMode> = emptyList(),
    /** Total question count */
    @SerialName("total_questions")
    val totalQuestions: Int = 0,
)

/**
 * Module-specific settings.
 */
@Serializable
data class ModuleSettings(
    @SerialName("default_time_per_question")
    val defaultTimePerQuestion: Float = 30f,
    @SerialName("conference_time_seconds")
    val conferenceTimeSeconds: Float? = null,
    @SerialName("enable_spoken_questions")
    val enableSpokenQuestions: Boolean = true,
    @SerialName("enable_spoken_answers")
    val enableSpokenAnswers: Boolean = true,
    @SerialName("minimum_mastery_for_completion")
    val minimumMasteryForCompletion: Float = 0.8f,
)

/**
 * A fully downloaded module ready for offline use.
 *
 * Contains all content needed to run the module without network.
 */
@Serializable
data class DownloadedModule(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    @SerialName("long_description")
    val longDescription: String = "",
    @SerialName("icon_name")
    val iconName: String = "extension",
    @SerialName("theme_color_hex")
    val themeColorHex: String = "#6200EE",
    @SerialName("supports_team_mode")
    val supportsTeamMode: Boolean = false,
    @SerialName("supports_speed_training")
    val supportsSpeedTraining: Boolean = false,
    @SerialName("supports_competition_sim")
    val supportsCompetitionSim: Boolean = false,
    val settings: ModuleSettings? = null,
    @SerialName("downloaded_at")
    val downloadedAt: Long = System.currentTimeMillis(),
    /** Module content (questions, config, etc.) */
    val content: ModuleContent = ModuleContent(),
) {
    /**
     * Total question count from content.
     */
    val totalQuestions: Int
        get() = content.totalQuestions

    /**
     * Domains from content.
     */
    val domains: List<ModuleDomain>
        get() = content.domains

    /**
     * Study modes from content.
     */
    val studyModes: List<ModuleStudyMode>
        get() = content.studyModes
}

/**
 * Response from GET /api/modules endpoint.
 */
@Serializable
data class ModuleListResponse(
    val modules: List<ModuleSummary>,
    val total: Int = 0,
)

/**
 * Request to download a module.
 */
@Serializable
data class ModuleDownloadRequest(
    @SerialName("module_id")
    val moduleId: String,
    @SerialName("include_audio")
    val includeAudio: Boolean = false,
)

/**
 * Module download progress.
 */
data class ModuleDownloadProgress(
    val moduleId: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

    enum class DownloadStatus {
        PENDING,
        DOWNLOADING,
        EXTRACTING,
        COMPLETED,
        FAILED,
    }
}

/**
 * Error from module service operations.
 */
sealed class ModuleServiceError : Exception() {
    data object NotConfigured : ModuleServiceError()

    data class NetworkError(override val message: String) : ModuleServiceError()

    data object InvalidResponse : ModuleServiceError()

    data class ServerError(val code: Int) : ModuleServiceError()

    data class ModuleNotFound(val moduleId: String) : ModuleServiceError()

    data class DownloadFailed(override val message: String) : ModuleServiceError()

    data class DecodingError(override val message: String) : ModuleServiceError()
}
