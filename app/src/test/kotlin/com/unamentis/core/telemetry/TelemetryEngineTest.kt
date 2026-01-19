package com.unamentis.core.telemetry

import com.unamentis.data.model.LatencyType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TelemetryEngine.
 *
 * Tests cover:
 * - Session lifecycle
 * - Latency recording and statistics
 * - Cost tracking and aggregation
 * - Provider categorization
 */
class TelemetryEngineTest {
    private lateinit var telemetryEngine: TelemetryEngine

    @Before
    fun setup() {
        telemetryEngine = TelemetryEngine()
    }

    // MARK: - Session Lifecycle Tests

    @Test
    fun `startSession sets current session ID`() =
        runTest {
            telemetryEngine.startSession("test-session-1")

            assertEquals("test-session-1", telemetryEngine.currentSessionId.value)
        }

    @Test
    fun `endSession clears current session ID`() =
        runTest {
            telemetryEngine.startSession("test-session-1")
            telemetryEngine.endSession()

            assertNull(telemetryEngine.currentSessionId.value)
        }

    @Test
    fun `multiple sessions can be tracked`() =
        runTest {
            telemetryEngine.startSession("session-1")
            telemetryEngine.recordLatency(LatencyType.STT, 100)
            telemetryEngine.endSession()

            telemetryEngine.startSession("session-2")
            telemetryEngine.recordLatency(LatencyType.STT, 200)

            // Verify both sessions have data
            val stats1 = telemetryEngine.getLatencyStats("session-1", LatencyType.STT)
            val stats2 = telemetryEngine.getLatencyStats("session-2", LatencyType.STT)

            assertEquals(100L, stats1.average)
            assertEquals(200L, stats2.average)
        }

    // MARK: - Latency Recording Tests

    @Test
    fun `recordLatency stores measurement for current session`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordLatency(LatencyType.STT, 150)

            val stats = telemetryEngine.getLatencyStats("test-session", LatencyType.STT)

            assertEquals(150L, stats.average)
        }

    @Test
    fun `recordLatency ignores measurements when no session active`() =
        runTest {
            // No session started
            telemetryEngine.recordLatency(LatencyType.STT, 150)

            // No session ID, so getLatencyStats returns empty
            val stats = telemetryEngine.getLatencyStats("any-session", LatencyType.STT)
            assertEquals(0L, stats.average)
        }

    @Test
    fun `recordLatency maintains separate categories`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordLatency(LatencyType.STT, 100)
            telemetryEngine.recordLatency(LatencyType.LLM_TTFT, 200)
            telemetryEngine.recordLatency(LatencyType.TTS_TTFB, 300)
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 400)

            val sttStats = telemetryEngine.getLatencyStats("test-session", LatencyType.STT)
            val llmStats = telemetryEngine.getLatencyStats("test-session", LatencyType.LLM_TTFT)
            val ttsStats = telemetryEngine.getLatencyStats("test-session", LatencyType.TTS_TTFB)
            val e2eStats = telemetryEngine.getLatencyStats("test-session", LatencyType.E2E_TURN)

            assertEquals(100L, sttStats.average)
            assertEquals(200L, llmStats.average)
            assertEquals(300L, ttsStats.average)
            assertEquals(400L, e2eStats.average)
        }

    // MARK: - Latency Statistics Tests

    @Test
    fun `getLatencyStats calculates correct min max`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 100)
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 200)
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 300)

            val stats = telemetryEngine.getLatencyStats("test-session", LatencyType.E2E_TURN)

            assertEquals(100L, stats.min)
            assertEquals(300L, stats.max)
        }

    @Test
    fun `getLatencyStats calculates correct median`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 100)
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 200)
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 300)

            val stats = telemetryEngine.getLatencyStats("test-session", LatencyType.E2E_TURN)

            assertEquals(200L, stats.median)
        }

    @Test
    fun `getLatencyStats calculates P99 correctly`() =
        runTest {
            telemetryEngine.startSession("test-session")

            // Add 100 latency values (1-100 ms)
            for (i in 1..100) {
                telemetryEngine.recordLatency(LatencyType.E2E_TURN, i.toLong())
            }

            val stats = telemetryEngine.getLatencyStats("test-session", LatencyType.E2E_TURN)

            // P99 should be close to 99
            assertTrue("P99 should be >= 95", stats.p99 >= 95)
        }

    @Test
    fun `getLatencyStats returns empty stats for unknown session`() =
        runTest {
            val stats = telemetryEngine.getLatencyStats("unknown-session", LatencyType.STT)

            assertEquals(0L, stats.min)
            assertEquals(0L, stats.max)
            assertEquals(0L, stats.average)
        }

    // MARK: - Cost Recording Tests

    @Test
    fun `recordCost accumulates cost for session`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
            telemetryEngine.recordCost("deepgram", 0.02, mapOf("type" to "STT"))

            val totalCost = telemetryEngine.getTotalCost("test-session")

            assertEquals(0.03, totalCost, 0.001)
        }

    @Test
    fun `recordCost ignores when no session active`() =
        runTest {
            // No session started
            telemetryEngine.recordCost("deepgram", 0.01)

            val totalCost = telemetryEngine.getTotalCost("any-session")
            assertEquals(0.0, totalCost, 0.001)
        }

    @Test
    fun `getTotalCost sums all provider costs`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
            telemetryEngine.recordCost("elevenlabs", 0.02, mapOf("type" to "TTS"))
            telemetryEngine.recordCost("openai", 0.03, mapOf("type" to "LLM"))

            val totalCost = telemetryEngine.getTotalCost("test-session")

            assertEquals(0.06, totalCost, 0.001)
        }

    // MARK: - Cost Breakdown Tests

    @Test
    fun `getCostBreakdownByType categorizes costs correctly`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
            telemetryEngine.recordCost("elevenlabs", 0.02, mapOf("type" to "TTS"))
            telemetryEngine.recordCost("anthropic", 0.03, mapOf("type" to "LLM"))

            val breakdown = telemetryEngine.getCostBreakdownByType("test-session")

            assertEquals(0.01, breakdown.sttCost, 0.001)
            assertEquals(0.02, breakdown.ttsCost, 0.001)
            assertEquals(0.03, breakdown.llmCost, 0.001)
            assertEquals(0.06, breakdown.totalCost, 0.001)
        }

    @Test
    fun `getCostBreakdownByProvider groups by provider`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
            telemetryEngine.recordCost("deepgram", 0.02, mapOf("type" to "STT"))
            telemetryEngine.recordCost("openai", 0.05, mapOf("type" to "LLM"))

            val breakdown = telemetryEngine.getCostBreakdownByProvider("test-session")

            assertEquals(2, breakdown.size)

            val openaiCost = breakdown.find { it.providerName == "openai" }
            assertNotNull(openaiCost)
            assertEquals(0.05, openaiCost!!.totalCost, 0.001)
            assertEquals(1, openaiCost.requestCount)

            val deepgramCost = breakdown.find { it.providerName == "deepgram" }
            assertNotNull(deepgramCost)
            assertEquals(0.03, deepgramCost!!.totalCost, 0.001)
            assertEquals(2, deepgramCost.requestCount)
        }

    // MARK: - Provider Categorization Tests

    @Test
    fun `provider categorization recognizes STT providers`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("Deepgram", 0.01)
            telemetryEngine.recordCost("Whisper", 0.01)

            val breakdown = telemetryEngine.getCostBreakdownByType("test-session")

            assertEquals(0.02, breakdown.sttCost, 0.001)
        }

    @Test
    fun `provider categorization recognizes TTS providers`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("ElevenLabs", 0.01)
            telemetryEngine.recordCost("eleven_labs_turbo", 0.01)

            val breakdown = telemetryEngine.getCostBreakdownByType("test-session")

            assertEquals(0.02, breakdown.ttsCost, 0.001)
        }

    @Test
    fun `provider categorization recognizes LLM providers`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordCost("OpenAI", 0.01)
            telemetryEngine.recordCost("Anthropic", 0.01)
            telemetryEngine.recordCost("Claude-3.5-Sonnet", 0.01)
            telemetryEngine.recordCost("GPT-4o", 0.01)

            val breakdown = telemetryEngine.getCostBreakdownByType("test-session")

            assertEquals(0.04, breakdown.llmCost, 0.001)
        }

    @Test
    fun `explicit type metadata overrides automatic categorization`() =
        runTest {
            telemetryEngine.startSession("test-session")
            // Provider name suggests LLM but metadata says STT
            telemetryEngine.recordCost("openai-whisper", 0.01, mapOf("type" to "STT"))

            val breakdown = telemetryEngine.getCostBreakdownByType("test-session")

            assertEquals(0.01, breakdown.sttCost, 0.001)
            assertEquals(0.0, breakdown.llmCost, 0.001)
        }

    // MARK: - Aggregated Metrics Tests

    @Test
    fun `getAggregatedCostBreakdown sums across sessions`() =
        runTest {
            telemetryEngine.startSession("session-1")
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
            telemetryEngine.recordCost("openai", 0.02, mapOf("type" to "LLM"))
            telemetryEngine.endSession()

            telemetryEngine.startSession("session-2")
            telemetryEngine.recordCost("deepgram", 0.03, mapOf("type" to "STT"))
            telemetryEngine.recordCost("openai", 0.04, mapOf("type" to "LLM"))

            val aggregated =
                telemetryEngine.getAggregatedCostBreakdown(
                    listOf("session-1", "session-2"),
                )

            assertEquals(0.04, aggregated.sttCost, 0.001)
            assertEquals(0.06, aggregated.llmCost, 0.001)
            assertEquals(0.10, aggregated.totalCost, 0.001)
        }

    @Test
    fun `getAggregatedProviderBreakdown combines provider costs`() =
        runTest {
            telemetryEngine.startSession("session-1")
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
            telemetryEngine.endSession()

            telemetryEngine.startSession("session-2")
            telemetryEngine.recordCost("deepgram", 0.02, mapOf("type" to "STT"))

            val aggregated =
                telemetryEngine.getAggregatedProviderBreakdown(
                    listOf("session-1", "session-2"),
                )

            assertEquals(1, aggregated.size)
            assertEquals("deepgram", aggregated[0].providerName)
            assertEquals(0.03, aggregated[0].totalCost, 0.001)
            assertEquals(2, aggregated[0].requestCount)
        }

    // MARK: - Session Metrics Tests

    @Test
    fun `getSessionMetrics returns aggregated turn metrics`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordLatency(LatencyType.STT, 100)
            telemetryEngine.recordLatency(LatencyType.LLM_TTFT, 200)
            telemetryEngine.recordLatency(LatencyType.TTS_TTFB, 150)
            telemetryEngine.recordLatency(LatencyType.E2E_TURN, 450)
            telemetryEngine.recordCost("openai", 0.05)

            val metrics = telemetryEngine.getSessionMetrics("test-session")

            assertFalse(metrics.isEmpty())
            assertEquals(100, metrics[0].sttLatency)
            assertEquals(200, metrics[0].llmTTFT)
            assertEquals(150, metrics[0].ttsTTFB)
            assertEquals(450, metrics[0].e2eLatency)
            assertEquals(0.05, metrics[0].estimatedCost, 0.001)
        }

    @Test
    fun `getSessionMetrics returns empty for unknown session`() =
        runTest {
            val metrics = telemetryEngine.getSessionMetrics("unknown-session")

            assertTrue(metrics.isEmpty())
        }

    // MARK: - Clear Session Tests

    @Test
    fun `clearSession removes all data for session`() =
        runTest {
            telemetryEngine.startSession("test-session")
            telemetryEngine.recordLatency(LatencyType.STT, 100)
            telemetryEngine.recordCost("openai", 0.05)

            telemetryEngine.clearSession("test-session")

            val latencyStats = telemetryEngine.getLatencyStats("test-session", LatencyType.STT)
            val totalCost = telemetryEngine.getTotalCost("test-session")

            assertEquals(0L, latencyStats.average)
            assertEquals(0.0, totalCost, 0.001)
        }

    @Test
    fun `clearSession does not affect other sessions`() =
        runTest {
            telemetryEngine.startSession("session-1")
            telemetryEngine.recordLatency(LatencyType.STT, 100)
            telemetryEngine.endSession()

            telemetryEngine.startSession("session-2")
            telemetryEngine.recordLatency(LatencyType.STT, 200)

            telemetryEngine.clearSession("session-1")

            val stats = telemetryEngine.getLatencyStats("session-2", LatencyType.STT)
            assertEquals(200L, stats.average)
        }
}
