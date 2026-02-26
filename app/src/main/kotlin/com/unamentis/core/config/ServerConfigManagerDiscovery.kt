package com.unamentis.core.config

import android.util.Log
import com.unamentis.core.discovery.DeviceDiscoveryManager
import com.unamentis.core.discovery.DiscoveredServer
import com.unamentis.core.discovery.DiscoveryState
import com.unamentis.core.discovery.DiscoveryTier
import kotlinx.coroutines.flow.StateFlow

/**
 * Integrates [DeviceDiscoveryManager] with [ServerConfigManager].
 *
 * Bridges automatic multi-tier server discovery with the existing
 * server configuration system. Discovered servers are converted to
 * [ServerConfig] instances and added to the managed server list.
 *
 * This matches the iOS `ServerConfigManager+Discovery.swift` extension.
 *
 * @property serverConfigManager The existing server config manager
 * @property discoveryManager The multi-tier discovery orchestrator
 */
class ServerConfigManagerDiscovery(
    private val serverConfigManager: ServerConfigManager,
    private val discoveryManager: DeviceDiscoveryManager,
) {
    /**
     * Connect using automatic discovery, falling back through all tiers.
     *
     * This is the recommended way to establish initial server connection.
     * Discovers a server, converts it to a [ServerConfig], and adds it
     * to the managed server list.
     *
     * @return The ServerConfig for the discovered server, or null if all tiers fail
     */
    suspend fun connectWithAutoDiscovery(): ServerConfig? {
        val discovered = discoveryManager.startDiscovery() ?: return null

        val config = makeServerConfig(discovered)
        serverConfigManager.addServer(config)

        Log.i(TAG, "Auto-discovery connected: ${config.name} at ${config.host}:${config.port}")
        return config
    }

    /**
     * Retry automatic discovery from the beginning.
     *
     * @return The ServerConfig for the discovered server, or null if all tiers fail
     */
    suspend fun retryAutoDiscovery(): ServerConfig? {
        val discovered = discoveryManager.retryDiscovery() ?: return null

        val config = makeServerConfig(discovered)
        serverConfigManager.addServer(config)
        return config
    }

    /**
     * Configure a server manually (bypassing automatic discovery).
     *
     * @param host Server hostname or IP address
     * @param port Server port
     * @param name Optional display name
     * @return The ServerConfig if validation succeeds, null otherwise
     */
    suspend fun configureServerManually(
        host: String,
        port: Int,
        name: String? = null,
    ): ServerConfig? {
        val discovered = discoveryManager.configureManually(host, port, name) ?: return null

        val config = makeServerConfig(discovered)
        serverConfigManager.addServer(config)
        return config
    }

    /**
     * Whether automatic discovery has found a server.
     */
    val hasAutoDiscoveredServer: Boolean
        get() = discoveryManager.connectedServer.value != null

    /**
     * Get the auto-discovered server as a [ServerConfig].
     */
    val autoDiscoveredServerConfig: ServerConfig?
        get() {
            val discovered = discoveryManager.connectedServer.value ?: return null
            return makeServerConfig(discovered)
        }

    /**
     * Current discovery state, observable by UI.
     */
    val discoveryState: StateFlow<DiscoveryState>
        get() = discoveryManager.state

    /**
     * Whether discovery is currently in progress.
     */
    val isDiscoveryInProgress: Boolean
        get() = discoveryManager.state.value.isDiscovering

    /**
     * Current discovery tier being attempted.
     */
    val currentDiscoveryTier: DiscoveryTier?
        get() = discoveryManager.currentTier.value

    /**
     * Discovery progress from 0.0 to 1.0.
     */
    val discoveryProgress: Float
        get() = discoveryManager.progress.value

    /**
     * Clear the discovery cache.
     */
    fun clearDiscoveryCache() {
        discoveryManager.clearCache()
    }

    /**
     * Cancel any ongoing discovery.
     */
    fun cancelDiscovery() {
        discoveryManager.cancelDiscovery()
    }

    /**
     * Start discovery in the background.
     *
     * Observe [discoveryState] and [DeviceDiscoveryManager.connectedServer]
     * for results.
     */
    fun startDiscoveryInBackground() {
        discoveryManager.startDiscoveryInBackground()
    }

    /**
     * Create a [ServerConfig] from a [DiscoveredServer].
     */
    private fun makeServerConfig(discovered: DiscoveredServer): ServerConfig =
        ServerConfig(
            name = discovered.name,
            host = discovered.host,
            port = discovered.port,
            type = ServerType.UNAMENTIS_GATEWAY,
            healthStatus = ServerHealthStatus.HEALTHY,
            lastHealthCheck = System.currentTimeMillis(),
        )

    companion object {
        private const val TAG = "ServerConfigDiscovery"
    }
}
