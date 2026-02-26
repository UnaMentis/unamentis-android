package com.unamentis.modules.knowledgebowl.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [KBSynonymMatcher].
 *
 * Verifies synonym lookup and bidirectional matching across all
 * supported answer types and dictionaries.
 */
class KBSynonymMatcherTest {
    private lateinit var matcher: KBSynonymMatcher

    @Before
    fun setUp() {
        matcher = KBSynonymMatcher()
    }

    // MARK: - findSynonyms Tests

    @Test
    fun `findSynonyms returns original text when no dictionary for type`() {
        val result = matcher.findSynonyms("hello", KBAnswerType.TEXT)

        assertEquals(setOf("hello"), result)
    }

    @Test
    fun `findSynonyms returns original text when no dictionary for multiple choice`() {
        val result = matcher.findSynonyms("A", KBAnswerType.MULTIPLE_CHOICE)

        assertEquals(setOf("a"), result)
    }

    @Test
    fun `findSynonyms normalizes input to lowercase and trimmed`() {
        val result = matcher.findSynonyms("  USA  ", KBAnswerType.PLACE)

        assertTrue(result.contains("usa"))
        assertTrue(result.contains("united states"))
    }

    @Test
    fun `findSynonyms looks up key in place dictionary`() {
        val result = matcher.findSynonyms("usa", KBAnswerType.PLACE)

        assertTrue(result.contains("usa"))
        assertTrue(result.contains("united states"))
        assertTrue(result.contains("united states of america"))
        assertTrue(result.contains("us"))
        assertTrue(result.contains("america"))
    }

    @Test
    fun `findSynonyms looks up value in place dictionary`() {
        val result = matcher.findSynonyms("united states", KBAnswerType.PLACE)

        assertTrue(result.contains("usa"))
        assertTrue(result.contains("united states"))
        assertTrue(result.contains("united states of america"))
    }

    @Test
    fun `findSynonyms looks up key in scientific dictionary`() {
        val result = matcher.findSynonyms("h2o", KBAnswerType.SCIENTIFIC)

        assertTrue(result.contains("h2o"))
        assertTrue(result.contains("water"))
        assertTrue(result.contains("dihydrogen monoxide"))
    }

    @Test
    fun `findSynonyms looks up value in scientific dictionary`() {
        val result = matcher.findSynonyms("water", KBAnswerType.SCIENTIFIC)

        assertTrue(result.contains("h2o"))
        assertTrue(result.contains("water"))
    }

    @Test
    fun `findSynonyms looks up key in historical dictionary for PERSON type`() {
        val result = matcher.findSynonyms("fdr", KBAnswerType.PERSON)

        assertTrue(result.contains("fdr"))
        assertTrue(result.contains("franklin delano roosevelt"))
        assertTrue(result.contains("franklin roosevelt"))
    }

    @Test
    fun `findSynonyms looks up key in historical dictionary for TITLE type`() {
        val result = matcher.findSynonyms("wwii", KBAnswerType.TITLE)

        assertTrue(result.contains("wwii"))
        assertTrue(result.contains("world war ii"))
        assertTrue(result.contains("world war two"))
        assertTrue(result.contains("second world war"))
    }

    @Test
    fun `findSynonyms uses mathematics dictionary for NUMBER type`() {
        val result = matcher.findSynonyms("pi", KBAnswerType.NUMBER)

        assertTrue(result.contains("pi"))
        assertTrue(result.contains("\u03C0"))
        assertTrue(result.contains("3.14159"))
    }

    @Test
    fun `findSynonyms uses mathematics dictionary for NUMERIC type`() {
        val result = matcher.findSynonyms("sqrt", KBAnswerType.NUMERIC)

        assertTrue(result.contains("sqrt"))
        assertTrue(result.contains("square root"))
    }

    @Test
    fun `findSynonyms uses mathematics dictionary for DATE type`() {
        val result = matcher.findSynonyms("ln", KBAnswerType.DATE)

        assertTrue(result.contains("ln"))
        assertTrue(result.contains("natural logarithm"))
        assertTrue(result.contains("natural log"))
    }

    @Test
    fun `findSynonyms returns only normalized text for unknown word`() {
        val result = matcher.findSynonyms("xyzabc123", KBAnswerType.SCIENTIFIC)

        assertEquals(setOf("xyzabc123"), result)
    }

    // MARK: - areSynonyms Tests

    @Test
    fun `areSynonyms returns true for exact match`() {
        assertTrue(matcher.areSynonyms("water", "water", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns true for case-insensitive match`() {
        assertTrue(matcher.areSynonyms("Water", "WATER", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns true for trimmed match`() {
        assertTrue(matcher.areSynonyms("  water  ", "water", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns true for key-value synonyms`() {
        assertTrue(matcher.areSynonyms("h2o", "water", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns true for value-key synonyms (bidirectional)`() {
        assertTrue(matcher.areSynonyms("water", "h2o", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns true for value-value synonyms`() {
        // Both "water" and "dihydrogen monoxide" are values of key "h2o"
        assertTrue(matcher.areSynonyms("water", "dihydrogen monoxide", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns false for unrelated terms`() {
        assertFalse(matcher.areSynonyms("water", "salt", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms returns false for TEXT type even with known synonyms`() {
        // TEXT type has no dictionary, so no synonym matching
        assertFalse(matcher.areSynonyms("h2o", "water", KBAnswerType.TEXT))
    }

    @Test
    fun `areSynonyms returns false for MULTIPLE_CHOICE type`() {
        assertFalse(matcher.areSynonyms("h2o", "water", KBAnswerType.MULTIPLE_CHOICE))
    }

    // MARK: - Bidirectionality Tests

    @Test
    fun `areSynonyms is bidirectional for places`() {
        assertTrue(matcher.areSynonyms("usa", "america", KBAnswerType.PLACE))
        assertTrue(matcher.areSynonyms("america", "usa", KBAnswerType.PLACE))
    }

    @Test
    fun `areSynonyms is bidirectional for scientific terms`() {
        assertTrue(matcher.areSynonyms("nacl", "table salt", KBAnswerType.SCIENTIFIC))
        assertTrue(matcher.areSynonyms("table salt", "nacl", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `areSynonyms is bidirectional for historical terms`() {
        assertTrue(matcher.areSynonyms("jfk", "john f kennedy", KBAnswerType.PERSON))
        assertTrue(matcher.areSynonyms("john f kennedy", "jfk", KBAnswerType.PERSON))
    }

    @Test
    fun `areSynonyms is bidirectional for mathematics`() {
        assertTrue(matcher.areSynonyms("sin", "sine", KBAnswerType.NUMBER))
        assertTrue(matcher.areSynonyms("sine", "sin", KBAnswerType.NUMBER))
    }

    // MARK: - Cross-domain Tests

    @Test
    fun `place synonyms include geographic abbreviations`() {
        assertTrue(matcher.areSynonyms("uk", "united kingdom", KBAnswerType.PLACE))
        assertTrue(matcher.areSynonyms("uk", "great britain", KBAnswerType.PLACE))
        assertTrue(matcher.areSynonyms("uk", "england", KBAnswerType.PLACE))
    }

    @Test
    fun `scientific synonyms include element symbols`() {
        assertTrue(matcher.areSynonyms("au", "gold", KBAnswerType.SCIENTIFIC))
        assertTrue(matcher.areSynonyms("ag", "silver", KBAnswerType.SCIENTIFIC))
        assertTrue(matcher.areSynonyms("fe", "iron", KBAnswerType.SCIENTIFIC))
    }

    @Test
    fun `historical synonyms include war abbreviations`() {
        assertTrue(matcher.areSynonyms("wwi", "great war", KBAnswerType.PERSON))
        assertTrue(matcher.areSynonyms("wwii", "second world war", KBAnswerType.TITLE))
    }

    @Test
    fun `mathematics synonyms include trig functions`() {
        assertTrue(matcher.areSynonyms("arcsin", "inverse sine", KBAnswerType.NUMBER))
        assertTrue(matcher.areSynonyms("arccos", "inverse cosine", KBAnswerType.NUMERIC))
        assertTrue(matcher.areSynonyms("arctan", "inverse tangent", KBAnswerType.DATE))
    }
}
