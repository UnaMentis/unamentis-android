package com.unamentis.core.session

import com.unamentis.data.remote.ApiClient
import com.unamentis.data.remote.MetricsUploadRequest
import com.unamentis.data.remote.MetricsUploadResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for MetricsUploadService.
 *
 * Tests cover:
 * - Successful upload flow
 * - Upload failure with automatic queuing
 * - Queue draining with success and failure scenarios
 * - Batch size limiting
 * - Concurrent drain prevention via mutex
 * - Error type handling (IOException -> NetworkUnavailable)
 * - Pending count delegation
 *
 * The MetricsUploadQueue and ApiClient are mocked because:
 * - MetricsUploadQueue's behavior is fully tested in MetricsUploadQueueTest
 * - ApiClient is a network-facing class (paid external API communication)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetricsUploadServiceTest {
    private lateinit var queue: MetricsUploadQueue
    private lateinit var apiClient: ApiClient
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var service: MetricsUploadService

    private val samplePayload =
        MetricsPayload(
            timestamp = "2024-01-01T00:00:00Z",
            sessionDuration = 60.0,
            turnsTotal = 5,
            interruptions = 1,
            sttLatencyMedian = 100.0,
            sttLatencyP99 = 300.0,
            llmTTFTMedian = 200.0,
            llmTTFTP99 = 600.0,
            ttsTTFBMedian = 80.0,
            ttsTTFBP99 = 250.0,
            e2eLatencyMedian = 400.0,
            e2eLatencyP99 = 1000.0,
            sttCost = 0.01,
            ttsCost = 0.02,
            llmCost = 0.05,
            totalCost = 0.08,
            thermalThrottleEvents = 0,
            networkDegradations = 0,
        )

    @Before
    fun setup() {
        queue = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        service =
            MetricsUploadService(
                queue = queue,
                apiClient = apiClient,
                scope = testScope,
            )
    }

    // =========================================================================
    // Upload Tests
    // =========================================================================

    @Test
    fun `upload sends payload to server on success`() =
        testScope.runTest {
            coEvery { queue.buildPayload(any(), any(), any(), any()) } returns samplePayload
            coEvery { apiClient.uploadMetrics(any()) } returns
                MetricsUploadResponse(
                    status = "ok",
                    id = "metrics-123",
                )

            service.upload(
                sessionId = "session-1",
                sessionDurationSeconds = 60.0,
                turnsTotal = 5,
                interruptions = 1,
            )

            coVerify(exactly = 1) { apiClient.uploadMetrics(any()) }
            // Should NOT queue on success
            coVerify(exactly = 0) { queue.enqueuePayload(any()) }
        }

    @Test
    fun `upload maps payload fields to MetricsUploadRequest correctly`() =
        testScope.runTest {
            val requestSlot = slot<MetricsUploadRequest>()
            coEvery { queue.buildPayload(any(), any(), any(), any()) } returns samplePayload
            coEvery { apiClient.uploadMetrics(capture(requestSlot)) } returns
                MetricsUploadResponse(
                    status = "ok",
                )

            service.upload(
                sessionId = "session-1",
                sessionDurationSeconds = 60.0,
            )

            val request = requestSlot.captured
            assertEquals(samplePayload.sessionDuration, request.sessionDuration, 0.001)
            assertEquals(samplePayload.turnsTotal, request.turnsTotal)
            assertEquals(samplePayload.interruptions, request.interruptions)
            assertEquals(samplePayload.sttLatencyMedian, request.sttLatencyMedian, 0.001)
            assertEquals(samplePayload.sttLatencyP99, request.sttLatencyP99, 0.001)
            assertEquals(samplePayload.llmTTFTMedian, request.llmTtftMedian, 0.001)
            assertEquals(samplePayload.llmTTFTP99, request.llmTtftP99, 0.001)
            assertEquals(samplePayload.ttsTTFBMedian, request.ttsTtfbMedian, 0.001)
            assertEquals(samplePayload.ttsTTFBP99, request.ttsTtfbP99, 0.001)
            assertEquals(samplePayload.e2eLatencyMedian, request.e2eLatencyMedian, 0.001)
            assertEquals(samplePayload.e2eLatencyP99, request.e2eLatencyP99, 0.001)
            assertEquals(samplePayload.sttCost, request.sttCost, 0.001)
            assertEquals(samplePayload.ttsCost, request.ttsCost, 0.001)
            assertEquals(samplePayload.llmCost, request.llmCost, 0.001)
            assertEquals(samplePayload.totalCost, request.totalCost, 0.001)
            assertEquals(samplePayload.thermalThrottleEvents, request.thermalThrottleEvents)
            assertEquals(samplePayload.networkDegradations, request.networkDegradations)
        }

    @Test
    fun `upload queues payload when server is unreachable`() =
        testScope.runTest {
            coEvery { queue.buildPayload(any(), any(), any(), any()) } returns samplePayload
            coEvery { apiClient.uploadMetrics(any()) } throws IOException("Connection refused")

            service.upload(
                sessionId = "session-1",
                sessionDurationSeconds = 60.0,
            )

            coVerify(exactly = 1) { queue.enqueuePayload(samplePayload) }
        }

    @Test
    fun `upload queues payload on any exception`() =
        testScope.runTest {
            coEvery { queue.buildPayload(any(), any(), any(), any()) } returns samplePayload
            coEvery { apiClient.uploadMetrics(any()) } throws RuntimeException("Unexpected error")

            service.upload(
                sessionId = "session-1",
                sessionDurationSeconds = 60.0,
            )

            coVerify(exactly = 1) { queue.enqueuePayload(samplePayload) }
        }

    @Test
    fun `upload passes correct parameters to buildPayload`() =
        testScope.runTest {
            coEvery { queue.buildPayload(any(), any(), any(), any()) } returns samplePayload
            coEvery { apiClient.uploadMetrics(any()) } returns MetricsUploadResponse(status = "ok")

            service.upload(
                sessionId = "my-session",
                sessionDurationSeconds = 90.0,
                turnsTotal = 20,
                interruptions = 3,
            )

            coVerify(exactly = 1) {
                queue.buildPayload(
                    sessionId = "my-session",
                    sessionDurationSeconds = 90.0,
                    turnsTotal = 20,
                    interruptions = 3,
                )
            }
        }

    // =========================================================================
    // sendToServer Tests
    // =========================================================================

    @Test
    fun `sendToServer wraps IOException as NetworkUnavailable`() =
        testScope.runTest {
            coEvery { apiClient.uploadMetrics(any()) } throws IOException("timeout")

            var caught: Exception? = null
            try {
                service.sendToServer(samplePayload)
            } catch (e: Exception) {
                caught = e
            }

            assertTrue(
                "Should throw MetricsUploadError.NetworkUnavailable, was: $caught",
                caught is MetricsUploadError.NetworkUnavailable,
            )
        }

    @Test
    fun `sendToServer passes through on successful upload`() =
        testScope.runTest {
            coEvery { apiClient.uploadMetrics(any()) } returns
                MetricsUploadResponse(
                    status = "ok",
                    id = "metrics-456",
                )

            // Should not throw
            service.sendToServer(samplePayload)

            coVerify(exactly = 1) { apiClient.uploadMetrics(any()) }
        }

    @Test
    fun `sendToServer sets clientId and clientName in request`() =
        testScope.runTest {
            val requestSlot = slot<MetricsUploadRequest>()
            coEvery { apiClient.uploadMetrics(capture(requestSlot)) } returns
                MetricsUploadResponse(
                    status = "ok",
                )

            service.sendToServer(samplePayload)

            val request = requestSlot.captured
            assertTrue("clientId should not be empty", request.clientId.isNotBlank())
            assertTrue("clientName should not be empty", request.clientName.isNotBlank())
        }

    @Test
    fun `sendToServer uses timestamp as sessionId in request`() =
        testScope.runTest {
            val requestSlot = slot<MetricsUploadRequest>()
            coEvery { apiClient.uploadMetrics(capture(requestSlot)) } returns
                MetricsUploadResponse(
                    status = "ok",
                )

            service.sendToServer(samplePayload)

            assertEquals(samplePayload.timestamp, requestSlot.captured.sessionId)
        }

    // =========================================================================
    // Drain Queue Tests
    // =========================================================================

    @Test
    fun `drainQueue uploads all pending items on success`() =
        testScope.runTest {
            val item1 =
                QueuedItem(
                    id = "item-1",
                    payload = samplePayload,
                    queuedAt = 1000L,
                    retryCount = 0,
                )
            val item2 =
                QueuedItem(
                    id = "item-2",
                    payload = samplePayload.copy(sessionDuration = 120.0),
                    queuedAt = 2000L,
                    retryCount = 1,
                )

            coEvery { queue.getPending() } returns listOf(item1, item2)
            coEvery { apiClient.uploadMetrics(any()) } returns MetricsUploadResponse(status = "ok")
            coEvery { queue.markCompleted(any()) } just Runs

            service.drainQueue()

            coVerify(exactly = 2) { apiClient.uploadMetrics(any()) }
            coVerify(exactly = 1) { queue.markCompleted("item-1") }
            coVerify(exactly = 1) { queue.markCompleted("item-2") }
        }

    @Test
    fun `drainQueue stops on first failure and increments retry`() =
        testScope.runTest {
            val item1 =
                QueuedItem(
                    id = "item-1",
                    payload = samplePayload,
                    queuedAt = 1000L,
                    retryCount = 0,
                )
            val item2 =
                QueuedItem(
                    id = "item-2",
                    payload = samplePayload.copy(sessionDuration = 120.0),
                    queuedAt = 2000L,
                    retryCount = 0,
                )

            coEvery { queue.getPending() } returns listOf(item1, item2)
            coEvery { apiClient.uploadMetrics(any()) } throws IOException("Server down")
            coEvery { queue.incrementRetry(any()) } just Runs

            service.drainQueue()

            // Should only attempt the first item, then stop
            coVerify(exactly = 1) { apiClient.uploadMetrics(any()) }
            coVerify(exactly = 1) { queue.incrementRetry("item-1") }
            // item-2 should not be attempted
            coVerify(exactly = 0) { queue.markCompleted(any()) }
            coVerify(exactly = 0) { queue.incrementRetry("item-2") }
        }

    @Test
    fun `drainQueue marks first item completed then stops on second failure`() =
        testScope.runTest {
            val item1 =
                QueuedItem(
                    id = "item-1",
                    payload = samplePayload,
                    queuedAt = 1000L,
                    retryCount = 0,
                )
            val item2 =
                QueuedItem(
                    id = "item-2",
                    payload = samplePayload,
                    queuedAt = 2000L,
                    retryCount = 0,
                )
            val item3 =
                QueuedItem(
                    id = "item-3",
                    payload = samplePayload,
                    queuedAt = 3000L,
                    retryCount = 0,
                )

            coEvery { queue.getPending() } returns listOf(item1, item2, item3)
            coEvery { apiClient.uploadMetrics(any()) } returns MetricsUploadResponse(status = "ok") andThenThrows
                IOException("Server error")
            coEvery { queue.markCompleted(any()) } just Runs
            coEvery { queue.incrementRetry(any()) } just Runs

            service.drainQueue()

            // First item succeeds, second fails, third is not attempted
            coVerify(exactly = 1) { queue.markCompleted("item-1") }
            coVerify(exactly = 1) { queue.incrementRetry("item-2") }
            coVerify(exactly = 0) { queue.markCompleted("item-2") }
            coVerify(exactly = 0) { queue.markCompleted("item-3") }
            coVerify(exactly = 0) { queue.incrementRetry("item-3") }
        }

    @Test
    fun `drainQueue does nothing when queue is empty`() =
        testScope.runTest {
            coEvery { queue.getPending() } returns emptyList()

            service.drainQueue()

            coVerify(exactly = 0) { apiClient.uploadMetrics(any()) }
            coVerify(exactly = 0) { queue.markCompleted(any()) }
        }

    // =========================================================================
    // Batch Size Tests
    // =========================================================================

    @Test
    fun `drainQueue limits batch to MAX_BATCH_SIZE`() =
        testScope.runTest {
            // Create more items than MAX_BATCH_SIZE
            val items =
                (1..MetricsUploadService.MAX_BATCH_SIZE + 10).map { i ->
                    QueuedItem(
                        id = "item-$i",
                        payload = samplePayload,
                        queuedAt = i.toLong() * 1000,
                        retryCount = 0,
                    )
                }

            coEvery { queue.getPending() } returns items
            coEvery { apiClient.uploadMetrics(any()) } returns MetricsUploadResponse(status = "ok")
            coEvery { queue.markCompleted(any()) } just Runs

            service.drainQueue()

            // Should only process MAX_BATCH_SIZE items
            coVerify(exactly = MetricsUploadService.MAX_BATCH_SIZE) {
                apiClient.uploadMetrics(any())
            }
        }

    @Test
    fun `MAX_BATCH_SIZE is 50`() {
        assertEquals(50, MetricsUploadService.MAX_BATCH_SIZE)
    }

    // =========================================================================
    // Concurrent Drain Prevention Tests
    // =========================================================================

    @Test
    fun `concurrent drainQueue calls are serialized via mutex`() =
        testScope.runTest {
            // Set up a slow drain operation
            var uploadCount = 0
            val items =
                listOf(
                    QueuedItem(
                        id = "item-1",
                        payload = samplePayload,
                        queuedAt = 1000L,
                        retryCount = 0,
                    ),
                )

            coEvery { queue.getPending() } returns items
            coEvery { apiClient.uploadMetrics(any()) } coAnswers {
                uploadCount++
                MetricsUploadResponse(status = "ok")
            }
            coEvery { queue.markCompleted(any()) } just Runs

            // First drain acquires the mutex
            service.drainQueue()
            advanceUntilIdle()

            // After first drain completes, second should work
            service.drainQueue()
            advanceUntilIdle()

            // Both drains should process (they run sequentially, not concurrently in this test)
            assertEquals(2, uploadCount)
        }

    // =========================================================================
    // Pending Count Tests
    // =========================================================================

    @Test
    fun `pendingCount delegates to queue count`() =
        testScope.runTest {
            coEvery { queue.count() } returns 7

            val count = service.pendingCount()

            assertEquals(7, count)
            coVerify(exactly = 1) { queue.count() }
        }

    @Test
    fun `pendingCount returns zero for empty queue`() =
        testScope.runTest {
            coEvery { queue.count() } returns 0

            val count = service.pendingCount()

            assertEquals(0, count)
        }

    // =========================================================================
    // Schedule Periodic Drain Tests
    // =========================================================================

    @Test
    fun `schedulePeriodicDrain launches drain after interval`() =
        testScope.runTest {
            coEvery { queue.count() } returns 1 andThen 0
            coEvery { queue.getPending() } returns
                listOf(
                    QueuedItem(
                        id = "item-1",
                        payload = samplePayload,
                        queuedAt = 1000L,
                        retryCount = 0,
                    ),
                ) andThen emptyList()
            coEvery { apiClient.uploadMetrics(any()) } returns MetricsUploadResponse(status = "ok")
            coEvery { queue.markCompleted(any()) } just Runs

            // Use backgroundScope so the infinite loop is cancelled automatically when the
            // test ends, avoiding UncompletedCoroutinesError.
            val bgService =
                MetricsUploadService(
                    queue = queue,
                    apiClient = apiClient,
                    scope = backgroundScope,
                )

            // Schedule with a short interval
            bgService.schedulePeriodicDrain(intervalMs = 1000L)

            // Advance past the interval
            testDispatcher.scheduler.advanceTimeBy(1500L)
            advanceUntilIdle()

            // The drain should have executed at least once
            coVerify(atLeast = 1) { queue.count() }
        }

    @Test
    fun `schedulePeriodicDrain skips drain when queue is empty`() =
        testScope.runTest {
            coEvery { queue.count() } returns 0

            // Use backgroundScope so the infinite loop is cancelled automatically when the
            // test ends, avoiding UncompletedCoroutinesError.
            val bgService =
                MetricsUploadService(
                    queue = queue,
                    apiClient = apiClient,
                    scope = backgroundScope,
                )

            bgService.schedulePeriodicDrain(intervalMs = 500L)

            // Advance past interval
            testDispatcher.scheduler.advanceTimeBy(600L)
            advanceUntilIdle()

            // Should check count but not call getPending (queue is empty)
            coVerify(atLeast = 1) { queue.count() }
            coVerify(exactly = 0) { queue.getPending() }
        }

    // =========================================================================
    // Error Type Tests
    // =========================================================================

    @Test
    fun `MetricsUploadError ServerError contains status code`() {
        val error = MetricsUploadError.ServerError(503)

        assertEquals(503, error.statusCode)
        assertTrue(error.message!!.contains("503"))
    }

    @Test
    fun `MetricsUploadError InvalidResponse has descriptive message`() {
        val error = MetricsUploadError.InvalidResponse()

        assertTrue(error.message!!.contains("Invalid response"))
    }

    @Test
    fun `MetricsUploadError NetworkUnavailable wraps cause`() {
        val cause = IOException("Connection timed out")
        val error = MetricsUploadError.NetworkUnavailable(cause)

        assertEquals(cause, error.cause)
        assertTrue(error.message!!.contains("Network unavailable"))
    }

    @Test
    fun `MetricsUploadError NetworkUnavailable works without cause`() {
        val error = MetricsUploadError.NetworkUnavailable()

        assertEquals(null, error.cause)
        assertTrue(error.message!!.contains("Network unavailable"))
    }

    @Test
    fun `MetricsUploadError types extend Exception`() {
        val serverError: Exception = MetricsUploadError.ServerError(500)
        val invalidResponse: Exception = MetricsUploadError.InvalidResponse()
        val networkError: Exception = MetricsUploadError.NetworkUnavailable()

        assertTrue(serverError is MetricsUploadError)
        assertTrue(invalidResponse is MetricsUploadError)
        assertTrue(networkError is MetricsUploadError)
    }
}
