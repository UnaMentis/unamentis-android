package com.unamentis.core.discovery

import androidx.annotation.StringRes
import com.unamentis.R

/**
 * Discovery tiers in order of preference.
 *
 * The discovery system uses a multi-tier fallback hierarchy:
 * 1. **Cached** - Check previously connected server (fastest, 2s timeout)
 * 2. **NSD** - Android Network Service Discovery / mDNS (3s timeout)
 * 3. **SubnetScan** - Parallel scan of local subnet (10s timeout)
 *
 * Each tier is tried in order; the first successful result is used.
 *
 * @property priority Sort priority (lower values tried first)
 * @property timeoutMs Timeout for this tier in milliseconds
 * @property displayNameResId String resource for UI display
 */
enum class DiscoveryTier(
    val priority: Int,
    val timeoutMs: Long,
    @StringRes val displayNameResId: Int,
) {
    /** Tier 1: Check for previously connected server */
    CACHED(
        priority = 1,
        timeoutMs = 2_000L,
        displayNameResId = R.string.discovery_tier_cached,
    ),

    /** Tier 2: mDNS/NSD network service discovery */
    NSD(
        priority = 2,
        timeoutMs = 3_000L,
        displayNameResId = R.string.discovery_tier_nsd,
    ),

    /** Tier 3: Parallel subnet scanning */
    SUBNET_SCAN(
        priority = 3,
        timeoutMs = 10_000L,
        displayNameResId = R.string.discovery_tier_subnet_scan,
    ),
}

/**
 * Interface for implementing a discovery tier.
 *
 * Each tier represents a different strategy for finding UnaMentis servers
 * on the local network. Tiers are tried in priority order by the
 * [DeviceDiscoveryManager].
 */
interface DiscoveryTierStrategy {
    /**
     * The tier this implementation represents.
     */
    val tier: DiscoveryTier

    /**
     * Attempt to discover a server using this tier's strategy.
     *
     * @param timeoutMs Maximum time to wait for discovery in milliseconds
     * @return A discovered server, or null if none found within the timeout
     * @throws DiscoveryException if discovery is cancelled or encounters an error
     */
    suspend fun discover(timeoutMs: Long): DiscoveredServer?

    /**
     * Cancel any ongoing discovery for this tier.
     */
    fun cancel()
}

/**
 * Errors that can occur during server discovery.
 */
sealed class DiscoveryException(message: String) : Exception(message) {
    /** Discovery was cancelled by the user or system */
    class Cancelled : DiscoveryException("Discovery was cancelled")

    /** Network is unavailable */
    class NetworkUnavailable : DiscoveryException("Network is unavailable")

    /** Discovery timed out */
    class Timeout : DiscoveryException("Discovery timed out")

    /** Server health check failed */
    class HealthCheckFailed(reason: String) : DiscoveryException("Health check failed: $reason")
}
