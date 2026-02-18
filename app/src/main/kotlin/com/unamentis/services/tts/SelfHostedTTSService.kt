package com.unamentis.services.tts

import android.util.Log
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Self-hosted Text-to-Speech service implementation.
 *
 * Connects to a self-hosted TTS server using the OpenAI-compatible
 * `/v1/audio/speech` HTTP endpoint. Returns audio data as a single chunk.
 *
 * Compatible servers:
 * - Piper TTS server (22050 Hz)
 * - VibeVoice / Microsoft VibeVoice-Realtime-0.5B (24000 Hz)
 * - OpenedAI Speech
 * - Coqui TTS server
 * - UnaMentis gateway TTS endpoint
 * - Any OpenAI-compatible TTS API
 *
 * Performance:
 * - Cost: $0 (self-hosted, only compute costs)
 * - Latency depends on server hardware and model
 *
 * @property serverUrl Base URL of the self-hosted TTS server (e.g., "http://localhost:11402")
 * @property voice Voice identifier for the server (default: "nova")
 * @property responseFormat Audio response format (default: "wav")
 * @property client OkHttp client for HTTP requests
 */
class SelfHostedTTSService(
    private val serverUrl: String,
    private val voice: String = "nova",
    private val responseFormat: String = "wav",
    private val client: OkHttpClient,
) : TTSService {
    companion object {
        private const val TAG = "SelfHostedTTS"
        private const val MIN_AUDIO_BYTES = 44
    }

    override val providerName: String = "SelfHosted"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var activeCall: Call? = null

    /**
     * Synthesize text to audio stream.
     *
     * Sends a POST request to the self-hosted TTS server's `/v1/audio/speech`
     * endpoint using the OpenAI-compatible request format. Returns a Flow
     * that emits a single audio chunk containing the complete synthesis result.
     *
     * @param text Text to synthesize
     * @return Flow of audio chunks (single chunk containing complete audio)
     */
    override fun synthesize(text: String): Flow<TTSAudioChunk> =
        callbackFlow {
            val speechUrl = "${serverUrl.trimEnd('/')}/v1/audio/speech"

            val requestBody =
                SpeechRequest(
                    model = "tts-1",
                    input = text,
                    voice = voice,
                    responseFormat = responseFormat,
                )

            val requestJson = json.encodeToString(requestBody)
            val startTime = System.currentTimeMillis()

            Log.i(TAG, "Synthesizing ${text.length} chars via $speechUrl")

            val request =
                Request.Builder()
                    .url(speechUrl)
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

            activeCall = client.newCall(request)

            activeCall?.enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val ttfb = System.currentTimeMillis() - startTime
                        Log.d(TAG, "TTFB: ${ttfb}ms, HTTP status: ${response.code}")

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            Log.e(TAG, "TTS request failed: ${response.code} - ${errorBody?.take(200)}")
                            close(IOException("TTS request failed: HTTP ${response.code}"))
                            return
                        }

                        val audioData = response.body?.bytes()
                        if (audioData == null || audioData.size < MIN_AUDIO_BYTES) {
                            Log.e(TAG, "Audio data too small: ${audioData?.size ?: 0} bytes")
                            close(IOException("Audio response too small"))
                            return
                        }

                        val totalTime = System.currentTimeMillis() - startTime
                        Log.i(TAG, "Synthesis complete: ${text.length} chars -> ${audioData.size} bytes in ${totalTime}ms")

                        trySend(
                            TTSAudioChunk(
                                audioData = audioData,
                                isFirst = true,
                                isLast = false,
                            ),
                        )

                        trySend(
                            TTSAudioChunk(
                                audioData = byteArrayOf(),
                                isFirst = false,
                                isLast = true,
                            ),
                        )

                        close()
                    }

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (call.isCanceled()) {
                            Log.i(TAG, "TTS request cancelled")
                            close()
                        } else {
                            Log.e(TAG, "TTS request failed", e)
                            close(e)
                        }
                    }
                },
            )

            awaitClose {
                doStop()
            }
        }

    /**
     * Stop synthesis and cancel any in-flight request.
     */
    override suspend fun stop() {
        doStop()
    }

    /**
     * Internal non-suspend stop implementation.
     */
    private fun doStop() {
        activeCall?.cancel()
        activeCall = null
    }

    // OpenAI-compatible TTS request model

    /**
     * Request body for the OpenAI-compatible `/v1/audio/speech` endpoint.
     */
    @Serializable
    private data class SpeechRequest(
        val model: String,
        val input: String,
        val voice: String,
        @kotlinx.serialization.SerialName("response_format")
        val responseFormat: String,
    )
}
