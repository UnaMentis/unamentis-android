package com.unamentis.modules.knowledgebowl.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Levenshtein distance algorithm.
 */
class LevenshteinDistanceTest {
    @Test
    fun `calculate returns 0 for identical strings`() {
        assertEquals(0, LevenshteinDistance.calculate("hello", "hello"))
    }

    @Test
    fun `calculate returns length of string when other is empty`() {
        assertEquals(5, LevenshteinDistance.calculate("hello", ""))
        assertEquals(5, LevenshteinDistance.calculate("", "world"))
    }

    @Test
    fun `calculate returns 0 for two empty strings`() {
        assertEquals(0, LevenshteinDistance.calculate("", ""))
    }

    @Test
    fun `calculate handles single character substitution`() {
        assertEquals(1, LevenshteinDistance.calculate("cat", "bat"))
        assertEquals(1, LevenshteinDistance.calculate("cat", "cot"))
        assertEquals(1, LevenshteinDistance.calculate("cat", "car"))
    }

    @Test
    fun `calculate handles single character insertion`() {
        assertEquals(1, LevenshteinDistance.calculate("cat", "cats"))
        assertEquals(1, LevenshteinDistance.calculate("cat", "scat"))
        assertEquals(1, LevenshteinDistance.calculate("cat", "coat"))
    }

    @Test
    fun `calculate handles single character deletion`() {
        assertEquals(1, LevenshteinDistance.calculate("cats", "cat"))
        assertEquals(1, LevenshteinDistance.calculate("scat", "cat"))
        assertEquals(1, LevenshteinDistance.calculate("coat", "cat"))
    }

    @Test
    fun `calculate handles multiple edits`() {
        assertEquals(3, LevenshteinDistance.calculate("kitten", "sitting"))
        assertEquals(2, LevenshteinDistance.calculate("book", "back"))
    }

    @Test
    fun `calculate handles completely different strings`() {
        assertEquals(3, LevenshteinDistance.calculate("abc", "xyz"))
    }

    @Test
    fun `calculate is case sensitive`() {
        assertEquals(1, LevenshteinDistance.calculate("Cat", "cat"))
    }

    @Test
    fun `calculate handles longer strings`() {
        val s1 = "the quick brown fox"
        val s2 = "the quick brown dog"
        // fox -> dog: f->d, o->o, x->g = 2 edits
        assertEquals(2, LevenshteinDistance.calculate(s1, s2))
    }

    // Similarity tests

    @Test
    fun `similarity returns 1 for identical strings`() {
        assertEquals(1.0f, LevenshteinDistance.similarity("hello", "hello"), 0.001f)
    }

    @Test
    fun `similarity returns 1 for two empty strings`() {
        assertEquals(1.0f, LevenshteinDistance.similarity("", ""), 0.001f)
    }

    @Test
    fun `similarity returns 0 for completely different same-length strings`() {
        assertEquals(0.0f, LevenshteinDistance.similarity("abc", "xyz"), 0.001f)
    }

    @Test
    fun `similarity returns expected value for partial match`() {
        // "cat" vs "bat" = 1 edit, length 3, so similarity = 1 - 1/3 = 0.667
        val similarity = LevenshteinDistance.similarity("cat", "bat")
        assertEquals(0.667f, similarity, 0.01f)
    }

    @Test
    fun `similarity handles different length strings`() {
        // "cat" vs "cats" = 1 edit, max length 4, so similarity = 1 - 1/4 = 0.75
        val similarity = LevenshteinDistance.similarity("cat", "cats")
        assertEquals(0.75f, similarity, 0.001f)
    }

    // Threshold tests

    @Test
    fun `isWithinThreshold returns true for strings within threshold`() {
        assertTrue(LevenshteinDistance.isWithinThreshold("cat", "bat", 1))
        assertTrue(LevenshteinDistance.isWithinThreshold("kitten", "sitting", 3))
    }

    @Test
    fun `isWithinThreshold returns false for strings beyond threshold`() {
        assertFalse(LevenshteinDistance.isWithinThreshold("cat", "dog", 1))
        assertFalse(LevenshteinDistance.isWithinThreshold("hello", "world", 3))
    }

    @Test
    fun `isWithinThreshold returns true for identical strings with any threshold`() {
        assertTrue(LevenshteinDistance.isWithinThreshold("hello", "hello", 0))
    }

    @Test
    fun `isWithinThreshold returns true at exact threshold boundary`() {
        // "cat" vs "bat" = 1 edit
        assertTrue(LevenshteinDistance.isWithinThreshold("cat", "bat", 1))
    }
}
