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
 * Anthropic Claude LLM service implementation with streaming support.
 *
 * Supports Claude 3.5 Sonnet, Claude 3.5 Haiku, and other Anthropic models
 * with Server-Sent Events (SSE) streaming for low latency.
 *
 * Features:
 * - Streaming responses (low time-to-first-token)
 * - Multiple model support
 * - Conversation history management
 * - System prompts
 *
 * @property apiKey Anthropic API key
 * @property model Model to use (default: "claude-3-5-haiku-20241022")
 * @property anthropicVersion API version (default: "2023-06-01")
 */
class AnthropicLLMService(
    private val apiKey: String,
    private val model: String = "claude-3-5-haiku-20241022",
    private val anthropicVersion: String = "2023-06-01",
    private val client: OkHttpClient,
) : LLMService {
    override val providerName: String = "Anthropic"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Stream completion for a conversation.
     *
     * Uses SSE (Server-Sent Events) to receive tokens incrementally.
     *
     * Note: Anthropic API requires separating system messages from user/assistant messages.
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
            // Separate system messages
            val systemMessage = messages.firstOrNull { it.role == "system" }?.content
            val conversationMessages = messages.filter { it.role != "system" }

            val requestBody =
                AnthropicRequest(
                    model = model,
                    messages = conversationMessages.map { AnthropicMessage(it.role, it.content) },
                    maxTokens = maxTokens,
                    temperature = temperature.toDouble(),
                    system = systemMessage,
                    stream = true,
                )

            val jsonBody = json.encodeToString(requestBody)
            val request =
                Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", anthropicVersion)
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

            val startTime = System.currentTimeMillis()
            var isFirstToken = true

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = response.body?.string() ?: "Unknown error"
                    throw Exception("Anthropic API error: ${response.code} - $error")
                }

                val reader =
                    response.body?.byteStream()?.bufferedReader()
                        ?: throw Exception("No response body")

                reader.use {
                    processSSEStream(it, isFirstToken, startTime) { token ->
                        emit(token)
                        if (token.content.isNotEmpty() && isFirstToken) {
                            val ttft = System.currentTimeMillis() - startTime
                            android.util.Log.i("Anthropic", "TTFT: ${ttft}ms")
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
        isFirstToken: Boolean,
        startTime: Long,
        emit: suspend (LLMToken) -> Unit,
    ) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (currentLine.startsWith("data: ")) {
                val data = currentLine.substring(6)

                try {
                    val event = json.decodeFromString<AnthropicStreamEvent>(data)

                    when (event.type) {
                        "content_block_delta" -> {
                            val text = event.delta?.text ?: ""
                            if (text.isNotEmpty()) {
                                emit(LLMToken(content = text, isDone = false))
                            }
                        }
                        "message_stop" -> {
                            emit(LLMToken(content = "", isDone = true))
                            return
                        }
                        "error" -> {
                            val errorMsg = event.error?.message ?: "Unknown error"
                            throw Exception("Anthropic error: $errorMsg")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("Anthropic", "Failed to parse chunk: $data", e)
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

    // Anthropic API models
    @Serializable
    private data class AnthropicRequest(
        val model: String,
        val messages: List<AnthropicMessage>,
        val maxTokens: Int,
        val temperature: Double = 0.7,
        val system: String? = null,
        val stream: Boolean = false,
    )

    @Serializable
    private data class AnthropicMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class AnthropicStreamEvent(
        val type: String,
        val delta: ContentDelta? = null,
        val error: ErrorInfo? = null,
    )

    @Serializable
    private data class ContentDelta(
        val type: String? = null,
        val text: String? = null,
    )

    @Serializable
    private data class ErrorInfo(
        val type: String,
        val message: String,
    )
}
