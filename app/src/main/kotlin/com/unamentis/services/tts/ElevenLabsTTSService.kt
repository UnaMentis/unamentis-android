package com.unamentis.services.tts

import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * ElevenLabs Text-to-Speech service implementation.
 *
 * Uses WebSocket streaming for real-time synthesis with low latency.
 *
 * Features:
 * - Real-time streaming synthesis
 * - High-quality voices
 * - Low time-to-first-byte (TTFB)
 * - Multiple voice options
 *
 * @property apiKey ElevenLabs API key
 * @property voiceId Voice identifier (default: Rachel voice)
 * @property model Model to use (default: "eleven_monolingual_v1")
 * @property stability Voice stability (0.0 - 1.0, default: 0.5)
 * @property similarityBoost Voice similarity boost (0.0 - 1.0, default: 0.75)
 */
class ElevenLabsTTSService(
    private val apiKey: String,
    private val voiceId: String = "21m00Tcm4TlvDq8ikWAM", // Rachel voice
    private val model: String = "eleven_monolingual_v1",
    private val stability: Float = 0.5f,
    private val similarityBoost: Float = 0.75f,
    private val client: OkHttpClient
) : TTSService {

    override val providerName: String = "ElevenLabs"

    private var webSocket: WebSocket? = null
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Synthesize text to audio stream.
     *
     * Opens a WebSocket connection to ElevenLabs and returns a Flow
     * that emits audio chunks as they're synthesized.
     *
     * @param text Text to synthesize
     * @return Flow of audio chunks (PCM 16-bit, 16kHz, mono)
     */
    override fun synthesize(text: String): Flow<TTSAudioChunk> = callbackFlow {
        val url = "wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input?model_id=$model"

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .build()

        var isFirstChunk = true
        val startTime = System.currentTimeMillis()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                android.util.Log.i("ElevenLabsTTS", "WebSocket connection opened")

                // Send configuration and text
                val config = ElevenLabsConfig(
                    text = text,
                    voiceSettings = VoiceSettings(
                        stability = stability,
                        similarityBoost = similarityBoost
                    )
                )

                val configJson = json.encodeToString(config)
                webSocket.send(configJson)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val audioData = bytes.toByteArray()

                if (isFirstChunk) {
                    val ttfb = System.currentTimeMillis() - startTime
                    android.util.Log.i("ElevenLabsTTS", "TTFB: ${ttfb}ms")
                    isFirstChunk = false

                    trySend(
                        TTSAudioChunk(
                            audioData = audioData,
                            isFirst = true,
                            isLast = false
                        )
                    )
                } else {
                    trySend(
                        TTSAudioChunk(
                            audioData = audioData,
                            isFirst = false,
                            isLast = false
                        )
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Handle JSON messages (errors, metadata, etc.)
                try {
                    val response = json.decodeFromString<ElevenLabsResponse>(text)

                    if (response.isFinal == true) {
                        // Send final empty chunk to signal completion
                        trySend(
                            TTSAudioChunk(
                                audioData = byteArrayOf(),
                                isFirst = false,
                                isLast = true
                            )
                        )
                        close()
                    }

                    if (response.error != null) {
                        android.util.Log.e("ElevenLabsTTS", "Server error: ${response.error}")
                        close(Exception(response.error))
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ElevenLabsTTS", "Non-JSON message: $text")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("ElevenLabsTTS", "WebSocket error", t)
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                android.util.Log.i("ElevenLabsTTS", "WebSocket closed: $code - $reason")
                close()
            }
        })

        awaitClose {
            doStop()
        }
    }

    /**
     * Stop synthesis and close WebSocket connection (suspend version).
     */
    override suspend fun stop() {
        doStop()
    }

    /**
     * Internal non-suspend stop implementation.
     */
    private fun doStop() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    // ElevenLabs API request/response models
    @Serializable
    private data class ElevenLabsConfig(
        val text: String,
        val voiceSettings: VoiceSettings? = null
    )

    @Serializable
    private data class VoiceSettings(
        val stability: Float,
        val similarityBoost: Float
    )

    @Serializable
    private data class ElevenLabsResponse(
        val isFinal: Boolean? = null,
        val error: String? = null
    )
}
