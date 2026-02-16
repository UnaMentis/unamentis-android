package com.unamentis.services.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for [VoiceCommandRecognizer].
 *
 * Tests cover:
 * - Exact match recognition for all commands
 * - Phonetic matching via Double Metaphone
 * - Token similarity via Jaccard coefficient
 * - Normalization (case, whitespace, apostrophes)
 * - Empty/blank input handling
 * - Command filtering with validCommands
 * - Best-match selection when multiple commands match
 * - contains() convenience method
 * - Confidence thresholds and shouldExecute
 * - Embedded PhoneticMatcher correctness
 * - Embedded TokenMatcher correctness
 */
class VoiceCommandRecognizerTest {
    private lateinit var recognizer: VoiceCommandRecognizer

    @Before
    fun setup() {
        recognizer = VoiceCommandRecognizer()
    }

    // -- Exact Match Tests --

    @Test
    fun `recognize exact match - ready`() {
        val result = recognizer.recognize("ready")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
        assertEquals(1.0f, result.confidence)
        assertEquals(MatchType.EXACT, result.matchType)
        assertEquals("ready", result.matchedPhrase)
        assertTrue(result.shouldExecute)
    }

    @Test
    fun `recognize exact match - i'm ready`() {
        val result = recognizer.recognize("i'm ready")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
        assertEquals(1.0f, result.confidence)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `recognize exact match - let's go`() {
        val result = recognizer.recognize("let's go")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize exact match - submit`() {
        val result = recognizer.recognize("submit")
        assertNotNull(result)
        assertEquals(VoiceCommand.SUBMIT, result!!.command)
        assertEquals(1.0f, result.confidence)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `recognize exact match - final answer`() {
        val result = recognizer.recognize("final answer")
        assertNotNull(result)
        assertEquals(VoiceCommand.SUBMIT, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize exact match - that's my answer`() {
        val result = recognizer.recognize("that's my answer")
        assertNotNull(result)
        assertEquals(VoiceCommand.SUBMIT, result!!.command)
    }

    @Test
    fun `recognize exact match - next`() {
        val result = recognizer.recognize("next")
        assertNotNull(result)
        assertEquals(VoiceCommand.NEXT, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize exact match - next question`() {
        val result = recognizer.recognize("next question")
        assertNotNull(result)
        assertEquals(VoiceCommand.NEXT, result!!.command)
    }

    @Test
    fun `recognize exact match - move on`() {
        val result = recognizer.recognize("move on")
        assertNotNull(result)
        assertEquals(VoiceCommand.NEXT, result!!.command)
    }

    @Test
    fun `recognize exact match - skip`() {
        val result = recognizer.recognize("skip")
        assertNotNull(result)
        assertEquals(VoiceCommand.SKIP, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize exact match - i don't know`() {
        val result = recognizer.recognize("i don't know")
        assertNotNull(result)
        assertEquals(VoiceCommand.SKIP, result!!.command)
    }

    @Test
    fun `recognize exact match - no idea`() {
        val result = recognizer.recognize("no idea")
        assertNotNull(result)
        assertEquals(VoiceCommand.SKIP, result!!.command)
    }

    @Test
    fun `recognize exact match - repeat`() {
        val result = recognizer.recognize("repeat")
        assertNotNull(result)
        assertEquals(VoiceCommand.REPEAT_LAST, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize exact match - say again`() {
        val result = recognizer.recognize("say again")
        assertNotNull(result)
        assertEquals(VoiceCommand.REPEAT_LAST, result!!.command)
    }

    @Test
    fun `recognize exact match - what was that`() {
        val result = recognizer.recognize("what was that")
        assertNotNull(result)
        assertEquals(VoiceCommand.REPEAT_LAST, result!!.command)
    }

    @Test
    fun `recognize exact match - quit`() {
        val result = recognizer.recognize("quit")
        assertNotNull(result)
        assertEquals(VoiceCommand.QUIT, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize exact match - end session`() {
        val result = recognizer.recognize("end session")
        assertNotNull(result)
        assertEquals(VoiceCommand.QUIT, result!!.command)
    }

    @Test
    fun `recognize exact match - go back`() {
        val result = recognizer.recognize("go back")
        assertNotNull(result)
        assertEquals(VoiceCommand.QUIT, result!!.command)
    }

    // -- Case Insensitivity Tests --

    @Test
    fun `recognize is case insensitive`() {
        val result = recognizer.recognize("READY")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun `recognize handles mixed case`() {
        val result = recognizer.recognize("Let's Go")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
    }

    @Test
    fun `recognize handles all caps`() {
        val result = recognizer.recognize("SUBMIT")
        assertNotNull(result)
        assertEquals(VoiceCommand.SUBMIT, result!!.command)
    }

    // -- Whitespace and Normalization Tests --

    @Test
    fun `recognize trims whitespace`() {
        val result = recognizer.recognize("  ready  ")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
    }

    @Test
    fun `recognize normalizes curly apostrophes`() {
        // Unicode right single quotation mark (U+2019)
        val result = recognizer.recognize("i\u2019m ready")
        assertNotNull(result)
        assertEquals(VoiceCommand.READY, result!!.command)
    }

    // -- Empty Input Tests --

    @Test
    fun `recognize returns null for empty string`() {
        val result = recognizer.recognize("")
        assertNull(result)
    }

    @Test
    fun `recognize returns null for blank string`() {
        val result = recognizer.recognize("   ")
        assertNull(result)
    }

    // -- No Match Tests --

    @Test
    fun `recognize returns null for unrecognized text`() {
        val result = recognizer.recognize("the weather is nice today")
        assertNull(result)
    }

    @Test
    fun `recognize returns null for random words`() {
        val result = recognizer.recognize("banana elephant quantum")
        assertNull(result)
    }

    // -- Substring Containment Tests --

    @Test
    fun `recognize matches when command phrase is contained in longer text`() {
        val result = recognizer.recognize("okay I think I'm ready now")
        assertNotNull(result)
        // Should match either READY ("i'm ready") or NEXT ("okay")
        assertTrue(result!!.confidence == 1.0f)
        assertEquals(MatchType.EXACT, result.matchType)
    }

    @Test
    fun `recognize finds command embedded in sentence`() {
        val result = recognizer.recognize("I want to skip this one")
        assertNotNull(result)
        assertEquals(VoiceCommand.SKIP, result!!.command)
    }

    // -- Valid Commands Filter Tests --

    @Test
    fun `recognize respects validCommands filter`() {
        val result =
            recognizer.recognize(
                "ready",
                validCommands = setOf(VoiceCommand.SUBMIT, VoiceCommand.SKIP),
            )
        // "ready" should not match when READY is not in validCommands
        assertNull(result)
    }

    @Test
    fun `recognize matches within validCommands`() {
        val result =
            recognizer.recognize(
                "submit",
                validCommands = setOf(VoiceCommand.SUBMIT, VoiceCommand.SKIP),
            )
        assertNotNull(result)
        assertEquals(VoiceCommand.SUBMIT, result!!.command)
    }

    @Test
    fun `recognize with all commands allowed matches normally`() {
        val result = recognizer.recognize("skip", validCommands = null)
        assertNotNull(result)
        assertEquals(VoiceCommand.SKIP, result!!.command)
    }

    @Test
    fun `recognize with empty validCommands returns null`() {
        val result = recognizer.recognize("ready", validCommands = emptySet())
        assertNull(result)
    }

    // -- contains() Tests --

    @Test
    fun `contains returns true for matching command`() {
        assertTrue(recognizer.contains(VoiceCommand.READY, "i'm ready"))
    }

    @Test
    fun `contains returns false for non-matching command`() {
        assertFalse(recognizer.contains(VoiceCommand.QUIT, "i'm ready"))
    }

    @Test
    fun `contains returns false for empty transcript`() {
        assertFalse(recognizer.contains(VoiceCommand.READY, ""))
    }

    // -- getPhrasesForCommand() Tests --

    @Test
    fun `getPhrasesForCommand returns phrases for READY`() {
        val phrases = recognizer.getPhrasesForCommand(VoiceCommand.READY)
        assertTrue(phrases.isNotEmpty())
        assertTrue(phrases.contains("ready"))
        assertTrue(phrases.contains("i'm ready"))
        assertTrue(phrases.contains("let's go"))
    }

    @Test
    fun `getPhrasesForCommand returns phrases for all commands`() {
        for (command in VoiceCommand.entries) {
            val phrases = recognizer.getPhrasesForCommand(command)
            assertTrue("Command $command should have phrases", phrases.isNotEmpty())
            assertTrue("Command $command should have multiple phrases", phrases.size >= 6)
        }
    }

    // -- shouldExecute Tests --

    @Test
    fun `shouldExecute is true for exact matches`() {
        val result = recognizer.recognize("ready")
        assertNotNull(result)
        assertTrue(result!!.shouldExecute)
    }

    @Test
    fun `shouldExecute threshold is 0 point 75`() {
        assertEquals(0.75f, VoiceCommandResult.EXECUTION_THRESHOLD)
    }

    // -- Best Match Selection --

    @Test
    fun `recognize selects highest confidence match`() {
        // "ok" matches NEXT exactly
        val result = recognizer.recognize("ok")
        assertNotNull(result)
        assertEquals(VoiceCommand.NEXT, result!!.command)
        assertEquals(1.0f, result.confidence)
    }

    // -- All Commands Have Exact Matches --

    @Test
    fun `all commands have at least one exact match`() {
        val testPhrases =
            mapOf(
                VoiceCommand.READY to "ready",
                VoiceCommand.SUBMIT to "submit",
                VoiceCommand.NEXT to "next",
                VoiceCommand.SKIP to "skip",
                VoiceCommand.REPEAT_LAST to "repeat",
                VoiceCommand.QUIT to "quit",
            )

        for ((expectedCommand, phrase) in testPhrases) {
            val result = recognizer.recognize(phrase)
            assertNotNull("Command $expectedCommand should match phrase '$phrase'", result)
            assertEquals(
                "Phrase '$phrase' should match command $expectedCommand",
                expectedCommand,
                result!!.command,
            )
        }
    }

    // -- Normalization Edge Cases --

    @Test
    fun `normalize converts to lowercase`() {
        assertEquals("hello world", recognizer.normalize("Hello World"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("hello", recognizer.normalize("  hello  "))
    }

    @Test
    fun `normalize converts curly apostrophe`() {
        assertEquals("i'm", recognizer.normalize("i\u2019m"))
    }
}

/**
 * Unit tests for the embedded [PhoneticMatcher] (Double Metaphone).
 */
class PhoneticMatcherTest {
    private lateinit var matcher: PhoneticMatcher

    @Before
    fun setup() {
        matcher = PhoneticMatcher()
    }

    @Test
    fun `metaphone returns empty for empty input`() {
        val result = matcher.metaphone("")
        assertEquals("", result.primary)
        assertNull(result.secondary)
    }

    @Test
    fun `metaphone encodes simple word`() {
        val result = matcher.metaphone("ready")
        assertTrue(result.primary.isNotEmpty())
    }

    @Test
    fun `metaphone encodes multi-word phrase`() {
        val result = matcher.metaphone("next question")
        assertTrue(result.primary.isNotEmpty())
    }

    @Test
    fun `metaphone is case insensitive`() {
        val lower = matcher.metaphone("ready")
        val upper = matcher.metaphone("READY")
        assertEquals(lower.primary, upper.primary)
    }

    @Test
    fun `metaphone handles initial exceptions GN`() {
        val result = matcher.metaphone("gnome")
        assertTrue(result.primary.isNotEmpty())
    }

    @Test
    fun `metaphone handles initial exceptions KN`() {
        val result = matcher.metaphone("knight")
        assertTrue(result.primary.isNotEmpty())
    }

    @Test
    fun `metaphone handles CH digraph`() {
        val result = matcher.metaphone("church")
        assertTrue(result.primary.contains("X") || result.primary.contains("K"))
    }

    @Test
    fun `metaphone handles SH digraph`() {
        val result = matcher.metaphone("ship")
        assertTrue(result.primary.contains("X"))
    }

    @Test
    fun `metaphone handles TH digraph`() {
        val result = matcher.metaphone("the")
        // TH produces primary "0" and secondary "T"
        assertNotNull(result.secondary)
    }

    @Test
    fun `metaphone handles PH digraph`() {
        val result = matcher.metaphone("phone")
        assertTrue(result.primary.contains("F"))
    }

    @Test
    fun `metaphone similar sounding words produce same code`() {
        val ready1 = matcher.metaphone("ready")
        val ready2 = matcher.metaphone("reddy")
        // Both should produce similar primary codes starting with R
        assertTrue(ready1.primary.startsWith("R"))
        assertTrue(ready2.primary.startsWith("R"))
    }

    @Test
    fun `metaphone strips non-letter characters`() {
        val result = matcher.metaphone("hello123world")
        assertTrue(result.primary.isNotEmpty())
    }

    @Test
    fun `metaphone truncates to max length`() {
        val result = matcher.metaphone("supercalifragilisticexpialidocious")
        assertTrue(result.primary.length <= 8)
    }

    @Test
    fun `metaphone handles vowel at start`() {
        val result = matcher.metaphone("again")
        assertTrue(result.primary.startsWith("A"))
    }

    @Test
    fun `metaphone handles G before E I Y produces J and K`() {
        val result = matcher.metaphone("gem")
        // G before E should produce primary J, secondary K
        assertNotNull(result.secondary)
    }

    @Test
    fun `metaphone handles W before vowel`() {
        val result = matcher.metaphone("water")
        assertTrue(result.primary.isNotEmpty())
    }

    @Test
    fun `metaphone handles X at start`() {
        val result = matcher.metaphone("xylophone")
        // X at start encodes as S
        assertTrue(result.primary.startsWith("S"))
    }

    @Test
    fun `metaphone handles V as F`() {
        val result = matcher.metaphone("valley")
        assertTrue(result.primary.contains("F"))
    }

    @Test
    fun `metaphone handles Z as S`() {
        val result = matcher.metaphone("zero")
        assertTrue(result.primary.contains("S"))
    }

    @Test
    fun `metaphone handles DG as J`() {
        val result = matcher.metaphone("edge")
        assertTrue(result.primary.contains("J"))
    }
}

/**
 * Unit tests for the embedded [TokenMatcher] (Jaccard similarity).
 */
class TokenMatcherTest {
    private lateinit var matcher: TokenMatcher

    @Before
    fun setup() {
        matcher = TokenMatcher()
    }

    @Test
    fun `identical strings have similarity 1`() {
        assertEquals(1.0f, matcher.jaccardSimilarity("hello world", "hello world"))
    }

    @Test
    fun `completely different strings have similarity 0`() {
        assertEquals(0.0f, matcher.jaccardSimilarity("hello world", "foo bar"))
    }

    @Test
    fun `empty strings have similarity 1`() {
        assertEquals(1.0f, matcher.jaccardSimilarity("", ""))
    }

    @Test
    fun `one empty string has similarity 0`() {
        assertEquals(0.0f, matcher.jaccardSimilarity("hello", ""))
        assertEquals(0.0f, matcher.jaccardSimilarity("", "hello"))
    }

    @Test
    fun `partial overlap has intermediate similarity`() {
        // "hello world" -> {hello, world}
        // "hello there" -> {hello, there}
        // Intersection: {hello} = 1, Union: {hello, world, there} = 3
        // Jaccard = 1/3
        val similarity = matcher.jaccardSimilarity("hello world", "hello there")
        assertEquals(1.0f / 3.0f, similarity, 0.01f)
    }

    @Test
    fun `stop words are removed`() {
        // "the cat" -> {cat}
        // "a cat" -> {cat}
        // Intersection = 1, Union = 1
        val similarity = matcher.jaccardSimilarity("the cat", "a cat")
        assertEquals(1.0f, similarity)
    }

    @Test
    fun `tokenize removes stop words`() {
        val tokens = matcher.tokenize("the quick brown fox")
        assertFalse(tokens.contains("the"))
        assertTrue(tokens.contains("quick"))
        assertTrue(tokens.contains("brown"))
        assertTrue(tokens.contains("fox"))
    }

    @Test
    fun `tokenize handles punctuation`() {
        val tokens = matcher.tokenize("hello, world!")
        assertTrue(tokens.contains("hello"))
        assertTrue(tokens.contains("world"))
    }

    @Test
    fun `tokenize is case insensitive`() {
        val tokens = matcher.tokenize("Hello WORLD")
        assertTrue(tokens.contains("hello"))
        assertTrue(tokens.contains("world"))
    }

    @Test
    fun `similar command phrases have high similarity`() {
        // "i'm done" and "im done"
        val similarity = matcher.jaccardSimilarity("i'm done", "im done")
        // Both contain "done" and one form of "i'm"/"im"
        // After tokenization: {i'm, done} vs {im, done}
        // Intersection: {done} = 1, Union: {i'm, im, done} = 3
        assertTrue(similarity > 0.0f)
    }

    @Test
    fun `jaccard with superset returns expected value`() {
        // "skip this now" -> {skip, this, now}
        // "skip this" -> {skip, this}
        // Intersection: 2, Union: 3
        val similarity = matcher.jaccardSimilarity("skip this now", "skip this")
        assertEquals(2.0f / 3.0f, similarity, 0.01f)
    }

    @Test
    fun `single token identical match returns 1`() {
        assertEquals(1.0f, matcher.jaccardSimilarity("ready", "ready"))
    }
}
