package com.unamentis.core.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Unit tests for AudioUtils.
 *
 * Tests audio processing utilities including RMS calculation,
 * peak detection, normalization, and format conversion.
 */
class AudioUtilsTest {
    @Test
    fun `calculateRMS returns zero for empty array`() {
        val rms = AudioUtils.calculateRMS(floatArrayOf())
        assertEquals(0f, rms, 0.001f)
    }

    @Test
    fun `calculateRMS returns correct value for constant signal`() {
        // Constant signal at 0.5 amplitude
        val samples = FloatArray(100) { 0.5f }
        val rms = AudioUtils.calculateRMS(samples)

        // RMS of constant signal equals the constant value
        assertEquals(0.5f, rms, 0.001f)
    }

    @Test
    fun `calculateRMS returns correct value for sine wave`() {
        // Generate one period of sine wave at 0.707 peak amplitude
        val samples =
            FloatArray(1000) { i ->
                (0.707 * sin(2 * Math.PI * i / 1000.0)).toFloat()
            }
        val rms = AudioUtils.calculateRMS(samples)

        // RMS of sine wave is peak / sqrt(2) ≈ 0.707 / 1.414 ≈ 0.5
        assertEquals(0.5f, rms, 0.01f)
    }

    @Test
    fun `calculatePeak returns zero for empty array`() {
        val peak = AudioUtils.calculatePeak(floatArrayOf())
        assertEquals(0f, peak, 0.001f)
    }

    @Test
    fun `calculatePeak returns correct value for mixed signals`() {
        val samples = floatArrayOf(0.1f, -0.5f, 0.3f, -0.9f, 0.2f)
        val peak = AudioUtils.calculatePeak(samples)

        assertEquals(0.9f, peak, 0.001f)
    }

    @Test
    fun `rmsToDecibels returns negative infinity for zero`() {
        val db = AudioUtils.rmsToDecibels(0f)
        assertEquals(Float.NEGATIVE_INFINITY, db, 0.001f)
    }

    @Test
    fun `rmsToDecibels returns zero for full scale`() {
        val db = AudioUtils.rmsToDecibels(1.0f)
        assertEquals(0f, db, 0.001f)
    }

    @Test
    fun `rmsToDecibels returns minus 6dB for half amplitude`() {
        val db = AudioUtils.rmsToDecibels(0.5f)
        assertEquals(-6.02f, db, 0.1f)
    }

    @Test
    fun `applyGain increases amplitude correctly`() {
        val samples = floatArrayOf(0.1f, 0.2f, 0.3f)
        val original = samples.clone()

        // Apply +6dB gain (2x amplitude)
        AudioUtils.applyGain(samples, 6.0f)

        for (i in samples.indices) {
            assertEquals(original[i] * 2.0f, samples[i], 0.01f)
        }
    }

    @Test
    fun `applyGain decreases amplitude correctly`() {
        val samples = floatArrayOf(0.4f, 0.6f, 0.8f)
        val original = samples.clone()

        // Apply -6dB gain (0.5x amplitude)
        AudioUtils.applyGain(samples, -6.0f)

        for (i in samples.indices) {
            assertEquals(original[i] * 0.5f, samples[i], 0.01f)
        }
    }

    @Test
    fun `normalize adjusts RMS to target level`() {
        val samples = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        AudioUtils.normalize(samples, targetRms = 0.5f)

        val normalizedRms = AudioUtils.calculateRMS(samples)
        assertEquals(0.5f, normalizedRms, 0.01f)
    }

    @Test
    fun `pcmToFloat converts correctly`() {
        val pcm = shortArrayOf(0, 16384, -16384, 32767, -32768)
        val floatSamples = AudioUtils.pcmToFloat(pcm)

        assertEquals(0.0f, floatSamples[0], 0.001f)
        assertEquals(0.5f, floatSamples[1], 0.001f)
        assertEquals(-0.5f, floatSamples[2], 0.001f)
        assertEquals(1.0f, floatSamples[3], 0.001f)
        assertEquals(-1.0f, floatSamples[4], 0.001f)
    }

    @Test
    fun `floatToPcm converts correctly`() {
        val floatSamples = floatArrayOf(0.0f, 0.5f, -0.5f, 1.0f, -1.0f)
        val pcm = AudioUtils.floatToPcm(floatSamples)

        assertEquals(0.toShort(), pcm[0])
        assertEquals(16383.toShort(), pcm[1])
        assertEquals((-16383).toShort(), pcm[2])
        assertEquals(32767.toShort(), pcm[3])
        assertEquals((-32767).toShort(), pcm[4])
    }

    @Test
    fun `floatToPcm clamps out-of-range values`() {
        val floatSamples = floatArrayOf(2.0f, -2.0f)
        val pcm = AudioUtils.floatToPcm(floatSamples)

        // Should clamp to valid range
        assertEquals(32767.toShort(), pcm[0])
        assertEquals((-32767).toShort(), pcm[1])
    }

    @Test
    fun `isSilence detects quiet audio`() {
        val quietSamples = FloatArray(100) { 0.001f }
        val isSilent = AudioUtils.isSilence(quietSamples, threshold = 0.01f)

        assertTrue(isSilent)
    }

    @Test
    fun `isSilence detects loud audio`() {
        val loudSamples = FloatArray(100) { 0.5f }
        val isSilent = AudioUtils.isSilence(loudSamples, threshold = 0.01f)

        assertFalse(isSilent)
    }

    @Test
    fun `pcm to float and back preserves values`() {
        val original = shortArrayOf(100, -200, 300, -400, 500)
        val floatSamples = AudioUtils.pcmToFloat(original)
        val converted = AudioUtils.floatToPcm(floatSamples)

        for (i in original.indices) {
            // Allow small rounding error
            assertTrue(abs(original[i] - converted[i]) <= 1)
        }
    }
}
