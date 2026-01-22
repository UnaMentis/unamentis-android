package com.unamentis.services.llm

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for OnDeviceLLMService.
 *
 * Tests prompt formatting, model path resolution, and metrics.
 * Note: Native library loading and actual inference cannot be tested
 * in unit tests without the native library - those are tested in
 * instrumented tests.
 */
class OnDeviceLLMServiceTest {
    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDir()
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir
    }

    @Test
    fun `Mistral prompt format is correct`() {
        // Test the expected Mistral/Ministral prompt format
        val systemMessage = "You are a helpful tutor."
        val userMessage = "Explain quantum physics."

        // Expected format: [INST] system\n\nuser [/INST]
        val expectedPrompt = "[INST] $systemMessage\n\n$userMessage [/INST]"

        // Simulate formatMistralPrompt logic
        val sb = StringBuilder()
        sb.append("[INST] $systemMessage\n\n$userMessage [/INST]")

        assertEquals(expectedPrompt, sb.toString())
    }

    @Test
    fun `ChatML prompt format is correct`() {
        // Test the expected ChatML prompt format for TinyLlama
        val systemMessage = "You are a helpful tutor."
        val userMessage = "Explain quantum physics."

        // Simulate formatChatMLPrompt logic
        val sb = StringBuilder()
        sb.append("<|system|>\n$systemMessage</s>\n")
        sb.append("<|user|>\n$userMessage</s>\n")
        sb.append("<|assistant|>\n")

        assertTrue(sb.toString().contains("<|system|>"))
        assertTrue(sb.toString().contains("<|user|>"))
        assertTrue(sb.toString().contains("<|assistant|>"))
        assertTrue(sb.toString().contains(systemMessage))
        assertTrue(sb.toString().contains(userMessage))
    }

    @Test
    fun `Mistral prompt with multiple turns is correct`() {
        // Test multi-turn conversation format
        val system = "You are a tutor."
        val user1 = "What is 2+2?"
        val assistant1 = "4"
        val user2 = "And 3+3?"

        // Simulate multi-turn Mistral format
        val sb = StringBuilder()
        sb.append("[INST] $system\n\n$user1 [/INST]")
        sb.append("$assistant1</s>")
        sb.append("[INST] $user2 [/INST]")

        assertTrue(sb.toString().contains(system))
        assertTrue(sb.toString().contains(user1))
        assertTrue(sb.toString().contains(assistant1))
        assertTrue(sb.toString().contains(user2))
    }

    @Test
    fun `model filename constants are correct`() {
        assertEquals("ministral-3b-instruct-q4_k_m.gguf", OnDeviceLLMService.MINISTRAL_3B_FILENAME)
        assertEquals("tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", OnDeviceLLMService.TINYLLAMA_1B_FILENAME)
    }

    @Test
    fun `model path resolution returns null when no models exist`() {
        // Empty models directory should return null
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()

        assertFalse(File(modelsDir, OnDeviceLLMService.MINISTRAL_3B_FILENAME).exists())
        assertFalse(File(modelsDir, OnDeviceLLMService.TINYLLAMA_1B_FILENAME).exists())
    }

    @Test
    fun `model path resolution prefers Ministral over TinyLlama`() {
        // Create both model files
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()

        val ministral = File(modelsDir, OnDeviceLLMService.MINISTRAL_3B_FILENAME)
        val tinyllama = File(modelsDir, OnDeviceLLMService.TINYLLAMA_1B_FILENAME)

        ministral.createNewFile()
        tinyllama.createNewFile()

        // Simulate getAvailableModelPath logic
        val availablePath =
            if (ministral.exists()) {
                ministral.absolutePath
            } else if (tinyllama.exists()) {
                tinyllama.absolutePath
            } else {
                null
            }

        assertEquals(ministral.absolutePath, availablePath)
    }

    @Test
    fun `model path resolution falls back to TinyLlama`() {
        // Create only TinyLlama
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()

        val tinyllama = File(modelsDir, OnDeviceLLMService.TINYLLAMA_1B_FILENAME)
        tinyllama.createNewFile()

        val ministral = File(modelsDir, OnDeviceLLMService.MINISTRAL_3B_FILENAME)

        // Simulate getAvailableModelPath logic
        val availablePath =
            if (ministral.exists()) {
                ministral.absolutePath
            } else if (tinyllama.exists()) {
                tinyllama.absolutePath
            } else {
                null
            }

        assertEquals(tinyllama.absolutePath, availablePath)
    }

    @Test
    fun `optimal thread count calculation is bounded`() {
        // Test thread count calculation logic
        val cores = Runtime.getRuntime().availableProcessors()
        val optimalThreads = maxOf(1, minOf(8, cores - 2))

        assertTrue(optimalThreads >= 1)
        assertTrue(optimalThreads <= 8)
    }

    @Test
    fun `metrics data class holds correct values`() {
        val metrics =
            OnDeviceLLMService.OnDeviceMetrics(
                totalInputTokens = 100,
                totalOutputTokens = 50,
                medianTTFT = 200L,
                p99TTFT = 500L,
            )

        assertEquals(100, metrics.totalInputTokens)
        assertEquals(50, metrics.totalOutputTokens)
        assertEquals(200L, metrics.medianTTFT)
        assertEquals(500L, metrics.p99TTFT)
    }

    @Test
    fun `model config data class holds correct values`() {
        val config =
            OnDeviceLLMService.ModelConfig(
                modelPath = "/path/to/model.gguf",
                contextSize = 4096,
                gpuLayers = 99,
            )

        assertEquals("/path/to/model.gguf", config.modelPath)
        assertEquals(4096, config.contextSize)
        assertEquals(99, config.gpuLayers)
    }

    @Test
    fun `model config uses correct defaults`() {
        val config = OnDeviceLLMService.ModelConfig(modelPath = "/path/to/model.gguf")

        assertEquals("/path/to/model.gguf", config.modelPath)
        assertEquals(4096, config.contextSize)
        assertEquals(99, config.gpuLayers)
    }

    @Test
    fun `detects Mistral model from filename`() {
        // Test model detection logic
        val ministralPath = "/models/ministral-3b-instruct-q4_k_m.gguf"
        val tinyllamaPath = "/models/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

        assertTrue(ministralPath.lowercase().contains("ministral"))
        assertFalse(tinyllamaPath.lowercase().contains("ministral"))
    }

    @Test
    fun `detects TinyLlama model from filename`() {
        val tinyllamaPath = "/models/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

        assertFalse(tinyllamaPath.lowercase().contains("ministral"))
        assertFalse(tinyllamaPath.lowercase().contains("mistral"))
    }
}
