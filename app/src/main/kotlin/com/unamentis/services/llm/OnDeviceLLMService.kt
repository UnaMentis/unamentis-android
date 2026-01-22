package com.unamentis.services.llm

import android.content.Context
import android.util.Log
import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.LLMToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM service using llama.cpp (b7263+ API).
 *
 * Provides fully offline inference with:
 * - Ministral-3B-Instruct-Q4_K_M (primary, ~2.1GB)
 * - TinyLlama-1.1B-Chat-Q4_K_M (fallback, ~670MB)
 *
 * Benefits:
 * - Free (no API costs)
 * - Works offline
 * - Privacy-preserving (data never leaves device)
 * - Low latency for short responses
 *
 * Matching iOS implementation for feature parity.
 */
@Suppress("TooManyFunctions")
@Singleton
class OnDeviceLLMService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LLMService {
        companion object {
            private const val TAG = "OnDeviceLLM"

            // Model specifications
            const val MINISTRAL_3B_FILENAME = "ministral-3b-instruct-q4_k_m.gguf"
            const val TINYLLAMA_1B_FILENAME = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

            // Default configuration
            private const val DEFAULT_CONTEXT_SIZE = 4096
            private const val DEFAULT_GPU_LAYERS = 99 // All layers to GPU
            private const val DEFAULT_MAX_TOKENS = 512

            init {
                try {
                    System.loadLibrary("llama_inference")
                    Log.i(TAG, "Native library loaded successfully")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Failed to load native library", e)
                }
            }
        }

        /**
         * Configuration for model loading.
         */
        data class ModelConfig(
            val modelPath: String,
            val contextSize: Int = DEFAULT_CONTEXT_SIZE,
            val gpuLayers: Int = DEFAULT_GPU_LAYERS,
        )

        override val providerName: String = "OnDevice"

        // Native context pointer (0 = not loaded)
        private var nativeContextPtr: Long = 0

        // Current model state
        private var isModelLoaded: Boolean = false
        private var currentModelPath: String? = null

        // Metrics tracking (matching iOS)
        private var totalInputTokens: Int = 0
        private var totalOutputTokens: Int = 0
        private val ttftMeasurements = mutableListOf<Long>()

        /**
         * Load model from specified path.
         *
         * @param config Model configuration
         * @return true if model loaded successfully
         */
        suspend fun loadModel(config: ModelConfig): Boolean =
            withContext(Dispatchers.IO) {
                if (isModelLoaded && currentModelPath == config.modelPath) {
                    Log.d(TAG, "Model already loaded: ${config.modelPath}")
                    return@withContext true
                }

                // Unload existing model if different
                if (isModelLoaded) {
                    unloadModel()
                }

                val modelFile = File(config.modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file not found: ${config.modelPath}")
                    return@withContext false
                }

                Log.i(TAG, "Loading model: ${config.modelPath}")
                val optimalThreads = getOptimalThreadCount()

                nativeContextPtr =
                    nativeLoadModel(
                        config.modelPath,
                        config.contextSize,
                        config.gpuLayers,
                        optimalThreads,
                    )

                if (nativeContextPtr == 0L) {
                    Log.e(TAG, "Failed to load model")
                    return@withContext false
                }

                isModelLoaded = true
                currentModelPath = config.modelPath
                Log.i(TAG, "Model loaded successfully with $optimalThreads threads")
                true
            }

        /**
         * Unload model and free resources.
         */
        fun unloadModel() {
            if (nativeContextPtr != 0L) {
                nativeFreeModel(nativeContextPtr)
                nativeContextPtr = 0
                isModelLoaded = false
                currentModelPath = null
                Log.i(TAG, "Model unloaded")
            }
        }

        /**
         * Check if model is loaded.
         */
        fun isLoaded(): Boolean = isModelLoaded && nativeContextPtr != 0L

        /**
         * Get available model path (Ministral first, then TinyLlama).
         */
        fun getAvailableModelPath(): String? {
            val modelsDir = getModelsDirectory()

            // Try Ministral first (primary)
            val ministral = File(modelsDir, MINISTRAL_3B_FILENAME)
            if (ministral.exists()) {
                Log.d(TAG, "Found Ministral model: ${ministral.absolutePath}")
                return ministral.absolutePath
            }

            // Fall back to TinyLlama
            val tinyllama = File(modelsDir, TINYLLAMA_1B_FILENAME)
            if (tinyllama.exists()) {
                Log.d(TAG, "Found TinyLlama model: ${tinyllama.absolutePath}")
                return tinyllama.absolutePath
            }

            Log.w(TAG, "No models found in: ${modelsDir.absolutePath}")
            return null
        }

        /**
         * Get the models directory.
         */
        fun getModelsDirectory(): File {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
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
                if (!isModelLoaded) {
                    val modelPath = getAvailableModelPath()
                    if (modelPath == null) {
                        Log.e(TAG, "No model available for on-device inference")
                        close(IllegalStateException("No on-device model available. Please download a model first."))
                        return@callbackFlow
                    }

                    val loaded = loadModel(ModelConfig(modelPath))
                    if (!loaded) {
                        close(IllegalStateException("Failed to load on-device model"))
                        return@callbackFlow
                    }
                }

                val prompt = formatPrompt(messages)
                val startTime = System.currentTimeMillis()
                var firstTokenEmitted = false
                var generatedTokens = 0

                Log.d(TAG, "Starting generation with ${prompt.length} char prompt")

                // Start native generation with callback
                nativeStartGeneration(
                    nativeContextPtr,
                    prompt,
                    maxTokens.coerceAtMost(DEFAULT_MAX_TOKENS),
                    temperature,
                ) { content, isDone ->
                    // Record TTFT (time to first token)
                    if (!firstTokenEmitted && content.isNotEmpty()) {
                        val ttft = System.currentTimeMillis() - startTime
                        ttftMeasurements.add(ttft)
                        firstTokenEmitted = true
                        Log.i(TAG, "TTFT: ${ttft}ms")
                    }

                    if (content.isNotEmpty()) {
                        generatedTokens++
                    }

                    trySend(LLMToken(content = content, isDone = isDone))

                    if (isDone) {
                        totalOutputTokens += generatedTokens
                        Log.i(TAG, "Generation complete: $generatedTokens tokens")
                        close()
                    }
                }

                // Estimate input tokens (~4 chars per token)
                totalInputTokens += prompt.length / 4

                awaitClose {
                    Log.d(TAG, "Flow closed, stopping generation")
                    nativeStopGeneration(nativeContextPtr)
                }
            }.flowOn(Dispatchers.IO)

        override suspend fun stop() {
            if (nativeContextPtr != 0L) {
                nativeStopGeneration(nativeContextPtr)
            }
        }

        /**
         * Format messages into model-specific prompt.
         * Detects model type from filename and uses appropriate format.
         */
        private fun formatPrompt(messages: List<LLMMessage>): String {
            val modelName = currentModelPath?.lowercase() ?: ""

            return if (modelName.contains("ministral") || modelName.contains("mistral")) {
                formatMistralPrompt(messages)
            } else {
                formatChatMLPrompt(messages)
            }
        }

        /**
         * Format for Mistral/Ministral models: [INST] ... [/INST]
         */
        private fun formatMistralPrompt(messages: List<LLMMessage>): String {
            val sb = StringBuilder()
            val systemMessage = messages.find { it.role == "system" }?.content
            var isFirstUser = true

            for (message in messages) {
                when (message.role) {
                    "system" -> {
                        // System message handled with first user message
                    }
                    "user" -> {
                        if (isFirstUser && systemMessage != null) {
                            sb.append("[INST] $systemMessage\n\n${message.content} [/INST]")
                            isFirstUser = false
                        } else {
                            sb.append("[INST] ${message.content} [/INST]")
                        }
                    }
                    "assistant" -> sb.append("${message.content}</s>")
                }
            }

            return sb.toString()
        }

        /**
         * Format for TinyLlama/ChatML models: <|system|> ... <|user|> ...
         */
        private fun formatChatMLPrompt(messages: List<LLMMessage>): String {
            val sb = StringBuilder()

            for (message in messages) {
                when (message.role) {
                    "system" -> sb.append("<|system|>\n${message.content}</s>\n")
                    "user" -> sb.append("<|user|>\n${message.content}</s>\n")
                    "assistant" -> sb.append("<|assistant|>\n${message.content}</s>\n")
                }
            }

            // Add assistant prompt for generation
            sb.append("<|assistant|>\n")
            return sb.toString()
        }

        /**
         * Get optimal thread count for device.
         * Uses N-2 cores, capped at 8 (matching iOS).
         */
        private fun getOptimalThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return maxOf(1, minOf(8, cores - 2))
        }

        /**
         * Get metrics for telemetry.
         */
        fun getMetrics(): OnDeviceMetrics {
            val sortedMeasurements = ttftMeasurements.sorted()
            val medianTTFT =
                if (sortedMeasurements.isNotEmpty()) {
                    sortedMeasurements[sortedMeasurements.size / 2]
                } else {
                    0L
                }
            val p99TTFT =
                if (sortedMeasurements.isNotEmpty()) {
                    val p99Index =
                        (sortedMeasurements.size * 0.99).toInt()
                            .coerceAtMost(sortedMeasurements.size - 1)
                    sortedMeasurements[p99Index]
                } else {
                    0L
                }
            return OnDeviceMetrics(
                totalInputTokens = totalInputTokens,
                totalOutputTokens = totalOutputTokens,
                medianTTFT = medianTTFT,
                p99TTFT = p99TTFT,
            )
        }

        /**
         * Metrics data class.
         */
        data class OnDeviceMetrics(
            val totalInputTokens: Int,
            val totalOutputTokens: Int,
            val medianTTFT: Long,
            val p99TTFT: Long,
        )

        // Native method declarations
        private external fun nativeLoadModel(
            modelPath: String,
            contextSize: Int,
            gpuLayers: Int,
            nThreads: Int,
        ): Long

        private external fun nativeStartGeneration(
            contextPtr: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            callback: (String, Boolean) -> Unit,
        )

        private external fun nativeStopGeneration(contextPtr: Long)

        private external fun nativeFreeModel(contextPtr: Long)
    }
