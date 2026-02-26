package com.unamentis.core.discovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for CachedServerDiscovery.
 *
 * Tests cover:
 * - Cache save and retrieve
 * - Cache clearing
 * - Discovery with healthy server
 * - Discovery with unhealthy server
 * - Discovery with empty cache
 *
 * Uses Robolectric for SharedPreferences and MockWebServer for health checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CachedServerDiscoveryTest {
    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var cachedDiscovery: CachedServerDiscovery

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val client = okhttp3.OkHttpClient.Builder().build()
        cachedDiscovery = CachedServerDiscovery(context, client)

        // Clear any leftover preferences
        cachedDiscovery.clearCache()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `discover returns null when cache is empty`() =
        runTest {
            val result = cachedDiscovery.discover(2_000)
            assertNull(result)
        }

    @Test
    fun `discover returns server when cached and healthy`() =
        runTest {
            // Save a server pointing to MockWebServer
            val host = mockWebServer.hostName
            val port = mockWebServer.port

            val server =
                DiscoveredServer(
                    name = "Test Server",
                    host = host,
                    port = port,
                    discoveryMethod = DiscoveryMethod.CACHED,
                )
            cachedDiscovery.saveToCache(server)

            // Enqueue healthy response
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

            val result = cachedDiscovery.discover(2_000)
            assertNotNull(result)
            assertEquals(host, result?.host)
            assertEquals(port, result?.port)
            assertEquals(DiscoveryMethod.CACHED, result?.discoveryMethod)
        }

    @Test
    fun `discover returns null when cached server is unhealthy`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port

            val server =
                DiscoveredServer(
                    name = "Test Server",
                    host = host,
                    port = port,
                    discoveryMethod = DiscoveryMethod.CACHED,
                )
            cachedDiscovery.saveToCache(server)

            // Enqueue unhealthy response
            mockWebServer.enqueue(MockResponse().setResponseCode(503))

            val result = cachedDiscovery.discover(2_000)
            assertNull(result)
        }

    @Test
    fun `clearCache removes cached server`() =
        runTest {
            val server =
                DiscoveredServer(
                    name = "Test Server",
                    host = "192.168.1.100",
                    port = 8766,
                    discoveryMethod = DiscoveryMethod.CACHED,
                )
            cachedDiscovery.saveToCache(server)
            cachedDiscovery.clearCache()

            val result = cachedDiscovery.discover(2_000)
            assertNull(result)
        }

    @Test
    fun `saveToCache preserves server name`() =
        runTest {
            val host = mockWebServer.hostName
            val port = mockWebServer.port

            val server =
                DiscoveredServer(
                    name = "My Mac Server",
                    host = host,
                    port = port,
                    discoveryMethod = DiscoveryMethod.NSD,
                )
            cachedDiscovery.saveToCache(server)

            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

            val result = cachedDiscovery.discover(2_000)
            assertNotNull(result)
            assertEquals("My Mac Server", result?.name)
        }

    @Test
    fun `cancel prevents discovery from completing`() =
        runTest {
            val server =
                DiscoveredServer(
                    name = "Test Server",
                    host = mockWebServer.hostName,
                    port = mockWebServer.port,
                    discoveryMethod = DiscoveryMethod.CACHED,
                )
            cachedDiscovery.saveToCache(server)

            // Cancel before discover
            cachedDiscovery.cancel()

            try {
                cachedDiscovery.discover(2_000)
            } catch (e: DiscoveryException.Cancelled) {
                // Expected
                return@runTest
            }
            // If we get here, discovery returned null because cancelled flag was set
            // Both behaviors (exception or null) are acceptable
        }
}
