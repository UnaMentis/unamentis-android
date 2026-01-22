package com.unamentis.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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
    }
