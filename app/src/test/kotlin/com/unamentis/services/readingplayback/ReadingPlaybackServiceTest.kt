package com.unamentis.services.readingplayback

import com.unamentis.core.audio.AudioEngine
import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ReadingPlaybackService.
 *
 * Tests cover:
 * - Playback state transitions (idle → playing → paused → stopped)
 * - Chunk index tracking
 * - Position saving on pause/stop
 * - Error handling for empty chunks
 * - Bookmark creation during playback
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlaybackServiceTest {
    private lateinit var mockTtsService: TTSService
    private lateinit var mockAudioEngine: AudioEngine
    private lateinit var mockReadingListManager: ReadingListManager
    private lateinit var service: ReadingPlaybackService
    private lateinit var testScope: CoroutineScope

    private val testChunks =
        listOf(
            ReadingChunkData(index = 0, text = "First chunk of text."),
            ReadingChunkData(index = 1, text = "Second chunk of text."),
            ReadingChunkData(index = 2, text = "Third chunk of text."),
        )

    @Before
    fun setUp() {
        mockTtsService = mockk(relaxed = true)
        mockAudioEngine = mockk(relaxed = true)
        mockReadingListManager = mockk(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher()
        testScope = CoroutineScope(testDispatcher)

        // Default TTS behavior: return empty flow (completes immediately)
        every { mockTtsService.synthesize(any()) } returns
            flowOf(
                TTSAudioChunk(audioData = byteArrayOf(0, 0, 0, 0), isFirst = true, isLast = true),
            )
        every { mockAudioEngine.queuePlayback(any()) } returns true

        coEvery { mockReadingListManager.updatePosition(any(), any()) } just Runs

        service =
            ReadingPlaybackService(
                ttsService = mockTtsService,
                audioEngine = mockAudioEngine,
                readingListManager = mockReadingListManager,
                scope = testScope,
            )
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `initial state is idle`() =
        runTest {
            assertEquals(ReadingPlaybackState.Idle, service.state.first())
        }

    @Test
    fun `startPlayback updates state and item ID`() =
        runTest {
            service.startPlayback("item-1", testChunks, 0)

            assertEquals("item-1", service.currentItemId)
            assertEquals(3, service.totalChunks.value)
        }

    @Test
    fun `startPlayback with empty chunks throws`() =
        runTest {
            try {
                service.startPlayback("item-1", emptyList())
                assertTrue("Should have thrown", false)
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("No chunks"))
            }
        }

    @Test
    fun `pause transitions to paused state`() =
        runTest {
            // Use a suspending TTS flow so the playback loop doesn't complete eagerly
            every { mockTtsService.synthesize(any()) } returns
                flow { awaitCancellation() }

            service.startPlayback("item-1", testChunks)

            service.pause()

            assertEquals(ReadingPlaybackState.Paused, service.state.value)
        }

    @Test
    fun `pause saves current position`() =
        runTest {
            service.startPlayback("item-1", testChunks)

            service.pause()

            coVerify { mockReadingListManager.updatePosition("item-1", any()) }
        }

    @Test
    fun `stopPlayback resets to idle`() =
        runTest {
            service.startPlayback("item-1", testChunks)

            service.stopPlayback()

            assertEquals(ReadingPlaybackState.Idle, service.state.value)
            assertEquals(null, service.currentItemId)
        }

    @Test
    fun `stopPlayback saves position before clearing`() =
        runTest {
            service.startPlayback("item-1", testChunks)

            service.stopPlayback()

            coVerify { mockReadingListManager.updatePosition("item-1", any()) }
        }

    @Test
    fun `skipToChunk updates current index`() =
        runTest {
            service.startPlayback("item-1", testChunks)

            service.skipToChunk(2)

            assertEquals(2, service.currentChunkIndex.value)
        }

    @Test
    fun `skipToChunk with invalid index throws`() =
        runTest {
            service.startPlayback("item-1", testChunks)

            try {
                service.skipToChunk(10)
                assertTrue("Should have thrown", false)
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("Invalid chunk index"))
            }
        }

    @Test
    fun `skipForward advances chunk index`() =
        runTest {
            service.startPlayback("item-1", testChunks, startIndex = 0)

            service.skipForward(2)

            assertEquals(2, service.currentChunkIndex.value)
        }

    @Test
    fun `skipBackward decrements chunk index`() =
        runTest {
            // Use a suspending TTS flow so the playback loop doesn't advance past startIndex
            every { mockTtsService.synthesize(any()) } returns
                flow { awaitCancellation() }

            service.startPlayback("item-1", testChunks, startIndex = 2)

            service.skipBackward(1)

            assertEquals(1, service.currentChunkIndex.value)
        }

    @Test
    fun `skipForward clamps to last chunk`() =
        runTest {
            service.startPlayback("item-1", testChunks, startIndex = 1)

            service.skipForward(100)

            assertEquals(2, service.currentChunkIndex.value)
        }

    @Test
    fun `skipBackward clamps to first chunk`() =
        runTest {
            service.startPlayback("item-1", testChunks, startIndex = 1)

            service.skipBackward(100)

            assertEquals(0, service.currentChunkIndex.value)
        }

    @Test
    fun `addBookmark delegates to reading list manager`() =
        runTest {
            coEvery { mockReadingListManager.addBookmark(any(), any(), any()) } returns "bookmark-1"

            service.startPlayback("item-1", testChunks)
            service.addBookmark("test note")

            coVerify { mockReadingListManager.addBookmark("item-1", any(), "test note") }
        }

    @Test
    fun `addBookmark without active item throws`() =
        runTest {
            try {
                service.addBookmark()
                assertTrue("Should have thrown", false)
            } catch (e: ReadingPlaybackError.NotConfigured) {
                // expected
            }
        }

    @Test
    fun `startPlayback clamps start index to valid range`() =
        runTest {
            // Use a suspending TTS flow so the playback loop doesn't advance past the clamped index
            every { mockTtsService.synthesize(any()) } returns
                flow { awaitCancellation() }

            service.startPlayback("item-1", testChunks, startIndex = 100)

            assertEquals(2, service.currentChunkIndex.value)
        }

    @Test
    fun `ReadingChunkData hasCachedAudio returns true when data present`() {
        val chunk =
            ReadingChunkData(
                index = 0,
                text = "test",
                cachedAudioData = byteArrayOf(1, 2, 3),
                cachedAudioSampleRate = 24000.0,
            )
        assertTrue(chunk.hasCachedAudio)
    }

    @Test
    fun `ReadingChunkData hasCachedAudio returns false when no data`() {
        val chunk = ReadingChunkData(index = 0, text = "test")
        assertTrue(!chunk.hasCachedAudio)
    }
}
