package com.unamentis.services.llm

import android.content.Context
import android.util.Log
import com.unamentis.core.device.DeviceCapabilityDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model download manager for on-device LLM models.
 *
 * Handles downloading GGUF models from HuggingFace with:
 * - Progress tracking via Flow
 * - SHA256 verification
 * - Resume support for interrupted downloads
 * - Storage management
 *
 * Models are stored in external files directory to avoid
 * bloating the APK size.
 */
@Singleton
@Suppress("LargeClass") // Manages multiple model types (llama.cpp, MediaPipe, ExecuTorch, GLM-ASR)
class ModelDownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val deviceCapabilityDetector: DeviceCapabilityDetector,
    ) {
        companion object {
            private const val TAG = "ModelDownloadManager"

            // ==========================================
            // llama.cpp Models (GGUF format)
            // ==========================================

            // HuggingFace model URLs
            // Using QuantFactory's public Ministral (bartowski's is now gated/401)
            private const val MINISTRAL_3B_URL_BASE =
                "https://huggingface.co/QuantFactory/Ministral-3b-instruct-GGUF/resolve/main/"
            private const val MINISTRAL_3B_FILE = "Ministral-3b-instruct.Q4_K_M.gguf"

            private const val TINYLLAMA_1B_URL_BASE =
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/"
            private const val TINYLLAMA_1B_FILE = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

            // Llama 3.2 1B for faster inference (llama.cpp format)
            private const val LLAMA_1B_GGUF_URL =
                "https://huggingface.co/lmstudio-community/" +
                    "Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
            const val LLAMA_1B_GGUF_FILENAME = "llama-3.2-1b-instruct-q4_k_m.gguf"

            // ==========================================
            // MediaPipe Models (.task format)
            // ==========================================

            // Gemma 2B for MediaPipe LLM Inference
            // Note: MediaPipe requires models in .task format converted via MediaPipe Model Maker
            // These are pre-converted models from Google's AI Edge model library
            private const val GEMMA_2B_TASK_URL =
                "https://storage.googleapis.com/mediapipe-assets/gemma-2b-it-gpu-int4.task"

            // ==========================================
            // ExecuTorch Models (.pte format)
            // ==========================================

            // Note: ExecuTorch .pte models require custom export with QNN backend
            // These models need to be exported using ExecuTorch's Llama export script
            // with Qualcomm-specific quantization (16a4w)
            // For now, we'll use placeholder URLs - actual models need to be hosted
            private const val LLAMA_1B_PTE_URL =
                "https://storage.googleapis.com/unamentis-models/llama-3.2-1b-instruct-qnn.pte"
            private const val LLAMA_3B_PTE_URL =
                "https://storage.googleapis.com/unamentis-models/llama-3.2-3b-instruct-qnn.pte"

            // ==========================================
            // GLM-ASR On-Device STT Models
            // ==========================================

            // GLM-ASR-Nano-2512 model components
            // Note: These URLs are placeholders - actual models to be hosted on HuggingFace
            private const val GLM_ASR_BASE_URL =
                "https://huggingface.co/zai-org/GLM-ASR-Nano-2512/resolve/main/"

            // Model filenames
            const val GLM_ASR_WHISPER_ENCODER_FILENAME = "glm_asr_whisper_encoder.onnx"
            const val GLM_ASR_AUDIO_ADAPTER_FILENAME = "glm_asr_audio_adapter.onnx"
            const val GLM_ASR_EMBED_HEAD_FILENAME = "glm_asr_embed_head.onnx"
            const val GLM_ASR_DECODER_FILENAME = "glm_asr_decoder_q4km.gguf"

            // SHA256 checksums for verification (from HuggingFace LFS OIDs)
            private val MODEL_CHECKSUMS =
                mapOf(
                    OnDeviceLLMService.MINISTRAL_3B_FILENAME to
                        "88d665cbee7f074f3b283c64d0ea5c356c5d923d635c9ab65d5c17329d41f734",
                    OnDeviceLLMService.TINYLLAMA_1B_FILENAME to
                        "9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0",
                    // New model checksums (to be updated once models are hosted)
                    // Will be populated once hosted
                    LLAMA_1B_GGUF_FILENAME to "",
                    // Google's model - no checksum verification needed
                    MediaPipeLLMService.GEMMA_2B_FILENAME to "",
                    // Custom export - checksum TBD
                    ExecuTorchLLMService.LLAMA_1B_FILENAME to "",
                    // Custom export - checksum TBD
                    ExecuTorchLLMService.LLAMA_3B_FILENAME to "",
                    // GLM-ASR STT models - checksums TBD
                    GLM_ASR_WHISPER_ENCODER_FILENAME to "",
                    GLM_ASR_AUDIO_ADAPTER_FILENAME to "",
                    GLM_ASR_EMBED_HEAD_FILENAME to "",
                    GLM_ASR_DECODER_FILENAME to "",
                )

            // Download timeout (models are large)
            private const val DOWNLOAD_TIMEOUT_MINUTES = 30L

            // Buffer size for file operations
            private const val BUFFER_SIZE = 8192
        }

        /**
         * Download state for a model.
         */
        sealed class DownloadState {
            data object Idle : DownloadState()

            data class Downloading(
                val progress: Float,
                val downloadedBytes: Long,
                val totalBytes: Long,
            ) : DownloadState()

            data class Verifying(val filename: String) : DownloadState()

            data class Complete(val modelPath: String) : DownloadState()

            data class Error(val message: String) : DownloadState()

            data object Cancelled : DownloadState()
        }

        /**
         * Model information for download.
         */
        data class ModelInfo(
            val spec: DeviceCapabilityDetector.OnDeviceModelSpec,
            val downloadUrl: String,
            val isDownloaded: Boolean,
            val fileSizeOnDisk: Long,
        )

        /**
         * Extended model specification for all backend types.
         */
        enum class ExtendedModelSpec(
            val filename: String,
            val displayName: String,
            val sizeBytes: Long,
            val minRamMB: Int,
            val backendType: LLMBackendType,
            val downloadUrl: String,
        ) {
            // llama.cpp models (GGUF format)
            LLAMA_1B_GGUF(
                filename = LLAMA_1B_GGUF_FILENAME,
                displayName = "Llama 3.2 1B (Fast)",
                // ~900MB
                sizeBytes = 900_000_000L,
                minRamMB = 2048,
                backendType = LLMBackendType.LLAMA_CPP,
                downloadUrl = LLAMA_1B_GGUF_URL,
            ),

            // MediaPipe models (.task format)
            GEMMA_2B_TASK(
                filename = MediaPipeLLMService.GEMMA_2B_FILENAME,
                displayName = "Gemma 2B (GPU)",
                // ~1.5GB
                sizeBytes = 1_500_000_000L,
                minRamMB = 4096,
                backendType = LLMBackendType.MEDIAPIPE,
                downloadUrl = GEMMA_2B_TASK_URL,
            ),

            // ExecuTorch models (.pte format)
            LLAMA_1B_PTE(
                filename = ExecuTorchLLMService.LLAMA_1B_FILENAME,
                displayName = "Llama 3.2 1B (NPU)",
                // ~1GB
                sizeBytes = 1_000_000_000L,
                minRamMB = 4096,
                backendType = LLMBackendType.EXECUTORCH_QNN,
                downloadUrl = LLAMA_1B_PTE_URL,
            ),
            LLAMA_3B_PTE(
                filename = ExecuTorchLLMService.LLAMA_3B_FILENAME,
                displayName = "Llama 3.2 3B (NPU)",
                // ~2.5GB
                sizeBytes = 2_500_000_000L,
                minRamMB = 8192,
                backendType = LLMBackendType.EXECUTORCH_QNN,
                downloadUrl = LLAMA_3B_PTE_URL,
            ),
        }

        /**
         * GLM-ASR on-device STT model specification.
         *
         * These models form the GLM-ASR-Nano-2512 pipeline:
         * 1. Whisper Encoder - Converts mel spectrograms to audio features (ONNX)
         * 2. Audio Adapter - Aligns features with LLM space (ONNX)
         * 3. Embed Head - Produces token embeddings (ONNX)
         * 4. Decoder - Generates text from embeddings (GGUF)
         */
        enum class GLMASRModelSpec(
            val filename: String,
            val displayName: String,
            val sizeBytes: Long,
            val minRamMB: Int,
            val downloadUrl: String,
        ) {
            /** Whisper encoder for mel spectrogram processing (~1.2GB). */
            WHISPER_ENCODER(
                filename = GLM_ASR_WHISPER_ENCODER_FILENAME,
                displayName = "Whisper Encoder",
                sizeBytes = 1_200_000_000L,
                minRamMB = 8192,
                downloadUrl = "${GLM_ASR_BASE_URL}whisper_encoder.onnx",
            ),

            /** Audio adapter for LLM alignment (~56MB). */
            AUDIO_ADAPTER(
                filename = GLM_ASR_AUDIO_ADAPTER_FILENAME,
                displayName = "Audio Adapter",
                sizeBytes = 56_000_000L,
                minRamMB = 8192,
                downloadUrl = "${GLM_ASR_BASE_URL}audio_adapter.onnx",
            ),

            /** Embed head for token embeddings (~232MB). */
            EMBED_HEAD(
                filename = GLM_ASR_EMBED_HEAD_FILENAME,
                displayName = "Embed Head",
                sizeBytes = 232_000_000L,
                minRamMB = 8192,
                downloadUrl = "${GLM_ASR_BASE_URL}embed_head.onnx",
            ),

            /** Text decoder for transcript generation (~935MB). */
            DECODER(
                filename = GLM_ASR_DECODER_FILENAME,
                displayName = "Text Decoder",
                sizeBytes = 935_000_000L,
                minRamMB = 8192,
                downloadUrl = "${GLM_ASR_BASE_URL}decoder_q4km.gguf",
            ),
            ;

            companion object {
                /** Total size of all GLM-ASR models. */
                val TOTAL_SIZE_BYTES: Long = entries.sumOf { it.sizeBytes }
            }
        }

        /**
         * GLM-ASR model info for UI display.
         */
        data class GLMASRModelInfo(
            val spec: GLMASRModelSpec,
            val isDownloaded: Boolean,
            val fileSizeOnDisk: Long,
        )

        /**
         * Extended model info including backend type.
         */
        data class ExtendedModelInfo(
            val spec: ExtendedModelSpec,
            val isDownloaded: Boolean,
            val fileSizeOnDisk: Long,
        )

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: Flow<DownloadState> = _downloadState.asStateFlow()

        @Volatile
        private var currentDownloadJob: kotlinx.coroutines.Job? = null

        @Volatile
        private var currentCall: Call? = null
        private val isCancelled = AtomicBoolean(false)

        // OkHttp client with long timeout for large downloads
        private val downloadClient =
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build()

        /**
         * Get the models directory.
         * Falls back to internal storage if external storage is unavailable.
         */
        fun getModelsDirectory(): File {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val modelsDir = File(baseDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            return modelsDir
        }

        /**
         * Get available models based on device capabilities.
         */
        fun getAvailableModels(): List<ModelInfo> {
            val supportedModels = deviceCapabilityDetector.getSupportedOnDeviceModels()
            val modelsDir = getModelsDirectory()

            return supportedModels.map { spec ->
                val file = File(modelsDir, spec.filename)
                val downloadUrl = getDownloadUrl(spec)

                ModelInfo(
                    spec = spec,
                    downloadUrl = downloadUrl,
                    isDownloaded = file.exists() && file.length() > 0,
                    fileSizeOnDisk = if (file.exists()) file.length() else 0L,
                )
            }
        }

        /**
         * Check if recommended model is downloaded.
         */
        fun isRecommendedModelDownloaded(): Boolean {
            val recommended = deviceCapabilityDetector.getRecommendedOnDeviceModel() ?: return false
            val file = File(getModelsDirectory(), recommended.filename)
            return file.exists() && file.length() > 0
        }

        /**
         * Download the recommended model for this device.
         */
        suspend fun downloadRecommendedModel(): Result<String> {
            val recommended =
                deviceCapabilityDetector.getRecommendedOnDeviceModel()
                    ?: return Result.failure(IllegalStateException("Device does not support on-device LLM"))

            return downloadModel(recommended)
        }

        /**
         * Download a specific model.
         *
         * @param spec The model specification
         * @return Result containing the model path on success
         */
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        suspend fun downloadModel(spec: DeviceCapabilityDetector.OnDeviceModelSpec): Result<String> =
            withContext(Dispatchers.IO) {
                // Track the current coroutine job so cancelDownload() can cancel it
                currentDownloadJob = currentCoroutineContext()[Job]
                isCancelled.set(false)
                _downloadState.value = DownloadState.Downloading(0f, 0, spec.sizeBytes)

                val modelsDir = getModelsDirectory()
                val targetFile = File(modelsDir, spec.filename)
                val tempFile = File(modelsDir, "${spec.filename}.download")

                try {
                    // Check if already downloaded
                    if (targetFile.exists() && targetFile.length() > 0) {
                        currentDownloadJob = null
                        Log.i(TAG, "Model already exists: ${targetFile.absolutePath}")
                        _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                        return@withContext Result.success(targetFile.absolutePath)
                    }

                    val downloadUrl = getDownloadUrl(spec)
                    Log.i(TAG, "Starting download: $downloadUrl")

                    // Build request (with resume support)
                    val requestBuilder = Request.Builder().url(downloadUrl)

                    // Resume from existing partial download
                    val startByte =
                        if (tempFile.exists()) {
                            val existingSize = tempFile.length()
                            requestBuilder.header("Range", "bytes=$existingSize-")
                            Log.i(TAG, "Resuming from byte $existingSize")
                            existingSize
                        } else {
                            0L
                        }

                    val request = requestBuilder.build()
                    val call = downloadClient.newCall(request)
                    currentCall = call
                    val response = call.execute()

                    // Use response.use to guarantee the response is closed on all paths
                    // (success, error, early return, or exception). The finally block in
                    // use() ensures closure even with non-local returns.
                    response.use { resp ->
                        if (!resp.isSuccessful && resp.code != 206) {
                            currentCall = null
                            currentDownloadJob = null
                            val error = "Download failed: HTTP ${resp.code}"
                            Log.e(TAG, error)
                            _downloadState.value = DownloadState.Error(error)
                            return@withContext Result.failure(IOException(error))
                        }

                        // Handle server ignoring Range header: if we requested a resume
                        // (startByte > 0) but got a 200 instead of 206, the server is sending
                        // the full file. We must restart the download to avoid corruption.
                        val actualStartByte: Long
                        if (startByte > 0 && resp.code != 206) {
                            Log.w(
                                TAG,
                                "Server ignored Range header (got 200 instead of 206). " +
                                    "Restarting download from beginning.",
                            )
                            currentCall = null
                            // Don't clear currentDownloadJob - recursive call will set it
                            // Delete the partial file and restart
                            tempFile.delete()
                            // Recursive call to restart without resume
                            // (response.use will close the response as we exit)
                            return@withContext downloadModel(spec)
                        } else {
                            actualStartByte = startByte
                        }

                        val body =
                            resp.body ?: run {
                                currentCall = null
                                currentDownloadJob = null
                                val error = "Empty response body"
                                Log.e(TAG, error)
                                _downloadState.value = DownloadState.Error(error)
                                return@withContext Result.failure(IOException(error))
                            }

                        // Get total size (handle Content-Range for resumed downloads)
                        val contentLength = body.contentLength()
                        val totalBytes =
                            if (resp.code == 206) {
                                // Parse Content-Range header: bytes 0-999/1000
                                val contentRange = resp.header("Content-Range")
                                contentRange?.substringAfter("/")?.toLongOrNull()
                                    ?: (actualStartByte + contentLength)
                            } else {
                                contentLength
                            }

                        Log.i(TAG, "Total size: $totalBytes bytes")

                        // Write to temp file
                        val outputStream =
                            FileOutputStream(tempFile, actualStartByte > 0) // append if resuming
                        val inputStream = body.byteStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = actualStartByte
                        var bytesRead: Int
                        var lastReportedProgress = -1 // Track last reported % to throttle updates

                        outputStream.use { output ->
                            inputStream.use { input ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (isCancelled.get()) {
                                        currentCall = null
                                        currentDownloadJob = null
                                        Log.i(TAG, "Download cancelled")
                                        _downloadState.value = DownloadState.Cancelled
                                        return@withContext Result.failure(
                                            IOException("Download cancelled"),
                                        )
                                    }

                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    // Throttle progress updates to ~1% increments to reduce UI churn
                                    val progress = downloadedBytes.toFloat() / totalBytes
                                    val currentPercent = (progress * 100).toInt()
                                    if (currentPercent > lastReportedProgress) {
                                        lastReportedProgress = currentPercent
                                        _downloadState.value =
                                            DownloadState.Downloading(
                                                progress = progress,
                                                downloadedBytes = downloadedBytes,
                                                totalBytes = totalBytes,
                                            )
                                    }
                                }
                            }
                        }
                    } // response.use closes the response here

                    Log.i(TAG, "Download complete, verifying...")
                    _downloadState.value = DownloadState.Verifying(spec.filename)

                    // Verify checksum (if available)
                    val expectedChecksum = MODEL_CHECKSUMS[spec.filename]
                    if (expectedChecksum != null) {
                        val actualChecksum = calculateSha256(tempFile)
                        if (actualChecksum != expectedChecksum) {
                            currentCall = null
                            currentDownloadJob = null
                            tempFile.delete()
                            val error = "Checksum verification failed"
                            Log.e(TAG, "$error: expected $expectedChecksum, got $actualChecksum")
                            _downloadState.value = DownloadState.Error(error)
                            return@withContext Result.failure(IOException(error))
                        }
                        Log.i(TAG, "Checksum verified")
                    } else {
                        Log.w(TAG, "No checksum available for ${spec.filename}, skipping verification")
                    }

                    // Rename temp file to final
                    if (!tempFile.renameTo(targetFile)) {
                        currentCall = null
                        currentDownloadJob = null
                        val error = "Failed to rename temp file"
                        Log.e(TAG, error)
                        _downloadState.value = DownloadState.Error(error)
                        return@withContext Result.failure(IOException(error))
                    }

                    currentCall = null
                    currentDownloadJob = null
                    Log.i(TAG, "Model ready: ${targetFile.absolutePath}")
                    _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                    Result.success(targetFile.absolutePath)
                } catch (e: Exception) {
                    currentCall = null
                    currentDownloadJob = null
                    // Check if the exception was due to cancellation
                    if (isCancelled.get()) {
                        Log.i(TAG, "Download cancelled")
                        _downloadState.value = DownloadState.Cancelled
                        Result.failure(IOException("Download cancelled"))
                    } else {
                        Log.e(TAG, "Download failed", e)
                        _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                        Result.failure(e)
                    }
                }
            }

        /**
         * Cancel the current download.
         *
         * This cancels both the coroutine job and the underlying OkHttp call,
         * ensuring that blocking network I/O is interrupted immediately.
         */
        fun cancelDownload() {
            isCancelled.set(true)
            // Cancel the OkHttp call to abort blocking network I/O
            currentCall?.cancel()
            currentCall = null
            currentDownloadJob?.cancel()
            currentDownloadJob = null
        }

        /**
         * Delete a downloaded model.
         *
         * @param spec The model to delete
         * @return true if deleted successfully
         */
        fun deleteModel(spec: DeviceCapabilityDetector.OnDeviceModelSpec): Boolean {
            val file = File(getModelsDirectory(), spec.filename)
            val tempFile = File(getModelsDirectory(), "${spec.filename}.download")

            var deleted = false
            if (file.exists()) {
                deleted = file.delete()
                Log.i(TAG, "Deleted model: ${spec.filename}, success: $deleted")
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }

            return deleted
        }

        /**
         * Get total storage used by models.
         */
        fun getTotalStorageUsed(): Long {
            val modelsDir = getModelsDirectory()
            return modelsDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".gguf") }
                ?.sumOf { it.length() }
                ?: 0L
        }

        /**
         * Get available storage space.
         */
        fun getAvailableStorage(): Long {
            val modelsDir = getModelsDirectory()
            return modelsDir.usableSpace
        }

        /**
         * Get download URL for a model specification.
         */
        private fun getDownloadUrl(spec: DeviceCapabilityDetector.OnDeviceModelSpec): String {
            return when (spec) {
                DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B ->
                    MINISTRAL_3B_URL_BASE + MINISTRAL_3B_FILE
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B ->
                    TINYLLAMA_1B_URL_BASE + TINYLLAMA_1B_FILE
            }
        }

        /**
         * Calculate SHA256 checksum of a file.
         */
        private fun calculateSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            file.inputStream().use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        // ==========================================
        // Extended Model Support (All Backend Types)
        // ==========================================

        /**
         * Get all extended models available for this device.
         * Includes llama.cpp, MediaPipe, and ExecuTorch models.
         */
        fun getExtendedModels(): List<ExtendedModelInfo> {
            val capabilities = deviceCapabilityDetector.detect()
            val modelsDir = getModelsDirectory()

            return ExtendedModelSpec.entries
                .filter { spec ->
                    // Filter by RAM requirements
                    capabilities.totalRamMB >= spec.minRamMB &&
                        // Filter by backend availability
                        when (spec.backendType) {
                            LLMBackendType.EXECUTORCH_QNN -> capabilities.hasQualcommNPU
                            LLMBackendType.MEDIAPIPE -> capabilities.hasOpenCLGPU
                            LLMBackendType.LLAMA_CPP -> true // Always available
                        }
                }
                .map { spec ->
                    val file = File(modelsDir, spec.filename)
                    ExtendedModelInfo(
                        spec = spec,
                        isDownloaded = file.exists() && file.length() > 0,
                        fileSizeOnDisk = if (file.exists()) file.length() else 0L,
                    )
                }
        }

        /**
         * Get the recommended extended model for this device.
         * Priority: ExecuTorch > MediaPipe > llama.cpp
         */
        fun getRecommendedExtendedModel(): ExtendedModelSpec? {
            val capabilities = deviceCapabilityDetector.detect()

            return when {
                // Flagship Qualcomm with NPU: Use ExecuTorch
                capabilities.hasQualcommNPU && capabilities.totalRamMB >= 8192 ->
                    ExtendedModelSpec.LLAMA_1B_PTE
                capabilities.hasQualcommNPU && capabilities.totalRamMB >= 4096 ->
                    ExtendedModelSpec.LLAMA_1B_PTE
                // High-end with GPU: Use MediaPipe
                capabilities.hasOpenCLGPU && capabilities.totalRamMB >= 4096 ->
                    ExtendedModelSpec.GEMMA_2B_TASK
                // Fallback: llama.cpp with fast 1B model
                capabilities.totalRamMB >= 2048 ->
                    ExtendedModelSpec.LLAMA_1B_GGUF
                else -> null
            }
        }

        /**
         * Download an extended model specification.
         */
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        suspend fun downloadExtendedModel(spec: ExtendedModelSpec): Result<String> =
            withContext(Dispatchers.IO) {
                currentDownloadJob = currentCoroutineContext()[Job]
                isCancelled.set(false)
                _downloadState.value = DownloadState.Downloading(0f, 0, spec.sizeBytes)

                val modelsDir = getModelsDirectory()
                val targetFile = File(modelsDir, spec.filename)
                val tempFile = File(modelsDir, "${spec.filename}.download")

                try {
                    // Check if already downloaded
                    if (targetFile.exists() && targetFile.length() > 0) {
                        currentDownloadJob = null
                        Log.i(TAG, "Extended model already exists: ${targetFile.absolutePath}")
                        _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                        return@withContext Result.success(targetFile.absolutePath)
                    }

                    Log.i(TAG, "Starting extended model download: ${spec.downloadUrl}")

                    // Build request (with resume support)
                    val requestBuilder = Request.Builder().url(spec.downloadUrl)

                    // Resume from existing partial download
                    val startByte =
                        if (tempFile.exists()) {
                            val existingSize = tempFile.length()
                            requestBuilder.header("Range", "bytes=$existingSize-")
                            Log.i(TAG, "Resuming from byte $existingSize")
                            existingSize
                        } else {
                            0L
                        }

                    val request = requestBuilder.build()
                    val call = downloadClient.newCall(request)
                    currentCall = call
                    val response = call.execute()

                    response.use { resp ->
                        if (!resp.isSuccessful && resp.code != 206) {
                            currentCall = null
                            currentDownloadJob = null
                            val error = "Download failed: HTTP ${resp.code}"
                            Log.e(TAG, error)
                            _downloadState.value = DownloadState.Error(error)
                            return@withContext Result.failure(IOException(error))
                        }

                        // Handle server ignoring Range header
                        val actualStartByte: Long
                        if (startByte > 0 && resp.code != 206) {
                            Log.w(TAG, "Server ignored Range header. Restarting download.")
                            currentCall = null
                            tempFile.delete()
                            return@withContext downloadExtendedModel(spec)
                        } else {
                            actualStartByte = startByte
                        }

                        val body =
                            resp.body ?: run {
                                currentCall = null
                                currentDownloadJob = null
                                val error = "Empty response body"
                                Log.e(TAG, error)
                                _downloadState.value = DownloadState.Error(error)
                                return@withContext Result.failure(IOException(error))
                            }

                        val contentLength = body.contentLength()
                        val totalBytes =
                            if (resp.code == 206) {
                                val contentRange = resp.header("Content-Range")
                                contentRange?.substringAfter("/")?.toLongOrNull()
                                    ?: (actualStartByte + contentLength)
                            } else {
                                contentLength
                            }

                        Log.i(TAG, "Total size: $totalBytes bytes")

                        val outputStream = FileOutputStream(tempFile, actualStartByte > 0)
                        val inputStream = body.byteStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloadedBytes = actualStartByte
                        var bytesRead: Int
                        var lastReportedProgress = -1

                        outputStream.use { output ->
                            inputStream.use { input ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (isCancelled.get()) {
                                        currentCall = null
                                        currentDownloadJob = null
                                        Log.i(TAG, "Download cancelled")
                                        _downloadState.value = DownloadState.Cancelled
                                        return@withContext Result.failure(
                                            IOException("Download cancelled"),
                                        )
                                    }

                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    val progress = downloadedBytes.toFloat() / totalBytes
                                    val currentPercent = (progress * 100).toInt()
                                    if (currentPercent > lastReportedProgress) {
                                        lastReportedProgress = currentPercent
                                        _downloadState.value =
                                            DownloadState.Downloading(
                                                progress = progress,
                                                downloadedBytes = downloadedBytes,
                                                totalBytes = totalBytes,
                                            )
                                    }
                                }
                            }
                        }
                    }

                    Log.i(TAG, "Download complete, verifying...")
                    _downloadState.value = DownloadState.Verifying(spec.filename)

                    // Verify checksum (if available)
                    val expectedChecksum = MODEL_CHECKSUMS[spec.filename]
                    if (!expectedChecksum.isNullOrBlank()) {
                        val actualChecksum = calculateSha256(tempFile)
                        if (actualChecksum != expectedChecksum) {
                            currentCall = null
                            currentDownloadJob = null
                            tempFile.delete()
                            val error = "Checksum verification failed"
                            Log.e(TAG, "$error: expected $expectedChecksum, got $actualChecksum")
                            _downloadState.value = DownloadState.Error(error)
                            return@withContext Result.failure(IOException(error))
                        }
                        Log.i(TAG, "Checksum verified")
                    } else {
                        Log.w(TAG, "No checksum available for ${spec.filename}, skipping verification")
                    }

                    // Rename temp file to final
                    if (!tempFile.renameTo(targetFile)) {
                        currentCall = null
                        currentDownloadJob = null
                        val error = "Failed to rename temp file"
                        Log.e(TAG, error)
                        _downloadState.value = DownloadState.Error(error)
                        return@withContext Result.failure(IOException(error))
                    }

                    currentCall = null
                    currentDownloadJob = null
                    Log.i(TAG, "Extended model ready: ${targetFile.absolutePath}")
                    _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                    Result.success(targetFile.absolutePath)
                } catch (e: Exception) {
                    currentCall = null
                    currentDownloadJob = null
                    if (isCancelled.get()) {
                        Log.i(TAG, "Download cancelled")
                        _downloadState.value = DownloadState.Cancelled
                        Result.failure(IOException("Download cancelled"))
                    } else {
                        Log.e(TAG, "Download failed", e)
                        _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                        Result.failure(e)
                    }
                }
            }

        /**
         * Download the recommended extended model for this device.
         */
        suspend fun downloadRecommendedExtendedModel(): Result<String> {
            val recommended =
                getRecommendedExtendedModel()
                    ?: return Result.failure(
                        IllegalStateException("No recommended model for this device"),
                    )

            return downloadExtendedModel(recommended)
        }

        /**
         * Check if the recommended extended model is downloaded.
         */
        fun isRecommendedExtendedModelDownloaded(): Boolean {
            val recommended = getRecommendedExtendedModel() ?: return false
            val file = File(getModelsDirectory(), recommended.filename)
            return file.exists() && file.length() > 0
        }

        /**
         * Delete an extended model.
         */
        fun deleteExtendedModel(spec: ExtendedModelSpec): Boolean {
            val file = File(getModelsDirectory(), spec.filename)
            val tempFile = File(getModelsDirectory(), "${spec.filename}.download")

            var deleted = false
            if (file.exists()) {
                deleted = file.delete()
                Log.i(TAG, "Deleted extended model: ${spec.filename}, success: $deleted")
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }

            return deleted
        }

        // ==========================================
        // GLM-ASR On-Device STT Model Support
        // ==========================================

        /**
         * Get GLM-ASR models directory.
         */
        fun getGLMASRModelsDirectory(): File {
            val modelsDir = getModelsDirectory()
            val glmAsrDir = File(modelsDir, "glm-asr-nano")
            if (!glmAsrDir.exists()) {
                glmAsrDir.mkdirs()
            }
            return glmAsrDir
        }

        /**
         * Get all GLM-ASR models with download status.
         */
        fun getGLMASRModels(): List<GLMASRModelInfo> {
            val modelsDir = getGLMASRModelsDirectory()

            return GLMASRModelSpec.entries.map { spec ->
                val file = File(modelsDir, spec.filename)
                GLMASRModelInfo(
                    spec = spec,
                    isDownloaded = file.exists() && file.length() > 0,
                    fileSizeOnDisk = if (file.exists()) file.length() else 0L,
                )
            }
        }

        /**
         * Check if all GLM-ASR models are downloaded.
         */
        fun areAllGLMASRModelsDownloaded(): Boolean {
            val modelsDir = getGLMASRModelsDirectory()
            return GLMASRModelSpec.entries.all { spec ->
                val file = File(modelsDir, spec.filename)
                file.exists() && file.length() > 0
            }
        }

        /**
         * Get total size of missing GLM-ASR models.
         */
        fun getMissingGLMASRModelsSize(): Long {
            val modelsDir = getGLMASRModelsDirectory()
            return GLMASRModelSpec.entries
                .filter { spec ->
                    val file = File(modelsDir, spec.filename)
                    !file.exists() || file.length() == 0L
                }
                .sumOf { it.sizeBytes }
        }

        /**
         * Download a specific GLM-ASR model.
         */
        @Suppress("LongMethod", "CyclomaticComplexMethod")
        suspend fun downloadGLMASRModel(spec: GLMASRModelSpec): Result<String> =
            withContext(Dispatchers.IO) {
                currentDownloadJob = currentCoroutineContext()[Job]
                isCancelled.set(false)
                _downloadState.value = DownloadState.Downloading(0f, 0, spec.sizeBytes)

                val modelsDir = getGLMASRModelsDirectory()
                val targetFile = File(modelsDir, spec.filename)
                val tempFile = File(modelsDir, "${spec.filename}.download")

                try {
                    // Check if already downloaded
                    if (targetFile.exists() && targetFile.length() > 0) {
                        currentDownloadJob = null
                        Log.i(TAG, "GLM-ASR model already exists: ${targetFile.absolutePath}")
                        _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                        return@withContext Result.success(targetFile.absolutePath)
                    }

                    Log.i(TAG, "Starting GLM-ASR download: ${spec.downloadUrl}")

                    // Build request with resume support
                    val requestBuilder = Request.Builder().url(spec.downloadUrl)
                    val startByte =
                        if (tempFile.exists()) {
                            val existingSize = tempFile.length()
                            requestBuilder.header("Range", "bytes=$existingSize-")
                            Log.i(TAG, "Resuming from byte $existingSize")
                            existingSize
                        } else {
                            0L
                        }

                    val request = requestBuilder.build()
                    val call = downloadClient.newCall(request)
                    currentCall = call
                    val response = call.execute()

                    response.use { resp ->
                        if (!resp.isSuccessful && resp.code != 206) {
                            currentCall = null
                            currentDownloadJob = null
                            val error = "GLM-ASR download failed: HTTP ${resp.code}"
                            Log.e(TAG, error)
                            _downloadState.value = DownloadState.Error(error)
                            return@withContext Result.failure(IOException(error))
                        }

                        val body =
                            resp.body
                                ?: run {
                                    _downloadState.value = DownloadState.Error("Empty response")
                                    return@withContext Result.failure(IOException("Empty response"))
                                }

                        val totalBytes = body.contentLength() + startByte
                        var downloadedBytes = startByte
                        var lastReportedProgress = -1

                        FileOutputStream(tempFile, startByte > 0).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            body.byteStream().use { input ->
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (isCancelled.get()) {
                                        _downloadState.value = DownloadState.Cancelled
                                        return@withContext Result.failure(
                                            IOException("Download cancelled"),
                                        )
                                    }

                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    // Update progress (throttled)
                                    val progress = downloadedBytes.toFloat() / totalBytes
                                    val currentPercent = (progress * 100).toInt()
                                    if (currentPercent > lastReportedProgress) {
                                        _downloadState.value =
                                            DownloadState.Downloading(
                                                progress = progress,
                                                downloadedBytes = downloadedBytes,
                                                totalBytes = totalBytes,
                                            )
                                        lastReportedProgress = currentPercent
                                    }
                                }
                            }
                        }
                    }

                    currentCall = null

                    // Rename temp file to final
                    if (!tempFile.renameTo(targetFile)) {
                        val error = "Failed to rename temp file"
                        Log.e(TAG, error)
                        _downloadState.value = DownloadState.Error(error)
                        return@withContext Result.failure(IOException(error))
                    }

                    currentDownloadJob = null
                    Log.i(TAG, "GLM-ASR model downloaded: ${targetFile.absolutePath}")
                    _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                    Result.success(targetFile.absolutePath)
                } catch (e: Exception) {
                    currentCall = null
                    currentDownloadJob = null
                    Log.e(TAG, "GLM-ASR download failed", e)
                    _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
                    Result.failure(e)
                }
            }

        /**
         * Download all GLM-ASR models sequentially.
         */
        suspend fun downloadAllGLMASRModels(): Result<Unit> {
            for (spec in GLMASRModelSpec.entries) {
                val result = downloadGLMASRModel(spec)
                if (result.isFailure) {
                    return Result.failure(
                        result.exceptionOrNull() ?: IOException("Download failed"),
                    )
                }
            }
            return Result.success(Unit)
        }

        /**
         * Delete a specific GLM-ASR model.
         */
        fun deleteGLMASRModel(spec: GLMASRModelSpec): Boolean {
            val file = File(getGLMASRModelsDirectory(), spec.filename)
            val tempFile = File(getGLMASRModelsDirectory(), "${spec.filename}.download")

            var deleted = false
            if (file.exists()) {
                deleted = file.delete()
                Log.i(TAG, "Deleted GLM-ASR model: ${spec.filename}, success: $deleted")
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }

            return deleted
        }

        /**
         * Delete all GLM-ASR models.
         */
        fun deleteAllGLMASRModels(): Boolean {
            var allDeleted = true
            for (spec in GLMASRModelSpec.entries) {
                if (!deleteGLMASRModel(spec)) {
                    allDeleted = false
                }
            }
            return allDeleted
        }
    }
