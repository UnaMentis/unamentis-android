package com.unamentis.services.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.unamentis.R
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaPipe LLM Inference service for GPU-accelerated inference.
 *
 * Uses Google's MediaPipe LLM Inference API with OpenCL GPU acceleration.
 * Supports Gemma 2B and other compatible models in .task format.
 *
 * Performance:
 * - High-end devices: 20-50 tok/sec
 * - Mid-range devices: 15-25 tok/sec
 *
 * Benefits:
 * - GPU acceleration via OpenCL
 * - Works on most Android devices with OpenCL support
 * - Google-maintained, stable API
 * - Simple integration via Gradle
 *
 * Requirements:
 * - Model file in .task or .litertlm format
 * - OpenCL support on device
 */
@Singleton
class MediaPipeLLMService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LLMBackend {
        companion object {
            private const val TAG = "MediaPipeLLM"

            // Model filename (downloaded via ModelDownloadManager)
            const val GEMMA_2B_FILENAME = "gemma-2b-it-gpu-int4.task"

            // Default configuration
            private const val DEFAULT_MAX_TOKENS = 512
            private const val MAX_TTFT_MEASUREMENTS = 100
        }

        override val providerName: String = context.getString(R.string.provider_mediapipe)
        override val backendName: String = "MediaPipe (GPU)"
        override val expectedToksPerSec: Int = 25

        // MediaPipe LLM Inference instance
        private var llmInference: LlmInference? = null
        private val isModelLoadedAtomic = AtomicBoolean(false)

        override val isLoaded: Boolean
            get() = isModelLoadedAtomic.get() && llmInference != null

        @Volatile
        private var currentModelPath: String? = null

        // Metrics tracking
        private val totalInputTokens = AtomicInteger(0)
        private val totalOutputTokens = AtomicInteger(0)
        private val ttftMeasurements = CopyOnWriteArrayList<Long>()
        private val toksPerSecMeasurements = CopyOnWriteArrayList<Float>()

        /**
         * Load model from the specified path.
         */
        override suspend fun loadModel(): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val modelPath = getAvailableModelPath()
                    if (modelPath == null) {
                        Log.e(TAG, "No MediaPipe model available")
                        return@withContext Result.failure(
                            IllegalStateException("No MediaPipe model available. Please download Gemma 2B first."),
                        )
                    }

                    if (isLoaded && currentModelPath == modelPath) {
                        Log.d(TAG, "Model already loaded: $modelPath")
                        return@withContext Result.success(Unit)
                    }

                    // Unload existing model if different
                    if (isLoaded) {
                        unloadModel()
                    }

                    Log.i(TAG, "Loading MediaPipe model: $modelPath")

                    val options =
                        LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelPath)
                            .setMaxTokens(DEFAULT_MAX_TOKENS)
                            .build()

                    llmInference = LlmInference.createFromOptions(context, options)

                    isModelLoadedAtomic.set(true)
                    currentModelPath = modelPath
                    Log.i(TAG, "MediaPipe model loaded successfully")

                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load MediaPipe model", e)
                    Result.failure(e)
                }
            }

        /**
         * Unload model and release resources.
         */
        override fun unloadModel() {
            try {
                llmInference?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing MediaPipe inference", e)
            }
            llmInference = null
            isModelLoadedAtomic.set(false)
            currentModelPath = null
            Log.i(TAG, "MediaPipe model unloaded")
        }

        /**
         * Get available model path.
         */
        fun getAvailableModelPath(): String? {
            val modelsDir = getModelsDirectory()
            val gemma = File(modelsDir, GEMMA_2B_FILENAME)
            if (gemma.exists()) {
                Log.d(TAG, "Found Gemma 2B model: ${gemma.absolutePath}")
                return gemma.absolutePath
            }

            Log.w(TAG, "No MediaPipe models found in: ${modelsDir.absolutePath}")
            return null
        }

        /**
         * Get models directory.
         */
        fun getModelsDirectory(): File {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val modelsDir = File(baseDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            return modelsDir
        }

        override fun streamCompletion(
            messages: List<LLMMessage>,
            temperature: Float,
            maxTokens: Int,
        ): Flow<LLMToken> =
            callbackFlow {
                // Auto-load model if not loaded
                if (!isLoaded) {
                    val result = loadModel()
                    if (result.isFailure) {
                        close(result.exceptionOrNull() ?: IllegalStateException("Failed to load model"))
                        return@callbackFlow
                    }
                }

                val inference = llmInference
                if (inference == null) {
                    close(IllegalStateException("MediaPipe inference not initialized"))
                    return@callbackFlow
                }

                val prompt = formatPrompt(messages)
                val startTime = System.currentTimeMillis()
                var firstTokenEmitted = false
                var generatedTokens = 0

                Log.d(TAG, "Starting MediaPipe async generation with ${prompt.length} char prompt")

                // Estimate input tokens (~4 chars per token)
                totalInputTokens.addAndGet(prompt.length / 4)

                try {
                    inference.generateResponseAsync(prompt) { partialResult, done ->
                        // Record TTFT on first non-empty partial result
                        if (!firstTokenEmitted && partialResult.isNotEmpty()) {
                            firstTokenEmitted = true
                            val ttft = System.currentTimeMillis() - startTime
                            if (ttftMeasurements.size >= MAX_TTFT_MEASUREMENTS) {
                                ttftMeasurements.removeAt(0)
                            }
                            ttftMeasurements.add(ttft)
                            Log.i(TAG, "TTFT: ${ttft}ms")
                        }

                        if (partialResult.isNotEmpty()) {
                            // Estimate token count (~4 chars per token)
                            val chunkTokens = partialResult.length / 4
                            generatedTokens += chunkTokens
                            totalOutputTokens.addAndGet(chunkTokens)
                            trySend(LLMToken(content = partialResult, isDone = false))
                        }

                        if (done) {
                            // Calculate tokens per second
                            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
                            if (elapsedSec > 0 && generatedTokens > 0) {
                                val toksPerSec = generatedTokens / elapsedSec
                                if (toksPerSecMeasurements.size >= MAX_TTFT_MEASUREMENTS) {
                                    toksPerSecMeasurements.removeAt(0)
                                }
                                toksPerSecMeasurements.add(toksPerSec)
                                Log.i(TAG, "Generation speed: ${toksPerSec.toInt()} tok/sec")
                            }

                            trySend(LLMToken(content = "", isDone = true))
                            Log.i(TAG, "Generation complete: $generatedTokens tokens")
                            close()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPipe generation error", e)
                    close(e)
                }

                awaitClose {
                    Log.d(TAG, "Flow closed")
                }
            }.flowOn(Dispatchers.IO)

        /**
         * Request generation stop. Note: This is a no-op because MediaPipe's synchronous
         * generation API does not support mid-generation cancellation. The current generation
         * will run to completion. Callers should not rely on this for immediate cancellation.
         */
        override suspend fun stop() {
            Log.d(TAG, "Stop requested (MediaPipe will complete current generation)")
        }

        /**
         * Format messages into prompt for Gemma.
         */
        private fun formatPrompt(messages: List<LLMMessage>): String {
            val sb = StringBuilder()
            var systemPrefix = ""

            for (message in messages) {
                when (message.role) {
                    "system" -> {
                        // Accumulate system content to prepend to first user message
                        systemPrefix += message.content + "\n\n"
                    }
                    "user" -> {
                        sb.append("<start_of_turn>user\n$systemPrefix${message.content}<end_of_turn>\n")
                        systemPrefix = ""
                    }
                    "assistant" -> {
                        sb.append("<start_of_turn>model\n${message.content}<end_of_turn>\n")
                    }
                }
            }

            // Add model turn prompt
            sb.append("<start_of_turn>model\n")
            return sb.toString()
        }

        /**
         * Note: TTFT metrics represent total generation latency for MediaPipe since it uses
         * synchronous generation. Compare with caution against streaming backends.
         */
        override fun getMetrics(): LLMBackendMetrics {
            val sortedTTFT = ttftMeasurements.toList().sorted()
            val medianTTFT =
                if (sortedTTFT.isNotEmpty()) {
                    sortedTTFT[sortedTTFT.size / 2]
                } else {
                    0L
                }
            val p99TTFT =
                if (sortedTTFT.isNotEmpty()) {
                    val p99Index =
                        (sortedTTFT.size * 0.99).toInt()
                            .coerceAtMost(sortedTTFT.size - 1)
                    sortedTTFT[p99Index]
                } else {
                    0L
                }

            val avgToksPerSec =
                if (toksPerSecMeasurements.isNotEmpty()) {
                    toksPerSecMeasurements.average().toFloat()
                } else {
                    0f
                }

            return LLMBackendMetrics(
                totalInputTokens = totalInputTokens.get(),
                totalOutputTokens = totalOutputTokens.get(),
                medianTTFT = medianTTFT,
                p99TTFT = p99TTFT,
                averageToksPerSec = avgToksPerSec,
            )
        }

        /**
         * Clear all metrics.
         */
        fun clearMetrics() {
            totalInputTokens.set(0)
            totalOutputTokens.set(0)
            ttftMeasurements.clear()
            toksPerSecMeasurements.clear()
        }

        /**
         * Check if MediaPipe LLM Inference is available on this device.
         *
         * Note: Classpath availability of the MediaPipe LLM library is checked by
         * [LLMBackendSelector.isMediaPipeAvailable] via reflection before this class is
         * instantiated, avoiding classloading failures from the direct LlmInference import.
         * This method only checks whether a compatible model file exists.
         */
        fun isAvailable(): Boolean {
            // Check if a model file exists
            val modelPath = getAvailableModelPath()
            if (modelPath == null) {
                Log.w(TAG, "MediaPipe model file not found - backend not available")
                return false
            }

            Log.d(TAG, "MediaPipe available with model: $modelPath")
            return true
        }
    }
