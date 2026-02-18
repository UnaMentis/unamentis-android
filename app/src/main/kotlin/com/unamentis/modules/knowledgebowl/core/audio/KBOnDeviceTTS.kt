package com.unamentis.modules.knowledgebowl.core.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knowledge Bowl on-device text-to-speech wrapper.
 *
 * Provides simplified TTS access for Knowledge Bowl practice sessions using
 * Android's built-in [TextToSpeech] engine. Unlike the main app's TTS services
 * which implement the [TTSService] streaming interface for audio chunks, this
 * wrapper offers a simpler fire-and-forget API with completion callbacks,
 * tailored for reading questions and feedback aloud during KB practice.
 *
 * Features:
 * - On-device processing (offline capable, no API costs)
 * - Configurable voice parameters via [VoiceConfig]
 * - Preset pacing for questions ([VoiceConfig.QUESTION_PACE]) and other modes
 * - Convenience [speakQuestion] method with appropriate pacing
 * - Auto-initialization on first use
 *
 * Limitations:
 * - Voice quality depends on device TTS engine
 * - No true streaming (synthesizes then plays)
 * - Pause/resume limited to stop-and-restart behavior
 *
 * @property context Application context for initializing the [TextToSpeech] engine
 */
@Suppress("TooManyFunctions")
@Singleton
class KBOnDeviceTTS
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KBOnDeviceTTS"
        }

        private var tts: TextToSpeech? = null

        @Volatile
        private var isInitialized: Boolean = false

        /**
         * Whether the TTS engine is currently speaking.
         */
        val isSpeaking: Boolean
            get() = tts?.isSpeaking == true

        // Text that was being spoken before a pause, for potential resume
        private var pausedText: String? = null
        private var pausedConfig: VoiceConfig? = null
        private var pausedOnDone: (() -> Unit)? = null

        /**
         * Voice configuration for controlling speech output parameters.
         *
         * @property language The locale for speech synthesis
         * @property rate Speech rate multiplier (1.0 = normal, <1.0 = slower, >1.0 = faster)
         * @property pitch Voice pitch multiplier (1.0 = normal)
         * @property volume Output volume (0.0 to 1.0)
         */
        data class VoiceConfig(
            val language: Locale = Locale.US,
            val rate: Float = 1.0f,
            val pitch: Float = 1.0f,
            val volume: Float = 1.0f,
        ) {
            companion object {
                /** Slightly slower pace for reading questions clearly. */
                val QUESTION_PACE = VoiceConfig(rate = 0.9f)

                /** Slow pace for deliberate reading or explanations. */
                val SLOW_PACE = VoiceConfig(rate = 0.75f)

                /** Faster pace for quick feedback or familiar content. */
                val FAST_PACE = VoiceConfig(rate = 1.1f)
            }
        }

        /**
         * Initialize the TTS engine.
         *
         * Sets up the [TextToSpeech] instance and configures the default language.
         * The [onReady] callback is invoked once initialization succeeds.
         * Safe to call multiple times; subsequent calls invoke [onReady] immediately
         * if already initialized.
         *
         * @param onReady Callback invoked when the engine is ready for use
         */
        fun initialize(onReady: () -> Unit = {}) {
            if (isInitialized && tts != null) {
                Log.d(TAG, "Already initialized")
                onReady()
                return
            }

            Log.i(TAG, "Initializing TTS engine")

            tts =
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            Log.e(TAG, "Language not supported: ${Locale.US}")
                            isInitialized = false
                        } else {
                            isInitialized = true
                            Log.i(TAG, "TTS engine initialized successfully")
                            onReady()
                        }
                    } else {
                        Log.e(TAG, "TTS initialization failed with status: $status")
                        isInitialized = false
                    }
                }
        }

        /**
         * Speak the given text with the specified voice configuration.
         *
         * Auto-initializes the TTS engine if it has not been initialized yet.
         * The [onDone] callback is invoked when speech playback completes.
         *
         * @param text The text to speak
         * @param config Voice configuration for rate, pitch, and volume
         * @param onDone Callback invoked when speech completes
         */
        fun speak(
            text: String,
            config: VoiceConfig = VoiceConfig(),
            onDone: () -> Unit = {},
        ) {
            // Clear any paused state since we are starting fresh
            pausedText = null
            pausedConfig = null
            pausedOnDone = null

            if (!isInitialized || tts == null) {
                Log.i(TAG, "Auto-initializing before speak")
                initialize {
                    doSpeak(text, config, onDone)
                }
                return
            }

            doSpeak(text, config, onDone)
        }

        /**
         * Speak a Knowledge Bowl question with appropriate pacing.
         *
         * Uses [VoiceConfig.QUESTION_PACE] by default for a slightly slower,
         * clearer delivery suitable for quiz questions.
         *
         * @param question The question text to speak
         * @param config Voice configuration (defaults to [VoiceConfig.QUESTION_PACE])
         * @param onDone Callback invoked when speech completes
         */
        fun speakQuestion(
            question: String,
            config: VoiceConfig = VoiceConfig.QUESTION_PACE,
            onDone: () -> Unit = {},
        ) {
            Log.i(TAG, "Speaking question: \"${question.take(50)}...\"")
            speak(question, config, onDone)
        }

        /**
         * Pause speech playback.
         *
         * Since Android's [TextToSpeech] does not support true pause/resume,
         * this stops playback and saves the current text so it can be
         * re-spoken from the beginning if [speak] is called again with the
         * same content. The paused state is cleared on the next [speak] or [stop] call.
         */
        fun pause() {
            if (!isSpeaking) {
                Log.d(TAG, "Not speaking, nothing to pause")
                return
            }

            // Save current state for potential resume
            // Note: Android TTS does not support true pause; we stop and record
            tts?.stop()
            Log.i(TAG, "Speech paused (stopped with saved state)")
        }

        /**
         * Stop speech playback and clear any paused state.
         */
        fun stop() {
            tts?.stop()
            pausedText = null
            pausedConfig = null
            pausedOnDone = null
            Log.i(TAG, "Speech stopped")
        }

        /**
         * Release all TTS resources.
         *
         * Must be called when the TTS engine is no longer needed (e.g., when
         * the Knowledge Bowl session ends or the component is destroyed).
         * After calling this method, [initialize] must be called again before
         * further use.
         */
        fun shutdown() {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            Log.i(TAG, "TTS engine shut down")
        }

        // Private Helpers

        /**
         * Internal speak implementation that configures the engine and starts playback.
         */
        private fun doSpeak(
            text: String,
            config: VoiceConfig,
            onDone: () -> Unit,
        ) {
            val engine = tts
            if (engine == null) {
                Log.e(TAG, "TTS engine is null, cannot speak")
                onDone()
                return
            }

            // Store for potential pause/resume
            pausedText = text
            pausedConfig = config
            pausedOnDone = onDone

            // Apply voice configuration
            engine.setLanguage(config.language)
            engine.setSpeechRate(config.rate)
            engine.setPitch(config.pitch)

            val utteranceId = UUID.randomUUID().toString()

            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        Log.d(TAG, "Utterance started: ${text.take(30)}...")
                    }

                    override fun onDone(id: String?) {
                        Log.d(TAG, "Utterance complete")
                        pausedText = null
                        pausedConfig = null
                        pausedOnDone = null
                        onDone()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        Log.e(TAG, "Utterance error")
                        pausedText = null
                        pausedConfig = null
                        pausedOnDone = null
                        onDone()
                    }

                    override fun onError(
                        id: String?,
                        errorCode: Int,
                    ) {
                        Log.e(TAG, "Utterance error with code: $errorCode")
                        pausedText = null
                        pausedConfig = null
                        pausedOnDone = null
                        onDone()
                    }
                },
            )

            // Set volume via params bundle
            val params =
                Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.volume)
                }

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Failed to start speaking, result code: $result")
                onDone()
            }
        }
    }
