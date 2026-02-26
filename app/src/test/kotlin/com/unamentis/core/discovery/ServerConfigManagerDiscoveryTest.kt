package com.unamentis.core.discovery

import com.unamentis.core.config.ServerConfigManager
import com.unamentis.core.config.ServerConfigManagerDiscovery
import com.unamentis.core.config.ServerType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ServerConfigManagerDiscovery.
 *
 * Tests the integration bridge between DeviceDiscoveryManager and
 * ServerConfigManager.
 *
 * Tests cover:
 * - Auto-discovery creates correct ServerConfig
 * - Manual configuration creates correct ServerConfig
 * - State delegation to DeviceDiscoveryManager
 * - Cache management delegation
 */
class ServerConfigManagerDiscoveryTest {
    private lateinit var mockServerConfigManager: ServerConfigManager
    private lateinit var mockDiscoveryManager: DeviceDiscoveryManager
    private lateinit var bridge: ServerConfigManagerDiscovery

    @Before
    fun setup() {
        mockServerConfigManager = mockk(relaxed = true)
        mockDiscoveryManager = mockk(relaxed = true)
        bridge = ServerConfigManagerDiscovery(mockServerConfigManager, mockDiscoveryManager)
    }

    @Test
    fun `connectWithAutoDiscovery returns ServerConfig on success`() =
        runTest {
            val server = createMockServer()
            coEvery { mockDiscoveryManager.startDiscovery() } returns server

            val config = bridge.connectWithAutoDiscovery()

            assertNotNull(config)
            assertEquals("Test Server", config?.name)
            assertEquals("192.168.1.100", config?.host)
            assertEquals(8766, config?.port)
            assertEquals(ServerType.UNAMENTIS_GATEWAY, config?.type)
        }

    @Test
    fun `connectWithAutoDiscovery returns null when discovery fails`() =
        runTest {
            coEvery { mockDiscoveryManager.startDiscovery() } returns null

            val config = bridge.connectWithAutoDiscovery()

            assertNull(config)
        }

    @Test
    fun `connectWithAutoDiscovery adds server to config manager`() =
        runTest {
            val server = createMockServer()
            coEvery { mockDiscoveryManager.startDiscovery() } returns server

            bridge.connectWithAutoDiscovery()

            verify { mockServerConfigManager.addServer(any()) }
        }

    @Test
    fun `retryAutoDiscovery delegates to discovery manager`() =
        runTest {
            coEvery { mockDiscoveryManager.retryDiscovery() } returns null

            val config = bridge.retryAutoDiscovery()

            assertNull(config)
        }

    @Test
    fun `configureServerManually returns ServerConfig on success`() =
        runTest {
            val server = createMockServer()
            coEvery {
                mockDiscoveryManager.configureManually(any(), any(), any())
            } returns server

            val config = bridge.configureServerManually("192.168.1.100", 8766, "My Server")

            assertNotNull(config)
            assertEquals(ServerType.UNAMENTIS_GATEWAY, config?.type)
        }

    @Test
    fun `configureServerManually returns null on failure`() =
        runTest {
            coEvery {
                mockDiscoveryManager.configureManually(any(), any(), any())
            } returns null

            val config = bridge.configureServerManually("192.168.1.100", 8766)

            assertNull(config)
        }

    @Test
    fun `cancelDiscovery delegates to discovery manager`() {
        bridge.cancelDiscovery()
        verify { mockDiscoveryManager.cancelDiscovery() }
    }

    @Test
    fun `clearDiscoveryCache delegates to discovery manager`() {
        bridge.clearDiscoveryCache()
        verify { mockDiscoveryManager.clearCache() }
    }

    @Test
    fun `startDiscoveryInBackground delegates to discovery manager`() {
        bridge.startDiscoveryInBackground()
        verify { mockDiscoveryManager.startDiscoveryInBackground() }
    }

    // MARK: - Helpers

    private fun createMockServer(): DiscoveredServer =
        DiscoveredServer(
            name = "Test Server",
            host = "192.168.1.100",
            port = 8766,
            discoveryMethod = DiscoveryMethod.CACHED,
        )
}
