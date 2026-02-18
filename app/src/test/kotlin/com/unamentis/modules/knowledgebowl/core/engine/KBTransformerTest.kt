package com.unamentis.modules.knowledgebowl.core.engine

import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBGradeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for KBTransformer.
 */
class KBTransformerTest {
    // MARK: - Transform Tests

    @Test
    fun `transform maps valid imported question to KBQuestion`() {
        val imported =
            ImportedQuestion(
                text = "What is the chemical symbol for gold?",
                answer = "Au",
                domain = "science",
                source = "test-set",
            )

        val result = KBTransformer.transform(imported)

        assertNotNull(result)
        assertEquals("What is the chemical symbol for gold?", result!!.text)
        assertEquals("Au", result.answer.primary)
        assertEquals(KBDomain.SCIENCE, result.domain)
    }

    @Test
    fun `transform returns null for unknown domain`() {
        val imported =
            ImportedQuestion(
                text = "Some question",
                answer = "answer",
                domain = "underwater basket weaving",
                source = "test",
            )

        val result = KBTransformer.transform(imported)
        assertNull(result)
    }

    @Test
    fun `transform returns null for blank text`() {
        val imported =
            ImportedQuestion(
                text = "",
                answer = "answer",
                domain = "science",
                source = "test",
            )

        val result = KBTransformer.transform(imported)
        assertNull(result)
    }

    // MARK: - Domain Mapping Tests

    @Test
    fun `transform maps various domain strings`() {
        val domains =
            mapOf(
                "science" to KBDomain.SCIENCE,
                "math" to KBDomain.MATHEMATICS,
                "mathematics" to KBDomain.MATHEMATICS,
                "lit" to KBDomain.LITERATURE,
                "history" to KBDomain.HISTORY,
                "social studies" to KBDomain.SOCIAL_STUDIES,
                "arts" to KBDomain.ARTS,
                "current events" to KBDomain.CURRENT_EVENTS,
                "tech" to KBDomain.TECHNOLOGY,
                "misc" to KBDomain.MISCELLANEOUS,
            )

        for ((domainStr, expectedDomain) in domains) {
            val imported =
                ImportedQuestion(
                    text = "Test question for $domainStr",
                    answer = "answer",
                    domain = domainStr,
                    source = "test",
                )
            val result = KBTransformer.transform(imported)
            assertNotNull("Domain '$domainStr' should map to $expectedDomain", result)
            assertEquals("Domain '$domainStr' should map to $expectedDomain", expectedDomain, result!!.domain)
        }
    }

    // MARK: - Difficulty Mapping Tests

    @Test
    fun `transform maps difficulty strings`() {
        val cases =
            mapOf(
                "easy" to KBDifficulty.OVERVIEW,
                "foundational" to KBDifficulty.FOUNDATIONAL,
                "intermediate" to KBDifficulty.INTERMEDIATE,
                "varsity" to KBDifficulty.VARSITY,
                "championship" to KBDifficulty.CHAMPIONSHIP,
                "research" to KBDifficulty.RESEARCH,
            )

        for ((diffStr, expectedDiff) in cases) {
            val imported =
                ImportedQuestion(
                    text = "Question with difficulty $diffStr",
                    answer = "answer",
                    domain = "science",
                    difficulty = diffStr,
                    source = "test",
                )
            val result = KBTransformer.transform(imported)
            assertNotNull(result)
            assertEquals("Difficulty '$diffStr' should map to $expectedDiff", expectedDiff, result!!.difficulty)
        }
    }

    @Test
    fun `transform defaults to VARSITY for null difficulty`() {
        val imported =
            ImportedQuestion(
                text = "Question without difficulty",
                answer = "answer",
                domain = "science",
                source = "test",
            )
        val result = KBTransformer.transform(imported)
        assertNotNull(result)
        assertEquals(KBDifficulty.VARSITY, result!!.difficulty)
    }

    // MARK: - Grade Level Inference Tests

    @Test
    fun `easy difficulty maps to middle school`() {
        val imported =
            ImportedQuestion(
                text = "Basic question",
                answer = "answer",
                domain = "science",
                difficulty = "easy",
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertEquals(KBGradeLevel.MIDDLE_SCHOOL, result.gradeLevel)
    }

    @Test
    fun `championship difficulty maps to advanced`() {
        val imported =
            ImportedQuestion(
                text = "Advanced question",
                answer = "answer",
                domain = "science",
                difficulty = "championship",
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertEquals(KBGradeLevel.ADVANCED, result.gradeLevel)
    }

    // MARK: - Text Cleaning Tests

    @Test
    fun `transform removes Quiz Bowl markers`() {
        val imported =
            ImportedQuestion(
                text = "For 10 points, name this element with symbol Au.",
                answer = "Gold",
                domain = "science",
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertFalse(result.text.contains("For 10 points"))
    }

    @Test
    fun `transform removes Science Bowl answer prefix`() {
        val imported =
            ImportedQuestion(
                text = "What element has symbol Au?",
                answer = "A) Gold",
                domain = "science",
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertEquals("Gold", result.answer.primary)
    }

    // MARK: - Suitability Tests

    @Test
    fun `questions with formulas not suitable for oral`() {
        val imported =
            ImportedQuestion(
                text = "Solve the equation",
                answer = "42",
                domain = "math",
                hasFormula = true,
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertFalse(result.suitability.forOral)
        assertTrue(result.suitability.requiresVisual)
    }

    @Test
    fun `questions with MCQ options are mcqPossible`() {
        val imported =
            ImportedQuestion(
                text = "What is 2+2?",
                answer = "4",
                domain = "math",
                mcqOptions = listOf("3", "4", "5", "6"),
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertTrue(result.suitability.mcqPossible)
    }

    // MARK: - Quality Score Tests

    @Test
    fun `quality score base is 0_5`() {
        val imported =
            ImportedQuestion(
                text = "Q",
                answer = "A",
                domain = "science",
                source = "test",
            )
        val score = KBTransformer.qualityScore(imported)
        assertTrue(score >= 0.5)
    }

    @Test
    fun `quality score increases with MCQ options`() {
        val withMcq =
            ImportedQuestion(
                text = "A reasonable length question here",
                answer = "A",
                domain = "science",
                mcqOptions = listOf("A", "B", "C", "D"),
                source = "test",
            )
        val withoutMcq =
            ImportedQuestion(
                text = "A reasonable length question here",
                answer = "A",
                domain = "science",
                source = "test",
            )
        assertTrue(KBTransformer.qualityScore(withMcq) > KBTransformer.qualityScore(withoutMcq))
    }

    @Test
    fun `filterByQuality removes low quality questions`() {
        val questions =
            listOf(
                ImportedQuestion(text = "Q", answer = "A", domain = "science", source = "test"),
                ImportedQuestion(
                    text = "A reasonable length question with options",
                    answer = "Answer",
                    domain = "science",
                    mcqOptions = listOf("A", "B", "C", "D"),
                    acceptableAnswers = listOf("alt"),
                    source = "test",
                ),
            )
        val filtered = KBTransformer.filterByQuality(questions, threshold = 0.7)
        assertEquals(1, filtered.size)
    }

    // MARK: - Batch Transform Tests

    @Test
    fun `transformBatch filters out unmappable questions`() {
        val questions =
            listOf(
                ImportedQuestion(text = "Valid question", answer = "answer", domain = "science", source = "test"),
                ImportedQuestion(
                    text = "Invalid question",
                    answer = "answer",
                    domain = "unknown_domain",
                    source = "test",
                ),
                ImportedQuestion(text = "Another valid", answer = "answer", domain = "math", source = "test"),
            )
        val results = KBTransformer.transformBatch(questions)
        assertEquals(2, results.size)
    }

    // MARK: - Answer Type Inference Tests

    @Test
    fun `numeric answer inferred correctly`() {
        val imported =
            ImportedQuestion(text = "How many?", answer = "42", domain = "math", source = "test")
        val result = KBTransformer.transform(imported)!!
        assertEquals(KBAnswerType.NUMERIC, result.answer.answerType)
    }

    @Test
    fun `person name inferred for capitalized multi-word`() {
        val imported =
            ImportedQuestion(
                text = "Who wrote this?",
                answer = "William Shakespeare",
                domain = "literature",
                source = "test",
            )
        val result = KBTransformer.transform(imported)!!
        assertEquals(KBAnswerType.PERSON, result.answer.answerType)
    }

    @Test
    fun `scientific term inferred for alphanumeric mix`() {
        val imported =
            ImportedQuestion(text = "Chemical formula?", answer = "H2O", domain = "science", source = "test")
        val result = KBTransformer.transform(imported)!!
        assertEquals(KBAnswerType.SCIENTIFIC, result.answer.answerType)
    }
}
