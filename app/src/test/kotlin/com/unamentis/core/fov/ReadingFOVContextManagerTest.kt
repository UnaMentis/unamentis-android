package com.unamentis.core.fov

import com.unamentis.services.readingplayback.ReadingChunkData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ReadingFOVContextManager.
 *
 * Tests cover:
 * - Context window builds with correct preceding/current/following text
 * - Truncation of long sections
 * - Edge cases (first chunk, last chunk, single chunk)
 * - Barge-in message building with conversation history
 * - Token estimation
 */
class ReadingFOVContextManagerTest {
    private lateinit var manager: ReadingFOVContextManager

    private val testChunks =
        listOf(
            ReadingChunkData(index = 0, text = "Chapter 1: Introduction to the topic."),
            ReadingChunkData(index = 1, text = "The first concept we explore is fundamental."),
            ReadingChunkData(index = 2, text = "Building on that, the second concept emerges."),
            ReadingChunkData(index = 3, text = "The third concept ties everything together."),
            ReadingChunkData(index = 4, text = "In conclusion, we have covered all key points."),
        )

    @Before
    fun setUp() {
        manager = ReadingFOVContextManager()
    }

    @Test
    fun `buildContext includes current chunk text`() {
        val window = manager.buildContext(testChunks, currentIndex = 2, title = "Test Doc")

        assertEquals("Building on that, the second concept emerges.", window.currentText)
    }

    @Test
    fun `buildContext includes preceding chunks`() {
        val window = manager.buildContext(testChunks, currentIndex = 3, title = "Test Doc")

        assertTrue(window.precedingText.contains("Introduction"))
        assertTrue(window.precedingText.contains("first concept"))
        assertTrue(window.precedingText.contains("second concept"))
    }

    @Test
    fun `buildContext includes following chunks`() {
        val window = manager.buildContext(testChunks, currentIndex = 2, title = "Test Doc")

        assertTrue(window.followingText.contains("third concept"))
        assertTrue(window.followingText.contains("conclusion"))
    }

    @Test
    fun `buildContext at first chunk has no preceding text`() {
        val window = manager.buildContext(testChunks, currentIndex = 0, title = "Test Doc")

        assertTrue(window.precedingText.isEmpty())
    }

    @Test
    fun `buildContext at last chunk has no following text`() {
        val window = manager.buildContext(testChunks, currentIndex = 4, title = "Test Doc")

        assertTrue(window.followingText.isEmpty())
    }

    @Test
    fun `buildContext with single chunk works`() {
        val singleChunk = listOf(ReadingChunkData(index = 0, text = "Only chunk."))

        val window = manager.buildContext(singleChunk, currentIndex = 0, title = "Single")

        assertEquals("Only chunk.", window.currentText)
        assertTrue(window.precedingText.isEmpty())
        assertTrue(window.followingText.isEmpty())
    }

    @Test
    fun `buildContext includes document title and author`() {
        val window =
            manager.buildContext(
                testChunks,
                currentIndex = 2,
                title = "My Document",
                author = "John Doe",
            )

        assertTrue(window.fullContext.contains("My Document"))
        assertTrue(window.fullContext.contains("John Doe"))
    }

    @Test
    fun `buildContext includes progress info`() {
        val window = manager.buildContext(testChunks, currentIndex = 2, title = "Test")

        assertTrue(window.fullContext.contains("Segment 3 of 5"))
    }

    @Test
    fun `buildContext truncates long sections`() {
        manager.maxSectionCharacters = 50

        val longChunks =
            listOf(
                ReadingChunkData(index = 0, text = "A".repeat(100)),
                ReadingChunkData(index = 1, text = "B".repeat(30)),
            )

        val window = manager.buildContext(longChunks, currentIndex = 1, title = "Test")

        assertTrue(window.precedingText.length <= 50)
    }

    @Test
    fun `estimatedTokenCount returns reasonable estimate`() {
        val window = manager.buildContext(testChunks, currentIndex = 2, title = "Test")

        assertTrue(window.estimatedTokenCount > 0)
        assertEquals(window.fullContext.length / 4, window.estimatedTokenCount)
    }

    @Test
    fun `buildBargeInMessages creates correct message structure`() {
        val messages =
            manager.buildBargeInMessages(
                question = "What was the first concept?",
                chunks = testChunks,
                currentIndex = 2,
                title = "Test Doc",
            )

        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("What was the first concept?", messages[1].content)
    }

    @Test
    fun `buildBargeInMessages includes conversation history`() {
        val history =
            listOf(
                "What is chapter 1 about?" to "It introduces the topic.",
            )

        val messages =
            manager.buildBargeInMessages(
                question = "And chapter 2?",
                chunks = testChunks,
                currentIndex = 2,
                title = "Test Doc",
                conversationHistory = history,
            )

        assertEquals(4, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("What is chapter 1 about?", messages[1].content)
        assertEquals("assistant", messages[2].role)
        assertEquals("It introduces the topic.", messages[2].content)
        assertEquals("user", messages[3].role)
        assertEquals("And chapter 2?", messages[3].content)
    }

    @Test
    fun `buildContext with custom chunk counts`() {
        manager.precedingChunkCount = 1
        manager.followingChunkCount = 1

        val window = manager.buildContext(testChunks, currentIndex = 2, title = "Test")

        // With preceding=1, should only include chunk 1 (not chunk 0)
        assertTrue(!window.precedingText.contains("Introduction"))
        assertTrue(window.precedingText.contains("first concept"))

        // With following=1, should only include chunk 3 (not chunk 4)
        assertTrue(window.followingText.contains("third concept"))
        assertTrue(!window.followingText.contains("conclusion"))
    }
}
