package com.unamentis.core.audio

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AudioEngine.
 *
 * Note: These tests verify the Kotlin API surface and state management.
 * Native code functionality would require instrumentation tests on device.
 */
class AudioEngineTest {
    private lateinit var audioEngine: AudioEngine

    @Before
    fun setup() {
        audioEngine = AudioEngine()
    }

    @After
    fun teardown() {
        audioEngine.release()
    }

    @Test
    fun `initial state is not capturing`() =
        runTest {
            val isCapturing = audioEngine.isCapturing.first()
            assertFalse(isCapturing)
        }

    @Test
    fun `initial state is not playing`() =
        runTest {
            val isPlaying = audioEngine.isPlaying.first()
            assertFalse(isPlaying)
        }

    @Test
    fun `initial audio level is zero`() =
        runTest {
            val level = audioEngine.audioLevel.first()
            assertEquals(0f, level.rms, 0.001f)
            assertEquals(0f, level.peak, 0.001f)
        }

    @Test
    fun `updateAudioLevel calculates RMS correctly`() =
        runTest {
            val samples = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
            audioEngine.updateAudioLevel(samples)

            val level = audioEngine.audioLevel.first()
            assertEquals(0.5f, level.rms, 0.001f)
        }

    @Test
    fun `updateAudioLevel calculates peak correctly`() =
        runTest {
            val samples = floatArrayOf(0.1f, -0.9f, 0.3f, -0.5f)
            audioEngine.updateAudioLevel(samples)

            val level = audioEngine.audioLevel.first()
            assertEquals(0.9f, level.peak, 0.001f)
        }

    @Test
    fun `updateAudioLevel handles empty array`() =
        runTest {
            audioEngine.updateAudioLevel(floatArrayOf())

            // Should not crash, level stays at zero
            val level = audioEngine.audioLevel.first()
            assertEquals(0f, level.rms, 0.001f)
        }

    @Test
    fun `AudioConfig has correct defaults`() {
        val config = AudioConfig()

        assertEquals(16000, config.sampleRate)
        assertEquals(1, config.channelCount)
        assertEquals(192, config.framesPerBurst)
    }

    @Test
    fun `AudioConfig can be customized`() {
        val config =
            AudioConfig(
                sampleRate = 48000,
                channelCount = 2,
                framesPerBurst = 256,
            )

        assertEquals(48000, config.sampleRate)
        assertEquals(2, config.channelCount)
        assertEquals(256, config.framesPerBurst)
    }
}
