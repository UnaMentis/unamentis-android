package com.unamentis.core.discovery

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tier 3: Scans the local subnet for UnaMentis servers.
 *
 * Performs parallel HTTP health checks across all addresses on the local
 * /24 subnet (e.g., 192.168.1.1-254) on configured ports. Priority hosts
 * (localhost, emulator host, local IP) are checked first for fast results.
 *
 * Port list matches the iOS implementation: 11400 (gateway), 8766
 * (management API), 11434 (Ollama).
 *
 * @property client OkHttpClient for health check requests
 * @property ports Ports to scan on each host
 */
class SubnetScanDiscovery(
    private val client: OkHttpClient,
    private val ports: List<Int> = DEFAULT_PORTS,
) : DiscoveryTierStrategy {
    override val tier: DiscoveryTier = DiscoveryTier.SUBNET_SCAN

    private val isCancelled = AtomicBoolean(false)

    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    override suspend fun discover(timeoutMs: Long): DiscoveredServer? {
        isCancelled.set(false)

        return withTimeoutOrNull(timeoutMs) {
            performSubnetScan()
        }
    }

    override fun cancel() {
        isCancelled.set(true)
    }

    /**
     * Performs the full subnet scan: priority hosts first, then parallel scan.
     */
    private suspend fun performSubnetScan(): DiscoveredServer? =
        withContext(Dispatchers.IO) {
            val localIp = getLocalIPAddress()
            Log.i(TAG, "Starting subnet scan from ${localIp ?: "unknown"}")

            // Priority hosts: emulator host, localhost, and local IP
            val priorityHosts =
                buildList {
                    add(EMULATOR_HOST)
                    add(LOCALHOST)
                    if (localIp != null) add(localIp)
                }.distinct()

            // Try priority hosts first (quick wins)
            for (host in priorityHosts) {
                if (isCancelled.get()) throw DiscoveryException.Cancelled()

                for (port in ports) {
                    val server = probeHost(host, port)
                    if (server != null) {
                        Log.i(TAG, "Found server at priority host: $host:$port")
                        return@withContext server
                    }
                }
            }

            // Extract subnet for full scan
            if (localIp == null) {
                Log.w(TAG, "Could not determine local IP address for subnet scan")
                return@withContext null
            }

            val parts = localIp.split(".")
            if (parts.size != IPV4_PARTS) {
                Log.w(TAG, "Invalid IP format: $localIp")
                return@withContext null
            }

            val subnet = parts.take(IPV4_PARTS - 1).joinToString(".")
            val candidates =
                (1..SUBNET_MAX_HOST).map { "$subnet.$it" }
                    .filter { it !in priorityHosts }

            // Scan subnet in parallel
            scanSubnetParallel(candidates)
        }

    /**
     * Scan a list of hosts in parallel across all configured ports.
     *
     * @param hosts List of IP addresses to scan
     * @return The first healthy server found, or null
     */
    private suspend fun scanSubnetParallel(hosts: List<String>): DiscoveredServer? =
        coroutineScope {
            // Launch all probes in parallel, chunked to avoid excessive concurrency
            val results =
                hosts.flatMap { host ->
                    ports.map { port -> host to port }
                }.chunked(PARALLEL_CHUNK_SIZE).firstNotNullOfOrNull { chunk ->
                    if (isCancelled.get()) return@coroutineScope null

                    val deferred =
                        chunk.map { (host, port) ->
                            async(Dispatchers.IO) {
                                if (isCancelled.get()) null else probeHost(host, port)
                            }
                        }

                    deferred.awaitAll().firstOrNull { it != null }
                }

            if (results != null) {
                Log.i(TAG, "Found server via subnet scan: ${results.host}:${results.port}")
            }
            results
        }

    /**
     * Probe a specific host:port for a UnaMentis server.
     *
     * Makes an HTTP GET to /health and returns a [DiscoveredServer] if
     * the server responds with HTTP 200.
     *
     * @param host The hostname or IP to probe
     * @param port The port to probe
     * @return A [DiscoveredServer] if healthy, null otherwise
     */
    internal fun probeHost(
        host: String,
        port: Int,
    ): DiscoveredServer? =
        try {
            val healthUrl = "http://$host:$port/health"
            val request =
                Request.Builder()
                    .url(healthUrl)
                    .get()
                    .build()

            probeClient.newCall(request).execute().use { response ->
                if (response.code == HTTP_OK) {
                    val serverName = extractServerName(response.body?.string())

                    DiscoveredServer(
                        name = serverName,
                        host = host,
                        port = port,
                        discoveryMethod = DiscoveryMethod.SUBNET_SCAN,
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            // Host not responding - expected during scanning
            null
        }

    /**
     * Extract server name from health check response body.
     *
     * @param body The response body string (JSON expected)
     * @return Server name if found, default name otherwise
     */
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private fun extractServerName(body: String?): String {
        if (body == null) return DEFAULT_SERVER_NAME

        return try {
            val json = jsonParser.parseToJsonElement(body).jsonObject
            val serverName =
                json["server_name"]?.jsonPrimitive?.content
                    ?: json["name"]?.jsonPrimitive?.content
            if (serverName.isNullOrEmpty()) DEFAULT_SERVER_NAME else serverName
        } catch (_: Exception) {
            DEFAULT_SERVER_NAME
        }
    }

    /**
     * Get the local device's IPv4 address on the WiFi interface.
     *
     * @return The local IP address, or null if unavailable
     */
    internal fun getLocalIPAddress(): String? =
        try {
            findWifiIPv4Address()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local IP address", e)
            null
        }

    /**
     * Finds the first IPv4 address on a WiFi or Ethernet interface.
     */
    private fun findWifiIPv4Address(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
        val eligible =
            interfaces.filter { iface ->
                iface.isUp &&
                    !iface.isLoopback &&
                    iface.name in listOf(WIFI_INTERFACE, ETH_INTERFACE)
            }
        return eligible.firstNotNullOfOrNull { iface ->
            iface.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }
    }

    companion object {
        private const val TAG = "SubnetScanDiscovery"
        private const val DEFAULT_SERVER_NAME = "UnaMentis Server"
        private const val LOCALHOST = "127.0.0.1"
        private const val EMULATOR_HOST = "10.0.2.2"
        private const val PROBE_TIMEOUT_MS = 300L
        private const val PARALLEL_CHUNK_SIZE = 50
        private const val WIFI_INTERFACE = "wlan0"
        private const val ETH_INTERFACE = "eth0"
        private const val IPV4_PARTS = 4
        private const val SUBNET_MAX_HOST = 254
        private const val HTTP_OK = 200

        /**
         * Default ports to scan, matching the iOS implementation.
         * - 11400: UnaMentis Gateway (alt)
         * - 8766: Management API
         * - 11434: Ollama
         */
        val DEFAULT_PORTS = listOf(11400, 8766, 11434)
    }
}
