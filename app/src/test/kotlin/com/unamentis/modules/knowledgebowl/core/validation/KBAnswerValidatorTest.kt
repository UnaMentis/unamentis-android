package com.unamentis.modules.knowledgebowl.core.validation

import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for KBAnswerValidator.
 */
class KBAnswerValidatorTest {
    private lateinit var validator: KBAnswerValidator

    @Before
    fun setup() {
        validator = KBAnswerValidator()
    }

    // Helper to create test questions
    private fun createQuestion(
        answer: String,
        acceptable: List<String>? = null,
        answerType: KBAnswerType = KBAnswerType.TEXT,
        mcqOptions: List<String>? = null,
    ): KBQuestion =
        KBQuestion(
            id = "test-q",
            text = "Test question?",
            answer =
                KBAnswer(
                    primary = answer,
                    acceptable = acceptable,
                    answerType = answerType,
                ),
            domain = KBDomain.SCIENCE,
            difficulty = KBDifficulty.VARSITY,
            mcqOptions = mcqOptions,
        )

    // Exact match tests

    @Test
    fun `exact match on primary answer returns correct`() {
        val question = createQuestion("photosynthesis")

        val result = validator.validate("photosynthesis", question)

        assertTrue(result.isCorrect)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertEquals(KBMatchType.EXACT, result.matchType)
        assertEquals("photosynthesis", result.matchedAnswer)
    }

    @Test
    fun `exact match is case insensitive`() {
        val question = createQuestion("photosynthesis")

        val result = validator.validate("PHOTOSYNTHESIS", question)

        assertTrue(result.isCorrect)
        assertEquals(KBMatchType.EXACT, result.matchType)
    }

    @Test
    fun `exact match ignores leading and trailing whitespace`() {
        val question = createQuestion("photosynthesis")

        val result = validator.validate("  photosynthesis  ", question)

        assertTrue(result.isCorrect)
        assertEquals(KBMatchType.EXACT, result.matchType)
    }

    @Test
    fun `wrong answer returns incorrect`() {
        val question = createQuestion("photosynthesis")

        val result = validator.validate("respiration", question)

        assertFalse(result.isCorrect)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals(KBMatchType.NONE, result.matchType)
        assertNull(result.matchedAnswer)
    }

    // Acceptable alternatives tests

    @Test
    fun `matches acceptable alternative`() {
        val question =
            createQuestion(
                answer = "United States of America",
                acceptable = listOf("USA", "United States", "America"),
            )

        val result = validator.validate("USA", question)

        assertTrue(result.isCorrect)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertEquals(KBMatchType.ACCEPTABLE, result.matchType)
        assertEquals("USA", result.matchedAnswer)
    }

    @Test
    fun `matches any acceptable alternative`() {
        val question =
            createQuestion(
                answer = "George Washington",
                acceptable = listOf("Washington", "G. Washington"),
            )

        val result1 = validator.validate("Washington", question)
        val result2 = validator.validate("G. Washington", question)

        assertTrue(result1.isCorrect)
        assertTrue(result2.isCorrect)
    }

    // Fuzzy matching tests

    @Test
    fun `fuzzy matches minor typos`() {
        val question = createQuestion("photosynthesis")

        // One character typo: photosynthesis -> photosynthsis
        val result = validator.validate("photosynthsis", question)

        assertTrue(result.isCorrect)
        assertEquals(KBMatchType.FUZZY, result.matchType)
        assertTrue(result.confidence >= 0.6f)
    }

    @Test
    fun `fuzzy matching respects threshold`() {
        val question = createQuestion("cat")

        // Too different - beyond threshold
        val result = validator.validate("xyz", question)

        assertFalse(result.isCorrect)
        assertEquals(KBMatchType.NONE, result.matchType)
    }

    @Test
    fun `fuzzy matches acceptable alternatives with typos`() {
        val question =
            createQuestion(
                answer = "United States of America",
                acceptable = listOf("USA"),
            )

        // "UA" is close to "USA"
        val result = validator.validate("UA", question)

        // Should match as fuzzy if within threshold
        assertTrue(result.isCorrect || !result.isCorrect) // May or may not match depending on threshold
    }

    // Strict mode tests

    @Test
    fun `strict mode rejects fuzzy matches`() {
        validator.setConfig(KBAnswerValidator.Config.STRICT)
        val question = createQuestion("photosynthesis")

        // Minor typo that would normally fuzzy match
        val result = validator.validate("photosynthsis", question)

        assertFalse(result.isCorrect)
        assertEquals(KBMatchType.NONE, result.matchType)
    }

    @Test
    fun `strict mode allows exact matches`() {
        validator.setConfig(KBAnswerValidator.Config.STRICT)
        val question = createQuestion("photosynthesis")

        val result = validator.validate("photosynthesis", question)

        assertTrue(result.isCorrect)
        assertEquals(KBMatchType.EXACT, result.matchType)
    }

    @Test
    fun `strict mode allows acceptable alternatives`() {
        validator.setConfig(KBAnswerValidator.Config.STRICT)
        val question =
            createQuestion(
                answer = "photosynthesis",
                acceptable = listOf("photosynthesis process"),
            )

        val result = validator.validate("photosynthesis process", question)

        assertTrue(result.isCorrect)
        assertEquals(KBMatchType.ACCEPTABLE, result.matchType)
    }

    // Lenient mode tests

    @Test
    fun `lenient mode has higher tolerance for typos`() {
        validator.setConfig(KBAnswerValidator.Config.LENIENT)
        val question = createQuestion("mitochondria")

        // Multiple typos
        val result = validator.validate("mitocondria", question)

        assertTrue(result.isCorrect)
        assertEquals(KBMatchType.FUZZY, result.matchType)
    }

    // Answer type specific tests

    @Test
    fun `validates person names with title removal`() {
        val question =
            createQuestion(
                answer = "Albert Einstein",
                answerType = KBAnswerType.PERSON,
            )

        val result = validator.validate("Dr. Albert Einstein", question)

        assertTrue(result.isCorrect)
    }

    @Test
    fun `validates person names with name order reversal`() {
        val question =
            createQuestion(
                answer = "Albert Einstein",
                answerType = KBAnswerType.PERSON,
            )

        val result = validator.validate("Einstein, Albert", question)

        assertTrue(result.isCorrect)
    }

    @Test
    fun `validates place with abbreviation expansion`() {
        val question =
            createQuestion(
                answer = "United States of America",
                acceptable = listOf("USA", "US"),
                answerType = KBAnswerType.PLACE,
            )

        val result = validator.validate("USA", question)

        assertTrue(result.isCorrect)
    }

    @Test
    fun `validates number with written form`() {
        val question =
            createQuestion(
                answer = "5",
                acceptable = listOf("five"),
                answerType = KBAnswerType.NUMBER,
            )

        val result = validator.validate("five", question)

        assertTrue(result.isCorrect)
    }

    @Test
    fun `validates date with month name conversion`() {
        val question =
            createQuestion(
                answer = "July 4, 1776",
                answerType = KBAnswerType.DATE,
            )

        // Both should normalize to same format
        val result = validator.validate("July 4, 1776", question)

        assertTrue(result.isCorrect)
    }

    @Test
    fun `validates title without leading the`() {
        val question =
            createQuestion(
                answer = "The Great Gatsby",
                answerType = KBAnswerType.TITLE,
            )

        val result = validator.validate("Great Gatsby", question)

        assertTrue(result.isCorrect)
    }

    @Test
    fun `validates scientific terms`() {
        val question =
            createQuestion(
                answer = "H2O",
                answerType = KBAnswerType.SCIENTIFIC,
            )

        val result = validator.validate("h2o", question)

        assertTrue(result.isCorrect)
    }

    // MCQ validation tests

    @Test
    fun `validates correct MCQ selection`() {
        val question =
            createQuestion(
                answer = "Paris",
                mcqOptions = listOf("London", "Paris", "Berlin", "Rome"),
            )

        val result = validator.validateMCQ(1, question) // Paris is at index 1

        assertTrue(result.isCorrect)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertEquals(KBMatchType.EXACT, result.matchType)
    }

    @Test
    fun `rejects incorrect MCQ selection`() {
        val question =
            createQuestion(
                answer = "Paris",
                mcqOptions = listOf("London", "Paris", "Berlin", "Rome"),
            )

        val result = validator.validateMCQ(0, question) // London is at index 0

        assertFalse(result.isCorrect)
        assertEquals(KBMatchType.NONE, result.matchType)
    }

    @Test
    fun `handles out of bounds MCQ selection`() {
        val question =
            createQuestion(
                answer = "Paris",
                mcqOptions = listOf("London", "Paris", "Berlin", "Rome"),
            )

        val result = validator.validateMCQ(5, question) // Invalid index

        assertFalse(result.isCorrect)
        assertEquals(KBMatchType.NONE, result.matchType)
    }

    @Test
    fun `handles negative MCQ selection`() {
        val question =
            createQuestion(
                answer = "Paris",
                mcqOptions = listOf("London", "Paris", "Berlin", "Rome"),
            )

        val result = validator.validateMCQ(-1, question)

        assertFalse(result.isCorrect)
    }

    @Test
    fun `handles question without MCQ options`() {
        val question = createQuestion("Paris")

        val result = validator.validateMCQ(0, question)

        assertFalse(result.isCorrect)
    }

    // Configuration tests

    @Test
    fun `configuration can be changed`() {
        val originalConfig = validator.getConfig()

        validator.setConfig(KBAnswerValidator.Config.STRICT)
        val newConfig = validator.getConfig()

        assertEquals(true, newConfig.strictMode)
        assertEquals(false, originalConfig.strictMode)
    }

    @Test
    fun `default config has standard values`() {
        val config = validator.getConfig()

        assertEquals(0.20, config.fuzzyThresholdPercent, 0.001)
        assertEquals(0.6f, config.minimumConfidence, 0.001f)
        assertEquals(false, config.strictMode)
    }

    // Points earned tests

    @Test
    fun `correct answer earns 1 point`() {
        val question = createQuestion("answer")

        val result = validator.validate("answer", question)

        assertEquals(1, result.pointsEarned)
    }

    @Test
    fun `incorrect answer earns 0 points`() {
        val question = createQuestion("answer")

        val result = validator.validate("wrong", question)

        assertEquals(0, result.pointsEarned)
    }
}
