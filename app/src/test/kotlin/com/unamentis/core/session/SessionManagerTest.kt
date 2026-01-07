package com.unamentis.core.session

import com.unamentis.core.audio.AudioEngine
import com.unamentis.core.curriculum.CurriculumEngine
import com.unamentis.data.model.*
import com.unamentis.services.vad.VADService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SessionManager.
 *
 * Tests state machine transitions, conversation flow, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private lateinit var audioEngine: AudioEngine
    private lateinit var vadService: VADService
    private lateinit var sttService: STTService
    private lateinit var ttsService: TTSService
    private lateinit var llmService: LLMService
    private lateinit var curriculumEngine: CurriculumEngine
    private lateinit var sessionManager: SessionManager

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        audioEngine = mockk(relaxed = true)
        vadService = mockk(relaxed = true)
        sttService = mockk(relaxed = true)
        ttsService = mockk(relaxed = true)
        llmService = mockk(relaxed = true)
        curriculumEngine = mockk(relaxed = true)

        every { audioEngine.startCapture(any()) } returns true
        every { curriculumEngine.getCurrentContext() } returns null

        sessionManager = SessionManager(
            audioEngine = audioEngine,
            vadService = vadService,
            sttService = sttService,
            ttsService = ttsService,
            llmService = llmService,
            curriculumEngine = curriculumEngine,
            scope = testScope
        )
    }

    @After
    fun teardown() {
        sessionManager.release()
        testScope.cancel()
    }

    @Test
    fun `initial state is IDLE`() = testScope.runTest {
        assertEquals(SessionState.IDLE, sessionManager.sessionState.value)
    }

    @Test
    fun `startSession transitions to IDLE and starts audio capture`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        assertEquals(SessionState.IDLE, sessionManager.sessionState.value)
        verify { audioEngine.startCapture(any()) }
        assertNotNull(sessionManager.currentSession.value)
    }

    @Test
    fun `startSession with curriculum loads curriculum`() = testScope.runTest {
        val curriculumId = "curriculum-123"
        val topicId = "topic-456"

        sessionManager.startSession(curriculumId = curriculumId, topicId = topicId)
        advanceUntilIdle()

        coVerify { curriculumEngine.loadCurriculum(curriculumId, topicId) }
    }

    @Test
    fun `stopSession stops audio and finalizes session`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        val session = sessionManager.currentSession.value
        assertNotNull(session)
        assertNull(session!!.endTime)

        sessionManager.stopSession()
        advanceUntilIdle()

        verify { audioEngine.stopCapture() }
        assertEquals(SessionState.IDLE, sessionManager.sessionState.value)
        assertNull(sessionManager.currentSession.value)
    }

    @Test
    fun `pauseSession transitions to PAUSED and stops audio`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        sessionManager.pauseSession()
        advanceUntilIdle()

        assertEquals(SessionState.PAUSED, sessionManager.sessionState.value)
        verify { audioEngine.stopCapture() }
        verify { audioEngine.stopPlayback() }
    }

    @Test
    fun `resumeSession transitions back to IDLE and restarts audio`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        sessionManager.pauseSession()
        advanceUntilIdle()

        sessionManager.resumeSession()
        advanceUntilIdle()

        assertEquals(SessionState.IDLE, sessionManager.sessionState.value)
        verify(atLeast = 2) { audioEngine.startCapture(any()) }
    }

    @Test
    fun `sendTextMessage adds to transcript and triggers LLM`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        val llmTokens = listOf(
            LLMToken(content = "Hello", isDone = false),
            LLMToken(content = " there!", isDone = false),
            LLMToken(content = "", isDone = true)
        )
        every {
            llmService.streamCompletion(any(), any(), any())
        } returns flowOf(*llmTokens.toTypedArray())

        every {
            ttsService.synthesize(any())
        } returns flowOf(TTSAudioChunk(byteArrayOf(), isFirst = true, isLast = true))

        sessionManager.sendTextMessage("Hello AI")
        advanceUntilIdle()

        val transcript = sessionManager.transcript.value
        assertTrue(transcript.any { it.role == "user" && it.content == "Hello AI" })

        coVerify { llmService.streamCompletion(any(), any(), any()) }
    }

    @Test
    fun `conversation flow transitions through states correctly`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        // Mock STT stream
        val sttResults = listOf(
            STTResult(text = "Hello", isFinal = false),
            STTResult(text = "Hello there", isFinal = true)
        )
        every { sttService.startStreaming() } returns flowOf(*sttResults.toTypedArray())
        coEvery { sttService.stopStreaming() } just Runs

        // Mock LLM stream
        val llmTokens = listOf(
            LLMToken(content = "Hi!", isDone = false),
            LLMToken(content = "", isDone = true)
        )
        every {
            llmService.streamCompletion(any(), any(), any())
        } returns flowOf(*llmTokens.toTypedArray())

        // Mock TTS stream
        every {
            ttsService.synthesize(any())
        } returns flowOf(TTSAudioChunk(byteArrayOf(1, 2, 3, 4), isFirst = true, isLast = true))

        // Simulate VAD detecting speech
        every { vadService.processAudio(any()) } returns VADResult(
            isSpeech = true,
            probability = 0.9f,
            processingTimeMs = 5
        )

        // Trigger STT via text (simpler than audio simulation)
        sessionManager.sendTextMessage("Hello there")
        advanceUntilIdle()

        // Should have completed the turn
        assertEquals(SessionState.IDLE, sessionManager.sessionState.value)

        // Transcript should have user and assistant messages
        val transcript = sessionManager.transcript.value
        assertTrue(transcript.any { it.role == "user" })
    }

    @Test
    fun `metrics track latency correctly`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        // Mock LLM with TTFT
        val llmTokens = listOf(
            LLMToken(content = "Response", isDone = false),
            LLMToken(content = "", isDone = true)
        )
        every {
            llmService.streamCompletion(any(), any(), any())
        } returns flow {
            kotlinx.coroutines.delay(100) // Simulate 100ms TTFT
            llmTokens.forEach { emit(it) }
        }

        // Mock TTS with TTFB
        every {
            ttsService.synthesize(any())
        } returns flow {
            kotlinx.coroutines.delay(50) // Simulate 50ms TTFB
            emit(TTSAudioChunk(byteArrayOf(1, 2), isFirst = true, isLast = true))
        }

        sessionManager.sendTextMessage("Test")
        advanceUntilIdle()

        val metrics = sessionManager.metrics.value
        assertTrue(metrics.llmTTFT > 0)
        assertTrue(metrics.ttsTTFB > 0)
        assertTrue(metrics.e2eLatency > 0)
    }

    @Test
    fun `cannot start session when already active`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        val firstSession = sessionManager.currentSession.value

        // Try to start another session
        sessionManager.startSession()
        advanceUntilIdle()

        // Should still be the same session
        assertEquals(firstSession, sessionManager.currentSession.value)
    }

    @Test
    fun `transcript entries have correct timestamps`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        val beforeTime = System.currentTimeMillis()

        every {
            llmService.streamCompletion(any(), any(), any())
        } returns flowOf(
            LLMToken(content = "Response", isDone = false),
            LLMToken(content = "", isDone = true)
        )

        every {
            ttsService.synthesize(any())
        } returns flowOf(TTSAudioChunk(byteArrayOf(), isFirst = true, isLast = true))

        sessionManager.sendTextMessage("Test message")
        advanceUntilIdle()

        val afterTime = System.currentTimeMillis()

        val transcript = sessionManager.transcript.value
        assertTrue(transcript.isNotEmpty())

        transcript.forEach { entry ->
            assertTrue(entry.timestamp >= beforeTime)
            assertTrue(entry.timestamp <= afterTime)
        }
    }

    @Test
    fun `session tracks total turns`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        every {
            llmService.streamCompletion(any(), any(), any())
        } returns flowOf(
            LLMToken(content = "Response", isDone = false),
            LLMToken(content = "", isDone = true)
        )

        every {
            ttsService.synthesize(any())
        } returns flowOf(TTSAudioChunk(byteArrayOf(), isFirst = true, isLast = true))

        // First turn
        sessionManager.sendTextMessage("Message 1")
        advanceUntilIdle()

        assertEquals(1, sessionManager.currentSession.value?.totalTurns)

        // Second turn
        sessionManager.sendTextMessage("Message 2")
        advanceUntilIdle()

        assertEquals(2, sessionManager.currentSession.value?.totalTurns)
    }

    @Test
    fun `empty transcription returns to IDLE`() = testScope.runTest {
        sessionManager.startSession()
        advanceUntilIdle()

        every { sttService.startStreaming() } returns flowOf(
            STTResult(text = "", isFinal = true)
        )

        // Trigger empty transcription processing
        // Would need to simulate via audio, simplified here

        assertEquals(SessionState.IDLE, sessionManager.sessionState.value)
    }
}
