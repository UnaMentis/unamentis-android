package com.unamentis.modules.knowledgebowl.core.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Knowledge Bowl on-device speech-to-text wrapper.
 *
 * Provides simplified STT access for Knowledge Bowl practice sessions using
 * Android's built-in [SpeechRecognizer] for offline/on-device speech recognition.
 * Unlike the main app's STT services which implement the [STTService] streaming
 * interface, this wrapper offers a simpler one-shot and callback-based API
 * tailored to KB answer input.
 *
 * Features:
 * - On-device processing via [RecognizerIntent.EXTRA_PREFER_OFFLINE]
 * - One-shot recognition via [recognize] for quick answer capture
 * - Continuous listening via [startListening] / [stopListening] callbacks
 * - API 33+ on-device availability check
 *
 * Limitations:
 * - Accuracy depends on device speech model
 * - SpeechRecognizer must be created/operated on the main thread
 * - On-device model may not be available on all devices
 *
 * @property context Application context for creating [SpeechRecognizer] instances
 */
@Suppress("TooManyFunctions")
@Singleton
class KBOnDeviceSTT
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KBOnDeviceSTT"
        }

        private val mainHandler = Handler(Looper.getMainLooper())
        private var speechRecognizer: SpeechRecognizer? = null
        private var isListening: Boolean = false

        /**
         * Check if on-device speech recognition is available.
         *
         * On API 33+ (TIRAMISU), uses [SpeechRecognizer.isOnDeviceRecognitionAvailable]
         * for a more accurate check of offline capability. On older API levels,
         * uses the general [SpeechRecognizer.isRecognitionAvailable] check.
         *
         * @return true if on-device recognition is available
         */
        fun isAvailable(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } else {
                SpeechRecognizer.isRecognitionAvailable(context)
            }

        /**
         * Perform one-shot speech recognition and return the transcribed text.
         *
         * Creates a temporary [SpeechRecognizer], listens for a single utterance,
         * and returns the recognized text. The recognizer is automatically cleaned
         * up after recognition completes or fails.
         *
         * This suspend function is safe to call from any coroutine context; all
         * SpeechRecognizer operations are posted to the main thread internally.
         *
         * @return The transcribed text, or null if recognition failed or no speech was detected
         */
        suspend fun recognize(): String? =
            suspendCancellableCoroutine { continuation ->
                if (!isAvailable()) {
                    Log.w(TAG, "On-device speech recognition not available")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                mainHandler.post {
                    if (continuation.isCancelled) return@post

                    var recognizer: SpeechRecognizer? = null
                    try {
                        recognizer = createRecognizer()

                        recognizer.setRecognitionListener(
                            object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) {
                                    Log.d(TAG, "One-shot: ready for speech")
                                }

                                override fun onBeginningOfSpeech() {
                                    Log.d(TAG, "One-shot: speech started")
                                }

                                override fun onRmsChanged(rmsdB: Float) {
                                    // Audio level changes - not used for one-shot
                                }

                                override fun onBufferReceived(buffer: ByteArray?) {
                                    // Audio buffer - not used for one-shot
                                }

                                override fun onEndOfSpeech() {
                                    Log.d(TAG, "One-shot: speech ended")
                                }

                                override fun onError(error: Int) {
                                    val errorMsg = describeError(error)
                                    Log.e(TAG, "One-shot recognition error: $errorMsg")
                                    destroyRecognizer(recognizer)
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }

                                override fun onResults(results: Bundle?) {
                                    val matches =
                                        results?.getStringArrayList(
                                            SpeechRecognizer.RESULTS_RECOGNITION,
                                        )
                                    val text = matches?.firstOrNull()
                                    Log.i(TAG, "One-shot result: \"$text\"")
                                    destroyRecognizer(recognizer)
                                    if (continuation.isActive) {
                                        continuation.resume(text)
                                    }
                                }

                                override fun onPartialResults(partialResults: Bundle?) {
                                    // Partial results ignored for one-shot mode
                                }

                                override fun onEvent(
                                    eventType: Int,
                                    params: Bundle?,
                                ) {
                                    // Additional events - not used
                                }
                            },
                        )

                        recognizer.startListening(createRecognitionIntent())
                        Log.i(TAG, "One-shot recognition started")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start one-shot recognition", e)
                        destroyRecognizer(recognizer)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    mainHandler.post {
                        stopListening()
                    }
                }
            }

        /**
         * Start continuous listening with callbacks for results and errors.
         *
         * Creates a [SpeechRecognizer] and begins listening for speech. Results
         * are delivered via [onResult] as they are finalized. Errors are reported
         * via [onError]. The recognizer remains active until [stopListening] is called.
         *
         * Must not be called if already listening; call [stopListening] first.
         *
         * @param onResult Callback invoked with the recognized text when a final result is available
         * @param onError Callback invoked with an error description if recognition fails
         */
        fun startListening(
            onResult: (String) -> Unit,
            onError: (String) -> Unit,
        ) {
            if (isListening) {
                Log.w(TAG, "Already listening, ignoring startListening call")
                return
            }

            if (!isAvailable()) {
                onError("On-device speech recognition not available")
                return
            }

            mainHandler.post {
                try {
                    val recognizer = createRecognizer()
                    speechRecognizer = recognizer

                    recognizer.setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                Log.d(TAG, "Continuous: ready for speech")
                            }

                            override fun onBeginningOfSpeech() {
                                Log.d(TAG, "Continuous: speech started")
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                // Audio level changes
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {
                                // Audio buffer
                            }

                            override fun onEndOfSpeech() {
                                Log.d(TAG, "Continuous: speech ended")
                            }

                            override fun onError(error: Int) {
                                val errorMsg = describeError(error)
                                Log.e(TAG, "Continuous recognition error: $errorMsg")

                                // For no-match or timeout, restart listening
                                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                ) {
                                    Log.d(TAG, "Restarting after recoverable error")
                                    restartListening(onError)
                                } else {
                                    isListening = false
                                    onError(errorMsg)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches =
                                    results?.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION,
                                    )
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotEmpty()) {
                                    Log.i(TAG, "Continuous result: \"$text\"")
                                    onResult(text)
                                }

                                // Restart listening for next utterance
                                if (isListening) {
                                    restartListening(onError)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                // Partial results during continuous listening
                                val matches =
                                    partialResults?.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION,
                                    )
                                Log.d(TAG, "Partial: ${matches?.firstOrNull()}")
                            }

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?,
                            ) {
                                // Additional events
                            }
                        },
                    )

                    recognizer.startListening(createRecognitionIntent())
                    isListening = true
                    Log.i(TAG, "Continuous listening started")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start continuous listening", e)
                    isListening = false
                    onError("Failed to start speech recognition: ${e.message}")
                }
            }
        }

        /**
         * Stop listening and release the speech recognizer.
         *
         * Safe to call even if not currently listening. All cleanup is
         * performed on the main thread.
         */
        fun stopListening() {
            isListening = false

            mainHandler.post {
                destroyRecognizer(speechRecognizer)
                speechRecognizer = null
                Log.i(TAG, "Listening stopped")
            }
        }

        // Private Helpers

        /**
         * Create an appropriate [SpeechRecognizer] instance.
         *
         * On API 33+, creates an on-device recognizer for guaranteed offline use.
         * On older API levels, creates the default recognizer (which may use network).
         */
        private fun createRecognizer(): SpeechRecognizer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

        /**
         * Create the recognition intent configured for on-device, free-form speech.
         */
        private fun createRecognitionIntent(): Intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

        /**
         * Restart continuous listening after a result or recoverable error.
         */
        private fun restartListening(onError: (String) -> Unit) {
            mainHandler.post {
                if (!isListening) return@post

                try {
                    speechRecognizer?.startListening(createRecognitionIntent())
                    Log.d(TAG, "Continuous listening restarted")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart listening", e)
                    isListening = false
                    onError("Failed to restart speech recognition: ${e.message}")
                }
            }
        }

        /**
         * Safely stop and destroy a [SpeechRecognizer] instance.
         * Must be called on the main thread.
         */
        private fun destroyRecognizer(recognizer: SpeechRecognizer?) {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying SpeechRecognizer", e)
            }
        }

        /**
         * Convert a [SpeechRecognizer] error code to a human-readable description.
         *
         * @param error The error code from [RecognitionListener.onError]
         * @return A descriptive error message
         */
        private fun describeError(error: Int): String =
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
    }
