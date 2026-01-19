package com.unamentis.core.featureflags

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages persistent caching of feature flags for offline support.
 *
 * Stores flags in a JSON file in the app's cache directory with expiration support.
 *
 * @property context Application context for file operations
 * @property maxCacheAgeMs Maximum age of cached data before expiration (default: 24 hours)
 */
class FeatureFlagCache(
    private val context: Context,
    private val maxCacheAgeMs: Long = 86_400_000L,
) {
    companion object {
        private const val TAG = "FeatureFlagCache"
        private const val CACHE_DIR_NAME = "feature_flags"
        private const val CACHE_FILE_NAME = "feature_flags_cache.json"
        private const val CACHE_VERSION = 1
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }
    }

    private val cacheFile: File by lazy {
        File(cacheDir, CACHE_FILE_NAME)
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var inMemoryCache = mutableMapOf<String, CachedFlag>()
    private var lastLoadTime: Long? = null
    private val mutex = Mutex()

    // MARK: - Types

    @Serializable
    private data class CachedFlag(
        val name: String,
        val enabled: Boolean,
        val variant: FeatureFlagVariant?,
        val cachedAt: Long,
    )

    @Serializable
    private data class CacheFile(
        val version: Int,
        val updatedAt: Long,
        val flags: Map<String, CachedFlag>,
    )

    // MARK: - Public Methods

    /**
     * Load flags from persistent cache.
     */
    suspend fun load() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!cacheFile.exists()) {
                    Log.d(TAG, "No cache file found")
                    return@withContext
                }

                try {
                    val content = cacheFile.readText()
                    val cache = json.decodeFromString<CacheFile>(content)

                    // Check if cache is still valid
                    val cacheAge = System.currentTimeMillis() - cache.updatedAt
                    if (cacheAge > maxCacheAgeMs) {
                        Log.i(TAG, "Cache expired (age: ${cacheAge / 1000}s), clearing")
                        cacheFile.delete()
                        return@withContext
                    }

                    inMemoryCache = cache.flags.toMutableMap()
                    lastLoadTime = System.currentTimeMillis()

                    Log.i(TAG, "Loaded ${cache.flags.size} flags from cache (age: ${cacheAge / 1000}s)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cache: ${e.message}")
                    throw FeatureFlagError.CacheError("Failed to load: ${e.message}")
                }
            }
        }

    /**
     * Save flags to persistent cache.
     *
     * @param flags Map of flag name to (enabled, variant) pair
     */
    suspend fun save(flags: Map<String, Pair<Boolean, FeatureFlagVariant?>>) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()

                // Update in-memory cache
                for ((name, value) in flags) {
                    inMemoryCache[name] =
                        CachedFlag(
                            name = name,
                            enabled = value.first,
                            variant = value.second,
                            cachedAt = now,
                        )
                }

                // Persist to disk
                val cacheFileData =
                    CacheFile(
                        version = CACHE_VERSION,
                        updatedAt = now,
                        flags = inMemoryCache,
                    )

                try {
                    val content = json.encodeToString(cacheFileData)
                    cacheFile.writeText(content)
                    Log.d(TAG, "Saved ${flags.size} flags to cache")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save cache: ${e.message}")
                    throw FeatureFlagError.CacheError("Failed to save: ${e.message}")
                }
            }
        }

    /**
     * Get a cached flag value.
     *
     * @param flagName Name of the flag
     * @return Pair of (enabled, variant) or null if not cached or expired
     */
    suspend fun get(flagName: String): Pair<Boolean, FeatureFlagVariant?>? =
        mutex.withLock {
            val cached = inMemoryCache[flagName] ?: return@withLock null

            // Check if this specific flag is expired
            val age = System.currentTimeMillis() - cached.cachedAt
            if (age > maxCacheAgeMs) {
                return@withLock null
            }

            cached.enabled to cached.variant
        }

    /**
     * Check if we have a valid cache.
     */
    val hasValidCache: Boolean
        get() = inMemoryCache.isNotEmpty()

    /**
     * Get cache statistics.
     *
     * @return Pair of (count, oldestAgeMs)
     */
    suspend fun getStatistics(): Pair<Int, Long?> =
        mutex.withLock {
            if (inMemoryCache.isEmpty()) {
                return@withLock 0 to null
            }

            val oldest = inMemoryCache.values.minOfOrNull { it.cachedAt }
            val age = oldest?.let { System.currentTimeMillis() - it }

            inMemoryCache.size to age
        }

    /**
     * Clear all cached flags.
     */
    suspend fun clear() =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                inMemoryCache.clear()
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
                Log.i(TAG, "Cache cleared")
            }
        }

    /**
     * Get all cached flag names.
     */
    suspend fun getCachedFlagNames(): List<String> =
        mutex.withLock {
            inMemoryCache.keys.toList()
        }
}
