package com.unamentis.services.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android native Speech-to-Text service implementation.
 *
 * Uses Android's built-in SpeechRecognizer for on-device transcription.
 * This works offline and doesn't require API keys.
 *
 * Features:
 * - On-device processing (offline capable)
 * - No API costs
 * - Partial results support
 * - Multiple language support
 *
 * Limitations:
 * - Lower accuracy than cloud services
 * - Language model depends on device
 * - May not work on all devices/Android versions
 *
 * @property context Application context
 * @property language Language code (default: "en-US")
 */
class AndroidSTTService(
    private val context: Context,
    private val language: String = "en-US",
) : STTService {
    override val providerName: String = "Android STT"

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Start streaming transcription using Android SpeechRecognizer.
     *
     * Note: Android SpeechRecognizer operates in "turn" mode rather than
     * continuous streaming. It will automatically stop after silence.
     *
     * IMPORTANT: SpeechRecognizer must be created and operated on the main thread.
     * This implementation uses a Handler to ensure all SpeechRecognizer operations
     * run on the main looper, regardless of which thread collects this flow.
     */
    override fun startStreaming(): Flow<STTResult> =
        callbackFlow {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                android.util.Log.e("AndroidSTT", "Speech recognition not available")
                close(IllegalStateException("Speech recognition not available on this device"))
                return@callbackFlow
            }

            val startTime = System.currentTimeMillis()

            // SpeechRecognizer MUST be created and operated on the main thread
            mainHandler.post {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    android.util.Log.i("AndroidSTT", "SpeechRecognizer created on main thread")

                    speechRecognizer?.setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                android.util.Log.i("AndroidSTT", "Ready for speech")
                            }

                            override fun onBeginningOfSpeech() {
                                android.util.Log.i("AndroidSTT", "Beginning of speech detected")
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                // Audio level updates (optional to use)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {
                                // Audio buffer updates (optional to use)
                            }

                            override fun onEndOfSpeech() {
                                android.util.Log.i("AndroidSTT", "End of speech detected")
                            }

                            override fun onError(error: Int) {
                                val errorMessage =
                                    when (error) {
                                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                        else -> "Unknown error: $error"
                                    }
                                android.util.Log.e("AndroidSTT", "Recognition error: $errorMessage")

                                // For no-match/timeout: emit empty final result so session can recover
                                // This typically happens when Oboe is holding the microphone
                                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                ) {
                                    android.util.Log.w(
                                        "AndroidSTT",
                                        "No speech detected, emitting empty result for session recovery",
                                    )
                                    val latency = System.currentTimeMillis() - startTime
                                    trySend(
                                        STTResult(
                                            text = "",
                                            isFinal = true,
                                            confidence = 0f,
                                            latencyMs = latency,
                                        ),
                                    )
                                } else {
                                    close(Exception(errorMessage))
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                                val latency = System.currentTimeMillis() - startTime

                                matches?.firstOrNull()?.let { text ->
                                    val confidence = confidences?.firstOrNull() ?: 1.0f
                                    android.util.Log.i("AndroidSTT", "Final result: $text (confidence: $confidence)")

                                    trySend(
                                        STTResult(
                                            text = text,
                                            isFinal = true,
                                            confidence = confidence,
                                            latencyMs = latency,
                                        ),
                                    )
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches =
                                    partialResults?.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION,
                                    )
                                val latency = System.currentTimeMillis() - startTime

                                matches?.firstOrNull()?.let { text ->
                                    android.util.Log.d("AndroidSTT", "Partial result: $text")
                                    // Partial results have lower confidence
                                    trySend(
                                        STTResult(
                                            text = text,
                                            isFinal = false,
                                            confidence = 0.5f,
                                            latencyMs = latency,
                                        ),
                                    )
                                }
                            }

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?,
                            ) {
                                // Additional events (optional to handle)
                            }
                        },
                    )

                    // Create recognition intent
                    val intent =
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        }

                    // Start listening
                    speechRecognizer?.startListening(intent)
                    android.util.Log.i("AndroidSTT", "Started listening")
                } catch (e: Exception) {
                    android.util.Log.e("AndroidSTT", "Failed to start SpeechRecognizer", e)
                    close(e)
                }
            }

            awaitClose {
                mainHandler.post {
                    doStopStreaming()
                }
            }
        }

    /**
     * Stop streaming and release recognizer (suspend version).
     */
    override suspend fun stopStreaming() {
        mainHandler.post {
            doStopStreaming()
        }
    }

    /**
     * Internal non-suspend stop implementation.
     * Must be called on the main thread.
     */
    private fun doStopStreaming() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            android.util.Log.i("AndroidSTT", "SpeechRecognizer stopped and destroyed")
        } catch (e: Exception) {
            android.util.Log.e("AndroidSTT", "Error stopping SpeechRecognizer", e)
        }
    }
}
