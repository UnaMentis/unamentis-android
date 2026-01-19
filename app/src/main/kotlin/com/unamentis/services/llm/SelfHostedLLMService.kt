package com.unamentis.services.llm

import android.util.Log
import com.unamentis.core.config.ServerConfig
import com.unamentis.core.config.ServerConfigManager
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.LLMToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader

/**
 * Self-hosted LLM service supporting Ollama and OpenAI-compatible servers.
 *
 * Supports multiple backends:
 * - Ollama (default port 11434)
 * - llama.cpp server
 * - vLLM
 * - text-generation-webui
 * - Any OpenAI-compatible API
 *
 * Features:
 * - Streaming responses (low time-to-first-token)
 * - Multiple model support via Ollama
 * - Zero cost (self-hosted)
 * - Ollama model management (list, pull)
 *
 * @property baseUrl Base URL for the LLM server
 * @property modelName Model name to use (e.g., "qwen2.5:7b", "llama3.2:3b")
 * @property authToken Optional authentication token
 */
class SelfHostedLLMService(
    private val baseUrl: String,
    private val modelName: String,
    private val authToken: String? = null,
    private val client: OkHttpClient,
) : LLMService {
    companion object {
        private const val TAG = "SelfHostedLLM"

        /**
         * Create an Ollama service instance.
         */
        fun ollama(
            host: String = "localhost",
            port: Int = 11434,
            model: String = "qwen2.5:7b",
            client: OkHttpClient,
        ): SelfHostedLLMService {
            return SelfHostedLLMService(
                baseUrl = "http://$host:$port",
                modelName = model,
                client = client,
            )
        }

        /**
         * Create a service from a ServerConfig.
         */
        fun fromServerConfig(
            serverConfig: ServerConfig,
            model: String = "qwen2.5:7b",
            client: OkHttpClient,
        ): SelfHostedLLMService {
            require(serverConfig.type.isLLM()) {
                "Server config must be an LLM-capable server type"
            }
            return SelfHostedLLMService(
                baseUrl = serverConfig.baseUrl,
                modelName = model,
                authToken = serverConfig.authToken,
                client = client,
            )
        }

        /**
         * Create a service using auto-discovery from ServerConfigManager.
         */
        fun autoDiscover(
            serverConfigManager: ServerConfigManager,
            model: String = "qwen2.5:7b",
            client: OkHttpClient,
        ): SelfHostedLLMService? {
            val llmServer =
                serverConfigManager.getLLMServers()
                    .filter { it.isUsable() }
                    .minByOrNull { it.healthStatus.ordinal }
                    ?: return null

            return fromServerConfig(llmServer, model, client)
        }
    }

    override val providerName: String
        get() = "SelfHosted ($modelName)"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Stream completion for a conversation.
     *
     * Uses SSE (Server-Sent Events) to receive tokens incrementally,
     * allowing for low latency and responsive UI updates.
     *
     * @param messages Conversation history
     * @param temperature Sampling temperature (0.0 - 2.0)
     * @param maxTokens Maximum tokens to generate
     * @return Flow of generated tokens
     */
    override fun streamCompletion(
        messages: List<LLMMessage>,
        temperature: Float,
        maxTokens: Int,
    ): Flow<LLMToken> =
        flow {
            val requestBody =
                ChatCompletionRequest(
                    model = modelName,
                    messages = messages.map { ChatMessage(it.role, it.content) },
                    temperature = temperature.toDouble(),
                    maxTokens = maxTokens,
                    stream = true,
                )

            val jsonBody = json.encodeToString(requestBody)
            val request =
                Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        authToken?.let { token ->
                            addHeader("Authorization", "Bearer $token")
                        }
                    }
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

            val startTime = System.currentTimeMillis()
            var isFirstToken = true

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "LLM API error: ${response.code} - $error")
                    when (response.code) {
                        401 -> throw SecurityException("Authentication failed")
                        else -> throw Exception("LLM API error: ${response.code}")
                    }
                }

                val reader =
                    response.body?.byteStream()?.bufferedReader()
                        ?: throw Exception("No response body")

                reader.use {
                    processSSEStream(it, startTime) { token ->
                        emit(token)
                        if (token.content.isNotEmpty() && isFirstToken) {
                            val ttft = System.currentTimeMillis() - startTime
                            Log.i(TAG, "TTFT: ${ttft}ms for $modelName")
                            isFirstToken = false
                        }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    /**
     * Process Server-Sent Events stream.
     */
    private suspend fun processSSEStream(
        reader: BufferedReader,
        startTime: Long,
        emit: suspend (LLMToken) -> Unit,
    ) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (currentLine.startsWith("data: ")) {
                val data = currentLine.substring(6).trim()

                // Check for stream end
                if (data == "[DONE]") {
                    emit(LLMToken(content = "", isDone = true))
                    break
                }

                if (data.isEmpty()) continue

                try {
                    val chunk = json.decodeFromString<StreamChunk>(data)
                    val choice = chunk.choices.firstOrNull() ?: continue
                    val content = choice.delta?.content ?: ""

                    if (content.isNotEmpty()) {
                        emit(LLMToken(content = content, isDone = false))
                    }

                    // Check if this is the last chunk
                    val finishReason = choice.finishReason
                    if (finishReason != null) {
                        emit(LLMToken(content = "", isDone = true))
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse chunk: $data", e)
                }
            }
        }
    }

    /**
     * Stop generation.
     */
    override suspend fun stop() {
        // With SSE, we can't actively stop the request mid-stream from client side
        // The caller should stop consuming the Flow
    }

    // Ollama-specific methods

    /**
     * List available models on the Ollama server.
     *
     * @return List of model names, or empty if failed
     */
    suspend fun listModels(): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url("$baseUrl/api/tags")
                        .get()
                        .apply {
                            authToken?.let { token ->
                                addHeader("Authorization", "Bearer $token")
                            }
                        }
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Failed to list models: ${response.code}")
                        return@withContext emptyList()
                    }

                    val body = response.body?.string() ?: return@withContext emptyList()
                    val modelsResponse = json.decodeFromString<OllamaModelsResponse>(body)
                    modelsResponse.models.map { it.name }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list models", e)
                emptyList()
            }
        }

    /**
     * Pull (download) a model on the Ollama server.
     *
     * @param name Model name to pull (e.g., "qwen2.5:7b")
     * @return true if successful
     */
    suspend fun pullModel(name: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(OllamaPullRequest(name))
                val request =
                    Request.Builder()
                        .url("$baseUrl/api/pull")
                        .post(requestBody.toRequestBody("application/json".toMediaType()))
                        .apply {
                            authToken?.let { token ->
                                addHeader("Authorization", "Bearer $token")
                            }
                        }
                        .build()

                client.newCall(request).execute().use { response ->
                    Log.i(TAG, "Pull model $name: ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull model $name", e)
                false
            }
        }

    /**
     * Check if the server is healthy.
     *
     * Tries multiple endpoints:
     * 1. /health (OpenAI-compatible)
     * 2. /api/version (Ollama-specific)
     *
     * @return true if server is reachable and healthy
     */
    suspend fun isHealthy(): Boolean =
        withContext(Dispatchers.IO) {
            // Try /health first
            try {
                val request =
                    Request.Builder()
                        .url("$baseUrl/health")
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext true
                }
            } catch (e: Exception) {
                // Try fallback
            }

            // Try Ollama-specific endpoint
            try {
                val request =
                    Request.Builder()
                        .url("$baseUrl/api/version")
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }

    // Request/Response models

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7,
        @SerialName("max_tokens")
        val maxTokens: Int? = null,
        val stream: Boolean = true,
        val stop: List<String>? = null,
        @SerialName("top_p")
        val topP: Double? = null,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class StreamChunk(
        val choices: List<StreamChoice>,
    )

    @Serializable
    private data class StreamChoice(
        val delta: Delta? = null,
        @SerialName("finish_reason")
        val finishReason: String? = null,
    )

    @Serializable
    private data class Delta(
        val content: String? = null,
        val role: String? = null,
    )

    @Serializable
    private data class OllamaModelsResponse(
        val models: List<OllamaModel> = emptyList(),
    )

    @Serializable
    private data class OllamaModel(
        val name: String,
        val size: Long = 0L,
        val digest: String = "",
    )

    @Serializable
    private data class OllamaPullRequest(
        val name: String,
    )
}
