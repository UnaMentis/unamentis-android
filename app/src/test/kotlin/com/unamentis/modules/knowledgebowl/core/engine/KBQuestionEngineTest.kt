package com.unamentis.modules.knowledgebowl.core.engine

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Tests for KBQuestionEngine.
 */
class KBQuestionEngineTest {
    private lateinit var mockContext: Context
    private lateinit var mockAssetManager: AssetManager
    private lateinit var engine: KBQuestionEngine

    private val sampleQuestionsJson =
        """
        {
          "version": "1.0.0",
          "generatedAt": "2023-11-14T00:00:00Z",
          "questions": [
            {
              "id": "q1",
              "text": "What is the chemical symbol for gold?",
              "answer": {"primary": "Au", "acceptable": ["AU"], "answerType": "scientific"},
              "domain": "science",
              "difficulty": "varsity",
              "gradeLevel": "highSchool",
              "suitability": {"forWritten": true, "forOral": true},
              "mcqOptions": ["Au", "Ag", "Fe", "Cu"]
            },
            {
              "id": "q2",
              "text": "What is the square root of 144?",
              "answer": {"primary": "12", "acceptable": ["twelve"], "answerType": "number"},
              "domain": "mathematics",
              "difficulty": "foundational",
              "gradeLevel": "middleSchool",
              "suitability": {"forWritten": true, "forOral": false},
              "mcqOptions": ["12", "11", "13", "14"]
            },
            {
              "id": "q3",
              "text": "Who wrote Romeo and Juliet?",
              "answer": {"primary": "William Shakespeare", "acceptable": ["Shakespeare"], "answerType": "person"},
              "domain": "literature",
              "difficulty": "intermediate",
              "gradeLevel": "highSchool",
              "suitability": {"forWritten": true, "forOral": true}
            },
            {
              "id": "q4",
              "text": "What is the capital of France?",
              "answer": {"primary": "Paris", "answerType": "place"},
              "domain": "socialStudies",
              "difficulty": "foundational",
              "gradeLevel": "middleSchool",
              "suitability": {"forWritten": true, "forOral": true}
            }
          ]
        }
        """.trimIndent()

    @Before
    fun setup() {
        mockAssetManager = mockk()
        mockContext = mockk()

        every { mockContext.assets } returns mockAssetManager
        every { mockAssetManager.open(any()) } returns ByteArrayInputStream(sampleQuestionsJson.toByteArray())

        engine = KBQuestionEngine(mockContext)
    }

    // Loading tests

    @Test
    fun `loadBundledQuestions loads questions from assets`() =
        runTest {
            engine.loadBundledQuestions()

            assertEquals(4, engine.totalQuestionCount)
            assertFalse(engine.isLoading.value)
        }

    @Test
    fun `questions state flow contains loaded questions`() =
        runTest {
            engine.loadBundledQuestions()

            assertEquals(4, engine.questions.value.size)
        }

    @Test
    fun `setQuestions directly sets questions`() {
        engine.setQuestions(emptyList())
        assertEquals(0, engine.totalQuestionCount)
    }

    // Filtering tests

    @Test
    fun `filter by domain returns matching questions`() =
        runTest {
            engine.loadBundledQuestions()
            val result =
                engine.filter(
                    domains =
                        listOf(
                            com.unamentis.modules.knowledgebowl.data.model.KBDomain.SCIENCE,
                        ),
                )

            assertEquals(1, result.size)
            assertEquals("q1", result[0].id)
        }

    @Test
    fun `filter by multiple domains returns all matching`() =
        runTest {
            engine.loadBundledQuestions()
            val result =
                engine.filter(
                    domains =
                        listOf(
                            com.unamentis.modules.knowledgebowl.data.model.KBDomain.SCIENCE,
                            com.unamentis.modules.knowledgebowl.data.model.KBDomain.MATHEMATICS,
                        ),
                )

            assertEquals(2, result.size)
        }

    @Test
    fun `filter by difficulty returns matching questions`() =
        runTest {
            engine.loadBundledQuestions()
            val result =
                engine.filter(
                    difficulty = com.unamentis.modules.knowledgebowl.data.model.KBDifficulty.FOUNDATIONAL,
                )

            assertEquals(2, result.size)
        }

    @Test
    fun `filter by grade level returns matching questions`() =
        runTest {
            engine.loadBundledQuestions()
            val result =
                engine.filter(
                    gradeLevel = com.unamentis.modules.knowledgebowl.data.model.KBGradeLevel.HIGH_SCHOOL,
                )

            assertEquals(2, result.size)
        }

    @Test
    fun `filter for written round returns suitable questions`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.filter(forWritten = true)

            assertEquals(4, result.size)
        }

    @Test
    fun `filter for oral round returns suitable questions`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.filter(forOral = true)

            assertEquals(3, result.size)
        }

    @Test
    fun `filter with null parameters returns all questions`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.filter()

            assertEquals(4, result.size)
        }

    // Selection tests

    @Test
    fun `selectRandom returns requested count`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.selectRandom(2)

            assertEquals(2, result.size)
        }

    @Test
    fun `selectRandom returns max available if count exceeds total`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.selectRandom(100)

            assertEquals(4, result.size)
        }

    @Test
    fun `selectRandom from filtered questions works`() =
        runTest {
            engine.loadBundledQuestions()
            val filtered =
                engine.filter(
                    domains =
                        listOf(
                            com.unamentis.modules.knowledgebowl.data.model.KBDomain.SCIENCE,
                        ),
                )
            val result = engine.selectRandom(5, filtered)

            assertEquals(1, result.size)
        }

    @Test
    fun `selectWeighted respects domain weights`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.selectWeighted(4, respectDomainWeights = true)

            // Should return some questions
            assertTrue(result.isNotEmpty())
        }

    @Test
    fun `selectWeighted without weights uses random selection`() =
        runTest {
            engine.loadBundledQuestions()
            val result = engine.selectWeighted(4, respectDomainWeights = false)

            assertEquals(4, result.size)
        }

    // Attempt tracking tests

    @Test
    fun `markAttempted tracks question`() =
        runTest {
            engine.loadBundledQuestions()

            assertFalse(engine.hasAttempted("q1"))
            engine.markAttempted("q1")
            assertTrue(engine.hasAttempted("q1"))
        }

    @Test
    fun `markAttempted with list tracks all questions`() =
        runTest {
            engine.loadBundledQuestions()

            engine.markAttempted(listOf("q1", "q2"))
            assertTrue(engine.hasAttempted("q1"))
            assertTrue(engine.hasAttempted("q2"))
        }

    @Test
    fun `clearAttemptedQuestions resets tracking`() =
        runTest {
            engine.loadBundledQuestions()

            engine.markAttempted("q1")
            engine.clearAttemptedQuestions()
            assertFalse(engine.hasAttempted("q1"))
        }

    @Test
    fun `filter with excludeAttempted removes attempted questions`() =
        runTest {
            engine.loadBundledQuestions()
            engine.markAttempted("q1")

            val result = engine.filter(excludeAttempted = true)

            assertEquals(3, result.size)
            assertFalse(result.any { it.id == "q1" })
        }

    // Statistics tests

    @Test
    fun `questionsByDomain returns correct counts`() =
        runTest {
            engine.loadBundledQuestions()
            val counts = engine.questionsByDomain

            assertEquals(1, counts[com.unamentis.modules.knowledgebowl.data.model.KBDomain.SCIENCE])
            assertEquals(1, counts[com.unamentis.modules.knowledgebowl.data.model.KBDomain.MATHEMATICS])
            assertEquals(1, counts[com.unamentis.modules.knowledgebowl.data.model.KBDomain.LITERATURE])
        }

    @Test
    fun `questionsByDifficulty returns correct counts`() =
        runTest {
            engine.loadBundledQuestions()
            val counts = engine.questionsByDifficulty

            assertEquals(2, counts[com.unamentis.modules.knowledgebowl.data.model.KBDifficulty.FOUNDATIONAL])
        }

    @Test
    fun `unattemptedCount decreases as questions are attempted`() =
        runTest {
            engine.loadBundledQuestions()

            assertEquals(4, engine.unattemptedCount)
            engine.markAttempted("q1")
            assertEquals(3, engine.unattemptedCount)
        }
}
