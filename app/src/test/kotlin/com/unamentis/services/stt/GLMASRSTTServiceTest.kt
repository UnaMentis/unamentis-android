package com.unamentis.services.stt

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GLMASRSTTService.
 */
class GLMASRSTTServiceTest {
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockWebSocket: WebSocket
    private lateinit var mockResponse: Response
    private lateinit var service: GLMASRSTTService

    @Before
    fun setup() {
        mockClient = mockk(relaxed = true)
        mockWebSocket = mockk(relaxed = true)
        mockResponse = mockk(relaxed = true)

        service =
            GLMASRSTTService(
                baseUrl = "wss://test.glmasr.local/v1/audio/stream",
                authToken = "test-token",
                config = GLMASRConfig.DEFAULT,
                client = mockClient,
            )
    }

    @Test
    fun `provider name is GLMASR`() {
        assertEquals("GLMASR", service.providerName)
    }

    @Test
    fun `GLMASRConfig defaults are correct`() {
        val config = GLMASRConfig.DEFAULT
        assertEquals("auto", config.language)
        assertTrue(config.interimResults)
        assertTrue(config.punctuate)
        assertEquals(3, config.reconnectAttempts)
        assertEquals(1000, config.reconnectDelayMs)
    }

    @Test
    fun `GLMASRConfig ENGLISH preset has correct language`() {
        val config = GLMASRConfig.ENGLISH
        assertEquals("en", config.language)
    }

    @Test
    fun `GLMASRConfig LOW_LATENCY has interim results disabled`() {
        val config = GLMASRConfig.LOW_LATENCY
        assertEquals(false, config.interimResults)
        assertEquals(false, config.punctuate)
    }

    @Test
    fun `startStreaming opens WebSocket with correct URL`() =
        runTest {
            val requestSlot = slot<Request>()
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(capture(requestSlot), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                // Simulate connection and immediate close
                listener.onOpen(mockWebSocket, mockResponse)
                listener.onClosed(mockWebSocket, 1000, "Test complete")
                mockWebSocket
            }

            service.startStreaming().toList()

            verify { mockClient.newWebSocket(any(), any()) }
            val url = requestSlot.captured.url.toString()
            assertTrue(url.contains("test.glmasr.local"))
        }

    @Test
    fun `startStreaming includes authorization header when token provided`() =
        runTest {
            val requestSlot = slot<Request>()
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(capture(requestSlot), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)
                listener.onClosed(mockWebSocket, 1000, "Test complete")
                mockWebSocket
            }

            service.startStreaming().toList()

            verify { mockClient.newWebSocket(any(), any()) }
            val authHeader = requestSlot.captured.header("Authorization")
            assertEquals("Bearer test-token", authHeader)
        }

    @Test
    fun `startStreaming works without auth token`() =
        runTest {
            val requestSlot = slot<Request>()
            val listenerSlot = slot<WebSocketListener>()

            val serviceWithoutAuth =
                GLMASRSTTService(
                    baseUrl = "wss://test.glmasr.local/v1/audio/stream",
                    authToken = null,
                    config = GLMASRConfig.DEFAULT,
                    client = mockClient,
                )

            every {
                mockClient.newWebSocket(capture(requestSlot), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)
                listener.onClosed(mockWebSocket, 1000, "Test complete")
                mockWebSocket
            }

            serviceWithoutAuth.startStreaming().toList()

            verify { mockClient.newWebSocket(any(), any()) }
            val authHeader = requestSlot.captured.header("Authorization")
            assertEquals(null, authHeader)
        }

    @Test
    fun `startStreaming sends start message on connection`() =
        runTest {
            val listenerSlot = slot<WebSocketListener>()
            val sentMessages = mutableListOf<String>()

            every { mockWebSocket.send(capture(slot<String>())) } answers {
                sentMessages.add(firstArg())
                true
            }

            every {
                mockClient.newWebSocket(any(), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)
                listener.onClosed(mockWebSocket, 1000, "Test complete")
                mockWebSocket
            }

            service.startStreaming().toList()

            assertTrue("Should send start message", sentMessages.isNotEmpty())
            val startMessage = sentMessages.first()
            assertTrue("Start message should contain type", startMessage.contains("\"type\":\"start\""))
            assertTrue("Start message should contain language", startMessage.contains("\"language\":\"auto\""))
        }

    @Test
    fun `partial results are emitted correctly`() =
        runTest {
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(any(), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)

                // Simulate partial result
                listener.onMessage(
                    mockWebSocket,
                    """{"type":"partial","text":"Hello","confidence":0.9,"timestamp_ms":100}""",
                )

                // Simulate final result
                listener.onMessage(
                    mockWebSocket,
                    """{"type":"final","text":"Hello world","confidence":0.95,"timestamp_ms":200}""",
                )

                listener.onClosed(mockWebSocket, 1000, "Complete")
                mockWebSocket
            }

            val results = service.startStreaming().toList()

            assertEquals(2, results.size)

            // First result should be partial
            assertEquals("Hello", results[0].text)
            assertEquals(false, results[0].isFinal)
            assertEquals(0.9f, results[0].confidence, 0.01f)

            // Second result should be final
            assertEquals("Hello world", results[1].text)
            assertEquals(true, results[1].isFinal)
            assertEquals(0.95f, results[1].confidence, 0.01f)
        }

    @Test
    fun `error messages are logged but do not emit results`() =
        runTest {
            val listenerSlot = slot<WebSocketListener>()

            every {
                mockClient.newWebSocket(any(), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)

                // Simulate error message
                listener.onMessage(
                    mockWebSocket,
                    """{"type":"error","code":"RATE_LIMIT","message":"Rate limit exceeded","recoverable":true}""",
                )

                // Simulate valid result after error
                listener.onMessage(
                    mockWebSocket,
                    """{"type":"final","text":"Test","confidence":1.0,"timestamp_ms":100}""",
                )

                listener.onClosed(mockWebSocket, 1000, "Complete")
                mockWebSocket
            }

            val results = service.startStreaming().toList()

            // Only the final result should be emitted, not the error
            assertEquals(1, results.size)
            assertEquals("Test", results[0].text)
        }

    @Test
    fun `withConfig creates new service with updated config`() {
        val newConfig = GLMASRConfig.ENGLISH
        val newService = service.withConfig(newConfig)

        assertEquals("GLMASR", newService.providerName)
        assertTrue(newService !== service)
    }

    @Test
    fun `withLanguage creates new service with updated language`() {
        val newService = service.withLanguage("es")

        assertEquals("GLMASR", newService.providerName)
        assertTrue(newService !== service)
    }

    @Test
    fun `stopStreaming sends end message and closes WebSocket`() =
        runTest {
            val listenerSlot = slot<WebSocketListener>()
            val sentMessages = mutableListOf<String>()

            every { mockWebSocket.send(capture(slot<String>())) } answers {
                sentMessages.add(firstArg())
                true
            }

            every {
                mockClient.newWebSocket(any(), capture(listenerSlot))
            } answers {
                val listener = listenerSlot.captured
                listener.onOpen(mockWebSocket, mockResponse)
                mockWebSocket
            }

            // Start streaming
            val flowJob =
                launch {
                    service.startStreaming().toList()
                }

            // Give it time to open
            delay(50)

            // Stop streaming
            service.stopStreaming()

            flowJob.cancel()

            // Verify end message was sent
            assertTrue(
                "Should send end message",
                sentMessages.any { it.contains("\"type\":\"end\"") },
            )

            verify { mockWebSocket.close(1000, "Client closing") }
        }
}
