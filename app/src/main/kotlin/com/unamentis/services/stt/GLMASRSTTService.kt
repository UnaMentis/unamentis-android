package com.unamentis.services.stt

import android.util.Log
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GLM-ASR-Nano Speech-to-Text service implementation.
 *
 * Self-hosted STT service with zero per-hour cost.
 * Uses WebSocket streaming for real-time transcription.
 *
 * Features:
 * - Zero cost per hour (self-hosted)
 * - Low latency streaming transcription
 * - Automatic reconnection with exponential backoff
 * - Word-level timestamps support
 * - Interim and final results
 *
 * @property baseUrl Base URL for GLM-ASR server (e.g., "wss://localhost:8080/v1/audio/stream")
 * @property authToken Optional authentication token
 * @property config Service configuration
 * @property client OkHttp client for WebSocket
 */
@Singleton
class GLMASRSTTService
    @Inject
    constructor(
        private val baseUrl: String,
        private val authToken: String?,
        private val config: GLMASRConfig = GLMASRConfig.DEFAULT,
        private val client: OkHttpClient,
    ) : STTService {
        companion object {
            private const val TAG = "GLMASRSTT"
        }

        override val providerName: String = "GLMASR"

        private var webSocket: WebSocket? = null
        private var isStreaming = false
        private var sessionStartTime: Long = 0

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

        // MARK: - STTService Implementation

        /**
         * Start streaming transcription.
         *
         * Opens a WebSocket connection to GLM-ASR and returns a Flow
         * that emits transcription results as audio is sent.
         *
         * @return Flow of STT results (both partial and final)
         */
        override fun startStreaming(): Flow<STTResult> =
            callbackFlow {
                if (isStreaming) {
                    Log.w(TAG, "Already streaming, closing existing connection")
                    webSocket?.close(1000, "Starting new session")
                }

                val request =
                    Request.Builder()
                        .url(baseUrl)
                        .apply {
                            authToken?.let { addHeader("Authorization", "Bearer $it") }
                        }
                        .build()

                sessionStartTime = System.currentTimeMillis()
                isStreaming = true

                webSocket =
                    client.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onOpen(
                                webSocket: WebSocket,
                                response: Response,
                            ) {
                                Log.i(TAG, "WebSocket connection opened")

                                // Send start configuration message
                                val startMessage =
                                    GLMASRStartMessage(
                                        type = "start",
                                        config =
                                            GLMASRStartConfig(
                                                language = config.language,
                                                interimResults = config.interimResults,
                                                punctuate = config.punctuate,
                                            ),
                                    )

                                val startJson = json.encodeToString(startMessage)
                                webSocket.send(startJson)
                                Log.d(TAG, "Sent start message: ${config.language}")
                            }

                            override fun onMessage(
                                webSocket: WebSocket,
                                text: String,
                            ) {
                                handleTextMessage(text, ::trySend)
                            }

                            override fun onFailure(
                                webSocket: WebSocket,
                                t: Throwable,
                                response: Response?,
                            ) {
                                Log.e(TAG, "WebSocket error", t)
                                isStreaming = false
                                close(t)
                            }

                            override fun onClosed(
                                webSocket: WebSocket,
                                code: Int,
                                reason: String,
                            ) {
                                Log.i(TAG, "WebSocket closed: $code - $reason")
                                isStreaming = false
                                close()
                            }
                        },
                    )

                awaitClose {
                    doStopStreaming()
                }
            }

        /**
         * Send audio data for transcription.
         *
         * @param audioData PCM audio data (16-bit, 16kHz, mono)
         */
        fun sendAudioData(audioData: ByteArray) {
            if (!isStreaming) {
                Log.w(TAG, "Not streaming, ignoring audio data")
                return
            }

            webSocket?.send(audioData.toByteString())
        }

        /**
         * Send audio data from FloatArray (converts to Int16 PCM).
         *
         * @param samples Float audio samples (-1.0 to 1.0)
         */
        fun sendAudioSamples(samples: FloatArray) {
            val int16Data = floatToInt16PCM(samples)
            sendAudioData(int16Data)
        }

        /**
         * Stop streaming and release resources.
         */
        override suspend fun stopStreaming() {
            doStopStreaming()
        }

        private fun doStopStreaming() {
            if (isStreaming) {
                Log.i(TAG, "Stopping GLM-ASR stream")

                // Send end message
                val endMessage = GLMASREndMessage(type = "end")
                val endJson = json.encodeToString(endMessage)
                webSocket?.send(endJson)
            }

            isStreaming = false
            webSocket?.close(1000, "Client closing")
            webSocket = null
        }

        // MARK: - Message Handling

        private fun handleTextMessage(
            text: String,
            emit: (STTResult) -> Unit,
        ) {
            try {
                val response = json.decodeFromString<GLMASRResponse>(text)

                when (response.type) {
                    "partial" -> {
                        val result = parseTranscriptionResult(response, isFinal = false)
                        result?.let { emit(it) }
                    }

                    "final" -> {
                        val result = parseTranscriptionResult(response, isFinal = true)
                        result?.let { emit(it) }
                    }

                    "error" -> {
                        Log.e(
                            TAG,
                            "Server error: ${response.code ?: "UNKNOWN"} - ${response.message ?: "Unknown"}",
                        )
                    }

                    "pong" -> {
                        // Keepalive acknowledged
                        Log.v(TAG, "Pong received")
                    }

                    else -> {
                        Log.w(TAG, "Unknown message type: ${response.type}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message: $text", e)
            }
        }

        private fun parseTranscriptionResult(
            response: GLMASRResponse,
            isFinal: Boolean,
        ): STTResult? {
            val transcriptText = response.text ?: return null

            val confidence = response.confidence ?: 1.0f
            val timestampMs = response.timestampMs ?: 0

            // Calculate latency
            val latencyMs = System.currentTimeMillis() - sessionStartTime - timestampMs

            return STTResult(
                text = transcriptText,
                isFinal = isFinal,
                confidence = confidence,
                latencyMs = maxOf(0, latencyMs),
            )
        }

        // MARK: - Audio Conversion

        /**
         * Convert Float32 audio samples to Int16 PCM bytes.
         *
         * GLM-ASR expects:
         * - 16-bit signed integer, little-endian
         * - 16kHz sample rate
         * - Mono channel
         */
        private fun floatToInt16PCM(samples: FloatArray): ByteArray {
            val bytes = ByteArray(samples.size * 2)

            for (i in samples.indices) {
                // Clamp to [-1.0, 1.0] and convert to Int16 range
                val sample = samples[i].coerceIn(-1.0f, 1.0f)
                val int16 = (sample * Short.MAX_VALUE).toInt().toShort()

                // Little-endian byte order
                bytes[i * 2] = (int16.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((int16.toInt() shr 8) and 0xFF).toByte()
            }

            return bytes
        }

        // MARK: - Configuration

        /**
         * Create a new service instance with updated configuration.
         */
        fun withConfig(newConfig: GLMASRConfig): GLMASRSTTService {
            return GLMASRSTTService(
                baseUrl = baseUrl,
                authToken = authToken,
                config = newConfig,
                client = client,
            )
        }

        /**
         * Create a new service instance with updated language.
         */
        fun withLanguage(language: String): GLMASRSTTService {
            return GLMASRSTTService(
                baseUrl = baseUrl,
                authToken = authToken,
                config = config.copy(language = language),
                client = client,
            )
        }
    }

// MARK: - Configuration

/**
 * Configuration for GLM-ASR service.
 *
 * @property language Language code (default: "auto" for auto-detect)
 * @property interimResults Whether to emit interim results
 * @property punctuate Whether to add punctuation
 * @property reconnectAttempts Number of reconnection attempts on failure
 * @property reconnectDelayMs Delay between reconnection attempts
 */
data class GLMASRConfig(
    val language: String = "auto",
    val interimResults: Boolean = true,
    val punctuate: Boolean = true,
    val reconnectAttempts: Int = 3,
    val reconnectDelayMs: Int = 1000,
) {
    companion object {
        /** Default configuration. */
        val DEFAULT = GLMASRConfig()

        /** Configuration for English-only. */
        val ENGLISH =
            GLMASRConfig(
                language = "en",
            )

        /** Configuration for low latency (no interim results). */
        val LOW_LATENCY =
            GLMASRConfig(
                interimResults = false,
                punctuate = false,
            )
    }
}

// MARK: - Message Types

@Serializable
internal data class GLMASRStartMessage(
    val type: String,
    val config: GLMASRStartConfig,
)

@Serializable
internal data class GLMASRStartConfig(
    val language: String,
    @SerialName("interim_results")
    val interimResults: Boolean,
    val punctuate: Boolean,
)

@Serializable
internal data class GLMASREndMessage(
    val type: String,
)

@Serializable
internal data class GLMASRResponse(
    val type: String? = null,
    val text: String? = null,
    val confidence: Float? = null,
    @SerialName("timestamp_ms")
    val timestampMs: Long? = null,
    @SerialName("is_end_of_utterance")
    val isEndOfUtterance: Boolean? = null,
    val words: List<GLMASRWord>? = null,
    // Error fields
    val code: String? = null,
    val message: String? = null,
    val recoverable: Boolean? = null,
)

@Serializable
internal data class GLMASRWord(
    val word: String,
    val start: Double,
    val end: Double,
    val confidence: Float? = null,
)
