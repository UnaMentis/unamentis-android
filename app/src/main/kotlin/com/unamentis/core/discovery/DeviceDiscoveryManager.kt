package com.unamentis.core.discovery

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Orchestrates multi-tier server discovery with fallback hierarchy.
 *
 * Tries each discovery tier in order of priority:
 * 1. **Cached** - Check previously connected server
 * 2. **NSD** - mDNS/Bonjour network service discovery
 * 3. **SubnetScan** - Parallel subnet scanning
 *
 * Each tier is tried in sequence. The first tier to return a healthy server
 * wins, and the result is saved to cache for future fast reconnection.
 *
 * Exposes discovery state, progress, and results via [StateFlow] properties
 * for reactive UI binding.
 *
 * @property cachedDiscovery Tier 1: cached server lookup
 * @property nsdDiscovery Tier 2: NSD/mDNS discovery
 * @property subnetDiscovery Tier 3: subnet scanning
 * @property client OkHttpClient for server health validation
 */
class DeviceDiscoveryManager(
    private val cachedDiscovery: CachedServerDiscovery,
    private val nsdDiscovery: NsdDiscovery,
    private val subnetDiscovery: SubnetScanDiscovery,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null

    private val healthCheckClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)

    /** Current state of the discovery process. */
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())

    /** All servers discovered during the current discovery session. */
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private val _connectedServer = MutableStateFlow<DiscoveredServer?>(null)

    /** The currently connected server, if any. */
    val connectedServer: StateFlow<DiscoveredServer?> = _connectedServer.asStateFlow()

    private val _currentTier = MutableStateFlow<DiscoveryTier?>(null)

    /** The discovery tier currently being attempted. */
    val currentTier: StateFlow<DiscoveryTier?> = _currentTier.asStateFlow()

    private val _progress = MutableStateFlow(0.0f)

    /** Discovery progress from 0.0 to 1.0. */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val tiers: List<DiscoveryTierStrategy>
        get() = listOf(cachedDiscovery, nsdDiscovery, subnetDiscovery)

    /**
     * Start automatic server discovery through all tiers.
     *
     * Iterates through tiers in priority order. The first tier to return
     * a healthy server is used. The result is cached for future reconnection.
     *
     * @return The first successfully discovered and validated server, or null
     */
    suspend fun startDiscovery(): DiscoveredServer? {
        cancelDiscovery()

        _state.value = DiscoveryState.Discovering
        _progress.value = 0.0f
        _discoveredServers.value = emptyList()
        _connectedServer.value = null

        Log.i(TAG, "Starting multi-tier discovery")

        val totalTiers = tiers.size.toFloat()

        for ((index, tier) in tiers.withIndex()) {
            _currentTier.value = tier.tier
            _progress.value = index.toFloat() / totalTiers
            _state.value = DiscoveryState.TryingTier(tier.tier)

            Log.i(TAG, "Trying tier ${index + 1}/${totalTiers.toInt()}: ${tier.tier.name}")

            try {
                val server = tier.discover(tier.tier.timeoutMs)
                if (server != null) {
                    // Validate with health check
                    if (validateServer(server)) {
                        _connectedServer.value = server
                        _state.value = DiscoveryState.Connected(server)
                        _progress.value = 1.0f

                        // Save to cache for next time
                        cachedDiscovery.saveToCache(server)

                        Log.i(
                            TAG,
                            "Connected via ${tier.tier.name}: ${server.host}:${server.port}",
                        )
                        return server
                    } else {
                        Log.d(TAG, "Server found but health check failed")
                    }
                }
            } catch (e: DiscoveryException.Cancelled) {
                Log.i(TAG, "Discovery cancelled")
                _state.value = DiscoveryState.Idle
                return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "${tier.tier.name} failed: ${e.message}")
            }
        }

        // All tiers exhausted
        _progress.value = 1.0f
        _state.value = DiscoveryState.ManualConfigRequired
        Log.i(TAG, "All discovery tiers exhausted, manual configuration required")
        return null
    }

    /**
     * Start discovery in the background.
     *
     * Launches discovery in a coroutine scope. Observe [state] and
     * [connectedServer] for results.
     */
    fun startDiscoveryInBackground() {
        discoveryJob =
            scope.launch {
                startDiscovery()
            }
    }

    /**
     * Cancel any ongoing discovery.
     */
    fun cancelDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        for (tier in tiers) {
            tier.cancel()
        }

        if (_state.value.isDiscovering) {
            _state.value = DiscoveryState.Idle
        }
    }

    /**
     * Retry discovery from the beginning.
     *
     * Cancels any ongoing discovery and starts fresh.
     *
     * @return The discovered server, or null if all tiers fail
     */
    suspend fun retryDiscovery(): DiscoveredServer? {
        cancelDiscovery()
        return startDiscovery()
    }

    /**
     * Manually configure a server.
     *
     * Validates the server with a health check before accepting it.
     *
     * @param host Server hostname or IP
     * @param port Server port
     * @param name Optional display name
     * @return The configured server if validation succeeds, null otherwise
     */
    suspend fun configureManually(
        host: String,
        port: Int,
        name: String? = null,
    ): DiscoveredServer? {
        val server =
            DiscoveredServer(
                name = name ?: DEFAULT_SERVER_NAME,
                host = host,
                port = port,
                discoveryMethod = DiscoveryMethod.MANUAL,
            )

        return if (validateServer(server)) {
            _connectedServer.value = server
            _state.value = DiscoveryState.Connected(server)
            cachedDiscovery.saveToCache(server)
            Log.i(TAG, "Manual configuration successful: $host:$port")
            server
        } else {
            _state.value = DiscoveryState.Failed("Could not connect to $host:$port")
            null
        }
    }

    /**
     * Clear the cached server data.
     */
    fun clearCache() {
        cachedDiscovery.clearCache()
        _connectedServer.value = null
        _state.value = DiscoveryState.Idle
    }

    /**
     * Validate that a server is reachable and healthy.
     *
     * @param server The server to validate
     * @return true if the server responds with HTTP 200 at /health
     */
    internal suspend fun validateServer(server: DiscoveredServer): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request.Builder()
                        .url(server.healthUrl)
                        .get()
                        .build()

                val response = healthCheckClient.newCall(request).execute()
                val isHealthy = response.code == HTTP_OK
                response.close()

                if (isHealthy) {
                    // Add to discovered servers list
                    val current = _discoveredServers.value
                    if (current.none { it.host == server.host && it.port == server.port }) {
                        _discoveredServers.value = current + server
                    }
                }

                isHealthy
            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "Health check failed for ${server.host}:${server.port}: ${e.message}",
                )
                false
            }
        }

    companion object {
        private const val TAG = "DeviceDiscoveryManager"
        private const val DEFAULT_SERVER_NAME = "UnaMentis Server"
        private const val HEALTH_CHECK_TIMEOUT_MS = 3_000L
        private const val HTTP_OK = 200
    }
}
