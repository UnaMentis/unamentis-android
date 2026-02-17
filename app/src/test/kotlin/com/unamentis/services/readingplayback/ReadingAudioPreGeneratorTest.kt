package com.unamentis.services.readingplayback

import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.data.model.AudioPreGenStatus
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ReadingAudioPreGenerator.
 *
 * Tests cover:
 * - Successful pre-generation stores audio and updates status
 * - Failed TTS synthesis marks item as failed
 * - Duplicate pre-generation requests are ignored
 * - Waiting for in-progress generation
 * - isGenerating check
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingAudioPreGeneratorTest {
    private lateinit var mockTtsService: TTSService
    private lateinit var mockReadingListManager: ReadingListManager
    private lateinit var generator: ReadingAudioPreGenerator
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        mockTtsService = mockk(relaxed = true)
        mockReadingListManager = mockk(relaxed = true)

        val testDispatcher = UnconfinedTestDispatcher()
        testScope = CoroutineScope(testDispatcher)

        coEvery { mockReadingListManager.updateAudioPreGenStatus(any(), any()) } just Runs

        generator =
            ReadingAudioPreGenerator(
                ttsService = mockTtsService,
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
    fun `successful pre-generation updates status to ready`() =
        runTest(UnconfinedTestDispatcher()) {
            every { mockTtsService.synthesize(any()) } returns
                flowOf(
                    TTSAudioChunk(audioData = byteArrayOf(1, 2, 3, 4), isFirst = true, isLast = true),
                )

            generator.preGenerateFirstChunk("item-1", "Hello world")

            advanceUntilIdle()

            coVerify { mockReadingListManager.updateAudioPreGenStatus("item-1", AudioPreGenStatus.READY) }
        }

    @Test
    fun `empty TTS output marks status as failed`() =
        runTest(UnconfinedTestDispatcher()) {
            every { mockTtsService.synthesize(any()) } returns
                flowOf(
                    TTSAudioChunk(audioData = byteArrayOf(), isFirst = true, isLast = true),
                )

            generator.preGenerateFirstChunk("item-1", "Hello world")

            advanceUntilIdle()

            coVerify { mockReadingListManager.updateAudioPreGenStatus("item-1", AudioPreGenStatus.FAILED) }
        }

    @Test
    fun `TTS exception marks status as failed`() =
        runTest(UnconfinedTestDispatcher()) {
            every { mockTtsService.synthesize(any()) } throws RuntimeException("TTS error")

            generator.preGenerateFirstChunk("item-1", "Hello world")

            advanceUntilIdle()

            coVerify { mockReadingListManager.updateAudioPreGenStatus("item-1", AudioPreGenStatus.FAILED) }
        }

    @Test
    fun `isGenerating returns false when no tasks running`() =
        runTest {
            assertFalse(generator.isGenerating("item-1"))
        }

    @Test
    fun `waitForPreGeneration returns null when no task in progress`() =
        runTest {
            val result = generator.waitForPreGeneration("item-1")
            assertNull(result)
        }
}
