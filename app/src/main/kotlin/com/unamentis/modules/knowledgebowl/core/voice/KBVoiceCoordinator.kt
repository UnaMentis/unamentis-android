package com.unamentis.modules.knowledgebowl.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.unamentis.data.model.STTService
import com.unamentis.data.model.TTSService
import com.unamentis.data.model.VADService
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates TTS and STT for voice-first Knowledge Bowl practice.
 *
 * Automatically speaks questions via TTS and listens for
 * verbal answers via STT, working alongside the text UI.
 */
@Suppress(
    "TooManyFunctions",
    "UnusedPrivateProperty",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "NestedBlockDepth",
)
@Singleton
class KBVoiceCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ttsService: TTSService,
        private val sttService: STTService,
        private val vadService: VADService,
    ) {
        companion object {
            private const val TAG = "KBVoiceCoordinator"
        }

        // Published state
        private val _isSpeaking = MutableStateFlow(false)
        val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

        private val _currentTranscript = MutableStateFlow("")
        val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

        private val _isReady = MutableStateFlow(false)
        val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        // Audio cache for pre-generated server audio
        private var audioCache: KBAudioCache? = null
        private var useServerTTS: Boolean = false

        // State management
        private var sttStreamJob: Job? = null
        private var ttsStreamJob: Job? = null
        private val scope = CoroutineScope(Dispatchers.Main)

        // Silence detection for utterance completion
        private var hasDetectedSpeech = false

        // Callbacks
        private var onTranscriptComplete: ((String) -> Unit)? = null

        // Setup

        /**
         * Initialize voice services for practice session.
         */
        suspend fun setup() {
            Log.i(TAG, "Setting up KB voice coordinator")

            // Check if self-hosted server is enabled for audio cache
            val prefs = context.getSharedPreferences("unamentis_prefs", Context.MODE_PRIVATE)
            val selfHostedEnabled = prefs.getBoolean("selfHostedEnabled", false)
            val serverIP = prefs.getString("primaryServerIP", "") ?: ""

            if (selfHostedEnabled && serverIP.isNotEmpty()) {
                audioCache = KBAudioCache(serverHost = serverIP)
                useServerTTS = true
                Log.i(TAG, "KB audio cache initialized for server: $serverIP")
            }

            _isReady.value = true
            Log.i(TAG, "KB voice coordinator ready")
        }

        /**
         * Shutdown all voice services.
         */
        suspend fun shutdown() {
            stopListening()
            stopSpeaking()
            audioCache?.clear()
            audioCache = null
            _isReady.value = false
            Log.i(TAG, "KB voice coordinator shut down")
        }

        // TTS: Speaking

        /**
         * Speak text using TTS and wait for completion.
         *
         * @param text Text to speak
         */
        suspend fun speak(text: String) {
            if (!_isReady.value) {
                Log.w(TAG, "Voice services not ready, cannot speak")
                return
            }

            _isSpeaking.value = true
            Log.i(TAG, "Speaking: \"${text.take(50)}...\"")

            try {
                // Collect all audio chunks from TTS stream
                ttsService.synthesize(text).collect { chunk ->
                    // Audio chunks are handled by the audio engine
                    // For now we just wait for completion
                    if (chunk.isLast) {
                        Log.d(TAG, "TTS synthesis complete")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed: ${e.message}")
            }

            _isSpeaking.value = false
            Log.i(TAG, "Finished speaking")
        }

        /**
         * Stop any ongoing TTS playback.
         */
        suspend fun stopSpeaking() {
            ttsStreamJob?.cancel()
            ttsStreamJob = null
            try {
                ttsService.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping TTS: ${e.message}")
            }
            _isSpeaking.value = false
        }

        /**
         * Speak a question with proper pacing for competition style.
         *
         * @param question Question to speak
         */
        suspend fun speakQuestion(question: KBQuestion) {
            // Try server cache first if available
            if (useServerTTS) {
                audioCache?.let { cache ->
                    try {
                        val cached = cache.getAudio(question.id, KBSegmentType.QUESTION)
                        if (cached != null) {
                            playCachedAudio(cached)
                            return
                        }
                    } catch (e: Exception) {
                        val msg = "Server audio unavailable, falling back to local"
                        Log.w(TAG, "$msg: ${e.message}")
                    }
                }
            }

            // Fallback: speak with local TTS
            speak(question.text)
        }

        /**
         * Speak correct/incorrect feedback with explanation.
         *
         * @param isCorrect Whether the answer was correct
         * @param correctAnswer The correct answer text
         * @param explanation Optional explanation
         * @param question Optional question for cached explanation audio
         */
        @Suppress("ReturnCount")
        suspend fun speakFeedback(
            isCorrect: Boolean,
            correctAnswer: String,
            explanation: String = "",
            question: KBQuestion? = null,
        ) {
            // Try server feedback audio first
            if (useServerTTS) {
                audioCache?.let { cache ->
                    try {
                        val feedbackType = if (isCorrect) "correct" else "incorrect"
                        val feedbackAudio = cache.getFeedbackAudio(feedbackType)
                        if (feedbackAudio != null) {
                            playCachedAudio(feedbackAudio)
                        } else {
                            // Fallback for feedback
                            speakLocalFeedback(isCorrect, correctAnswer)
                        }
                    } catch (e: Exception) {
                        speakLocalFeedback(isCorrect, correctAnswer)
                    }
                }
            } else {
                speakLocalFeedback(isCorrect, correctAnswer)
            }

            // Brief pause before explanation
            if (explanation.isNotEmpty()) {
                kotlinx.coroutines.delay(500)

                // Try cached explanation
                if (useServerTTS && question != null) {
                    audioCache?.let { cache ->
                        try {
                            val explainCached = cache.getAudio(question.id, KBSegmentType.EXPLANATION)
                            if (explainCached != null) {
                                playCachedAudio(explainCached)
                                return
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Explanation cache miss, using local TTS")
                        }
                    }
                }

                // Fallback: local TTS
                speak(explanation)
            }
        }

        private suspend fun speakLocalFeedback(
            isCorrect: Boolean,
            correctAnswer: String,
        ) {
            if (isCorrect) {
                speak("Correct!")
            } else {
                speak("Incorrect. The correct answer is $correctAnswer.")
            }
        }

        /**
         * Speak session completion message.
         *
         * @param correctCount Number of correct answers
         * @param totalCount Total number of questions
         */
        @Suppress("MagicNumber")
        suspend fun speakCompletion(
            correctCount: Int,
            totalCount: Int,
        ) {
            val accuracy =
                if (totalCount > 0) {
                    (correctCount.toFloat() / totalCount * 100).toInt()
                } else {
                    0
                }
            val message =
                when {
                    accuracy >= 80 ->
                        "Excellent work! You got $correctCount out of $totalCount correct. " +
                            "That's $accuracy percent accuracy."
                    accuracy >= 60 ->
                        "Good effort! You got $correctCount out of $totalCount correct. " +
                            "Keep practicing to improve."
                    else ->
                        "Session complete. You got $correctCount out of $totalCount correct. " +
                            "Consider reviewing the topics you missed."
                }

            speak(message)
        }

        // Server Audio Cache

        @Suppress("MagicNumber")
        private suspend fun playCachedAudio(cached: KBCachedAudio) {
            _isSpeaking.value = true
            Log.d(TAG, "Playing cached audio (${cached.data.size} bytes)")

            // Skip WAV header (44 bytes) to get raw PCM data
            val pcmData =
                if (cached.data.size > 44) {
                    cached.data.copyOfRange(44, cached.data.size)
                } else {
                    cached.data
                }

            // Use sample rate from cached audio, default to 22050 Hz (common TTS rate)
            val sampleRate = cached.sampleRate.takeIf { it > 0 } ?: 22050

            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    playPcmAudio(pcmData, sampleRate)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play cached audio: ${e.message}")
            }

            _isSpeaking.value = false
        }

        /**
         * Play raw PCM audio data using AudioTrack.
         *
         * @param pcmData Raw 16-bit PCM audio data
         * @param sampleRate Sample rate in Hz (default 22050 for TTS)
         */
        @Suppress("MagicNumber")
        private fun playPcmAudio(
            pcmData: ByteArray,
            sampleRate: Int,
        ) {
            val audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

            val audioFormat =
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

            val minBufferSize =
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )

            val bufferSize = maxOf(minBufferSize, pcmData.size)

            val audioTrack =
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

            try {
                audioTrack.write(pcmData, 0, pcmData.size)
                audioTrack.play()

                // Wait for playback to complete
                // Calculate duration: (samples / sampleRate) seconds
                // For 16-bit mono audio: samples = bytes / 2
                val durationMs = (pcmData.size / 2 * 1000L) / sampleRate
                Thread.sleep(durationMs + 100) // Add small buffer for completion

                audioTrack.stop()
                Log.d(TAG, "PCM audio playback complete (${durationMs}ms)")
            } finally {
                audioTrack.release()
            }
        }

        /**
         * Warm the cache at session start.
         *
         * @param questions Questions to warm cache for
         */
        suspend fun warmCache(questions: List<KBQuestion>) {
            audioCache?.warmCache(questions, lookahead = 5)
        }

        /**
         * Prefetch audio for upcoming questions.
         *
         * @param questions Full list of questions
         * @param currentIndex Current question index
         */
        suspend fun prefetchUpcoming(
            questions: List<KBQuestion>,
            currentIndex: Int,
        ) {
            audioCache?.prefetchUpcoming(questions, currentIndex, lookahead = 3)
        }

        // STT: Listening for Answers

        /**
         * Start listening for speech input.
         */
        fun startListening() {
            if (_isListening.value) return

            _isListening.value = true
            _currentTranscript.value = ""
            hasDetectedSpeech = false

            sttStreamJob =
                scope.launch {
                    try {
                        sttService.startStreaming().collect { result ->
                            handleSTTResult(result.text, result.isFinal)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "STT listening failed: ${e.message}")
                        _isListening.value = false
                    }
                }

            Log.i(TAG, "KB voice listening started")
        }

        /**
         * Stop listening for speech input.
         */
        suspend fun stopListening() {
            sttStreamJob?.cancel()
            sttStreamJob = null

            try {
                sttService.stopStreaming()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping STT: ${e.message}")
            }

            _isListening.value = false
            hasDetectedSpeech = false
            _currentTranscript.value = ""

            Log.i(TAG, "KB voice listening stopped")
        }

        /**
         * Set callback for when a complete transcript is available.
         *
         * @param callback Callback function receiving the transcript
         */
        fun onTranscriptComplete(callback: (String) -> Unit) {
            this.onTranscriptComplete = callback
        }

        /**
         * Clear the current transcript and reset for new answer.
         */
        fun resetTranscript() {
            _currentTranscript.value = ""
            hasDetectedSpeech = false
        }

        // Private: STT Result Handling

        private fun handleSTTResult(
            transcript: String,
            isFinal: Boolean,
        ) {
            _currentTranscript.value = transcript

            if (transcript.isNotEmpty()) {
                hasDetectedSpeech = true
            }

            if (isFinal && transcript.isNotEmpty()) {
                finalizeUtterance()
            }
        }

        private fun finalizeUtterance() {
            val transcript = _currentTranscript.value
            if (transcript.isEmpty()) return

            Log.i(TAG, "Utterance complete: \"$transcript\"")

            // Reset state
            hasDetectedSpeech = false

            // Notify callback
            onTranscriptComplete?.invoke(transcript)
        }
    }

/**
 * Errors for KBVoiceCoordinator operations.
 */
sealed class VoiceCoordinatorError : Exception() {
    data object NotConfigured : VoiceCoordinatorError() {
        override val message = "Voice services not configured"
    }

    data object AudioFormatUnavailable : VoiceCoordinatorError() {
        override val message = "Audio format not available"
    }

    data object TTSUnavailable : VoiceCoordinatorError() {
        override val message = "Text-to-speech service unavailable"
    }

    data object STTUnavailable : VoiceCoordinatorError() {
        override val message = "Speech-to-text service unavailable"
    }
}
