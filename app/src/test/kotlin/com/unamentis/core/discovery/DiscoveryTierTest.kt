package com.unamentis.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DiscoveryTier and DiscoveryException.
 *
 * Tests cover:
 * - Tier enumeration and ordering
 * - Tier timeout configuration
 * - Exception messages
 */
class DiscoveryTierTest {
    // MARK: - DiscoveryTier Tests

    @Test
    fun `DiscoveryTier has all expected values`() {
        val tiers = DiscoveryTier.entries
        assertEquals(3, tiers.size)
        assertTrue(tiers.contains(DiscoveryTier.CACHED))
        assertTrue(tiers.contains(DiscoveryTier.NSD))
        assertTrue(tiers.contains(DiscoveryTier.SUBNET_SCAN))
    }

    @Test
    fun `tiers are ordered by priority`() {
        val tiers = DiscoveryTier.entries.sortedBy { it.priority }
        assertEquals(DiscoveryTier.CACHED, tiers[0])
        assertEquals(DiscoveryTier.NSD, tiers[1])
        assertEquals(DiscoveryTier.SUBNET_SCAN, tiers[2])
    }

    @Test
    fun `CACHED tier has shortest timeout`() {
        assertEquals(2_000L, DiscoveryTier.CACHED.timeoutMs)
    }

    @Test
    fun `NSD tier has medium timeout`() {
        assertEquals(3_000L, DiscoveryTier.NSD.timeoutMs)
    }

    @Test
    fun `SUBNET_SCAN tier has longest timeout`() {
        assertEquals(10_000L, DiscoveryTier.SUBNET_SCAN.timeoutMs)
    }

    @Test
    fun `CACHED has highest priority`() {
        assertEquals(1, DiscoveryTier.CACHED.priority)
    }

    @Test
    fun `NSD has middle priority`() {
        assertEquals(2, DiscoveryTier.NSD.priority)
    }

    @Test
    fun `SUBNET_SCAN has lowest priority`() {
        assertEquals(3, DiscoveryTier.SUBNET_SCAN.priority)
    }

    @Test
    fun `each tier timeout is longer or equal to previous tier`() {
        val tiers = DiscoveryTier.entries.sortedBy { it.priority }
        for (i in 1 until tiers.size) {
            assertTrue(
                "Tier ${tiers[i]} timeout should be >= ${tiers[i - 1]} timeout",
                tiers[i].timeoutMs >= tiers[i - 1].timeoutMs,
            )
        }
    }

    // MARK: - DiscoveryException Tests

    @Test
    fun `Cancelled exception has correct message`() {
        val exception = DiscoveryException.Cancelled()
        assertEquals("Discovery was cancelled", exception.message)
    }

    @Test
    fun `NetworkUnavailable exception has correct message`() {
        val exception = DiscoveryException.NetworkUnavailable()
        assertEquals("Network is unavailable", exception.message)
    }

    @Test
    fun `Timeout exception has correct message`() {
        val exception = DiscoveryException.Timeout()
        assertEquals("Discovery timed out", exception.message)
    }

    @Test
    fun `HealthCheckFailed exception includes reason`() {
        val exception = DiscoveryException.HealthCheckFailed("Server returned 503")
        assertEquals("Health check failed: Server returned 503", exception.message)
    }
}
