package com.unamentis.services.llm

import android.content.Context
import com.unamentis.core.device.DeviceCapabilityDetector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for ModelDownloadManager.
 *
 * Tests model detection, storage calculations, and download state management.
 * Actual network downloads are tested in instrumented tests.
 */
class ModelDownloadManagerTest {
    private lateinit var mockContext: Context
    private lateinit var mockCapabilityDetector: DeviceCapabilityDetector
    private lateinit var tempDir: File
    private lateinit var modelsDir: File

    @Before
    fun setup() {
        tempDir = createTempDir()
        modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()

        mockContext = mockk(relaxed = true)
        mockCapabilityDetector = mockk(relaxed = true)

        every { mockContext.getExternalFilesDir(null) } returns tempDir
    }

    @Test
    fun `download state starts as Idle`() =
        runTest {
            val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
            val state = manager.downloadState.first()
            assertEquals(ModelDownloadManager.DownloadState.Idle, state)
        }

    @Test
    fun `getModelsDirectory creates directory if not exists`() {
        val newTempDir = createTempDir()
        val newModelsDir = File(newTempDir, "models")

        val context = mockk<Context>(relaxed = true)
        every { context.getExternalFilesDir(null) } returns newTempDir

        assertFalse(newModelsDir.exists())

        val manager = ModelDownloadManager(context, mockCapabilityDetector)
        val result = manager.getModelsDirectory()

        assertTrue(result.exists())
        assertEquals(File(newTempDir, "models").absolutePath, result.absolutePath)
    }

    @Test
    fun `getTotalStorageUsed returns 0 for empty directory`() {
        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        assertEquals(0L, manager.getTotalStorageUsed())
    }

    @Test
    fun `getTotalStorageUsed calculates gguf files only`() {
        // Create some test files
        val ggufFile = File(modelsDir, "test.gguf")
        ggufFile.writeText("x".repeat(1000))

        val otherFile = File(modelsDir, "test.txt")
        otherFile.writeText("x".repeat(500))

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        val usedStorage = manager.getTotalStorageUsed()

        assertEquals(1000L, usedStorage)
    }

    @Test
    fun `isRecommendedModelDownloaded returns false when no model exists`() {
        every { mockCapabilityDetector.getRecommendedOnDeviceModel() } returns
            DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        assertFalse(manager.isRecommendedModelDownloaded())
    }

    @Test
    fun `isRecommendedModelDownloaded returns true when model exists`() {
        every { mockCapabilityDetector.getRecommendedOnDeviceModel() } returns
            DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B

        // Create the model file
        val modelFile = File(modelsDir, DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B.filename)
        modelFile.writeText("model data")

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        assertTrue(manager.isRecommendedModelDownloaded())
    }

    @Test
    fun `isRecommendedModelDownloaded returns false when device does not support LLM`() {
        every { mockCapabilityDetector.getRecommendedOnDeviceModel() } returns null

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        assertFalse(manager.isRecommendedModelDownloaded())
    }

    @Test
    fun `getAvailableModels returns empty list when device does not support LLM`() {
        every { mockCapabilityDetector.getSupportedOnDeviceModels() } returns emptyList()

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        assertTrue(manager.getAvailableModels().isEmpty())
    }

    @Test
    fun `getAvailableModels returns models with correct download status`() {
        every { mockCapabilityDetector.getSupportedOnDeviceModels() } returns
            listOf(
                DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B,
                DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B,
            )

        // Create only TinyLlama file
        val tinyllamaFile = File(modelsDir, DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename)
        tinyllamaFile.writeText("model data")

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        val models = manager.getAvailableModels()

        assertEquals(2, models.size)

        val ministralModel = models.find { it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B }
        val tinyllamaModel = models.find { it.spec == DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B }

        assertFalse(ministralModel!!.isDownloaded)
        assertTrue(tinyllamaModel!!.isDownloaded)
    }

    @Test
    fun `deleteModel removes model file`() {
        val modelFile = File(modelsDir, DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename)
        modelFile.writeText("model data")

        assertTrue(modelFile.exists())

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        val deleted = manager.deleteModel(DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B)

        assertTrue(deleted)
        assertFalse(modelFile.exists())
    }

    @Test
    fun `deleteModel also removes partial download file`() {
        val tinyllamaFilename = DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B.filename
        val modelFile = File(modelsDir, tinyllamaFilename)
        val partialFile = File(modelsDir, "$tinyllamaFilename.download")

        modelFile.writeText("model data")
        partialFile.writeText("partial data")

        assertTrue(modelFile.exists())
        assertTrue(partialFile.exists())

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        manager.deleteModel(DeviceCapabilityDetector.OnDeviceModelSpec.TINYLLAMA_1B)

        assertFalse(modelFile.exists())
        assertFalse(partialFile.exists())
    }

    @Test
    fun `ModelInfo contains correct spec information`() {
        every { mockCapabilityDetector.getSupportedOnDeviceModels() } returns
            listOf(DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B)

        val manager = ModelDownloadManager(mockContext, mockCapabilityDetector)
        val models = manager.getAvailableModels()

        val ministralInfo = models.first()

        assertEquals(DeviceCapabilityDetector.OnDeviceModelSpec.MINISTRAL_3B, ministralInfo.spec)
        assertTrue(ministralInfo.downloadUrl.contains("huggingface.co"))
        assertFalse(ministralInfo.isDownloaded)
        assertEquals(0L, ministralInfo.fileSizeOnDisk)
    }

    @Test
    fun `DownloadState Downloading tracks progress correctly`() {
        val state =
            ModelDownloadManager.DownloadState.Downloading(
                progress = 0.5f,
                downloadedBytes = 500_000_000L,
                totalBytes = 1_000_000_000L,
            )

        assertEquals(0.5f, state.progress)
        assertEquals(500_000_000L, state.downloadedBytes)
        assertEquals(1_000_000_000L, state.totalBytes)
    }

    @Test
    fun `DownloadState Complete contains model path`() {
        val state = ModelDownloadManager.DownloadState.Complete("/path/to/model.gguf")
        assertEquals("/path/to/model.gguf", state.modelPath)
    }

    @Test
    fun `DownloadState Error contains error message`() {
        val state = ModelDownloadManager.DownloadState.Error("Network error")
        assertEquals("Network error", state.message)
    }

    @Test
    fun `DownloadState Verifying contains filename`() {
        val state = ModelDownloadManager.DownloadState.Verifying("model.gguf")
        assertEquals("model.gguf", state.filename)
    }
}
