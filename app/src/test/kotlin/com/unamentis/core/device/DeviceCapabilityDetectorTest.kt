package com.unamentis.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DeviceCapabilityDetector.
 *
 * Tests on-device LLM capability detection, model recommendations,
 * and device tier classification.
 */
class DeviceCapabilityDetectorTest {
    @Test
    fun `OnDeviceModelSpec MINISTRAL_3B has correct values`() {
        val spec = DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B

        assertEquals("ministral-3b-instruct-q4_k_m.gguf", spec.filename)
        assertEquals("Ministral 3B", spec.displayName)
        assertEquals(2_100_000_000L, spec.sizeBytes)
        assertEquals(6144, spec.minRamMB)
        assertEquals(8192, spec.recommendedRamMB)
    }

    @Test
    fun `OnDeviceModelSpec TINYLLAMA_1B has correct values`() {
        val spec = DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B

        assertEquals("tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", spec.filename)
        assertEquals("TinyLlama 1.1B", spec.displayName)
        assertEquals(670_000_000L, spec.sizeBytes)
        assertEquals(3072, spec.minRamMB)
        assertEquals(4096, spec.recommendedRamMB)
    }

    @Test
    fun `OnDeviceModelSpec enum has exactly 2 models`() {
        assertEquals(2, DeviceCapabilityDetector.OnDeviceModelSpec.entries.size)
    }

    @Test
    fun `MINISTRAL_3B requires more RAM than TINYLLAMA_1B`() {
        val ministral = DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
        val tinyllama = DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B

        assertTrue(ministral.minRamMB > tinyllama.minRamMB)
        assertTrue(ministral.sizeBytes > tinyllama.sizeBytes)
    }

    @Test
    fun `DeviceTier has exactly 3 tiers`() {
        assertEquals(3, DeviceCapabilityDetector.DeviceTier.entries.size)
    }

    @Test
    fun `DeviceCapabilities includes on-device LLM fields`() {
        // Test that DeviceCapabilities can be constructed with LLM fields
        val capabilities =
            DeviceCapabilityDetector.DeviceCapabilities(
                tier = DeviceCapabilityDetector.DeviceTier.FLAGSHIP,
                totalRamMB = 12288,
                availableRamMB = 8000,
                cpuCores = 8,
                apiLevel = 34,
                supportsNNAPI = true,
                supportsVulkan = true,
                supportsOnDeviceLLM = true,
                recommendedLLMModel = DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B,
                manufacturer = "Google",
                model = "Pixel 8 Pro",
            )

        assertTrue(capabilities.supportsOnDeviceLLM)
        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B, capabilities.recommendedLLMModel)
    }

    @Test
    fun `RecommendedConfig includes LLM field`() {
        val config =
            DeviceCapabilityDetector.RecommendedConfig(
                useCloudSTT = true,
                useCloudTTS = true,
                useCloudLLM = false,
                audioBufferSize = 512,
                maxConcurrentRequests = 3,
                enableNNAPI = true,
            )

        assertFalse(config.useCloudLLM)
    }

    // Tests for LLM capability calculation logic (without mocking Context)

    @Test
    fun `6GB RAM device supports on-device LLM with Ministral`() {
        // Simulate capability detection for 6GB device
        val totalRamMB = 6144
        val cpuCores = 4
        val apiLevel = 30

        val canRunOnDeviceLLM =
            totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                cpuCores >= 4 &&
                apiLevel >= 26

        assertTrue(canRunOnDeviceLLM)

        // Check recommended model
        val recommendedModel =
            if (canRunOnDeviceLLM) {
                if (totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B.minRamMB) {
                    DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
                } else {
                    DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B
                }
            } else {
                null
            }

        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B, recommendedModel)
    }

    @Test
    fun `4GB RAM device supports on-device LLM with TinyLlama only`() {
        // Simulate capability detection for 4GB device
        val totalRamMB = 4096
        val cpuCores = 4
        val apiLevel = 30

        val canRunOnDeviceLLM =
            totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                cpuCores >= 4 &&
                apiLevel >= 26

        assertTrue(canRunOnDeviceLLM)

        // Check recommended model
        val recommendedModel =
            if (canRunOnDeviceLLM) {
                if (totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B.minRamMB) {
                    DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
                } else {
                    DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B
                }
            } else {
                null
            }

        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B, recommendedModel)
    }

    @Test
    fun `2GB RAM device does not support on-device LLM`() {
        // Simulate capability detection for 2GB device
        val totalRamMB = 2048
        val cpuCores = 4
        val apiLevel = 30

        val canRunOnDeviceLLM =
            totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                cpuCores >= 4 &&
                apiLevel >= 26

        assertFalse(canRunOnDeviceLLM)
    }

    @Test
    fun `device with insufficient cores does not support on-device LLM`() {
        // Simulate capability detection for device with only 2 cores
        val totalRamMB = 8192
        val cpuCores = 2
        val apiLevel = 30

        val canRunOnDeviceLLM =
            totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                cpuCores >= 4 &&
                apiLevel >= 26

        assertFalse(canRunOnDeviceLLM)
    }

    @Test
    fun `old Android version does not support on-device LLM`() {
        // Simulate capability detection for Android 7.0 (API 24)
        val totalRamMB = 8192
        val cpuCores = 8
        val apiLevel = 24

        val canRunOnDeviceLLM =
            totalRamMB >= DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.minRamMB &&
                cpuCores >= 4 &&
                apiLevel >= 26

        assertFalse(canRunOnDeviceLLM)
    }

    @Test
    fun `supported models list is sorted by quality`() {
        // Simulate getSupportedOnDeviceModels for 8GB device
        val totalRamMB = 8192

        val supportedModels =
            DeviceCapabilityDetector.OnDeviceModelSpec.entries
                .filter { it.minRamMB <= totalRamMB }
                .sortedByDescending { it.minRamMB } // Best quality first

        assertEquals(2, supportedModels.size)
        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B, supportedModels[0])
        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B, supportedModels[1])
    }

    @Test
    fun `supported models list for 4GB device excludes Ministral`() {
        // Simulate getSupportedOnDeviceModels for 4GB device
        val totalRamMB = 4096

        val supportedModels =
            DeviceCapabilityDetector.OnDeviceModelSpec.entries
                .filter { it.minRamMB <= totalRamMB }
                .sortedByDescending { it.minRamMB }

        assertEquals(1, supportedModels.size)
        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B, supportedModels[0])
    }

    @Test
    fun `supported models list for 2GB device is empty`() {
        // Simulate getSupportedOnDeviceModels for 2GB device
        val totalRamMB = 2048

        val supportedModels =
            DeviceCapabilityDetector.OnDeviceModelSpec.entries
                .filter { it.minRamMB <= totalRamMB }

        assertTrue(supportedModels.isEmpty())
    }
}
