package com.unamentis.modules.knowledgebowl.core.validation

import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for AnswerNormalizer.
 */
class AnswerNormalizerTest {
    // TEXT normalization tests

    @Test
    fun `normalizes text to lowercase`() {
        assertEquals("hello world", AnswerNormalizer.normalize("HELLO WORLD", KBAnswerType.TEXT))
    }

    @Test
    fun `removes common punctuation from text`() {
        assertEquals("hello world", AnswerNormalizer.normalize("Hello, World!", KBAnswerType.TEXT))
    }

    @Test
    fun `collapses multiple spaces in text`() {
        assertEquals("hello world", AnswerNormalizer.normalize("hello   world", KBAnswerType.TEXT))
    }

    @Test
    fun `removes leading articles from text`() {
        assertEquals("cat", AnswerNormalizer.normalize("The cat", KBAnswerType.TEXT))
        assertEquals("apple", AnswerNormalizer.normalize("an apple", KBAnswerType.TEXT))
        assertEquals("dog", AnswerNormalizer.normalize("a dog", KBAnswerType.TEXT))
    }

    @Test
    fun `trims whitespace from text`() {
        assertEquals("hello", AnswerNormalizer.normalize("  hello  ", KBAnswerType.TEXT))
    }

    // PERSON normalization tests

    @Test
    fun `removes personal titles from person names`() {
        assertEquals("john smith", AnswerNormalizer.normalize("Dr. John Smith", KBAnswerType.PERSON))
        assertEquals("jane doe", AnswerNormalizer.normalize("Mrs. Jane Doe", KBAnswerType.PERSON))
        assertEquals("albert einstein", AnswerNormalizer.normalize("Prof. Albert Einstein", KBAnswerType.PERSON))
    }

    @Test
    fun `handles last first format for person names`() {
        assertEquals("john smith", AnswerNormalizer.normalize("Smith, John", KBAnswerType.PERSON))
    }

    @Test
    fun `normalizes person names consistently`() {
        val result1 = AnswerNormalizer.normalize("Albert Einstein", KBAnswerType.PERSON)
        val result2 = AnswerNormalizer.normalize("Einstein, Albert", KBAnswerType.PERSON)
        assertEquals(result1, result2)
    }

    // PLACE normalization tests

    @Test
    fun `expands USA abbreviation`() {
        assertEquals("united states of america", AnswerNormalizer.normalize("USA", KBAnswerType.PLACE))
    }

    @Test
    fun `expands US abbreviation`() {
        assertEquals("united states", AnswerNormalizer.normalize("US", KBAnswerType.PLACE))
    }

    @Test
    fun `expands UK abbreviation`() {
        assertEquals("united kingdom", AnswerNormalizer.normalize("UK", KBAnswerType.PLACE))
    }

    @Test
    fun `preserves non-abbreviated place names`() {
        assertEquals("france", AnswerNormalizer.normalize("France", KBAnswerType.PLACE))
    }

    @Test
    fun `expands mount abbreviation`() {
        assertEquals("mount", AnswerNormalizer.normalize("Mt", KBAnswerType.PLACE))
    }

    // NUMBER normalization tests

    @Test
    fun `converts written numbers to digits`() {
        assertEquals("5", AnswerNormalizer.normalize("five", KBAnswerType.NUMBER))
        assertEquals("10", AnswerNormalizer.normalize("ten", KBAnswerType.NUMBER))
        assertEquals("20", AnswerNormalizer.normalize("twenty", KBAnswerType.NUMBER))
    }

    @Test
    fun `removes commas from numbers`() {
        assertEquals("1000000", AnswerNormalizer.normalize("1,000,000", KBAnswerType.NUMBER))
    }

    @Test
    fun `preserves digit numbers`() {
        assertEquals("42", AnswerNormalizer.normalize("42", KBAnswerType.NUMBER))
    }

    // DATE normalization tests

    @Test
    fun `converts full month names to numbers`() {
        assertEquals("7 4 1776", AnswerNormalizer.normalize("July 4 1776", KBAnswerType.DATE))
    }

    @Test
    fun `converts abbreviated month names to numbers`() {
        assertEquals("1 1 2000", AnswerNormalizer.normalize("Jan 1 2000", KBAnswerType.DATE))
    }

    @Test
    fun `handles various date formats`() {
        val result = AnswerNormalizer.normalize("December 25, 2023", KBAnswerType.DATE)
        assertEquals("12 25 2023", result)
    }

    // TITLE normalization tests

    @Test
    fun `removes leading the from titles`() {
        assertEquals("great gatsby", AnswerNormalizer.normalize("The Great Gatsby", KBAnswerType.TITLE))
    }

    @Test
    fun `removes subtitle after colon`() {
        assertEquals("star wars", AnswerNormalizer.normalize("Star Wars: A New Hope", KBAnswerType.TITLE))
    }

    @Test
    fun `preserves titles without the`() {
        assertEquals("moby dick", AnswerNormalizer.normalize("Moby Dick", KBAnswerType.TITLE))
    }

    // SCIENTIFIC normalization tests

    @Test
    fun `removes whitespace from scientific terms`() {
        assertEquals("h2o", AnswerNormalizer.normalize("H 2 O", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `preserves special characters in scientific terms`() {
        assertEquals("e=mc²", AnswerNormalizer.normalize("E = mc²", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `lowercases scientific terms`() {
        assertEquals("nacl", AnswerNormalizer.normalize("NaCl", KBAnswerType.SCIENTIFIC))
    }

    // MULTIPLE_CHOICE normalization tests

    @Test
    fun `extracts letter from MCQ answer`() {
        assertEquals("a", AnswerNormalizer.normalize("A", KBAnswerType.MULTIPLE_CHOICE))
        assertEquals("b", AnswerNormalizer.normalize("B", KBAnswerType.MULTIPLE_CHOICE))
    }

    @Test
    fun `extracts first letter from MCQ with text`() {
        assertEquals("a", AnswerNormalizer.normalize("A) George Washington", KBAnswerType.MULTIPLE_CHOICE))
    }

    @Test
    fun `handles lowercase MCQ input`() {
        assertEquals("c", AnswerNormalizer.normalize("c", KBAnswerType.MULTIPLE_CHOICE))
    }
}
