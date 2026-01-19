package com.unamentis.core.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for UMCFParser.
 */
class UMCFParserTest {
    private lateinit var parser: UMCFParser

    @Before
    fun setup() {
        parser = UMCFParser()
    }

    @Test
    fun `parses minimal UMCF document`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-001"},
                "title": "Test Curriculum",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Introduction",
                        "transcript": {
                            "segments": [
                                {
                                    "id": "seg-001",
                                    "type": "content",
                                    "content": "Welcome to this course."
                                }
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        assertEquals("curriculum-001", curriculum!!.id)
        assertEquals("Test Curriculum", curriculum.title)
        assertEquals(1, curriculum.topics.size)
        assertEquals("topic-001", curriculum.topics[0].id)
        assertEquals("Introduction", curriculum.topics[0].title)
        assertEquals(1, curriculum.topics[0].transcript.size)
        assertEquals("Welcome to this course.", curriculum.topics[0].transcript[0].content)
    }

    @Test
    fun `parses UMCF with nested content hierarchy`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-002"},
                "title": "Nested Curriculum",
                "content": [
                    {
                        "id": "unit-001",
                        "title": "Unit 1",
                        "type": "unit",
                        "children": [
                            {
                                "id": "topic-001",
                                "title": "Topic 1.1",
                                "transcript": {
                                    "segments": [
                                        {"content": "First topic content"}
                                    ]
                                }
                            },
                            {
                                "id": "topic-002",
                                "title": "Topic 1.2",
                                "transcript": {
                                    "segments": [
                                        {"content": "Second topic content"}
                                    ]
                                }
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        assertEquals(2, curriculum!!.topics.size)
        assertEquals("Topic 1.1", curriculum.topics[0].title)
        assertEquals("Topic 1.2", curriculum.topics[1].title)
        assertEquals(0, curriculum.topics[0].orderIndex)
        assertEquals(1, curriculum.topics[1].orderIndex)
    }

    @Test
    fun `parses UMCF with checkpoints`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-003"},
                "title": "Curriculum with Checkpoints",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Quiz Topic",
                        "transcript": {
                            "segments": [
                                {
                                    "id": "seg-001",
                                    "type": "content",
                                    "content": "Learn this concept."
                                },
                                {
                                    "id": "seg-002",
                                    "type": "checkpoint",
                                    "content": "Check your understanding.",
                                    "checkpoint": {
                                        "type": "comprehension",
                                        "question": "What did you learn?",
                                        "expected_concepts": ["concept1", "concept2"],
                                        "hints": ["Think about X", "Remember Y"]
                                    }
                                }
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        val topic = curriculum!!.topics[0]
        assertEquals(2, topic.transcript.size)

        val checkpointSegment = topic.transcript[1]
        assertNotNull(checkpointSegment.stoppingPoint)
        assertEquals("comprehension", checkpointSegment.stoppingPoint!!.type)
        assertEquals("What did you learn?", checkpointSegment.stoppingPoint!!.prompt)
        assertEquals(listOf("concept1", "concept2"), checkpointSegment.stoppingPoint!!.expectedConcepts)
        assertEquals(listOf("Think about X", "Remember Y"), checkpointSegment.stoppingPoint!!.hints)
    }

    @Test
    fun `parses UMCF with learning objectives`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-004"},
                "title": "Curriculum with Objectives",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Topic with Objectives",
                        "learning_objectives": [
                            {"description": "Understand concept A"},
                            {"description": "Apply concept B"}
                        ],
                        "transcript": {
                            "segments": [
                                {"content": "Content here"}
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        val topic = curriculum!!.topics[0]
        assertEquals(2, topic.learningObjectives.size)
        assertEquals("Understand concept A", topic.learningObjectives[0])
        assertEquals("Apply concept B", topic.learningObjectives[1])
    }

    @Test
    fun `parses UMCF with visual assets`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-005"},
                "title": "Curriculum with Assets",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Visual Topic",
                        "transcript": {
                            "segments": [
                                {
                                    "content": "Look at this diagram.",
                                    "visual_id": "diagram-001"
                                }
                            ]
                        },
                        "media": {
                            "embedded": [
                                {
                                    "id": "diagram-001",
                                    "type": "diagram",
                                    "url": "https://example.com/diagram.png",
                                    "mime_type": "image/png",
                                    "caption": "A helpful diagram"
                                }
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        val topic = curriculum!!.topics[0]
        assertEquals(1, topic.visualAssets.size)
        assertEquals("diagram-001", topic.visualAssets[0].id)
        assertEquals("image/png", topic.visualAssets[0].mimeType)
        assertEquals("https://example.com/diagram.png", topic.visualAssets[0].url)
        assertEquals("A helpful diagram", topic.visualAssets[0].caption)

        // Check segment references the visual
        assertEquals("diagram-001", topic.transcript[0].visualAssetId)
    }

    @Test
    fun `parses UMCF with glossary`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-006"},
                "title": "Curriculum with Glossary",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Basic Topic",
                        "transcript": {
                            "segments": [
                                {"content": "Content here"}
                            ]
                        }
                    }
                ],
                "glossary": {
                    "terms": {
                        "Algorithm": {
                            "definition": "A step-by-step procedure for solving a problem",
                            "examples": ["Sorting algorithm", "Search algorithm"]
                        },
                        "Data Structure": {
                            "definition": "A way of organizing data"
                        }
                    }
                }
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        assertEquals(2, curriculum!!.glossaryTerms.size)

        val algorithm = curriculum.glossaryTerms.find { it.term == "Algorithm" }
        assertNotNull(algorithm)
        assertEquals("A step-by-step procedure for solving a problem", algorithm!!.definition)
        assertEquals(2, algorithm.examples.size)
    }

    @Test
    fun `parses UMCF with educational metadata`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-007"},
                "title": "Full Metadata Curriculum",
                "description": "A comprehensive curriculum",
                "version": {
                    "version": "2.1.0",
                    "status": "published"
                },
                "educational": {
                    "difficulty": "intermediate",
                    "age_range": "16-18",
                    "duration": "PT4H"
                },
                "metadata": {
                    "keywords": ["math", "algebra", "equations"]
                },
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Topic",
                        "transcript": {
                            "segments": [
                                {"content": "Content"}
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        assertEquals("2.1.0", curriculum!!.version)
        assertEquals("A comprehensive curriculum", curriculum.description)
        assertEquals("intermediate", curriculum.difficulty)
        assertEquals("16-18", curriculum.ageRange)
        assertEquals("PT4H", curriculum.duration)
        assertEquals(listOf("math", "algebra", "equations"), curriculum.keywords)
    }

    @Test
    fun `parses UMCF with spoken text variants`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-008"},
                "title": "TTS Optimized",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Spoken Topic",
                        "transcript": {
                            "segments": [
                                {
                                    "content": "The formula E=mc² demonstrates...",
                                    "spoken_text": "The formula E equals m c squared demonstrates..."
                                }
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        val segment = curriculum!!.topics[0].transcript[0]
        assertEquals("The formula E=mc² demonstrates...", segment.content)
        assertEquals("The formula E equals m c squared demonstrates...", segment.spokenText)
    }

    @Test(expected = UMCFParseException::class)
    fun `throws exception for missing id`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "title": "No ID Curriculum",
                "content": []
            }
            """.trimIndent()

        parser.parse(json)
    }

    @Test(expected = UMCFParseException::class)
    fun `throws exception for empty content`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-bad"},
                "title": "Empty Curriculum",
                "content": []
            }
            """.trimIndent()

        parser.parse(json)
    }

    @Test
    fun `handles missing optional fields gracefully`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "minimal"},
                "title": "Minimal",
                "content": [
                    {
                        "id": "topic",
                        "title": "Topic",
                        "transcript": {
                            "segments": [
                                {"content": "Just content, no optional fields"}
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        assertEquals("", curriculum!!.description)
        assertTrue(curriculum.glossaryTerms.isEmpty())
        assertTrue(curriculum.learningObjectives.isEmpty())
        assertEquals(null, curriculum.difficulty)
    }

    @Test
    fun `generates segment IDs when missing`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "curriculum-009"},
                "title": "No Segment IDs",
                "content": [
                    {
                        "id": "topic-001",
                        "title": "Topic",
                        "transcript": {
                            "segments": [
                                {"content": "First segment"},
                                {"content": "Second segment"}
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json)

        assertNotNull(curriculum)
        val segments = curriculum!!.topics[0].transcript
        assertEquals("segment-0", segments[0].id)
        assertEquals("segment-1", segments[1].id)
    }

    @Test
    fun `parses bytes correctly`() {
        val json =
            """
            {
                "umcf": "1.0.0",
                "id": {"id": "bytes-test"},
                "title": "Byte Input",
                "content": [
                    {
                        "id": "topic",
                        "title": "Topic",
                        "transcript": {
                            "segments": [{"content": "Content"}]
                        }
                    }
                ]
            }
            """.trimIndent()

        val curriculum = parser.parse(json.toByteArray())

        assertNotNull(curriculum)
        assertEquals("bytes-test", curriculum!!.id)
    }
}
