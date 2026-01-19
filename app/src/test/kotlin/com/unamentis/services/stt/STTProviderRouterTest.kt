package com.unamentis.services.stt

import com.unamentis.core.health.HealthMonitorConfig
import com.unamentis.core.health.HealthStatus
import com.unamentis.core.health.ProviderHealthMonitor
import com.unamentis.data.model.STTResult
import com.unamentis.data.model.STTService
import com.unamentis.helpers.MockSTTService
import com.unamentis.helpers.STTException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class STTProviderRouterTest {
    private lateinit var router: STTProviderRouter
    private lateinit var mockPrimarySTT: MockSTTService
    private lateinit var mockFallbackSTT: MockSTTService
    private lateinit var mockOnDeviceSTT: MockSTTService

    @Before
    fun setup() {
        router = STTProviderRouter()
        mockPrimarySTT =
            MockSTTService().apply {
                configure("Primary provider transcript")
            }
        mockFallbackSTT =
            MockSTTService().apply {
                configure("Fallback provider transcript")
            }
        mockOnDeviceSTT =
            MockSTTService().apply {
                configure("On-device provider transcript")
            }
    }

    @After
    fun teardown() {
        router.shutdown()
    }

    // Helper to create a mock STT with custom provider name
    private fun createNamedMockSTT(
        name: String,
        transcript: String = "Test transcript",
    ): STTService {
        return object : STTService {
            override val providerName: String = name

            override fun startStreaming(): Flow<STTResult> =
                flow {
                    emit(STTResult(text = transcript, isFinal = true, confidence = 0.95f))
                }

            override suspend fun stopStreaming() {}
        }
    }

    // Helper to create a failing STT
    private fun createFailingMockSTT(
        name: String,
        error: Exception,
    ): STTService {
        return object : STTService {
            override val providerName: String = name

            override fun startStreaming(): Flow<STTResult> =
                flow {
                    throw error
                }

            override suspend fun stopStreaming() {}
        }
    }

    // Provider Registration Tests

    @Test
    fun `should register providers and track them`() {
        router.registerProvider(
            STTProviderRegistration(
                service = mockPrimarySTT,
                priority = STTProviderPriority.CLOUD_PRIMARY,
            ),
        )

        val providers = router.getRegisteredProviders()
        assertEquals(1, providers.size)
        assertTrue(providers.contains("MockSTT"))
    }

    @Test
    fun `should sort providers by priority`() {
        // Register in reverse order
        router.registerProvider(
            STTProviderRegistration(
                service = createNamedMockSTT("Fallback"),
                priority = STTProviderPriority.CLOUD_FALLBACK,
            ),
        )
        router.registerProvider(
            STTProviderRegistration(
                service = createNamedMockSTT("OnDevice"),
                priority = STTProviderPriority.ON_DEVICE,
            ),
        )
        router.registerProvider(
            STTProviderRegistration(
                service = createNamedMockSTT("Primary"),
                priority = STTProviderPriority.CLOUD_PRIMARY,
            ),
        )

        val providers = router.getRegisteredProviders()
        assertEquals(listOf("OnDevice", "Primary", "Fallback"), providers)
    }

    @Test
    fun `should unregister providers`() {
        router.registerProvider(
            STTProviderRegistration(
                service = createNamedMockSTT("TestProvider"),
                priority = STTProviderPriority.CLOUD_PRIMARY,
            ),
        )

        router.unregisterProvider("TestProvider")

        val providers = router.getRegisteredProviders()
        assertTrue(providers.isEmpty())
    }

    // Provider Selection Tests

    @Test
    fun `should select highest priority healthy provider`() =
        runTest {
            val onDeviceSTT = createNamedMockSTT("OnDevice", "On-device result")
            val cloudSTT = createNamedMockSTT("Cloud", "Cloud result")

            router.registerProvider(
                STTProviderRegistration(
                    service = cloudSTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )
            router.registerProvider(
                STTProviderRegistration(
                    service = onDeviceSTT,
                    priority = STTProviderPriority.ON_DEVICE,
                    isOnDevice = true,
                ),
            )

            val results = router.startStreaming().toList()

            assertTrue(results.any { it.text == "On-device result" })
            assertTrue(router.currentProviderIdentifier.contains("OnDevice"))
        }

    @Test
    fun `should throw when no providers registered`() =
        runTest {
            try {
                router.startStreaming().first()
                fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                // Expected
            }
        }

    @Test
    fun `should report hasAvailableProvider correctly`() {
        assertFalse(router.hasAvailableProvider())

        router.registerProvider(
            STTProviderRegistration(
                service = mockPrimarySTT,
                priority = STTProviderPriority.CLOUD_PRIMARY,
            ),
        )

        assertTrue(router.hasAvailableProvider())
    }

    // Health-Based Failover Tests

    @Test
    fun `should skip unhealthy providers`() =
        runTest {
            val primarySTT = createNamedMockSTT("Primary", "Primary result")
            val fallbackSTT = createNamedMockSTT("Fallback", "Fallback result")

            // Create a health monitor that reports unhealthy
            val unhealthyMonitor = createUnhealthyHealthMonitor()

            router.registerProvider(
                STTProviderRegistration(
                    service = primarySTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                    healthMonitor = unhealthyMonitor,
                ),
            )
            router.registerProvider(
                STTProviderRegistration(
                    service = fallbackSTT,
                    priority = STTProviderPriority.CLOUD_FALLBACK,
                ),
            )

            val results = router.startStreaming().toList()

            assertTrue(results.any { it.text == "Fallback result" })
            assertTrue(router.currentProviderIdentifier.contains("Fallback"))
        }

    @Test
    fun `should use degraded providers if no healthy alternative`() =
        runTest {
            val primarySTT = createNamedMockSTT("Primary", "Primary result")

            val degradedMonitor = createDegradedHealthMonitor()

            router.registerProvider(
                STTProviderRegistration(
                    service = primarySTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                    healthMonitor = degradedMonitor,
                ),
            )

            val results = router.startStreaming().toList()

            assertTrue(results.any { it.text == "Primary result" })
        }

    @Test
    fun `should return correct health status for provider`() {
        val healthyMonitor = createHealthyHealthMonitor()

        router.registerProvider(
            STTProviderRegistration(
                service = createNamedMockSTT("TestProvider"),
                priority = STTProviderPriority.CLOUD_PRIMARY,
                healthMonitor = healthyMonitor,
            ),
        )

        val health = router.getProviderHealth("TestProvider")
        assertEquals(HealthStatus.HEALTHY, health)
    }

    @Test
    fun `should return null health for provider without monitor`() {
        router.registerProvider(
            STTProviderRegistration(
                service = createNamedMockSTT("TestProvider"),
                priority = STTProviderPriority.CLOUD_PRIMARY,
                healthMonitor = null,
            ),
        )

        val health = router.getProviderHealth("TestProvider")
        assertEquals(null, health)
    }

    // Forced Provider Selection Tests

    @Test
    fun `should allow forcing specific provider`() =
        runTest {
            val onDeviceSTT = createNamedMockSTT("OnDevice", "On-device result")
            val cloudSTT = createNamedMockSTT("Cloud", "Cloud result")

            router.registerProvider(
                STTProviderRegistration(
                    service = onDeviceSTT,
                    priority = STTProviderPriority.ON_DEVICE,
                ),
            )
            router.registerProvider(
                STTProviderRegistration(
                    service = cloudSTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )

            // Force cloud provider
            val result = router.forceProvider("Cloud")
            assertTrue(result)

            val results = router.startStreaming().toList()
            assertTrue(results.any { it.text == "Cloud result" })
        }

    @Test
    fun `should return false when forcing non-existent provider`() {
        val result = router.forceProvider("NonExistent")
        assertFalse(result)
    }

    @Test
    fun `should clear forced provider and return to auto selection`() =
        runTest {
            val onDeviceSTT = createNamedMockSTT("OnDevice", "On-device result")
            val cloudSTT = createNamedMockSTT("Cloud", "Cloud result")

            router.registerProvider(
                STTProviderRegistration(
                    service = onDeviceSTT,
                    priority = STTProviderPriority.ON_DEVICE,
                ),
            )
            router.registerProvider(
                STTProviderRegistration(
                    service = cloudSTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )

            // Force cloud, then clear
            router.forceProvider("Cloud")
            router.clearForcedProvider()

            // Should return to auto selection (OnDevice has higher priority)
            val results = router.startStreaming().toList()
            assertTrue(results.any { it.text == "On-device result" })
        }

    // Error Handling Tests

    @Test
    fun `should propagate provider errors`() =
        runTest {
            val failingSTT =
                createFailingMockSTT(
                    "Failing",
                    STTException.ConnectionFailed("Test error"),
                )

            router.registerProvider(
                STTProviderRegistration(
                    service = failingSTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )

            try {
                router.startStreaming().toList()
                fail("Expected STTException.ConnectionFailed")
            } catch (e: STTException.ConnectionFailed) {
                // Expected
            }
        }

    // Streaming Operations Tests

    @Test
    fun `should stream results from selected provider`() =
        runTest {
            mockPrimarySTT.configureWithInterim(
                "Hello",
                "Hello world",
                finalText = "Hello world!",
            )

            router.registerProvider(
                STTProviderRegistration(
                    service = mockPrimarySTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )

            val results = router.startStreaming().toList()

            assertEquals(3, results.size)
            assertTrue(results.last().isFinal)
            assertEquals("Hello world!", results.last().text)
        }

    @Test
    fun `should stop streaming`() =
        runTest {
            router.registerProvider(
                STTProviderRegistration(
                    service = mockPrimarySTT,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )

            // Start streaming and collect first result to set activeProvider
            router.startStreaming().first()
            router.stopStreaming()

            assertEquals(1, mockPrimarySTT.stopStreamingCallCount)
        }

    // Provider Name Tests

    @Test
    fun `should report router with active provider name`() =
        runTest {
            router.registerProvider(
                STTProviderRegistration(
                    service = createNamedMockSTT("TestProvider"),
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                ),
            )

            router.startStreaming().first()

            assertTrue(router.providerName.contains("Router"))
            assertTrue(router.providerName.contains("TestProvider"))
        }

    @Test
    fun `should report none when no provider selected`() {
        assertTrue(router.providerName.contains("none"))
    }

    // Helper methods to create health monitors in different states
    private fun createHealthyHealthMonitor(): ProviderHealthMonitor {
        val client = OkHttpClient.Builder().build()
        return ProviderHealthMonitor(
            config =
                HealthMonitorConfig(
                    healthEndpoint = "http://localhost/health",
                    checkIntervalMs = 60000,
                ),
            client = client,
            providerName = "Test",
        )
        // Default state is HEALTHY
    }

    private fun createDegradedHealthMonitor(): ProviderHealthMonitor {
        val client = OkHttpClient.Builder().build()
        val monitor =
            ProviderHealthMonitor(
                config =
                    HealthMonitorConfig(
                        healthEndpoint = "http://localhost/health",
                        checkIntervalMs = 60000,
                    ),
                client = client,
                providerName = "Test",
            )
        monitor.recordFailure() // One failure = DEGRADED
        return monitor
    }

    private fun createUnhealthyHealthMonitor(): ProviderHealthMonitor {
        val client = OkHttpClient.Builder().build()
        val monitor =
            ProviderHealthMonitor(
                config =
                    HealthMonitorConfig(
                        healthEndpoint = "http://localhost/health",
                        checkIntervalMs = 60000,
                        unhealthyThreshold = 1,
                    ),
                client = client,
                providerName = "Test",
            )
        monitor.recordFailure() // With threshold 1, this makes it UNHEALTHY
        return monitor
    }
}
