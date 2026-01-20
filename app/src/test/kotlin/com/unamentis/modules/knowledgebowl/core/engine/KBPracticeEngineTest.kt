package com.unamentis.modules.knowledgebowl.core.engine

import com.unamentis.modules.knowledgebowl.core.validation.KBAnswerValidator
import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for KBPracticeEngine.
 */
class KBPracticeEngineTest {
    private lateinit var engine: KBPracticeEngine
    private lateinit var validator: KBAnswerValidator

    private val sampleQuestions =
        listOf(
            KBQuestion(
                id = "q1",
                text = "What is the chemical symbol for gold?",
                answer = KBAnswer(primary = "Au", acceptable = listOf("AU"), answerType = KBAnswerType.SCIENTIFIC),
                domain = KBDomain.SCIENCE,
                difficulty = KBDifficulty.VARSITY,
                mcqOptions = listOf("Au", "Ag", "Fe", "Cu"),
            ),
            KBQuestion(
                id = "q2",
                text = "What is 2 + 2?",
                answer = KBAnswer(primary = "4", acceptable = listOf("four"), answerType = KBAnswerType.NUMBER),
                domain = KBDomain.MATHEMATICS,
                difficulty = KBDifficulty.FOUNDATIONAL,
                mcqOptions = listOf("3", "4", "5", "6"),
            ),
            KBQuestion(
                id = "q3",
                text = "Who wrote Hamlet?",
                answer =
                    KBAnswer(
                        primary = "William Shakespeare",
                        acceptable = listOf("Shakespeare"),
                        answerType = KBAnswerType.PERSON,
                    ),
                domain = KBDomain.LITERATURE,
                difficulty = KBDifficulty.INTERMEDIATE,
            ),
        )

    private val defaultConfig =
        KBSessionConfig(
            region = KBRegion.COLORADO,
            roundType = KBRoundType.WRITTEN,
            questionCount = 3,
            timeLimitSeconds = null,
            studyMode = KBStudyMode.DIAGNOSTIC,
            pointsPerQuestion = 1,
        )

    @Before
    fun setup() {
        validator = KBAnswerValidator()
        engine = KBPracticeEngine(validator)
    }

    // Session lifecycle tests

    @Test
    fun `initial state is NOT_STARTED`() {
        assertEquals(KBPracticeState.NOT_STARTED, engine.sessionState.value)
    }

    @Test
    fun `startSession initializes session`() {
        engine.startSession(sampleQuestions, defaultConfig)

        assertEquals(KBPracticeState.IN_PROGRESS, engine.sessionState.value)
        assertNotNull(engine.session.value)
        assertNotNull(engine.currentQuestion.value)
        assertEquals(0, engine.questionIndex.value)
    }

    @Test
    fun `startSession shuffles questions for non-diagnostic mode`() {
        val config = defaultConfig.copy(studyMode = KBStudyMode.TARGETED)

        // Run multiple times to check shuffling happens
        var differentOrder = false
        repeat(10) {
            engine.startSession(sampleQuestions, config)
            if (engine.currentQuestion.value?.id != "q1") {
                differentOrder = true
            }
        }

        // At least once it should be different if shuffling works
        // (Note: This is probabilistic, but with 10 tries and 3 questions, it's very likely)
        assertTrue("Questions should be shuffled for non-diagnostic mode", differentOrder)
    }

    @Test
    fun `startSession does not shuffle for diagnostic mode`() {
        val config = defaultConfig.copy(studyMode = KBStudyMode.DIAGNOSTIC)

        engine.startSession(sampleQuestions, config)

        // First question should always be the first one
        assertEquals("q1", engine.currentQuestion.value?.id)
    }

    // Answer submission tests

    @Test
    fun `submitAnswer returns correct result for correct answer`() {
        engine.startSession(sampleQuestions, defaultConfig)

        val result = engine.submitAnswer("Au")

        assertNotNull(result)
        assertTrue(result!!.isCorrect)
        assertEquals(KBPracticeState.SHOWING_ANSWER, engine.sessionState.value)
    }

    @Test
    fun `submitAnswer returns incorrect result for wrong answer`() {
        engine.startSession(sampleQuestions, defaultConfig)

        val result = engine.submitAnswer("wrong")

        assertNotNull(result)
        assertFalse(result!!.isCorrect)
    }

    @Test
    fun `submitAnswer records attempt`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.submitAnswer("Au")

        val session = engine.session.value
        assertNotNull(session)
        assertEquals(1, session!!.attempts.size)
        assertTrue(session.attempts[0].wasCorrect)
    }

    @Test
    fun `submitAnswer returns null when not in progress`() {
        engine.startSession(sampleQuestions, defaultConfig)
        engine.submitAnswer("Au")

        // Now in SHOWING_ANSWER state
        val result = engine.submitAnswer("another answer")

        assertNull(result)
    }

    // MCQ submission tests

    @Test
    fun `submitMCQAnswer validates correct selection`() {
        engine.startSession(sampleQuestions, defaultConfig)

        // Au is at index 0
        val result = engine.submitMCQAnswer(0)

        assertNotNull(result)
        assertTrue(result!!.isCorrect)
    }

    @Test
    fun `submitMCQAnswer validates incorrect selection`() {
        engine.startSession(sampleQuestions, defaultConfig)

        // Ag is at index 1
        val result = engine.submitMCQAnswer(1)

        assertNotNull(result)
        assertFalse(result!!.isCorrect)
    }

    // Skip tests

    @Test
    fun `skipQuestion moves to showing answer state`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.skipQuestion()

        assertEquals(KBPracticeState.SHOWING_ANSWER, engine.sessionState.value)
    }

    @Test
    fun `skipQuestion records skipped attempt`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.skipQuestion()

        val session = engine.session.value
        assertNotNull(session)
        assertEquals(1, session!!.attempts.size)
        assertTrue(session.attempts[0].wasSkipped)
        assertFalse(session.attempts[0].wasCorrect)
    }

    // Navigation tests

    @Test
    fun `nextQuestion advances to next question`() {
        engine.startSession(sampleQuestions, defaultConfig)
        engine.submitAnswer("Au")

        engine.nextQuestion()

        assertEquals(KBPracticeState.IN_PROGRESS, engine.sessionState.value)
        assertEquals(1, engine.questionIndex.value)
        assertEquals("q2", engine.currentQuestion.value?.id)
    }

    @Test
    fun `nextQuestion does nothing when not showing answer`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.nextQuestion()

        // Should still be on first question
        assertEquals(0, engine.questionIndex.value)
    }

    @Test
    fun `completing all questions ends session`() =
        runTest {
            engine.startSession(sampleQuestions, defaultConfig)

            // Answer all questions
            repeat(3) {
                engine.submitAnswer("any")
                engine.nextQuestion()
            }

            assertEquals(KBPracticeState.COMPLETED, engine.sessionState.value)
        }

    // Pause/Resume tests

    @Test
    fun `pauseSession changes state to PAUSED`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.pauseSession()

        assertEquals(KBPracticeState.PAUSED, engine.sessionState.value)
    }

    @Test
    fun `resumeSession restores IN_PROGRESS state`() {
        engine.startSession(sampleQuestions, defaultConfig)
        engine.pauseSession()

        engine.resumeSession()

        assertEquals(KBPracticeState.IN_PROGRESS, engine.sessionState.value)
    }

    @Test
    fun `pauseSession does nothing when not in progress`() {
        engine.pauseSession()

        assertEquals(KBPracticeState.NOT_STARTED, engine.sessionState.value)
    }

    // End session tests

    @Test
    fun `endSessionEarly completes session`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.endSessionEarly()

        assertEquals(KBPracticeState.COMPLETED, engine.sessionState.value)
    }

    // Summary generation tests

    @Test
    fun `generateSummary returns session summary`() {
        engine.startSession(sampleQuestions, defaultConfig)
        engine.submitAnswer("Au") // correct
        engine.nextQuestion()
        engine.submitAnswer("4") // correct
        engine.nextQuestion()

        val summary = engine.generateSummary()

        assertNotNull(summary)
        assertEquals(2, summary!!.totalQuestions)
        assertEquals(2, summary.totalCorrect)
    }

    @Test
    fun `generateSummary returns null with no session`() {
        val summary = engine.generateSummary()

        assertNull(summary)
    }

    // Reset tests

    @Test
    fun `reset clears all state`() {
        engine.startSession(sampleQuestions, defaultConfig)
        engine.submitAnswer("Au")

        engine.reset()

        assertEquals(KBPracticeState.NOT_STARTED, engine.sessionState.value)
        assertNull(engine.session.value)
        assertNull(engine.currentQuestion.value)
        assertEquals(0, engine.questionIndex.value)
    }

    // Points tests

    @Test
    fun `correct answer earns points per question`() {
        val config = defaultConfig.copy(pointsPerQuestion = 2)
        engine.startSession(sampleQuestions, config)

        engine.submitAnswer("Au")

        val attempt = engine.session.value?.attempts?.first()
        assertEquals(2, attempt?.pointsEarned)
    }

    @Test
    fun `incorrect answer earns zero points`() {
        engine.startSession(sampleQuestions, defaultConfig)

        engine.submitAnswer("wrong")

        val attempt = engine.session.value?.attempts?.first()
        assertEquals(0, attempt?.pointsEarned)
    }

    // Validation result tests

    @Test
    fun `lastValidationResult is updated after answer`() {
        engine.startSession(sampleQuestions, defaultConfig)

        assertNull(engine.lastValidationResult.value)

        engine.submitAnswer("Au")

        assertNotNull(engine.lastValidationResult.value)
        assertTrue(engine.lastValidationResult.value!!.isCorrect)
    }

    @Test
    fun `lastValidationResult is null after skip`() {
        engine.startSession(sampleQuestions, defaultConfig)
        engine.submitAnswer("Au")

        engine.nextQuestion()
        engine.skipQuestion()

        assertNull(engine.lastValidationResult.value)
    }
}
