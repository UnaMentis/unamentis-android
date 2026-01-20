package com.unamentis.services.tts

import android.util.Log
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chatterbox Text-to-Speech service implementation.
 *
 * Features:
 * - Emotion-aware synthesis with exaggeration control
 * - Paralinguistic tags ([laugh], [sigh], etc.)
 * - Streaming synthesis with low TTFB (~472ms)
 * - 23 language support
 * - Classifier-free guidance for quality control
 *
 * Endpoints:
 * - Streaming: POST /tts (primary for low latency)
 * - Non-streaming: POST /v1/audio/speech (OpenAI-compatible)
 *
 * @property baseUrl Base URL for Chatterbox API
 * @property apiKey API key for authentication (optional for self-hosted)
 * @property config Default synthesis configuration
 * @property client OkHttp client for HTTP requests
 */
@Singleton
class ChatterboxTTSService
    @Inject
    constructor(
        private val baseUrl: String,
        private val apiKey: String?,
        private val config: ChatterboxConfig = ChatterboxConfig.TUTOR,
        private val client: OkHttpClient,
    ) : TTSService {
        companion object {
            private const val TAG = "ChatterboxTTS"
            private const val CONTENT_TYPE_JSON = "application/json"
            private const val STREAMING_ENDPOINT = "/tts"
            private const val SPEECH_ENDPOINT = "/v1/audio/speech"
            private const val BUFFER_SIZE = 4096
        }

        override val providerName: String = "Chatterbox"

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

        private var currentCall: Call? = null

        // MARK: - TTSService Implementation

        /**
         * Synthesize text to audio stream using the streaming endpoint.
         *
         * @param text Text to synthesize (may include paralinguistic tags)
         * @return Flow of audio chunks (PCM 16-bit, 24kHz, mono)
         */
        override fun synthesize(text: String): Flow<TTSAudioChunk> = synthesizeStreaming(text, config)

        /**
         * Synthesize with custom configuration.
         *
         * @param text Text to synthesize
         * @param customConfig Configuration to use for this synthesis
         * @return Flow of audio chunks
         */
        fun synthesize(
            text: String,
            customConfig: ChatterboxConfig,
        ): Flow<TTSAudioChunk> = synthesizeStreaming(text, customConfig)

        /**
         * Stop synthesis and cancel any pending request.
         */
        override suspend fun stop() {
            withContext(Dispatchers.IO) {
                currentCall?.cancel()
                currentCall = null
                Log.d(TAG, "Synthesis stopped")
            }
        }

        // MARK: - Streaming Implementation

        /**
         * Streaming synthesis using POST /tts endpoint.
         *
         * This endpoint provides lower latency (~472ms TTFB) and supports
         * emotion control via exaggeration parameter.
         */
        private fun synthesizeStreaming(
            text: String,
            synthesisConfig: ChatterboxConfig,
        ): Flow<TTSAudioChunk> =
            callbackFlow {
                val processedText = preprocessText(text, synthesisConfig)

                val requestBody =
                    ChatterboxStreamRequest(
                        text = processedText,
                        exaggeration = synthesisConfig.exaggeration,
                        cfgWeight = synthesisConfig.cfgWeight,
                        speed = synthesisConfig.speed,
                        language = synthesisConfig.language.code,
                        voiceId = synthesisConfig.voiceId,
                    )

                val requestJson = json.encodeToString(requestBody)
                Log.d(TAG, "Streaming request: ${synthesisConfig.language.code}, exag=${synthesisConfig.exaggeration}")

                val request =
                    Request.Builder()
                        .url("$baseUrl$STREAMING_ENDPOINT")
                        .post(requestJson.toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
                        .apply {
                            apiKey?.let { addHeader("Authorization", "Bearer $it") }
                        }
                        .build()

                var isFirstChunk = true
                val startTime = System.currentTimeMillis()

                val call = client.newCall(request)
                currentCall = call

                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            if (call.isCanceled()) {
                                Log.d(TAG, "Request cancelled")
                                close()
                            } else {
                                Log.e(TAG, "Streaming request failed", e)
                                close(e)
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            try {
                                if (!response.isSuccessful) {
                                    val errorBody = response.body?.string()
                                    Log.e(TAG, "Streaming error: ${response.code} - $errorBody")
                                    close(IOException("Chatterbox error: ${response.code}"))
                                    return
                                }

                                response.body?.let { body ->
                                    val inputStream = body.byteStream()
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var bytesRead: Int

                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        if (call.isCanceled()) {
                                            Log.d(TAG, "Streaming cancelled during read")
                                            break
                                        }

                                        val audioData = buffer.copyOf(bytesRead)

                                        if (isFirstChunk) {
                                            val ttfb = System.currentTimeMillis() - startTime
                                            Log.i(TAG, "TTFB: ${ttfb}ms")
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

                                    // Send final chunk
                                    trySend(
                                        TTSAudioChunk(
                                            audioData = byteArrayOf(),
                                            isFirst = false,
                                            isLast = true,
                                        ),
                                    )

                                    val totalTime = System.currentTimeMillis() - startTime
                                    Log.i(TAG, "Streaming complete: ${totalTime}ms")
                                }

                                close()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing response", e)
                                close(e)
                            }
                        }
                    },
                )

                awaitClose {
                    call.cancel()
                    currentCall = null
                }
            }.flowOn(Dispatchers.IO)

        // MARK: - Non-Streaming Implementation

        /**
         * Non-streaming synthesis using OpenAI-compatible endpoint.
         *
         * Use this for simpler integration or when streaming is not needed.
         * Returns complete audio after full synthesis.
         */
        fun synthesizeNonStreaming(text: String): Flow<TTSAudioChunk> =
            callbackFlow {
                val processedText = preprocessText(text, config)

                val requestBody =
                    ChatterboxSpeechRequest(
                        input = processedText,
                        speed = config.speed,
                    )

                val requestJson = json.encodeToString(requestBody)
                Log.d(TAG, "Non-streaming request")

                val request =
                    Request.Builder()
                        .url("$baseUrl$SPEECH_ENDPOINT")
                        .post(requestJson.toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
                        .apply {
                            apiKey?.let { addHeader("Authorization", "Bearer $it") }
                        }
                        .build()

                val startTime = System.currentTimeMillis()
                val call = client.newCall(request)
                currentCall = call

                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            if (call.isCanceled()) {
                                close()
                            } else {
                                Log.e(TAG, "Non-streaming request failed", e)
                                close(e)
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            try {
                                if (!response.isSuccessful) {
                                    val errorBody = response.body?.string()
                                    Log.e(TAG, "Non-streaming error: ${response.code} - $errorBody")
                                    close(IOException("Chatterbox error: ${response.code}"))
                                    return
                                }

                                response.body?.let { body ->
                                    val audioData = body.bytes()
                                    val totalTime = System.currentTimeMillis() - startTime
                                    Log.i(TAG, "Non-streaming complete: ${totalTime}ms, ${audioData.size} bytes")

                                    // Emit as single chunk
                                    trySend(
                                        TTSAudioChunk(
                                            audioData = audioData,
                                            isFirst = true,
                                            isLast = true,
                                        ),
                                    )
                                }

                                close()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing response", e)
                                close(e)
                            }
                        }
                    },
                )

                awaitClose {
                    call.cancel()
                    currentCall = null
                }
            }.flowOn(Dispatchers.IO)

        // MARK: - Text Preprocessing

        /**
         * Preprocess text before synthesis.
         *
         * Handles paralinguistic tags and text normalization.
         */
        private fun preprocessText(
            text: String,
            synthesisConfig: ChatterboxConfig,
        ): String {
            var processed = text.trim()

            // Remove paralinguistic tags if disabled
            if (!synthesisConfig.enableParalinguisticTags) {
                ChatterboxParalinguisticTags.ALL_TAGS.forEach { tag ->
                    processed = processed.replace(tag, "", ignoreCase = true)
                }
            }

            return processed
        }

        // MARK: - Configuration

        /**
         * Create a new service instance with updated configuration.
         */
        fun withConfig(newConfig: ChatterboxConfig): ChatterboxTTSService {
            return ChatterboxTTSService(
                baseUrl = baseUrl,
                apiKey = apiKey,
                config = newConfig,
                client = client,
            )
        }

        /**
         * Create a new service instance with updated language.
         */
        fun withLanguage(language: ChatterboxLanguage): ChatterboxTTSService {
            return ChatterboxTTSService(
                baseUrl = baseUrl,
                apiKey = apiKey,
                config = config.copy(language = language),
                client = client,
            )
        }

        /**
         * Create a new service instance with updated exaggeration.
         */
        fun withExaggeration(exaggeration: Float): ChatterboxTTSService {
            return ChatterboxTTSService(
                baseUrl = baseUrl,
                apiKey = apiKey,
                config = config.copy(exaggeration = exaggeration),
                client = client,
            )
        }
    }
