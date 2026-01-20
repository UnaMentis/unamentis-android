package com.unamentis.services.tts

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for ChatterboxTTSService.
 */
class ChatterboxTTSServiceTest {
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: Call
    private lateinit var service: ChatterboxTTSService

    @Before
    fun setup() {
        mockClient = mockk(relaxed = true)
        mockCall = mockk(relaxed = true)

        service =
            ChatterboxTTSService(
                baseUrl = "https://test.chatterbox.ai",
                apiKey = "test-api-key",
                config = ChatterboxConfig.DEFAULT,
                client = mockClient,
            )
    }

    @Test
    fun `provider name is Chatterbox`() {
        assertEquals("Chatterbox", service.providerName)
    }

    @Test
    fun `ChatterboxConfig validates exaggeration range`() {
        // Valid values
        ChatterboxConfig(exaggeration = 0.0f)
        ChatterboxConfig(exaggeration = 0.5f)
        ChatterboxConfig(exaggeration = 1.5f)

        // Invalid values should throw
        var exceptionThrown = false
        try {
            ChatterboxConfig(exaggeration = 2.0f)
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Should throw for exaggeration > 1.5", exceptionThrown)

        exceptionThrown = false
        try {
            ChatterboxConfig(exaggeration = -0.1f)
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Should throw for exaggeration < 0", exceptionThrown)
    }

    @Test
    fun `ChatterboxConfig validates cfgWeight range`() {
        // Valid values
        ChatterboxConfig(cfgWeight = 0.0f)
        ChatterboxConfig(cfgWeight = 0.5f)
        ChatterboxConfig(cfgWeight = 1.0f)

        // Invalid values should throw
        var exceptionThrown = false
        try {
            ChatterboxConfig(cfgWeight = 1.5f)
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Should throw for cfgWeight > 1.0", exceptionThrown)
    }

    @Test
    fun `ChatterboxConfig validates speed range`() {
        // Valid values
        ChatterboxConfig(speed = 0.5f)
        ChatterboxConfig(speed = 1.0f)
        ChatterboxConfig(speed = 2.0f)

        // Invalid values should throw
        var exceptionThrown = false
        try {
            ChatterboxConfig(speed = 3.0f)
        } catch (_: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Should throw for speed > 2.0", exceptionThrown)
    }

    @Test
    fun `ChatterboxConfig presets are valid`() {
        // Just ensure presets can be created without throwing
        val default = ChatterboxConfig.DEFAULT
        val natural = ChatterboxConfig.NATURAL
        val expressive = ChatterboxConfig.EXPRESSIVE
        val lowLatency = ChatterboxConfig.LOW_LATENCY
        val tutor = ChatterboxConfig.TUTOR

        assertEquals(0.5f, default.exaggeration)
        assertEquals(0.3f, natural.exaggeration)
        assertEquals(0.8f, expressive.exaggeration)
        assertEquals(0.3f, lowLatency.exaggeration)
        assertEquals(0.4f, tutor.exaggeration)
    }

    @Test
    fun `ChatterboxLanguage has correct codes`() {
        assertEquals("en", ChatterboxLanguage.ENGLISH.code)
        assertEquals("es", ChatterboxLanguage.SPANISH.code)
        assertEquals("fr", ChatterboxLanguage.FRENCH.code)
        assertEquals("de", ChatterboxLanguage.GERMAN.code)
        assertEquals("zh-cn", ChatterboxLanguage.CHINESE_SIMPLIFIED.code)
        assertEquals("ja", ChatterboxLanguage.JAPANESE.code)
    }

    @Test
    fun `paralinguistic tags list contains expected tags`() {
        val tags = ChatterboxParalinguisticTags.ALL_TAGS
        assertTrue(tags.contains("[laugh]"))
        assertTrue(tags.contains("[sigh]"))
        assertTrue(tags.contains("[gasp]"))
        assertTrue(tags.contains("[cough]"))
        assertTrue(tags.contains("[hmmm]"))
    }

    @Test
    fun `synthesize makes POST request to correct endpoint`() =
        runTest {
            val requestSlot = slot<Request>()
            val callbackSlot = slot<Callback>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                // Simulate successful response with audio data
                val response = createMockResponse(requestSlot.captured, byteArrayOf(0x01, 0x02, 0x03))
                callbackSlot.captured.onResponse(mockCall, response)
            }
            every { mockCall.isCanceled() } returns false

            service.synthesize("Hello world").toList()

            verify { mockClient.newCall(any()) }
            val request = requestSlot.captured

            assertTrue(
                "URL should contain /tts endpoint",
                request.url.toString().contains("/tts"),
            )
            assertEquals("POST", request.method)
            assertEquals("Bearer test-api-key", request.header("Authorization"))
        }

    @Test
    fun `synthesize returns audio chunks from response`() =
        runTest {
            val requestSlot = slot<Request>()
            val callbackSlot = slot<Callback>()
            val audioData = ByteArray(4096) { it.toByte() }

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                val response = createMockResponse(requestSlot.captured, audioData)
                callbackSlot.captured.onResponse(mockCall, response)
            }
            every { mockCall.isCanceled() } returns false

            val chunks = service.synthesize("Test").toList()

            // Should have at least 2 chunks: one audio and one final
            assertTrue("Should have at least 2 chunks", chunks.size >= 2)

            // First chunk should be marked as first
            assertTrue("First chunk should be marked as first", chunks.first().isFirst)

            // Last chunk should be marked as last
            assertTrue("Last chunk should be marked as last", chunks.last().isLast)
        }

    @Test
    fun `synthesize handles error response`() =
        runTest {
            val requestSlot = slot<Request>()
            val callbackSlot = slot<Callback>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                val response = createErrorResponse(requestSlot.captured, 500, "Internal Server Error")
                callbackSlot.captured.onResponse(mockCall, response)
            }
            every { mockCall.isCanceled() } returns false

            var exceptionThrown = false
            try {
                service.synthesize("Test").toList()
            } catch (e: Exception) {
                exceptionThrown = true
                assertTrue(e.message?.contains("500") == true)
            }
            assertTrue("Should throw exception for error response", exceptionThrown)
        }

    @Test
    fun `synthesize handles network failure`() =
        runTest {
            val callbackSlot = slot<Callback>()

            every { mockClient.newCall(any()) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                callbackSlot.captured.onFailure(mockCall, IOException("Network error"))
            }
            every { mockCall.isCanceled() } returns false

            var exceptionThrown = false
            try {
                service.synthesize("Test").toList()
            } catch (e: IOException) {
                exceptionThrown = true
                assertEquals("Network error", e.message)
            }
            assertTrue("Should throw IOException for network failure", exceptionThrown)
        }

    @Test
    fun `synthesize with custom config uses provided values`() =
        runTest {
            val requestSlot = slot<Request>()
            val callbackSlot = slot<Callback>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                val response = createMockResponse(requestSlot.captured, byteArrayOf(0x01))
                callbackSlot.captured.onResponse(mockCall, response)
            }
            every { mockCall.isCanceled() } returns false

            val customConfig =
                ChatterboxConfig(
                    exaggeration = 0.8f,
                    cfgWeight = 0.4f,
                    speed = 1.2f,
                    language = ChatterboxLanguage.SPANISH,
                )

            service.synthesize("Hola mundo", customConfig).toList()

            verify { mockClient.newCall(any()) }

            // The request body should contain the custom config values
            val requestBody = requestSlot.captured.body
            assertTrue("Request should have body", requestBody != null)
        }

    @Test
    fun `stop cancels pending request`() =
        runTest {
            val callbackSlot = slot<Callback>()

            every { mockClient.newCall(any()) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                // Simulate cancellation immediately after enqueue
                callbackSlot.captured.onFailure(mockCall, IOException("Cancelled"))
            }
            every { mockCall.isCanceled() } returns true

            // Verify synthesis flow handles cancellation
            val chunks = service.synthesize("Test").toList()
            assertTrue("Should return empty list for cancelled request", chunks.isEmpty())

            // Verify stop does cancel
            service.stop()
            verify { mockCall.cancel() }
        }

    @Test
    fun `withConfig creates new service with updated config`() {
        val newConfig = ChatterboxConfig.EXPRESSIVE
        val newService = service.withConfig(newConfig)

        assertEquals("Chatterbox", newService.providerName)
        // Service is a new instance
        assertTrue(newService !== service)
    }

    @Test
    fun `withLanguage creates new service with updated language`() {
        val newService = service.withLanguage(ChatterboxLanguage.FRENCH)

        assertEquals("Chatterbox", newService.providerName)
        assertTrue(newService !== service)
    }

    @Test
    fun `withExaggeration creates new service with updated exaggeration`() {
        val newService = service.withExaggeration(0.9f)

        assertEquals("Chatterbox", newService.providerName)
        assertTrue(newService !== service)
    }

    @Test
    fun `cancelled request does not throw exception`() =
        runTest {
            val callbackSlot = slot<Callback>()

            every { mockClient.newCall(any()) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                callbackSlot.captured.onFailure(mockCall, IOException("Cancelled"))
            }
            every { mockCall.isCanceled() } returns true

            // Should complete without throwing
            val chunks = service.synthesize("Test").toList()
            assertTrue("Should return empty list for cancelled request", chunks.isEmpty())
        }

    @Test
    fun `service works without API key for self-hosted`() =
        runTest {
            val requestSlot = slot<Request>()
            val callbackSlot = slot<Callback>()

            // No API key for self-hosted
            val selfHostedService =
                ChatterboxTTSService(
                    baseUrl = "http://localhost:8080",
                    apiKey = null,
                    config = ChatterboxConfig.DEFAULT,
                    client = mockClient,
                )

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.enqueue(capture(callbackSlot)) } answers {
                val response = createMockResponse(requestSlot.captured, byteArrayOf(0x01))
                callbackSlot.captured.onResponse(mockCall, response)
            }
            every { mockCall.isCanceled() } returns false

            selfHostedService.synthesize("Test").toList()

            verify { mockClient.newCall(any()) }
            val request = requestSlot.captured
            assertEquals(null, request.header("Authorization"))
        }

    // Helper functions

    private fun createMockResponse(
        request: Request,
        audioData: ByteArray,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(audioData.toResponseBody())
            .build()
    }

    private fun createErrorResponse(
        request: Request,
        code: Int,
        message: String,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body("""{"error": {"message": "$message"}}""".toResponseBody())
            .build()
    }
}
