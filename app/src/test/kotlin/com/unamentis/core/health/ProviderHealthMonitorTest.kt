package com.unamentis.core.health

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for ProviderHealthMonitor.
 *
 * Tests health monitoring state transitions and HTTP health checks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderHealthMonitorTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var healthMonitor: ProviderHealthMonitor

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client =
            OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun createHealthMonitor(
        unhealthyThreshold: Int = 3,
        healthyThreshold: Int = 2,
    ): ProviderHealthMonitor {
        return ProviderHealthMonitor(
            config =
                HealthMonitorConfig(
                    healthEndpoint = mockWebServer.url("/health").toString(),
                    checkIntervalMs = 100L,
                    unhealthyThreshold = unhealthyThreshold,
                    healthyThreshold = healthyThreshold,
                    timeoutMs = 1000L,
                ),
            client = client,
            providerName = "TestProvider",
        )
    }

    // Initial State Tests

    @Test
    fun `should start with HEALTHY status`() {
        healthMonitor = createHealthMonitor()
        assertEquals(HealthStatus.HEALTHY, healthMonitor.currentStatus)
    }

    @Test
    fun `should report as available initially`() {
        healthMonitor = createHealthMonitor()
        assertTrue(healthMonitor.isAvailable())
    }

    // Health Check Success Tests

    @Test
    fun `should remain HEALTHY on successful check`() =
        runTest {
            healthMonitor = createHealthMonitor()
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            val status = healthMonitor.checkHealth()

            assertEquals(HealthStatus.HEALTHY, status)
            assertEquals(HealthStatus.HEALTHY, healthMonitor.currentStatus)
        }

    @Test
    fun `should transition from DEGRADED to HEALTHY after threshold successes`() =
        runTest {
            healthMonitor = createHealthMonitor(healthyThreshold = 2)

            // First, make it degraded by recording a failure
            healthMonitor.recordFailure()
            assertEquals(HealthStatus.DEGRADED, healthMonitor.currentStatus)

            // Enqueue successful responses
            mockWebServer.enqueue(MockResponse().setResponseCode(200))
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            healthMonitor.checkHealth()
            assertEquals(HealthStatus.DEGRADED, healthMonitor.currentStatus)

            healthMonitor.checkHealth()
            assertEquals(HealthStatus.HEALTHY, healthMonitor.currentStatus)
        }

    @Test
    fun `should transition from UNHEALTHY to DEGRADED on first success`() =
        runTest {
            healthMonitor = createHealthMonitor()

            // Make it unhealthy
            repeat(3) { healthMonitor.recordFailure() }
            assertEquals(HealthStatus.UNHEALTHY, healthMonitor.currentStatus)

            // One success should make it degraded
            mockWebServer.enqueue(MockResponse().setResponseCode(200))
            healthMonitor.checkHealth()

            assertEquals(HealthStatus.DEGRADED, healthMonitor.currentStatus)
        }

    // Health Check Failure Tests

    @Test
    fun `should transition to DEGRADED on first failure`() =
        runTest {
            healthMonitor = createHealthMonitor()

            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            healthMonitor.checkHealth()

            assertEquals(HealthStatus.DEGRADED, healthMonitor.currentStatus)
        }

    @Test
    fun `should transition to UNHEALTHY after threshold failures`() =
        runTest {
            healthMonitor = createHealthMonitor(unhealthyThreshold = 3)

            repeat(3) {
                mockWebServer.enqueue(MockResponse().setResponseCode(500))
                healthMonitor.checkHealth()
            }

            assertEquals(HealthStatus.UNHEALTHY, healthMonitor.currentStatus)
        }

    @Test
    fun `should report as unavailable when UNHEALTHY`() =
        runTest {
            healthMonitor = createHealthMonitor(unhealthyThreshold = 1)

            mockWebServer.enqueue(MockResponse().setResponseCode(500))
            healthMonitor.checkHealth()

            assertFalse(healthMonitor.isAvailable())
        }

    // Manual Recording Tests

    @Test
    fun `should allow manual success recording`() {
        healthMonitor = createHealthMonitor()

        // Make it degraded first
        healthMonitor.recordFailure()

        // Manual success
        healthMonitor.recordSuccess()
        healthMonitor.recordSuccess()

        assertEquals(HealthStatus.HEALTHY, healthMonitor.currentStatus)
    }

    @Test
    fun `should allow manual failure recording`() {
        healthMonitor = createHealthMonitor(unhealthyThreshold = 2)

        healthMonitor.recordFailure()
        healthMonitor.recordFailure()

        assertEquals(HealthStatus.UNHEALTHY, healthMonitor.currentStatus)
    }

    @Test
    fun `should reset consecutive counters on opposite result`() {
        healthMonitor = createHealthMonitor(unhealthyThreshold = 3)

        // Two failures
        healthMonitor.recordFailure()
        healthMonitor.recordFailure()
        assertEquals(HealthStatus.DEGRADED, healthMonitor.currentStatus)

        // One success resets failure counter
        healthMonitor.recordSuccess()

        // Two more failures should not trigger unhealthy (counter was reset)
        healthMonitor.recordFailure()
        healthMonitor.recordFailure()
        assertEquals(HealthStatus.DEGRADED, healthMonitor.currentStatus)

        // Third failure triggers unhealthy
        healthMonitor.recordFailure()
        assertEquals(HealthStatus.UNHEALTHY, healthMonitor.currentStatus)
    }

    // Reset Tests

    @Test
    fun `should reset to HEALTHY status`() {
        healthMonitor = createHealthMonitor(unhealthyThreshold = 1)

        healthMonitor.recordFailure()
        assertEquals(HealthStatus.UNHEALTHY, healthMonitor.currentStatus)

        healthMonitor.reset()

        assertEquals(HealthStatus.HEALTHY, healthMonitor.currentStatus)
        assertTrue(healthMonitor.isAvailable())
    }

    // StateFlow Tests

    @Test
    fun `should emit status changes through StateFlow`() =
        runTest {
            healthMonitor = createHealthMonitor(unhealthyThreshold = 1)

            // Initially HEALTHY
            assertEquals(HealthStatus.HEALTHY, healthMonitor.status.value)

            // Record failure
            healthMonitor.recordFailure()

            // Status should be updated
            assertEquals(HealthStatus.UNHEALTHY, healthMonitor.status.value)
        }
}
