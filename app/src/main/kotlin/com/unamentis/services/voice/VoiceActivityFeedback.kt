package com.unamentis.services.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.unamentis.R
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * System sounds for quick feedback (no TTS latency).
 *
 * Each tone maps to a specific haptic pattern for accessibility
 * when audio is disabled.
 */
enum class FeedbackTone {
    /** Subtle confirmation when a voice command is recognized. */
    COMMAND_RECOGNIZED,

    /** Brief error tone for invalid commands. */
    COMMAND_INVALID,

    /** Countdown tick sound. */
    COUNTDOWN_TICK,

    /** Success chime for correct answers. */
    CORRECT,

    /** Failure tone for incorrect answers. */
    INCORRECT,

    /** Get user's attention. */
    ATTENTION,
}

/**
 * Provides audio and haptic feedback for voice-first activities.
 *
 * Two feedback modes:
 * 1. **TTS Announcements**: For state changes, instructions, results (uses configured TTS provider)
 * 2. **System Tones**: For quick acknowledgments, countdowns (instant playback via SoundPool)
 *
 * All feedback is designed to work with:
 * - Hands-free scenarios (driving, cooking)
 * - TalkBack enabled (accessibility)
 * - Sound off (haptic fallback)
 *
 * Platform Adaptations from iOS:
 * - UIImpactFeedbackGenerator -> Android VibrationEffect with varying amplitudes
 * - UINotificationFeedbackGenerator -> Android VibrationEffect patterns (success/warning/error)
 * - UISelectionFeedbackGenerator -> Android VibrationEffect.EFFECT_TICK
 * - System sounds -> Android SoundPool for low-latency playback
 *
 * @property context Application context for vibrator and sound access
 * @property ttsService Optional TTS service for spoken announcements
 */
@Suppress("TooManyFunctions")
class VoiceActivityFeedback(
    private val context: Context,
    private val ttsService: TTSService? = null,
) {
    companion object {
        private const val TAG = "VoiceActivityFeedback"
        private const val MAX_STREAMS = 4
    }

    // -- Configuration --

    /** Whether audio announcements are enabled. */
    private val _audioEnabled = MutableStateFlow(true)
    val audioEnabled: StateFlow<Boolean> = _audioEnabled.asStateFlow()

    /** Whether haptic feedback is enabled. */
    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    /** Whether currently speaking an announcement. */
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // -- Private State --

    private val vibrator: Vibrator? = getVibrator(context)
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<FeedbackTone, Int>()
    private var soundsLoaded = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentSpeakingJob: Job? = null

    init {
        setupSoundPool()
    }

    // -- Public API: Configuration --

    /**
     * Set whether audio announcements are enabled.
     *
     * @param enabled true to enable audio, false to disable
     */
    fun setAudioEnabled(enabled: Boolean) {
        _audioEnabled.value = enabled
        Log.d(TAG, "Audio enabled: $enabled")
    }

    /**
     * Set whether haptic feedback is enabled.
     *
     * @param enabled true to enable haptics, false to disable
     */
    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        Log.d(TAG, "Haptics enabled: $enabled")
    }

    // -- Public API: Announcements --

    /**
     * Speak an announcement via the configured TTS provider.
     *
     * @param text Text to speak
     * @param priority If true, interrupts current speech
     */
    fun announce(
        text: String,
        priority: Boolean = false,
    ) {
        if (!_audioEnabled.value) {
            Log.d(TAG, "Audio disabled, skipping announcement: $text")
            return
        }

        if (priority) {
            stopSpeaking()
        }

        currentSpeakingJob =
            scope.launch {
                speakWithTTS(text)
            }

        Log.d(TAG, "Announced: $text")
    }

    /**
     * Announce countdown start.
     *
     * @param seconds Total countdown seconds
     * @param contextText Activity context (e.g., "conference time")
     */
    fun announceCountdownStart(
        seconds: Int,
        contextText: String,
    ) {
        announce(
            context.getString(R.string.voice_feedback_countdown_start, contextText, seconds),
            priority = true,
        )
        impactHaptic()
    }

    /**
     * Announce countdown milestone.
     *
     * @param seconds Remaining seconds
     */
    fun announceCountdownMilestone(seconds: Int) {
        announce(context.getString(R.string.voice_feedback_countdown_milestone, seconds))
    }

    /**
     * Announce countdown complete.
     *
     * @param contextText What happens next (e.g., "Ready to answer")
     */
    fun announceCountdownComplete(contextText: String) {
        announce(
            context.getString(R.string.voice_feedback_countdown_complete, contextText),
            priority = true,
        )
        notificationHaptic(NotificationType.WARNING)
    }

    /**
     * Announce command was recognized via tone and haptic.
     *
     * Does not TTS the command name -- the resulting action confirms it.
     *
     * @param command The recognized command
     */
    fun announceCommandRecognized(
        @Suppress("UNUSED_PARAMETER") command: VoiceCommand,
    ) {
        playTone(FeedbackTone.COMMAND_RECOGNIZED)
        selectionHaptic()
    }

    /**
     * Announce that an answer was received.
     */
    fun announceAnswerReceived() {
        announce(context.getString(R.string.voice_feedback_answer_received), priority = false)
        selectionHaptic()
    }

    /**
     * Announce correct answer with success tone and haptic.
     */
    fun announceCorrect() {
        playTone(FeedbackTone.CORRECT)
        notificationHaptic(NotificationType.SUCCESS)
        announce(context.getString(R.string.voice_feedback_correct))
    }

    /**
     * Announce incorrect answer with error tone and haptic.
     *
     * @param correctAnswer The correct answer to announce (optional)
     */
    fun announceIncorrect(correctAnswer: String? = null) {
        playTone(FeedbackTone.INCORRECT)
        notificationHaptic(NotificationType.ERROR)
        if (correctAnswer != null) {
            announce(context.getString(R.string.voice_feedback_incorrect_with_answer, correctAnswer))
        } else {
            announce(context.getString(R.string.voice_feedback_incorrect))
        }
    }

    /**
     * Announce activity started.
     *
     * @param name Activity name
     */
    fun announceActivityStarted(name: String) {
        announce(context.getString(R.string.voice_feedback_activity_started, name), priority = true)
        impactHaptic()
    }

    /**
     * Announce activity completed with summary.
     *
     * @param summary Completion summary
     */
    fun announceActivityCompleted(summary: String) {
        announce(summary, priority = true)
        notificationHaptic(NotificationType.SUCCESS)
    }

    /**
     * Announce next question number and total.
     *
     * @param number Question number
     * @param total Total questions
     */
    fun announceNextQuestion(
        number: Int,
        total: Int,
    ) {
        announce(context.getString(R.string.voice_feedback_next_question, number, total))
    }

    // -- Public API: Tones --

    /**
     * Play a feedback tone (instant, no TTS latency).
     *
     * @param tone The tone to play
     */
    fun playTone(tone: FeedbackTone) {
        if (!_audioEnabled.value) return

        val soundId = soundIds[tone]
        if (soundId != null && soundsLoaded) {
            soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            Log.d(TAG, "Played tone: $tone")
        } else {
            Log.d(TAG, "Tone not loaded, skipping: $tone")
        }

        playToneHaptic(tone)
    }

    /**
     * Play countdown tick with light haptic.
     */
    fun playCountdownTick() {
        playTone(FeedbackTone.COUNTDOWN_TICK)
        impactHaptic(amplitude = IMPACT_LIGHT)
    }

    // -- Public API: Haptics --

    /**
     * Trigger impact haptic feedback.
     *
     * @param amplitude Vibration amplitude (1-255, default [IMPACT_MEDIUM])
     */
    fun impactHaptic(amplitude: Int = IMPACT_MEDIUM) {
        if (!_hapticsEnabled.value) return
        vibrate(duration = 50L, amplitude = amplitude)
    }

    /**
     * Trigger notification haptic feedback.
     *
     * @param type Notification type (success, warning, error)
     */
    fun notificationHaptic(type: NotificationType) {
        if (!_hapticsEnabled.value) return

        val (timings, amplitudes) =
            when (type) {
                NotificationType.SUCCESS -> longArrayOf(0, 30, 60, 30) to intArrayOf(0, 80, 0, 160)
                NotificationType.WARNING -> longArrayOf(0, 40, 50, 40) to intArrayOf(0, 120, 0, 120)
                NotificationType.ERROR -> longArrayOf(0, 50, 40, 60) to intArrayOf(0, 180, 0, 200)
            }

        vibratePattern(timings, amplitudes)
    }

    /**
     * Trigger selection haptic (light tap).
     */
    fun selectionHaptic() {
        if (!_hapticsEnabled.value) return
        vibrate(duration = 20L, amplitude = IMPACT_LIGHT)
    }

    // -- Control --

    /**
     * Stop any ongoing speech.
     */
    fun stopSpeaking() {
        currentSpeakingJob?.cancel()
        currentSpeakingJob = null
        _isSpeaking.value = false
    }

    /**
     * Release all resources.
     *
     * Call when done using this service.
     */
    fun release() {
        stopSpeaking()
        scope.cancel()
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        soundsLoaded = false
    }

    // -- Private: TTS --

    /**
     * Synthesize and play text using the configured TTS provider.
     */
    private suspend fun speakWithTTS(text: String) {
        val service =
            ttsService ?: run {
                Log.w(TAG, "No TTS service configured, cannot speak: $text")
                return
            }

        _isSpeaking.value = true
        try {
            service.synthesize(text).toList()
            // Audio playback is handled by the TTS service pipeline;
            // we just need to collect the flow to completion.
        } catch (e: Exception) {
            Log.w(TAG, "TTS announcement failed: ${e.message}")
        } finally {
            _isSpeaking.value = false
        }
    }

    // -- Public API: Tone Loading --

    /**
     * Load a tone from a raw resource ID.
     *
     * Call this to register audio files for each [FeedbackTone].
     * If no tones are loaded, [playTone] will only trigger haptic feedback.
     *
     * @param tone The feedback tone to associate with this resource
     * @param resourceId Raw resource ID (e.g., R.raw.tone_correct)
     */
    fun loadTone(
        tone: FeedbackTone,
        resourceId: Int,
    ) {
        try {
            val id = soundPool?.load(context, resourceId, 1)
            if (id != null && id > 0) {
                soundIds[tone] = id
                Log.d(TAG, "Loaded tone: $tone")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to load tone for $tone: ${e.message}")
        }
    }

    // -- Private: Sound Setup --

    private fun setupSoundPool() {
        val attributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        soundPool =
            SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attributes)
                .build()
                .also { pool ->
                    pool.setOnLoadCompleteListener { _, _, status ->
                        if (status == 0) {
                            soundsLoaded = true
                            Log.d(TAG, "Sound pool loaded")
                        }
                    }
                }
    }

    // -- Private: Haptics --

    private fun playToneHaptic(tone: FeedbackTone) {
        if (!_hapticsEnabled.value) return

        when (tone) {
            FeedbackTone.COMMAND_RECOGNIZED -> selectionHaptic()
            FeedbackTone.COMMAND_INVALID -> notificationHaptic(NotificationType.WARNING)
            FeedbackTone.COUNTDOWN_TICK -> impactHaptic(amplitude = IMPACT_LIGHT_HALF)
            FeedbackTone.CORRECT -> notificationHaptic(NotificationType.SUCCESS)
            FeedbackTone.INCORRECT -> notificationHaptic(NotificationType.ERROR)
            FeedbackTone.ATTENTION -> impactHaptic(amplitude = IMPACT_HEAVY)
        }
    }

    private fun vibrate(
        duration: Long,
        amplitude: Int,
    ) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(duration, amplitude)
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(duration)
        }
    }

    private fun vibratePattern(
        timings: LongArray,
        amplitudes: IntArray,
    ) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(timings, -1)
        }
    }

    /**
     * Notification haptic types matching iOS UINotificationFeedbackGenerator.
     */
    enum class NotificationType {
        /** Positive outcome (e.g., correct answer). */
        SUCCESS,

        /** Caution or attention needed. */
        WARNING,

        /** Negative outcome (e.g., incorrect answer). */
        ERROR,
    }
}

// -- Vibration amplitude constants --

/** Light impact amplitude (iOS UIImpactFeedbackGenerator.style.light). */
private const val IMPACT_LIGHT = 40

/** Half of light impact for subtle ticks. */
private const val IMPACT_LIGHT_HALF = 80

/** Medium impact amplitude (iOS UIImpactFeedbackGenerator.style.medium). */
private const val IMPACT_MEDIUM = 128

/** Heavy impact amplitude (iOS UIImpactFeedbackGenerator.style.heavy). */
private const val IMPACT_HEAVY = 255

/**
 * Obtain the system Vibrator service, using VibratorManager on API 31+.
 */
private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
