package com.unamentis.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Session data models.
 *
 * Verifies session state transitions and data integrity.
 */
class SessionTest {
    @Test
    fun `session state enum has correct values`() {
        // Verify all expected states exist
        assertEquals(SessionState.IDLE, SessionState.valueOf("IDLE"))
        assertEquals(SessionState.USER_SPEAKING, SessionState.valueOf("USER_SPEAKING"))
        assertEquals(SessionState.AI_THINKING, SessionState.valueOf("AI_THINKING"))
        assertEquals(SessionState.AI_SPEAKING, SessionState.valueOf("AI_SPEAKING"))
        assertEquals(SessionState.INTERRUPTED, SessionState.valueOf("INTERRUPTED"))
        assertEquals(SessionState.PAUSED, SessionState.valueOf("PAUSED"))
        assertEquals(SessionState.ERROR, SessionState.valueOf("ERROR"))
    }

    @Test
    fun `transcript entry creation works correctly`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val entry =
            TranscriptEntry(
                id = "entry-001",
                sessionId = "session-001",
                role = "user",
                text = "Hello, how are you?",
                timestamp = timestamp,
            )

        // Then
        assertEquals("entry-001", entry.id)
        assertEquals("user", entry.role)
        assertEquals("Hello, how are you?", entry.text)
        assertEquals(timestamp, entry.timestamp)
        assertEquals(true, entry.metadata.isEmpty())
    }

    @Test
    fun `session with transcript entries works correctly`() {
        // Given
        val session =
            Session(
                id = "session-001",
                startTime = System.currentTimeMillis(),
                transcript =
                    listOf(
                        TranscriptEntry(
                            id = "entry-001",
                            sessionId = "session-001",
                            role = "user",
                            text = "Hello",
                            timestamp = System.currentTimeMillis(),
                        ),
                        TranscriptEntry(
                            id = "entry-002",
                            sessionId = "session-001",
                            role = "assistant",
                            text = "Hi there!",
                            timestamp = System.currentTimeMillis(),
                        ),
                    ),
            )

        // Then
        assertEquals(2, session.transcript.size)
        assertEquals("user", session.transcript[0].role)
        assertEquals("assistant", session.transcript[1].role)
    }

    @Test
    fun `topic progress tracks mastery correctly`() {
        // Given
        val progress =
            TopicProgress(
                topicId = "topic-001",
                curriculumId = "curriculum-001",
                timeSpentSeconds = 1800, // 30 minutes
                masteryLevel = 0.75f, // 75% mastery
                lastAccessedAt = System.currentTimeMillis(),
                completedSegments = listOf("seg-001", "seg-002", "seg-003"),
            )

        // Then
        assertEquals(0.75f, progress.masteryLevel, 0.001f)
        assertEquals(3, progress.completedSegments.size)
        assertEquals(1800L, progress.timeSpentSeconds)
    }
}
