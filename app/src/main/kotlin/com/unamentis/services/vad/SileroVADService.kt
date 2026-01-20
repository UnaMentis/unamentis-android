package com.unamentis.services.vad

import android.content.Context
import com.unamentis.data.model.VADResult
import com.unamentis.data.model.VADService
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Silero VAD implementation using TensorFlow Lite.
 *
 * Silero VAD is a robust voice activity detection model that works well
 * in various noise conditions. This implementation uses the TensorFlow Lite
 * version of the model for on-device inference.
 *
 * Model specifications:
 * - Input: 512 samples at 16kHz (32ms frames)
 * - Output: Speech probability (0.0 - 1.0)
 * - Frame rate: ~31 frames/second
 *
 * @property context Application context for loading model from assets
 * @property threshold Speech detection threshold (default: 0.5)
 * @property useNnapi Whether to use NNAPI acceleration (default: true)
 */
class SileroVADService(
    private val context: Context,
    private val threshold: Float = 0.5f,
    private val useNnapi: Boolean = true,
) : VADService {
    private var interpreter: Interpreter? = null
    private val inputBuffer =
        ByteBuffer.allocateDirect(512 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
    private val outputBuffer =
        ByteBuffer.allocateDirect(1 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

    companion object {
        private const val MODEL_FILENAME = "silero_vad.tflite"
        private const val FRAME_SIZE = 512 // 32ms at 16kHz
    }

    /**
     * Initialize the VAD service by loading the TFLite model.
     *
     * @throws IllegalStateException if model file is not found
     */
    fun initialize() {
        try {
            val modelBuffer = loadModelFile()
            val options =
                Interpreter.Options().apply {
                    setNumThreads(2)
                    if (useNnapi) {
                        // Enable NNAPI acceleration if available
                        try {
                            setUseNNAPI(true)
                        } catch (e: Exception) {
                            android.util.Log.w("SileroVAD", "NNAPI not available", e)
                        }
                    }
                }

            interpreter = Interpreter(modelBuffer, options)
            android.util.Log.i("SileroVAD", "Model loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e("SileroVAD", "Failed to load model", e)
            throw IllegalStateException("Failed to initialize Silero VAD: ${e.message}", e)
        }
    }

    /**
     * Process audio samples and detect speech.
     *
     * @param samples Audio samples (16-bit PCM, 16kHz). Must be 512 samples.
     * @return VAD result with speech detection and confidence
     */
    override fun processAudio(samples: FloatArray): VADResult {
        if (interpreter == null) {
            android.util.Log.w("SileroVAD", "VAD not initialized, returning no speech")
            return VADResult(isSpeech = false, confidence = 0f)
        }

        if (samples.size != FRAME_SIZE) {
            android.util.Log.w(
                "SileroVAD",
                "Invalid frame size: ${samples.size}, expected $FRAME_SIZE",
            )
            return VADResult(isSpeech = false, confidence = 0f)
        }

        // Prepare input buffer
        inputBuffer.rewind()
        for (sample in samples) {
            inputBuffer.putFloat(sample)
        }

        // Prepare output buffer
        outputBuffer.rewind()

        // Run inference
        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            android.util.Log.e("SileroVAD", "Inference failed", e)
            return VADResult(isSpeech = false, confidence = 0f)
        }

        // Extract result
        outputBuffer.rewind()
        val probability = outputBuffer.float

        return VADResult(
            isSpeech = probability > threshold,
            confidence = probability,
        )
    }

    /**
     * Release VAD resources.
     */
    override fun release() {
        interpreter?.close()
        interpreter = null
        android.util.Log.i("SileroVAD", "VAD released")
    }

    /**
     * Load the TFLite model from assets.
     *
     * @return Memory-mapped model buffer
     */
    private fun loadModelFile(): MappedByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd(MODEL_FILENAME)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Model file '$MODEL_FILENAME' not found in assets. " +
                    "Please download and add the Silero VAD TFLite model.",
                e,
            )
        }
    }
}
