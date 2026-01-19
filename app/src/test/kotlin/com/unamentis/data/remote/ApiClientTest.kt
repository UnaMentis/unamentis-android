package com.unamentis.data.remote

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ApiClient.
 *
 * Uses MockWebServer to simulate server responses without
 * making actual network requests.
 */
class ApiClientTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var apiClient: ApiClient
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val context = mockk<Context>(relaxed = true)
        apiClient =
            ApiClient(
                context = context,
                okHttpClient = OkHttpClient(),
                json = json,
                logServerUrl = mockServer.url("/").toString().trimEnd('/'),
                managementUrl = mockServer.url("/").toString().trimEnd('/'),
            )
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `getCurricula returns list of curricula`() =
        runTest {
            // Given
            val mockResponse =
                """
                [
                    {
                        "id": "curriculum-001",
                        "title": "Test Curriculum",
                        "description": "A test curriculum",
                        "version": "1.0.0",
                        "topic_count": 5
                    }
                ]
                """.trimIndent()

            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(mockResponse)
                    .addHeader("Content-Type", "application/json"),
            )

            // When
            val result = apiClient.getCurricula()

            // Then
            assertTrue(result is ApiResult.Success)
            val curricula = (result as ApiResult.Success).data
            assertEquals(1, curricula.size)
            assertEquals("curriculum-001", curricula[0].id)
            assertEquals("Test Curriculum", curricula[0].title)
            assertEquals(5, curricula[0].topicCount)

            // Verify request
            val request = mockServer.takeRequest()
            assertEquals("/api/curricula", request.path)
            assertNotNull(request.getHeader("X-Client-ID"))
            assertNotNull(request.getHeader("X-Client-Platform"))
        }

    @Test
    fun `uploadMetrics sends correct request`() =
        runTest {
            // Given
            val metrics =
                MetricsUploadRequest(
                    clientId = "test-client",
                    clientName = "Test Device",
                    sessionId = "session-001",
                    sessionDuration = 300.0,
                    turnsTotal = 10,
                    interruptions = 2,
                    sttLatencyMedian = 250.0,
                    sttLatencyP99 = 800.0,
                    llmTtftMedian = 180.0,
                    llmTtftP99 = 450.0,
                    ttsTtfbMedian = 150.0,
                    ttsTtfbP99 = 350.0,
                    e2eLatencyMedian = 480.0,
                    e2eLatencyP99 = 950.0,
                    sttCost = 0.25,
                    ttsCost = 0.15,
                    llmCost = 1.50,
                    totalCost = 1.90,
                    thermalThrottleEvents = 0,
                    networkDegradations = 1,
                )

            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"status": "received", "id": "metrics-001"}""")
                    .addHeader("Content-Type", "application/json"),
            )

            // When
            val response = apiClient.uploadMetrics(metrics)

            // Then
            assertEquals("received", response.status)
            assertEquals("metrics-001", response.id)

            // Verify request
            val request = mockServer.takeRequest()
            assertEquals("/api/metrics", request.path)
            assertNotNull(request.body)
        }

    @Test
    fun `sendLog handles failures gracefully`() =
        runTest {
            // Given
            val logEntry =
                LogEntry(
                    level = "INFO",
                    message = "Test log message",
                    label = "Test",
                    timestamp = System.currentTimeMillis(),
                )

            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Server error"),
            )

            // When - Should not throw exception
            apiClient.sendLog(logEntry)

            // Then - Request was made but failure was handled
            val request = mockServer.takeRequest()
            assertEquals("/log", request.path)
        }
}
