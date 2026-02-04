package com.unamentis.services.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unit tests for GLM-ASR On-Device STT components.
 *
 * Tests cover:
 * - GLMASROnDeviceConfig - configuration class
 * - GLMASRMelSpectrogram - audio preprocessing
 *
 * Note: GLMASROnDeviceSTTService requires Android context and native libraries,
 * so full service tests are done as instrumented tests.
 */
class GLMASROnDeviceSTTServiceTest {
    // ==================== GLMASROnDeviceConfig Tests ====================

    @Test
    fun `config has correct default values`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        assertEquals(30.0f, config.maxAudioDurationSeconds, 0.01f)
        assertTrue(config.useGPU)
        assertEquals(99, config.gpuLayers)
        assertEquals(4, config.numThreads)
        assertEquals("auto", config.language)

        tempDir.deleteRecursively()
    }

    @Test
    fun `config model paths are correct`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        assertEquals(
            tempDir.resolve(GLMASROnDeviceConfig.MODEL_WHISPER_ENCODER),
            config.whisperEncoderPath,
        )
        assertEquals(
            tempDir.resolve(GLMASROnDeviceConfig.MODEL_AUDIO_ADAPTER),
            config.audioAdapterPath,
        )
        assertEquals(
            tempDir.resolve(GLMASROnDeviceConfig.MODEL_EMBED_HEAD),
            config.embedHeadPath,
        )
        assertEquals(
            tempDir.resolve(GLMASROnDeviceConfig.MODEL_DECODER),
            config.decoderPath,
        )

        tempDir.deleteRecursively()
    }

    @Test
    fun `areModelsPresent returns false when models missing`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        assertFalse(config.areModelsPresent())

        tempDir.deleteRecursively()
    }

    @Test
    fun `areModelsPresent returns true when all models exist`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        // Create dummy model files
        config.whisperEncoderPath.writeText("dummy")
        config.audioAdapterPath.writeText("dummy")
        config.embedHeadPath.writeText("dummy")
        config.decoderPath.writeText("dummy")

        assertTrue(config.areModelsPresent())

        tempDir.deleteRecursively()
    }

    @Test
    fun `getMissingModels returns correct list`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        // Create some but not all model files
        config.whisperEncoderPath.writeText("dummy")
        config.audioAdapterPath.writeText("dummy")

        val missing = config.getMissingModels()

        assertEquals(2, missing.size)
        assertTrue(missing.contains(GLMASROnDeviceConfig.MODEL_EMBED_HEAD))
        assertTrue(missing.contains(GLMASROnDeviceConfig.MODEL_DECODER))
        assertFalse(missing.contains(GLMASROnDeviceConfig.MODEL_WHISPER_ENCODER))
        assertFalse(missing.contains(GLMASROnDeviceConfig.MODEL_AUDIO_ADAPTER))

        tempDir.deleteRecursively()
    }

    @Test
    fun `getTotalModelSize returns -1 when models missing`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        assertEquals(-1L, config.getTotalModelSize())

        tempDir.deleteRecursively()
    }

    @Test
    fun `getTotalModelSize returns correct sum when models exist`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig(modelDirectory = tempDir)

        // Create model files with known sizes
        val content1 = ByteArray(100) { 0 }
        val content2 = ByteArray(200) { 0 }
        val content3 = ByteArray(300) { 0 }
        val content4 = ByteArray(400) { 0 }

        config.whisperEncoderPath.writeBytes(content1)
        config.audioAdapterPath.writeBytes(content2)
        config.embedHeadPath.writeBytes(content3)
        config.decoderPath.writeBytes(content4)

        assertEquals(1000L, config.getTotalModelSize())

        tempDir.deleteRecursively()
    }

    @Test
    fun `low memory config has reduced settings`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig.lowMemory(tempDir)

        assertFalse(config.useGPU)
        assertEquals(0, config.gpuLayers)
        assertEquals(2, config.numThreads)
        assertEquals(15.0f, config.maxAudioDurationSeconds, 0.01f)

        tempDir.deleteRecursively()
    }

    @Test
    fun `high performance config has optimal settings`() {
        val tempDir = createTempDirectory("glm-asr-test")
        val config = GLMASROnDeviceConfig.highPerformance(tempDir, cpuCores = 8)

        assertTrue(config.useGPU)
        assertEquals(99, config.gpuLayers)
        assertEquals(6, config.numThreads) // max(4, 8-2) = 6
        assertEquals(30.0f, config.maxAudioDurationSeconds, 0.01f)

        tempDir.deleteRecursively()
    }

    @Test
    fun `config companion object has correct constants`() {
        assertEquals("glm_asr_whisper_encoder.onnx", GLMASROnDeviceConfig.MODEL_WHISPER_ENCODER)
        assertEquals("glm_asr_audio_adapter.onnx", GLMASROnDeviceConfig.MODEL_AUDIO_ADAPTER)
        assertEquals("glm_asr_embed_head.onnx", GLMASROnDeviceConfig.MODEL_EMBED_HEAD)
        assertEquals("glm-asr-nano-q4km.gguf", GLMASROnDeviceConfig.MODEL_DECODER)
        assertEquals(16000, GLMASROnDeviceConfig.SAMPLE_RATE)
        assertEquals(400, GLMASROnDeviceConfig.N_FFT)
        assertEquals(160, GLMASROnDeviceConfig.HOP_LENGTH)
        assertEquals(128, GLMASROnDeviceConfig.N_MELS)
    }

    @Test
    fun `config size constants are reasonable`() {
        // Whisper encoder ~1.2GB
        assertTrue(GLMASROnDeviceConfig.SIZE_WHISPER_ENCODER > 1_000_000_000L)
        assertTrue(GLMASROnDeviceConfig.SIZE_WHISPER_ENCODER < 2_000_000_000L)

        // Audio adapter ~56MB
        assertTrue(GLMASROnDeviceConfig.SIZE_AUDIO_ADAPTER > 50_000_000L)
        assertTrue(GLMASROnDeviceConfig.SIZE_AUDIO_ADAPTER < 100_000_000L)

        // Total ~2.4GB
        val total = GLMASROnDeviceConfig.SIZE_TOTAL
        assertTrue(total > 2_000_000_000L)
        assertTrue(total < 3_000_000_000L)
    }

    // ==================== GLMASRMelSpectrogram Tests ====================

    private lateinit var melSpectrogram: GLMASRMelSpectrogram

    @Before
    fun setupMelSpectrogram() {
        melSpectrogram = GLMASRMelSpectrogram()
    }

    @Test
    fun `mel spectrogram has correct output dimensions`() {
        // 1 second of audio at 16kHz
        val samples = FloatArray(16000) { 0f }

        val result = melSpectrogram.compute(samples)

        // Should have 128 mel bands
        assertEquals(128, result.size)

        // Number of frames = (16000 - 400) / 160 + 1 = 98
        // With maxFrames = 3000, should be 98
        val expectedFrames = (16000 - 400) / 160 + 1
        assertEquals(expectedFrames, result[0].size)
    }

    @Test
    fun `mel spectrogram handles empty input`() {
        val samples = FloatArray(0)

        val result = melSpectrogram.compute(samples)

        assertEquals(128, result.size) // Still 128 mel bands
        assertEquals(0, result[0].size) // But 0 frames
    }

    @Test
    fun `mel spectrogram handles short input`() {
        // Input shorter than FFT window (400 samples)
        val samples = FloatArray(200) { 0f }

        val result = melSpectrogram.compute(samples)

        assertEquals(128, result.size)
        assertEquals(0, result[0].size) // Not enough samples for a frame
    }

    @Test
    fun `mel spectrogram produces finite values`() {
        // Generate sine wave test signal
        val frequency = 440f // A4 note
        val samples =
            FloatArray(16000) { i ->
                sin(2.0 * PI * frequency * i / 16000.0).toFloat()
            }

        val result = melSpectrogram.compute(samples)

        // Check all values are finite (not NaN or Infinity)
        for (mel in 0 until 128) {
            for (frame in 0 until result[mel].size) {
                assertTrue(
                    "Value at [$mel][$frame] should be finite",
                    result[mel][frame].isFinite(),
                )
            }
        }
    }

    @Test
    fun `mel spectrogram computeFlat produces correct shape`() {
        val samples = FloatArray(16000) { 0f }

        val result = melSpectrogram.computeFlat(samples)

        val expectedFrames = (16000 - 400) / 160 + 1
        assertEquals(128 * expectedFrames, result.size)
    }

    @Test
    fun `mel spectrogram computeFlat handles empty input`() {
        val samples = FloatArray(0)

        val result = melSpectrogram.computeFlat(samples)

        assertEquals(0, result.size)
    }

    @Test
    fun `getOutputShape returns correct dimensions`() {
        val (nMels, nFrames) = melSpectrogram.getOutputShape(16000)

        assertEquals(128, nMels)
        assertEquals((16000 - 400) / 160 + 1, nFrames)
    }

    @Test
    fun `getOutputShape returns zero frames for short input`() {
        val (nMels, nFrames) = melSpectrogram.getOutputShape(200)

        assertEquals(128, nMels)
        assertEquals(0, nFrames)
    }

    @Test
    fun `getSamplesForFrames returns correct value`() {
        val numFrames = 100
        val samples = melSpectrogram.getSamplesForFrames(numFrames)

        // samples = (numFrames - 1) * hopLength + nFFT
        // = (100 - 1) * 160 + 400 = 99 * 160 + 400 = 15840 + 400 = 16240
        assertEquals(16240, samples)
    }

    @Test
    fun `mel spectrogram respects maxFrames parameter`() {
        // 2 seconds of audio would give ~198 frames
        val samples = FloatArray(32000) { 0f }

        val result = melSpectrogram.compute(samples, maxFrames = 50)

        assertEquals(128, result.size)
        assertEquals(50, result[0].size) // Limited to 50 frames
    }

    @Test
    fun `mel spectrogram differentiates silence from signal`() {
        // Silent audio
        val silence = FloatArray(16000) { 0f }

        // Loud sine wave
        val signal =
            FloatArray(16000) { i ->
                (sin(2.0 * PI * 440.0 * i / 16000.0) * 0.9).toFloat()
            }

        val silenceSpec = melSpectrogram.compute(silence)
        val signalSpec = melSpectrogram.compute(signal)

        // Signal should have higher energy (less negative log values)
        var silenceSum = 0.0
        var signalSum = 0.0

        for (mel in 0 until 128) {
            for (frame in 0 until minOf(silenceSpec[mel].size, signalSpec[mel].size)) {
                silenceSum += silenceSpec[mel][frame]
                signalSum += signalSpec[mel][frame]
            }
        }

        // Signal should have higher (less negative) average values
        assertTrue(
            "Signal should have higher energy than silence",
            signalSum > silenceSum,
        )
    }

    @Test
    fun `mel spectrogram shows frequency response for different tones`() {
        // Low frequency tone (200 Hz)
        val lowTone =
            FloatArray(16000) { i ->
                sin(2.0 * PI * 200.0 * i / 16000.0).toFloat()
            }

        // High frequency tone (4000 Hz)
        val highTone =
            FloatArray(16000) { i ->
                sin(2.0 * PI * 4000.0 * i / 16000.0).toFloat()
            }

        val lowSpec = melSpectrogram.compute(lowTone)
        val highSpec = melSpectrogram.compute(highTone)

        // Find which mel bins have highest energy for each tone
        fun findPeakMelBin(spec: Array<FloatArray>): Int {
            var maxEnergy = Float.NEGATIVE_INFINITY
            var peakBin = 0
            for (mel in 0 until 128) {
                val avgEnergy = spec[mel].average().toFloat()
                if (avgEnergy > maxEnergy) {
                    maxEnergy = avgEnergy
                    peakBin = mel
                }
            }
            return peakBin
        }

        val lowPeak = findPeakMelBin(lowSpec)
        val highPeak = findPeakMelBin(highSpec)

        // Higher frequency should activate higher mel bins
        assertTrue(
            "High tone should peak in higher mel bin than low tone",
            highPeak > lowPeak,
        )
    }

    // ==================== Helper Functions ====================

    private fun createTempDirectory(prefix: String): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "$prefix-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir
    }
}
