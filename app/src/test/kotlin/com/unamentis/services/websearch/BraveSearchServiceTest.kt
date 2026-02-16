package com.unamentis.services.websearch

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for BraveSearchService.
 *
 * Uses MockWebServer to simulate Brave Search API responses.
 */
class BraveSearchServiceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client =
            OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun createService(apiKey: String = "test-api-key"): BraveSearchService {
        return BraveSearchService(
            apiKey = apiKey,
            client = client,
            baseUrl = mockWebServer.url("/res/v1/web/search").toString(),
        )
    }

    // region Success Scenarios

    @Test
    fun `search returns parsed results on success`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(SAMPLE_RESPONSE),
            )

            val service = createService()
            val response = service.search("kotlin coroutines")

            assertEquals("kotlin coroutines", response.query)
            assertEquals(2, response.results.size)

            val first = response.results[0]
            assertEquals("Kotlin Coroutines Guide", first.title)
            assertEquals(
                "https://kotlinlang.org/docs/coroutines-guide.html",
                first.url,
            )
            assertEquals("Official guide to Kotlin coroutines", first.description)

            val second = response.results[1]
            assertEquals("Coroutines on Android", second.title)
        }

    @Test
    fun `search returns empty results when no web results`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"query": {"original": "xyz"}, "web": {"results": []}}""",
                    ),
            )

            val service = createService()
            val response = service.search("asdfghjkl")

            assertEquals("asdfghjkl", response.query)
            assertTrue(response.results.isEmpty())
        }

    @Test
    fun `search sends correct headers`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(SAMPLE_RESPONSE),
            )

            val service = createService(apiKey = "my-brave-key")
            service.search("test")

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals(
                "my-brave-key",
                recordedRequest.getHeader("X-Subscription-Token"),
            )
            assertEquals(
                "application/json",
                recordedRequest.getHeader("Accept"),
            )
        }

    @Test
    fun `search sends correct query parameters`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(SAMPLE_RESPONSE),
            )

            val service = createService()
            service.search("hello world", maxResults = 3)

            val recordedRequest = mockWebServer.takeRequest()
            val requestUrl = recordedRequest.requestUrl!!
            assertEquals("hello world", requestUrl.queryParameter("q"))
            assertEquals("3", requestUrl.queryParameter("count"))
        }

    // endregion

    // region Error Scenarios

    @Test(expected = WebSearchException.ApiKeyMissing::class)
    fun `search throws ApiKeyMissing when key is blank`() =
        runTest {
            val service = createService(apiKey = "")
            service.search("test")
        }

    @Test(expected = WebSearchException.ApiKeyMissing::class)
    fun `search throws ApiKeyMissing when key is whitespace`() =
        runTest {
            val service = createService(apiKey = "   ")
            service.search("test")
        }

    @Test(expected = WebSearchException.RateLimited::class)
    fun `search throws RateLimited on 429`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(429))

            val service = createService()
            service.search("test")
        }

    @Test(expected = WebSearchException.QuotaExceeded::class)
    fun `search throws QuotaExceeded on 402`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(402))

            val service = createService()
            service.search("test")
        }

    @Test(expected = WebSearchException.RequestFailed::class)
    fun `search throws RequestFailed on 500`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val service = createService()
            service.search("test")
        }

    @Test(expected = WebSearchException.InvalidResponse::class)
    fun `search throws InvalidResponse on malformed JSON`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("not json"),
            )

            val service = createService()
            service.search("test")
        }

    // endregion

    // region Result Formatting

    @Test
    fun `formattedForLLM includes query and numbered results`() {
        val response =
            WebSearchResponse(
                query = "test query",
                results =
                    listOf(
                        WebSearchResult("Title 1", "https://example.com/1", "Desc 1"),
                        WebSearchResult("Title 2", "https://example.com/2", "Desc 2"),
                    ),
            )

        val formatted = response.formattedForLLM
        assertTrue(formatted.contains("Search results for: test query"))
        assertTrue(formatted.contains("1. Title 1"))
        assertTrue(formatted.contains("URL: https://example.com/1"))
        assertTrue(formatted.contains("2. Title 2"))
    }

    @Test
    fun `formattedForLLM shows no results message when empty`() {
        val response =
            WebSearchResponse(
                query = "no results query",
                results = emptyList(),
            )

        assertEquals(
            "No results found for: no results query",
            response.formattedForLLM,
        )
    }

    @Test
    fun `WebSearchResult formatted returns markdown link`() {
        val result = WebSearchResult("Title", "https://example.com", "Description")
        assertEquals(
            "[Title](https://example.com)\nDescription",
            result.formatted,
        )
    }

    // endregion

    // region Max Results Clamping

    @Test
    fun `search clamps maxResults to 20`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(SAMPLE_RESPONSE),
            )

            val service = createService()
            service.search("test", maxResults = 50)

            val recordedRequest = mockWebServer.takeRequest()
            assertEquals(
                "20",
                recordedRequest.requestUrl!!.queryParameter("count"),
            )
        }

    // endregion

    // region Total Results Parsing

    @Test
    fun `search parses totalResults when present`() =
        runTest {
            val responseWithTotal =
                """
                {
                    "web": {
                        "results": [],
                        "totalResults": 12345
                    }
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(responseWithTotal),
            )

            val service = createService()
            val response = service.search("test")
            assertNotNull(response.totalResults)
            assertEquals(12345, response.totalResults)
        }

    // endregion

    companion object {
        private val SAMPLE_RESPONSE =
            """
            {
                "query": {"original": "kotlin coroutines"},
                "web": {
                    "results": [
                        {
                            "title": "Kotlin Coroutines Guide",
                            "url": "https://kotlinlang.org/docs/coroutines-guide.html",
                            "description": "Official guide to Kotlin coroutines"
                        },
                        {
                            "title": "Coroutines on Android",
                            "url": "https://developer.android.com/kotlin/coroutines",
                            "description": "Using coroutines on Android"
                        }
                    ],
                    "totalResults": 1500000
                }
            }
            """.trimIndent()
    }
}
