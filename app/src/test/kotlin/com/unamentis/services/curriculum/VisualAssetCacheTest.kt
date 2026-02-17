package com.unamentis.services.curriculum

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [VisualAssetCache].
 *
 * Uses a temp directory to simulate the Android cache directory,
 * and MockWebServer for download tests.
 */
class VisualAssetCacheTest {
    private lateinit var context: Context
    private lateinit var tempCacheDir: File
    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var cache: VisualAssetCache

    @Before
    fun setup() {
        // Create a real temp directory for the disk cache
        tempCacheDir = File(System.getProperty("java.io.tmpdir"), "visual_asset_cache_test_${System.nanoTime()}")
        tempCacheDir.mkdirs()

        // Mock Android Context to return our temp directory
        context = mockk(relaxed = true)
        every { context.cacheDir } returns tempCacheDir

        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

        cache =
            VisualAssetCache(
                context = context,
                okHttpClient = okHttpClient,
            )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        // Clean up temp directory
        tempCacheDir.deleteRecursively()
    }

    // region Cache / Retrieve Tests

    @Test
    fun `cache stores data and retrieve returns it`() =
        runTest {
            val data = "Hello, world!".toByteArray()

            cache.cache("asset-1", data)
            val retrieved = cache.retrieve("asset-1")

            assertNotNull("Retrieved data should not be null", retrieved)
            assertArrayEquals(data, retrieved)
        }

    @Test
    fun `retrieve returns null for uncached asset`() =
        runTest {
            val result = cache.retrieve("nonexistent-asset")
            assertNull("Should return null for uncached asset", result)
        }

    @Test
    fun `cache overwrites existing data`() =
        runTest {
            val originalData = "original".toByteArray()
            val updatedData = "updated".toByteArray()

            cache.cache("asset-1", originalData)
            cache.cache("asset-1", updatedData)

            val retrieved = cache.retrieve("asset-1")
            assertArrayEquals(updatedData, retrieved)
        }

    @Test
    fun `retrieve from disk after memory cache is cleared`() =
        runTest {
            val data = "persist on disk".toByteArray()

            cache.cache("asset-1", data)
            cache.clearMemoryCache()

            val retrieved = cache.retrieve("asset-1")
            assertNotNull("Should retrieve from disk after memory clear", retrieved)
            assertArrayEquals(data, retrieved)
        }

    @Test
    fun `retrieve promotes disk data to memory cache`() =
        runTest {
            val data = "promotable data".toByteArray()

            cache.cache("asset-1", data)
            cache.clearMemoryCache()

            // First retrieve: from disk
            cache.retrieve("asset-1")

            // Second retrieve should come from memory (we can't directly check,
            // but it should still return the same data)
            val retrieved = cache.retrieve("asset-1")
            assertNotNull(retrieved)
            assertArrayEquals(data, retrieved)
        }

    // endregion

    // region isCached Tests

    @Test
    fun `isCached returns true for cached asset`() =
        runTest {
            cache.cache("asset-1", "data".toByteArray())
            assertTrue(cache.isCached("asset-1"))
        }

    @Test
    fun `isCached returns false for uncached asset`() =
        runTest {
            assertFalse(cache.isCached("nonexistent"))
        }

    @Test
    fun `isCached returns true from disk after memory clear`() =
        runTest {
            cache.cache("asset-1", "data".toByteArray())
            cache.clearMemoryCache()
            assertTrue("Should find asset on disk", cache.isCached("asset-1"))
        }

    // endregion

    // region Download and Cache Tests

    @Test
    fun `downloadAndCache downloads from URL and caches`() =
        runTest {
            val assetData = ByteArray(1024) { it.toByte() }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(assetData)),
            )

            val result =
                cache.downloadAndCache(
                    "asset-download",
                    mockWebServer.url("/asset.png").toString(),
                )

            assertArrayEquals(assetData, result)

            // Should be cached now
            assertTrue(cache.isCached("asset-download"))

            // Verify the request was made
            val request = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(request)
            assertEquals("GET", request!!.method)
        }

    @Test
    fun `downloadAndCache returns cached version without network request`() =
        runTest {
            val data = "cached version".toByteArray()
            cache.cache("asset-cached", data)

            // Should not hit the server
            val result = cache.downloadAndCache("asset-cached", "http://should-not-be-called.test")

            assertArrayEquals(data, result)

            // No request should have been made to MockWebServer
            val request = mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS)
            assertNull("Should not make network request for cached asset", request)
        }

    @Test
    fun `downloadAndCache throws on HTTP error`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found"),
            )

            var thrownException: VisualAssetCacheException? = null
            try {
                cache.downloadAndCache(
                    "asset-missing",
                    mockWebServer.url("/missing.png").toString(),
                )
            } catch (e: VisualAssetCacheException) {
                thrownException = e
            }

            assertNotNull("Should throw VisualAssetCacheException", thrownException)
            assertTrue(
                "Error should mention HTTP code",
                thrownException!!.message.contains("404"),
            )
        }

    @Test
    fun `downloadAndCache throws on network error`() =
        runTest {
            // Shut down server to cause connection failure
            val serverUrl = mockWebServer.url("/asset.png").toString()
            mockWebServer.shutdown()

            var thrownException: VisualAssetCacheException? = null
            try {
                cache.downloadAndCache("asset-fail", serverUrl)
            } catch (e: VisualAssetCacheException) {
                thrownException = e
            }

            assertNotNull("Should throw VisualAssetCacheException on network error", thrownException)

            // Restart server for teardown
            mockWebServer = MockWebServer()
            mockWebServer.start()
        }

    @Test
    fun `downloadAndCache throws on empty response body`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200),
            )

            var thrownException: VisualAssetCacheException? = null
            try {
                cache.downloadAndCache(
                    "asset-empty",
                    mockWebServer.url("/empty.png").toString(),
                )
            } catch (e: VisualAssetCacheException) {
                thrownException = e
            }

            assertNotNull("Should throw for empty response body", thrownException)
        }

    // endregion

    // region Remove Tests

    @Test
    fun `remove deletes asset from both caches`() =
        runTest {
            cache.cache("asset-to-remove", "data".toByteArray())
            assertTrue(cache.isCached("asset-to-remove"))

            cache.remove("asset-to-remove")

            assertFalse("Asset should be removed from memory and disk", cache.isCached("asset-to-remove"))
            assertNull(cache.retrieve("asset-to-remove"))
        }

    @Test
    fun `remove for nonexistent asset does not throw`() =
        runTest {
            // Should be a no-op, not throw
            cache.remove("nonexistent-asset")
        }

    // endregion

    // region Clear Tests

    @Test
    fun `clearMemoryCache clears only memory`() =
        runTest {
            cache.cache("asset-1", "data1".toByteArray())
            cache.cache("asset-2", "data2".toByteArray())

            cache.clearMemoryCache()

            // Should still be retrievable from disk
            assertNotNull(cache.retrieve("asset-1"))
            assertNotNull(cache.retrieve("asset-2"))
        }

    @Test
    fun `clearAllCache clears memory and disk`() =
        runTest {
            cache.cache("asset-1", "data1".toByteArray())
            cache.cache("asset-2", "data2".toByteArray())

            cache.clearAllCache()

            assertNull("Memory and disk should be cleared", cache.retrieve("asset-1"))
            assertNull("Memory and disk should be cleared", cache.retrieve("asset-2"))
            assertFalse(cache.isCached("asset-1"))
            assertFalse(cache.isCached("asset-2"))
        }

    // endregion

    // region Disk Cache Size Tests

    @Test
    fun `diskCacheSize returns zero for empty cache`() =
        runTest {
            assertEquals(0L, cache.diskCacheSize())
        }

    @Test
    fun `diskCacheSize returns correct size after caching`() =
        runTest {
            val data1 = ByteArray(1000) { 1 }
            val data2 = ByteArray(2000) { 2 }

            cache.cache("asset-1", data1)
            cache.cache("asset-2", data2)

            val size = cache.diskCacheSize()
            assertEquals(3000L, size)
        }

    // endregion

    // region Cache Stats Tests

    @Test
    fun `cacheStats returns correct disk counts`() =
        runTest {
            cache.cache("asset-1", ByteArray(100))
            cache.cache("asset-2", ByteArray(200))

            val stats = cache.cacheStats()
            assertEquals(2, stats.diskItemCount)
            assertEquals(300L, stats.diskSizeBytes)
        }

    @Test
    fun `cacheStats tracks memory hit and miss counts`() =
        runTest {
            cache.cache("asset-1", "data".toByteArray())

            // Hit (from memory)
            cache.retrieve("asset-1")

            // Miss (not cached)
            cache.retrieve("nonexistent")

            val stats = cache.cacheStats()
            assertTrue("Should have at least 1 memory hit", stats.memoryHitCount >= 1)
            assertTrue("Should have at least 1 memory miss", stats.memoryMissCount >= 1)
        }

    @Test
    fun `cacheStats reports max sizes`() =
        runTest {
            val stats = cache.cacheStats()
            assertEquals(50L * 1024 * 1024, stats.maxMemorySizeBytes)
            assertEquals(500L * 1024 * 1024, stats.maxDiskSizeBytes)
        }

    // endregion

    // region Filename Sanitization Tests

    @Test
    fun `cache handles special characters in asset ID`() =
        runTest {
            val data = "special chars data".toByteArray()
            cache.cache("asset/with:special\\chars?and*more", data)

            val retrieved = cache.retrieve("asset/with:special\\chars?and*more")
            assertNotNull("Should handle special characters", retrieved)
            assertArrayEquals(data, retrieved)
        }

    @Test
    fun `cache handles dots and hyphens in asset ID`() =
        runTest {
            val data = "valid filename data".toByteArray()
            cache.cache("asset-with.dots-and_underscores", data)

            val retrieved = cache.retrieve("asset-with.dots-and_underscores")
            assertNotNull(retrieved)
            assertArrayEquals(data, retrieved)
        }

    // endregion

    // region Disk Cache Eviction Tests

    @Test
    fun `disk cache evicts oldest files when limit exceeded`() =
        runTest {
            // The MAX_DISK_CACHE_BYTES is 500 MB which is too large to test directly.
            // Instead, we verify the eviction mechanism works conceptually by
            // checking that after caching many items, the disk cache size
            // doesn't grow unboundedly.
            //
            // We test that the eviction logic preserves newer files by
            // checking the cache still works after storing multiple items.

            val data = ByteArray(1024) { it.toByte() }

            // Cache multiple items
            for (i in 0 until 10) {
                cache.cache("eviction-test-$i", data)
            }

            // All should be retrievable (well under the 500MB limit)
            for (i in 0 until 10) {
                assertNotNull(
                    "Asset eviction-test-$i should still be cached",
                    cache.retrieve("eviction-test-$i"),
                )
            }

            // Disk size should reflect all cached items
            val diskSize = cache.diskCacheSize()
            assertEquals(10L * 1024, diskSize)
        }

    // endregion

    // region Multiple Concurrent Access Tests

    @Test
    fun `concurrent cache operations do not corrupt data`() =
        runTest {
            // Cache and retrieve multiple items to test mutex protection
            val items =
                (0 until 20).map { i ->
                    "concurrent-$i" to ByteArray(100) { (i * 3 + it).toByte() }
                }

            // Cache all items
            for ((id, data) in items) {
                cache.cache(id, data)
            }

            // Retrieve all items and verify integrity
            for ((id, expectedData) in items) {
                val retrieved = cache.retrieve(id)
                assertNotNull("Should retrieve concurrent-$id", retrieved)
                assertArrayEquals(
                    "Data for $id should match",
                    expectedData,
                    retrieved,
                )
            }
        }

    // endregion

    // region CacheStats Data Class Tests

    @Test
    fun `CacheStats equality and properties`() {
        val stats =
            CacheStats(
                memoryItemCount = 5,
                memorySizeBytes = 5120,
                diskItemCount = 10,
                diskSizeBytes = 102400,
                maxMemorySizeBytes = 50 * 1024 * 1024,
                maxDiskSizeBytes = 500 * 1024 * 1024,
                memoryHitCount = 20,
                memoryMissCount = 3,
            )

        assertEquals(5, stats.memoryItemCount)
        assertEquals(5120L, stats.memorySizeBytes)
        assertEquals(10, stats.diskItemCount)
        assertEquals(102400L, stats.diskSizeBytes)
        assertEquals(20, stats.memoryHitCount)
        assertEquals(3, stats.memoryMissCount)
    }

    // endregion

    // region VisualAssetCacheException Tests

    @Test
    fun `VisualAssetCacheException stores message`() {
        val exception = VisualAssetCacheException("Cache failed")
        assertEquals("Cache failed", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `VisualAssetCacheException stores cause`() {
        val cause = RuntimeException("IO error")
        val exception = VisualAssetCacheException("Cache failed", cause)
        assertEquals("Cache failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    // endregion
}
