package com.unamentis.services.llm

import com.unamentis.data.model.LLMService

/**
 * LLM Backend interface for high-performance on-device inference.
 *
 * This interface extends [LLMService] with additional capabilities for
 * backend management, metrics, and performance optimization.
 *
 * Backend implementations:
 * - [ExecuTorchLLMService]: Qualcomm NPU acceleration (50+ tok/sec on flagship)
 * - [MediaPipeLLMService]: GPU acceleration via OpenCL (20-30 tok/sec)
 * - [OnDeviceLLMService]: CPU-only llama.cpp fallback (5-10 tok/sec)
 *
 * The [LLMBackendSelector] chooses the optimal backend based on device capabilities.
 */
interface LLMBackend : LLMService {
    /**
     * Human-readable name of this backend for logging and UI.
     * Examples: "ExecuTorch (QNN)", "MediaPipe (GPU)", "llama.cpp (CPU)"
     */
    val backendName: String

    /**
     * Expected tokens per second for this backend on typical devices.
     * Used for UI display and latency estimation, not guarantees.
     */
    val expectedToksPerSec: Int

    /**
     * Whether the backend model is currently loaded and ready for inference.
     */
    val isLoaded: Boolean

    /**
     * Load the model for inference.
     *
     * This may involve:
     * - Downloading the model if not present
     * - Loading weights into memory
     * - Initializing GPU/NPU contexts
     *
     * @return Result.success(Unit) on success, Result.failure(exception) on error
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Unload the model and release resources.
     *
     * Should free GPU/NPU memory, close native handles, etc.
     */
    fun unloadModel()

    /**
     * Get performance metrics for this backend.
     */
    fun getMetrics(): LLMBackendMetrics
}

/**
 * Performance metrics for an LLM backend.
 */
data class LLMBackendMetrics(
    /** Total input tokens processed since last reset */
    val totalInputTokens: Int,
    /** Total output tokens generated since last reset */
    val totalOutputTokens: Int,
    /** Median time to first token in milliseconds */
    val medianTTFT: Long,
    /** 99th percentile time to first token in milliseconds */
    val p99TTFT: Long,
    /** Average tokens per second during generation */
    val averageToksPerSec: Float = 0f,
)

/**
 * Configuration for LLM generation.
 */
data class LLMGenerationConfig(
    /** Sampling temperature (0.0 - 2.0). Higher = more random. */
    val temperature: Float = 0.7f,
    /** Maximum number of tokens to generate */
    val maxTokens: Int = 512,
    /** Top-K sampling. 0 = disabled. */
    val topK: Int = 40,
    /** Top-P (nucleus) sampling. 1.0 = disabled. */
    val topP: Float = 1.0f,
    /** Repetition penalty. 1.0 = disabled. */
    val repetitionPenalty: Float = 1.0f,
)

/**
 * Model specification for on-device LLM backends.
 */
data class LLMModelSpec(
    /** Unique identifier for this model */
    val id: String,
    /** Human-readable display name */
    val displayName: String,
    /** Filename of the model (without path) */
    val filename: String,
    /** Download URL for the model */
    val downloadUrl: String,
    /** Size of the model file in bytes */
    val sizeBytes: Long,
    /** SHA256 checksum for verification */
    val sha256: String,
    /** Minimum RAM required in MB */
    val minRamMB: Int,
    /** Recommended RAM in MB for optimal performance */
    val recommendedRamMB: Int,
    /** Backend type this model is compatible with */
    val backendType: LLMBackendType,
)

/**
 * Available LLM backend types.
 */
enum class LLMBackendType {
    /** ExecuTorch with Qualcomm QNN for NPU acceleration */
    EXECUTORCH_QNN,

    /** MediaPipe LLM Inference with GPU acceleration */
    MEDIAPIPE,

    /** llama.cpp with CPU (and optional OpenCL) */
    LLAMA_CPP,
}

/**
 * Result of backend selection.
 */
data class BackendSelectionResult(
    /** Selected backend */
    val backend: LLMBackend,
    /** Reason for selection */
    val reason: String,
    /** Available alternatives in priority order */
    val alternatives: List<LLMBackend>,
)
