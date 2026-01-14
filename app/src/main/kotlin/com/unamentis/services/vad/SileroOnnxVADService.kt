package com.unamentis.services.vad

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.unamentis.data.model.VADResult
import com.unamentis.data.model.VADService
import java.nio.FloatBuffer

/**
 * Silero VAD implementation using ONNX Runtime.
 *
 * This is the recommended VAD implementation for Android, using the official
 * Silero VAD v5 ONNX model for high-accuracy voice activity detection.
 *
 * Model specifications (Silero VAD v5):
 * - Input: Audio chunks (512 samples at 16kHz = 32ms frames)
 * - Inputs: "input" (audio), "state" (hidden state), "sr" (sample rate)
 * - Outputs: "output" (probability), "stateN" (next hidden state)
 *
 * @property context Application context for loading model from assets
 * @property threshold Speech detection threshold (default: 0.5)
 */
class SileroOnnxVADService(
    private val context: Context,
    private val threshold: Float = 0.5f,
) : VADService {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // State tensor for Silero VAD v5 - shape: [2, 1, 128]
    // 2 layers, batch size 1, 128 hidden units
    private var state: FloatArray = FloatArray(2 * 1 * 128)

    // Sample rate (required by Silero VAD v5)
    private val sampleRate: Long = 16000L

    // Audio buffer to accumulate samples until we have FRAME_SIZE
    private val audioBuffer = FloatArray(FRAME_SIZE)
    private var bufferPosition = 0

    // Last VAD result (returned when buffer isn't full yet)
    private var lastResult = VADResult(isSpeech = false, confidence = 0f)

    // Counter for periodic logging
    private var inferenceCount = 0

    companion object {
        private const val MODEL_FILENAME = "silero_vad.onnx"
        private const val FRAME_SIZE = 512 // 32ms at 16kHz
        private const val TAG = "SileroOnnxVAD"
    }

    /**
     * Initialize the VAD service by loading the ONNX model.
     *
     * @throws IllegalStateException if model file is not found or fails to load
     */
    fun initialize() {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load model from assets
            val modelBytes = context.assets.open(MODEL_FILENAME).use { it.readBytes() }

            // Create session options
            val sessionOptions =
                OrtSession.SessionOptions().apply {
                    // Enable optimizations
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Use single thread for inference (VAD is lightweight)
                    setIntraOpNumThreads(1)
                }

            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)

            // Log model input/output names for debugging
            ortSession?.let { session ->
                val inputNames = session.inputNames.joinToString()
                val outputNames = session.outputNames.joinToString()
                android.util.Log.i(TAG, "Model inputs: $inputNames")
                android.util.Log.i(TAG, "Model outputs: $outputNames")
            }

            // Reset state
            resetStates()

            android.util.Log.i(TAG, "Silero VAD (ONNX) loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load ONNX model", e)
            throw IllegalStateException("Failed to initialize Silero VAD (ONNX): ${e.message}", e)
        }
    }

    /**
     * Reset the hidden state and audio buffer.
     * Call this when starting a new audio stream/session.
     */
    fun resetStates() {
        state = FloatArray(2 * 1 * 128)
        bufferPosition = 0
        lastResult = VADResult(isSpeech = false, confidence = 0f)
    }

    /**
     * Process audio samples and detect speech.
     *
     * This method buffers incoming audio until we have FRAME_SIZE (512) samples,
     * then runs VAD inference. Returns the last result while buffering.
     *
     * @param samples Audio samples (float, -1.0 to 1.0). Any size is accepted.
     * @return VAD result with speech detection and confidence
     */
    override fun processAudio(samples: FloatArray): VADResult {
        val session = ortSession
        val env = ortEnvironment

        if (session == null || env == null) {
            android.util.Log.w(TAG, "VAD not initialized, returning no speech")
            return VADResult(isSpeech = false, confidence = 0f)
        }

        // Add samples to buffer
        var samplesOffset = 0
        while (samplesOffset < samples.size) {
            val samplesToAdd = minOf(samples.size - samplesOffset, FRAME_SIZE - bufferPosition)
            System.arraycopy(samples, samplesOffset, audioBuffer, bufferPosition, samplesToAdd)
            bufferPosition += samplesToAdd
            samplesOffset += samplesToAdd

            // Process when buffer is full
            if (bufferPosition >= FRAME_SIZE) {
                lastResult = processFullBuffer(session, env)
                bufferPosition = 0
            }
        }

        return lastResult
    }

    /**
     * Process a full 512-sample buffer through the VAD model.
     */
    private fun processFullBuffer(
        session: OrtSession,
        env: OrtEnvironment,
    ): VADResult {
        return try {
            // Prepare input tensors for Silero VAD v5

            // Audio input: shape [1, 512]
            val inputShape = longArrayOf(1, FRAME_SIZE.toLong())
            val inputTensor =
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(audioBuffer),
                    inputShape,
                )

            // State tensor: shape [2, 1, 128]
            val stateShape = longArrayOf(2, 1, 128)
            val stateTensor =
                OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(state),
                    stateShape,
                )

            // Sample rate tensor: shape [1]
            val srTensor =
                OnnxTensor.createTensor(
                    env,
                    longArrayOf(sampleRate),
                )

            // Run inference with correct input names for v5
            val inputs =
                mapOf(
                    "input" to inputTensor,
                    "state" to stateTensor,
                    "sr" to srTensor,
                )

            val results = session.run(inputs)

            // Extract outputs
            // Output: speech probability
            val outputTensor = results.get("output").get() as OnnxTensor
            val probability = outputTensor.floatBuffer.get(0)

            // Update state for next call
            val stateNTensor = results.get("stateN").get() as OnnxTensor
            stateNTensor.floatBuffer.get(state)

            // Clean up tensors
            inputTensor.close()
            stateTensor.close()
            srTensor.close()
            results.close()

            inferenceCount++
            val isSpeech = probability > threshold

            // Log periodically or when speech detected
            if (isSpeech || inferenceCount % 100 == 0) {
                android.util.Log.d(TAG, "VAD: isSpeech=$isSpeech, confidence=${"%.4f".format(probability)}")
            }
            VADResult(
                isSpeech = isSpeech,
                confidence = probability,
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Inference failed", e)
            e.printStackTrace()
            VADResult(isSpeech = false, confidence = 0f)
        }
    }

    /**
     * Release VAD resources.
     */
    override fun release() {
        try {
            ortSession?.close()
            ortSession = null
            // Note: OrtEnvironment is a singleton and should not be closed
            ortEnvironment = null
            resetStates()
            android.util.Log.i(TAG, "Silero VAD (ONNX) released")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error releasing VAD resources", e)
        }
    }
}
