package com.unamentis.navigation

import android.net.Uri

/**
 * Deep link route definitions for the UnaMentis app.
 *
 * Supports the `unamentis://` URL scheme with the following routes:
 * - unamentis://session - Open Session tab
 * - unamentis://session/start?curriculum_id=X&topic_id=Y - Start session with context
 * - unamentis://curriculum - Open Curriculum tab
 * - unamentis://curriculum/{id} - Open specific curriculum
 * - unamentis://todo - Open To-Do tab
 * - unamentis://history - Open History tab
 * - unamentis://history/{id} - Open session detail
 * - unamentis://analytics - Open Analytics
 * - unamentis://settings?section=X - Open Settings (optionally to specific section)
 */
object DeepLinkRoutes {
    const val SCHEME = "unamentis"

    // Route paths (without scheme)
    const val SESSION = "session"
    const val SESSION_START = "session/start"
    const val CURRICULUM = "curriculum"
    const val TODO = "todo"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val READING_LIST = "reading_list"

    // Query parameter keys
    const val PARAM_CURRICULUM_ID = "curriculum_id"
    const val PARAM_TOPIC_ID = "topic_id"
    const val PARAM_SECTION = "section"

    // Navigation route patterns for Compose Navigation
    const val ROUTE_SESSION = "session"
    const val ROUTE_SESSION_START = "session/start?curriculum_id={curriculum_id}&topic_id={topic_id}"
    const val ROUTE_CURRICULUM = "curriculum"
    const val ROUTE_CURRICULUM_DETAIL = "curriculum/{id}"
    const val ROUTE_TODO = "todo"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_HISTORY_DETAIL = "history/{id}"
    const val ROUTE_ANALYTICS = "analytics"
    const val ROUTE_SETTINGS = "settings?section={section}"
    const val ROUTE_READING_LIST = "reading_list"

    // Deep link URI patterns
    const val URI_SESSION = "$SCHEME://session"
    const val URI_SESSION_START = "$SCHEME://session/start"
    const val URI_CURRICULUM = "$SCHEME://curriculum"
    const val URI_CURRICULUM_DETAIL = "$SCHEME://curriculum/{id}"
    const val URI_TODO = "$SCHEME://todo"
    const val URI_HISTORY = "$SCHEME://history"
    const val URI_HISTORY_DETAIL = "$SCHEME://history/{id}"
    const val URI_ANALYTICS = "$SCHEME://analytics"
    const val URI_SETTINGS = "$SCHEME://settings"
    const val URI_READING_LIST = "$SCHEME://reading_list"
}

/**
 * Represents a parsed deep link with its destination and parameters.
 */
sealed class DeepLinkDestination {
    data object Session : DeepLinkDestination()

    data class SessionStart(
        val curriculumId: String? = null,
        val topicId: String? = null,
    ) : DeepLinkDestination()

    data object Curriculum : DeepLinkDestination()

    data class CurriculumDetail(val id: String) : DeepLinkDestination()

    data object Todo : DeepLinkDestination()

    data object History : DeepLinkDestination()

    data class HistoryDetail(val id: String) : DeepLinkDestination()

    data object Analytics : DeepLinkDestination()

    data class Settings(val section: String? = null) : DeepLinkDestination()

    data object ReadingList : DeepLinkDestination()

    data object Unknown : DeepLinkDestination()

    /**
     * Converts this destination to a navigation route string.
     */
    fun toNavigationRoute(): String =
        when (this) {
            is Session -> DeepLinkRoutes.ROUTE_SESSION
            is SessionStart -> buildSessionStartRoute(curriculumId, topicId)
            is Curriculum -> DeepLinkRoutes.ROUTE_CURRICULUM
            is CurriculumDetail -> "curriculum/$id"
            is Todo -> DeepLinkRoutes.ROUTE_TODO
            is History -> DeepLinkRoutes.ROUTE_HISTORY
            is HistoryDetail -> "history/$id"
            is Analytics -> DeepLinkRoutes.ROUTE_ANALYTICS
            is Settings -> buildSettingsRoute(section)
            is ReadingList -> DeepLinkRoutes.ROUTE_READING_LIST
            is Unknown -> DeepLinkRoutes.ROUTE_SESSION
        }

    private fun buildSessionStartRoute(
        curriculumId: String?,
        topicId: String?,
    ): String {
        val params = mutableListOf<String>()
        curriculumId?.let { params.add("curriculum_id=$it") }
        topicId?.let { params.add("topic_id=$it") }
        return if (params.isEmpty()) {
            "session/start"
        } else {
            "session/start?${params.joinToString("&")}"
        }
    }

    private fun buildSettingsRoute(section: String?): String =
        if (section != null) {
            "settings?section=$section"
        } else {
            "settings"
        }
}

/**
 * Settings sections that can be deep linked to.
 */
enum class SettingsSection(val value: String) {
    PROVIDERS("providers"),
    VOICE("voice"),
    SELF_HOSTED("self_hosted"),
    DEBUG("debug"),
    HELP("help"),
    ABOUT("about"),
    ;

    companion object {
        fun fromValue(value: String): SettingsSection? = entries.find { it.value == value }
    }
}

/**
 * Utility functions for building deep link URIs.
 */
object DeepLinkBuilder {
    /**
     * Builds a deep link URI for starting a session.
     */
    fun buildSessionStartUri(
        curriculumId: String? = null,
        topicId: String? = null,
    ): Uri {
        val builder =
            Uri
                .Builder()
                .scheme(DeepLinkRoutes.SCHEME)
                .authority("session")
                .appendPath("start")

        curriculumId?.let { builder.appendQueryParameter(DeepLinkRoutes.PARAM_CURRICULUM_ID, it) }
        topicId?.let { builder.appendQueryParameter(DeepLinkRoutes.PARAM_TOPIC_ID, it) }

        return builder.build()
    }

    /**
     * Builds a deep link URI for opening a specific curriculum.
     */
    fun buildCurriculumUri(curriculumId: String): Uri =
        Uri
            .Builder()
            .scheme(DeepLinkRoutes.SCHEME)
            .authority("curriculum")
            .appendPath(curriculumId)
            .build()

    /**
     * Builds a deep link URI for opening a session history detail.
     */
    fun buildHistoryDetailUri(sessionId: String): Uri =
        Uri
            .Builder()
            .scheme(DeepLinkRoutes.SCHEME)
            .authority("history")
            .appendPath(sessionId)
            .build()

    /**
     * Builds a deep link URI for opening settings to a specific section.
     */
    fun buildSettingsUri(section: SettingsSection? = null): Uri {
        val builder =
            Uri
                .Builder()
                .scheme(DeepLinkRoutes.SCHEME)
                .authority("settings")

        section?.let { builder.appendQueryParameter(DeepLinkRoutes.PARAM_SECTION, it.value) }

        return builder.build()
    }
}
