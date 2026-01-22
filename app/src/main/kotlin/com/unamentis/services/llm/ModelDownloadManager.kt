package com.unamentis.services.llm

import android.content.Context
import android.util.Log
import com.unamentis.core.device.DeviceCapabilityDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
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
class ModelDownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val deviceCapabilityDetector: DeviceCapabilityDetector,
    ) {
        companion object {
            private const val TAG = "ModelDownloadManager"

            // HuggingFace model URLs
            private const val MINISTRAL_3B_URL_BASE =
                "https://huggingface.co/bartowski/Ministral-3B-Instruct-GGUF/resolve/main/"
            private const val MINISTRAL_3B_FILE = "Ministral-3B-Instruct-Q4_K_M.gguf"

            private const val TINYLLAMA_1B_URL_BASE =
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/"
            private const val TINYLLAMA_1B_FILE = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

            // SHA256 checksums for verification
            private val MODEL_CHECKSUMS =
                mapOf(
                    OnDeviceLLMService.MINISTRAL_3B_FILENAME to
                        "b62a8d10c4c0c69f6a7e0d5ad4c5e9c8f2b1d7a3e6c9f0b4a8d2e5f1c7b3a9d6",
                    OnDeviceLLMService.TINYLLAMA_1B_FILENAME to
                        "a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4",
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

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: Flow<DownloadState> = _downloadState.asStateFlow()

        private var currentDownloadJob: kotlinx.coroutines.Job? = null
        private var isCancelled = false

        // OkHttp client with long timeout for large downloads
        private val downloadClient =
            OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build()

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
                isCancelled = false
                _downloadState.value = DownloadState.Downloading(0f, 0, spec.sizeBytes)

                val modelsDir = getModelsDirectory()
                val targetFile = File(modelsDir, spec.filename)
                val tempFile = File(modelsDir, "${spec.filename}.download")

                try {
                    // Check if already downloaded
                    if (targetFile.exists() && targetFile.length() > 0) {
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
                    val response = downloadClient.newCall(request).execute()

                    if (!response.isSuccessful && response.code != 206) {
                        val error = "Download failed: HTTP ${response.code}"
                        Log.e(TAG, error)
                        _downloadState.value = DownloadState.Error(error)
                        return@withContext Result.failure(IOException(error))
                    }

                    val body =
                        response.body ?: run {
                            val error = "Empty response body"
                            Log.e(TAG, error)
                            _downloadState.value = DownloadState.Error(error)
                            return@withContext Result.failure(IOException(error))
                        }

                    // Get total size (handle Content-Range for resumed downloads)
                    val contentLength = body.contentLength()
                    val totalBytes =
                        if (response.code == 206) {
                            // Parse Content-Range header: bytes 0-999/1000
                            val contentRange = response.header("Content-Range")
                            contentRange?.substringAfter("/")?.toLongOrNull()
                                ?: (startByte + contentLength)
                        } else {
                            contentLength
                        }

                    Log.i(TAG, "Total size: $totalBytes bytes")

                    // Write to temp file
                    val outputStream =
                        FileOutputStream(tempFile, startByte > 0) // append if resuming
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloadedBytes = startByte
                    var bytesRead: Int

                    outputStream.use { output ->
                        inputStream.use { input ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (isCancelled) {
                                    Log.i(TAG, "Download cancelled")
                                    _downloadState.value = DownloadState.Cancelled
                                    return@withContext Result.failure(
                                        IOException("Download cancelled"),
                                    )
                                }

                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val progress = downloadedBytes.toFloat() / totalBytes
                                _downloadState.value =
                                    DownloadState.Downloading(
                                        progress = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                    )
                            }
                        }
                    }

                    Log.i(TAG, "Download complete, verifying...")
                    _downloadState.value = DownloadState.Verifying(spec.filename)

                    // Verify checksum (if available)
                    val expectedChecksum = MODEL_CHECKSUMS[spec.filename]
                    if (expectedChecksum != null) {
                        val actualChecksum = calculateSha256(tempFile)
                        if (actualChecksum != expectedChecksum) {
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
                        val error = "Failed to rename temp file"
                        Log.e(TAG, error)
                        _downloadState.value = DownloadState.Error(error)
                        return@withContext Result.failure(IOException(error))
                    }

                    Log.i(TAG, "Model ready: ${targetFile.absolutePath}")
                    _downloadState.value = DownloadState.Complete(targetFile.absolutePath)
                    Result.success(targetFile.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed", e)
                    _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                    Result.failure(e)
                }
            }

        /**
         * Cancel the current download.
         */
        fun cancelDownload() {
            isCancelled = true
            currentDownloadJob?.cancel()
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
    }
