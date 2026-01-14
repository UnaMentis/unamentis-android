package com.unamentis.core.audio

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Utility functions for audio processing.
 */
object AudioUtils {
    /**
     * Calculate Root Mean Square (RMS) amplitude of audio samples.
     *
     * RMS provides a measure of the average power of the audio signal.
     *
     * @param samples Audio samples (normalized -1.0 to 1.0)
     * @return RMS amplitude (0.0 to 1.0)
     */
    fun calculateRMS(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f

        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += sample * sample
        }

        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Calculate peak amplitude of audio samples.
     *
     * @param samples Audio samples (normalized -1.0 to 1.0)
     * @return Peak amplitude (0.0 to 1.0)
     */
    fun calculatePeak(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f

        var peak = 0f
        for (sample in samples) {
            val abs = kotlin.math.abs(sample)
            if (abs > peak) peak = abs
        }

        return peak
    }

    /**
     * Convert RMS amplitude to decibels.
     *
     * @param rms RMS amplitude (0.0 to 1.0)
     * @param referenceLevel Reference level (default: 1.0)
     * @return Amplitude in decibels (negative values, -∞ to 0 dB)
     */
    fun rmsToDecibels(
        rms: Float,
        referenceLevel: Float = 1.0f,
    ): Float {
        if (rms <= 0f) return Float.NEGATIVE_INFINITY
        return 20 * kotlin.math.log10(rms / referenceLevel)
    }

    /**
     * Apply gain to audio samples in-place.
     *
     * @param samples Audio samples to modify
     * @param gainDb Gain in decibels
     */
    fun applyGain(
        samples: FloatArray,
        gainDb: Float,
    ) {
        val gainLinear = 10.0.pow(gainDb / 20.0).toFloat()
        for (i in samples.indices) {
            samples[i] *= gainLinear
        }
    }

    /**
     * Normalize audio samples to a target RMS level.
     *
     * @param samples Audio samples to modify
     * @param targetRms Target RMS level (0.0 to 1.0)
     */
    fun normalize(
        samples: FloatArray,
        targetRms: Float = 0.5f,
    ) {
        val currentRms = calculateRMS(samples)
        if (currentRms > 0f) {
            val gain = targetRms / currentRms
            for (i in samples.indices) {
                samples[i] *= gain
            }
        }
    }

    /**
     * Convert 16-bit PCM samples to float (-1.0 to 1.0).
     *
     * @param pcm 16-bit PCM samples
     * @return Float samples normalized to -1.0 to 1.0
     */
    fun pcmToFloat(pcm: ShortArray): FloatArray {
        val floatSamples = FloatArray(pcm.size)
        for (i in pcm.indices) {
            floatSamples[i] = pcm[i] / 32768.0f
        }
        return floatSamples
    }

    /**
     * Convert float samples to 16-bit PCM.
     *
     * @param floatSamples Float samples (-1.0 to 1.0)
     * @return 16-bit PCM samples
     */
    fun floatToPcm(floatSamples: FloatArray): ShortArray {
        val pcm = ShortArray(floatSamples.size)
        for (i in floatSamples.indices) {
            val clamped = floatSamples[i].coerceIn(-1.0f, 1.0f)
            pcm[i] = (clamped * 32767.0f).toInt().toShort()
        }
        return pcm
    }

    /**
     * Detect silence in audio samples.
     *
     * @param samples Audio samples
     * @param threshold RMS threshold for silence (default: 0.01)
     * @return true if samples are below silence threshold
     */
    fun isSilence(
        samples: FloatArray,
        threshold: Float = 0.01f,
    ): Boolean {
        return calculateRMS(samples) < threshold
    }
}
