package com.unamentis.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles parsing and validation of deep links for the UnaMentis app.
 *
 * Security considerations:
 * - Validates all incoming parameters
 * - Sanitizes IDs to prevent injection attacks
 * - Logs unknown/malformed links for monitoring
 */
@Singleton
class DeepLinkHandler
    @Inject
    constructor() {
        companion object {
            private const val TAG = "DeepLinkHandler"

            // Regex for valid UUID format
            private val UUID_REGEX =
                Regex(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                )

            // Maximum length for string parameters to prevent abuse
            private const val MAX_PARAM_LENGTH = 256
        }

        /**
         * Parses a deep link URI into a [DeepLinkDestination].
         *
         * @param uri The URI to parse
         * @return The parsed destination, or [DeepLinkDestination.Unknown] if invalid
         */
        fun parseDeepLink(uri: Uri?): DeepLinkDestination {
            if (uri == null) {
                Log.d(TAG, "Received null URI")
                return DeepLinkDestination.Unknown
            }

            if (uri.scheme != DeepLinkRoutes.SCHEME) {
                Log.w(TAG, "Unknown scheme: ${uri.scheme}")
                return DeepLinkDestination.Unknown
            }

            val host = uri.host ?: return DeepLinkDestination.Unknown
            val pathSegments = uri.pathSegments

            Log.d(TAG, "Parsing deep link: host=$host, path=$pathSegments")

            return when (host) {
                DeepLinkRoutes.SESSION -> parseSessionLink(pathSegments, uri)
                DeepLinkRoutes.CURRICULUM -> parseCurriculumLink(pathSegments)
                DeepLinkRoutes.TODO -> DeepLinkDestination.Todo
                DeepLinkRoutes.HISTORY -> parseHistoryLink(pathSegments)
                DeepLinkRoutes.ANALYTICS -> DeepLinkDestination.Analytics
                DeepLinkRoutes.SETTINGS -> parseSettingsLink(uri)
                else -> {
                    Log.w(TAG, "Unknown host: $host")
                    DeepLinkDestination.Unknown
                }
            }
        }

        /**
         * Parses a deep link from an Intent.
         *
         * @param intent The intent that may contain a deep link
         * @return The parsed destination, or null if no valid deep link
         */
        fun parseFromIntent(intent: Intent?): DeepLinkDestination? {
            if (intent == null) return null

            // Check for VIEW action with data URI
            if (intent.action == Intent.ACTION_VIEW) {
                val uri = intent.data
                if (uri != null && uri.scheme == DeepLinkRoutes.SCHEME) {
                    return parseDeepLink(uri)
                }
            }

            return null
        }

        /**
         * Checks if an intent contains a valid deep link.
         */
        fun hasDeepLink(intent: Intent?): Boolean {
            if (intent == null) return false
            return intent.action == Intent.ACTION_VIEW &&
                intent.data?.scheme == DeepLinkRoutes.SCHEME
        }

        private fun parseSessionLink(
            pathSegments: List<String>,
            uri: Uri,
        ): DeepLinkDestination =
            when {
                pathSegments.isEmpty() -> DeepLinkDestination.Session
                pathSegments.firstOrNull() == "start" -> {
                    val curriculumId =
                        sanitizeId(uri.getQueryParameter(DeepLinkRoutes.PARAM_CURRICULUM_ID))
                    val topicId = sanitizeId(uri.getQueryParameter(DeepLinkRoutes.PARAM_TOPIC_ID))
                    DeepLinkDestination.SessionStart(curriculumId, topicId)
                }

                else -> DeepLinkDestination.Session
            }

        private fun parseCurriculumLink(pathSegments: List<String>): DeepLinkDestination =
            when {
                pathSegments.isEmpty() -> DeepLinkDestination.Curriculum
                pathSegments.size == 1 -> {
                    val id = sanitizeId(pathSegments[0])
                    if (id != null) {
                        DeepLinkDestination.CurriculumDetail(id)
                    } else {
                        Log.w(TAG, "Invalid curriculum ID: ${pathSegments[0]}")
                        DeepLinkDestination.Curriculum
                    }
                }

                else -> DeepLinkDestination.Curriculum
            }

        private fun parseHistoryLink(pathSegments: List<String>): DeepLinkDestination =
            when {
                pathSegments.isEmpty() -> DeepLinkDestination.History
                pathSegments.size == 1 -> {
                    val id = sanitizeId(pathSegments[0])
                    if (id != null) {
                        DeepLinkDestination.HistoryDetail(id)
                    } else {
                        Log.w(TAG, "Invalid history ID: ${pathSegments[0]}")
                        DeepLinkDestination.History
                    }
                }

                else -> DeepLinkDestination.History
            }

        private fun parseSettingsLink(uri: Uri): DeepLinkDestination {
            val section = sanitizeString(uri.getQueryParameter(DeepLinkRoutes.PARAM_SECTION))
            return DeepLinkDestination.Settings(section)
        }

        /**
         * Sanitizes an ID parameter to ensure it's a valid UUID.
         * Returns null if the ID is invalid or potentially malicious.
         */
        private fun sanitizeId(id: String?): String? {
            if (id.isNullOrBlank()) return null
            if (id.length > MAX_PARAM_LENGTH) {
                Log.w(TAG, "ID exceeds maximum length")
                return null
            }

            // Check if it's a valid UUID format
            return if (UUID_REGEX.matches(id)) {
                try {
                    // Parse and re-format to ensure consistency
                    UUID.fromString(id).toString()
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Invalid UUID format: $id")
                    null
                }
            } else {
                // Allow alphanumeric IDs for flexibility
                if (id.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    id
                } else {
                    Log.w(TAG, "Invalid ID format: $id")
                    null
                }
            }
        }

        /**
         * Sanitizes a string parameter to prevent injection attacks.
         */
        private fun sanitizeString(value: String?): String? {
            if (value.isNullOrBlank()) return null
            if (value.length > MAX_PARAM_LENGTH) {
                Log.w(TAG, "String parameter exceeds maximum length")
                return null
            }

            // Allow only alphanumeric and underscore
            return if (value.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                value
            } else {
                Log.w(TAG, "Invalid string parameter: $value")
                null
            }
        }
    }
