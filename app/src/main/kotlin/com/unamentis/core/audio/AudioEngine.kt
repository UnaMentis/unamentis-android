package com.unamentis.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio engine configuration.
 *
 * @property sampleRate Sample rate in Hz (default: 16000 for STT compatibility)
 * @property channelCount Number of audio channels (default: 1 for mono)
 * @property framesPerBurst Frames per audio burst (default: 192, ~12ms at 16kHz)
 */
data class AudioConfig(
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val framesPerBurst: Int = 192,
)

/**
 * Audio level information for visualization.
 *
 * @property rms Root Mean Square amplitude (0.0 - 1.0)
 * @property peak Peak amplitude in current frame (0.0 - 1.0)
 */
data class AudioLevel(
    val rms: Float = 0f,
    val peak: Float = 0f,
)

/**
 * Low-latency audio engine for voice conversations.
 *
 * This class provides a Kotlin interface to the native audio engine,
 * handling audio capture and playback with minimal latency.
 *
 * Features:
 * - Low-latency audio I/O via native code
 * - Real-time audio level monitoring
 * - Configurable sample rate and buffer size
 * - Thread-safe operation
 *
 * Usage:
 * ```kotlin
 * val engine = AudioEngine()
 * engine.initialize(AudioConfig())
 * engine.startCapture { audioData ->
 *     // Process captured audio
 * }
 * ```
 */
class AudioEngine {
    private var nativeEnginePtr: Long = 0
    private var captureCallback: ((FloatArray) -> Unit)? = null

    private val _audioLevel = MutableStateFlow(AudioLevel())
    val audioLevel: StateFlow<AudioLevel> = _audioLevel.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    companion object {
        init {
            try {
                System.loadLibrary("audio_engine")
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("AudioEngine", "Failed to load native library", e)
            }
        }
    }

    /**
     * Initialize the audio engine with configuration.
     *
     * @param config Audio configuration
     * @return true if initialization succeeded
     */
    fun initialize(config: AudioConfig = AudioConfig()): Boolean {
        if (nativeEnginePtr != 0L) {
            android.util.Log.w("AudioEngine", "Engine already initialized")
            return true
        }

        nativeEnginePtr = nativeCreate()
        if (nativeEnginePtr == 0L) {
            android.util.Log.e("AudioEngine", "Failed to create native engine")
            return false
        }

        val success =
            nativeInitialize(
                nativeEnginePtr,
                config.sampleRate,
                config.channelCount,
                config.framesPerBurst,
            )

        if (!success) {
            nativeDestroy(nativeEnginePtr)
            nativeEnginePtr = 0
        }

        return success
    }

    /**
     * Start capturing audio.
     *
     * @param callback Callback invoked with captured audio data
     * @return true if capture started successfully
     */
    fun startCapture(callback: (FloatArray) -> Unit): Boolean {
        if (nativeEnginePtr == 0L) {
            android.util.Log.e("AudioEngine", "Engine not initialized")
            return false
        }

        if (_isCapturing.value) {
            android.util.Log.w("AudioEngine", "Already capturing")
            return false
        }

        captureCallback = callback
        val success = nativeStartCapture(nativeEnginePtr)

        if (success) {
            _isCapturing.value = true
        }

        return success
    }

    /**
     * Stop capturing audio.
     */
    fun stopCapture() {
        if (nativeEnginePtr == 0L || !_isCapturing.value) {
            return
        }

        nativeStopCapture(nativeEnginePtr)
        _isCapturing.value = false
        captureCallback = null
        _audioLevel.value = AudioLevel()
    }

    /**
     * Queue audio data for playback.
     *
     * @param audioData Audio samples (float, -1.0 to 1.0)
     * @return true if data was queued successfully
     */
    fun queuePlayback(audioData: FloatArray): Boolean {
        if (nativeEnginePtr == 0L) {
            android.util.Log.e("AudioEngine", "Engine not initialized")
            return false
        }

        val success = nativeQueuePlayback(nativeEnginePtr, audioData)

        if (success && !_isPlaying.value) {
            _isPlaying.value = true
        }

        return success
    }

    /**
     * Stop audio playback.
     */
    fun stopPlayback() {
        if (nativeEnginePtr == 0L || !_isPlaying.value) {
            return
        }

        nativeStopPlayback(nativeEnginePtr)
        _isPlaying.value = false
    }

    /**
     * Calculate audio level from samples.
     *
     * @param samples Audio samples
     */
    fun updateAudioLevel(samples: FloatArray) {
        if (samples.isEmpty()) return

        // Calculate RMS
        var sumSquares = 0f
        var peak = 0f

        for (sample in samples) {
            val abs = kotlin.math.abs(sample)
            sumSquares += sample * sample
            if (abs > peak) peak = abs
        }

        val rms = kotlin.math.sqrt(sumSquares / samples.size)

        _audioLevel.value = AudioLevel(rms = rms, peak = peak)
    }

    /**
     * Called from native code when audio is captured.
     * This method is invoked from the native audio thread via JNI.
     * Do not call directly.
     *
     * @param audioData Captured audio samples (float, -1.0 to 1.0)
     */
    @Suppress("unused")
    fun onNativeAudioData(audioData: FloatArray) {
        updateAudioLevel(audioData)
        captureCallback?.invoke(audioData)
    }

    /**
     * Release native resources.
     */
    fun release() {
        stopCapture()
        stopPlayback()

        if (nativeEnginePtr != 0L) {
            nativeDestroy(nativeEnginePtr)
            nativeEnginePtr = 0
        }
    }

    // Native method declarations
    private external fun nativeCreate(): Long

    private external fun nativeInitialize(
        enginePtr: Long,
        sampleRate: Int,
        channelCount: Int,
        framesPerBurst: Int,
    ): Boolean

    private external fun nativeStartCapture(enginePtr: Long): Boolean

    private external fun nativeStopCapture(enginePtr: Long)

    private external fun nativeQueuePlayback(
        enginePtr: Long,
        audioData: FloatArray,
    ): Boolean

    private external fun nativeStopPlayback(enginePtr: Long)

    private external fun nativeDestroy(enginePtr: Long)
}
