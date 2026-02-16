package com.unamentis.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DiscoveredServer, DiscoveryMethod, and DiscoveryState.
 *
 * Tests cover:
 * - Server URL construction
 * - Discovery method enumeration
 * - Discovery state transitions and queries
 */
class DiscoveredServerTest {
    // MARK: - DiscoveredServer Tests

    @Test
    fun `baseUrl is constructed correctly`() {
        val server = createServer(host = "192.168.1.100", port = 8766)
        assertEquals("http://192.168.1.100:8766", server.baseUrl)
    }

    @Test
    fun `healthUrl is constructed correctly`() {
        val server = createServer(host = "192.168.1.100", port = 8766)
        assertEquals("http://192.168.1.100:8766/health", server.healthUrl)
    }

    @Test
    fun `baseUrl with localhost`() {
        val server = createServer(host = "127.0.0.1", port = 11434)
        assertEquals("http://127.0.0.1:11434", server.baseUrl)
    }

    @Test
    fun `two servers with different IDs are not equal`() {
        val server1 = createServer()
        val server2 = createServer()
        assertNotEquals(server1.id, server2.id)
    }

    @Test
    fun `server with metadata preserves metadata`() {
        val metadata = mapOf("version" to "1.0", "platform" to "linux")
        val server = createServer(metadata = metadata)
        assertEquals("1.0", server.metadata["version"])
        assertEquals("linux", server.metadata["platform"])
    }

    // MARK: - DiscoveryMethod Tests

    @Test
    fun `DiscoveryMethod has all expected values`() {
        val methods = DiscoveryMethod.entries
        assertEquals(5, methods.size)
        assertTrue(methods.contains(DiscoveryMethod.CACHED))
        assertTrue(methods.contains(DiscoveryMethod.NSD))
        assertTrue(methods.contains(DiscoveryMethod.SUBNET_SCAN))
        assertTrue(methods.contains(DiscoveryMethod.MANUAL))
        assertTrue(methods.contains(DiscoveryMethod.QR_CODE))
    }

    // MARK: - DiscoveryState Tests

    @Test
    fun `Idle state is not discovering`() {
        val state: DiscoveryState = DiscoveryState.Idle
        assertFalse(state.isDiscovering)
        assertFalse(state.isConnected)
    }

    @Test
    fun `Discovering state is discovering`() {
        val state: DiscoveryState = DiscoveryState.Discovering
        assertTrue(state.isDiscovering)
        assertFalse(state.isConnected)
    }

    @Test
    fun `TryingTier state is discovering`() {
        val state: DiscoveryState = DiscoveryState.TryingTier(DiscoveryTier.NSD)
        assertTrue(state.isDiscovering)
        assertFalse(state.isConnected)
    }

    @Test
    fun `Connected state is connected`() {
        val server = createServer()
        val state: DiscoveryState = DiscoveryState.Connected(server)
        assertFalse(state.isDiscovering)
        assertTrue(state.isConnected)
    }

    @Test
    fun `ManualConfigRequired state is not discovering or connected`() {
        val state: DiscoveryState = DiscoveryState.ManualConfigRequired
        assertFalse(state.isDiscovering)
        assertFalse(state.isConnected)
    }

    @Test
    fun `Failed state is not discovering or connected`() {
        val state: DiscoveryState = DiscoveryState.Failed("Connection refused")
        assertFalse(state.isDiscovering)
        assertFalse(state.isConnected)
    }

    @Test
    fun `Failed state preserves error message`() {
        val state = DiscoveryState.Failed("Connection refused")
        assertEquals("Connection refused", state.message)
    }

    // MARK: - Helpers

    private fun createServer(
        host: String = "192.168.1.100",
        port: Int = 8766,
        name: String = "Test Server",
        method: DiscoveryMethod = DiscoveryMethod.CACHED,
        metadata: Map<String, String> = emptyMap(),
    ): DiscoveredServer =
        DiscoveredServer(
            name = name,
            host = host,
            port = port,
            discoveryMethod = method,
            metadata = metadata,
        )
}
