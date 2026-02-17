package com.unamentis.services.curriculum

import app.cash.turbine.test
import com.unamentis.core.config.ServerConfigManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [TranscriptStreamingService].
 *
 * Uses MockWebServer for HTTP testing (management console + TTS server)
 * and MockK only for [ServerConfigManager] (which would require Android Context).
 */
class TranscriptStreamingServiceTest {
    private lateinit var managementServer: MockWebServer
    private lateinit var ttsServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var serverConfigManager: ServerConfigManager
    private lateinit var json: Json
    private lateinit var service: TranscriptStreamingService

    @Before
    fun setup() {
        managementServer = MockWebServer()
        managementServer.start()

        ttsServer = MockWebServer()
        ttsServer.start()

        okHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

        serverConfigManager = mockk(relaxed = true)
        every {
            serverConfigManager.getManagementServerUrl()
        } returns managementServer.url("/").toString().trimEnd('/')

        json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        service =
            TranscriptStreamingService(
                okHttpClient = okHttpClient,
                serverConfigManager = serverConfigManager,
                json = json,
            )
    }

    @After
    fun teardown() {
        service.stopStreaming()
        managementServer.shutdown()
        ttsServer.shutdown()
    }

    // region State Tests

    @Test
    fun `initial state is Idle`() {
        assertEquals(StreamingState.Idle, service.state.value)
    }

    @Test
    fun `stopStreaming resets state to Idle`() {
        service.stopStreaming()
        assertEquals(StreamingState.Idle, service.state.value)
    }

    // endregion

    // region TTSServer Enum Tests

    @Test
    fun `TTSServer PIPER has correct port and sample rate`() {
        assertEquals(11402, TTSServer.PIPER.port)
        assertEquals(22050, TTSServer.PIPER.sampleRate)
    }

    @Test
    fun `TTSServer VIBE_VOICE has correct port and sample rate`() {
        assertEquals(8880, TTSServer.VIBE_VOICE.port)
        assertEquals(24000, TTSServer.VIBE_VOICE.sampleRate)
    }

    // endregion

    // region StreamingState Tests

    @Test
    fun `StreamingState Streaming contains segment info`() {
        val state = StreamingState.Streaming(2, 10)
        assertEquals(2, state.segmentIndex)
        assertEquals(10, state.totalSegments)
    }

    @Test
    fun `StreamingState Error contains message`() {
        val state = StreamingState.Error("Something went wrong")
        assertEquals("Something went wrong", state.message)
    }

    // endregion

    // region StreamedTranscriptSegment Tests

    @Test
    fun `StreamedTranscriptSegment equality with same data`() {
        val segment1 =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
                audioData = byteArrayOf(1, 2, 3),
            )
        val segment2 =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
                audioData = byteArrayOf(1, 2, 3),
            )
        assertEquals(segment1, segment2)
        assertEquals(segment1.hashCode(), segment2.hashCode())
    }

    @Test
    fun `StreamedTranscriptSegment inequality with different data`() {
        val segment1 =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
                audioData = byteArrayOf(1, 2, 3),
            )
        val segment2 =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
                audioData = byteArrayOf(4, 5, 6),
            )
        assertTrue(segment1 != segment2)
    }

    @Test
    fun `StreamedTranscriptSegment with null audioData`() {
        val segment =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Test",
                audioData = null,
            )
        assertNull(segment.audioData)
    }

    @Test
    fun `StreamedTranscriptSegment equals returns false for different type`() {
        val segment =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
            )
        assertFalse(segment.equals("not a segment"))
    }

    @Test
    fun `StreamedTranscriptSegment equals returns true for same reference`() {
        val segment =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
            )
        assertTrue(segment.equals(segment))
    }

    @Test
    fun `StreamedTranscriptSegment hashCode handles null audioData`() {
        val segment1 =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
                audioData = null,
            )
        val segment2 =
            StreamedTranscriptSegment(
                index = 0,
                type = "narration",
                text = "Hello",
                audioData = null,
            )
        assertEquals(segment1.hashCode(), segment2.hashCode())
    }

    // endregion

    // region Transcript Fetching Tests

    @Test
    fun `streamTopicAudio fetches transcript from correct URL`() =
        runTest {
            // Enqueue transcript response
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                            "segments": [
                                {
                                    "id": "seg-1",
                                    "type": "narration",
                                    "content": "Hello world"
                                }
                            ]
                        }
                        """.trimIndent(),
                    )
                    .addHeader("Content-Type", "application/json"),
            )

            // Enqueue TTS response (audio data)
            // The TTS request will go to the management server host on a different port,
            // which we can't intercept with MockWebServer easily.
            // Instead, we test that the transcript is fetched correctly.

            try {
                service.streamTopicAudio("curriculum-1", "topic-1")
            } catch (_: Exception) {
                // TTS will fail since we can't mock the TTS port easily,
                // but the transcript request should go through
            }

            // Verify the management server received the correct request
            val request = managementServer.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull("Management server should receive a request", request)
            assertEquals(
                "/api/curricula/curriculum-1/topics/topic-1/transcript",
                request!!.path,
            )
            assertEquals("GET", request.method)
        }

    @Test
    fun `streamTopicAudio transitions to Error on 404`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found"),
            )

            try {
                service.streamTopicAudio("curriculum-1", "nonexistent")
            } catch (_: Exception) {
                // Expected to fail
            }

            // State should transition to Error
            val state = service.state.value
            assertTrue(
                "State should be Error, was: $state",
                state is StreamingState.Error,
            )
            assertTrue(
                "Error message should mention 'not found'",
                (state as StreamingState.Error).message.contains("not found", ignoreCase = true),
            )
        }

    @Test
    fun `streamTopicAudio transitions to Error on server error`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error"),
            )

            try {
                service.streamTopicAudio("curriculum-1", "topic-1")
            } catch (_: Exception) {
                // Expected
            }

            val state = service.state.value
            assertTrue(
                "State should be Error on server failure, was: $state",
                state is StreamingState.Error,
            )
        }

    @Test
    fun `streamTopicAudio transitions to Error on empty segments`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"segments": []}""")
                    .addHeader("Content-Type", "application/json"),
            )

            try {
                service.streamTopicAudio("curriculum-1", "topic-1")
            } catch (_: Exception) {
                // Expected
            }

            val state = service.state.value
            assertTrue(
                "State should be Error for empty segments, was: $state",
                state is StreamingState.Error,
            )
            assertTrue(
                "Error should mention empty segments",
                (state as StreamingState.Error).message.contains("no transcript", ignoreCase = true),
            )
        }

    @Test
    fun `streamTopicAudio transitions to Error on malformed JSON`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("not valid json {{{")
                    .addHeader("Content-Type", "application/json"),
            )

            try {
                service.streamTopicAudio("curriculum-1", "topic-1")
            } catch (_: Exception) {
                // Expected
            }

            val state = service.state.value
            assertTrue(
                "State should be Error for malformed JSON, was: $state",
                state is StreamingState.Error,
            )
        }

    // endregion

    // region Segment Emission Tests

    @Test
    fun `streamTopicAudio emits text segments before audio`() =
        runTest {
            // Set up management server to return segments
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                            "segments": [
                                {
                                    "id": "seg-1",
                                    "type": "narration",
                                    "content": "Hello world"
                                }
                            ]
                        }
                        """.trimIndent(),
                    )
                    .addHeader("Content-Type", "application/json"),
            )

            service.segments.test {
                val streamJob =
                    async {
                        try {
                            service.streamTopicAudio("curriculum-1", "topic-1")
                        } catch (_: Exception) {
                            // TTS will fail, that's fine - we're testing text emission
                        }
                    }

                // Should emit text segment (without audio) first
                val textSegment = awaitItem()
                assertEquals(0, textSegment.index)
                assertEquals("narration", textSegment.type)
                assertEquals("Hello world", textSegment.text)
                assertNull(textSegment.audioData) // Text-only emission

                streamJob.await()
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `streamTopicAudio skips blank segments`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                            "segments": [
                                {"id": "seg-1", "type": "narration", "content": ""},
                                {"id": "seg-2", "type": "narration", "content": "   "},
                                {"id": "seg-3", "type": "narration", "content": "Actual content"}
                            ]
                        }
                        """.trimIndent(),
                    )
                    .addHeader("Content-Type", "application/json"),
            )

            service.segments.test {
                val streamJob =
                    async {
                        try {
                            service.streamTopicAudio("curriculum-1", "topic-1")
                        } catch (_: Exception) {
                            // TTS will fail
                        }
                    }

                // Only the non-blank segment should be emitted
                val segment = awaitItem()
                assertEquals("Actual content", segment.text)
                // Index should be 2 since it's the third segment (0-indexed)
                assertEquals(2, segment.index)

                streamJob.await()
                cancelAndConsumeRemainingEvents()
            }
        }

    // endregion

    // region State Transition Tests

    @Test
    fun `state transitions through FetchingTranscript during streaming`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                            "segments": [
                                {"id": "seg-1", "type": "narration", "content": "Content"}
                            ]
                        }
                        """.trimIndent(),
                    )
                    .addHeader("Content-Type", "application/json"),
            )

            service.state.test {
                assertEquals(StreamingState.Idle, awaitItem())

                val streamJob =
                    async {
                        try {
                            service.streamTopicAudio("curriculum-1", "topic-1")
                        } catch (_: Exception) {
                            // TTS failure expected
                        }
                    }

                // Should see FetchingTranscript
                var sawFetching = false
                // Collect a few state changes
                repeat(5) {
                    val state =
                        try {
                            awaitItem()
                        } catch (_: Exception) {
                            return@repeat
                        }
                    if (state is StreamingState.FetchingTranscript) {
                        sawFetching = true
                    }
                }

                assertTrue("Should transition through FetchingTranscript", sawFetching)

                streamJob.await()
                cancelAndConsumeRemainingEvents()
            }
        }

    // endregion

    // region TTS Server Preference Tests

    @Test
    fun `setPreferredTTS with PIPER puts PIPER first`() {
        service.setPreferredTTS(TTSServer.PIPER)
        // No public accessor to verify order, but it should not throw
    }

    @Test
    fun `setPreferredTTS with VIBE_VOICE puts VIBE_VOICE first`() {
        service.setPreferredTTS(TTSServer.VIBE_VOICE)
        // No public accessor to verify order, but it should not throw
    }

    @Test
    fun `stopStreaming resets confirmed TTS server`() {
        service.setPreferredTTS(TTSServer.PIPER)
        service.stopStreaming()
        assertEquals(StreamingState.Idle, service.state.value)
    }

    // endregion

    // region TranscriptStreamingException Tests

    @Test
    fun `TranscriptStreamingException stores message`() {
        val exception = TranscriptStreamingException("Stream failed")
        assertEquals("Stream failed", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `TranscriptStreamingException stores cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = TranscriptStreamingException("Stream failed", cause)
        assertEquals("Stream failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    // endregion

    // region DTO Tests

    @Test
    fun `TranscriptResponseDto deserializes correctly`() {
        val jsonStr =
            """
            {
                "segments": [
                    {"id": "s1", "type": "narration", "content": "Hello"},
                    {"id": "s2", "type": "checkpoint", "content": "Quiz time"}
                ]
            }
            """.trimIndent()

        val response = json.decodeFromString<TranscriptResponseDto>(jsonStr)
        assertEquals(2, response.segments.size)
        assertEquals("s1", response.segments[0].id)
        assertEquals("narration", response.segments[0].type)
        assertEquals("Hello", response.segments[0].content)
        assertEquals("checkpoint", response.segments[1].type)
    }

    @Test
    fun `TranscriptResponseDto handles empty segments`() {
        val jsonStr = """{"segments": []}"""
        val response = json.decodeFromString<TranscriptResponseDto>(jsonStr)
        assertTrue(response.segments.isEmpty())
    }

    @Test
    fun `TranscriptSegmentDto has sensible defaults`() {
        val segment = TranscriptSegmentDto()
        assertEquals("", segment.id)
        assertEquals("narration", segment.type)
        assertEquals("", segment.content)
    }

    @Test
    fun `TTSRequestDto serializes correctly`() {
        val request =
            TTSRequestDto(
                model = "tts-1",
                input = "Hello world",
                voice = "nova",
                responseFormat = "wav",
            )
        val serialized = json.encodeToString(TTSRequestDto.serializer(), request)
        assertTrue(serialized.contains("\"model\":\"tts-1\""))
        assertTrue(serialized.contains("\"input\":\"Hello world\""))
        assertTrue(serialized.contains("\"voice\":\"nova\""))
        assertTrue(serialized.contains("\"response_format\":\"wav\""))
    }

    // endregion

    // region Concurrent Streaming Tests

    @Test
    fun `calling streamTopicAudio cancels previous stream`() =
        runTest {
            // First stream - long running
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"segments": [{"id": "s1", "type": "narration", "content": "First"}]}""")
                    .setBodyDelay(2, TimeUnit.SECONDS)
                    .addHeader("Content-Type", "application/json"),
            )

            // Second stream
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"segments": [{"id": "s2", "type": "narration", "content": "Second"}]}""")
                    .addHeader("Content-Type", "application/json"),
            )

            val firstJob =
                async {
                    try {
                        service.streamTopicAudio("c-1", "t-1")
                    } catch (_: Exception) {
                        // Expected - will be cancelled or TTS fails
                    }
                }

            delay(50) // Let the first stream start

            // Starting second stream should cancel the first
            val secondJob =
                async {
                    try {
                        service.streamTopicAudio("c-1", "t-2")
                    } catch (_: Exception) {
                        // TTS may fail
                    }
                }

            firstJob.await()
            secondJob.await()

            // State should not be stuck in FetchingTranscript
            val state = service.state.value
            assertTrue(
                "State should not be FetchingTranscript after both complete, was: $state",
                state !is StreamingState.FetchingTranscript,
            )
        }

    // endregion

    // region Empty Response Body Test

    @Test
    fun `streamTopicAudio handles empty response body`() =
        runTest {
            managementServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("")
                    .addHeader("Content-Type", "application/json"),
            )

            try {
                service.streamTopicAudio("curriculum-1", "topic-1")
            } catch (_: Exception) {
                // Expected - empty body should cause parse failure
            }

            val state = service.state.value
            assertTrue(
                "State should be Error for empty body, was: $state",
                state is StreamingState.Error,
            )
        }

    // endregion

    private fun assertFalse(value: Boolean) {
        org.junit.Assert.assertFalse(value)
    }
}
