package com.unamentis.services.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for GLMASRONNXInference.
 *
 * Tests cover:
 * - OutputDimensions constants
 * - Initial state (isLoaded returns false)
 * - Constructor parameters
 * - Behavior when models aren't loaded (inference methods return null)
 * - Release functionality
 *
 * Note: Actual ONNX inference requires native libraries that aren't available
 * in unit tests. Full inference testing is done in instrumented tests.
 */
class GLMASRONNXInferenceTest {
    // ==================== OutputDimensions Constants Tests ====================

    @Test
    fun `output dimensions constants match encoder output`() {
        assertEquals(1500, GLMASRONNXInference.OutputDimensions.ENCODER_TOKENS)
        assertEquals(1280, GLMASRONNXInference.OutputDimensions.ENCODER_DIM)
    }

    @Test
    fun `output dimensions constants match adapter output`() {
        assertEquals(375, GLMASRONNXInference.OutputDimensions.ADAPTER_TOKENS)
        assertEquals(2048, GLMASRONNXInference.OutputDimensions.ADAPTER_DIM)
    }

    @Test
    fun `output dimensions constants match embed head output`() {
        assertEquals(4096, GLMASRONNXInference.OutputDimensions.EMBED_DIM)
    }

    @Test
    fun `encoder output size is correct`() {
        val expectedSize =
            GLMASRONNXInference.OutputDimensions.ENCODER_TOKENS *
                GLMASRONNXInference.OutputDimensions.ENCODER_DIM
        assertEquals(1_920_000, expectedSize)
    }

    @Test
    fun `adapter output size is correct`() {
        val expectedSize =
            GLMASRONNXInference.OutputDimensions.ADAPTER_TOKENS *
                GLMASRONNXInference.OutputDimensions.ADAPTER_DIM
        assertEquals(768_000, expectedSize)
    }

    @Test
    fun `embed head output size is correct`() {
        val expectedSize =
            GLMASRONNXInference.OutputDimensions.ADAPTER_TOKENS *
                GLMASRONNXInference.OutputDimensions.EMBED_DIM
        assertEquals(1_536_000, expectedSize)
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `isLoaded returns false initially`() {
        val inference = GLMASRONNXInference()

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `isLoaded returns false with custom parameters`() {
        val inference = GLMASRONNXInference(useGPU = false, numThreads = 2)

        assertFalse(inference.isLoaded())
    }

    // ==================== Constructor Tests ====================

    @Test
    fun `constructor with default parameters creates instance`() {
        val inference = GLMASRONNXInference()

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `constructor with GPU disabled creates instance`() {
        val inference = GLMASRONNXInference(useGPU = false)

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `constructor with custom thread count creates instance`() {
        val inference = GLMASRONNXInference(numThreads = 8)

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `constructor with all custom parameters creates instance`() {
        val inference = GLMASRONNXInference(useGPU = false, numThreads = 1)

        assertFalse(inference.isLoaded())
    }

    // ==================== Inference Before Load Tests ====================

    @Test
    fun `runWhisperEncoder returns null when not loaded`() {
        val inference = GLMASRONNXInference()
        val melSpectrogram = FloatArray(128 * 100) // Dummy input

        val result = inference.runWhisperEncoder(melSpectrogram, nMels = 128, nFrames = 100)

        assertNull(result)
    }

    @Test
    fun `runAudioAdapter returns null when not loaded`() {
        val inference = GLMASRONNXInference()
        val expectedSize =
            GLMASRONNXInference.OutputDimensions.ENCODER_TOKENS *
                GLMASRONNXInference.OutputDimensions.ENCODER_DIM
        val encodedAudio = FloatArray(expectedSize)

        val result = inference.runAudioAdapter(encodedAudio)

        assertNull(result)
    }

    @Test
    fun `runEmbedHead returns null when not loaded`() {
        val inference = GLMASRONNXInference()
        val expectedSize =
            GLMASRONNXInference.OutputDimensions.ADAPTER_TOKENS *
                GLMASRONNXInference.OutputDimensions.ADAPTER_DIM
        val adaptedFeatures = FloatArray(expectedSize)

        val result = inference.runEmbedHead(adaptedFeatures)

        assertNull(result)
    }

    @Test
    fun `runEncoderPipeline returns null when not loaded`() {
        val inference = GLMASRONNXInference()
        val melSpectrogram = FloatArray(128 * 100)

        val result = inference.runEncoderPipeline(melSpectrogram, nMels = 128, nFrames = 100)

        assertNull(result)
    }

    // ==================== Release Tests ====================

    @Test
    fun `release on unloaded instance does not crash`() {
        val inference = GLMASRONNXInference()

        // Should not throw
        inference.release()

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `release can be called multiple times`() {
        val inference = GLMASRONNXInference()

        // Should not throw on multiple releases
        inference.release()
        inference.release()
        inference.release()

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `isLoaded returns false after release`() {
        val inference = GLMASRONNXInference()

        inference.release()

        assertFalse(inference.isLoaded())
    }

    @Test
    fun `inference methods return null after release`() {
        val inference = GLMASRONNXInference()
        inference.release()

        val melResult = inference.runWhisperEncoder(FloatArray(128 * 100), 128, 100)
        val adapterResult = inference.runAudioAdapter(FloatArray(1_920_000))
        val embedResult = inference.runEmbedHead(FloatArray(768_000))
        val pipelineResult = inference.runEncoderPipeline(FloatArray(128 * 100), 128, 100)

        assertNull(melResult)
        assertNull(adapterResult)
        assertNull(embedResult)
        assertNull(pipelineResult)
    }

    // ==================== Dimension Relationship Tests ====================

    @Test
    fun `encoder output feeds into adapter input`() {
        // Verify dimensional compatibility between stages
        val encoderOutputTokens = GLMASRONNXInference.OutputDimensions.ENCODER_TOKENS
        val encoderOutputDim = GLMASRONNXInference.OutputDimensions.ENCODER_DIM

        // Audio adapter expects [1, 1500, 1280] input
        assertEquals(1500, encoderOutputTokens)
        assertEquals(1280, encoderOutputDim)
    }

    @Test
    fun `adapter output feeds into embed head input`() {
        // Verify dimensional compatibility between stages
        val adapterOutputTokens = GLMASRONNXInference.OutputDimensions.ADAPTER_TOKENS
        val adapterOutputDim = GLMASRONNXInference.OutputDimensions.ADAPTER_DIM

        // Embed head expects [1, 375, 2048] input
        assertEquals(375, adapterOutputTokens)
        assertEquals(2048, adapterOutputDim)
    }

    @Test
    fun `embed head output matches llama embedding dimension`() {
        // GLM-ASR decoder expects 4096-dim embeddings
        val embedDim = GLMASRONNXInference.OutputDimensions.EMBED_DIM
        assertEquals(4096, embedDim)
    }

    @Test
    fun `final output token count is reduced from encoder`() {
        // Pipeline reduces 1500 encoder tokens to 375 output tokens (4x reduction)
        val encoderTokens = GLMASRONNXInference.OutputDimensions.ENCODER_TOKENS
        val outputTokens = GLMASRONNXInference.OutputDimensions.ADAPTER_TOKENS

        assertEquals(4, encoderTokens / outputTokens)
    }
}
