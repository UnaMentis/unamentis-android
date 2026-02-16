package com.unamentis.core.discovery

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SubnetScanDiscovery.
 *
 * Tests cover:
 * - Probing a healthy server
 * - Probing an unhealthy server
 * - Probing an unreachable server
 * - Server name extraction from health response
 * - Cancel behavior
 *
 * Uses MockWebServer for simulating server responses.
 */
class SubnetScanDiscoveryTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var subnetDiscovery: SubnetScanDiscovery

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = OkHttpClient.Builder().build()
        subnetDiscovery = SubnetScanDiscovery(client, listOf(mockWebServer.port))
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `probeHost returns server for healthy endpoint`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status": "ok", "server_name": "My Mac"}"""),
        )

        val result = subnetDiscovery.probeHost(mockWebServer.hostName, mockWebServer.port)

        assertNotNull(result)
        assertEquals("My Mac", result?.name)
        assertEquals(mockWebServer.hostName, result?.host)
        assertEquals(mockWebServer.port, result?.port)
        assertEquals(DiscoveryMethod.SUBNET_SCAN, result?.discoveryMethod)
    }

    @Test
    fun `probeHost returns null for unhealthy endpoint`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(503))

        val result = subnetDiscovery.probeHost(mockWebServer.hostName, mockWebServer.port)

        assertNull(result)
    }

    @Test
    fun `probeHost returns default name when body has no name field`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status": "ok"}"""),
        )

        val result = subnetDiscovery.probeHost(mockWebServer.hostName, mockWebServer.port)

        assertNotNull(result)
        assertEquals("UnaMentis Server", result?.name)
    }

    @Test
    fun `probeHost extracts name from name field`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"name": "Living Room Server"}"""),
        )

        val result = subnetDiscovery.probeHost(mockWebServer.hostName, mockWebServer.port)

        assertNotNull(result)
        assertEquals("Living Room Server", result?.name)
    }

    @Test
    fun `probeHost returns null for unreachable host`() {
        val result = subnetDiscovery.probeHost("10.255.255.1", 99999)
        assertNull(result)
    }

    @Test
    fun `probeHost returns default name for non-JSON response`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK"),
        )

        val result = subnetDiscovery.probeHost(mockWebServer.hostName, mockWebServer.port)

        assertNotNull(result)
        assertEquals("UnaMentis Server", result?.name)
    }

    @Test
    fun `cancel stops discovery`() =
        runTest {
            subnetDiscovery.cancel()

            // After cancellation, discover should throw or return null
            try {
                val result = subnetDiscovery.discover(1_000)
                // If it returns null, that's acceptable (cancellation during priority host check)
                assertNull(result)
            } catch (e: DiscoveryException.Cancelled) {
                // Also acceptable
            }
        }

    @Test
    fun `tier is SUBNET_SCAN`() {
        assertEquals(DiscoveryTier.SUBNET_SCAN, subnetDiscovery.tier)
    }

    @Test
    fun `default ports match iOS implementation`() {
        val defaultPorts = SubnetScanDiscovery.DEFAULT_PORTS
        assertEquals(3, defaultPorts.size)
        assertEquals(11400, defaultPorts[0])
        assertEquals(8766, defaultPorts[1])
        assertEquals(11434, defaultPorts[2])
    }
}
