package com.unamentis.services.stt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ONNX Runtime inference manager for GLM-ASR models.
 *
 * Manages three ONNX models that form the encoder pipeline:
 * 1. Whisper Encoder - Converts mel spectrograms to audio features
 * 2. Audio Adapter - Aligns audio features with LLM embedding space
 * 3. Embed Head - Produces final token embeddings for the decoder
 *
 * Input/Output shapes:
 * - Whisper Encoder: [1, 128, nFrames] -> [1, 1500, 1280]
 * - Audio Adapter: [1, 1500, 1280] -> [1, 375, 2048]
 * - Embed Head: [1, 375, 2048] -> [1, 375, 4096]
 *
 * Thread Safety:
 * - Model loading/unloading should be done from a single thread
 * - Inference is thread-safe per session
 *
 * @property useGPU Whether to attempt GPU acceleration
 * @property numThreads Number of CPU threads for inference
 */
@Suppress("UnusedPrivateProperty") // useGPU will be used when GPU execution provider is added
class GLMASRONNXInference(
    private val useGPU: Boolean = true,
    private val numThreads: Int = 4,
) {
    companion object {
        private const val TAG = "GLMASRONNXInference"

        // Expected output dimensions
        private const val ENCODER_OUTPUT_TOKENS = 1500
        private const val ENCODER_OUTPUT_DIM = 1280
        private const val ADAPTER_OUTPUT_TOKENS = 375
        private const val ADAPTER_OUTPUT_DIM = 2048
        private const val EMBED_OUTPUT_DIM = 4096
    }

    // ONNX Runtime environment (shared singleton)
    private var ortEnvironment: OrtEnvironment? = null

    // ONNX sessions for each model
    private var whisperEncoderSession: OrtSession? = null
    private var audioAdapterSession: OrtSession? = null
    private var embedHeadSession: OrtSession? = null

    // Loading state
    private val isLoaded = AtomicBoolean(false)

    // Inference counters for logging
    private var inferenceCount = 0

    /**
     * Check if all models are loaded.
     */
    fun isLoaded(): Boolean = isLoaded.get()

    /**
     * Load all ONNX models for the encoder pipeline.
     *
     * @param whisperEncoderPath Path to Whisper encoder ONNX model
     * @param audioAdapterPath Path to audio adapter ONNX model
     * @param embedHeadPath Path to embed head ONNX model
     * @return true if all models loaded successfully
     */
    fun loadModels(
        whisperEncoderPath: File,
        audioAdapterPath: File,
        embedHeadPath: File,
    ): Boolean {
        if (isLoaded.get()) {
            Log.d(TAG, "Models already loaded")
            return true
        }

        try {
            // Get ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()
            val env = ortEnvironment ?: return false

            // Create session options
            val sessionOptions = createSessionOptions()

            // Load Whisper encoder
            Log.i(TAG, "Loading Whisper encoder from ${whisperEncoderPath.absolutePath}")
            if (!whisperEncoderPath.exists()) {
                Log.e(TAG, "Whisper encoder not found: ${whisperEncoderPath.absolutePath}")
                return false
            }
            val encoderBytes = whisperEncoderPath.readBytes()
            whisperEncoderSession = env.createSession(encoderBytes, sessionOptions)
            logModelInfo("WhisperEncoder", whisperEncoderSession!!)

            // Load audio adapter
            Log.i(TAG, "Loading audio adapter from ${audioAdapterPath.absolutePath}")
            if (!audioAdapterPath.exists()) {
                Log.e(TAG, "Audio adapter not found: ${audioAdapterPath.absolutePath}")
                release()
                return false
            }
            val adapterBytes = audioAdapterPath.readBytes()
            audioAdapterSession = env.createSession(adapterBytes, sessionOptions)
            logModelInfo("AudioAdapter", audioAdapterSession!!)

            // Load embed head
            Log.i(TAG, "Loading embed head from ${embedHeadPath.absolutePath}")
            if (!embedHeadPath.exists()) {
                Log.e(TAG, "Embed head not found: ${embedHeadPath.absolutePath}")
                release()
                return false
            }
            val embedBytes = embedHeadPath.readBytes()
            embedHeadSession = env.createSession(embedBytes, sessionOptions)
            logModelInfo("EmbedHead", embedHeadSession!!)

            isLoaded.set(true)
            Log.i(TAG, "All ONNX models loaded successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX models", e)
            release()
            return false
        }
    }

    /**
     * Run Whisper encoder on mel spectrogram.
     *
     * @param melSpectrogram Flattened mel spectrogram [nMels, nFrames]
     * @param nMels Number of mel bands (128)
     * @param nFrames Number of time frames
     * @return Encoded audio features [1500 * 1280], or null on error
     */
    fun runWhisperEncoder(
        melSpectrogram: FloatArray,
        nMels: Int,
        nFrames: Int,
    ): FloatArray? {
        val session = whisperEncoderSession
        val env = ortEnvironment

        if (session == null || env == null) {
            Log.e(TAG, "Whisper encoder not loaded")
            return null
        }

        return try {
            // Input shape: [1, nMels, nFrames]
            val inputShape = longArrayOf(1, nMels.toLong(), nFrames.toLong())
            val inputTensor =
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(melSpectrogram),
                    inputShape,
                )

            // Log tensor info periodically
            if (inferenceCount % 10 == 0) {
                Log.d(TAG, "Whisper encoder input shape: ${inputShape.contentToString()}")
            }

            // Run inference
            val inputs = mapOf("input" to inputTensor)
            val results = session.run(inputs)

            // Extract output
            val outputTensor = results.iterator().next().value as OnnxTensor
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            val output = FloatArray(outputSize)
            outputBuffer.get(output)

            // Log output info periodically
            if (inferenceCount % 10 == 0) {
                Log.d(
                    TAG,
                    "Whisper encoder output: shape=${outputTensor.info.shape.contentToString()}, " +
                        "size=$outputSize",
                )
            }

            // Clean up
            inputTensor.close()
            results.close()

            output
        } catch (e: Exception) {
            Log.e(TAG, "Whisper encoder inference failed", e)
            null
        }
    }

    /**
     * Run audio adapter to align features with LLM embedding space.
     *
     * @param encodedAudio Whisper encoder output [1500 * 1280]
     * @return Adapted features [375 * 2048], or null on error
     */
    fun runAudioAdapter(encodedAudio: FloatArray): FloatArray? {
        val session = audioAdapterSession
        val env = ortEnvironment

        if (session == null || env == null) {
            Log.e(TAG, "Audio adapter not loaded")
            return null
        }

        return try {
            // Input shape: [1, 1500, 1280]
            val inputShape =
                longArrayOf(
                    1,
                    ENCODER_OUTPUT_TOKENS.toLong(),
                    ENCODER_OUTPUT_DIM.toLong(),
                )
            val inputTensor =
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(encodedAudio),
                    inputShape,
                )

            // Run inference
            val inputs = mapOf("input" to inputTensor)
            val results = session.run(inputs)

            // Extract output
            val outputTensor = results.iterator().next().value as OnnxTensor
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            val output = FloatArray(outputSize)
            outputBuffer.get(output)

            // Log output info periodically
            if (inferenceCount % 10 == 0) {
                Log.d(
                    TAG,
                    "Audio adapter output: shape=${outputTensor.info.shape.contentToString()}, " +
                        "size=$outputSize",
                )
            }

            // Clean up
            inputTensor.close()
            results.close()

            output
        } catch (e: Exception) {
            Log.e(TAG, "Audio adapter inference failed", e)
            null
        }
    }

    /**
     * Run embed head to produce token embeddings.
     *
     * @param adaptedFeatures Audio adapter output [375 * 2048]
     * @return Token embeddings [375 * 4096], or null on error
     */
    fun runEmbedHead(adaptedFeatures: FloatArray): FloatArray? {
        val session = embedHeadSession
        val env = ortEnvironment

        if (session == null || env == null) {
            Log.e(TAG, "Embed head not loaded")
            return null
        }

        return try {
            // Input shape: [1, 375, 2048]
            val inputShape =
                longArrayOf(
                    1,
                    ADAPTER_OUTPUT_TOKENS.toLong(),
                    ADAPTER_OUTPUT_DIM.toLong(),
                )
            val inputTensor =
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(adaptedFeatures),
                    inputShape,
                )

            // Run inference
            val inputs = mapOf("input" to inputTensor)
            val results = session.run(inputs)

            // Extract output
            val outputTensor = results.iterator().next().value as OnnxTensor
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            val output = FloatArray(outputSize)
            outputBuffer.get(output)

            // Log output info periodically
            if (inferenceCount % 10 == 0) {
                Log.d(
                    TAG,
                    "Embed head output: shape=${outputTensor.info.shape.contentToString()}, " +
                        "size=$outputSize",
                )
            }

            inferenceCount++

            // Clean up
            inputTensor.close()
            results.close()

            output
        } catch (e: Exception) {
            Log.e(TAG, "Embed head inference failed", e)
            null
        }
    }

    /**
     * Run the full encoder pipeline.
     *
     * @param melSpectrogram Flattened mel spectrogram
     * @param nMels Number of mel bands
     * @param nFrames Number of time frames
     * @return Token embeddings for decoder, or null on error
     */
    @Suppress("ReturnCount") // Pipeline stages have early returns on failure
    fun runEncoderPipeline(
        melSpectrogram: FloatArray,
        nMels: Int,
        nFrames: Int,
    ): FloatArray? {
        // Step 1: Whisper encoder
        val encodedAudio = runWhisperEncoder(melSpectrogram, nMels, nFrames)
        if (encodedAudio == null) {
            Log.e(TAG, "Whisper encoder failed")
            return null
        }

        // Step 2: Audio adapter
        val adaptedFeatures = runAudioAdapter(encodedAudio)
        if (adaptedFeatures == null) {
            Log.e(TAG, "Audio adapter failed")
            return null
        }

        // Step 3: Embed head
        val embeddings = runEmbedHead(adaptedFeatures)
        if (embeddings == null) {
            Log.e(TAG, "Embed head failed")
            return null
        }

        return embeddings
    }

    /**
     * Release all ONNX resources.
     */
    fun release() {
        try {
            whisperEncoderSession?.close()
            whisperEncoderSession = null

            audioAdapterSession?.close()
            audioAdapterSession = null

            embedHeadSession?.close()
            embedHeadSession = null

            // Note: OrtEnvironment is a singleton and should not be closed
            ortEnvironment = null

            isLoaded.set(false)
            inferenceCount = 0

            Log.i(TAG, "ONNX resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ONNX resources", e)
        }
    }

    /**
     * Create session options with optimizations.
     */
    private fun createSessionOptions(): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            // Enable all optimizations
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

            // Set number of CPU threads
            setIntraOpNumThreads(numThreads)

            // Note: GPU execution provider setup would go here
            // if (useGPU) {
            //     // NNAPI or GPU execution provider
            //     addNnapi()
            // }
        }
    }

    /**
     * Log model input/output information.
     */
    private fun logModelInfo(
        modelName: String,
        session: OrtSession,
    ) {
        val inputNames = session.inputNames.joinToString()
        val outputNames = session.outputNames.joinToString()
        Log.i(TAG, "$modelName inputs: $inputNames")
        Log.i(TAG, "$modelName outputs: $outputNames")
    }

    /**
     * Get expected output dimensions for each model stage.
     */
    object OutputDimensions {
        const val ENCODER_TOKENS = ENCODER_OUTPUT_TOKENS
        const val ENCODER_DIM = ENCODER_OUTPUT_DIM
        const val ADAPTER_TOKENS = ADAPTER_OUTPUT_TOKENS
        const val ADAPTER_DIM = ADAPTER_OUTPUT_DIM
        const val EMBED_DIM = EMBED_OUTPUT_DIM
    }
}
