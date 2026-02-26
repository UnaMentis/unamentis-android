package com.unamentis.services.embeddings

import android.util.Log
import com.unamentis.core.telemetry.TelemetryEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI embedding service for generating vector embeddings.
 *
 * Generates text embeddings using the OpenAI Embeddings API for use in
 * semantic search, knowledge base answer validation, and similarity comparison.
 *
 * Features:
 * - Single and batch embedding requests
 * - Automatic text preprocessing (newline replacement)
 * - Cost tracking via [TelemetryEngine]
 * - Structured error handling for rate limits, auth failures, etc.
 *
 * @property apiKey OpenAI API key for authentication
 * @property model Embedding model to use (default: [EmbeddingModel.TEXT_EMBEDDING_3_SMALL])
 * @property client OkHttp client for HTTP requests
 * @property telemetryEngine Telemetry engine for cost tracking (optional)
 * @property baseUrl API base URL (default: OpenAI official endpoint)
 */
class OpenAIEmbeddingService(
    private val apiKey: String,
    private val model: EmbeddingModel = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
    private val client: OkHttpClient,
    private val telemetryEngine: TelemetryEngine? = null,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : EmbeddingProvider {
    override val providerName: String = "OpenAI"
    override val embeddingDimension: Int = model.dimension

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Generate an embedding for a single text input.
     *
     * Preprocesses the text by replacing newlines with spaces as recommended
     * by the OpenAI API documentation.
     *
     * @param text The text to generate an embedding for
     * @return The embedding result containing the vector and usage metadata
     * @throws EmbeddingError if the request fails
     */
    override suspend fun embed(text: String): EmbeddingResult =
        withContext(Dispatchers.IO) {
            val cleanedText = preprocessText(text)

            val requestBody =
                OpenAIEmbeddingRequest(
                    input = listOf(cleanedText),
                    model = model.modelId,
                )

            val response = executeRequest(requestBody)

            val embedding =
                response.data.firstOrNull()?.embedding
                    ?: throw EmbeddingError.ApiError(
                        statusCode = 0,
                        message = "No embedding returned in response",
                    )

            val tokensUsed = response.usage.totalTokens
            trackCost(tokensUsed)

            EmbeddingResult(
                embedding = embedding,
                tokensUsed = tokensUsed,
                model = model,
            )
        }

    /**
     * Generate embeddings for multiple text inputs in a single request.
     *
     * More efficient than calling [embed] repeatedly, as it sends all texts
     * in a single API call. Results are returned in the same order as inputs.
     *
     * @param texts The list of texts to generate embeddings for
     * @return The batch embedding result containing all vectors and usage metadata
     * @throws EmbeddingError if the request fails
     * @throws IllegalArgumentException if the texts list is empty
     */
    override suspend fun embedBatch(texts: List<String>): BatchEmbeddingResult =
        withContext(Dispatchers.IO) {
            require(texts.isNotEmpty()) { "Texts list must not be empty" }

            val cleanedTexts = texts.map { preprocessText(it) }

            val requestBody =
                OpenAIEmbeddingRequest(
                    input = cleanedTexts,
                    model = model.modelId,
                )

            val response = executeRequest(requestBody)

            // Sort by index to ensure correct ordering
            val sortedEmbeddings =
                response.data
                    .sortedBy { it.index }
                    .map { it.embedding }

            val tokensUsed = response.usage.totalTokens
            trackCost(tokensUsed)

            BatchEmbeddingResult(
                embeddings = sortedEmbeddings,
                totalTokensUsed = tokensUsed,
                model = model,
            )
        }

    /**
     * Execute an embedding request against the OpenAI API.
     *
     * @param requestBody The serialized request body
     * @return The parsed API response
     * @throws EmbeddingError on API failures
     */
    private fun executeRequest(requestBody: OpenAIEmbeddingRequest): OpenAIEmbeddingResponse {
        val jsonBody = json.encodeToString(requestBody)
        val request =
            Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        val response =
            try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Network error during embedding request", e)
                throw EmbeddingError.NetworkError(e.message ?: "Unknown network error")
            }

        response.use { httpResponse ->
            val responseBody = httpResponse.body?.string() ?: ""

            if (!httpResponse.isSuccessful) {
                handleErrorResponse(httpResponse.code, responseBody)
            }

            return try {
                json.decodeFromString<OpenAIEmbeddingResponse>(responseBody)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse embedding response: $responseBody", e)
                throw EmbeddingError.ApiError(
                    statusCode = httpResponse.code,
                    message = "Failed to parse response: ${e.message}",
                )
            }
        }
    }

    /**
     * Handle non-successful HTTP responses with structured errors.
     *
     * @param statusCode The HTTP status code
     * @param responseBody The raw response body
     * @throws EmbeddingError always
     */
    private fun handleErrorResponse(
        statusCode: Int,
        responseBody: String,
    ): Nothing {
        Log.e(TAG, "Embedding request failed with status $statusCode: $responseBody")

        throw mapStatusToError(statusCode, responseBody)
    }

    private fun mapStatusToError(
        statusCode: Int,
        responseBody: String,
    ): EmbeddingError =
        when (statusCode) {
            HTTP_UNAUTHORIZED -> EmbeddingError.InvalidApiKey
            HTTP_TOO_MANY_REQUESTS -> EmbeddingError.RateLimited(retryAfterSeconds = parseRetryAfter(responseBody))
            HTTP_BAD_REQUEST -> mapBadRequestError(statusCode, responseBody)
            else -> EmbeddingError.ApiError(statusCode = statusCode, message = responseBody)
        }

    private fun mapBadRequestError(
        statusCode: Int,
        responseBody: String,
    ): EmbeddingError =
        if (responseBody.contains("maximum context length", ignoreCase = true)) {
            EmbeddingError.InputTooLong(maxTokens = MAX_TOKENS)
        } else {
            EmbeddingError.ApiError(statusCode = statusCode, message = responseBody)
        }

    /**
     * Attempt to parse a retry-after value from the error response.
     *
     * @param responseBody The raw response body
     * @return The retry-after seconds, or null if not found
     */
    private fun parseRetryAfter(responseBody: String): Int? {
        return try {
            val errorResponse = json.decodeFromString<OpenAIErrorResponse>(responseBody)
            // OpenAI sometimes includes retry info in the error message
            val message = errorResponse.error.message
            RETRY_AFTER_REGEX.find(message)?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Preprocess text for embedding generation.
     *
     * Replaces newlines with spaces as recommended by the OpenAI documentation
     * for better embedding quality.
     *
     * @param text The raw input text
     * @return The preprocessed text
     */
    private fun preprocessText(text: String): String {
        return text.replace("\n", " ").trim()
    }

    /**
     * Track embedding cost via the telemetry engine.
     *
     * @param tokensUsed The number of tokens consumed
     */
    private fun trackCost(tokensUsed: Int) {
        val cost = tokensUsed * model.costPerToken
        telemetryEngine?.recordCost(
            provider = "OpenAI-Embeddings",
            costUsd = cost,
            metadata = mapOf("type" to "EMBEDDING", "model" to model.modelId),
        )
        Log.d(TAG, "Embedding cost: $cost USD ($tokensUsed tokens, model: ${model.modelId})")
    }

    // region API Models

    @Serializable
    private data class OpenAIEmbeddingRequest(
        val input: List<String>,
        val model: String,
    )

    @Serializable
    private data class OpenAIEmbeddingResponse(
        val data: List<EmbeddingData>,
        val model: String,
        val usage: Usage,
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Float>,
        val index: Int,
    )

    @Serializable
    private data class Usage(
        @SerialName("prompt_tokens")
        val promptTokens: Int,
        @SerialName("total_tokens")
        val totalTokens: Int,
    )

    @Serializable
    private data class OpenAIErrorResponse(
        val error: ErrorDetail,
    )

    @Serializable
    private data class ErrorDetail(
        val message: String,
        val type: String = "",
        val code: String? = null,
    )

    // endregion

    companion object {
        private const val TAG = "OpenAIEmbeddingService"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1/embeddings"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_BAD_REQUEST = 400
        private const val MAX_TOKENS = 8191

        private val RETRY_AFTER_REGEX = Regex("""try again in (\d+)""", RegexOption.IGNORE_CASE)
    }
}
