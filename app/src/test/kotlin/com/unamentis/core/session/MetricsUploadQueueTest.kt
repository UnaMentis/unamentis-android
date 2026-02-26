package com.unamentis.core.session

import com.unamentis.core.telemetry.TelemetryEngine
import com.unamentis.data.local.dao.QueuedMetricsDao
import com.unamentis.data.local.entity.QueuedMetricsEntity
import com.unamentis.data.model.LatencyType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MetricsUploadQueue.
 *
 * Tests cover:
 * - Payload building from TelemetryEngine data
 * - Enqueueing payloads with Room persistence
 * - Queue size management with eviction
 * - Pending item retrieval with retry filtering
 * - Item completion and retry increment
 * - Queue clearing
 *
 * The QueuedMetricsDao is mocked because it is a Room DAO interface
 * that requires Android instrumentation to instantiate. The TelemetryEngine
 * is a real instance per the project's testing philosophy (no mocking
 * internal services).
 */
class MetricsUploadQueueTest {
    private lateinit var queuedMetricsDao: QueuedMetricsDao
    private lateinit var telemetryEngine: TelemetryEngine
    private lateinit var json: Json
    private lateinit var queue: MetricsUploadQueue

    @Before
    fun setup() {
        queuedMetricsDao = mockk(relaxed = true)
        telemetryEngine = TelemetryEngine()
        json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        queue =
            MetricsUploadQueue(
                queuedMetricsDao = queuedMetricsDao,
                telemetryEngine = telemetryEngine,
                json = json,
            )
    }

    // =========================================================================
    // Payload Building Tests
    // =========================================================================

    @Test
    fun `buildPayload creates payload with correct session duration and turn count`() {
        telemetryEngine.startSession("session-1")

        val payload =
            queue.buildPayload(
                sessionId = "session-1",
                sessionDurationSeconds = 120.5,
                turnsTotal = 15,
                interruptions = 3,
            )

        assertEquals(120.5, payload.sessionDuration, 0.001)
        assertEquals(15, payload.turnsTotal)
        assertEquals(3, payload.interruptions)
    }

    @Test
    fun `buildPayload reads latency stats from TelemetryEngine`() {
        val sessionId = "session-latency"
        telemetryEngine.startSession(sessionId)

        // Record STT latencies
        telemetryEngine.recordLatency(LatencyType.STT, 100)
        telemetryEngine.recordLatency(LatencyType.STT, 200)
        telemetryEngine.recordLatency(LatencyType.STT, 300)

        // Record LLM TTFT latencies
        telemetryEngine.recordLatency(LatencyType.LLM_TTFT, 150)
        telemetryEngine.recordLatency(LatencyType.LLM_TTFT, 250)

        // Record TTS TTFB latencies
        telemetryEngine.recordLatency(LatencyType.TTS_TTFB, 50)
        telemetryEngine.recordLatency(LatencyType.TTS_TTFB, 80)

        // Record E2E latencies
        telemetryEngine.recordLatency(LatencyType.E2E_TURN, 400)
        telemetryEngine.recordLatency(LatencyType.E2E_TURN, 500)

        val payload =
            queue.buildPayload(
                sessionId = sessionId,
                sessionDurationSeconds = 60.0,
            )

        // STT stats: sorted=[100,200,300], median=200, p99=300
        assertEquals(200.0, payload.sttLatencyMedian, 0.001)
        assertEquals(300.0, payload.sttLatencyP99, 0.001)

        // LLM TTFT stats: sorted=[150,250], median=250 (size/2=1 -> index 1)
        assertEquals(250.0, payload.llmTTFTMedian, 0.001)

        // TTS TTFB stats: sorted=[50,80], median=80 (size/2=1 -> index 1)
        assertEquals(80.0, payload.ttsTTFBMedian, 0.001)

        // E2E stats: sorted=[400,500], median=500 (size/2=1 -> index 1)
        assertEquals(500.0, payload.e2eLatencyMedian, 0.001)
    }

    @Test
    fun `buildPayload reads cost breakdown from TelemetryEngine`() {
        val sessionId = "session-cost"
        telemetryEngine.startSession(sessionId)
        telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))
        telemetryEngine.recordCost("elevenlabs", 0.02, mapOf("type" to "TTS"))
        telemetryEngine.recordCost("openai", 0.05, mapOf("type" to "LLM"))

        val payload =
            queue.buildPayload(
                sessionId = sessionId,
                sessionDurationSeconds = 90.0,
            )

        assertEquals(0.01, payload.sttCost, 0.001)
        assertEquals(0.02, payload.ttsCost, 0.001)
        assertEquals(0.05, payload.llmCost, 0.001)
        assertEquals(0.08, payload.totalCost, 0.001)
    }

    @Test
    fun `buildPayload returns zero stats when no data recorded`() {
        telemetryEngine.startSession("empty-session")

        val payload =
            queue.buildPayload(
                sessionId = "empty-session",
                sessionDurationSeconds = 10.0,
            )

        assertEquals(0.0, payload.sttLatencyMedian, 0.001)
        assertEquals(0.0, payload.sttLatencyP99, 0.001)
        assertEquals(0.0, payload.llmTTFTMedian, 0.001)
        assertEquals(0.0, payload.llmTTFTP99, 0.001)
        assertEquals(0.0, payload.ttsTTFBMedian, 0.001)
        assertEquals(0.0, payload.ttsTTFBP99, 0.001)
        assertEquals(0.0, payload.e2eLatencyMedian, 0.001)
        assertEquals(0.0, payload.e2eLatencyP99, 0.001)
        assertEquals(0.0, payload.sttCost, 0.001)
        assertEquals(0.0, payload.ttsCost, 0.001)
        assertEquals(0.0, payload.llmCost, 0.001)
        assertEquals(0.0, payload.totalCost, 0.001)
    }

    @Test
    fun `buildPayload generates ISO 8601 timestamp`() {
        telemetryEngine.startSession("timestamp-session")

        val payload =
            queue.buildPayload(
                sessionId = "timestamp-session",
                sessionDurationSeconds = 1.0,
            )

        // ISO 8601 format: yyyy-MM-dd'T'HH:mm:ss'Z'
        assertTrue(
            "Timestamp should match ISO 8601 format, was: ${payload.timestamp}",
            payload.timestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")),
        )
    }

    @Test
    fun `buildPayload defaults turnsTotal and interruptions to zero`() {
        telemetryEngine.startSession("defaults-session")

        val payload =
            queue.buildPayload(
                sessionId = "defaults-session",
                sessionDurationSeconds = 5.0,
            )

        assertEquals(0, payload.turnsTotal)
        assertEquals(0, payload.interruptions)
    }

    // =========================================================================
    // Enqueue Tests
    // =========================================================================

    @Test
    fun `enqueuePayload inserts entity into DAO`() =
        runTest {
            val entitySlot = slot<QueuedMetricsEntity>()
            coEvery { queuedMetricsDao.insert(capture(entitySlot)) } just Runs
            coEvery { queuedMetricsDao.count() } returns 1

            val payload =
                MetricsPayload(
                    timestamp = "2024-01-01T00:00:00Z",
                    sessionDuration = 60.0,
                    turnsTotal = 5,
                    sttCost = 0.01,
                )

            queue.enqueuePayload(payload)

            coVerify(exactly = 1) { queuedMetricsDao.insert(any()) }

            val entity = entitySlot.captured
            assertTrue("Entity ID should not be blank", entity.id.isNotBlank())
            assertEquals(0, entity.retryCount)
            assertTrue("queuedAt should be recent", entity.queuedAt > 0)

            // Verify the JSON payload can be deserialized back
            val decoded = json.decodeFromString<MetricsPayload>(entity.payloadJson)
            assertEquals(60.0, decoded.sessionDuration, 0.001)
            assertEquals(5, decoded.turnsTotal)
            assertEquals(0.01, decoded.sttCost, 0.001)
        }

    @Test
    fun `enqueue builds payload from TelemetryEngine and inserts`() =
        runTest {
            val sessionId = "enqueue-session"
            telemetryEngine.startSession(sessionId)
            telemetryEngine.recordLatency(LatencyType.STT, 100)
            telemetryEngine.recordCost("deepgram", 0.01, mapOf("type" to "STT"))

            val entitySlot = slot<QueuedMetricsEntity>()
            coEvery { queuedMetricsDao.insert(capture(entitySlot)) } just Runs
            coEvery { queuedMetricsDao.count() } returns 1

            queue.enqueue(
                sessionId = sessionId,
                sessionDurationSeconds = 30.0,
                turnsTotal = 2,
                interruptions = 1,
            )

            coVerify(exactly = 1) { queuedMetricsDao.insert(any()) }

            val decoded = json.decodeFromString<MetricsPayload>(entitySlot.captured.payloadJson)
            assertEquals(30.0, decoded.sessionDuration, 0.001)
            assertEquals(2, decoded.turnsTotal)
            assertEquals(1, decoded.interruptions)
            assertEquals(0.01, decoded.sttCost, 0.001)
        }

    // =========================================================================
    // Queue Size Management & Eviction Tests
    // =========================================================================

    @Test
    fun `enqueuePayload triggers eviction when queue exceeds max size`() =
        runTest {
            coEvery { queuedMetricsDao.insert(any()) } just Runs
            coEvery { queuedMetricsDao.count() } returns MetricsUploadQueue.MAX_QUEUE_SIZE + 1
            coEvery { queuedMetricsDao.evictOldest(MetricsUploadQueue.MAX_QUEUE_SIZE) } just Runs

            val payload =
                MetricsPayload(
                    timestamp = "2024-01-01T00:00:00Z",
                    sessionDuration = 10.0,
                )

            queue.enqueuePayload(payload)

            coVerify(exactly = 1) {
                queuedMetricsDao.evictOldest(MetricsUploadQueue.MAX_QUEUE_SIZE)
            }
        }

    @Test
    fun `enqueuePayload does not evict when queue is at max size`() =
        runTest {
            coEvery { queuedMetricsDao.insert(any()) } just Runs
            coEvery { queuedMetricsDao.count() } returns MetricsUploadQueue.MAX_QUEUE_SIZE

            val payload =
                MetricsPayload(
                    timestamp = "2024-01-01T00:00:00Z",
                    sessionDuration = 10.0,
                )

            queue.enqueuePayload(payload)

            coVerify(exactly = 0) {
                queuedMetricsDao.evictOldest(any())
            }
        }

    @Test
    fun `enqueuePayload does not evict when queue is under max size`() =
        runTest {
            coEvery { queuedMetricsDao.insert(any()) } just Runs
            coEvery { queuedMetricsDao.count() } returns 50

            val payload =
                MetricsPayload(
                    timestamp = "2024-01-01T00:00:00Z",
                    sessionDuration = 10.0,
                )

            queue.enqueuePayload(payload)

            coVerify(exactly = 0) {
                queuedMetricsDao.evictOldest(any())
            }
        }

    // =========================================================================
    // Get Pending Tests
    // =========================================================================

    @Test
    fun `getPending returns deserialized items from DAO`() =
        runTest {
            val payload1 =
                MetricsPayload(
                    timestamp = "2024-01-01T00:00:00Z",
                    sessionDuration = 60.0,
                    turnsTotal = 5,
                )
            val payload2 =
                MetricsPayload(
                    timestamp = "2024-01-02T00:00:00Z",
                    sessionDuration = 120.0,
                    turnsTotal = 10,
                )

            val entities =
                listOf(
                    QueuedMetricsEntity(
                        id = "item-1",
                        payloadJson = json.encodeToString(MetricsPayload.serializer(), payload1),
                        queuedAt = 1000L,
                        retryCount = 0,
                    ),
                    QueuedMetricsEntity(
                        id = "item-2",
                        payloadJson = json.encodeToString(MetricsPayload.serializer(), payload2),
                        queuedAt = 2000L,
                        retryCount = 2,
                    ),
                )

            coEvery { queuedMetricsDao.getPending(MetricsUploadQueue.MAX_RETRIES) } returns entities

            val pending = queue.getPending()

            assertEquals(2, pending.size)

            assertEquals("item-1", pending[0].id)
            assertEquals(60.0, pending[0].payload.sessionDuration, 0.001)
            assertEquals(5, pending[0].payload.turnsTotal)
            assertEquals(1000L, pending[0].queuedAt)
            assertEquals(0, pending[0].retryCount)

            assertEquals("item-2", pending[1].id)
            assertEquals(120.0, pending[1].payload.sessionDuration, 0.001)
            assertEquals(10, pending[1].payload.turnsTotal)
            assertEquals(2000L, pending[1].queuedAt)
            assertEquals(2, pending[1].retryCount)
        }

    @Test
    fun `getPending returns empty list when no items are pending`() =
        runTest {
            coEvery { queuedMetricsDao.getPending(MetricsUploadQueue.MAX_RETRIES) } returns emptyList()

            val pending = queue.getPending()

            assertTrue(pending.isEmpty())
        }

    @Test
    fun `getPending passes MAX_RETRIES to DAO`() =
        runTest {
            coEvery { queuedMetricsDao.getPending(any()) } returns emptyList()

            queue.getPending()

            coVerify(exactly = 1) {
                queuedMetricsDao.getPending(MetricsUploadQueue.MAX_RETRIES)
            }
        }

    // =========================================================================
    // Mark Completed Tests
    // =========================================================================

    @Test
    fun `markCompleted deletes item by ID`() =
        runTest {
            coEvery { queuedMetricsDao.deleteById("item-123") } just Runs

            queue.markCompleted("item-123")

            coVerify(exactly = 1) { queuedMetricsDao.deleteById("item-123") }
        }

    // =========================================================================
    // Increment Retry Tests
    // =========================================================================

    @Test
    fun `incrementRetry increments retry count via DAO`() =
        runTest {
            coEvery { queuedMetricsDao.incrementRetryCount("item-456") } just Runs

            queue.incrementRetry("item-456")

            coVerify(exactly = 1) { queuedMetricsDao.incrementRetryCount("item-456") }
        }

    // =========================================================================
    // Count Tests
    // =========================================================================

    @Test
    fun `count returns DAO count`() =
        runTest {
            coEvery { queuedMetricsDao.count() } returns 42

            val count = queue.count()

            assertEquals(42, count)
        }

    @Test
    fun `count returns zero for empty queue`() =
        runTest {
            coEvery { queuedMetricsDao.count() } returns 0

            val count = queue.count()

            assertEquals(0, count)
        }

    // =========================================================================
    // Clear Tests
    // =========================================================================

    @Test
    fun `clear deletes all items via DAO`() =
        runTest {
            coEvery { queuedMetricsDao.deleteAll() } just Runs

            queue.clear()

            coVerify(exactly = 1) { queuedMetricsDao.deleteAll() }
        }

    // =========================================================================
    // Serialization Round-Trip Tests
    // =========================================================================

    @Test
    fun `MetricsPayload serialization round-trip preserves all fields`() {
        val original =
            MetricsPayload(
                timestamp = "2024-06-15T12:30:45Z",
                sessionDuration = 5432.1,
                turnsTotal = 42,
                interruptions = 7,
                sttLatencyMedian = 150.5,
                sttLatencyP99 = 450.2,
                llmTTFTMedian = 200.0,
                llmTTFTP99 = 800.0,
                ttsTTFBMedian = 100.0,
                ttsTTFBP99 = 300.0,
                e2eLatencyMedian = 400.0,
                e2eLatencyP99 = 1200.0,
                sttCost = 0.01,
                ttsCost = 0.02,
                llmCost = 0.05,
                totalCost = 0.08,
                thermalThrottleEvents = 2,
                networkDegradations = 1,
            )

        val encoded = json.encodeToString(MetricsPayload.serializer(), original)
        val decoded = json.decodeFromString<MetricsPayload>(encoded)

        assertEquals(original, decoded)
    }

    // =========================================================================
    // Constants Tests
    // =========================================================================

    @Test
    fun `MAX_QUEUE_SIZE is 100`() {
        assertEquals(100, MetricsUploadQueue.MAX_QUEUE_SIZE)
    }

    @Test
    fun `MAX_RETRIES is 5`() {
        assertEquals(5, MetricsUploadQueue.MAX_RETRIES)
    }
}
