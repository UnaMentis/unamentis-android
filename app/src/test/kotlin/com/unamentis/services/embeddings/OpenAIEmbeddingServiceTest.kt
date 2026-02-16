package com.unamentis.services.embeddings

import com.unamentis.core.telemetry.TelemetryEngine
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [OpenAIEmbeddingService].
 *
 * Uses MockWebServer to simulate the OpenAI Embeddings API and verifies
 * request formatting, response parsing, error handling, and cost tracking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAIEmbeddingServiceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var telemetryEngine: TelemetryEngine
    private lateinit var service: OpenAIEmbeddingService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

        telemetryEngine = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    /**
     * Create a service that targets our MockWebServer instead of the real OpenAI API.
     */
    private fun createService(
        model: EmbeddingModel = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
        apiKey: String = "test-api-key",
    ): OpenAIEmbeddingService {
        return OpenAIEmbeddingService(
            apiKey = apiKey,
            model = model,
            client = client,
            telemetryEngine = telemetryEngine,
            baseUrl = mockWebServer.url("/v1/embeddings").toString(),
        )
    }

    // region Provider Properties

    @Test
    fun `provider name is OpenAI`() {
        service = createService()
        assertEquals("OpenAI", service.providerName)
    }

    @Test
    fun `embedding dimension matches text-embedding-3-small`() {
        service = createService(model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
        assertEquals(1536, service.embeddingDimension)
    }

    @Test
    fun `embedding dimension matches text-embedding-ada-002`() {
        service = createService(model = EmbeddingModel.TEXT_EMBEDDING_ADA_002)
        assertEquals(1536, service.embeddingDimension)
    }

    // endregion

    // region Single Embedding

    @Test
    fun `embed returns correct embedding vector`() =
        runTest {
            service = createService()

            val embeddingValues = List(SMALL_EMBEDDING_SIZE) { it.toFloat() / SMALL_EMBEDDING_SIZE }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(buildSingleEmbeddingResponse(embeddingValues, tokensUsed = 5)),
            )

            val result = service.embed("Hello world")

            assertEquals(SMALL_EMBEDDING_SIZE, result.embedding.size)
            assertEquals(5, result.tokensUsed)
            assertEquals(EmbeddingModel.TEXT_EMBEDDING_3_SMALL, result.model)
        }

    @Test
    fun `embed sends correct request format`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(buildSingleEmbeddingResponse(listOf(0.1f, 0.2f), tokensUsed = 3)),
            )

            service.embed("Test input")

            val request = mockWebServer.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(
                "Request should contain Bearer auth",
                request.getHeader("Authorization")?.startsWith("Bearer ") == true,
            )
            assertTrue(
                "Request should have JSON content type",
                request.getHeader("Content-Type")?.contains("application/json") == true,
            )

            val body = request.body.readUtf8()
            assertTrue("Request body should contain model name", body.contains("text-embedding-3-small"))
            assertTrue("Request body should contain input text", body.contains("Test input"))
        }

    @Test
    fun `embed preprocesses text by replacing newlines with spaces`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(buildSingleEmbeddingResponse(listOf(0.1f), tokensUsed = 2)),
            )

            service.embed("Line one\nLine two\nLine three")

            val request = mockWebServer.takeRequest()
            val body = request.body.readUtf8()
            assertTrue(
                "Request body should have newlines replaced with spaces",
                body.contains("Line one Line two Line three"),
            )
        }

    @Test
    fun `embed tracks cost via telemetry engine`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(buildSingleEmbeddingResponse(listOf(0.1f), tokensUsed = 100)),
            )

            service.embed("Test")

            verify {
                telemetryEngine.recordCost(
                    provider = "OpenAI-Embeddings",
                    costUsd = any(),
                    metadata =
                        match {
                            it["type"] == "EMBEDDING" && it["model"] == "text-embedding-3-small"
                        },
                )
            }
        }

    @Test
    fun `embed sends correct authorization header`() =
        runTest {
            service = createService(apiKey = "sk-test-key-123")

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(buildSingleEmbeddingResponse(listOf(0.1f), tokensUsed = 1)),
            )

            service.embed("Test")

            val request = mockWebServer.takeRequest()
            assertEquals("Bearer sk-test-key-123", request.getHeader("Authorization"))
        }

    // endregion

    // region Batch Embedding

    @Test
    fun `embedBatch returns correct number of embeddings`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        buildBatchEmbeddingResponse(
                            embeddings =
                                listOf(
                                    listOf(0.1f, 0.2f),
                                    listOf(0.3f, 0.4f),
                                    listOf(0.5f, 0.6f),
                                ),
                            tokensUsed = 15,
                        ),
                    ),
            )

            val result = service.embedBatch(listOf("Text 1", "Text 2", "Text 3"))

            assertEquals(3, result.embeddings.size)
            assertEquals(15, result.totalTokensUsed)
            assertEquals(EmbeddingModel.TEXT_EMBEDDING_3_SMALL, result.model)
        }

    @Test
    fun `embedBatch sends all texts in single request`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        buildBatchEmbeddingResponse(
                            embeddings = listOf(listOf(0.1f), listOf(0.2f)),
                            tokensUsed = 10,
                        ),
                    ),
            )

            service.embedBatch(listOf("First text", "Second text"))

            assertEquals("Should send only one request", 1, mockWebServer.requestCount)
            val request = mockWebServer.takeRequest()
            val body = request.body.readUtf8()
            assertTrue("Body should contain First text", body.contains("First text"))
            assertTrue("Body should contain Second text", body.contains("Second text"))
        }

    @Test
    fun `embedBatch returns embeddings in correct order even if response is unordered`() =
        runTest {
            service = createService()

            // Response with indices out of order
            val responseBody =
                """
                {
                    "data": [
                        {"embedding": [0.3, 0.4], "index": 1},
                        {"embedding": [0.1, 0.2], "index": 0},
                        {"embedding": [0.5, 0.6], "index": 2}
                    ],
                    "model": "text-embedding-3-small",
                    "usage": {"prompt_tokens": 10, "total_tokens": 10}
                }
                """.trimIndent()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseBody),
            )

            val result = service.embedBatch(listOf("Text A", "Text B", "Text C"))

            // Should be sorted by index
            assertEquals(listOf(0.1f, 0.2f), result.embeddings[0])
            assertEquals(listOf(0.3f, 0.4f), result.embeddings[1])
            assertEquals(listOf(0.5f, 0.6f), result.embeddings[2])
        }

    @Test(expected = IllegalArgumentException::class)
    fun `embedBatch throws on empty text list`() =
        runTest {
            service = createService()
            service.embedBatch(emptyList())
        }

    @Test
    fun `embedBatch tracks cost for total tokens used`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        buildBatchEmbeddingResponse(
                            embeddings = listOf(listOf(0.1f), listOf(0.2f)),
                            tokensUsed = 200,
                        ),
                    ),
            )

            service.embedBatch(listOf("Text 1", "Text 2"))

            verify {
                telemetryEngine.recordCost(
                    provider = "OpenAI-Embeddings",
                    costUsd = any(),
                    metadata = any(),
                )
            }
        }

    // endregion

    // region Error Handling

    @Test
    fun `embed throws InvalidApiKey on 401 response`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_UNAUTHORIZED)
                    .setBody(
                        """{"error": {"message": "Incorrect API key provided", "type": "invalid_request_error"}}""",
                    ),
            )

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.InvalidApiKey) {
                // Expected
            }
        }

    @Test
    fun `embed throws RateLimited on 429 response`() =
        runTest {
            service = createService()

            val rateLimitBody =
                """{"error": {"message": """ +
                    """"Rate limit exceeded, try again in 30 seconds", """ +
                    """"type": "rate_limit_error"}}"""
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_TOO_MANY_REQUESTS)
                    .setBody(rateLimitBody),
            )

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.RateLimited) {
                assertEquals(30, e.retryAfterSeconds)
            }
        }

    @Test
    fun `embed throws RateLimited with null retryAfter when not parseable`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_TOO_MANY_REQUESTS)
                    .setBody(
                        """{"error": {"message": "Rate limit exceeded", "type": "rate_limit_error"}}""",
                    ),
            )

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.RateLimited) {
                assertEquals(null, e.retryAfterSeconds)
            }
        }

    @Test
    fun `embed throws InputTooLong on 400 with context length error`() =
        runTest {
            service = createService()

            val contextLengthBody =
                """{"error": {"message": """ +
                    """"This model's maximum context length is 8191 tokens", """ +
                    """"type": "invalid_request_error"}}"""
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_BAD_REQUEST)
                    .setBody(contextLengthBody),
            )

            try {
                service.embed("Very long text")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.InputTooLong) {
                // Expected
            }
        }

    @Test
    fun `embed throws ApiError on 400 without context length message`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_BAD_REQUEST)
                    .setBody(
                        """{"error": {"message": "Invalid input", "type": "invalid_request_error"}}""",
                    ),
            )

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.ApiError) {
                assertEquals(HTTP_BAD_REQUEST, e.statusCode)
            }
        }

    @Test
    fun `embed throws ApiError on 500 response`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_INTERNAL_ERROR)
                    .setBody(
                        """{"error": {"message": "Internal server error", "type": "server_error"}}""",
                    ),
            )

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.ApiError) {
                assertEquals(HTTP_INTERNAL_ERROR, e.statusCode)
            }
        }

    @Test
    fun `embed throws NetworkError when server is unreachable`() =
        runTest {
            // Create service pointing to a closed server
            mockWebServer.shutdown()

            service = createService()

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.NetworkError) {
                // Expected
            }
        }

    @Test
    fun `embed throws ApiError on malformed response body`() =
        runTest {
            service = createService()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody("not valid json"),
            )

            try {
                service.embed("Test")
                assertTrue("Should have thrown", false)
            } catch (e: EmbeddingError.ApiError) {
                // Expected - parse failure
            }
        }

    // endregion

    // region Model Configuration

    @Test
    fun `uses ada-002 model when configured`() =
        runTest {
            service = createService(model = EmbeddingModel.TEXT_EMBEDDING_ADA_002)

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(HTTP_OK)
                    .setHeader("Content-Type", "application/json")
                    .setBody(buildSingleEmbeddingResponse(listOf(0.1f), tokensUsed = 5)),
            )

            val result = service.embed("Test")

            val request = mockWebServer.takeRequest()
            val body = request.body.readUtf8()
            assertTrue("Body should contain ada-002 model", body.contains("text-embedding-ada-002"))
            assertEquals(EmbeddingModel.TEXT_EMBEDDING_ADA_002, result.model)
        }

    @Test
    fun `EmbeddingModel costPerToken values are correct`() {
        assertEquals(0.00000002, EmbeddingModel.TEXT_EMBEDDING_3_SMALL.costPerToken, 0.0)
        assertEquals(0.0000001, EmbeddingModel.TEXT_EMBEDDING_ADA_002.costPerToken, 0.0)
    }

    @Test
    fun `EmbeddingModel dimensions are correct`() {
        assertEquals(1536, EmbeddingModel.TEXT_EMBEDDING_3_SMALL.dimension)
        assertEquals(1536, EmbeddingModel.TEXT_EMBEDDING_ADA_002.dimension)
    }

    @Test
    fun `EmbeddingModel modelId values are correct`() {
        assertEquals("text-embedding-3-small", EmbeddingModel.TEXT_EMBEDDING_3_SMALL.modelId)
        assertEquals("text-embedding-ada-002", EmbeddingModel.TEXT_EMBEDDING_ADA_002.modelId)
    }

    // endregion

    // region Cosine Similarity

    @Test
    fun `cosineSimilarity of identical vectors is 1`() {
        val vector = listOf(1.0f, 2.0f, 3.0f)
        val similarity = cosineSimilarity(vector, vector)
        assertEquals(1.0f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity of opposite vectors is negative 1`() {
        val a = listOf(1.0f, 0.0f, 0.0f)
        val b = listOf(-1.0f, 0.0f, 0.0f)
        val similarity = cosineSimilarity(a, b)
        assertEquals(-1.0f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity of orthogonal vectors is 0`() {
        val a = listOf(1.0f, 0.0f, 0.0f)
        val b = listOf(0.0f, 1.0f, 0.0f)
        val similarity = cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity of empty vectors is 0`() {
        val similarity = cosineSimilarity(emptyList(), emptyList())
        assertEquals(0.0f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity of different-length vectors is 0`() {
        val a = listOf(1.0f, 2.0f)
        val b = listOf(1.0f, 2.0f, 3.0f)
        val similarity = cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity of zero vector is 0`() {
        val a = listOf(0.0f, 0.0f, 0.0f)
        val b = listOf(1.0f, 2.0f, 3.0f)
        val similarity = cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity produces expected value for known vectors`() {
        // cos(45 degrees) = 0.707...
        val a = listOf(1.0f, 0.0f)
        val b = listOf(1.0f, 1.0f)
        val similarity = cosineSimilarity(a, b)
        assertEquals(0.7071f, similarity, FLOAT_TOLERANCE)
    }

    @Test
    fun `cosineSimilarity is symmetric`() {
        val a = listOf(1.0f, 2.0f, 3.0f)
        val b = listOf(4.0f, 5.0f, 6.0f)
        assertEquals(cosineSimilarity(a, b), cosineSimilarity(b, a), FLOAT_TOLERANCE)
    }

    // endregion

    // region EmbeddingResult and BatchEmbeddingResult Data Classes

    @Test
    fun `EmbeddingResult holds correct data`() {
        val result =
            EmbeddingResult(
                embedding = listOf(0.1f, 0.2f, 0.3f),
                tokensUsed = 10,
                model = EmbeddingModel.TEXT_EMBEDDING_3_SMALL,
            )
        assertEquals(3, result.embedding.size)
        assertEquals(10, result.tokensUsed)
        assertEquals(EmbeddingModel.TEXT_EMBEDDING_3_SMALL, result.model)
    }

    @Test
    fun `BatchEmbeddingResult holds correct data`() {
        val result =
            BatchEmbeddingResult(
                embeddings = listOf(listOf(0.1f), listOf(0.2f)),
                totalTokensUsed = 20,
                model = EmbeddingModel.TEXT_EMBEDDING_ADA_002,
            )
        assertEquals(2, result.embeddings.size)
        assertEquals(20, result.totalTokensUsed)
        assertEquals(EmbeddingModel.TEXT_EMBEDDING_ADA_002, result.model)
    }

    // endregion

    // region Helper Methods

    /**
     * Build a mock API response for a single embedding.
     */
    private fun buildSingleEmbeddingResponse(
        embedding: List<Float>,
        tokensUsed: Int,
    ): String {
        val embeddingStr = embedding.joinToString(",")
        return """
            {
                "data": [
                    {"embedding": [$embeddingStr], "index": 0}
                ],
                "model": "text-embedding-3-small",
                "usage": {"prompt_tokens": $tokensUsed, "total_tokens": $tokensUsed}
            }
            """.trimIndent()
    }

    /**
     * Build a mock API response for a batch embedding.
     */
    private fun buildBatchEmbeddingResponse(
        embeddings: List<List<Float>>,
        tokensUsed: Int,
    ): String {
        val dataEntries =
            embeddings.mapIndexed { index, embedding ->
                val embeddingStr = embedding.joinToString(",")
                """{"embedding": [$embeddingStr], "index": $index}"""
            }.joinToString(",")

        return """
            {
                "data": [$dataEntries],
                "model": "text-embedding-3-small",
                "usage": {"prompt_tokens": $tokensUsed, "total_tokens": $tokensUsed}
            }
            """.trimIndent()
    }

    // endregion

    companion object {
        private const val FLOAT_TOLERANCE = 0.001f
        private const val SMALL_EMBEDDING_SIZE = 10
        private const val HTTP_OK = 200
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_ERROR = 500
    }
}
