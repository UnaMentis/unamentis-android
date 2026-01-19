package com.unamentis.services.llm

import com.unamentis.data.model.LLMMessage
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.LLMToken
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PatchPanelService.
 *
 * Tests routing logic, provider selection, and fallback behavior.
 */
class PatchPanelServiceTest {
    private lateinit var mockOpenAI: LLMService
    private lateinit var mockAnthropic: LLMService
    private lateinit var mockOllama: LLMService
    private lateinit var providers: Map<String, LLMService>
    private lateinit var patchPanel: PatchPanelService

    @Before
    fun setup() {
        mockOpenAI = mockk(relaxed = true)
        mockAnthropic = mockk(relaxed = true)
        mockOllama = mockk(relaxed = true)

        every { mockOpenAI.providerName } returns "OpenAI"
        every { mockAnthropic.providerName } returns "Anthropic"
        every { mockOllama.providerName } returns "Ollama"

        providers =
            mapOf(
                "OpenAI" to mockOpenAI,
                "Anthropic" to mockAnthropic,
                "Ollama" to mockOllama,
            )

        patchPanel = PatchPanelService(providers)
    }

    @Test
    fun `routes tutoring explanation task to Anthropic with BALANCED preference`() =
        runTest {
            // With BALANCED preference, TUTORING_EXPLANATION routes to Anthropic first
            // because "explain" keyword triggers TUTORING_EXPLANATION task type
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "You are a tutor for mathematics."),
                    LLMMessage(role = "user", content = "Explain quadratic equations."),
                )

            val expectedTokens =
                listOf(
                    LLMToken(content = "A quadratic", isDone = false),
                    LLMToken(content = " equation", isDone = false),
                    LLMToken(content = "", isDone = true),
                )

            every {
                mockAnthropic.streamCompletion(any(), any(), any())
            } returns flowOf(*expectedTokens.toTypedArray())

            val tokens = patchPanel.streamCompletion(messages).toList()

            assertEquals(expectedTokens, tokens)
            verify { mockAnthropic.streamCompletion(messages, 0.7f, 500) }
        }

    @Test
    fun `routes summarization task to OpenAI with BALANCED preference`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "Summarize the following text."),
                    LLMMessage(role = "user", content = "Long text here..."),
                )

            val expectedTokens =
                listOf(
                    LLMToken(content = "Summary:", isDone = false),
                    LLMToken(content = "", isDone = true),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(*expectedTokens.toTypedArray())

            val tokens = patchPanel.streamCompletion(messages).toList()

            assertEquals(expectedTokens, tokens)
            verify { mockOpenAI.streamCompletion(messages, 0.7f, 500) }
        }

    @Test
    fun `falls back to OpenAI if Anthropic unavailable`() =
        runTest {
            val providersWithoutAnthropic =
                mapOf(
                    "OpenAI" to mockOpenAI,
                    "Ollama" to mockOllama,
                )
            val patchPanelLimited = PatchPanelService(providersWithoutAnthropic)

            val messages =
                listOf(
                    LLMMessage(role = "system", content = "You are a tutor."),
                    LLMMessage(role = "user", content = "Help me learn."),
                )

            val expectedTokens =
                listOf(
                    LLMToken(content = "Let's start", isDone = false),
                    LLMToken(content = "", isDone = true),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(*expectedTokens.toTypedArray())

            val tokens = patchPanelLimited.streamCompletion(messages).toList()

            assertEquals(expectedTokens, tokens)
            verify { mockOpenAI.streamCompletion(messages, 0.7f, 500) }
        }

    @Test
    fun `stop delegates to current provider`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "user", content = "Test"),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Test", isDone = false))

            patchPanel.streamCompletion(messages).toList()
            patchPanel.stop()

            coVerify { mockOpenAI.stop() }
        }

    @Test
    fun `default routing table has correct provider priorities`() {
        val routingTable = RoutingTable.default()

        // TUTORING with QUALITY should prioritize Anthropic
        val tutoringQuality =
            routingTable.getProviderCandidates(
                RoutingContext(
                    taskType = TaskType.TUTORING,
                    deviceTier = DeviceTier.FLAGSHIP,
                    networkQuality = NetworkQuality.EXCELLENT,
                    costPreference = CostPreference.QUALITY,
                ),
            )
        assertEquals("Anthropic", tutoringQuality.first())

        // SIMPLE_RESPONSE with COST should prioritize Ollama
        val simpleCost =
            routingTable.getProviderCandidates(
                RoutingContext(
                    taskType = TaskType.SIMPLE_RESPONSE,
                    deviceTier = DeviceTier.MINIMUM,
                    networkQuality = NetworkQuality.GOOD,
                    costPreference = CostPreference.COST,
                ),
            )
        assertEquals("Ollama", simpleCost.first())
    }

    @Test
    fun `extracts TUTORING task type from system message without explain keyword`() =
        runTest {
            // Use "teach" keyword instead of "explain" to trigger base TUTORING task
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "You are a tutor for physics."),
                    LLMMessage(role = "user", content = "Teach me about gravity."),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Gravity", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // "teach" triggers TUTORING which with BALANCED defaults to OpenAI first
            verify { mockOpenAI.streamCompletion(any(), any(), any()) }
        }

    @Test
    fun `extracts PLANNING task type from system message`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "Create a study plan for the user."),
                    LLMMessage(role = "user", content = "I want to learn calculus."),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Study plan:", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // Should route to OpenAI (PLANNING + BALANCED defaults to OpenAI first)
            verify { mockOpenAI.streamCompletion(any(), any(), any()) }
        }

    @Test(expected = IllegalStateException::class)
    fun `throws exception if no providers available`() =
        runTest {
            val emptyPatchPanel = PatchPanelService(emptyMap())

            val messages =
                listOf(
                    LLMMessage(role = "user", content = "Test"),
                )

            emptyPatchPanel.streamCompletion(messages).toList()
        }

    // Tests for new task types

    @Test
    fun `extracts TUTORING_EXPLANATION from explain keyword`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "You are an AI tutor."),
                    LLMMessage(role = "user", content = "Explain how photosynthesis works."),
                )

            every {
                mockAnthropic.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Photosynthesis is", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // TUTORING_EXPLANATION with BALANCED defaults to Anthropic first
            verify { mockAnthropic.streamCompletion(any(), any(), any()) }
        }

    @Test
    fun `extracts ASSESSMENT_QUIZ from quiz keyword`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "Generate a quiz."),
                    LLMMessage(role = "user", content = "Quiz me on biology."),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Question 1:", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // ASSESSMENT_QUIZ with BALANCED defaults to OpenAI first
            verify { mockOpenAI.streamCompletion(any(), any(), any()) }
        }

    @Test
    fun `extracts SIMPLE_GREETING from hello keyword`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "user", content = "Hello there!"),
                )

            every {
                mockOllama.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Hi!", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // SIMPLE_GREETING with BALANCED defaults to Ollama first (ultraFast)
            verify { mockOllama.streamCompletion(any(), any(), any()) }
        }

    @Test
    fun `extracts META_REFLECTION from progress review phrase`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "user", content = "How am I doing in my studies?"),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "You've made great progress", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // META_REFLECTION with BALANCED defaults to OpenAI (balanced list)
            verify { mockOpenAI.streamCompletion(any(), any(), any()) }
        }

    // Tests for condition-based routing

    @Test
    fun `offline network filters to only offline-capable providers`() {
        val routingTable = RoutingTable.default()

        val offlineContext =
            RoutingContext(
                taskType = TaskType.TUTORING,
                deviceTier = DeviceTier.STANDARD,
                networkQuality = NetworkQuality.OFFLINE,
                costPreference = CostPreference.QUALITY,
            )

        val candidates = routingTable.getProviderCandidates(offlineContext)

        // Should only contain OnDevice and Ollama (local providers)
        assertTrue(candidates.all { it in listOf("OnDevice", "Ollama") })
        assertTrue(candidates.isNotEmpty())
    }

    @Test
    fun `poor network deprioritizes cloud providers`() {
        val routingTable = RoutingTable.default()

        // Cost preference includes local providers
        val poorNetworkContext =
            RoutingContext(
                taskType = TaskType.TUTORING,
                deviceTier = DeviceTier.STANDARD,
                networkQuality = NetworkQuality.POOR,
                costPreference = CostPreference.COST,
            )

        val candidates = routingTable.getProviderCandidates(poorNetworkContext)

        // Local providers should come before cloud providers
        val ollamaIndex = candidates.indexOf("Ollama")
        val onDeviceIndex = candidates.indexOf("OnDevice")
        val openaiIndex = candidates.indexOf("OpenAI")

        if (ollamaIndex >= 0 && openaiIndex >= 0) {
            assertTrue("Ollama should come before OpenAI in poor network", ollamaIndex < openaiIndex)
        }
        if (onDeviceIndex >= 0 && openaiIndex >= 0) {
            assertTrue("OnDevice should come before OpenAI in poor network", onDeviceIndex < openaiIndex)
        }
    }

    @Test
    fun `routing table covers all 28 task types`() {
        val routingTable = RoutingTable.default()

        // Verify all task types have routing rules
        for (taskType in TaskType.entries) {
            val context =
                RoutingContext(
                    taskType = taskType,
                    deviceTier = DeviceTier.STANDARD,
                    networkQuality = NetworkQuality.GOOD,
                    costPreference = CostPreference.BALANCED,
                )
            val candidates = routingTable.getProviderCandidates(context)
            assertTrue("Task type $taskType should have routing candidates", candidates.isNotEmpty())
        }
    }

    @Test
    fun `all cost preferences are handled for each task type`() {
        val routingTable = RoutingTable.default()

        for (taskType in TaskType.entries) {
            for (costPref in CostPreference.entries) {
                val context =
                    RoutingContext(
                        taskType = taskType,
                        deviceTier = DeviceTier.STANDARD,
                        networkQuality = NetworkQuality.GOOD,
                        costPreference = costPref,
                    )
                val candidates = routingTable.getProviderCandidates(context)
                assertTrue(
                    "Task type $taskType with $costPref should have candidates",
                    candidates.isNotEmpty(),
                )
            }
        }
    }
}
