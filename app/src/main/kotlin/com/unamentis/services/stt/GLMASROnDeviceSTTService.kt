package com.unamentis.services.stt

import android.content.Context
import android.util.Log
import com.unamentis.core.device.DeviceCapabilityDetector
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GLM-ASR On-Device Speech-to-Text service.
 *
 * Provides fully on-device speech recognition using GLM-ASR-Nano-2512.
 *
 * Pipeline:
 * 1. Audio (16kHz PCM) -> Mel Spectrogram
 * 2. Mel Spectrogram -> Whisper Encoder (ONNX Runtime)
 * 3. Encoded Audio -> Audio Adapter (ONNX Runtime)
 * 4. Adapted Features -> Embed Head (ONNX Runtime)
 * 5. Embeddings -> Text Decoder (llama.cpp GGUF)
 * 6. Tokens -> Transcript
 *
 * Features:
 * - Zero cost (no API fees)
 * - Complete privacy (audio never leaves device)
 * - Works offline
 * - Low latency for on-device inference
 *
 * Requirements:
 * - 8GB+ RAM (12GB recommended)
 * - Android 12+ (API 31+)
 * - ~2.4GB storage for models
 *
 * @property context Application context
 * @property config On-device configuration
 */
@Singleton
@Suppress("TooManyFunctions")
class GLMASROnDeviceSTTService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : STTService {
        companion object {
            private const val TAG = "GLMASROnDevice"

            // Audio processing constants
            private const val SAMPLE_RATE = 16000
            private const val CHUNK_DURATION_MS = 3000 // Process 3 seconds at a time
            private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_DURATION_MS / 1000

            // Stub transcript for testing when ONNX Runtime is not available
            private const val STUB_TRANSCRIPT = "[Stub: Audio transcription would appear here]"

            // Native library for ONNX Runtime (optional)
            private var onnxAvailable = false

            init {
                try {
                    // Try to load ONNX Runtime native library
                    System.loadLibrary("onnxruntime")
                    onnxAvailable = true
                    Log.i(TAG, "ONNX Runtime native library loaded")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "ONNX Runtime not available, on-device GLM-ASR disabled")
                    onnxAvailable = false
                }
            }
        }

        override val providerName: String = "GLMASROnDevice"

        // Configuration
        private var config: GLMASROnDeviceConfig? = null

        // Model loading state
        private val isLoaded = AtomicBoolean(false)
        private val loadMutex = Mutex()

        // Streaming state
        private val isStreaming = AtomicBoolean(false)
        private val audioBuffer = mutableListOf<Float>()
        private val bufferMutex = Mutex()
        private var sessionStartTime = 0L

        // ONNX Runtime sessions (native pointers)
        private val whisperEncoderPtr = AtomicLong(0)
        private val audioAdapterPtr = AtomicLong(0)
        private val embedHeadPtr = AtomicLong(0)

        // llama.cpp context for text decoding
        private val llamaContextPtr = AtomicLong(0)

        // Mel spectrogram processor
        private val melSpectrogram = GLMASRMelSpectrogram()

        // Metrics
        private val latencyMeasurements = mutableListOf<Long>()

        /**
         * Check if on-device GLM-ASR is supported on this device.
         */
        fun isSupported(): Boolean {
            if (!onnxAvailable) {
                Log.d(TAG, "ONNX Runtime not available")
                return false
            }

            val detector = DeviceCapabilityDetector(context)
            return detector.supportsGLMASROnDevice(checkModels = false)
        }

        /**
         * Check if models are downloaded and ready.
         */
        fun areModelsReady(): Boolean {
            val cfg = config ?: GLMASROnDeviceConfig.default(context.filesDir)
            return cfg.areModelsPresent()
        }

        /**
         * Initialize with configuration.
         *
         * @param config On-device configuration
         */
        fun initialize(config: GLMASROnDeviceConfig) {
            this.config = config
            Log.i(TAG, "Initialized with model directory: ${config.modelDirectory}")
        }

        /**
         * Load all models for on-device inference.
         *
         * This is a heavyweight operation that should be called during
         * app initialization or when the user enables on-device mode.
         *
         * @return true if all models loaded successfully
         */
        suspend fun loadModels(): Boolean =
            loadMutex.withLock {
                if (isLoaded.get()) {
                    Log.d(TAG, "Models already loaded")
                    return@withLock true
                }

                val cfg = config ?: GLMASROnDeviceConfig.default(context.filesDir)

                if (!cfg.areModelsPresent()) {
                    val missing = cfg.getMissingModels()
                    Log.e(TAG, "Missing model files: $missing")
                    return@withLock false
                }

                Log.i(TAG, "Loading GLM-ASR models...")

                return@withLock withContext(Dispatchers.IO) {
                    try {
                        // Load ONNX models
                        if (!loadONNXModels(cfg)) {
                            return@withContext false
                        }

                        // Load llama.cpp decoder
                        if (!loadLlamaDecoder(cfg)) {
                            unloadONNXModels()
                            return@withContext false
                        }

                        isLoaded.set(true)
                        Log.i(TAG, "All models loaded successfully")
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load models", e)
                        unloadAllModels()
                        false
                    }
                }
            }

        /**
         * Unload all models and free resources.
         */
        fun unloadModels() {
            if (!isLoaded.get()) return

            Log.i(TAG, "Unloading models...")
            unloadAllModels()
            isLoaded.set(false)
            Log.i(TAG, "Models unloaded")
        }

        /**
         * Check if models are loaded.
         */
        fun isLoaded(): Boolean = isLoaded.get()

        // ==================== STTService Implementation ====================

        override fun startStreaming(): Flow<STTResult> =
            callbackFlow<STTResult> {
                if (!isLoaded.get()) {
                    // Try to load models
                    if (!loadModels()) {
                        close(IllegalStateException("GLM-ASR models not loaded and failed to load"))
                        return@callbackFlow
                    }
                }

                if (isStreaming.getAndSet(true)) {
                    Log.w(TAG, "Already streaming, closing existing stream")
                }

                sessionStartTime = System.currentTimeMillis()
                audioBuffer.clear()
                latencyMeasurements.clear()

                Log.i(TAG, "Started on-device streaming")

                awaitClose {
                    doStopStreaming()
                }
            }.flowOn(Dispatchers.IO)

        /**
         * Send audio samples for transcription.
         *
         * Audio should be 16kHz mono float samples normalized to [-1.0, 1.0].
         *
         * @param samples Audio samples
         */
        suspend fun sendAudioSamples(samples: FloatArray) {
            if (!isStreaming.get()) {
                Log.w(TAG, "Not streaming, ignoring audio")
                return
            }

            bufferMutex.withLock {
                audioBuffer.addAll(samples.toList())

                // Process when we have enough samples
                if (audioBuffer.size >= CHUNK_SAMPLES) {
                    val chunk = audioBuffer.take(CHUNK_SAMPLES).toFloatArray()
                    audioBuffer.subList(0, CHUNK_SAMPLES).clear()

                    processAudioChunk(chunk)
                }
            }
        }

        /**
         * Send raw PCM audio data (16-bit signed, 16kHz mono).
         *
         * @param audioData Raw PCM bytes
         */
        suspend fun sendAudioData(audioData: ByteArray) {
            // Convert Int16 PCM to Float samples
            val samples = int16ToFloat(audioData)
            sendAudioSamples(samples)
        }

        override suspend fun stopStreaming() {
            doStopStreaming()
        }

        private fun doStopStreaming() {
            if (!isStreaming.getAndSet(false)) {
                return
            }

            Log.i(TAG, "Stopping on-device streaming")

            // Process any remaining audio
            val remainingAudio = audioBuffer.toFloatArray()
            audioBuffer.clear()

            if (remainingAudio.isNotEmpty()) {
                // Process synchronously before stopping
                // In a real implementation, we'd emit the final result
                processAudioChunkSync(remainingAudio)
            }

            logSessionMetrics()
        }

        // ==================== Audio Processing ====================

        /**
         * Process an audio chunk through the GLM-ASR pipeline.
         */
        private suspend fun processAudioChunk(samples: FloatArray) {
            withContext(Dispatchers.Default) {
                processAudioChunkSync(samples)
            }
        }

        /**
         * Process audio chunk synchronously (for final processing).
         */
        @Suppress("ReturnCount")
        private fun processAudioChunkSync(samples: FloatArray): STTResult? {
            val startTime = System.currentTimeMillis()

            return try {
                // Run the full pipeline
                val transcript = runPipeline(samples) ?: return null

                val latency = System.currentTimeMillis() - startTime
                latencyMeasurements.add(latency)

                Log.d(TAG, "Processed chunk: \"$transcript\" (${latency}ms)")

                // On-device doesn't provide confidence, use default value
                STTResult(
                    text = transcript,
                    isFinal = true,
                    confidence = 0.9f,
                    latencyMs = latency,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error processing audio chunk", e)
                null
            }
        }

        /**
         * Run the full GLM-ASR pipeline on audio samples.
         */
        @Suppress("ReturnCount")
        private fun runPipeline(samples: FloatArray): String? {
            // Step 1: Compute mel spectrogram
            val melSpec = melSpectrogram.computeFlat(samples)
            if (melSpec.isEmpty()) {
                Log.w(TAG, "Empty mel spectrogram")
                return null
            }

            // Step 2: Run through ONNX pipeline (stub for now)
            val encodedAudio = runWhisperEncoder(melSpec) ?: return null
            val adaptedAudio = runAudioAdapter(encodedAudio) ?: return null
            val embeddings = runEmbedHead(adaptedAudio) ?: return null

            // Step 3: Run llama.cpp decoder
            return runLlamaDecoder(embeddings)
        }

        // ==================== ONNX Runtime Integration ====================

        /**
         * Load ONNX models.
         *
         * NOTE: This is a stub implementation. Full implementation requires
         * ONNX Runtime Android library integration.
         */
        @Suppress("UnusedParameter")
        private fun loadONNXModels(cfg: GLMASROnDeviceConfig): Boolean {
            if (!onnxAvailable) {
                Log.w(TAG, "ONNX Runtime not available, using stub implementation")
                // Return true to allow testing with stub
                return true
            }

            // TODO: Implement actual ONNX model loading when library is added
            // whisperEncoderPtr.set(nativeLoadONNXModel(cfg.whisperEncoderPath.absolutePath, cfg.useGPU))
            // audioAdapterPtr.set(nativeLoadONNXModel(cfg.audioAdapterPath.absolutePath, cfg.useGPU))
            // embedHeadPtr.set(nativeLoadONNXModel(cfg.embedHeadPath.absolutePath, cfg.useGPU))

            Log.i(TAG, "ONNX models loaded (stub)")
            return true
        }

        private fun unloadONNXModels() {
            // TODO: Implement ONNX model unloading
            whisperEncoderPtr.set(0)
            audioAdapterPtr.set(0)
            embedHeadPtr.set(0)
        }

        /**
         * Run Whisper encoder on mel spectrogram.
         *
         * @param melSpec Flattened mel spectrogram [nMels * nFrames]
         * @return Encoded audio features, or null on error
         */
        @Suppress("UnusedParameter")
        private fun runWhisperEncoder(melSpec: FloatArray): FloatArray? {
            if (!onnxAvailable) {
                // Stub: Return simulated encoded output
                return FloatArray(1500 * 1280) // Expected output shape
            }

            // TODO: Run actual ONNX inference
            // return nativeRunONNXInference(whisperEncoderPtr.get(), melSpec)
            return null
        }

        /**
         * Run audio adapter to align features with LLM space.
         *
         * @param encodedAudio Whisper encoder output
         * @return Adapted features, or null on error
         */
        @Suppress("UnusedParameter")
        private fun runAudioAdapter(encodedAudio: FloatArray): FloatArray? {
            if (!onnxAvailable) {
                // Stub: Return simulated adapted output
                return FloatArray(375 * 2048) // Expected output shape
            }

            // TODO: Run actual ONNX inference
            // return nativeRunONNXInference(audioAdapterPtr.get(), encodedAudio)
            return null
        }

        /**
         * Run embed head to produce token embeddings.
         *
         * @param adaptedAudio Adapter output
         * @return Token embeddings, or null on error
         */
        @Suppress("UnusedParameter")
        private fun runEmbedHead(adaptedAudio: FloatArray): FloatArray? {
            if (!onnxAvailable) {
                // Stub: Return simulated embeddings
                return FloatArray(375 * 4096) // Expected output shape
            }

            // TODO: Run actual ONNX inference
            // return nativeRunONNXInference(embedHeadPtr.get(), adaptedAudio)
            return null
        }

        // ==================== llama.cpp Decoder ====================

        /**
         * Load llama.cpp decoder model.
         */
        private fun loadLlamaDecoder(cfg: GLMASROnDeviceConfig): Boolean {
            val decoderPath = cfg.decoderPath
            if (!decoderPath.exists()) {
                Log.e(TAG, "Decoder model not found: ${decoderPath.absolutePath}")
                return false
            }

            // Use the native llama.cpp bindings from OnDeviceLLMService
            // TODO: Implement decoder loading through JNI
            // llamaContextPtr.set(nativeLoadLlamaModel(decoderPath.absolutePath, cfg.numThreads, cfg.gpuLayers))

            Log.i(TAG, "llama.cpp decoder loaded (stub)")
            return true
        }

        private fun unloadLlamaDecoder() {
            val ptr = llamaContextPtr.getAndSet(0)
            if (ptr != 0L) {
                // nativeFreeLlamaModel(ptr)
            }
        }

        /**
         * Run llama.cpp decoder on embeddings to produce text.
         *
         * @param embeddings Token embeddings from embed head
         * @return Transcribed text, or null on error
         */
        @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
        private fun runLlamaDecoder(embeddings: FloatArray): String {
            // TODO: Implement actual decoding with audio embeddings
            // For now, return stub output

            // The real implementation would:
            // 1. Inject embeddings into the decoder context
            // 2. Run autoregressive generation
            // 3. Detokenize and return text

            // Stub implementation - always returns placeholder text
            return STUB_TRANSCRIPT
        }

        // ==================== Utilities ====================

        private fun unloadAllModels() {
            unloadONNXModels()
            unloadLlamaDecoder()
        }

        /**
         * Convert Int16 PCM bytes to float samples.
         */
        private fun int16ToFloat(audioData: ByteArray): FloatArray {
            val numSamples = audioData.size / 2
            val samples = FloatArray(numSamples)

            val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                val sample = buffer.getShort()
                samples[i] = sample.toFloat() / Short.MAX_VALUE
            }

            return samples
        }

        private fun logSessionMetrics() {
            if (latencyMeasurements.isEmpty()) return

            val sortedLatencies = latencyMeasurements.sorted()
            val medianLatency = sortedLatencies[sortedLatencies.size / 2]
            val p99Index = (sortedLatencies.size * 0.99).toInt().coerceAtMost(sortedLatencies.size - 1)
            val p99Latency = sortedLatencies[p99Index]

            Log.i(
                TAG,
                "Session metrics: " +
                    "chunks=${latencyMeasurements.size}, " +
                    "median=${medianLatency}ms, " +
                    "p99=${p99Latency}ms",
            )
        }

        /**
         * Get model directory.
         */
        fun getModelDirectory(): File {
            return config?.modelDirectory ?: context.filesDir.resolve("models/glm-asr-nano")
        }

        /**
         * Get session metrics.
         */
        fun getMetrics(): GLMASROnDeviceMetrics {
            if (latencyMeasurements.isEmpty()) {
                return GLMASROnDeviceMetrics(0, 0, 0)
            }

            val sortedLatencies = latencyMeasurements.sorted()
            val medianLatency = sortedLatencies[sortedLatencies.size / 2]
            val p99Index = (sortedLatencies.size * 0.99).toInt().coerceAtMost(sortedLatencies.size - 1)
            val p99Latency = sortedLatencies[p99Index]

            return GLMASROnDeviceMetrics(
                medianLatencyMs = medianLatency,
                p99LatencyMs = p99Latency,
                chunksProcessed = latencyMeasurements.size,
            )
        }

        /**
         * Metrics for on-device GLM-ASR.
         */
        data class GLMASROnDeviceMetrics(
            val medianLatencyMs: Long,
            val p99LatencyMs: Long,
            val chunksProcessed: Int,
        )
    }
