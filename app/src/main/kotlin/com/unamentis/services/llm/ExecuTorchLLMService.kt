package com.unamentis.services.llm

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExecuTorch LLM service for Qualcomm NPU-accelerated inference.
 *
 * Uses PyTorch ExecuTorch with Qualcomm AI Engine Direct (QNN) backend
 * for maximum performance on Snapdragon 8 Gen2+ devices.
 *
 * Performance:
 * - Snapdragon 8 Gen3: 50+ tok/sec decode, 260+ tok/sec prefill
 * - Snapdragon 8 Gen2: 30-40 tok/sec decode
 *
 * Benefits:
 * - Full NPU acceleration (Hexagon DSP)
 * - Official Meta/Qualcomm collaboration
 * - Best performance on flagship devices
 * - 2.5x decode latency improvement vs CPU
 *
 * Requirements:
 * - Snapdragon 8 Gen2+ (SM8550, SM8650, SM8750)
 * - 16GB RAM for 3B models, 8GB for 1B models
 * - Model in .pte format (ExecuTorch serialized)
 */
@Singleton
class ExecuTorchLLMService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LLMBackend {
        companion object {
            private const val TAG = "ExecuTorchLLM"

            // Model filename (requires .pte format from ExecuTorch export)
            const val LLAMA_1B_FILENAME = "llama-3.2-1b-instruct-qnn.pte"
            const val LLAMA_3B_FILENAME = "llama-3.2-3b-instruct-qnn.pte"

            // Default configuration
            private const val DEFAULT_MAX_TOKENS = 512
            private const val MAX_TTFT_MEASUREMENTS = 100

            // Supported Snapdragon SoC models
            private val SUPPORTED_SOCS =
                setOf(
                    // Snapdragon 8 Gen 2
                    "SM8550",
                    // Snapdragon 8 Gen 3
                    "SM8650",
                    // Snapdragon 8 Gen 4
                    "SM8750",
                    // Snapdragon 8 Elite
                    "SM8750-AB",
                )

            // Native library loading flag
            private var nativeLibraryLoaded = false

            init {
                try {
                    // ExecuTorch AAR should provide these libraries
                    System.loadLibrary("executorch_jni")
                    nativeLibraryLoaded = true
                    Log.i(TAG, "ExecuTorch native library loaded successfully")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "ExecuTorch native library not available: ${e.message}")
                    nativeLibraryLoaded = false
                }
            }
        }

        override val providerName: String = context.getString(R.string.provider_executorch)
        override val backendName: String = "ExecuTorch (QNN NPU)"
        override val expectedToksPerSec: Int = 50

        // Native module pointer (0 = not loaded)
        private val nativeModulePtr = AtomicLong(0)
        private val isModelLoadedAtomic = AtomicBoolean(false)

        override val isLoaded: Boolean
            get() = isModelLoadedAtomic.get() && nativeModulePtr.get() != 0L

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
                if (!nativeLibraryLoaded) {
                    return@withContext Result.failure(
                        IllegalStateException("ExecuTorch native library not available"),
                    )
                }

                try {
                    val modelPath = getAvailableModelPath()
                    if (modelPath == null) {
                        Log.e(TAG, "No ExecuTorch model available")
                        return@withContext Result.failure(
                            IllegalStateException("No ExecuTorch model available. Please download Llama 3.2 1B first."),
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

                    Log.i(TAG, "Loading ExecuTorch model: $modelPath")

                    val ptr = nativeLoadModel(modelPath)
                    if (ptr == 0L) {
                        Log.e(TAG, "Failed to load ExecuTorch model")
                        return@withContext Result.failure(
                            IllegalStateException("Failed to load ExecuTorch model"),
                        )
                    }

                    nativeModulePtr.set(ptr)
                    isModelLoadedAtomic.set(true)
                    currentModelPath = modelPath
                    Log.i(TAG, "ExecuTorch model loaded successfully")

                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load ExecuTorch model", e)
                    Result.failure(e)
                }
            }

        /**
         * Unload model and release resources.
         */
        override fun unloadModel() {
            val ptr = nativeModulePtr.getAndSet(0)
            if (ptr != 0L) {
                try {
                    nativeFreeModel(ptr)
                } catch (e: Exception) {
                    Log.w(TAG, "Error freeing ExecuTorch model", e)
                }
            }
            isModelLoadedAtomic.set(false)
            currentModelPath = null
            Log.i(TAG, "ExecuTorch model unloaded")
        }

        /**
         * Get available model path.
         * Prefers 1B model for faster inference.
         */
        fun getAvailableModelPath(): String? {
            val modelsDir = getModelsDirectory()

            // Prefer 1B model for speed
            val llama1B = File(modelsDir, LLAMA_1B_FILENAME)
            if (llama1B.exists()) {
                Log.d(TAG, "Found Llama 3.2 1B model: ${llama1B.absolutePath}")
                return llama1B.absolutePath
            }

            // Fall back to 3B model
            val llama3B = File(modelsDir, LLAMA_3B_FILENAME)
            if (llama3B.exists()) {
                Log.d(TAG, "Found Llama 3.2 3B model: ${llama3B.absolutePath}")
                return llama3B.absolutePath
            }

            Log.w(TAG, "No ExecuTorch models found in: ${modelsDir.absolutePath}")
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
                val modulePtr = ensureModelReady()

                if (modulePtr != null) {
                    val prompt = formatPrompt(messages)
                    val context = GenerationContext(System.currentTimeMillis())

                    Log.d(TAG, "Starting ExecuTorch generation with ${prompt.length} char prompt")
                    totalInputTokens.addAndGet(prompt.length / 4)

                    try {
                        runGeneration(modulePtr, prompt, maxTokens, temperature, context)
                    } catch (e: Exception) {
                        Log.e(TAG, "ExecuTorch generation error", e)
                        close(e)
                    }
                }

                awaitClose {
                    Log.d(TAG, "Flow closed, stopping generation")
                    val ptr = nativeModulePtr.get()
                    if (ptr != 0L) {
                        nativeStopGeneration(ptr)
                    }
                }
            }.flowOn(Dispatchers.IO)

        private suspend fun kotlinx.coroutines.channels.ProducerScope<LLMToken>.ensureModelReady(): Long? {
            if (!nativeLibraryLoaded) {
                close(IllegalStateException("ExecuTorch native library not available"))
                return null
            }

            if (!isLoaded) {
                val result = loadModel()
                if (result.isFailure) {
                    close(result.exceptionOrNull() ?: IllegalStateException("Failed to load"))
                    return null
                }
            }

            val ptr = nativeModulePtr.get()
            if (ptr == 0L) {
                close(IllegalStateException("ExecuTorch module not initialized"))
                return null
            }
            return ptr
        }

        private class GenerationContext(val startTime: Long) {
            var firstTokenEmitted = false
            var generatedTokens = 0
        }

        private fun kotlinx.coroutines.channels.ProducerScope<LLMToken>.runGeneration(
            modulePtr: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            ctx: GenerationContext,
        ) {
            val effectiveMaxTokens = maxTokens.coerceAtMost(DEFAULT_MAX_TOKENS)
            if (effectiveMaxTokens < maxTokens) {
                Log.w(TAG, "maxTokens capped from $maxTokens to $effectiveMaxTokens (limit: $DEFAULT_MAX_TOKENS)")
            }
            nativeGenerate(
                modulePtr,
                prompt,
                effectiveMaxTokens,
                temperature,
            ) { content, isDone ->
                handleToken(content, isDone, ctx)
            }
        }

        private fun kotlinx.coroutines.channels.ProducerScope<LLMToken>.handleToken(
            content: String,
            isDone: Boolean,
            ctx: GenerationContext,
        ) {
            recordTTFT(content, ctx)
            if (content.isNotEmpty()) ctx.generatedTokens++
            trySend(LLMToken(content = content, isDone = isDone))
            if (isDone) finalizeGeneration(ctx)
        }

        private fun recordTTFT(
            content: String,
            ctx: GenerationContext,
        ) {
            if (!ctx.firstTokenEmitted && content.isNotEmpty()) {
                val ttft = System.currentTimeMillis() - ctx.startTime
                addMeasurement(ttftMeasurements, ttft)
                ctx.firstTokenEmitted = true
                Log.i(TAG, "TTFT: ${ttft}ms")
            }
        }

        private fun kotlinx.coroutines.channels.ProducerScope<LLMToken>.finalizeGeneration(ctx: GenerationContext) {
            val elapsedSec = (System.currentTimeMillis() - ctx.startTime) / 1000f
            if (elapsedSec > 0 && ctx.generatedTokens > 0) {
                val toksPerSec = ctx.generatedTokens / elapsedSec
                addMeasurement(toksPerSecMeasurements, toksPerSec)
                Log.i(TAG, "Generation speed: ${toksPerSec.toInt()} tok/sec")
            }
            totalOutputTokens.addAndGet(ctx.generatedTokens)
            Log.i(TAG, "Generation complete: ${ctx.generatedTokens} tokens")
            close()
        }

        private fun <T> addMeasurement(
            list: CopyOnWriteArrayList<T>,
            value: T,
        ) {
            if (list.size >= MAX_TTFT_MEASUREMENTS) {
                list.removeAt(0)
            }
            list.add(value)
        }

        override suspend fun stop() {
            val ptr = nativeModulePtr.get()
            if (ptr != 0L) {
                nativeStopGeneration(ptr)
            }
        }

        /**
         * Format messages into Llama 3 prompt format.
         */
        private fun formatPrompt(messages: List<LLMMessage>): String {
            val sb = StringBuilder()

            // Llama 3 format: <|begin_of_text|><|start_header_id|>system<|end_header_id|>...
            sb.append("<|begin_of_text|>")

            for (message in messages) {
                when (message.role) {
                    "system" -> {
                        sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
                        sb.append(message.content)
                        sb.append("<|eot_id|>")
                    }
                    "user" -> {
                        sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                        sb.append(message.content)
                        sb.append("<|eot_id|>")
                    }
                    "assistant" -> {
                        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                        sb.append(message.content)
                        sb.append("<|eot_id|>")
                    }
                }
            }

            // Add assistant header for generation
            sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            return sb.toString()
        }

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
         * Check if ExecuTorch with QNN is available on this device.
         *
         * Requires:
         * - Snapdragon 8 Gen2+ SoC
         * - Native library loaded
         */
        fun isAvailable(): Boolean {
            if (!nativeLibraryLoaded) {
                return false
            }

            val socModel = getSoCModel()
            val isSupported = SUPPORTED_SOCS.any { socModel.contains(it, ignoreCase = true) }

            Log.d(TAG, "SoC: $socModel, ExecuTorch supported: $isSupported")
            return isSupported
        }

        /**
         * Get the SoC model identifier.
         */
        @SuppressLint("NewApi")
        private fun getSoCModel(): String {
            return try {
                // Try to get SoC model from system property
                val process = Runtime.getRuntime().exec("getprop ro.board.platform")
                val result = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                result.ifEmpty { android.os.Build.SOC_MODEL }
            } catch (e: Exception) {
                android.os.Build.SOC_MODEL
            }
        }

        // Native method declarations (implemented in ExecuTorch JNI)
        private external fun nativeLoadModel(modelPath: String): Long

        private external fun nativeGenerate(
            modulePtr: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            callback: (String, Boolean) -> Unit,
        )

        private external fun nativeStopGeneration(modulePtr: Long)

        private external fun nativeFreeModel(modulePtr: Long)
    }
