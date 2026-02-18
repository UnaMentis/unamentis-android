package com.unamentis.services.tts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Kyutai Pocket TTS model files.
 *
 * Handles downloading, validating, and managing the on-device Pocket TTS model.
 * Model files (~230MB total) are stored in the app's internal files directory
 * at `files/models/kyutai/`.
 *
 * Expected directory structure for the native engine:
 * ```
 * models/kyutai/
 *   model.safetensors    - Main transformer weights (~225MB)
 *   tokenizer.model      - SentencePiece tokenizer
 *   voices/              - Voice embedding directory
 *     alba.safetensors
 *     marius.safetensors
 *     ...                - 8 voice files total
 * ```
 *
 * @property context Application context for accessing internal storage
 */
@Singleton
class KyutaiPocketModelManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KyutaiModelMgr"

            /** Subdirectory within filesDir for Kyutai models. */
            private const val MODEL_DIR = "models/kyutai"

            /** Main model weights filename. */
            private const val MODEL_WEIGHTS_FILE = "model.safetensors"

            /** Tokenizer filename. */
            private const val TOKENIZER_FILE = "tokenizer.model"

            /** Voice embeddings subdirectory. */
            private const val VOICES_DIR = "voices"

            /** Expected number of built-in voices. */
            private const val EXPECTED_VOICE_COUNT = 8

            /** Total model size in bytes (~230MB). */
            private const val MODEL_SIZE_BYTES = 241_172_480L
        }

        /**
         * Model download/availability state.
         */
        enum class ModelState {
            /** Model files are not present on device. */
            NOT_DOWNLOADED,

            /** Model download is in progress. */
            DOWNLOADING,

            /** Model files are present and validated. */
            AVAILABLE,

            /** An error occurred during download or validation. */
            ERROR,
        }

        private val _modelState = MutableStateFlow(ModelState.NOT_DOWNLOADED)

        /** Observable state of the model. */
        val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

        private val _downloadProgress = MutableStateFlow(0f)

        /**
         * Download progress as a fraction from 0.0 to 1.0.
         *
         * Only meaningful when [modelState] is [ModelState.DOWNLOADING].
         */
        val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

        /** Base directory for Kyutai Pocket model files. */
        private val modelDirectory: File
            get() = File(context.filesDir, MODEL_DIR)

        init {
            refreshModelState()
        }

        /**
         * Check whether all required model files are present on device.
         *
         * Validates the existence of model weights, tokenizer, and voice embeddings.
         *
         * @return `true` if all required model files exist
         */
        fun isModelDownloaded(): Boolean {
            val weightsExist = File(modelDirectory, MODEL_WEIGHTS_FILE).exists()
            val tokenizerExists = File(modelDirectory, TOKENIZER_FILE).exists()
            val voicesDirExists = File(modelDirectory, VOICES_DIR).isDirectory

            val result = weightsExist && tokenizerExists && voicesDirExists
            Log.d(
                TAG,
                "isModelDownloaded: $result " +
                    "(weights=$weightsExist, tokenizer=$tokenizerExists, voices=$voicesDirExists)",
            )
            return result
        }

        /**
         * Download the Pocket TTS model files.
         *
         * This is a stub implementation. The actual download logic will use
         * the model distribution server or bundled assets.
         *
         * @throws UnsupportedOperationException Always, until download is implemented
         */
        suspend fun downloadModel() {
            Log.i(TAG, "downloadModel called")
            _modelState.value = ModelState.DOWNLOADING
            _downloadProgress.value = 0f

            // TODO: Implement model download from models.unamentis.com
            // Steps will include:
            // 1. Check available disk space
            // 2. Download model archive from server
            // 3. Verify checksum
            // 4. Extract to modelDirectory
            // 5. Validate all required files

            Log.w(TAG, "Model download not yet implemented - JNI bindings pending")
            _modelState.value = ModelState.ERROR
            _downloadProgress.value = 0f

            throw UnsupportedOperationException(
                "Model download not yet implemented. Copy model files manually to ${modelDirectory.absolutePath}",
            )
        }

        /**
         * Delete all model files from device storage.
         *
         * Frees disk space by removing the entire Kyutai model directory.
         *
         * @return `true` if deletion was successful or directory did not exist
         */
        fun deleteModel(): Boolean {
            Log.i(TAG, "deleteModel called")

            return if (modelDirectory.exists()) {
                val deleted = modelDirectory.deleteRecursively()
                if (deleted) {
                    Log.i(TAG, "Model files deleted successfully")
                    _modelState.value = ModelState.NOT_DOWNLOADED
                } else {
                    Log.e(TAG, "Failed to delete model files")
                }
                deleted
            } else {
                Log.d(TAG, "Model directory does not exist, nothing to delete")
                _modelState.value = ModelState.NOT_DOWNLOADED
                true
            }
        }

        /**
         * Get the absolute path to the model directory.
         *
         * This path is passed to the native JNI engine for model loading.
         *
         * @return Absolute path to the model directory
         */
        fun getModelPath(): String {
            val path = modelDirectory.absolutePath
            Log.d(TAG, "getModelPath: $path")
            return path
        }

        /**
         * Get the total size of downloaded model files in bytes.
         *
         * @return Size in bytes, or 0 if model is not downloaded
         */
        fun getModelSize(): Long {
            if (!modelDirectory.exists()) {
                return 0L
            }

            val size =
                modelDirectory.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }

            Log.d(TAG, "getModelSize: $size bytes")
            return size
        }

        /**
         * Get the expected total model size in bytes.
         *
         * @return Expected model size in bytes
         */
        fun getExpectedModelSize(): Long = MODEL_SIZE_BYTES

        /**
         * Refresh the model state by checking file system.
         */
        private fun refreshModelState() {
            _modelState.value =
                if (isModelDownloaded()) {
                    Log.i(TAG, "Model files found at: ${modelDirectory.absolutePath}")
                    ModelState.AVAILABLE
                } else {
                    Log.i(TAG, "Model files not found, need to be installed")
                    ModelState.NOT_DOWNLOADED
                }
        }
    }
