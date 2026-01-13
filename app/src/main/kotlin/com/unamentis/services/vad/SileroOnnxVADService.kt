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
 * Silero VAD ONNX model for high-accuracy voice activity detection.
 *
 * Model specifications:
 * - Input: Audio chunks (512 samples at 16kHz = 32ms frames)
 * - Output: Speech probability (0.0 - 1.0)
 * - Stateful: Uses hidden states (h, c) for temporal context
 *
 * @property context Application context for loading model from assets
 * @property threshold Speech detection threshold (default: 0.5)
 */
class SileroOnnxVADService(
    private val context: Context,
    private val threshold: Float = 0.5f
) : VADService {

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Hidden states for the LSTM - Silero VAD is stateful
    private var hState: FloatArray = FloatArray(2 * 1 * 64) // 2 layers, batch 1, 64 hidden
    private var cState: FloatArray = FloatArray(2 * 1 * 64)

    // Sample rate state (required by Silero VAD v5)
    private val sampleRate: Long = 16000L

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
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Enable optimizations
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // Use single thread for inference (VAD is lightweight)
                setIntraOpNumThreads(1)
            }

            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)

            // Reset hidden states
            resetStates()

            android.util.Log.i(TAG, "Silero VAD (ONNX) loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load ONNX model", e)
            throw IllegalStateException("Failed to initialize Silero VAD (ONNX): ${e.message}", e)
        }
    }

    /**
     * Reset the LSTM hidden states.
     * Call this when starting a new audio stream/session.
     */
    fun resetStates() {
        hState = FloatArray(2 * 1 * 64)
        cState = FloatArray(2 * 1 * 64)
    }

    /**
     * Process audio samples and detect speech.
     *
     * @param samples Audio samples (float, -1.0 to 1.0). Must be 512 samples.
     * @return VAD result with speech detection and confidence
     */
    override fun processAudio(samples: FloatArray): VADResult {
        val session = ortSession
        val env = ortEnvironment

        if (session == null || env == null) {
            android.util.Log.w(TAG, "VAD not initialized, returning no speech")
            return VADResult(isSpeech = false, confidence = 0f)
        }

        if (samples.size != FRAME_SIZE) {
            android.util.Log.w(
                TAG,
                "Invalid frame size: ${samples.size}, expected $FRAME_SIZE"
            )
            return VADResult(isSpeech = false, confidence = 0f)
        }

        return try {
            // Prepare input tensors
            // Input shape: [batch_size, chunk_size] = [1, 512]
            val inputShape = longArrayOf(1, FRAME_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(samples),
                inputShape
            )

            // Sample rate tensor: scalar int64
            val srTensor = OnnxTensor.createTensor(
                env,
                longArrayOf(sampleRate)
            )

            // Hidden state tensors: [num_layers, batch_size, hidden_size] = [2, 1, 64]
            val stateShape = longArrayOf(2, 1, 64)
            val hTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(hState),
                stateShape
            )
            val cTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(cState),
                stateShape
            )

            // Run inference
            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )

            val results = session.run(inputs)

            // Extract outputs
            // Output: speech probability [1, 1]
            val outputTensor = results.get("output").get() as OnnxTensor
            val probability = outputTensor.floatBuffer.get(0)

            // Update hidden states for next call
            val hnTensor = results.get("hn").get() as OnnxTensor
            val cnTensor = results.get("cn").get() as OnnxTensor

            hnTensor.floatBuffer.get(hState)
            cnTensor.floatBuffer.get(cState)

            // Clean up tensors
            inputTensor.close()
            srTensor.close()
            hTensor.close()
            cTensor.close()
            results.close()

            VADResult(
                isSpeech = probability > threshold,
                confidence = probability
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Inference failed", e)
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
