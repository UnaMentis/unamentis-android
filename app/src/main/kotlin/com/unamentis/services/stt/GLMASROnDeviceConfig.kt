package com.unamentis.services.stt

import java.io.File

/**
 * Configuration for GLM-ASR on-device speech-to-text service.
 *
 * GLM-ASR-Nano-2512 runs entirely on-device using ONNX Runtime
 * for neural network inference and llama.cpp for text decoding.
 *
 * Pipeline:
 * 1. Audio (16kHz PCM) -> Mel Spectrogram
 * 2. Mel Spectrogram -> Whisper Encoder (ONNX)
 * 3. Encoded Audio -> Audio Adapter (ONNX)
 * 4. Adapted Audio -> Text Decoder (llama.cpp GGUF)
 * 5. Tokens -> Transcript
 *
 * @property modelDirectory Directory containing all model files
 * @property maxAudioDurationSeconds Maximum audio duration to process at once
 * @property useGPU Whether to use GPU acceleration for ONNX inference
 * @property gpuLayers Number of GPU layers for llama.cpp decoder (0 = CPU only)
 * @property numThreads Number of CPU threads for inference
 * @property language Language hint for recognition ("auto", "en", "zh", "yue")
 */
data class GLMASROnDeviceConfig(
    val modelDirectory: File,
    val maxAudioDurationSeconds: Float = 30.0f,
    val useGPU: Boolean = true,
    val gpuLayers: Int = 99,
    val numThreads: Int = 4,
    val language: String = "auto",
) {
    /**
     * Path to Whisper encoder ONNX model.
     */
    val whisperEncoderPath: File
        get() = modelDirectory.resolve(MODEL_WHISPER_ENCODER)

    /**
     * Path to audio adapter ONNX model.
     */
    val audioAdapterPath: File
        get() = modelDirectory.resolve(MODEL_AUDIO_ADAPTER)

    /**
     * Path to embed head ONNX model.
     */
    val embedHeadPath: File
        get() = modelDirectory.resolve(MODEL_EMBED_HEAD)

    /**
     * Path to GGUF text decoder model.
     */
    val decoderPath: File
        get() = modelDirectory.resolve(MODEL_DECODER)

    /**
     * Check if all required model files are present.
     *
     * @return true if all model files exist
     */
    fun areModelsPresent(): Boolean {
        return whisperEncoderPath.exists() &&
            audioAdapterPath.exists() &&
            embedHeadPath.exists() &&
            decoderPath.exists()
    }

    /**
     * Get list of missing model files.
     *
     * @return List of missing file names
     */
    fun getMissingModels(): List<String> {
        val missing = mutableListOf<String>()
        if (!whisperEncoderPath.exists()) missing.add(MODEL_WHISPER_ENCODER)
        if (!audioAdapterPath.exists()) missing.add(MODEL_AUDIO_ADAPTER)
        if (!embedHeadPath.exists()) missing.add(MODEL_EMBED_HEAD)
        if (!decoderPath.exists()) missing.add(MODEL_DECODER)
        return missing
    }

    /**
     * Get total size of all model files in bytes.
     *
     * @return Total size in bytes, or -1 if any file is missing
     */
    fun getTotalModelSize(): Long {
        if (!areModelsPresent()) return -1

        return whisperEncoderPath.length() +
            audioAdapterPath.length() +
            embedHeadPath.length() +
            decoderPath.length()
    }

    companion object {
        // Model file names (matching iOS implementation)
        const val MODEL_WHISPER_ENCODER = "glm_asr_whisper_encoder.onnx"
        const val MODEL_AUDIO_ADAPTER = "glm_asr_audio_adapter.onnx"
        const val MODEL_EMBED_HEAD = "glm_asr_embed_head.onnx"
        const val MODEL_DECODER = "glm-asr-nano-q4km.gguf"

        // Model sizes for download progress
        const val SIZE_WHISPER_ENCODER = 1_200_000_000L // ~1.2GB
        const val SIZE_AUDIO_ADAPTER = 56_000_000L // ~56MB
        const val SIZE_EMBED_HEAD = 232_000_000L // ~232MB
        const val SIZE_DECODER = 935_000_000L // ~935MB
        const val SIZE_TOTAL = SIZE_WHISPER_ENCODER + SIZE_AUDIO_ADAPTER + SIZE_EMBED_HEAD + SIZE_DECODER

        // Audio processing constants (matching iOS)
        const val SAMPLE_RATE = 16000
        const val N_FFT = 400
        const val HOP_LENGTH = 160
        const val N_MELS = 128
        const val CHUNK_LENGTH_SECONDS = 30

        /**
         * Hugging Face model repository URL.
         */
        const val MODEL_REPO_URL = "https://huggingface.co/zai-org/GLM-ASR-Nano-2512"

        /**
         * Create configuration with default model directory.
         *
         * @param filesDir App's files directory (context.filesDir)
         * @return Default configuration
         */
        fun default(filesDir: File): GLMASROnDeviceConfig {
            return GLMASROnDeviceConfig(
                modelDirectory = filesDir.resolve("models/glm-asr-nano"),
            )
        }

        /**
         * Create configuration optimized for low-memory devices.
         *
         * Uses fewer threads and disables GPU to reduce memory pressure.
         *
         * @param filesDir App's files directory
         * @return Low-memory configuration
         */
        fun lowMemory(filesDir: File): GLMASROnDeviceConfig {
            return GLMASROnDeviceConfig(
                modelDirectory = filesDir.resolve("models/glm-asr-nano"),
                useGPU = false,
                gpuLayers = 0,
                numThreads = 2,
                maxAudioDurationSeconds = 15.0f,
            )
        }

        /**
         * Create configuration optimized for maximum performance.
         *
         * Uses all available resources for fastest inference.
         *
         * @param filesDir App's files directory
         * @param cpuCores Number of CPU cores available
         * @return High-performance configuration
         */
        fun highPerformance(
            filesDir: File,
            cpuCores: Int,
        ): GLMASROnDeviceConfig {
            return GLMASROnDeviceConfig(
                modelDirectory = filesDir.resolve("models/glm-asr-nano"),
                useGPU = true,
                gpuLayers = 99,
                numThreads = maxOf(4, cpuCores - 2),
                maxAudioDurationSeconds = 30.0f,
            )
        }
    }
}
