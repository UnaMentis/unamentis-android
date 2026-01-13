package com.unamentis.services.stt

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
 * Deepgram Nova-3 Speech-to-Text service implementation.
 *
 * Uses WebSocket streaming for real-time transcription with low latency.
 *
 * Features:
 * - Real-time streaming transcription
 * - Interim and final results
 * - Smart formatting
 * - Punctuation and capitalization
 *
 * @property apiKey Deepgram API key
 * @property model Model to use (default: "nova-2")
 * @property language Language code (default: "en-US")
 * @property smartFormat Enable smart formatting (default: true)
 */
class DeepgramSTTService(
    private val apiKey: String,
    private val model: String = "nova-2",
    private val language: String = "en-US",
    private val smartFormat: Boolean = true,
    private val client: OkHttpClient
) : STTService {

    override val providerName: String = "Deepgram"

    private var webSocket: WebSocket? = null
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Start streaming transcription.
     *
     * Opens a WebSocket connection to Deepgram and returns a Flow
     * that emits transcription results as audio is sent.
     *
     * Audio data should be sent via the WebSocket using sendAudioData().
     */
    override fun startStreaming(): Flow<STTResult> = callbackFlow {
        val url = buildWebSocketUrl()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $apiKey")
            .build()

        val startTime = System.currentTimeMillis()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.i("DeepgramSTT", "WebSocket connection opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val response = json.decodeFromString<DeepgramResponse>(text)
                    val latency = System.currentTimeMillis() - startTime

                    response.channel?.alternatives?.firstOrNull()?.let { alternative ->
                        val isFinal = response.is_final ?: false
                        val confidence = alternative.confidence?.toFloat() ?: 1.0f

                        trySend(
                            STTResult(
                                text = alternative.transcript,
                                isFinal = isFinal,
                                confidence = confidence,
                                latencyMs = latency
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DeepgramSTT", "Failed to parse response", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Check if failure is due to certificate pinning
                if (t is javax.net.ssl.SSLPeerUnverifiedException) {
                    android.util.Log.e(
                        "DeepgramSTT",
                        "Certificate pinning failed for Deepgram. This may indicate a MITM attack or certificate rotation.",
                        t
                    )
                    close(
                        SecurityException(
                            "Network security validation failed. Please check your connection."
                        )
                    )
                } else {
                    android.util.Log.e("DeepgramSTT", "WebSocket error", t)
                    close(t)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.i("DeepgramSTT", "WebSocket closed: $code - $reason")
                close()
            }
        })

        awaitClose {
            doStopStreaming()
        }
    }

    /**
     * Send audio data to Deepgram for transcription.
     *
     * @param audioData PCM audio data (16-bit, 16kHz, mono)
     */
    fun sendAudioData(audioData: ByteArray) {
        webSocket?.send(ByteString.of(*audioData))
    }

    /**
     * Stop streaming and close WebSocket connection (suspend version).
     */
    override suspend fun stopStreaming() {
        doStopStreaming()
    }

    /**
     * Internal non-suspend stop implementation.
     */
    private fun doStopStreaming() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    private fun buildWebSocketUrl(): String {
        val params = mutableListOf(
            "model=$model",
            "language=$language",
            "encoding=linear16",
            "sample_rate=16000",
            "channels=1"
        )

        if (smartFormat) {
            params.add("smart_format=true")
        }

        params.add("interim_results=true")

        return "wss://api.deepgram.com/v1/listen?${params.joinToString("&")}"
    }

    // Deepgram API response models
    @Serializable
    private data class DeepgramResponse(
        val channel: Channel? = null,
        val is_final: Boolean? = null
    )

    @Serializable
    private data class Channel(
        val alternatives: List<Alternative>? = null
    )

    @Serializable
    private data class Alternative(
        val transcript: String,
        val confidence: Double? = null
    )
}
