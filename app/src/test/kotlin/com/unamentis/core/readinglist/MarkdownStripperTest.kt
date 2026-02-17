package com.unamentis.core.readinglist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MarkdownStripper.
 *
 * Tests cover:
 * - YAML front matter removal
 * - HTML comment removal
 * - Code block handling
 * - Image alt text preservation
 * - Link text extraction
 * - Emphasis removal (bold, italic, strikethrough)
 * - Inline code stripping
 * - Header conversion
 * - Blockquote removal
 * - List marker removal
 * - Footnote removal
 * - HTML entity decoding
 * - Whitespace normalization
 */
class MarkdownStripperTest {
    @Test
    fun `strips YAML front matter`() {
        val input = "---\ntitle: Test\ndate: 2024-01-01\n---\nHello world"
        val result = MarkdownStripper.strip(input)
        assertEquals("Hello world", result)
    }

    @Test
    fun `removes HTML comments`() {
        val input = "Before <!-- This is a comment --> After"
        val result = MarkdownStripper.strip(input)
        assertEquals("Before After", result)
    }

    @Test
    fun `preserves code block content`() {
        val input = "Before\n```python\nprint('hello')\n```\nAfter"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("print('hello')"))
        assertTrue(result.contains("Before"))
        assertTrue(result.contains("After"))
    }

    @Test
    fun `keeps image alt text`() {
        val input = "![A beautiful sunset](https://example.com/sunset.jpg)"
        val result = MarkdownStripper.strip(input)
        assertEquals("A beautiful sunset.", result)
    }

    @Test
    fun `converts links to display text`() {
        val input = "Visit [Google](https://google.com) for more."
        val result = MarkdownStripper.strip(input)
        assertEquals("Visit Google for more.", result)
    }

    @Test
    fun `removes bold emphasis`() {
        val input = "This is **bold** text"
        val result = MarkdownStripper.strip(input)
        assertEquals("This is bold text", result)
    }

    @Test
    fun `removes italic emphasis`() {
        val input = "This is *italic* text"
        val result = MarkdownStripper.strip(input)
        assertEquals("This is italic text", result)
    }

    @Test
    fun `removes strikethrough`() {
        val input = "This is ~~deleted~~ text"
        val result = MarkdownStripper.strip(input)
        assertEquals("This is deleted text", result)
    }

    @Test
    fun `removes inline code`() {
        val input = "Use the `println` function"
        val result = MarkdownStripper.strip(input)
        assertEquals("Use the println function", result)
    }

    @Test
    fun `converts headers to plain text`() {
        val input = "## Section Title\nContent here"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("Section Title"))
        assertTrue(result.contains("Content here"))
        assertFalse(result.contains("##"))
    }

    @Test
    fun `removes blockquote markers`() {
        val input = "> This is a quote\n> Second line"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("This is a quote"))
        assertFalse(result.contains(">"))
    }

    @Test
    fun `removes unordered list markers`() {
        val input = "- Item one\n- Item two\n* Item three"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("Item one"))
        assertTrue(result.contains("Item two"))
        assertTrue(result.contains("Item three"))
        assertFalse(result.startsWith("-"))
    }

    @Test
    fun `removes ordered list markers`() {
        val input = "1. First\n2. Second\n3. Third"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("First"))
        assertTrue(result.contains("Second"))
        assertFalse(result.contains("1."))
    }

    @Test
    fun `removes footnote references`() {
        val input = "Main text[^1] continues here[^2]."
        val result = MarkdownStripper.strip(input)
        assertEquals("Main text continues here.", result)
    }

    @Test
    fun `decodes HTML entities`() {
        val input = "Tom &amp; Jerry &lt;3 &gt; friends &quot;forever&quot;"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("Tom & Jerry"))
        assertTrue(result.contains("\"forever\""))
    }

    @Test
    fun `decodes numeric HTML entities`() {
        val input = "&#65; &#x42;"
        val result = MarkdownStripper.strip(input)
        assertEquals("A B", result)
    }

    @Test
    fun `normalizes excessive whitespace`() {
        val input = "First\n\n\n\n\nSecond\n\n\n\n\nThird"
        val result = MarkdownStripper.strip(input)
        assertFalse(result.contains("\n\n\n"))
    }

    @Test
    fun `handles empty input`() {
        assertEquals("", MarkdownStripper.strip(""))
    }

    @Test
    fun `handles plain text without markdown`() {
        val input = "This is plain text with no markdown formatting."
        assertEquals(input, MarkdownStripper.strip(input))
    }

    @Test
    fun `removes horizontal rules`() {
        val input = "Before\n---\nAfter"
        val result = MarkdownStripper.strip(input)
        assertTrue(result.contains("Before"))
        assertTrue(result.contains("After"))
    }
}
