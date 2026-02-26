package com.unamentis.core.discovery

import androidx.annotation.StringRes
import com.unamentis.R
import java.util.UUID

/**
 * A server discovered through automatic discovery.
 *
 * Represents a UnaMentis server found via mDNS/NSD, cached lookup,
 * subnet scanning, manual configuration, or QR code scanning.
 *
 * @property id Unique identifier for this discovered server
 * @property name Display name for the server
 * @property host Server hostname or IP address
 * @property port Server port number
 * @property discoveryMethod How this server was discovered
 * @property timestampMs Unix timestamp when the server was discovered (milliseconds)
 * @property metadata Additional key-value metadata from discovery
 */
data class DiscoveredServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val discoveryMethod: DiscoveryMethod,
    val timestampMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
) {
    /**
     * Base URL for this server (http only for local servers).
     */
    val baseUrl: String
        get() = "http://$host:$port"

    /**
     * URL for the health check endpoint.
     */
    val healthUrl: String
        get() = "$baseUrl/health"
}

/**
 * How a server was discovered.
 *
 * Each method has a localized display name accessible via [displayNameResId].
 */
enum class DiscoveryMethod(
    @StringRes val displayNameResId: Int,
) {
    /** Server found in local cache from a previous connection */
    CACHED(R.string.discovery_method_cached),

    /** Server found via mDNS/NSD network service discovery */
    NSD(R.string.discovery_method_nsd),

    /** Server found via subnet scanning */
    SUBNET_SCAN(R.string.discovery_method_subnet_scan),

    /** Server configured manually by user */
    MANUAL(R.string.discovery_method_manual),

    /** Server configured from QR code scan */
    QR_CODE(R.string.discovery_method_qr_code),
}

/**
 * Current state of the discovery process.
 *
 * Tracks progression through multi-tier discovery. UI can observe
 * this state to display appropriate feedback.
 */
sealed class DiscoveryState {
    /** Discovery has not started or has been reset */
    data object Idle : DiscoveryState()

    /** Discovery is actively in progress */
    data object Discovering : DiscoveryState()

    /** Currently attempting a specific discovery tier */
    data class TryingTier(val tier: DiscoveryTier) : DiscoveryState()

    /** Successfully connected to a server */
    data class Connected(val server: DiscoveredServer) : DiscoveryState()

    /** All automatic tiers exhausted; manual configuration required */
    data object ManualConfigRequired : DiscoveryState()

    /** Discovery failed with an error */
    data class Failed(val message: String) : DiscoveryState()

    /**
     * Whether discovery is currently in progress.
     */
    val isDiscovering: Boolean
        get() = this is Discovering || this is TryingTier

    /**
     * Whether a server is connected.
     */
    val isConnected: Boolean
        get() = this is Connected
}
