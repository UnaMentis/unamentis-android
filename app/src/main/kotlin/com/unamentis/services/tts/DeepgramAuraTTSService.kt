package com.unamentis.services.tts

import android.util.Log
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
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
import okio.ByteString

/**
 * Deepgram Aura Text-to-Speech service implementation.
 *
 * Uses WebSocket streaming for real-time synthesis with ultra-low latency.
 * Deepgram Aura-2 provides high-quality, natural-sounding voices at 24kHz.
 *
 * Features:
 * - Real-time WebSocket streaming synthesis
 * - Ultra-low TTFB (Time-To-First-Byte) ~150ms
 * - 24kHz PCM audio output
 * - Multiple voice options (Aura voices)
 * - Word-level timing information
 *
 * Performance targets:
 * - TTFB: <200ms median, <400ms P99
 * - Audio quality: 24kHz, 16-bit PCM
 *
 * @property apiKey Deepgram API key
 * @property voice Voice model to use (default: "aura-asteria-en" - female US English)
 * @property encoding Audio encoding format (default: "linear16" for PCM)
 * @property sampleRate Sample rate in Hz (default: 24000 for best quality)
 * @property containerFormat Container format (default: "none" for raw PCM)
 */
class DeepgramAuraTTSService(
    private val apiKey: String,
    private val voice: String = "aura-asteria-en",
    private val encoding: String = "linear16",
    private val sampleRate: Int = 24000,
    private val containerFormat: String = "none",
    private val client: OkHttpClient,
) : TTSService {
    companion object {
        private const val TAG = "DeepgramAuraTTS"
        private const val BASE_URL = "wss://api.deepgram.com/v1/speak"

        // Available Aura voices
        val VOICES =
            mapOf(
                "aura-asteria-en" to "Asteria (Female, US English)",
                "aura-luna-en" to "Luna (Female, US English)",
                "aura-stella-en" to "Stella (Female, US English)",
                "aura-athena-en" to "Athena (Female, UK English)",
                "aura-hera-en" to "Hera (Female, US English)",
                "aura-orion-en" to "Orion (Male, US English)",
                "aura-arcas-en" to "Arcas (Male, US English)",
                "aura-perseus-en" to "Perseus (Male, US English)",
                "aura-angus-en" to "Angus (Male, Irish English)",
                "aura-orpheus-en" to "Orpheus (Male, US English)",
                "aura-helios-en" to "Helios (Male, UK English)",
                "aura-zeus-en" to "Zeus (Male, US English)",
            )

        /**
         * Create service with specific voice.
         */
        fun withVoice(
            apiKey: String,
            voiceName: String,
            client: OkHttpClient,
        ): DeepgramAuraTTSService {
            val voice =
                VOICES.keys.find { it.contains(voiceName, ignoreCase = true) }
                    ?: "aura-asteria-en"
            return DeepgramAuraTTSService(apiKey = apiKey, voice = voice, client = client)
        }
    }

    override val providerName: String = "DeepgramAura"

    private var webSocket: WebSocket? = null
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Synthesize text to audio stream.
     *
     * Opens a WebSocket connection to Deepgram and returns a Flow
     * that emits audio chunks as they're synthesized.
     *
     * Protocol:
     * 1. Connect to WebSocket with query parameters
     * 2. Send text message with JSON payload
     * 3. Receive binary audio chunks
     * 4. Send flush message to signal end
     * 5. Receive completion confirmation
     *
     * @param text Text to synthesize
     * @return Flow of audio chunks (PCM 16-bit, 24kHz, mono)
     */
    override fun synthesize(text: String): Flow<TTSAudioChunk> =
        callbackFlow {
            val url = buildWebSocketUrl()

            val request =
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Token $apiKey")
                    .build()

            var isFirstChunk = true
            val startTime = System.currentTimeMillis()
            var totalBytesReceived = 0L

            webSocket =
                client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            Log.i(TAG, "WebSocket connection opened for voice: $voice")

                            // Send text message with speak request
                            val speakRequest =
                                SpeakRequest(
                                    type = "Speak",
                                    text = text,
                                )
                            val requestJson = json.encodeToString(speakRequest)
                            webSocket.send(requestJson)
                            Log.d(TAG, "Sent text request: ${text.take(50)}...")

                            // Send flush to signal end of input
                            val flushRequest = FlushRequest(type = "Flush")
                            webSocket.send(json.encodeToString(flushRequest))
                            Log.d(TAG, "Sent flush request")
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            bytes: ByteString,
                        ) {
                            val audioData = bytes.toByteArray()
                            totalBytesReceived += audioData.size

                            if (isFirstChunk) {
                                val ttfb = System.currentTimeMillis() - startTime
                                Log.i(TAG, "TTFB: ${ttfb}ms, first chunk size: ${audioData.size} bytes")
                                isFirstChunk = false

                                trySend(
                                    TTSAudioChunk(
                                        audioData = audioData,
                                        isFirst = true,
                                        isLast = false,
                                    ),
                                )
                            } else {
                                trySend(
                                    TTSAudioChunk(
                                        audioData = audioData,
                                        isFirst = false,
                                        isLast = false,
                                    ),
                                )
                            }
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            // Handle JSON control messages
                            try {
                                val response = json.decodeFromString<DeepgramTTSResponse>(text)

                                when (response.type) {
                                    "Flushed" -> {
                                        Log.i(TAG, "Synthesis complete. Total bytes: $totalBytesReceived")
                                        trySend(
                                            TTSAudioChunk(
                                                audioData = byteArrayOf(),
                                                isFirst = false,
                                                isLast = true,
                                            ),
                                        )
                                        close()
                                    }
                                    "Warning" -> {
                                        Log.w(TAG, "Server warning: ${response.warnMsg}")
                                    }
                                    "Error" -> {
                                        Log.e(TAG, "Server error: ${response.errMsg}")
                                        close(Exception("Deepgram error: ${response.errMsg}"))
                                    }
                                    "Metadata" -> {
                                        Log.d(TAG, "Metadata received: request_id=${response.requestId}")
                                    }
                                    else -> {
                                        Log.d(TAG, "Unknown message type: ${response.type}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse message: $text", e)
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            val errorMsg = response?.body?.string() ?: t.message
                            Log.e(TAG, "WebSocket error: $errorMsg", t)

                            when (response?.code) {
                                401 -> close(SecurityException("Deepgram authentication failed"))
                                429 -> close(Exception("Rate limited by Deepgram"))
                                else -> close(t)
                            }
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            val duration = System.currentTimeMillis() - startTime
                            Log.i(TAG, "WebSocket closed: $code - $reason (duration: ${duration}ms)")
                            close()
                        }
                    },
                )

            awaitClose {
                doStop()
            }
        }

    /**
     * Build the WebSocket URL with query parameters.
     */
    private fun buildWebSocketUrl(): String {
        return "$BASE_URL?model=$voice&encoding=$encoding&sample_rate=$sampleRate&container=$containerFormat"
    }

    /**
     * Stop synthesis and close WebSocket connection.
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

    // Deepgram TTS request/response models

    @Serializable
    private data class SpeakRequest(
        val type: String,
        val text: String,
    )

    @Serializable
    private data class FlushRequest(
        val type: String,
    )

    @Serializable
    private data class DeepgramTTSResponse(
        val type: String? = null,
        @SerialName("request_id")
        val requestId: String? = null,
        @SerialName("warn_msg")
        val warnMsg: String? = null,
        @SerialName("err_msg")
        val errMsg: String? = null,
        @SerialName("err_code")
        val errCode: String? = null,
    )
}
