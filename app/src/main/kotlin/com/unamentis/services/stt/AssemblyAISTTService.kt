package com.unamentis.services.stt

import android.util.Base64
import android.util.Log
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
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

/**
 * AssemblyAI Speech-to-Text service implementation.
 *
 * Uses WebSocket streaming for real-time transcription with low latency.
 * AssemblyAI requires base64-encoded audio data sent as JSON payloads.
 *
 * Features:
 * - Real-time streaming transcription
 * - Interim and final results
 * - Word-level timestamps
 * - High accuracy transcription
 *
 * Performance:
 * - Median latency: ~150ms
 * - P99 latency: ~300ms
 * - Cost: $0.65/hour of audio
 *
 * @property apiKey AssemblyAI API key
 * @property sampleRate Audio sample rate (default: 16000)
 */
class AssemblyAISTTService(
    private val apiKey: String,
    private val sampleRate: Int = 16000,
    private val client: OkHttpClient,
) : STTService {
    companion object {
        private const val TAG = "AssemblyAISTT"
        private const val BASE_URL = "wss://api.assemblyai.com/v2/realtime/ws"
    }

    override val providerName: String = "AssemblyAI"

    private var webSocket: WebSocket? = null
    private var streamStartTime: Long = 0L
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Start streaming transcription.
     *
     * Opens a WebSocket connection to AssemblyAI and returns a Flow
     * that emits transcription results as audio is sent.
     *
     * Audio data should be sent via sendAudioData().
     */
    override fun startStreaming(): Flow<STTResult> =
        callbackFlow {
            val url = "$BASE_URL?sample_rate=$sampleRate"

            val request =
                Request.Builder()
                    .url(url)
                    .addHeader("Authorization", apiKey)
                    .build()

            streamStartTime = System.currentTimeMillis()

            webSocket =
                client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            Log.i(TAG, "WebSocket connection opened")
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            try {
                                val response = json.decodeFromString<AssemblyAIResponse>(text)

                                when (response.message_type) {
                                    "SessionBegins" -> {
                                        Log.i(TAG, "Session started: ${response.session_id}")
                                    }
                                    "PartialTranscript" -> {
                                        val latency =
                                            if (response.audio_start != null) {
                                                System.currentTimeMillis() - streamStartTime -
                                                    response.audio_start
                                            } else {
                                                System.currentTimeMillis() - streamStartTime
                                            }

                                        trySend(
                                            STTResult(
                                                text = response.text ?: "",
                                                isFinal = false,
                                                confidence = response.confidence?.toFloat() ?: 1.0f,
                                                latencyMs = latency,
                                            ),
                                        )
                                    }
                                    "FinalTranscript" -> {
                                        val latency =
                                            if (response.audio_start != null) {
                                                System.currentTimeMillis() - streamStartTime -
                                                    response.audio_start
                                            } else {
                                                System.currentTimeMillis() - streamStartTime
                                            }

                                        trySend(
                                            STTResult(
                                                text = response.text ?: "",
                                                isFinal = true,
                                                confidence = response.confidence?.toFloat() ?: 1.0f,
                                                latencyMs = latency,
                                            ),
                                        )
                                    }
                                    "SessionTerminated" -> {
                                        Log.i(TAG, "Session terminated")
                                        close()
                                    }
                                    "SessionInformation" -> {
                                        Log.d(TAG, "Session info: ${response.audio_duration_seconds}s")
                                    }
                                    else -> {
                                        Log.d(TAG, "Unknown message type: ${response.message_type}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse response: $text", e)
                            }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            if (t is javax.net.ssl.SSLPeerUnverifiedException) {
                                Log.e(TAG, "Certificate pinning failed for AssemblyAI", t)
                                close(
                                    SecurityException(
                                        "Network security validation failed. Please check your connection.",
                                    ),
                                )
                            } else {
                                Log.e(TAG, "WebSocket error", t)
                                close(t)
                            }
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
     * Send audio data to AssemblyAI for transcription.
     *
     * AssemblyAI requires audio to be sent as base64-encoded PCM data
     * in a JSON payload: {"audio_data": "base64_string"}
     *
     * @param audioData PCM audio data (16-bit signed integers)
     */
    fun sendAudioData(audioData: ByteArray) {
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val payload = json.encodeToString(AudioDataMessage(base64Audio))
        webSocket?.send(payload)
    }

    /**
     * Send Float32 audio samples to AssemblyAI.
     *
     * Converts Float32 samples to Int16 PCM format before encoding.
     *
     * @param samples Float32 audio samples (normalized to -1.0 to 1.0)
     */
    fun sendAudioSamples(samples: FloatArray) {
        val pcmData = convertFloat32ToInt16(samples)
        sendAudioData(pcmData)
    }

    /**
     * Convert Float32 samples to Int16 PCM bytes.
     */
    private fun convertFloat32ToInt16(samples: FloatArray): ByteArray {
        val pcmData = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            // Clamp to [-1.0, 1.0] and scale to Int16 range
            val clamped = samples[i].coerceIn(-1.0f, 1.0f)
            val int16Value = (clamped * Short.MAX_VALUE).toInt().toShort()

            // Little-endian byte order
            pcmData[i * 2] = (int16Value.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((int16Value.toInt() shr 8) and 0xFF).toByte()
        }
        return pcmData
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
        // Send termination message before closing
        try {
            val terminateMessage = json.encodeToString(TerminateSessionMessage())
            webSocket?.send(terminateMessage)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send termination message", e)
        }

        webSocket?.close(1000, "Client closing")
        webSocket = null
    }

    /**
     * Cancel streaming immediately without sending termination message.
     */
    fun cancelStreaming() {
        webSocket?.cancel()
        webSocket = null
    }

    // AssemblyAI message models

    @Serializable
    private data class AudioDataMessage(
        val audio_data: String,
    )

    @Serializable
    private data class TerminateSessionMessage(
        val terminate_session: Boolean = true,
    )

    @Serializable
    private data class AssemblyAIResponse(
        val message_type: String? = null,
        val session_id: String? = null,
        val text: String? = null,
        val confidence: Double? = null,
        val audio_start: Long? = null,
        val audio_end: Long? = null,
        val audio_duration_seconds: Double? = null,
        val words: List<Word>? = null,
    )

    @Serializable
    private data class Word(
        val text: String,
        val start: Long,
        val end: Long,
        val confidence: Double,
    )
}
