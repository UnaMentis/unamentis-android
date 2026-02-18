package com.unamentis.services.stt

import android.util.Log
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Self-hosted Speech-to-Text service implementation.
 *
 * Connects to a self-hosted Whisper or OpenAI-compatible STT server via WebSocket
 * for real-time streaming transcription. Supports multiple server formats.
 *
 * Compatible servers:
 * - whisper.cpp server (WebSocket streaming at /ws)
 * - faster-whisper server (WebSocket at /ws/transcribe)
 * - UnaMentis gateway STT endpoint
 * - Any OpenAI-compatible transcription API
 *
 * Streaming protocol:
 * - Connect via WebSocket with audio configuration query parameters
 * - Send audio chunks as binary data
 * - Receive JSON transcription results
 * - Close connection to finalize
 *
 * Performance:
 * - Cost: $0 (self-hosted, only compute costs)
 * - Latency depends on server hardware and model
 *
 * @property serverUrl Base URL of the self-hosted STT server (e.g., "http://localhost:11401")
 * @property language Language code for transcription (e.g., "en", "auto")
 * @property sampleRate Audio sample rate in Hz (default: 16000)
 * @property client OkHttp client for WebSocket connections
 */
class SelfHostedSTTService(
    private val serverUrl: String,
    private val language: String = "en",
    private val sampleRate: Int = 16000,
    private val client: OkHttpClient,
) : STTService {
    companion object {
        private const val TAG = "SelfHostedSTT"
    }

    override val providerName: String = "SelfHosted"

    private var webSocket: WebSocket? = null
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Start streaming transcription via WebSocket.
     *
     * Opens a WebSocket connection to the self-hosted STT server and returns a Flow
     * that emits transcription results as audio is sent.
     *
     * Audio data should be sent via [sendAudioData] after calling this method.
     *
     * @return Flow of STT results (both partial and final)
     */
    override fun startStreaming(): Flow<STTResult> =
        callbackFlow {
            val url = buildWebSocketUrl()

            val request =
                Request.Builder()
                    .url(url)
                    .build()

            val startTime = System.currentTimeMillis()

            webSocket =
                client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            Log.i(TAG, "WebSocket connection opened to $serverUrl")
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            val latency = System.currentTimeMillis() - startTime

                            val result = parseResponse(text, latency)
                            if (result != null) {
                                trySend(result)
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            Log.e(TAG, "WebSocket error: ${t.message}", t)
                            close(t)
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            Log.i(TAG, "WebSocket closed: $code - $reason")
                            close()
                        }
                    },
                )

            awaitClose {
                doStopStreaming()
            }
        }

    /**
     * Send audio data to the self-hosted server for transcription.
     *
     * @param audioData PCM audio data (16-bit, 16kHz, mono)
     */
    fun sendAudioData(audioData: ByteArray) {
        webSocket?.send(ByteString.of(*audioData))
    }

    /**
     * Stop streaming and close WebSocket connection.
     */
    override suspend fun stopStreaming() {
        doStopStreaming()
    }

    /**
     * Internal non-suspend stop implementation.
     */
    private fun doStopStreaming() {
        // Send empty data to signal end of stream (some servers expect this)
        webSocket?.send(ByteString.EMPTY)
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    /**
     * Build WebSocket URL with audio configuration query parameters.
     *
     * Converts the HTTP server URL to a WebSocket URL and appends
     * the appropriate streaming path and query parameters.
     *
     * @return Complete WebSocket URL string
     */
    private fun buildWebSocketUrl(): String {
        val baseUrl =
            serverUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .trimEnd('/')

        val wsPath =
            if (baseUrl.contains("/ws")) {
                baseUrl
            } else {
                "$baseUrl/ws"
            }

        return "$wsPath?language=$language&encoding=linear16&sample_rate=$sampleRate&channels=1"
    }

    /**
     * Parse a JSON transcription response from the server.
     *
     * Supports multiple server response formats:
     * - OpenAI-style: `{ "text": "...", "is_final": true }`
     * - whisper.cpp streaming: `{ "result": { "text": "..." } }`
     * - faster-whisper: `{ "transcript": "...", "partial": false }`
     *
     * @param jsonString Raw JSON string from the server
     * @param latencyMs Time since stream started in milliseconds
     * @return Parsed STT result, or null if the response could not be parsed
     */
    private fun parseResponse(
        jsonString: String,
        latencyMs: Long,
    ): STTResult? {
        try {
            // Try Format 1: OpenAI-style { "text": "...", "is_final": true }
            try {
                val openAiResponse = json.decodeFromString<OpenAIWhisperResponse>(jsonString)
                if (openAiResponse.text != null) {
                    return STTResult(
                        text = openAiResponse.text,
                        isFinal = openAiResponse.isFinal ?: openAiResponse.is_final ?: true,
                        confidence = openAiResponse.confidence?.toFloat() ?: 0.9f,
                        latencyMs = latencyMs,
                    )
                }
            } catch (_: Exception) {
                // Not this format, try next
            }

            // Try Format 2: whisper.cpp { "result": { "text": "..." } }
            try {
                val whisperCppResponse = json.decodeFromString<WhisperCppResponse>(jsonString)
                if (whisperCppResponse.result?.text != null) {
                    return STTResult(
                        text = whisperCppResponse.result.text,
                        isFinal = whisperCppResponse.is_final ?: true,
                        confidence = 0.9f,
                        latencyMs = latencyMs,
                    )
                }
            } catch (_: Exception) {
                // Not this format, try next
            }

            // Try Format 3: faster-whisper { "transcript": "...", "partial": false }
            try {
                val fasterWhisperResponse = json.decodeFromString<FasterWhisperResponse>(jsonString)
                if (fasterWhisperResponse.transcript != null) {
                    return STTResult(
                        text = fasterWhisperResponse.transcript,
                        isFinal = !(fasterWhisperResponse.partial ?: false),
                        confidence = fasterWhisperResponse.confidence?.toFloat() ?: 0.9f,
                        latencyMs = latencyMs,
                    )
                }
            } catch (_: Exception) {
                // Not this format either
            }

            Log.w(TAG, "Could not parse response in any known format: ${jsonString.take(200)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse streaming response", e)
        }
        return null
    }

    // Server response models supporting multiple formats

    /**
     * OpenAI-compatible Whisper response format.
     */
    @Serializable
    private data class OpenAIWhisperResponse(
        val text: String? = null,
        val is_final: Boolean? = null,
        val isFinal: Boolean? = null,
        val confidence: Double? = null,
    )

    /**
     * whisper.cpp streaming response format.
     */
    @Serializable
    private data class WhisperCppResponse(
        val result: WhisperCppResult? = null,
        val is_final: Boolean? = null,
    )

    /**
     * Inner result object for whisper.cpp response.
     */
    @Serializable
    private data class WhisperCppResult(
        val text: String? = null,
    )

    /**
     * faster-whisper response format.
     */
    @Serializable
    private data class FasterWhisperResponse(
        val transcript: String? = null,
        val partial: Boolean? = null,
        val confidence: Double? = null,
    )
}
