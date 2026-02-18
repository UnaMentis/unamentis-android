package com.unamentis.modules.knowledgebowl.data.remote

import android.content.Context
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBPack
import com.unamentis.modules.knowledgebowl.data.model.KBPacksResponse
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Service for fetching Knowledge Bowl packs from the Management API.
 *
 * Provides reactive state for pack listing and methods for fetching
 * individual packs and their questions.
 *
 * API endpoints:
 * - GET /api/kb/packs — list all packs
 * - GET /api/kb/packs/{id} — get a specific pack
 * - GET /api/kb/packs/{packId}/questions — get questions for a pack
 *
 * Maps to iOS KBPackService.
 */
@Singleton
class KBPackService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KBPackService"
            private const val DEFAULT_PORT = 8766
            private const val TIMEOUT_SECONDS = 10L
            private const val PREFS_NAME = "unamentis_prefs"
            private const val KEY_SELF_HOSTED_ENABLED = "selfHostedEnabled"
            private const val KEY_PRIMARY_SERVER_IP = "primaryServerIP"
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private val httpClient =
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        // Observable state
        private val _packs = MutableStateFlow<List<KBPack>>(emptyList())
        val packs: StateFlow<List<KBPack>> = _packs.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow<KBPackServiceError?>(null)
        val error: StateFlow<KBPackServiceError?> = _error.asStateFlow()

        // MARK: - Pack Operations

        /**
         * Fetch all available packs from the server.
         *
         * Updates [packs], [isLoading], and [error] state flows.
         */
        suspend fun fetchPacks() {
            _isLoading.value = true
            _error.value = null

            try {
                val url = "${getBaseUrl()}/api/kb/packs"
                val body = executeGet(url)
                val response = json.decodeFromString<KBPacksResponse>(body)
                _packs.value = response.packs.map { it.toPack() }
                Log.i(TAG, "Fetched ${_packs.value.size} packs")
            } catch (e: CancellationException) {
                throw e
            } catch (e: KBPackServiceError) {
                _error.value = e
                Log.e(TAG, "Failed to fetch packs: ${e.message}")
            } catch (e: Exception) {
                _error.value = KBPackServiceError.NetworkError(e)
                Log.e(TAG, "Failed to fetch packs: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Fetch a specific pack by ID.
         *
         * @param id Pack identifier
         * @return The pack, or throws on error
         */
        suspend fun fetchPack(id: String): KBPack {
            val url = "${getBaseUrl()}/api/kb/packs/$id"
            val body = executeGet(url)
            return try {
                val dto = json.decodeFromString<com.unamentis.modules.knowledgebowl.data.model.KBPackDTO>(body)
                dto.toPack()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KBPackServiceError.DecodingError(e)
            }
        }

        /**
         * Fetch questions for a specific pack.
         *
         * @param packId Pack identifier
         * @return List of questions in the pack
         */
        suspend fun fetchPackQuestions(packId: String): List<KBQuestion> {
            val url = "${getBaseUrl()}/api/kb/packs/$packId/questions"
            val body = executeGet(url)
            return try {
                val response = json.decodeFromString<KBQuestionsResponse>(body)
                response.questions.map { it.toQuestion() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw KBPackServiceError.DecodingError(e)
            }
        }

        // MARK: - Internal

        private fun getBaseUrl(): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val selfHostedEnabled = prefs.getBoolean(KEY_SELF_HOSTED_ENABLED, false)
            val serverIP = prefs.getString(KEY_PRIMARY_SERVER_IP, "") ?: ""

            return if (selfHostedEnabled && serverIP.isNotEmpty()) {
                "http://$serverIP:$DEFAULT_PORT"
            } else {
                "http://localhost:$DEFAULT_PORT"
            }
        }

        private suspend fun executeGet(url: String): String =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "GET $url")

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw KBPackServiceError.ServerError(response.code)
                        }
                        response.body?.string()
                            ?: throw KBPackServiceError.InvalidResponse
                    }
                } catch (e: KBPackServiceError) {
                    throw e
                } catch (e: IOException) {
                    throw KBPackServiceError.NetworkError(e)
                }
            }
    }

// MARK: - Response Models

/**
 * Response from the pack questions endpoint.
 */
@Serializable
private data class KBQuestionsResponse(
    val questions: List<KBQuestionDTO>,
    val total: Int? = null,
)

/**
 * DTO for questions within a pack response.
 */
@Serializable
private data class KBQuestionDTO(
    val id: String,
    @SerialName("question_text")
    val questionText: String,
    @SerialName("answer_text")
    val answerText: String,
    @SerialName("domain_id")
    val domainId: String,
    val subcategory: String? = null,
    val difficulty: Int = 4,
    val source: String? = null,
    @SerialName("acceptable_answers")
    val acceptableAnswers: List<String>? = null,
) {
    /**
     * Convert DTO to domain model.
     */
    fun toQuestion(): KBQuestion =
        KBQuestion(
            id = id,
            text = questionText,
            answer =
                KBAnswer(
                    primary = answerText,
                    acceptable = acceptableAnswers,
                    answerType = KBAnswerType.TEXT,
                ),
            domain = KBDomain.fromId(domainId) ?: KBDomain.MISCELLANEOUS,
            subdomain = subcategory,
            difficulty = KBDifficulty.fromLevel(difficulty) ?: KBDifficulty.VARSITY,
            source = source,
        )
}

// MARK: - Errors

/**
 * Errors from the pack service.
 */
sealed class KBPackServiceError : Exception() {
    /** Server returned an empty or unparseable response. */
    data object InvalidResponse : KBPackServiceError() {
        override val message: String get() = "Invalid response from server"
    }

    /** Server returned a non-2xx status code. */
    data class ServerError(val statusCode: Int) : KBPackServiceError() {
        override val message: String get() = "Server error: $statusCode"
    }

    /** Network connectivity issue. */
    data class NetworkError(val underlying: Exception) : KBPackServiceError() {
        override val message: String get() = "Network error: ${underlying.message}"
    }

    /** Failed to decode the response body. */
    data class DecodingError(val underlying: Exception) : KBPackServiceError() {
        override val message: String get() = "Decoding error: ${underlying.message}"
    }
}
