package com.unamentis.core.todo

import android.util.Log
import com.unamentis.core.config.ServerConfigManager
import com.unamentis.data.local.dao.CurriculumDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for suggesting curricula matching learning targets.
 *
 * When a user creates a learning target todo (e.g., "Learn calculus"),
 * this service attempts to find matching curricula via:
 * 1. The management console API (server-side search)
 * 2. Local database search (fallback)
 *
 * Matched curriculum IDs are stored on the todo item so the UI can
 * display relevant suggestions.
 *
 * @property todoManager Manager for updating todo items
 * @property curriculumDao DAO for local curriculum queries
 * @property serverConfigManager Config manager for server URLs
 * @property okHttpClient HTTP client for API requests
 * @property json JSON serializer
 */
@Singleton
class CurriculumSuggestionService
    @Inject
    constructor(
        private val curriculumDao: CurriculumDao,
        private val serverConfigManager: ServerConfigManager,
        private val okHttpClient: OkHttpClient,
        private val json: Json,
    ) {
        /**
         * Fetch curriculum suggestions for a learning target query.
         *
         * Attempts to fetch from the management console API first,
         * falling back to local database search on network errors
         * or when the endpoint is not available.
         *
         * @param query The learning target text to match
         * @return List of curriculum IDs that match the query
         */
        suspend fun fetchSuggestions(query: String): List<String> {
            Log.i(TAG, "Fetching curriculum suggestions for: $query")

            return try {
                fetchFromServer(query)
            } catch (e: Exception) {
                Log.w(TAG, "Server suggestions failed, falling back to local search", e)
                localSuggestions(query)
            }
        }

        /**
         * Fetch suggestions from the management console API.
         */
        private suspend fun fetchFromServer(query: String): List<String> =
            withContext(Dispatchers.IO) {
                val baseUrl = serverConfigManager.getManagementServerUrl()
                val url = "$baseUrl/api/curricula/suggest"

                val requestBody =
                    json.encodeToString(SuggestionRequest(query = query))
                        .toRequestBody("application/json".toMediaType())

                val request =
                    Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                val response = okHttpClient.newCall(request).execute()

                response.use { resp ->
                    when (resp.code) {
                        404 -> {
                            Log.i(TAG, "Suggestion endpoint not available, falling back to local search")
                            return@withContext localSuggestions(query)
                        }
                        200 -> {
                            val body = resp.body?.string() ?: throw CurriculumSuggestionError.InvalidResponse
                            val suggestionResponse = json.decodeFromString<SuggestionResponse>(body)
                            Log.i(TAG, "Received ${suggestionResponse.curriculumIds.size} suggestions from server")
                            return@withContext suggestionResponse.curriculumIds
                        }
                        else -> {
                            Log.e(TAG, "Suggestion request failed with status: ${resp.code}")
                            throw CurriculumSuggestionError.ServerError(resp.code)
                        }
                    }
                }
            }

        /**
         * Search local database for matching curricula.
         *
         * Performs a simple keyword search across curriculum names
         * by splitting the query into words and matching any word.
         *
         * @param query Search query text
         * @return List of matching curriculum IDs (up to 5)
         */
        internal suspend fun localSuggestions(query: String): List<String> {
            Log.i(TAG, "Performing local curriculum search for: $query")

            val words =
                query.lowercase().split(" ").filter { it.isNotBlank() }
            if (words.isEmpty()) return emptyList()

            // Use a simple approach: search each word in curriculum names
            val allCurricula = curriculumDao.getAllCurriculaList()
            val matchingIds =
                allCurricula
                    .filter { curriculum ->
                        val name = curriculum.title.lowercase()
                        val summary = curriculum.description.lowercase()
                        words.any { word -> name.contains(word) || summary.contains(word) }
                    }
                    .take(MAX_LOCAL_SUGGESTIONS)
                    .map { it.id }

            Log.i(TAG, "Found ${matchingIds.size} local curriculum matches")
            return matchingIds
        }

        /**
         * Update a todo item with curriculum suggestions.
         *
         * Fetches suggestions based on the todo's title and stores
         * matching curriculum IDs on the item. Only applies to
         * learning target items.
         *
         * @param todo The todo item to update with suggestions
         */
        suspend fun updateTodoWithSuggestions(todo: com.unamentis.data.model.Todo) {
            if (todo.itemType != com.unamentis.data.model.TodoItemType.LEARNING_TARGET) {
                return
            }

            try {
                val suggestions = fetchSuggestions(todo.title)
                if (suggestions.isNotEmpty()) {
                    val suggestedIdsJson = json.encodeToString(suggestions)
                    internalTodoUpdater?.invoke(todo.id, suggestedIdsJson)
                    Log.i(TAG, "Updated todo with ${suggestions.size} suggestions")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch suggestions for todo: ${todo.title}", e)
            }
        }

        /**
         * Internal callback for updating todo suggestions.
         * Set by TodoManager to avoid circular dependency.
         */
        internal var internalTodoUpdater: (suspend (String, String) -> Unit)? = null

        companion object {
            private const val TAG = "CurriculumSuggestionSvc"

            /** Maximum number of local suggestions to return. */
            private const val MAX_LOCAL_SUGGESTIONS = 5
        }
    }

// region Request/Response Models

@Serializable
internal data class SuggestionRequest(
    val query: String,
)

@Serializable
internal data class SuggestionResponse(
    val curriculumIds: List<String>,
)

// endregion

// region Errors

/**
 * Errors that can occur during curriculum suggestion operations.
 */
sealed class CurriculumSuggestionError(message: String) : Exception(message) {
    /** The suggestion URL was invalid. */
    data object InvalidURL : CurriculumSuggestionError("Invalid suggestion URL")

    /** The server response was invalid. */
    data object InvalidResponse : CurriculumSuggestionError("Invalid response from server")

    /** The server returned an error status code. */
    class ServerError(val code: Int) : CurriculumSuggestionError("Server error: $code")

    /** A network error occurred. */
    class NetworkError(cause: Throwable) : CurriculumSuggestionError("Network error: ${cause.message}")
}

// endregion
