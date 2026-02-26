package com.unamentis.core.discovery

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tier 1: Attempts to reconnect to the last known good server.
 *
 * Checks SharedPreferences for a previously cached server configuration
 * and validates it with a health check. This is the fastest discovery tier
 * since it avoids network scanning entirely.
 *
 * @property context Application context for SharedPreferences access
 * @property client OkHttpClient for health check requests
 */
class CachedServerDiscovery(
    private val context: Context,
    private val client: OkHttpClient,
) : DiscoveryTierStrategy {
    override val tier: DiscoveryTier = DiscoveryTier.CACHED

    private val isCancelled = AtomicBoolean(false)

    private val healthCheckClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun discover(timeoutMs: Long): DiscoveredServer? {
        isCancelled.set(false)

        val host = prefs.getString(KEY_HOST, null)
        if (host.isNullOrEmpty()) {
            Log.d(TAG, "No cached server found")
            return null
        }

        val port = prefs.getInt(KEY_PORT, 0)
        if (port <= 0) {
            Log.d(TAG, "Invalid cached port")
            return null
        }

        val name = prefs.getString(KEY_NAME, DEFAULT_SERVER_NAME) ?: DEFAULT_SERVER_NAME
        val timestamp = prefs.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        Log.i(TAG, "Found cached server: $host:$port")

        if (isCancelled.get()) throw DiscoveryException.Cancelled()

        val server =
            DiscoveredServer(
                name = name,
                host = host,
                port = port,
                discoveryMethod = DiscoveryMethod.CACHED,
                timestampMs = timestamp,
            )

        return if (performHealthCheck(server)) {
            Log.i(TAG, "Cached server is healthy")
            server
        } else {
            Log.i(TAG, "Cached server health check failed")
            null
        }
    }

    override fun cancel() {
        isCancelled.set(true)
    }

    /**
     * Save a server to the cache for future reconnection.
     *
     * @param server The server to cache
     */
    fun saveToCache(server: DiscoveredServer) {
        prefs.edit()
            .putString(KEY_HOST, server.host)
            .putInt(KEY_PORT, server.port)
            .putString(KEY_NAME, server.name)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "Saved server to cache: ${server.host}:${server.port}")
    }

    /**
     * Clear the cached server data.
     */
    fun clearCache() {
        prefs.edit()
            .remove(KEY_HOST)
            .remove(KEY_PORT)
            .remove(KEY_NAME)
            .remove(KEY_TIMESTAMP)
            .apply()
        Log.i(TAG, "Cleared cached server")
    }

    /**
     * Perform a health check against the server.
     *
     * @param server The server to check
     * @return true if the server responds with HTTP 200
     */
    private fun performHealthCheck(server: DiscoveredServer): Boolean =
        try {
            val request =
                Request.Builder()
                    .url(server.healthUrl)
                    .get()
                    .build()

            val response = healthCheckClient.newCall(request).execute()
            val isHealthy = response.code == HTTP_OK
            response.close()
            isHealthy
        } catch (e: Exception) {
            Log.d(TAG, "Health check failed for ${server.host}:${server.port}: ${e.message}")
            false
        }

    companion object {
        private const val TAG = "CachedServerDiscovery"
        private const val PREFS_NAME = "unamentis_discovery_cache"
        private const val KEY_HOST = "discovery.cached.host"
        private const val KEY_PORT = "discovery.cached.port"
        private const val KEY_NAME = "discovery.cached.name"
        private const val KEY_TIMESTAMP = "discovery.cached.timestamp"
        private const val DEFAULT_SERVER_NAME = "Cached Server"
        private const val HEALTH_CHECK_TIMEOUT_MS = 3_000L
        private const val HTTP_OK = 200
    }
}
