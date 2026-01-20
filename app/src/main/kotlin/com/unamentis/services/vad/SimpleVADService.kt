package com.unamentis.services.vad

import com.unamentis.data.model.VADResult
import com.unamentis.data.model.VADService
import kotlin.math.sqrt

/**
 * Simple amplitude-based Voice Activity Detection.
 *
 * This is a lightweight fallback VAD that uses RMS amplitude and
 * zero-crossing rate to detect speech. It's less accurate than
 * neural network-based VAD but works without external dependencies.
 *
 * Detection algorithm:
 * 1. Calculate RMS amplitude of the frame
 * 2. Calculate zero-crossing rate (speech has characteristic ZCR)
 * 3. Apply adaptive threshold based on noise floor
 *
 * @property threshold RMS threshold for speech detection (0.0 - 1.0)
 * @property hangoverFrames Number of frames to keep speech state after drop
 */
class SimpleVADService(
    private val threshold: Float = 0.02f,
    private val hangoverFrames: Int = 5,
) : VADService {
    private var noiseFloor: Float = 0.01f
    private var speechFrameCount: Int = 0
    private var hangoverCount: Int = 0
    private var initialized: Boolean = false

    companion object {
        private const val NOISE_ADAPT_RATE = 0.001f
        private const val MIN_SPEECH_FRAMES = 3
    }

    /**
     * Initialize the VAD service.
     * For SimpleVAD, this just sets the initialized flag.
     */
    fun initialize() {
        noiseFloor = 0.01f
        speechFrameCount = 0
        hangoverCount = 0
        initialized = true
        android.util.Log.i("SimpleVAD", "Simple VAD initialized (amplitude-based)")
    }

    /**
     * Process audio samples and detect speech.
     *
     * @param samples Audio samples (float, -1.0 to 1.0)
     * @return VAD result with speech detection and confidence
     */
    override fun processAudio(samples: FloatArray): VADResult {
        if (!initialized) {
            initialize()
        }

        if (samples.isEmpty()) {
            return VADResult(isSpeech = false, confidence = 0f)
        }

        // Calculate RMS amplitude
        val rms = calculateRMS(samples)

        // Calculate zero-crossing rate (helpful for distinguishing speech from noise)
        val zcr = calculateZeroCrossingRate(samples)

        // Adaptive noise floor estimation (update during silence)
        val dynamicThreshold = noiseFloor * 3f + threshold

        // Speech detection with hysteresis
        val isCurrentlySpeech = rms > dynamicThreshold && zcr < 0.5f

        if (isCurrentlySpeech) {
            speechFrameCount++
            hangoverCount = hangoverFrames
        } else {
            // Update noise floor during silence
            if (speechFrameCount == 0) {
                noiseFloor = noiseFloor * (1 - NOISE_ADAPT_RATE) + rms * NOISE_ADAPT_RATE
            }

            if (hangoverCount > 0) {
                hangoverCount--
            } else {
                speechFrameCount = 0
            }
        }

        // Require minimum speech frames to confirm speech
        val confirmedSpeech = speechFrameCount >= MIN_SPEECH_FRAMES || hangoverCount > 0

        // Calculate confidence based on how far above threshold
        val confidence =
            if (confirmedSpeech) {
                ((rms - noiseFloor) / (dynamicThreshold - noiseFloor + 0.001f)).coerceIn(0.5f, 1f)
            } else {
                (rms / dynamicThreshold).coerceIn(0f, 0.5f)
            }

        return VADResult(
            isSpeech = confirmedSpeech,
            confidence = confidence,
        )
    }

    /**
     * Release VAD resources.
     */
    override fun release() {
        initialized = false
        android.util.Log.i("SimpleVAD", "Simple VAD released")
    }

    /**
     * Calculate Root Mean Square amplitude.
     */
    private fun calculateRMS(samples: FloatArray): Float {
        var sumSquares = 0f
        for (sample in samples) {
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / samples.size)
    }

    /**
     * Calculate zero-crossing rate.
     * Speech typically has ZCR between 0.1 and 0.4.
     * Noise tends to have higher ZCR.
     */
    private fun calculateZeroCrossingRate(samples: FloatArray): Float {
        if (samples.size < 2) return 0f

        var crossings = 0
        for (i in 1 until samples.size) {
            val isZeroCrossing = isSignChange(samples[i - 1], samples[i])
            if (isZeroCrossing) {
                crossings++
            }
        }
        return crossings.toFloat() / (samples.size - 1)
    }

    /**
     * Check if there is a sign change between two consecutive samples.
     */
    private fun isSignChange(
        previous: Float,
        current: Float,
    ): Boolean {
        val crossingPositiveToNegative = current >= 0 && previous < 0
        val crossingNegativeToPositive = current < 0 && previous >= 0
        return crossingPositiveToNegative || crossingNegativeToPositive
    }
}
