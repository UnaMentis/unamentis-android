package com.unamentis.services.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Android native Text-to-Speech service implementation.
 *
 * Uses Android's built-in TextToSpeech engine for on-device synthesis.
 * This works offline and doesn't require API keys.
 *
 * Features:
 * - On-device processing (offline capable)
 * - No API costs
 * - Multiple language support
 * - Voice customization
 *
 * Limitations:
 * - Lower quality than cloud services
 * - Voice options depend on device
 * - Higher latency than streaming services
 * - No streaming (generates complete audio first)
 *
 * @property context Application context
 * @property language Language locale (default: US English)
 */
class AndroidTTSService(
    private val context: Context,
    private val language: Locale = Locale.US,
) : TTSService {
    override val providerName: String = "Android TTS"

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var initializationInProgress = false
    private val initLock = Object()
    private val initCallbacks = mutableListOf<() -> Unit>()

    /**
     * Initialize TTS engine.
     * Should be called before synthesize().
     */
    fun initialize(onReady: (() -> Unit)? = null) {
        synchronized(initLock) {
            if (isInitialized) {
                onReady?.invoke()
                return
            }
            if (onReady != null) {
                initCallbacks.add(onReady)
            }
            if (initializationInProgress) {
                return
            }
            initializationInProgress = true
        }

        tts =
            TextToSpeech(context) { status ->
                synchronized(initLock) {
                    initializationInProgress = false
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(language)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            android.util.Log.e("AndroidTTS", "Language not supported: $language")
                            isInitialized = false
                        } else {
                            isInitialized = true
                            android.util.Log.i("AndroidTTS", "TTS initialized successfully")
                        }
                    } else {
                        android.util.Log.e("AndroidTTS", "TTS initialization failed")
                        isInitialized = false
                    }
                    // Notify all waiting callbacks
                    initCallbacks.forEach { it.invoke() }
                    initCallbacks.clear()
                }
            }
    }

    /**
     * Synthesize text to audio.
     *
     * Note: Android TTS doesn't support true streaming. This implementation
     * synthesizes to a file first, then reads it back as chunks.
     *
     * Auto-initializes if not already initialized.
     *
     * @param text Text to synthesize
     * @return Flow of audio chunks
     */
    override fun synthesize(text: String): Flow<TTSAudioChunk> =
        callbackFlow {
            // Auto-initialize if needed
            if (!isInitialized || tts == null) {
                android.util.Log.i("AndroidTTS", "Auto-initializing TTS...")
                val initComplete = kotlinx.coroutines.CompletableDeferred<Boolean>()

                initialize {
                    initComplete.complete(isInitialized)
                }

                // Wait for initialization (with timeout)
                val initialized =
                    kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        initComplete.await()
                    } ?: false

                if (!initialized) {
                    android.util.Log.e("AndroidTTS", "TTS initialization failed or timed out")
                    close(IllegalStateException("TTS initialization failed. Please try again."))
                    return@callbackFlow
                }
                android.util.Log.i("AndroidTTS", "TTS auto-initialized successfully")
            }

            val engine =
                tts ?: run {
                    close(IllegalStateException("TTS engine not available"))
                    return@callbackFlow
                }

            val utteranceId = UUID.randomUUID().toString()
            val audioFile = File(context.cacheDir, "tts_$utteranceId.wav")
            val startTime = System.currentTimeMillis()
            var isFirstChunk = true

            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        android.util.Log.i("AndroidTTS", "Synthesis started")
                    }

                    override fun onDone(utteranceId: String) {
                        // Read the audio file and emit as chunks
                        try {
                            val audioData = audioFile.readBytes()
                            val chunkSize = 4096 // 4KB chunks

                            audioData.toList().chunked(chunkSize).forEachIndexed { index, chunk ->
                                val chunkArray = chunk.toByteArray()
                                val isLast = (index * chunkSize + chunk.size) >= audioData.size

                                if (isFirstChunk) {
                                    val ttfb = System.currentTimeMillis() - startTime
                                    android.util.Log.i("AndroidTTS", "TTFB: ${ttfb}ms")
                                    isFirstChunk = false

                                    trySend(
                                        TTSAudioChunk(
                                            audioData = chunkArray,
                                            isFirst = true,
                                            isLast = isLast,
                                        ),
                                    )
                                } else {
                                    trySend(
                                        TTSAudioChunk(
                                            audioData = chunkArray,
                                            isFirst = false,
                                            isLast = isLast,
                                        ),
                                    )
                                }
                            }

                            close()
                        } catch (e: Exception) {
                            android.util.Log.e("AndroidTTS", "Failed to read audio file", e)
                            close(e)
                        } finally {
                            // Clean up temp file
                            audioFile.delete()
                        }
                    }

                    override fun onError(utteranceId: String) {
                        android.util.Log.e("AndroidTTS", "Synthesis error")
                        audioFile.delete()
                        close(Exception("TTS synthesis failed"))
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(
                        utteranceId: String?,
                        errorCode: Int,
                    ) {
                        android.util.Log.e("AndroidTTS", "Synthesis error: $errorCode")
                        audioFile.delete()
                        close(Exception("TTS synthesis failed with code $errorCode"))
                    }
                },
            )

            // Start synthesis to file
            val result = engine.synthesizeToFile(text, null, audioFile, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                android.util.Log.e("AndroidTTS", "Failed to start synthesis")
                close(Exception("Failed to start TTS synthesis"))
            }

            awaitClose {
                doStop()
            }
        }

    /**
     * Stop synthesis and release resources (suspend version).
     */
    override suspend fun stop() {
        doStop()
    }

    /**
     * Internal non-suspend stop implementation.
     */
    private fun doStop() {
        tts?.stop()
    }

    /**
     * Release TTS engine completely.
     * Call when done using the service.
     */
    fun release() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
