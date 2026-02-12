package com.unamentis.services.llm

import android.util.Log
import com.unamentis.core.device.DeviceCapabilityDetector
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * LLM Backend Selector for runtime backend selection.
 *
 * Selects the optimal LLM backend based on device capabilities:
 *
 * 1. **ExecuTorch + QNN** (Flagship): Snapdragon 8 Gen2+ devices
 *    - Best performance: 50+ tok/sec
 *    - Full NPU acceleration
 *
 * 2. **MediaPipe** (High-end): Devices with OpenCL GPU support
 *    - Good performance: 20-30 tok/sec
 *    - GPU acceleration
 *
 * 3. **llama.cpp** (Fallback): All other devices
 *    - Baseline performance: 5-10 tok/sec
 *    - CPU-only inference
 *
 * Backend selection is performed once at startup and cached.
 * Use [selectBestBackend] to get the optimal backend for this device.
 */
@Singleton
class LLMBackendSelector
    @Inject
    constructor(
        private val deviceCapabilityDetector: DeviceCapabilityDetector,
        private val execuTorchProvider: Provider<ExecuTorchLLMService>,
        private val mediaPipeProvider: Provider<MediaPipeLLMService>,
        private val llamaCppProvider: Provider<OnDeviceLLMService>,
    ) {
        companion object {
            private const val TAG = "LLMBackendSelector"
        }

        // Cached selection result
        @Volatile
        private var cachedSelection: BackendSelectionResult? = null

        /**
         * Select the best available LLM backend for this device.
         *
         * @return BackendSelectionResult with selected backend and alternatives
         */
        fun selectBestBackend(): BackendSelectionResult {
            cachedSelection?.let { return it }

            logDeviceInfo()
            val (backends, selected, reason) = collectAvailableBackends()

            val result = buildSelectionResult(selected, reason, backends)
            cachedSelection = result
            logSelectionResult(result)
            return result
        }

        private fun logDeviceInfo() {
            val capabilities = deviceCapabilityDetector.detect()
            Log.i(TAG, "Selecting backend: ${capabilities.manufacturer} ${capabilities.model}")
            Log.i(TAG, "Device tier: ${capabilities.tier}, RAM: ${capabilities.totalRamMB}MB")
        }

        private fun collectAvailableBackends(): Triple<List<LLMBackend>, LLMBackend?, String> {
            val backends = mutableListOf<LLMBackend>()
            var selected: LLMBackend? = null
            var reason = ""

            tryAddExecuTorch(backends)?.let { (backend, r) ->
                if (selected == null) {
                    selected = backend
                    reason = r
                }
            }

            tryAddMediaPipe(backends)?.let { (backend, r) ->
                if (selected == null) {
                    selected = backend
                    reason = r
                }
            }

            tryAddLlamaCpp(backends)?.let { (backend, r) ->
                if (selected == null) {
                    selected = backend
                    reason = r
                }
            }

            return Triple(backends, selected, reason)
        }

        private fun tryAddExecuTorch(backends: MutableList<LLMBackend>): Pair<LLMBackend, String>? {
            if (!isExecuTorchAvailable()) {
                return null
            }
            return try {
                val backend = execuTorchProvider.get()
                if (backend.isAvailable()) {
                    backends.add(backend)
                    val reason = "Qualcomm NPU detected (Snapdragon 8 Gen2+)"
                    Log.i(TAG, "Selected ExecuTorch: $reason")
                    backend to reason
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "ExecuTorch not available: ${e.message}")
                null
            }
        }

        private fun tryAddMediaPipe(backends: MutableList<LLMBackend>): Pair<LLMBackend, String>? {
            if (!isMediaPipeAvailable()) {
                return null
            }
            return try {
                val backend = mediaPipeProvider.get()
                if (backend.isAvailable()) {
                    backends.add(backend)
                    val reason = "OpenCL GPU acceleration available"
                    Log.i(TAG, "Selected MediaPipe: $reason")
                    backend to reason
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe not available: ${e.message}")
                null
            }
        }

        private fun tryAddLlamaCpp(backends: MutableList<LLMBackend>): Pair<LLMBackend, String>? {
            if (!deviceCapabilityDetector.supportsOnDeviceLLM()) {
                return null
            }
            return try {
                val backend = LlamaCppBackendAdapter(llamaCppProvider.get())
                backends.add(backend)
                val reason = "CPU-only fallback (device supports on-device LLM)"
                Log.i(TAG, "Selected llama.cpp: $reason")
                backend to reason
            } catch (e: Exception) {
                Log.w(TAG, "llama.cpp not available: ${e.message}")
                null
            }
        }

        private fun buildSelectionResult(
            selected: LLMBackend?,
            reason: String,
            backends: List<LLMBackend>,
        ): BackendSelectionResult {
            if (selected == null) {
                Log.e(TAG, "No LLM backend available!")
                throw IllegalStateException("No LLM backend available.")
            }
            return BackendSelectionResult(
                backend = selected,
                reason = reason,
                alternatives = backends.filter { it != selected },
            )
        }

        private fun logSelectionResult(result: BackendSelectionResult) {
            val count = 1 + result.alternatives.size
            Log.i(TAG, "Backend: ${result.backend.backendName} ($count available)")
        }

        /**
         * Get the selected backend (or select one if not yet done).
         */
        fun getSelectedBackend(): LLMBackend {
            return selectBestBackend().backend
        }

        /**
         * Get all available backends for this device.
         */
        fun getAvailableBackends(): List<LLMBackend> {
            return selectBestBackend().let {
                listOf(it.backend) + it.alternatives
            }
        }

        /**
         * Force re-selection of backend (useful after model downloads).
         */
        fun clearCache() {
            cachedSelection = null
            Log.i(TAG, "Backend selection cache cleared")
        }

        /**
         * Check if ExecuTorch native library is available.
         */
        private fun isExecuTorchAvailable(): Boolean {
            return try {
                Class.forName("org.pytorch.executorch.LlamaModule")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }

        /**
         * Check if MediaPipe LLM Inference is available.
         */
        private fun isMediaPipeAvailable(): Boolean {
            return try {
                Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }

        /**
         * Get a human-readable summary of available backends.
         */
        fun getBackendSummary(): String {
            val selection = selectBestBackend()
            return buildString {
                appendLine("Selected: ${selection.backend.backendName}")
                appendLine("Reason: ${selection.reason}")
                appendLine("Expected speed: ${selection.backend.expectedToksPerSec} tok/sec")
                if (selection.alternatives.isNotEmpty()) {
                    appendLine("Alternatives:")
                    selection.alternatives.forEach { alt ->
                        appendLine("  - ${alt.backendName} (${alt.expectedToksPerSec} tok/sec)")
                    }
                }
            }
        }
    }

/**
 * Adapter to make OnDeviceLLMService conform to LLMBackend interface.
 */
internal class LlamaCppBackendAdapter(
    private val service: OnDeviceLLMService,
) : LLMBackend {
    override val providerName: String = service.providerName
    override val backendName: String = "llama.cpp (CPU)"
    override val expectedToksPerSec: Int = 8

    override val isLoaded: Boolean
        get() = service.isLoaded()

    override suspend fun loadModel(): Result<Unit> {
        val modelPath = service.getAvailableModelPath()
        return if (modelPath != null) {
            val loaded = service.loadModel(OnDeviceLLMService.ModelConfig(modelPath))
            if (loaded) Result.success(Unit) else Result.failure(IllegalStateException("Failed to load model"))
        } else {
            Result.failure(IllegalStateException("No model available"))
        }
    }

    override fun unloadModel() {
        service.unloadModel()
    }

    override fun streamCompletion(
        messages: List<com.unamentis.data.model.LLMMessage>,
        temperature: Float,
        maxTokens: Int,
    ) = service.streamCompletion(messages, temperature, maxTokens)

    override suspend fun stop() {
        service.stop()
    }

    override fun getMetrics(): LLMBackendMetrics {
        val metrics = service.getMetrics()
        return LLMBackendMetrics(
            totalInputTokens = metrics.totalInputTokens,
            totalOutputTokens = metrics.totalOutputTokens,
            medianTTFT = metrics.medianTTFT,
            p99TTFT = metrics.p99TTFT,
            // Not tracked in original service
            averageToksPerSec = 0f,
        )
    }
}
