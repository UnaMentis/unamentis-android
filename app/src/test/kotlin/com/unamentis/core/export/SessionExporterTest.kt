package com.unamentis.core.export

import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SessionExporter.
 */
class SessionExporterTest {
    private lateinit var exporter: SessionExporter
    private lateinit var testSession: Session
    private lateinit var testTranscript: List<TranscriptEntry>

    @Before
    fun setup() {
        exporter = SessionExporter()

        // Test session: 2024-01-18 16:00:00 to 17:00:00 UTC
        testSession =
            Session(
                id = "test-session-123",
                curriculumId = "curriculum-456",
                topicId = "topic-789",
                startTime = 1705593600000L,
                endTime = 1705597200000L,
                turnCount = 5,
            )

        testTranscript =
            listOf(
                TranscriptEntry(
                    id = "entry-1",
                    sessionId = "test-session-123",
                    role = "user",
                    text = "Hello, can you help me learn?",
                    timestamp = 1705593600000L,
                ),
                TranscriptEntry(
                    id = "entry-2",
                    sessionId = "test-session-123",
                    role = "assistant",
                    text = "Of course! What would you like to learn about?",
                    timestamp = 1705593605000L,
                ),
                TranscriptEntry(
                    id = "entry-3",
                    sessionId = "test-session-123",
                    role = "user",
                    text = "I want to learn about \"quotes\" and special characters: <>&",
                    timestamp = 1705593610000L,
                ),
            )
    }

    @Test
    fun `export to JSON returns success with valid content`() {
        val result = exporter.export(testSession, testTranscript, ExportFormat.JSON)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        assertEquals(ExportFormat.JSON, success.format)
        assertTrue(success.suggestedFilename.endsWith(".json"))
        assertTrue(success.suggestedFilename.startsWith("unamentis_"))

        // Verify JSON structure - session data is nested under "session" key
        assertTrue(success.content.contains("\"session\":"))
        assertTrue(success.content.contains("\"id\": \"test-session-123\""))
        assertTrue(success.content.contains("\"curriculumId\": \"curriculum-456\""))
        assertTrue(success.content.contains("\"topicId\": \"topic-789\""))
        assertTrue(success.content.contains("\"turnCount\": 5"))
        assertTrue(success.content.contains("\"transcript\":"))
    }

    @Test
    fun `export to JSON properly escapes special characters`() {
        val result = exporter.export(testSession, testTranscript, ExportFormat.JSON)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        // The third entry contains quotes that should be escaped
        assertTrue(success.content.contains("\\\"quotes\\\""))
    }

    @Test
    fun `export to TEXT returns success with readable content`() {
        val result = exporter.export(testSession, testTranscript, ExportFormat.TEXT)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        assertEquals(ExportFormat.TEXT, success.format)
        assertTrue(success.suggestedFilename.endsWith(".txt"))

        // Verify text structure
        assertTrue(success.content.contains("UnaMentis Session Export"))
        assertTrue(success.content.contains("Session ID: test-session-123"))
        assertTrue(success.content.contains("Curriculum: curriculum-456"))
        // "Turns:" is the actual format, not "Turn Count:"
        assertTrue(success.content.contains("Turns: 5"))
        assertTrue(success.content.contains("You:"))
        assertTrue(success.content.contains("AI Tutor:"))
    }

    @Test
    fun `export to MARKDOWN returns success with formatted content`() {
        val result = exporter.export(testSession, testTranscript, ExportFormat.MARKDOWN)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        assertEquals(ExportFormat.MARKDOWN, success.format)
        assertTrue(success.suggestedFilename.endsWith(".md"))

        // Verify markdown structure
        assertTrue(success.content.contains("# UnaMentis Session Export"))
        assertTrue(success.content.contains("## Session Information"))
        assertTrue(success.content.contains("## Transcript"))
        assertTrue(success.content.contains("**You**"))
        assertTrue(success.content.contains("**AI Tutor**"))
    }

    @Test
    fun `export to CSV returns success with tabular data`() {
        val result = exporter.export(testSession, testTranscript, ExportFormat.CSV)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        assertEquals(ExportFormat.CSV, success.format)
        assertTrue(success.suggestedFilename.endsWith(".csv"))

        // Verify CSV structure - header is lowercase, roles are formatted
        assertTrue(success.content.contains("timestamp,role,text"))
        assertTrue(success.content.contains("User"))
        assertTrue(success.content.contains("AI Tutor"))
    }

    @Test
    fun `export to CSV properly escapes commas and quotes`() {
        val sessionWithCommas = testSession
        val transcriptWithCommas =
            listOf(
                TranscriptEntry(
                    id = "entry-special",
                    sessionId = "test-session-123",
                    role = "user",
                    text = "Hello, world. This has \"quotes\" and commas, too.",
                    timestamp = 1705593600000L,
                ),
            )

        val result = exporter.export(sessionWithCommas, transcriptWithCommas, ExportFormat.CSV)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        // CSV should escape quotes by doubling them
        assertTrue(success.content.contains("\"\"quotes\"\""))
    }

    @Test
    fun `export with empty transcript returns success`() {
        val result = exporter.export(testSession, emptyList(), ExportFormat.JSON)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        // Empty transcript array - the JSON output has "transcript": [  ] with newlines
        assertTrue(success.content.contains("\"transcript\": ["))
        assertTrue(success.content.contains("  ]"))
    }

    @Test
    fun `export with null curriculum and topic IDs handles gracefully`() {
        val sessionWithNulls =
            Session(
                id = "test-session-123",
                curriculumId = null,
                topicId = null,
                startTime = 1705593600000L,
                endTime = null,
                turnCount = 0,
            )

        val result = exporter.export(sessionWithNulls, emptyList(), ExportFormat.JSON)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertTrue(success.content.contains("\"curriculumId\": null"))
        assertTrue(success.content.contains("\"topicId\": null"))
    }

    @Test
    fun `suggested filename includes date`() {
        val result = exporter.export(testSession, testTranscript, ExportFormat.JSON)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success

        // Filename format: unamentis_{topicId_or_session}_{date}.json
        // testSession has topicId = "topic-789"
        val filenameRegex = Regex("unamentis_topic-789_\\d{4}-\\d{2}-\\d{2}_\\d{6}\\.json")
        assertTrue(
            "Filename '${ success.suggestedFilename}' should match pattern",
            filenameRegex.matches(success.suggestedFilename),
        )
    }

    @Test
    fun `all export formats return correct mime types`() {
        assertEquals("application/json", ExportFormat.JSON.mimeType)
        assertEquals("text/plain", ExportFormat.TEXT.mimeType)
        assertEquals("text/markdown", ExportFormat.MARKDOWN.mimeType)
        assertEquals("text/csv", ExportFormat.CSV.mimeType)
    }

    @Test
    fun `all export formats return correct extensions`() {
        assertEquals("json", ExportFormat.JSON.extension)
        assertEquals("txt", ExportFormat.TEXT.extension)
        assertEquals("md", ExportFormat.MARKDOWN.extension)
        assertEquals("csv", ExportFormat.CSV.extension)
    }

    @Test
    fun `all export formats have display names`() {
        assertNotNull(ExportFormat.JSON.displayName)
        assertNotNull(ExportFormat.TEXT.displayName)
        assertNotNull(ExportFormat.MARKDOWN.displayName)
        assertNotNull(ExportFormat.CSV.displayName)
    }
}
