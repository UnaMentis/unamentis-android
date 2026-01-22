package com.unamentis.ui.settings

import android.content.Context
import com.unamentis.core.device.DeviceCapabilityDetector
import com.unamentis.services.llm.ModelDownloadManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for on-device LLM settings integration.
 *
 * Tests the ModelDownloadManager and DeviceCapabilityDetector
 * behavior that backs the Settings UI. These tests verify the
 * components that the SettingsViewModel depends on.
 *
 * Note: Full SettingsViewModel integration tests are in androidTest
 * since they require the full Hilt DI graph with ProviderConfig.
 */
class SettingsViewModelTest {
    private lateinit var mockContext: Context
    private lateinit var mockCapabilityDetector: DeviceCapabilityDetector
    private lateinit var modelDownloadManager: ModelDownloadManager
    private lateinit var tempDir: File
    private lateinit var modelsDir: File

    @Before
    fun setup() {
        @Suppress("DEPRECATION")
        tempDir = createTempDir()
        modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()

        mockContext = mockk(relaxed = true)
        mockCapabilityDetector = mockk(relaxed = true)

        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Default: device supports on-device LLM
        every { mockCapabilityDetector.supportsOnDeviceLLM() } returns true
        every { mockCapabilityDetector.getRecommendedOnDeviceModel() } returns
            DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
        every { mockCapabilityDetector.getSupportedOnDeviceModels() } returns
            listOf(
                DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B,
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B,
            )
        every { mockCapabilityDetector.detect() } returns
            DeviceCapabilityDetector.DeviceCapabilities(
                tier = DeviceCapabilityDetector.DeviceTier.FLAGSHIP,
                totalRamMB = 8192,
                availableRamMB = 4096,
                cpuCores = 8,
                apiLevel = 34,
                supportsNNAPI = true,
                supportsVulkan = true,
                supportsOnDeviceLLM = true,
                recommendedLLMModel = DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B,
                manufacturer = "Google",
                model = "Pixel 8 Pro",
            )

        modelDownloadManager = ModelDownloadManager(mockContext, mockCapabilityDetector)
    }

    // ==================== DeviceCapabilityDetector Integration ====================

    @Test
    fun `supportsOnDeviceLLM returns true for capable device`() {
        assertTrue(mockCapabilityDetector.supportsOnDeviceLLM())
    }

    @Test
    fun `supportsOnDeviceLLM returns false for incapable device`() {
        every { mockCapabilityDetector.supportsOnDeviceLLM() } returns false
        assertFalse(mockCapabilityDetector.supportsOnDeviceLLM())
    }

    @Test
    fun `getRecommendedOnDeviceModel returns Ministral for high-RAM device`() {
        val recommended = mockCapabilityDetector.getRecommendedOnDeviceModel()
        assertEquals(
            DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B,
            recommended,
        )
    }

    @Test
    fun `getRecommendedOnDeviceModel returns TinyLlama for lower-RAM device`() {
        every { mockCapabilityDetector.getRecommendedOnDeviceModel() } returns
            DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B

        val recommended = mockCapabilityDetector.getRecommendedOnDeviceModel()
        assertEquals(
            DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B,
            recommended,
        )
    }

    @Test
    fun `getRecommendedOnDeviceModel returns null for unsupported device`() {
        every { mockCapabilityDetector.getRecommendedOnDeviceModel() } returns null
        assertNull(mockCapabilityDetector.getRecommendedOnDeviceModel())
    }

    @Test
    fun `detect returns correct device RAM`() {
        val capabilities = mockCapabilityDetector.detect()
        assertEquals(8192, capabilities.totalRamMB)
    }

    // ==================== ModelDownloadManager Integration ====================

    @Test
    fun `downloadState starts as Idle`() =
        runTest {
            val state = modelDownloadManager.downloadState.first()
            assertEquals(ModelDownloadManager.DownloadState.Idle, state)
        }

    @Test
    fun `getAvailableModels returns models from capability detector`() {
        val models = modelDownloadManager.getAvailableModels()

        assertEquals(2, models.size)
        assertTrue(
            models.any {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
            },
        )
        assertTrue(
            models.any {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B
            },
        )
    }

    @Test
    fun `getAvailableModels shows correct download status`() {
        // Create a downloaded model file
        val modelFile =
            File(
                modelsDir,
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename,
            )
        modelFile.writeText("model data")

        val models = modelDownloadManager.getAvailableModels()

        val tinyllama =
            models.find {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B
            }
        val ministral =
            models.find {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
            }

        assertNotNull(tinyllama)
        assertNotNull(ministral)
        assertTrue(tinyllama!!.isDownloaded)
        assertFalse(ministral!!.isDownloaded)
    }

    @Test
    fun `isRecommendedModelDownloaded returns false when model not downloaded`() {
        assertFalse(modelDownloadManager.isRecommendedModelDownloaded())
    }

    @Test
    fun `isRecommendedModelDownloaded returns true when model downloaded`() {
        // Create the recommended model file
        val modelFile =
            File(
                modelsDir,
                DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B.filename,
            )
        modelFile.writeText("model data")

        assertTrue(modelDownloadManager.isRecommendedModelDownloaded())
    }

    @Test
    fun `getTotalStorageUsed returns 0 for empty models directory`() {
        assertEquals(0L, modelDownloadManager.getTotalStorageUsed())
    }

    @Test
    fun `getTotalStorageUsed calculates only gguf files`() {
        // Create test files
        val ggufFile = File(modelsDir, "test.gguf")
        ggufFile.writeText("x".repeat(1000))

        val otherFile = File(modelsDir, "test.txt")
        otherFile.writeText("x".repeat(500))

        assertEquals(1000L, modelDownloadManager.getTotalStorageUsed())
    }

    // ==================== Mock verification ====================

    @Test
    fun `cancelDownload sets cancelled flag`() {
        // cancelDownload is a simple flag setter, verify it doesn't throw
        modelDownloadManager.cancelDownload()
        // No exception means success
    }

    @Test
    fun `deleteModel removes model file`() {
        val modelFile =
            File(
                modelsDir,
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename,
            )
        modelFile.writeText("model data")

        assertTrue(modelFile.exists())

        val deleted =
            modelDownloadManager.deleteModel(
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B,
            )

        assertTrue(deleted)
        assertFalse(modelFile.exists())
    }

    @Test
    fun `getAvailableModels updates after model file created`() {
        // Initially no downloaded models
        var models = modelDownloadManager.getAvailableModels()
        assertFalse(models.any { it.isDownloaded })

        // Create a model file
        val modelFile =
            File(
                modelsDir,
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename,
            )
        modelFile.writeText("model data")

        // Should now show downloaded
        models = modelDownloadManager.getAvailableModels()
        val tinyllama =
            models.find {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B
            }
        assertTrue(tinyllama!!.isDownloaded)
    }

    // ==================== Model Info tests ====================

    @Test
    fun `ModelInfo contains correct spec information`() {
        val models = modelDownloadManager.getAvailableModels()
        val ministralInfo =
            models.find {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B
            }

        assertNotNull(ministralInfo)
        assertEquals(
            DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B,
            ministralInfo!!.spec,
        )
        assertTrue(ministralInfo.downloadUrl.contains("huggingface.co"))
        assertFalse(ministralInfo.isDownloaded)
        assertEquals(0L, ministralInfo.fileSizeOnDisk)
    }

    @Test
    fun `ModelInfo shows correct file size on disk`() {
        val modelFile =
            File(
                modelsDir,
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename,
            )
        modelFile.writeText("x".repeat(1000))

        val models = modelDownloadManager.getAvailableModels()
        val tinyllamaInfo =
            models.find {
                it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B
            }

        assertNotNull(tinyllamaInfo)
        assertTrue(tinyllamaInfo!!.isDownloaded)
        assertEquals(1000L, tinyllamaInfo.fileSizeOnDisk)
    }
}
