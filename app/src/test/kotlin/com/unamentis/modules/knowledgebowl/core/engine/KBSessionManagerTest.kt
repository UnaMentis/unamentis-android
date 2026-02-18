package com.unamentis.modules.knowledgebowl.core.engine

import android.content.Context
import com.unamentis.modules.knowledgebowl.core.stats.KBSessionStore
import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionAttempt
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for KBSessionManager.
 */
class KBSessionManagerTest {
    private lateinit var sessionManager: KBSessionManager
    private lateinit var sessionStore: KBSessionStore
    private lateinit var tempDir: File

    private val testConfig =
        KBSessionConfig(
            region = KBRegion.COLORADO,
            roundType = KBRoundType.WRITTEN,
            questionCount = 3,
        )

    private val testQuestions =
        listOf(
            KBQuestion(
                id = "q1",
                text = "What is the chemical symbol for gold?",
                answer = KBAnswer(primary = "Au"),
                domain = KBDomain.SCIENCE,
            ),
            KBQuestion(
                id = "q2",
                text = "What is 2 + 2?",
                answer = KBAnswer(primary = "4"),
                domain = KBDomain.MATHEMATICS,
            ),
            KBQuestion(
                id = "q3",
                text = "Who wrote Hamlet?",
                answer = KBAnswer(primary = "Shakespeare"),
                domain = KBDomain.LITERATURE,
            ),
        )

    @Before
    fun setUp() {
        tempDir = createTempDir("kb_session_mgr_test")
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.filesDir } returns tempDir
        sessionStore = KBSessionStore(mockContext)
        sessionManager = KBSessionManager(sessionStore)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `startSession creates session`() =
        runTest {
            val session = sessionManager.startSession(testQuestions, testConfig)

            assertNotNull(session)
            assertEquals(testConfig.region, session.config.region)
            assertEquals(testConfig.roundType, session.config.roundType)
        }

    @Test
    fun `getCurrentSession returns active session`() =
        runTest {
            val session = sessionManager.startSession(testQuestions, testConfig)

            val current = sessionManager.getCurrentSession()
            assertNotNull(current)
            assertEquals(session.id, current!!.id)
        }

    @Test
    fun `getCurrentSession returns null when no session`() =
        runTest {
            assertNull(sessionManager.getCurrentSession())
        }

    @Test
    fun `getCurrentQuestion returns first question`() =
        runTest {
            sessionManager.startSession(testQuestions, testConfig)

            val question = sessionManager.getCurrentQuestion()
            assertNotNull(question)
            assertEquals("q1", question!!.id)
        }

    @Test
    fun `advanceToNextQuestion moves to next question`() =
        runTest {
            sessionManager.startSession(testQuestions, testConfig)

            val next = sessionManager.advanceToNextQuestion()
            assertNotNull(next)
            assertEquals("q2", next!!.id)
        }

    @Test
    fun `isLastQuestion returns true at end`() =
        runTest {
            sessionManager.startSession(testQuestions, testConfig)

            // Advance to last question (index 2)
            sessionManager.advanceToNextQuestion() // index 1
            sessionManager.advanceToNextQuestion() // index 2

            assertTrue(sessionManager.isLastQuestion())
        }

    @Test
    fun `getProgress tracks correctly`() =
        runTest {
            sessionManager.startSession(testQuestions, testConfig)

            // Record one attempt
            sessionManager.recordAttempt(
                KBQuestionAttempt(
                    id = "a1",
                    questionId = "q1",
                    domain = KBDomain.SCIENCE,
                    responseTimeSeconds = 3.0f,
                    wasCorrect = true,
                    pointsEarned = 1,
                    roundType = KBRoundType.WRITTEN,
                ),
            )

            val progress = sessionManager.getProgress()
            assertEquals(1.0 / 3.0, progress, 0.01)
        }

    @Test
    fun `completeSession persists session`() =
        runTest {
            val session = sessionManager.startSession(testQuestions, testConfig)

            // Record an attempt
            sessionManager.recordAttempt(
                KBQuestionAttempt(
                    id = "a1",
                    questionId = "q1",
                    domain = KBDomain.SCIENCE,
                    responseTimeSeconds = 3.0f,
                    wasCorrect = true,
                    pointsEarned = 1,
                    roundType = KBRoundType.WRITTEN,
                ),
            )

            sessionManager.completeSession()

            // Verify the session was persisted
            val loaded = sessionStore.load(session.id)
            assertNotNull(loaded)
            assertTrue(loaded!!.isComplete)
            assertEquals(1, loaded.attempts.size)
        }

    @Test(expected = KBSessionError.NoActiveSession::class)
    fun `completeSession throws when no active session`() =
        runTest {
            sessionManager.completeSession()
        }

    @Test
    fun `cancelSession clears current session`() =
        runTest {
            sessionManager.startSession(testQuestions, testConfig)
            assertNotNull(sessionManager.getCurrentSession())

            sessionManager.cancelSession()
            assertNull(sessionManager.getCurrentSession())
        }

    @Test
    fun `loadRecentSessions returns stored sessions`() =
        runTest {
            val session = sessionManager.startSession(testQuestions, testConfig)

            // Complete and persist the session
            sessionManager.completeSession()

            val recent = sessionManager.loadRecentSessions()
            assertEquals(1, recent.size)
            assertEquals(session.id, recent[0].id)
        }

    @Test
    fun `calculateStatistics works after completing session`() =
        runTest {
            sessionManager.startSession(testQuestions, testConfig)

            sessionManager.recordAttempt(
                KBQuestionAttempt(
                    id = "a1",
                    questionId = "q1",
                    domain = KBDomain.SCIENCE,
                    responseTimeSeconds = 3.0f,
                    wasCorrect = true,
                    pointsEarned = 1,
                    roundType = KBRoundType.WRITTEN,
                ),
            )

            sessionManager.completeSession()

            val stats = sessionManager.calculateStatistics()
            assertEquals(1, stats.totalSessions)
            assertEquals(1, stats.totalQuestions)
            assertEquals(1, stats.totalCorrect)
        }
}
