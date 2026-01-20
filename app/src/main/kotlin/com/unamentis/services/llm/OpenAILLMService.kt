package com.unamentis.services.llm

import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.LLMToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader

/**
 * OpenAI LLM service implementation with streaming support.
 *
 * Supports GPT-4o, GPT-4o-mini, and other OpenAI models with
 * Server-Sent Events (SSE) streaming for low latency.
 *
 * Features:
 * - Streaming responses (low time-to-first-token)
 * - Multiple model support
 * - Conversation history management
 * - Function calling (optional)
 *
 * @property apiKey OpenAI API key
 * @property model Model to use (default: "gpt-4o-mini")
 * @property baseUrl API base URL (default: OpenAI official)
 */
class OpenAILLMService(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val client: OkHttpClient,
) : LLMService {
    override val providerName: String = "OpenAI"

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
                OpenAIRequest(
                    model = model,
                    messages = messages.map { OpenAIMessage(it.role, it.content) },
                    temperature = temperature.toDouble(),
                    maxTokens = maxTokens,
                    stream = true,
                )

            val jsonBody = json.encodeToString(requestBody)
            val request =
                Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

            val startTime = System.currentTimeMillis()
            var isFirstToken = true

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = response.body?.string() ?: "Unknown error"
                    throw Exception("OpenAI API error: ${response.code} - $error")
                }

                val reader =
                    response.body?.byteStream()?.bufferedReader()
                        ?: throw Exception("No response body")

                reader.use {
                    processSSEStream(it, isFirstToken, startTime) { token ->
                        emit(token)
                        if (token.content.isNotEmpty() && isFirstToken) {
                            val ttft = System.currentTimeMillis() - startTime
                            android.util.Log.i("OpenAI", "TTFT: ${ttft}ms")
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
        _isFirstToken: Boolean,
        _startTime: Long,
        emit: suspend (LLMToken) -> Unit,
    ) {
        var line: String? = null
        var streamComplete = false
        while (!streamComplete && reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (currentLine.startsWith("data: ")) {
                val data = currentLine.substring(6)

                // Check for stream end
                if (data == "[DONE]") {
                    emit(LLMToken(content = "", isDone = true))
                    streamComplete = true
                } else {
                    streamComplete = processSSEChunk(data, emit)
                }
            }
        }
    }

    /**
     * Process a single SSE chunk.
     *
     * @return true if stream is complete, false otherwise
     */
    private suspend fun processSSEChunk(
        data: String,
        emit: suspend (LLMToken) -> Unit,
    ): Boolean {
        return try {
            val chunk = json.decodeFromString<OpenAIStreamChunk>(data)
            val content = chunk.choices.firstOrNull()?.delta?.content ?: ""

            if (content.isNotEmpty()) {
                emit(LLMToken(content = content, isDone = false))
            }

            // Check if this is the last chunk
            val finishReason = chunk.choices.firstOrNull()?.finishReason
            if (finishReason != null) {
                emit(LLMToken(content = "", isDone = true))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.w("OpenAI", "Failed to parse chunk: $data", e)
            false
        }
    }

    /**
     * Stop generation.
     */
    override suspend fun stop() {
        // With SSE, we can't actively stop the request mid-stream from client side
        // The caller should stop consuming the Flow
    }

    // OpenAI API models
    @Serializable
    private data class OpenAIRequest(
        val model: String,
        val messages: List<OpenAIMessage>,
        val temperature: Double = 0.7,
        val maxTokens: Int? = null,
        val stream: Boolean = false,
    )

    @Serializable
    private data class OpenAIMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class OpenAIStreamChunk(
        val choices: List<Choice>,
    )

    @Serializable
    private data class Choice(
        val delta: Delta,
        val finishReason: String? = null,
    )

    @Serializable
    private data class Delta(
        val content: String? = null,
        val role: String? = null,
    )
}
