package com.unamentis.core.curriculum

import android.util.Log
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Document
import com.unamentis.data.model.GlossaryTerm
import com.unamentis.data.model.StoppingPoint
import com.unamentis.data.model.Topic
import com.unamentis.data.model.TranscriptSegment
import com.unamentis.data.model.VisualAsset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for UMCF (Una Mentis Curriculum Format) documents.
 *
 * Converts UMCF JSON documents into internal Curriculum data models.
 * Supports UMCF v1.0.0 specification with:
 * - Nested content hierarchy
 * - Transcript segments with voice profiles
 * - Visual asset collections
 * - Checkpoints and stopping points
 * - Glossary terms and pronunciation guides
 *
 * Usage:
 * ```kotlin
 * val parser = UMCFParser()
 * val curriculum = parser.parse(jsonString)
 * ```
 *
 * @see <a href="https://unamentis.com/schemas/umcf/v1.0.0">UMCF Specification</a>
 */
@Singleton
class UMCFParser
    @Inject
    constructor() {
        companion object {
            private const val TAG = "UMCFParser"
            private const val SUPPORTED_UMCF_VERSION = "1.0.0"
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }

        /**
         * Parse UMCF JSON string into a Curriculum object.
         *
         * @param jsonString UMCF document as JSON string
         * @return Parsed Curriculum or null if parsing fails
         * @throws UMCFParseException if the document is invalid
         */
        fun parse(jsonString: String): Curriculum? {
            return try {
                val document = json.decodeFromString<UMCFDocument>(jsonString)
                validateDocument(document)
                convertToCurriculum(document)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse UMCF document", e)
                throw UMCFParseException("Failed to parse UMCF document: ${e.message}", e)
            }
        }

        /**
         * Parse UMCF from a byte array.
         */
        fun parse(bytes: ByteArray): Curriculum? {
            return parse(bytes.toString(Charsets.UTF_8))
        }

        /**
         * Validate the UMCF document structure.
         */
        private fun validateDocument(document: UMCFDocument) {
            // Check required fields
            requireNotNull(document.umcf) { "Missing 'umcf' version field" }
            requireNotNull(document.id) { "Missing 'id' field" }
            requireNotNull(document.title) { "Missing 'title' field" }
            require(document.content.isNotEmpty()) { "Content array cannot be empty" }

            // Log version compatibility
            if (document.umcf != SUPPORTED_UMCF_VERSION) {
                Log.w(TAG, "UMCF version ${document.umcf} may not be fully supported. Expected $SUPPORTED_UMCF_VERSION")
            }
        }

        /**
         * Convert UMCF document to internal Curriculum model.
         */
        private fun convertToCurriculum(document: UMCFDocument): Curriculum {
            val topics = mutableListOf<Topic>()
            var orderIndex = 0

            // Recursively extract topics from content tree
            for (contentNode in document.content) {
                extractTopics(contentNode, topics, orderIndex)
                orderIndex = topics.size
            }

            // Extract glossary terms
            val glossaryTerms =
                document.glossary?.terms?.map { (term, definition) ->
                    GlossaryTerm(
                        term = term,
                        definition = definition.definition,
                        examples = definition.examples ?: emptyList(),
                    )
                } ?: emptyList()

            return Curriculum(
                id = document.id?.id ?: throw UMCFParseException("Document ID is required"),
                title = document.title,
                description = document.description ?: "",
                version = document.version?.version ?: "1.0.0",
                topics = topics,
                glossaryTerms = glossaryTerms,
                learningObjectives = extractTopLevelObjectives(document.content),
                difficulty = document.educational?.difficulty,
                ageRange = document.educational?.ageRange,
                duration = document.educational?.duration,
                keywords = document.metadata?.keywords ?: emptyList(),
            )
        }

        /**
         * Recursively extract topics from content tree.
         */
        private fun extractTopics(
            node: UMCFContentNode,
            topics: MutableList<Topic>,
            startIndex: Int,
        ) {
            // If this node has a transcript, it's a topic
            if (node.transcript != null) {
                val topic = convertToTopic(node, startIndex + topics.size)
                topics.add(topic)
            }

            // Recursively process children
            node.children?.forEach { child ->
                extractTopics(child, topics, startIndex)
            }
        }

        /**
         * Convert a content node with transcript to a Topic.
         */
        private fun convertToTopic(
            node: UMCFContentNode,
            orderIndex: Int,
        ): Topic {
            val transcript =
                node.transcript ?: return Topic(
                    id = node.id,
                    title = node.title,
                    orderIndex = orderIndex,
                    transcript = emptyList(),
                )

            // Convert segments
            val segments =
                transcript.segments.mapIndexed { index, segment ->
                    TranscriptSegment(
                        id = segment.id ?: "segment-$index",
                        type = segment.type ?: "content",
                        content = segment.content,
                        spokenText = segment.spokenText,
                        stoppingPoint =
                            segment.checkpoint?.let { checkpoint ->
                                StoppingPoint(
                                    type = checkpoint.type ?: "comprehension",
                                    prompt = checkpoint.question,
                                    expectedConcepts = checkpoint.expectedConcepts ?: emptyList(),
                                    hints = checkpoint.hints ?: emptyList(),
                                )
                            },
                        visualAssetId = segment.visualId,
                    )
                }

            // Convert visual assets from media collection
            val visualAssets = mutableListOf<VisualAsset>()

            // Embedded assets
            node.media?.embedded?.forEach { asset ->
                visualAssets.add(
                    VisualAsset(
                        id = asset.id,
                        filename = asset.title ?: asset.id,
                        mimeType = asset.mimeType ?: "image/png",
                        url = asset.url,
                        caption = asset.caption,
                    ),
                )
            }

            // Reference assets
            node.media?.reference?.forEach { asset ->
                visualAssets.add(
                    VisualAsset(
                        id = asset.id,
                        filename = asset.title ?: asset.id,
                        mimeType = "application/octet-stream",
                        url = asset.url,
                        caption = asset.description,
                    ),
                )
            }

            // Convert documents
            val documents =
                node.media?.reference?.filter { it.type in listOf("pdf", "text", "markdown") }?.map { ref ->
                    Document(
                        id = ref.id,
                        title = ref.title ?: ref.id,
                        url = ref.url ?: "",
                        type = ref.type,
                    )
                } ?: emptyList()

            return Topic(
                id = node.id,
                title = node.title,
                orderIndex = orderIndex,
                transcript = segments,
                learningObjectives = node.learningObjectives?.map { it.description } ?: emptyList(),
                documents = documents,
                visualAssets = visualAssets,
                description = node.description,
                duration = node.educational?.duration,
            )
        }

        /**
         * Extract top-level learning objectives from root content.
         */
        private fun extractTopLevelObjectives(content: List<UMCFContentNode>): List<String> {
            return content.flatMap { node ->
                node.learningObjectives?.map { it.description } ?: emptyList()
            }
        }
    }

/**
 * Exception thrown when UMCF parsing fails.
 */
class UMCFParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

// ============================================================================
// UMCF Data Transfer Objects (DTOs)
// Matching UMCF v1.0.0 JSON Schema
// ============================================================================

/**
 * Root UMCF document structure.
 */
@Serializable
data class UMCFDocument(
    val umcf: String? = null,
    val id: UMCFIdentifier? = null,
    val title: String,
    val description: String? = null,
    val version: UMCFVersionInfo? = null,
    val lifecycle: UMCFLifecycle? = null,
    val metadata: UMCFMetadata? = null,
    val educational: UMCFEducationalContext? = null,
    val rights: UMCFRights? = null,
    val content: List<UMCFContentNode> = emptyList(),
    val glossary: UMCFGlossary? = null,
    val extensions: Map<String, String>? = null,
)

/**
 * UMCF identifier with type and namespace.
 */
@Serializable
data class UMCFIdentifier(
    val id: String,
    val type: String? = null,
    val namespace: String? = null,
)

/**
 * Version information.
 */
@Serializable
data class UMCFVersionInfo(
    val version: String,
    val status: String? = null,
    @SerialName("release_date")
    val releaseDate: String? = null,
)

/**
 * Document lifecycle information.
 */
@Serializable
data class UMCFLifecycle(
    val status: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val authors: List<UMCFContributor>? = null,
    val contributors: List<UMCFContributor>? = null,
)

/**
 * Contributor information.
 */
@Serializable
data class UMCFContributor(
    val name: String,
    val role: String? = null,
    val email: String? = null,
    val organization: String? = null,
)

/**
 * Metadata for search and discovery.
 */
@Serializable
data class UMCFMetadata(
    val language: String? = null,
    val keywords: List<String>? = null,
    val subjects: List<String>? = null,
    val coverage: String? = null,
)

/**
 * Educational context and requirements.
 */
@Serializable
data class UMCFEducationalContext(
    val difficulty: String? = null,
    @SerialName("age_range")
    val ageRange: String? = null,
    val duration: String? = null,
    @SerialName("learning_resource_type")
    val learningResourceType: String? = null,
    @SerialName("intended_end_user_role")
    val intendedEndUserRole: String? = null,
    val prerequisites: List<String>? = null,
)

/**
 * Rights and licensing information.
 */
@Serializable
data class UMCFRights(
    val license: String? = null,
    @SerialName("license_url")
    val licenseUrl: String? = null,
    val copyright: String? = null,
    val attribution: String? = null,
)

/**
 * Hierarchical content node (topic, unit, module, etc.).
 */
@Serializable
data class UMCFContentNode(
    val id: String,
    val title: String,
    /** Type: unit, module, topic, or section */
    val type: String? = null,
    val description: String? = null,
    @SerialName("learning_objectives")
    val learningObjectives: List<UMCFLearningObjective>? = null,
    val educational: UMCFEducationalContext? = null,
    val transcript: UMCFTranscript? = null,
    val media: UMCFMediaCollection? = null,
    val assessment: UMCFAssessment? = null,
    val children: List<UMCFContentNode>? = null,
)

/**
 * Learning objective with optional standard alignment.
 */
@Serializable
data class UMCFLearningObjective(
    val id: String? = null,
    val description: String,
    /** Type: knowledge, skill, or attitude */
    val type: String? = null,
    @SerialName("bloom_level")
    val bloomLevel: String? = null,
    @SerialName("standard_alignments")
    val standardAlignments: List<UMCFStandardAlignment>? = null,
)

/**
 * Standard alignment reference.
 */
@Serializable
data class UMCFStandardAlignment(
    val framework: String,
    val identifier: String,
    val description: String? = null,
)

/**
 * Transcript container with segments and voice settings.
 */
@Serializable
data class UMCFTranscript(
    val segments: List<UMCFTranscriptSegment> = emptyList(),
    @SerialName("voice_profile")
    val voiceProfile: UMCFVoiceProfile? = null,
    @SerialName("pronunciation_guide")
    val pronunciationGuide: Map<String, UMCFPronunciation>? = null,
    @SerialName("alternative_explanations")
    val alternativeExplanations: Map<String, List<String>>? = null,
)

/**
 * Individual transcript segment.
 */
@Serializable
data class UMCFTranscriptSegment(
    val id: String? = null,
    /** Type: introduction, lecture, explanation, example, checkpoint, etc. */
    val type: String? = null,
    val content: String,
    @SerialName("spoken_text")
    val spokenText: String? = null,
    @SerialName("speaking_notes")
    val speakingNotes: UMCFSpeakingNotes? = null,
    val checkpoint: UMCFCheckpoint? = null,
    @SerialName("visual_id")
    val visualId: String? = null,
)

/**
 * Speaking notes for TTS guidance.
 */
@Serializable
data class UMCFSpeakingNotes(
    /** Pace: slow, normal, or fast */
    val pace: String? = null,
    val emphasis: List<String>? = null,
    @SerialName("emotional_tone")
    val emotionalTone: String? = null,
    val pauses: List<UMCFPause>? = null,
)

/**
 * Pause indication in transcript.
 */
@Serializable
data class UMCFPause(
    @SerialName("after_word")
    val afterWord: String? = null,
    /** Duration: short, medium, or long */
    val duration: String? = null,
)

/**
 * Checkpoint for comprehension verification.
 */
@Serializable
data class UMCFCheckpoint(
    val id: String? = null,
    /** Type: comprehension, reflection, or application */
    val type: String? = null,
    val question: String,
    @SerialName("expected_concepts")
    val expectedConcepts: List<String>? = null,
    val hints: List<String>? = null,
    @SerialName("response_branches")
    val responseBranches: UMCFResponseBranches? = null,
)

/**
 * Response-based branching for checkpoints.
 */
@Serializable
data class UMCFResponseBranches(
    val correct: UMCFBranchAction? = null,
    val partial: UMCFBranchAction? = null,
    val incorrect: UMCFBranchAction? = null,
)

/**
 * Action to take based on checkpoint response.
 */
@Serializable
data class UMCFBranchAction(
    val feedback: String? = null,
    /** Action: continue, explain, retry, or skip */
    val action: String? = null,
    @SerialName("goto_segment")
    val gotoSegment: String? = null,
)

/**
 * Voice profile for TTS.
 */
@Serializable
data class UMCFVoiceProfile(
    val tone: String? = null,
    val pace: String? = null,
    val accent: String? = null,
    @SerialName("voice_id")
    val voiceId: String? = null,
)

/**
 * Pronunciation guide entry.
 */
@Serializable
data class UMCFPronunciation(
    val ipa: String? = null,
    val respelling: String? = null,
    val language: String? = null,
)

/**
 * Media collection with embedded and reference assets.
 */
@Serializable
data class UMCFMediaCollection(
    val embedded: List<UMCFEmbeddedAsset>? = null,
    val reference: List<UMCFReferenceAsset>? = null,
)

/**
 * Embedded visual asset synchronized with transcript.
 */
@Serializable
data class UMCFEmbeddedAsset(
    val id: String,
    /** Type: image, diagram, equation, chart, etc. */
    val type: String,
    val url: String? = null,
    @SerialName("local_path")
    val localPath: String? = null,
    val title: String? = null,
    val alt: String? = null,
    val caption: String? = null,
    @SerialName("mime_type")
    val mimeType: String? = null,
    val dimensions: UMCFDimensions? = null,
    @SerialName("segment_timing")
    val segmentTiming: UMCFSegmentTiming? = null,
    @SerialName("audio_description")
    val audioDescription: String? = null,
    /** LaTeX source for equations */
    val latex: String? = null,
)

/**
 * Asset dimensions.
 */
@Serializable
data class UMCFDimensions(
    val width: Int? = null,
    val height: Int? = null,
)

/**
 * Segment timing for embedded assets.
 */
@Serializable
data class UMCFSegmentTiming(
    @SerialName("start_segment")
    val startSegment: Int? = null,
    @SerialName("end_segment")
    val endSegment: Int? = null,
    /** Display mode: persistent, highlight, popup, or inline */
    @SerialName("display_mode")
    val displayMode: String? = null,
)

/**
 * Reference asset (user-requestable).
 */
@Serializable
data class UMCFReferenceAsset(
    val id: String,
    val type: String,
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val keywords: List<String>? = null,
)

/**
 * Assessment or quiz section.
 */
@Serializable
data class UMCFAssessment(
    val id: String? = null,
    val title: String? = null,
    /** Type: formative, summative, or diagnostic */
    val type: String? = null,
    val questions: List<UMCFQuestion>? = null,
)

/**
 * Assessment question.
 */
@Serializable
data class UMCFQuestion(
    val id: String? = null,
    /** Type: multiple-choice, short-answer, or essay */
    val type: String? = null,
    val prompt: String,
    val options: List<String>? = null,
    @SerialName("correct_answer")
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val points: Int? = null,
)

/**
 * Glossary with term definitions.
 */
@Serializable
data class UMCFGlossary(
    val terms: Map<String, UMCFGlossaryDefinition> = emptyMap(),
)

/**
 * Glossary term definition.
 */
@Serializable
data class UMCFGlossaryDefinition(
    val definition: String,
    val examples: List<String>? = null,
    @SerialName("related_terms")
    val relatedTerms: List<String>? = null,
    val pronunciation: UMCFPronunciation? = null,
)
