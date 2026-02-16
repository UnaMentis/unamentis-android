package com.unamentis.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Reading List data models.
 *
 * Tests cover:
 * - ReadingListSourceType: raw value mapping, file extension inference, URL inference
 * - ReadingListStatus: raw value mapping, computed properties
 * - AudioPreGenStatus: raw value mapping
 */
class ReadingListModelsTest {
    // ── ReadingListSourceType Tests ──

    @Test
    fun `source type rawValue roundtrips correctly`() {
        ReadingListSourceType.entries.forEach { type ->
            assertEquals(type, ReadingListSourceType.fromRawValue(type.rawValue))
        }
    }

    @Test
    fun `source type fromRawValue defaults to PLAIN_TEXT for unknown`() {
        assertEquals(ReadingListSourceType.PLAIN_TEXT, ReadingListSourceType.fromRawValue("unknown"))
        assertEquals(ReadingListSourceType.PLAIN_TEXT, ReadingListSourceType.fromRawValue(""))
    }

    @Test
    fun `source type fromFileExtension maps correctly`() {
        assertEquals(ReadingListSourceType.PDF, ReadingListSourceType.fromFileExtension("pdf"))
        assertEquals(ReadingListSourceType.PDF, ReadingListSourceType.fromFileExtension("PDF"))
        assertEquals(ReadingListSourceType.PLAIN_TEXT, ReadingListSourceType.fromFileExtension("txt"))
        assertEquals(ReadingListSourceType.MARKDOWN, ReadingListSourceType.fromFileExtension("md"))
        assertEquals(ReadingListSourceType.MARKDOWN, ReadingListSourceType.fromFileExtension("markdown"))
        assertEquals(ReadingListSourceType.WEB_ARTICLE, ReadingListSourceType.fromFileExtension("html"))
        assertEquals(ReadingListSourceType.WEB_ARTICLE, ReadingListSourceType.fromFileExtension("htm"))
        assertEquals(ReadingListSourceType.PLAIN_TEXT, ReadingListSourceType.fromFileExtension("docx"))
    }

    @Test
    fun `source type fromUrl infers correctly`() {
        assertEquals(ReadingListSourceType.PDF, ReadingListSourceType.fromUrl("https://example.com/doc.pdf"))
        assertEquals(ReadingListSourceType.MARKDOWN, ReadingListSourceType.fromUrl("https://example.com/README.md"))
        assertEquals(ReadingListSourceType.PLAIN_TEXT, ReadingListSourceType.fromUrl("file:///path/notes.txt"))
        assertEquals(ReadingListSourceType.WEB_ARTICLE, ReadingListSourceType.fromUrl("https://example.com/article"))
        assertEquals(ReadingListSourceType.PLAIN_TEXT, ReadingListSourceType.fromUrl("not-a-url"))
    }

    // ── ReadingListStatus Tests ──

    @Test
    fun `status rawValue roundtrips correctly`() {
        ReadingListStatus.entries.forEach { status ->
            assertEquals(status, ReadingListStatus.fromRawValue(status.rawValue))
        }
    }

    @Test
    fun `status fromRawValue defaults to UNREAD for unknown`() {
        assertEquals(ReadingListStatus.UNREAD, ReadingListStatus.fromRawValue("unknown"))
    }

    @Test
    fun `status isActive returns true for non-archived`() {
        assertTrue(ReadingListStatus.UNREAD.isActive)
        assertTrue(ReadingListStatus.IN_PROGRESS.isActive)
        assertTrue(ReadingListStatus.COMPLETED.isActive)
        assertFalse(ReadingListStatus.ARCHIVED.isActive)
    }

    @Test
    fun `status isPlayable returns true for in_progress and completed`() {
        assertFalse(ReadingListStatus.UNREAD.isPlayable)
        assertTrue(ReadingListStatus.IN_PROGRESS.isPlayable)
        assertTrue(ReadingListStatus.COMPLETED.isPlayable)
        assertFalse(ReadingListStatus.ARCHIVED.isPlayable)
    }

    @Test
    fun `status sortPriority orders correctly`() {
        val sorted =
            ReadingListStatus.entries.sortedBy { it.sortPriority }
        assertEquals(ReadingListStatus.IN_PROGRESS, sorted[0])
        assertEquals(ReadingListStatus.UNREAD, sorted[1])
        assertEquals(ReadingListStatus.COMPLETED, sorted[2])
        assertEquals(ReadingListStatus.ARCHIVED, sorted[3])
    }

    // ── AudioPreGenStatus Tests ──

    @Test
    fun `audio pre-gen status rawValue roundtrips correctly`() {
        AudioPreGenStatus.entries.forEach { status ->
            assertEquals(status, AudioPreGenStatus.fromRawValue(status.rawValue))
        }
    }

    @Test
    fun `audio pre-gen status fromRawValue defaults to NONE for unknown`() {
        assertEquals(AudioPreGenStatus.NONE, AudioPreGenStatus.fromRawValue("unknown"))
    }
}
