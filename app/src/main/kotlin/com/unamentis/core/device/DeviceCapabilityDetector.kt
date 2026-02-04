package com.unamentis.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device capability detector.
 *
 * Detects device hardware capabilities and classifies device tier
 * for adaptive performance configuration.
 *
 * Device tiers:
 * - FLAGSHIP: High-end devices (8GB+ RAM, recent CPU)
 * - STANDARD: Mid-range devices (4-8GB RAM)
 * - MINIMUM: Entry-level devices (<4GB RAM)
 *
 * Used to configure:
 * - Default provider selection (cloud vs on-device)
 * - Audio buffer sizes
 * - ML model selection
 * - Concurrent processing limits
 */
@Singleton
class DeviceCapabilityDetector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "DeviceCapability"

            // Qualcomm Snapdragon 8 Gen2+ SoC models (support QNN NPU acceleration)
            private val QUALCOMM_NPU_SOCS =
                setOf(
                    // Snapdragon 8 Gen 2
                    "SM8550",
                    // Snapdragon 8 Gen 3
                    "SM8650",
                    // Snapdragon 8 Gen 4
                    "SM8750",
                    // Snapdragon 8 Elite
                    "SM8750-AB",
                    // Snapdragon 8 Gen 1 (limited QNN support)
                    "taro",
                    // Snapdragon 8 Gen 2 codename
                    "kalama",
                    // Snapdragon 8 Gen 3 codename
                    "pineapple",
                )

            // SoCs known to have good OpenCL GPU support
            private val OPENCL_GPU_SOCS =
                setOf(
                    // Qualcomm Snapdragon (Adreno GPUs)
                    "SM8550",
                    "SM8650",
                    "SM8750",
                    "SM8450",
                    "SM8475",
                    "SM8350",
                    "SM7675",
                    "SM7550",
                    "kalama",
                    "pineapple",
                    "taro",
                    // MediaTek Dimensity (Mali GPUs)
                    "MT6989",
                    "MT6985",
                    "MT6983",
                    // Samsung Exynos (RDNA GPUs - best Vulkan support)
                    "s5e9945",
                    "s5e9935",
                )
        }

        private val activityManager: ActivityManager? = context.getSystemService()

        /**
         * Device tier classification.
         */
        enum class DeviceTier {
            FLAGSHIP,
            STANDARD,
            MINIMUM,
        }

        /**
         * On-device LLM model specifications.
         *
         * Matches iOS implementation for feature parity.
         * Models are GGUF format, compatible with llama.cpp.
         */
        enum class OnDeviceModelSpec(
            val filename: String,
            val displayName: String,
            val sizeBytes: Long,
            val minRamMB: Int,
            val recommendedRamMB: Int,
        ) {
            /**
             * Ministral 3B Instruct Q4_K_M - Primary model.
             * Best quality for devices with 6GB+ RAM.
             * Size: ~2.1GB, requires 6GB RAM, recommended 8GB.
             */
            MINISTRAL_3B(
                filename = "ministral-3b-instruct-q4_k_m.gguf",
                displayName = "Ministral 3B",
                sizeBytes = 2_100_000_000L,
                minRamMB = 6144,
                recommendedRamMB = 8192,
            ),

            /**
             * TinyLlama 1.1B Chat Q4_K_M - Fallback model.
             * Works on devices with 3GB+ RAM.
             * Size: ~670MB, requires 3GB RAM, recommended 4GB.
             */
            TINYLLAMA_1B(
                filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                displayName = "TinyLlama 1.1B",
                sizeBytes = 670_000_000L,
                minRamMB = 3072,
                recommendedRamMB = 4096,
            ),
        }

        /**
         * Device capabilities summary.
         */
        data class DeviceCapabilities(
            val tier: DeviceTier,
            val totalRamMB: Int,
            val availableRamMB: Int,
            val cpuCores: Int,
            val apiLevel: Int,
            val supportsNNAPI: Boolean,
            val supportsVulkan: Boolean,
            val supportsOnDeviceLLM: Boolean,
            val recommendedLLMModel: OnDeviceModelSpec?,
            val manufacturer: String,
            val model: String,
            /** Whether device has Qualcomm NPU for QNN acceleration */
            val hasQualcommNPU: Boolean = false,
            /** Whether device has OpenCL GPU support */
            val hasOpenCLGPU: Boolean = false,
            /** SoC platform identifier */
            val socPlatform: String = "",
        )

        /**
         * Detect and classify device capabilities.
         */
        fun detect(): DeviceCapabilities {
            val totalRamMB = getTotalRamMB()
            val availableRamMB = getAvailableRamMB()
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val apiLevel = Build.VERSION.SDK_INT
            val supportsNNAPI = apiLevel >= 27 // Android 8.1+
            val supportsVulkan = apiLevel >= 24 // Android 7.0+

            val tier = classifyTier(totalRamMB, cpuCores, apiLevel)

            // Detect SoC platform for NPU/GPU capabilities
            val socPlatform = detectSoCPlatform()
            val hasQualcommNPU = checkQualcommNPUSupport(socPlatform)
            val hasOpenCLGPU = checkOpenCLGPUSupport(socPlatform)

            Log.i(TAG, "SoC: $socPlatform, NPU: $hasQualcommNPU, OpenCL: $hasOpenCLGPU")

            // Check on-device LLM support (3GB+ RAM, 4+ cores, Android 8.0+)
            val canRunOnDeviceLLM =
                totalRamMB >= OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                    cpuCores >= 4 &&
                    apiLevel >= 26

            // Determine recommended LLM model
            val recommendedModel =
                if (canRunOnDeviceLLM) {
                    if (totalRamMB >= OnDeviceModelSpec.MINISTRAL_3B.minRamMB) {
                        OnDeviceModelSpec.MINISTRAL_3B
                    } else {
                        OnDeviceModelSpec.TINYLLAMA_1B
                    }
                } else {
                    null
                }

            return DeviceCapabilities(
                tier = tier,
                totalRamMB = totalRamMB,
                availableRamMB = availableRamMB,
                cpuCores = cpuCores,
                apiLevel = apiLevel,
                supportsNNAPI = supportsNNAPI,
                supportsVulkan = supportsVulkan,
                supportsOnDeviceLLM = canRunOnDeviceLLM,
                recommendedLLMModel = recommendedModel,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                hasQualcommNPU = hasQualcommNPU,
                hasOpenCLGPU = hasOpenCLGPU,
                socPlatform = socPlatform,
            )
        }

        /**
         * Get total device RAM in MB.
         */
        private fun getTotalRamMB(): Int {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            return (memoryInfo.totalMem / (1024 * 1024)).toInt()
        }

        /**
         * Get available RAM in MB.
         */
        private fun getAvailableRamMB(): Int {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            return (memoryInfo.availMem / (1024 * 1024)).toInt()
        }

        /**
         * Classify device tier based on capabilities.
         */
        private fun classifyTier(
            totalRamMB: Int,
            cpuCores: Int,
            apiLevel: Int,
        ): DeviceTier {
            // Flagship tier: 8GB+ RAM, 6+ cores, Android 11+
            if (totalRamMB >= 8192 && cpuCores >= 6 && apiLevel >= 30) {
                return DeviceTier.FLAGSHIP
            }

            // Standard tier: 4GB+ RAM, 4+ cores
            if (totalRamMB >= 4096 && cpuCores >= 4) {
                return DeviceTier.STANDARD
            }

            // Minimum tier: everything else
            return DeviceTier.MINIMUM
        }

        /**
         * Get recommended configuration based on device tier.
         */
        fun getRecommendedConfig(): RecommendedConfig {
            val capabilities = detect()

            return when (capabilities.tier) {
                DeviceTier.FLAGSHIP ->
                    RecommendedConfig(
                        useCloudSTT = true,
                        useCloudTTS = true,
                        useCloudLLM = true,
                        // 32ms at 16kHz
                        audioBufferSize = 512,
                        maxConcurrentRequests = 3,
                        enableNNAPI = capabilities.supportsNNAPI,
                    )
                DeviceTier.STANDARD ->
                    RecommendedConfig(
                        useCloudSTT = true,
                        useCloudTTS = true,
                        useCloudLLM = true,
                        // 64ms at 16kHz
                        audioBufferSize = 1024,
                        maxConcurrentRequests = 2,
                        enableNNAPI = capabilities.supportsNNAPI,
                    )
                DeviceTier.MINIMUM ->
                    RecommendedConfig(
                        // Still use cloud for quality
                        useCloudSTT = true,
                        // Use Android TTS
                        useCloudTTS = false,
                        // Use on-device LLM if supported, otherwise fall back to cloud
                        useCloudLLM = !capabilities.supportsOnDeviceLLM,
                        // 128ms at 16kHz
                        audioBufferSize = 2048,
                        maxConcurrentRequests = 1,
                        // May not be reliable
                        enableNNAPI = false,
                    )
            }
        }

        /**
         * Recommended configuration for device tier.
         */
        data class RecommendedConfig(
            val useCloudSTT: Boolean,
            val useCloudTTS: Boolean,
            val useCloudLLM: Boolean,
            val audioBufferSize: Int,
            val maxConcurrentRequests: Int,
            val enableNNAPI: Boolean,
        )

        /**
         * Check if device meets minimum requirements.
         */
        fun meetsMinimumRequirements(): Boolean {
            val capabilities = detect()
            return capabilities.totalRamMB >= 2048 && // 2GB minimum
                capabilities.cpuCores >= 2 && // Dual-core minimum
                capabilities.apiLevel >= 26 // Android 8.0 minimum
        }

        /**
         * Check if device supports on-device LLM inference.
         *
         * Requires:
         * - At least 3GB RAM (for TinyLlama fallback)
         * - At least 4 CPU cores
         * - Android 8.0+ (API 26)
         *
         * @return true if device can run on-device LLM (at least TinyLlama)
         */
        fun supportsOnDeviceLLM(): Boolean {
            val capabilities = detect()
            return capabilities.totalRamMB >= OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                capabilities.cpuCores >= 4 &&
                capabilities.apiLevel >= 26
        }

        /**
         * Get the recommended on-device LLM model for this device.
         *
         * Selection logic:
         * - 6GB+ RAM: Ministral 3B (best quality)
         * - 3GB+ RAM: TinyLlama 1.1B (fallback)
         * - <3GB RAM: null (on-device LLM not supported)
         *
         * @return Recommended model spec, or null if device doesn't support on-device LLM
         */
        fun getRecommendedOnDeviceModel(): OnDeviceModelSpec? {
            val capabilities = detect()

            if (!supportsOnDeviceLLM()) {
                return null
            }

            // Prefer Ministral 3B if device has enough RAM
            if (capabilities.totalRamMB >= OnDeviceModelSpec.MINISTRAL_3B.minRamMB) {
                return OnDeviceModelSpec.MINISTRAL_3B
            }

            // Fall back to TinyLlama for lower-RAM devices
            return OnDeviceModelSpec.TINYLLAMA_1B
        }

        /**
         * Get all on-device models that can run on this device.
         *
         * @return List of supported models, ordered by quality (best first)
         */
        fun getSupportedOnDeviceModels(): List<OnDeviceModelSpec> {
            val capabilities = detect()

            if (!supportsOnDeviceLLM()) {
                return emptyList()
            }

            return OnDeviceModelSpec.entries
                .filter { it.minRamMB <= capabilities.totalRamMB }
                .sortedByDescending { it.minRamMB } // Best quality first
        }

        /**
         * Get human-readable device summary.
         */
        fun getDeviceSummary(): String {
            val capabilities = detect()
            return buildString {
                appendLine("Device: ${capabilities.manufacturer} ${capabilities.model}")
                appendLine("Tier: ${capabilities.tier}")
                appendLine("RAM: ${capabilities.totalRamMB}MB total, ${capabilities.availableRamMB}MB available")
                appendLine("CPU: ${capabilities.cpuCores} cores")
                appendLine("Android: API ${capabilities.apiLevel} (${getAndroidVersionName(capabilities.apiLevel)})")
                appendLine("NNAPI: ${if (capabilities.supportsNNAPI) "Yes" else "No"}")
                appendLine("Vulkan: ${if (capabilities.supportsVulkan) "Yes" else "No"}")
                appendLine("On-Device LLM: ${if (capabilities.supportsOnDeviceLLM) "Yes" else "No"}")
                capabilities.recommendedLLMModel?.let { model ->
                    appendLine("Recommended LLM: ${model.displayName}")
                }
            }
        }

        /**
         * Get Android version name from API level.
         */
        private fun getAndroidVersionName(apiLevel: Int): String {
            return when (apiLevel) {
                35 -> "Android 15"
                34 -> "Android 14"
                33 -> "Android 13"
                32 -> "Android 12L"
                31 -> "Android 12"
                30 -> "Android 11"
                29 -> "Android 10"
                28 -> "Android 9"
                27 -> "Android 8.1"
                26 -> "Android 8.0"
                else -> "Android $apiLevel"
            }
        }

        /**
         * Detect the SoC platform identifier.
         */
        private fun detectSoCPlatform(): String {
            // Try multiple sources for SoC identification
            val sources =
                listOf(
                    // API 31+
                    { Build.SOC_MODEL },
                    { getSystemProperty("ro.board.platform") },
                    { getSystemProperty("ro.hardware") },
                    { Build.HARDWARE },
                )

            for (source in sources) {
                try {
                    val value = source()
                    if (!value.isNullOrBlank() && value != "unknown") {
                        return value
                    }
                } catch (e: Exception) {
                    // Continue to next source
                }
            }

            return "unknown"
        }

        /**
         * Get a system property value.
         */
        private fun getSystemProperty(key: String): String? {
            return try {
                val process = Runtime.getRuntime().exec("getprop $key")
                val result = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                result.ifEmpty { null }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Check if device has Qualcomm NPU support for QNN acceleration.
         *
         * Requires Snapdragon 8 Gen2+ for best QNN support.
         */
        private fun checkQualcommNPUSupport(socPlatform: String): Boolean {
            // Check if SoC matches known Qualcomm NPU-capable chips
            val normalizedSoc = socPlatform.lowercase()
            return QUALCOMM_NPU_SOCS.any { normalizedSoc.contains(it.lowercase()) }
        }

        /**
         * Check if device has OpenCL GPU support.
         *
         * Checks both known SoCs and attempts to load OpenCL library.
         */
        private fun checkOpenCLGPUSupport(socPlatform: String): Boolean {
            // First check if SoC is known to have good OpenCL support
            val normalizedSoc = socPlatform.lowercase()
            val hasKnownSupport = OPENCL_GPU_SOCS.any { normalizedSoc.contains(it.lowercase()) }

            if (hasKnownSupport) {
                return true
            }

            // Try to load OpenCL library as fallback check
            return try {
                System.loadLibrary("OpenCL")
                true
            } catch (e: UnsatisfiedLinkError) {
                // OpenCL not available
                false
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Check if device has Qualcomm NPU for QNN acceleration.
         *
         * @return true if device supports ExecuTorch with QNN backend
         */
        fun hasQualcommNPU(): Boolean {
            return detect().hasQualcommNPU
        }

        /**
         * Check if device has OpenCL GPU support.
         *
         * @return true if device supports MediaPipe with OpenCL backend
         */
        fun hasOpenCLGPU(): Boolean {
            return detect().hasOpenCLGPU
        }

        /**
         * Check if device is a flagship Qualcomm device (Snapdragon 8 Gen2+).
         *
         * @return true if device has Snapdragon 8 Gen2 or newer
         */
        fun isSnapdragon8Gen2OrNewer(): Boolean {
            val socPlatform = detectSoCPlatform().lowercase()
            val flagshipSocs = setOf("sm8550", "sm8650", "sm8750", "kalama", "pineapple")
            return flagshipSocs.any { socPlatform.contains(it) }
        }

        /**
         * Get the recommended LLM backend type for this device.
         *
         * @return Recommended backend type based on device capabilities
         */
        fun getRecommendedLLMBackendType(): LLMBackendType {
            val capabilities = detect()

            return when {
                capabilities.hasQualcommNPU && capabilities.totalRamMB >= 8192 ->
                    LLMBackendType.EXECUTORCH_QNN
                capabilities.hasOpenCLGPU && capabilities.totalRamMB >= 4096 ->
                    LLMBackendType.MEDIAPIPE
                else ->
                    LLMBackendType.LLAMA_CPP
            }
        }

        /**
         * LLM Backend types for device capability detection.
         */
        enum class LLMBackendType {
            EXECUTORCH_QNN,
            MEDIAPIPE,
            LLAMA_CPP,
        }

        // ==================== GLM-ASR On-Device Support ====================

        /**
         * GLM-ASR On-Device model specifications.
         *
         * GLM-ASR-Nano-2512 is a speech-to-text model that can run on-device.
         * Requires ~2.4GB of model storage and 8GB+ RAM for operation.
         */
        object GLMASRModelSpec {
            /** Total size of all model files */
            const val TOTAL_SIZE_BYTES = 2_400_000_000L // ~2.4GB

            /** Minimum RAM required (in MB) */
            const val MIN_RAM_MB = 8192 // 8GB

            /** Recommended RAM for optimal performance (in MB) */
            const val RECOMMENDED_RAM_MB = 12288 // 12GB

            /** Minimum Android API level */
            const val MIN_API_LEVEL = 31 // Android 12

            /** Model files */
            const val WHISPER_ENCODER_FILE = "glm_asr_whisper_encoder.onnx"
            const val AUDIO_ADAPTER_FILE = "glm_asr_audio_adapter.onnx"
            const val EMBED_HEAD_FILE = "glm_asr_embed_head.onnx"
            const val DECODER_FILE = "glm-asr-nano-q4km.gguf"
        }

        /**
         * Check if device supports on-device GLM-ASR speech recognition.
         *
         * Requirements:
         * - 8GB+ RAM minimum (12GB recommended)
         * - Android 12+ (API 31+)
         * - 4+ CPU cores
         * - Models present in app storage (optional check)
         *
         * @param checkModels If true, also verify model files are present
         * @return true if device can run on-device GLM-ASR
         */
        fun supportsGLMASROnDevice(checkModels: Boolean = false): Boolean {
            val capabilities = detect()

            val meetsHardwareRequirements =
                capabilities.totalRamMB >= GLMASRModelSpec.MIN_RAM_MB &&
                    capabilities.cpuCores >= 4 &&
                    capabilities.apiLevel >= GLMASRModelSpec.MIN_API_LEVEL

            if (!meetsHardwareRequirements) {
                Log.d(TAG, "Device does not meet GLM-ASR hardware requirements")
                return false
            }

            if (checkModels) {
                return areGLMASRModelsPresent()
            }

            return true
        }

        /**
         * Check if GLM-ASR model files are present in app storage.
         *
         * @return true if all required model files exist
         */
        fun areGLMASRModelsPresent(): Boolean {
            val modelDir = context.filesDir.resolve("models/glm-asr-nano")
            if (!modelDir.exists()) {
                return false
            }

            val requiredFiles =
                listOf(
                    GLMASRModelSpec.WHISPER_ENCODER_FILE,
                    GLMASRModelSpec.AUDIO_ADAPTER_FILE,
                    GLMASRModelSpec.EMBED_HEAD_FILE,
                    GLMASRModelSpec.DECODER_FILE,
                )

            return requiredFiles.all { modelDir.resolve(it).exists() }
        }

        /**
         * Get the GLM-ASR model directory.
         *
         * @return File pointing to the GLM-ASR model directory
         */
        fun getGLMASRModelDirectory(): java.io.File {
            return context.filesDir.resolve("models/glm-asr-nano")
        }

        /**
         * Check if device has optimal hardware for GLM-ASR on-device.
         *
         * Optimal means:
         * - 12GB+ RAM
         * - Qualcomm NPU or good OpenCL GPU support
         * - High-end SoC (Snapdragon 8 Gen2+)
         *
         * @return true if device has optimal hardware
         */
        fun hasOptimalGLMASRHardware(): Boolean {
            val capabilities = detect()
            return capabilities.totalRamMB >= GLMASRModelSpec.RECOMMENDED_RAM_MB &&
                (capabilities.hasQualcommNPU || capabilities.hasOpenCLGPU) &&
                isSnapdragon8Gen2OrNewer()
        }
    }
