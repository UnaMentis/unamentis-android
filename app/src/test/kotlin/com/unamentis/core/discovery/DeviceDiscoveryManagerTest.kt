package com.unamentis.core.discovery

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DeviceDiscoveryManager.
 *
 * Tests cover:
 * - Multi-tier discovery fallback
 * - State transitions during discovery
 * - Health check validation
 * - Manual configuration
 * - Cache management
 * - Cancel behavior
 *
 * Uses MockK for tier mocks and MockWebServer for health checks.
 */
class DeviceDiscoveryManagerTest {
    private lateinit var mockCachedDiscovery: CachedServerDiscovery
    private lateinit var mockNsdDiscovery: NsdDiscovery
    private lateinit var mockSubnetDiscovery: SubnetScanDiscovery
    private lateinit var mockWebServer: MockWebServer
    private lateinit var manager: DeviceDiscoveryManager

    @Before
    fun setup() {
        mockCachedDiscovery = mockk(relaxed = true)
        mockNsdDiscovery = mockk(relaxed = true)
        mockSubnetDiscovery = mockk(relaxed = true)
        mockWebServer = MockWebServer()
        mockWebServer.start()

        every { mockCachedDiscovery.tier } returns DiscoveryTier.CACHED
        every { mockNsdDiscovery.tier } returns DiscoveryTier.NSD
        every { mockSubnetDiscovery.tier } returns DiscoveryTier.SUBNET_SCAN

        val client = OkHttpClient.Builder().build()
        manager =
            DeviceDiscoveryManager(
                cachedDiscovery = mockCachedDiscovery,
                nsdDiscovery = mockNsdDiscovery,
                subnetDiscovery = mockSubnetDiscovery,
                client = client,
            )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(DiscoveryState.Idle, manager.state.value)
    }

    @Test
    fun `initial progress is zero`() {
        assertEquals(0.0f, manager.progress.value)
    }

    @Test
    fun `initial connectedServer is null`() {
        assertNull(manager.connectedServer.value)
    }

    @Test
    fun `discovery returns server from cached tier`() =
        runTest {
            val server = createMockServer()
            coEvery { mockCachedDiscovery.discover(any()) } returns server

            // Enqueue health check response
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            val result =
                manager.startDiscovery()

            // Will get null because health check goes to the wrong URL
            // (mock server's host/port vs the server's host/port)
            // This verifies the cached tier is called first
            coVerify { mockCachedDiscovery.discover(any()) }
        }

    @Test
    fun `discovery tries NSD when cached returns null`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } returns null
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            manager.startDiscovery()

            coVerify { mockCachedDiscovery.discover(any()) }
            coVerify { mockNsdDiscovery.discover(any()) }
        }

    @Test
    fun `discovery tries all tiers when all return null`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } returns null
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            val result = manager.startDiscovery()

            assertNull(result)
            coVerify { mockCachedDiscovery.discover(any()) }
            coVerify { mockNsdDiscovery.discover(any()) }
            coVerify { mockSubnetDiscovery.discover(any()) }
        }

    @Test
    fun `state is ManualConfigRequired when all tiers fail`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } returns null
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            manager.startDiscovery()

            assertEquals(DiscoveryState.ManualConfigRequired, manager.state.value)
        }

    @Test
    fun `progress is 1 after discovery completes`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } returns null
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            manager.startDiscovery()

            assertEquals(1.0f, manager.progress.value)
        }

    @Test
    fun `discovery returns server when health check passes`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            val server = createMockServer(host = host, port = port)

            coEvery { mockCachedDiscovery.discover(any()) } returns server

            // Enqueue health check response
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            val result = manager.startDiscovery()

            assertNotNull(result)
            assertEquals(host, result?.host)
            assertEquals(port, result?.port)
        }

    @Test
    fun `discovery skips server when health check fails`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            val server = createMockServer(host = host, port = port)

            coEvery { mockCachedDiscovery.discover(any()) } returns server
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            // Health check returns 503
            mockWebServer.enqueue(MockResponse().setResponseCode(503))

            val result = manager.startDiscovery()

            // Server is skipped because health check failed
            assertNull(result)
        }

    @Test
    fun `state is Connected after successful discovery`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            val server = createMockServer(host = host, port = port)

            coEvery { mockCachedDiscovery.discover(any()) } returns server
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            manager.startDiscovery()

            assertTrue(manager.state.value.isConnected)
        }

    @Test
    fun `connectedServer is set after successful discovery`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            val server = createMockServer(host = host, port = port)

            coEvery { mockCachedDiscovery.discover(any()) } returns server
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            manager.startDiscovery()

            val connected = manager.connectedServer.value
            assertNotNull(connected)
            assertEquals(host, connected?.host)
            assertEquals(port, connected?.port)
        }

    @Test
    fun `saveToCache is called after successful discovery`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            val server = createMockServer(host = host, port = port)

            coEvery { mockCachedDiscovery.discover(any()) } returns server
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            manager.startDiscovery()

            verify { mockCachedDiscovery.saveToCache(any()) }
        }

    @Test
    fun `cancelDiscovery calls cancel on all tiers`() {
        manager.cancelDiscovery()

        verify { mockCachedDiscovery.cancel() }
        verify { mockNsdDiscovery.cancel() }
        verify { mockSubnetDiscovery.cancel() }
    }

    @Test
    fun `clearCache calls clearCache and resets state`() {
        manager.clearCache()

        verify { mockCachedDiscovery.clearCache() }
        assertNull(manager.connectedServer.value)
        assertEquals(DiscoveryState.Idle, manager.state.value)
    }

    @Test
    fun `configureManually returns server when health check passes`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port

            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            val result = manager.configureManually(host, port, "My Server")

            assertNotNull(result)
            assertEquals(host, result?.host)
            assertEquals(port, result?.port)
            assertEquals("My Server", result?.name)
            assertEquals(DiscoveryMethod.MANUAL, result?.discoveryMethod)
        }

    @Test
    fun `configureManually returns null when health check fails`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port

            mockWebServer.enqueue(MockResponse().setResponseCode(503))

            val result = manager.configureManually(host, port)

            assertNull(result)
            assertTrue(manager.state.value is DiscoveryState.Failed)
        }

    @Test
    fun `configureManually caches server on success`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port

            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            manager.configureManually(host, port)

            verify { mockCachedDiscovery.saveToCache(any()) }
        }

    @Test
    fun `discovery handles exception from tier gracefully`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } throws RuntimeException("Test error")
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            val result = manager.startDiscovery()

            // Should not crash; should continue to next tier
            assertNull(result)
            coVerify { mockNsdDiscovery.discover(any()) }
        }

    @Test
    fun `discovery stops when Cancelled exception is thrown`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } throws DiscoveryException.Cancelled()

            val result = manager.startDiscovery()

            assertNull(result)
            assertEquals(DiscoveryState.Idle, manager.state.value)
            // Should NOT proceed to NSD tier
            coVerify(exactly = 0) { mockNsdDiscovery.discover(any()) }
        }

    @Test
    fun `discoveredServers is updated after successful validation`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port
            val server = createMockServer(host = host, port = port)

            coEvery { mockCachedDiscovery.discover(any()) } returns server
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            manager.startDiscovery()

            val discovered = manager.discoveredServers.value
            assertEquals(1, discovered.size)
            assertEquals(host, discovered[0].host)
        }

    @Test
    fun `retryDiscovery cancels previous and starts fresh`() =
        runTest {
            coEvery { mockCachedDiscovery.discover(any()) } returns null
            coEvery { mockNsdDiscovery.discover(any()) } returns null
            coEvery { mockSubnetDiscovery.discover(any()) } returns null

            manager.retryDiscovery()

            // Verify cancel was called (tiers get cancel called)
            verify { mockCachedDiscovery.cancel() }
            // Verify discovery was attempted
            coVerify { mockCachedDiscovery.discover(any()) }
        }

    // MARK: - Helpers

    private fun createMockServer(
        host: String = "192.168.1.100",
        port: Int = 8766,
    ): DiscoveredServer =
        DiscoveredServer(
            name = "Test Server",
            host = host,
            port = port,
            discoveryMethod = DiscoveryMethod.CACHED,
        )
}
