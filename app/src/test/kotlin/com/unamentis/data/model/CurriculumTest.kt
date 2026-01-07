package com.unamentis.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for Curriculum data models.
 *
 * Verifies serialization, deserialization, and data integrity.
 */
class CurriculumTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `curriculum serialization works correctly`() {
        // Given
        val curriculum = Curriculum(
            id = "test-curriculum-001",
            title = "Test Curriculum",
            description = "A test curriculum",
            version = "1.0.0",
            topics = emptyList()
        )

        // When
        val jsonString = json.encodeToString(curriculum)

        // Then
        assertNotNull(jsonString)
        assertEquals(true, jsonString.contains("test-curriculum-001"))
        assertEquals(true, jsonString.contains("Test Curriculum"))
    }

    @Test
    fun `curriculum deserialization works correctly`() {
        // Given
        val jsonString = """
            {
                "id": "test-curriculum-001",
                "title": "Test Curriculum",
                "description": "A test curriculum",
                "version": "1.0.0",
                "topics": []
            }
        """.trimIndent()

        // When
        val curriculum = json.decodeFromString<Curriculum>(jsonString)

        // Then
        assertEquals("test-curriculum-001", curriculum.id)
        assertEquals("Test Curriculum", curriculum.title)
        assertEquals("1.0.0", curriculum.version)
        assertEquals(0, curriculum.topics.size)
    }

    @Test
    fun `topic with transcript segments serializes correctly`() {
        // Given
        val topic = Topic(
            id = "topic-001",
            title = "Introduction",
            orderIndex = 0,
            transcript = listOf(
                TranscriptSegment(
                    id = "seg-001",
                    type = "content",
                    content = "Welcome to the course."
                )
            )
        )

        // When
        val jsonString = json.encodeToString(topic)

        // Then
        assertNotNull(jsonString)
        assertEquals(true, jsonString.contains("topic-001"))
        assertEquals(true, jsonString.contains("content"))
    }

    @Test
    fun `stopping point with expected concepts works correctly`() {
        // Given
        val stoppingPoint = StoppingPoint(
            type = "quiz",
            prompt = "What is the capital of France?",
            expectedConcepts = listOf("Paris", "France")
        )

        val segment = TranscriptSegment(
            id = "seg-002",
            type = "checkpoint",
            content = "Let's test your knowledge.",
            stoppingPoint = stoppingPoint
        )

        // When
        val jsonString = json.encodeToString(segment)

        // Then
        assertNotNull(jsonString)
        assertEquals(true, jsonString.contains("quiz"))
        assertEquals(true, jsonString.contains("Paris"))
    }
}
