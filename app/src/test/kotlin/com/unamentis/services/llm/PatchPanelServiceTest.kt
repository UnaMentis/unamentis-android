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
    fun `routes tutoring task to OpenAI with BALANCED preference`() =
        runTest {
            // With BALANCED preference, TUTORING routes to OpenAI first
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
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(*expectedTokens.toTypedArray())

            val tokens = patchPanel.streamCompletion(messages).toList()

            assertEquals(expectedTokens, tokens)
            verify { mockOpenAI.streamCompletion(messages, 0.7f, 500) }
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
    fun `extracts TUTORING task type from system message`() =
        runTest {
            val messages =
                listOf(
                    LLMMessage(role = "system", content = "You are a tutor for physics."),
                    LLMMessage(role = "user", content = "Explain gravity."),
                )

            every {
                mockOpenAI.streamCompletion(any(), any(), any())
            } returns flowOf(LLMToken(content = "Gravity", isDone = false))

            patchPanel.streamCompletion(messages).toList()

            // Should route to OpenAI (TUTORING + BALANCED defaults to OpenAI first)
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
}
