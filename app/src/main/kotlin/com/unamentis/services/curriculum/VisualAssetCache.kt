package com.unamentis.services.curriculum

import android.content.Context
import android.util.Log
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Disk and memory cache for visual assets (images, diagrams, equations, etc.).
 *
 * Ported from iOS VisualAssetCache. Provides:
 * - Two-level caching: in-memory LRU cache + on-disk file cache
 * - Automatic LRU eviction when disk cache exceeds configured maximum
 * - Concurrent-safe access via [Mutex]
 * - Download-and-cache in one step
 * - Preloading of assets for offline access
 * - Cache statistics for monitoring
 *
 * @property context Application context for accessing cache directory
 * @property okHttpClient HTTP client for downloading remote assets
 */
@Singleton
class VisualAssetCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
    ) {
        companion object {
            private const val TAG = "VisualAssetCache"

            /** Maximum in-memory cache size (50 MB). */
            private const val MAX_MEMORY_CACHE_BYTES = 50L * 1024 * 1024

            /** Maximum on-disk cache size (500 MB). */
            private const val MAX_DISK_CACHE_BYTES = 500L * 1024 * 1024

            /** Subdirectory name inside the app's cache directory. */
            private const val CACHE_DIR_NAME = "visual_assets"
        }

        private val cacheDirectory: File by lazy {
            File(context.cacheDir, CACHE_DIR_NAME).also { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        }

        /**
         * In-memory LRU cache. Keys are sanitized asset IDs; values are raw bytes.
         * Max size is measured in bytes.
         */
        private val memoryCache: LruCache<String, ByteArray> =
            object : LruCache<String, ByteArray>((MAX_MEMORY_CACHE_BYTES / 1024).toInt()) {
                override fun sizeOf(
                    key: String,
                    value: ByteArray,
                ): Int = (value.size / 1024).coerceAtLeast(1)
            }

        private val diskMutex = Mutex()

        /**
         * Cache asset data under the given asset ID.
         *
         * Writes to both the in-memory LRU cache and the on-disk file cache.
         * If the disk cache exceeds [MAX_DISK_CACHE_BYTES] after writing,
         * the least-recently-used files are evicted.
         *
         * @param assetId Unique identifier for the asset
         * @param data Raw asset bytes
         */
        suspend fun cache(
            assetId: String,
            data: ByteArray,
        ) {
            val key = sanitizeFilename(assetId)

            // Memory cache (synchronous, thread-safe internally)
            memoryCache.put(key, data)

            // Disk cache
            diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        val file = File(cacheDirectory, key)
                        file.writeBytes(data)
                        Log.d(TAG, "Cached asset $assetId (${data.size} bytes)")
                    } catch (e: IOException) {
                        Log.w(TAG, "Failed to write asset $assetId to disk: ${e.message}")
                    }
                }
            }

            // Evict excess disk entries if needed
            evictIfNeeded()
        }

        /**
         * Retrieve cached asset data by ID.
         *
         * Checks the in-memory cache first, then falls back to the disk cache.
         * If found on disk but not in memory, the data is promoted to the memory cache.
         *
         * @param assetId Unique identifier for the asset
         * @return Raw asset bytes, or null if not cached
         */
        suspend fun retrieve(assetId: String): ByteArray? {
            val key = sanitizeFilename(assetId)

            // Check memory cache first
            memoryCache.get(key)?.let { data ->
                Log.d(TAG, "Memory cache hit for $assetId")
                return data
            }

            // Check disk cache
            return diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    val file = File(cacheDirectory, key)
                    if (file.exists()) {
                        try {
                            val data = file.readBytes()
                            // Promote to memory cache
                            memoryCache.put(key, data)
                            // Touch the file to update access time for LRU
                            file.setLastModified(System.currentTimeMillis())
                            Log.d(TAG, "Disk cache hit for $assetId")
                            data
                        } catch (e: IOException) {
                            Log.w(TAG, "Failed to read asset $assetId from disk: ${e.message}")
                            null
                        }
                    } else {
                        Log.d(TAG, "Cache miss for $assetId")
                        null
                    }
                }
            }
        }

        /**
         * Check whether an asset is cached (memory or disk).
         *
         * @param assetId Unique identifier for the asset
         * @return true if the asset is available in either cache layer
         */
        suspend fun isCached(assetId: String): Boolean {
            val key = sanitizeFilename(assetId)
            if (memoryCache.get(key) != null) return true

            return diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    File(cacheDirectory, key).exists()
                }
            }
        }

        /**
         * Download an asset from a remote URL and cache it.
         *
         * If the asset is already cached, the cached version is returned without
         * making a network request.
         *
         * @param assetId Unique identifier for the asset
         * @param url Remote URL to download from
         * @return Downloaded (or cached) asset bytes
         * @throws VisualAssetCacheException if the download or cache operation fails
         */
        @Suppress("ThrowsCount")
        suspend fun downloadAndCache(
            assetId: String,
            url: String,
        ): ByteArray {
            // Return cached version if available
            retrieve(assetId)?.let { return it }

            // Download from network
            val data =
                withContext(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(url).get().build()
                        okHttpClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw VisualAssetCacheException(
                                    "Download failed for $assetId: HTTP ${response.code}",
                                )
                            }
                            response.body?.bytes()
                                ?: throw VisualAssetCacheException(
                                    "Empty response body for asset $assetId",
                                )
                        }
                    } catch (e: VisualAssetCacheException) {
                        throw e
                    } catch (e: Exception) {
                        throw VisualAssetCacheException(
                            "Download failed for $assetId: ${e.message}",
                            e,
                        )
                    }
                }

            // Cache the downloaded data
            cache(assetId, data)
            return data
        }

        /**
         * Clear the in-memory cache only.
         *
         * The disk cache is preserved. Useful for responding to low-memory conditions.
         */
        fun clearMemoryCache() {
            memoryCache.evictAll()
            Log.i(TAG, "Memory cache cleared")
        }

        /**
         * Clear all cached data (memory and disk).
         */
        suspend fun clearAllCache() {
            memoryCache.evictAll()

            diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    cacheDirectory.listFiles()?.forEach { file ->
                        file.delete()
                    }
                }
            }

            Log.i(TAG, "All cache cleared")
        }

        /**
         * Remove a single asset from all cache layers.
         *
         * @param assetId The asset ID to remove
         */
        suspend fun remove(assetId: String) {
            val key = sanitizeFilename(assetId)
            memoryCache.remove(key)

            diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    val file = File(cacheDirectory, key)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }

            Log.d(TAG, "Removed asset $assetId from cache")
        }

        /**
         * Get the total disk cache size in bytes.
         *
         * @return Total size of all files in the disk cache directory
         */
        suspend fun diskCacheSize(): Long =
            diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    cacheDirectory.listFiles()?.sumOf { it.length() } ?: 0L
                }
            }

        /**
         * Get cache statistics for monitoring and diagnostics.
         *
         * @return [CacheStats] with current memory and disk usage
         */
        suspend fun cacheStats(): CacheStats {
            val diskFiles =
                diskMutex.withLock {
                    withContext(Dispatchers.IO) {
                        cacheDirectory.listFiles()?.size ?: 0
                    }
                }
            val diskSize = diskCacheSize()

            return CacheStats(
                memoryItemCount = memoryCache.size(),
                memorySizeBytes = memoryCache.size().toLong() * 1024,
                diskItemCount = diskFiles,
                diskSizeBytes = diskSize,
                maxMemorySizeBytes = MAX_MEMORY_CACHE_BYTES,
                maxDiskSizeBytes = MAX_DISK_CACHE_BYTES,
                memoryHitCount = memoryCache.hitCount(),
                memoryMissCount = memoryCache.missCount(),
            )
        }

        /**
         * Evict least-recently-used files from the disk cache if total size exceeds the limit.
         */
        private suspend fun evictIfNeeded() {
            diskMutex.withLock {
                withContext(Dispatchers.IO) {
                    val files = cacheDirectory.listFiles() ?: return@withContext
                    val totalSize = files.sumOf { it.length() }

                    if (totalSize <= MAX_DISK_CACHE_BYTES) return@withContext

                    // Sort by last modified (oldest first) for LRU eviction
                    val sortedFiles = files.sortedBy { it.lastModified() }
                    var freed = 0L
                    val targetFree = totalSize - MAX_DISK_CACHE_BYTES

                    for (file in sortedFiles) {
                        if (freed >= targetFree) break
                        val fileSize = file.length()
                        if (file.delete()) {
                            freed += fileSize
                            memoryCache.remove(file.name)
                            Log.d(TAG, "Evicted ${file.name} ($fileSize bytes)")
                        }
                    }

                    Log.i(TAG, "Disk cache eviction freed $freed bytes")
                }
            }
        }

        /**
         * Sanitize an asset ID into a safe filename.
         *
         * Replaces non-alphanumeric characters with underscores.
         */
        private fun sanitizeFilename(assetId: String): String = assetId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

/**
 * Cache statistics for the visual asset cache.
 *
 * @property memoryItemCount Number of items in the memory cache
 * @property memorySizeBytes Approximate memory usage in bytes
 * @property diskItemCount Number of files on disk
 * @property diskSizeBytes Total disk usage in bytes
 * @property maxMemorySizeBytes Maximum allowed memory cache size
 * @property maxDiskSizeBytes Maximum allowed disk cache size
 * @property memoryHitCount Number of memory cache hits
 * @property memoryMissCount Number of memory cache misses
 */
data class CacheStats(
    val memoryItemCount: Int,
    val memorySizeBytes: Long,
    val diskItemCount: Int,
    val diskSizeBytes: Long,
    val maxMemorySizeBytes: Long,
    val maxDiskSizeBytes: Long,
    val memoryHitCount: Int,
    val memoryMissCount: Int,
)

/**
 * Exception thrown when visual asset cache operations fail.
 *
 * @property message Description of the failure
 * @property cause Optional underlying exception
 */
class VisualAssetCacheException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
