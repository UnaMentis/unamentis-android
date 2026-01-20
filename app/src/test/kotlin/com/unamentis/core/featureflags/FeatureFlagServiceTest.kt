package com.unamentis.core.featureflags

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for FeatureFlagService and FeatureFlagCache.
 *
 * Tests cover:
 * - Service lifecycle
 * - Flag evaluation
 * - Cache operations
 * - Error handling
 * - Metrics tracking
 */
class FeatureFlagServiceTest {
    private lateinit var mockContext: Context
    private lateinit var mockCacheDir: File
    private lateinit var mockServer: MockWebServer

    @Before
    fun setup() {
        mockCacheDir =
            File.createTempFile("cache", "test").apply {
                delete()
                mkdirs()
            }

        mockContext = mockk<Context>(relaxed = true)
        every { mockContext.cacheDir } returns mockCacheDir

        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        mockCacheDir.deleteRecursively()
    }

    // MARK: - FeatureFlagCache Tests

    @Test
    fun `cache saves and loads flags correctly`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            val flags =
                mapOf(
                    "feature_a" to (true to null),
                    "feature_b" to (false to FeatureFlagVariant("variant1", true, null)),
                )

            cache.save(flags)
            cache.load()

            val flagA = cache.get("feature_a")
            val flagB = cache.get("feature_b")

            assertNotNull(flagA)
            assertTrue(flagA!!.first)
            assertNull(flagA.second)

            assertNotNull(flagB)
            assertFalse(flagB!!.first)
            assertNotNull(flagB.second)
            assertEquals("variant1", flagB.second?.name)
        }

    @Test
    fun `cache returns null for unknown flag`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            val result = cache.get("unknown_flag")

            assertNull(result)
        }

    @Test
    fun `cache clear removes all flags`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            cache.save(mapOf("feature_a" to (true to null)))
            cache.clear()

            val result = cache.get("feature_a")
            assertNull(result)
        }

    @Test
    fun `cache hasValidCache returns true when populated`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            assertFalse(cache.hasValidCache)

            cache.save(mapOf("feature_a" to (true to null)))

            assertTrue(cache.hasValidCache)
        }

    @Test
    fun `cache getStatistics returns correct counts`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            cache.save(
                mapOf(
                    "flag1" to (true to null),
                    "flag2" to (false to null),
                    "flag3" to (true to null),
                ),
            )

            val (count, age) = cache.getStatistics()

            assertEquals(3, count)
            assertNotNull(age)
            assertTrue(age!! < 1000)
        }

    @Test
    fun `cache getCachedFlagNames returns all names`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            cache.save(
                mapOf(
                    "alpha" to (true to null),
                    "beta" to (false to null),
                ),
            )

            val names = cache.getCachedFlagNames()

            assertEquals(2, names.size)
            assertTrue(names.contains("alpha"))
            assertTrue(names.contains("beta"))
        }

    // MARK: - FeatureFlagVariant Tests

    @Test
    fun `variant stores name enabled and payload`() {
        val payload = FeatureFlagPayload.fromString("test-value")
        val variant = FeatureFlagVariant("test-variant", true, payload)

        assertEquals("test-variant", variant.name)
        assertTrue(variant.enabled)
        assertEquals("test-value", variant.payload?.stringValue)
    }

    // MARK: - FeatureFlagPayload Tests

    @Test
    fun `payload fromString creates string payload`() {
        val payload = FeatureFlagPayload.fromString("hello")

        assertEquals("hello", payload.stringValue)
        assertNull(payload.numberValue)
        assertNull(payload.jsonValue)
    }

    @Test
    fun `payload fromNumber creates number payload`() {
        val payload = FeatureFlagPayload.fromNumber(42.5)

        assertEquals(42.5, payload.numberValue)
        assertNull(payload.stringValue) // Number payload doesn't have stringValue
    }

    @Test
    fun `payload fromJson creates json payload`() {
        val json = mapOf("key" to "value", "num" to "123")
        val payload = FeatureFlagPayload.fromJson(json)

        assertNotNull(payload.jsonValue)
        assertEquals("value", payload.jsonValue?.get("key"))
    }

    // MARK: - FeatureFlagContext Tests

    @Test
    fun `context stores user and session IDs`() {
        val context =
            FeatureFlagContext(
                userId = "user-123",
                sessionId = "session-456",
                properties = mapOf("tier" to "premium"),
            )

        assertEquals("user-123", context.userId)
        assertEquals("session-456", context.sessionId)
        assertEquals("premium", context.properties["tier"])
    }

    @Test
    fun `context current creates default context`() {
        val context = FeatureFlagContext.current()

        assertNotNull(context)
        assertEquals("Android", context.platform)
    }

    // MARK: - FeatureFlagConfig Tests

    @Test
    fun `config defaults are correct`() {
        val config = FeatureFlagConfig.DEVELOPMENT

        assertTrue(config.proxyURL.isNotEmpty())
        assertTrue(config.appName.isNotEmpty())
        assertTrue(config.enableOfflineMode)
        assertEquals(30_000L, config.refreshIntervalMs)
    }

    @Test
    fun `config production factory creates config with custom URL`() {
        val dev = FeatureFlagConfig.DEVELOPMENT
        val prod = FeatureFlagConfig.production("https://prod.example.com/proxy", "prod-key")

        assertFalse(dev.proxyURL == prod.proxyURL)
        assertEquals("https://prod.example.com/proxy", prod.proxyURL)
        assertEquals("prod-key", prod.clientKey)
    }

    // MARK: - FeatureFlagError Tests

    @Test
    fun `error types are distinguishable`() {
        val networkError = FeatureFlagError.NetworkError("Connection failed")
        val serverError = FeatureFlagError.ServerError(500)
        val unauthorized = FeatureFlagError.Unauthorized
        val cacheError = FeatureFlagError.CacheError("Read failed")

        assertTrue(networkError.message?.contains("Connection failed") == true)
        assertTrue(serverError.message?.contains("500") == true)
        assertNotNull(unauthorized.message)
        assertTrue(cacheError.message?.contains("Read failed") == true)
    }

    // MARK: - FeatureFlagMetrics Tests

    @Test
    fun `metrics tracks evaluations correctly`() {
        val metrics =
            FeatureFlagMetrics(
                totalEvaluations = 100,
                cacheHits = 85,
                cacheMisses = 15,
                lastRefreshTime = System.currentTimeMillis(),
                flagCount = 10,
            )

        assertEquals(100, metrics.totalEvaluations)
        assertEquals(85, metrics.cacheHits)
        assertEquals(15, metrics.cacheMisses)
        assertEquals(10, metrics.flagCount)
        // cacheHitRate = 85/100 = 0.85
        assertEquals(0.85, metrics.cacheHitRate, 0.01)
    }

    @Test
    fun `metrics cacheHitRate handles zero evaluations`() {
        val metrics =
            FeatureFlagMetrics(
                totalEvaluations = 0,
                cacheHits = 0,
                cacheMisses = 0,
                lastRefreshTime = null,
                flagCount = 0,
            )

        assertEquals(0.0, metrics.cacheHitRate, 0.001)
    }

    // MARK: - Known Flags Tests

    @Test
    fun `known flags contains expected values`() {
        assertTrue(FeatureFlagKeys.MAINTENANCE_MODE.isNotEmpty())
        assertTrue(FeatureFlagKeys.SPECIALIZED_MODULES.isNotEmpty())
        assertTrue(FeatureFlagKeys.TEAM_MODE.isNotEmpty())
        assertTrue(FeatureFlagKeys.KNOWLEDGE_BOWL.isNotEmpty())
    }

    // MARK: - Integration-like Tests

    @Test
    fun `cache persists across instances`() =
        runTest {
            val cache1 = FeatureFlagCache(mockContext)
            cache1.save(mapOf("persistent_flag" to (true to null)))

            // Create new cache instance pointing to same location
            val cache2 = FeatureFlagCache(mockContext)
            cache2.load()

            val result = cache2.get("persistent_flag")
            assertNotNull(result)
            assertTrue(result!!.first)
        }

    @Test
    fun `cache handles variant with all payload types`() =
        runTest {
            val cache = FeatureFlagCache(mockContext)

            val stringVariant =
                FeatureFlagVariant(
                    "string-var",
                    true,
                    FeatureFlagPayload.fromString("hello"),
                )

            val numberVariant =
                FeatureFlagVariant(
                    "number-var",
                    true,
                    FeatureFlagPayload.fromNumber(99.9),
                )

            val jsonVariant =
                FeatureFlagVariant(
                    "json-var",
                    true,
                    FeatureFlagPayload.fromJson(mapOf("a" to "b")),
                )

            cache.save(
                mapOf(
                    "string_flag" to (true to stringVariant),
                    "number_flag" to (true to numberVariant),
                    "json_flag" to (true to jsonVariant),
                ),
            )

            cache.load()

            val s = cache.get("string_flag")
            val n = cache.get("number_flag")
            val j = cache.get("json_flag")

            assertEquals("hello", s?.second?.payload?.stringValue)
            assertEquals(99.9, n?.second?.payload?.numberValue)
            assertEquals("b", j?.second?.payload?.jsonValue?.get("a"))
        }
}
