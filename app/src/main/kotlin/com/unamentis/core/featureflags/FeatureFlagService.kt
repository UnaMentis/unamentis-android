package com.unamentis.core.featureflags

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature flag service using Unleash proxy with offline support.
 *
 * Provides feature flag evaluation with:
 * - Background polling for flag updates
 * - Offline caching support
 * - Metrics tracking
 * - Context-based evaluation
 *
 * ## Usage
 * ```kotlin
 * val service = FeatureFlagService(context, config)
 * service.start()
 *
 * if (service.isEnabled("feature_knowledge_bowl")) {
 *     // Feature is enabled
 * }
 * ```
 */
@Singleton
class FeatureFlagService
    @Inject
    constructor(
        private val context: Context,
        private val config: FeatureFlagConfig = FeatureFlagConfig.DEVELOPMENT,
    ) : FeatureFlagEvaluating {
        companion object {
            private const val TAG = "FeatureFlagService"
            private const val CONNECTION_TIMEOUT_MS = 10_000
            private const val READ_TIMEOUT_MS = 10_000
        }

        private val cache: FeatureFlagCache = FeatureFlagCache(context)
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val mutex = Mutex()

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private var flags = mutableMapOf<String, Pair<Boolean, FeatureFlagVariant?>>()
        private var isInitialized = false
        private var refreshJob: Job? = null
        private var lastRefreshTime: Long? = null

        // Metrics
        private var totalEvaluations = 0
        private var cacheHits = 0
        private var cacheMisses = 0

        // MARK: - Lifecycle

        /**
         * Start the service and begin polling.
         *
         * Loads from cache first for immediate availability, then fetches
         * fresh flags from server.
         *
         * @throws FeatureFlagError If initial fetch fails and no cache available
         */
        suspend fun start() {
            mutex.withLock {
                if (isInitialized) {
                    Log.w(TAG, "FeatureFlagService already started")
                    return
                }

                Log.i(TAG, "Starting FeatureFlagService...")

                // Load from cache first for immediate availability
                if (config.enableOfflineMode) {
                    try {
                        cache.load()
                        val (count, _) = cache.getStatistics()
                        if (count > 0) {
                            Log.i(TAG, "Loaded $count flags from cache")
                            // Populate from cache
                            for (name in cache.getCachedFlagNames()) {
                                cache.get(name)?.let { cached ->
                                    flags[name] = cached
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load cache: ${e.message}")
                    }
                }

                // Fetch fresh flags from server
                try {
                    fetchFlags()
                } catch (e: Exception) {
                    Log.w(TAG, "Initial fetch failed: ${e.message}")
                    // Continue if we have cached data
                    if (flags.isEmpty()) {
                        throw e
                    }
                }

                isInitialized = true

                // Start background refresh
                startRefreshLoop()

                Log.i(TAG, "FeatureFlagService started with ${flags.size} flags")
            }
        }

        /**
         * Stop the service.
         */
        fun stop() {
            refreshJob?.cancel()
            refreshJob = null
            isInitialized = false
            Log.i(TAG, "FeatureFlagService stopped")
        }

        // MARK: - FeatureFlagEvaluating Implementation

        override suspend fun isEnabled(flagName: String): Boolean {
            return isEnabled(flagName, FeatureFlagContext.current())
        }

        override suspend fun isEnabled(
            flagName: String,
            context: FeatureFlagContext,
        ): Boolean =
            mutex.withLock {
                totalEvaluations++

                // Check in-memory flags first
                flags[flagName]?.let { flag ->
                    cacheHits++
                    Log.d(TAG, "Flag '$flagName' = ${flag.first} (memory)")
                    return@withLock flag.first
                }

                // Check persistent cache
                if (config.enableOfflineMode) {
                    cache.get(flagName)?.let { cached ->
                        cacheHits++
                        Log.d(TAG, "Flag '$flagName' = ${cached.first} (cache)")
                        return@withLock cached.first
                    }
                }

                cacheMisses++
                Log.d(TAG, "Flag '$flagName' not found, defaulting to false")
                false
            }

        override suspend fun getVariant(flagName: String): FeatureFlagVariant? {
            return getVariant(flagName, FeatureFlagContext.current())
        }

        override suspend fun getVariant(
            flagName: String,
            context: FeatureFlagContext,
        ): FeatureFlagVariant? =
            mutex.withLock {
                totalEvaluations++

                // Check in-memory flags first
                flags[flagName]?.let { flag ->
                    cacheHits++
                    return@withLock flag.second
                }

                // Check persistent cache
                if (config.enableOfflineMode) {
                    cache.get(flagName)?.let { cached ->
                        cacheHits++
                        return@withLock cached.second
                    }
                }

                cacheMisses++
                null
            }

        override suspend fun refresh() {
            fetchFlags()
        }

        // MARK: - Convenience Methods

        /**
         * Check multiple flags at once.
         *
         * @param flagNames List of flag names to check
         * @return Map of flag names to enabled status
         */
        suspend fun areEnabled(flagNames: List<String>): Map<String, Boolean> {
            return flagNames.associateWith { isEnabled(it) }
        }

        /**
         * Get current metrics.
         */
        fun getMetrics(): FeatureFlagMetrics {
            return FeatureFlagMetrics(
                totalEvaluations = totalEvaluations,
                cacheHits = cacheHits,
                cacheMisses = cacheMisses,
                lastRefreshTime = lastRefreshTime,
                flagCount = flags.size,
            )
        }

        /**
         * Get all flag names.
         */
        val flagNames: List<String>
            get() = flags.keys.toList()

        // MARK: - Private Methods

        private suspend fun fetchFlags() =
            withContext(Dispatchers.IO) {
                val url = buildUrl()

                Log.d(TAG, "Fetching flags from $url")

                try {
                    val connection =
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("Authorization", config.clientKey)
                            setRequestProperty("Accept", "application/json")
                            connectTimeout = CONNECTION_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                        }

                    val responseCode = connection.responseCode

                    when (responseCode) {
                        in 200..299 -> {
                            val response = connection.inputStream.bufferedReader().readText()
                            val proxyResponse = json.decodeFromString<UnleashProxyResponse>(response)

                            // Update flags
                            val newFlags = mutableMapOf<String, Pair<Boolean, FeatureFlagVariant?>>()
                            for (toggle in proxyResponse.toggles) {
                                val variant =
                                    toggle.variant?.let { v ->
                                        FeatureFlagVariant(
                                            name = v.name,
                                            enabled = v.enabled,
                                            payload = parsePayload(v.payload),
                                        )
                                    }
                                newFlags[toggle.name] = toggle.enabled to variant
                            }

                            mutex.withLock {
                                flags = newFlags
                                lastRefreshTime = System.currentTimeMillis()
                            }

                            // Persist to cache
                            if (config.enableOfflineMode) {
                                try {
                                    cache.save(newFlags)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to save flags to cache: ${e.message}")
                                }
                            }

                            Log.i(TAG, "Fetched ${proxyResponse.toggles.size} flags")
                        }
                        401, 403 -> throw FeatureFlagError.Unauthorized
                        else -> throw FeatureFlagError.ServerError(responseCode)
                    }

                    connection.disconnect()
                } catch (e: FeatureFlagError) {
                    throw e
                } catch (e: Exception) {
                    throw FeatureFlagError.NetworkError(e.message ?: "Unknown error")
                }
            }

        private fun buildUrl(): String {
            return buildString {
                append(config.proxyURL)
                append("?appName=")
                append(config.appName)
            }
        }

        private fun parsePayload(payload: UnleashPayload?): FeatureFlagPayload? {
            payload ?: return null

            return when (payload.type) {
                "string" -> FeatureFlagPayload.fromString(payload.value)
                "number" -> {
                    val number = payload.value.toDoubleOrNull()
                    if (number != null) {
                        FeatureFlagPayload.fromNumber(number)
                    } else {
                        FeatureFlagPayload.fromString(payload.value)
                    }
                }
                "json" -> {
                    try {
                        val map = json.decodeFromString<Map<String, String>>(payload.value)
                        FeatureFlagPayload.fromJson(map)
                    } catch (_: Exception) {
                        // Failed to parse as JSON map, fall back to string
                        FeatureFlagPayload.fromString(payload.value)
                    }
                }
                else -> FeatureFlagPayload.fromString(payload.value)
            }
        }

        private fun startRefreshLoop() {
            refreshJob?.cancel()

            refreshJob =
                scope.launch {
                    while (isActive) {
                        delay(config.refreshIntervalMs)

                        if (isActive) {
                            try {
                                fetchFlags()
                            } catch (e: Exception) {
                                Log.w(TAG, "Background refresh failed: ${e.message}", e)
                            }
                        }
                    }
                }
        }
    }
