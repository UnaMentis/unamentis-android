package com.unamentis.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a complete curriculum in the UMCF (Una Mentis Curriculum Format).
 *
 * A curriculum contains structured learning content organized into topics,
 * with associated metadata, learning objectives, and visual assets.
 *
 * @property id Unique identifier for the curriculum
 * @property title Human-readable title
 * @property description Brief description of the curriculum content
 * @property version Curriculum version (semantic versioning)
 * @property topics List of topics in sequential order
 * @property glossaryTerms Optional list of key terms and definitions
 * @property learningObjectives Top-level learning goals for the curriculum
 * @property difficulty Difficulty level (e.g., "beginner", "intermediate", "advanced")
 * @property ageRange Recommended age range (e.g., "18+", "12-16")
 * @property duration Estimated total duration (ISO 8601 duration format)
 * @property keywords Searchable keywords
 */
@Serializable
data class Curriculum(
    val id: String,
    val title: String,
    val description: String,
    val version: String,
    val topics: List<Topic>,
    val glossaryTerms: List<GlossaryTerm> = emptyList(),
    val learningObjectives: List<String> = emptyList(),
    val difficulty: String? = null,
    val ageRange: String? = null,
    val duration: String? = null,
    val keywords: List<String> = emptyList()
)

/**
 * Represents a single topic within a curriculum.
 *
 * Topics are the primary organizational unit of curriculum content,
 * containing transcript segments, learning objectives, and visual assets.
 *
 * @property id Unique identifier for the topic
 * @property title Human-readable title
 * @property orderIndex Sequential position in curriculum (0-indexed)
 * @property transcript List of transcript segments comprising the topic content
 * @property learningObjectives Specific learning goals for this topic
 * @property documents Associated reference documents
 * @property visualAssets Images, diagrams, and other visual aids
 * @property description Brief description of topic content
 * @property duration Estimated time to complete (ISO 8601 duration format)
 */
@Serializable
data class Topic(
    val id: String,
    val title: String,
    val orderIndex: Int,
    val transcript: List<TranscriptSegment>,
    val learningObjectives: List<String> = emptyList(),
    val documents: List<Document> = emptyList(),
    val visualAssets: List<VisualAsset> = emptyList(),
    val description: String? = null,
    val duration: String? = null
)

/**
 * Represents a segment of the topic transcript.
 *
 * Transcript segments define the content flow during a voice session,
 * including content delivery, checkpoints, and interactive activities.
 *
 * @property id Unique identifier for the segment
 * @property type Segment type ("content", "checkpoint", "activity")
 * @property content Main content text
 * @property spokenText Optional TTS-optimized version of content
 * @property stoppingPoint Optional point requiring user interaction
 * @property visualAssetId Reference to associated visual asset
 */
@Serializable
data class TranscriptSegment(
    val id: String,
    val type: String,  // "content", "checkpoint", "activity"
    val content: String,
    val spokenText: String? = null,
    val stoppingPoint: StoppingPoint? = null,
    val visualAssetId: String? = null
)

/**
 * Represents a stopping point in the curriculum flow.
 *
 * Stopping points pause content delivery to engage with the user,
 * such as quizzes, reflections, or practice activities.
 *
 * @property type Type of stopping point ("quiz", "reflection", "practice")
 * @property prompt Question or instruction for the user
 * @property expectedConcepts Key concepts expected in user response
 * @property hints Optional hints to guide user
 */
@Serializable
data class StoppingPoint(
    val type: String,  // "quiz", "reflection", "practice"
    val prompt: String,
    val expectedConcepts: List<String> = emptyList(),
    val hints: List<String> = emptyList()
)

/**
 * Represents a visual asset (image, diagram, etc.).
 *
 * Visual assets can be displayed during voice sessions to supplement
 * audio content with visual information.
 *
 * @property id Unique identifier
 * @property filename Original filename
 * @property mimeType MIME type (e.g., "image/png")
 * @property url URL for downloading the asset
 * @property caption Optional caption text
 */
@Serializable
data class VisualAsset(
    val id: String,
    val filename: String,
    val mimeType: String,
    val url: String? = null,
    val caption: String? = null
)

/**
 * Represents a reference document.
 *
 * @property id Unique identifier
 * @property title Document title
 * @property url URL to document
 * @property type Document type (e.g., "pdf", "text", "link")
 */
@Serializable
data class Document(
    val id: String,
    val title: String,
    val url: String,
    val type: String
)

/**
 * Represents a glossary term definition.
 *
 * @property term The term being defined
 * @property definition Definition of the term
 * @property examples Optional usage examples
 */
@Serializable
data class GlossaryTerm(
    val term: String,
    val definition: String,
    val examples: List<String> = emptyList()
)
