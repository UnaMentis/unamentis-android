package com.unamentis.services.tts

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DeepgramAuraTTSService.
 */
class DeepgramAuraTTSServiceTest {
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockWebSocket: WebSocket
    private lateinit var mockResponse: Response
    private lateinit var service: DeepgramAuraTTSService

    @Before
    fun setup() {
        mockClient = mockk(relaxed = true)
        mockWebSocket = mockk(relaxed = true)
        mockResponse = mockk(relaxed = true)

        service =
            DeepgramAuraTTSService(
                apiKey = "test-api-key",
                voice = "aura-asteria-en",
                client = mockClient,
            )
    }

    @Test
    fun `provider name is DeepgramAura`() {
        assertEquals("DeepgramAura", service.providerName)
    }

    @Test
    fun `synthesize opens WebSocket with correct URL`() =
        runTest {
            val requestSlot = slot<Request>()
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(capture(requestSlot), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                // Simulate connection and immediate completion
                listener.onOpen(mockWebSocket, mockResponse)
                listener.onMessage(mockWebSocket, byteArrayOf(0x01).toByteString())
                listener.onMessage(mockWebSocket, """{"type": "Flushed"}""")
                mockWebSocket
            }

            // Collect flow to trigger execution
            service.synthesize("Hello world").toList()

            // Verify the request URL contains expected parameters
            verify { mockClient.newWebSocket(any(), any()) }
            val url = requestSlot.captured.url.toString()
            assertTrue("URL should contain model parameter", url.contains("model=aura-asteria-en"))
            assertTrue("URL should contain encoding parameter", url.contains("encoding=linear16"))
            assertTrue("URL should contain sample_rate parameter", url.contains("sample_rate=24000"))
        }

    @Test
    fun `synthesize includes authorization header`() =
        runTest {
            val requestSlot = slot<Request>()
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(capture(requestSlot), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)
                listener.onMessage(mockWebSocket, byteArrayOf(0x01).toByteString())
                listener.onMessage(mockWebSocket, """{"type": "Flushed"}""")
                mockWebSocket
            }

            service.synthesize("Test").toList()

            verify { mockClient.newWebSocket(any(), any()) }
            val authHeader = requestSlot.captured.header("Authorization")
            assertEquals("Token test-api-key", authHeader)
        }

    @Test
    fun `withVoice factory creates service with specified voice`() {
        val service =
            DeepgramAuraTTSService.withVoice(
                apiKey = "test-key",
                voiceName = "orion",
                client = mockClient,
            )

        assertEquals("DeepgramAura", service.providerName)
    }

    @Test
    fun `VOICES map contains expected voices`() {
        assertTrue(DeepgramAuraTTSService.VOICES.containsKey("aura-asteria-en"))
        assertTrue(DeepgramAuraTTSService.VOICES.containsKey("aura-orion-en"))
        assertTrue(DeepgramAuraTTSService.VOICES.containsKey("aura-luna-en"))
        assertTrue(DeepgramAuraTTSService.VOICES.size >= 10)
    }

    @Test
    fun `audio chunks flow correctly from WebSocket`() =
        runTest {
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(any(), capture(listenerSlot))
            } answers {
                // Simulate WebSocket behavior
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)

                // Simulate receiving audio chunks
                val audioData1 = byteArrayOf(0x01, 0x02, 0x03)
                val audioData2 = byteArrayOf(0x04, 0x05, 0x06)
                listener.onMessage(mockWebSocket, audioData1.toByteString())
                listener.onMessage(mockWebSocket, audioData2.toByteString())

                // Simulate completion
                listener.onMessage(mockWebSocket, """{"type": "Flushed"}""")

                mockWebSocket
            }

            val chunks = service.synthesize("Test").toList()

            assertEquals(3, chunks.size)

            // First chunk should be marked as first
            assertTrue(chunks[0].isFirst)
            assertFalse(chunks[0].isLast)

            // Middle chunk should be neither
            assertFalse(chunks[1].isFirst)
            assertFalse(chunks[1].isLast)

            // Last chunk should be marked as last
            assertFalse(chunks[2].isFirst)
            assertTrue(chunks[2].isLast)
        }

    @Test
    fun `stop closes WebSocket gracefully`() =
        runTest {
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(any(), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)
                // Send one chunk to start the flow
                listener.onMessage(mockWebSocket, byteArrayOf(0x01).toByteString())
                mockWebSocket
            }

            // Start collecting in background, then stop
            val job =
                launch {
                    service.synthesize("Test").first() // Get at least one chunk
                }
            job.join()
            service.stop()

            verify { mockWebSocket.close(1000, "Client closing") }
        }
}
