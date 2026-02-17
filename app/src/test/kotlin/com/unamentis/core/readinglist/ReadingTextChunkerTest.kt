package com.unamentis.core.readinglist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ReadingTextChunker.
 *
 * Tests cover:
 * - Basic sentence chunking
 * - Chunk word count targets
 * - Duration estimation
 * - Character offset tracking
 * - Empty and blank input handling
 * - Single sentence handling
 * - Minimum chunk merging
 */
class ReadingTextChunkerTest {
    @Test
    fun `chunks text into multiple parts`() {
        val text = buildLongText(10) // 10 sentences
        val chunks = ReadingTextChunker.chunk(text)

        assertTrue("Should produce multiple chunks", chunks.size > 1)
    }

    @Test
    fun `chunks have sequential indices`() {
        val text = buildLongText(10)
        val chunks = ReadingTextChunker.chunk(text)

        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.index)
        }
    }

    @Test
    fun `chunks have positive duration estimates`() {
        val text = buildLongText(5)
        val chunks = ReadingTextChunker.chunk(text)

        chunks.forEach { chunk ->
            assertTrue(
                "Duration should be positive: ${chunk.estimatedDurationSeconds}",
                chunk.estimatedDurationSeconds > 0,
            )
        }
    }

    @Test
    fun `character offsets are non-decreasing`() {
        val text = buildLongText(10)
        val chunks = ReadingTextChunker.chunk(text)

        for (i in 1 until chunks.size) {
            assertTrue(
                "Offset ${chunks[i].characterOffset} should be >= ${chunks[i - 1].characterOffset}",
                chunks[i].characterOffset >= chunks[i - 1].characterOffset,
            )
        }
    }

    @Test
    fun `empty text returns empty list`() {
        val chunks = ReadingTextChunker.chunk("")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `blank text returns empty list`() {
        val chunks = ReadingTextChunker.chunk("   \n\n   ")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `single short sentence returns one chunk`() {
        val text = "This is a single sentence."
        val chunks = ReadingTextChunker.chunk(text)

        assertEquals(1, chunks.size)
        assertEquals("This is a single sentence.", chunks[0].text)
    }

    @Test
    fun `respects custom config target words`() {
        val text = buildLongText(20)
        val smallConfig =
            ReadingTextChunker.ChunkConfig(
                targetWords = 10,
                maxWords = 15,
                minWords = 5,
            )

        val chunks = ReadingTextChunker.chunk(text, smallConfig)

        // With smaller target, should produce more chunks
        val defaultChunks = ReadingTextChunker.chunk(text)
        assertTrue(
            "Small target should produce more chunks",
            chunks.size >= defaultChunks.size,
        )
    }

    @Test
    fun `all chunks contain non-empty text`() {
        val text = buildLongText(15)
        val chunks = ReadingTextChunker.chunk(text)

        chunks.forEach { chunk ->
            assertTrue("Chunk text should not be blank", chunk.text.isNotBlank())
        }
    }

    @Test
    fun `normalizes whitespace in input`() {
        val text = "First sentence.   Second sentence.\n\n\n\n\nThird sentence."
        val chunks = ReadingTextChunker.chunk(text)

        chunks.forEach { chunk ->
            assertTrue(
                "Should not have excessive whitespace",
                !chunk.text.contains("  "),
            )
        }
    }

    @Test
    fun `extractAndChunk processes markdown`() {
        val markdown = "# Title\n\nThis is **bold** text. And some more text here."
        val chunks = ReadingTextChunker.extractAndChunk(markdown, "markdown")

        chunks.forEach { chunk ->
            assertTrue("Should not contain markdown syntax", !chunk.text.contains("**"))
            assertTrue("Should not contain header markers", !chunk.text.contains("#"))
        }
    }

    /**
     * Build a text with N sentences, each approximately 15-20 words.
     */
    private fun buildLongText(sentenceCount: Int): String {
        val sentences =
            listOf(
                "The quick brown fox jumps over the lazy dog in the park on a sunny day.",
                "Scientists recently discovered a new species of deep-sea fish near the ocean floor.",
                "The annual conference attracted more than five hundred participants from around the world.",
                "Modern artificial intelligence systems can process vast amounts of data very quickly.",
                "The ancient ruins were found beneath several layers of volcanic ash and sediment.",
                "Researchers at the university published their findings in a prestigious scientific journal.",
                "The new transportation system will significantly reduce commute times for local residents.",
                "Classical music has been shown to have positive effects on cognitive development.",
                "The international space station continues to orbit earth every ninety minutes.",
                "Environmental conservation efforts have led to the recovery of several endangered species.",
                "The medieval castle stood atop a hill overlooking the peaceful valley below.",
                "Quantum computing promises to revolutionize the way we solve computational problems.",
                "The chef prepared a magnificent feast using only locally sourced organic ingredients.",
                "Digital technology has transformed how people communicate and share information globally.",
                "The marathon runner crossed the finish line after four hours of intense running.",
                "Renewable energy sources are becoming increasingly cost effective compared to fossil fuels.",
                "The archaeological team carefully excavated artifacts dating back several thousand years.",
                "Marine biologists are studying the effects of ocean acidification on coral reefs.",
                "The symphony orchestra performed a stunning rendition of the famous classical piece.",
                "Advances in medical technology have greatly improved patient outcomes in recent years.",
            )

        return (0 until sentenceCount).joinToString(" ") { i ->
            sentences[i % sentences.size]
        }
    }
}
