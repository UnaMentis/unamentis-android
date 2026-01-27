package com.unamentis.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * UMCF (Una Mentis Curriculum Format) Data Transfer Objects.
 *
 * These models represent the UMCF 2.0 format as returned by the server's
 * `/api/curricula/{id}/full-with-assets` endpoint. The UMCFParser converts
 * these into the internal Curriculum model used throughout the app.
 *
 * ## Root Document
 */
@Serializable
data class UMCFDocument(
    val umcf: String,
    val id: UMCFIdentifier,
    val title: String,
    val description: String? = null,
    val version: UMCFVersionInfo,
    val lifecycle: UMCFLifecycle? = null,
    val metadata: UMCFMetadata? = null,
    val educational: UMCFEducationalContext? = null,
    val content: List<UMCFContentNode>,
    val glossary: UMCFGlossary? = null,
    val extensions: JsonElement? = null,
    val rights: JsonElement? = null,
    val assetData: Map<String, UMCFAssetData>? = null,
)

/**
 * Asset data included in full-with-assets response.
 *
 * @property data Base64 encoded asset data
 * @property mimeType MIME type of the asset
 * @property size Size in bytes
 */
@Serializable
data class UMCFAssetData(
    val data: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
)

// =============================================================================
// IDENTIFIERS AND VERSION
// =============================================================================

/**
 * Custom serializer for UMCFIdentifier that handles both string and object formats.
 *
 * The server may return identifiers as either:
 * - A simple string: "topic-123"
 * - An object: {"catalog": "UUID", "value": "topic-123"}
 */
object UMCFIdentifierSerializer : KSerializer<UMCFIdentifier> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("UMCFIdentifier") {
            element<String?>("catalog", isOptional = true)
            element<String>("value")
        }

    override fun serialize(
        encoder: Encoder,
        value: UMCFIdentifier,
    ) {
        val composite = encoder.beginStructure(descriptor)
        value.catalog?.let { composite.encodeStringElement(descriptor, 0, it) }
        composite.encodeStringElement(descriptor, 1, value.value)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): UMCFIdentifier {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: return UMCFIdentifier(value = decoder.decodeString())

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> UMCFIdentifier(value = element.content)
            is JsonObject -> {
                val catalog = element["catalog"]?.jsonPrimitive?.content
                val value =
                    element["value"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("UMCFIdentifier object must have 'value' field")
                UMCFIdentifier(catalog = catalog, value = value)
            }
            else -> throw IllegalArgumentException("UMCFIdentifier must be string or object")
        }
    }
}

/**
 * Flexible identifier that can be either a simple string or an object with catalog/value.
 */
@Serializable(with = UMCFIdentifierSerializer::class)
data class UMCFIdentifier(
    val catalog: String? = null,
    val value: String,
)

/**
 * Version information for a UMCF document.
 */
@Serializable
data class UMCFVersionInfo(
    val number: String,
    val date: String? = null,
    val changelog: String? = null,
)

// =============================================================================
// LIFECYCLE AND METADATA
// =============================================================================

/**
 * Lifecycle information for a UMCF document.
 */
@Serializable
data class UMCFLifecycle(
    val status: String? = null,
    val contributors: List<UMCFContributor>? = null,
    val created: String? = null,
    val modified: String? = null,
)

/**
 * Contributor to a UMCF document.
 */
@Serializable
data class UMCFContributor(
    val name: String,
    val role: String,
    val organization: String? = null,
)

/**
 * Metadata for a UMCF document.
 */
@Serializable
data class UMCFMetadata(
    val language: String? = null,
    val keywords: List<String>? = null,
    val subject: List<String>? = null,
    val coverage: List<String>? = null,
)

// =============================================================================
// EDUCATIONAL CONTEXT
// =============================================================================

/**
 * Educational context and settings for a UMCF document.
 */
@Serializable
data class UMCFEducationalContext(
    val interactivityType: String? = null,
    val interactivityLevel: String? = null,
    val learningResourceType: List<String>? = null,
    val intendedEndUserRole: List<String>? = null,
    val context: List<String>? = null,
    val typicalAgeRange: String? = null,
    val difficulty: String? = null,
    val typicalLearningTime: String? = null,
    val educationalAlignment: List<UMCFEducationalAlignment>? = null,
    val audienceProfile: UMCFAudienceProfile? = null,
    // Legacy/alternative fields
    val alignment: UMCFAlignment? = null,
    val targetAudience: UMCFTargetAudience? = null,
    val prerequisites: List<UMCFPrerequisite>? = null,
    val estimatedDuration: String? = null,
)

/**
 * Educational alignment information.
 */
@Serializable
data class UMCFEducationalAlignment(
    val alignmentType: String? = null,
    val educationalFramework: String? = null,
    val targetName: String? = null,
    val targetDescription: String? = null,
)

/**
 * Audience profile for a UMCF document.
 */
@Serializable
data class UMCFAudienceProfile(
    val educationLevel: String? = null,
    val gradeLevel: String? = null,
    val prerequisites: List<UMCFPrerequisite>? = null,
)

/**
 * Alignment information (legacy format).
 */
@Serializable
data class UMCFAlignment(
    val standards: List<UMCFStandard>? = null,
    val frameworks: List<String>? = null,
    val gradeLevel: List<String>? = null,
)

/**
 * Standard reference.
 */
@Serializable
data class UMCFStandard(
    val id: UMCFIdentifier,
    val name: String,
    val description: String? = null,
    val url: String? = null,
)

/**
 * Target audience information (legacy format).
 */
@Serializable
data class UMCFTargetAudience(
    val type: String? = null,
    val ageRange: UMCFAgeRange? = null,
    val educationalRole: List<String>? = null,
    val industry: List<String>? = null,
    val skillLevel: String? = null,
)

/**
 * Age range specification.
 */
@Serializable
data class UMCFAgeRange(
    val minimum: Int? = null,
    val maximum: Int? = null,
    val typical: String? = null,
)

/**
 * Prerequisite information.
 */
@Serializable
data class UMCFPrerequisite(
    val id: UMCFIdentifier? = null,
    val type: String? = null,
    val description: String? = null,
    val required: Boolean? = null,
)

// =============================================================================
// CONTENT NODES
// =============================================================================

/**
 * Content node representing a course, topic, or subtopic.
 */
@Serializable
data class UMCFContentNode(
    val id: UMCFIdentifier,
    val title: String,
    val type: String,
    val orderIndex: Int? = null,
    val description: String? = null,
    val learningObjectives: List<UMCFLearningObjective>? = null,
    val timeEstimates: UMCFTimeEstimates? = null,
    val transcript: UMCFTranscript? = null,
    val examples: List<UMCFExample>? = null,
    val assessments: List<UMCFAssessment>? = null,
    val glossaryTerms: List<UMCFGlossaryTerm>? = null,
    val misconceptions: List<UMCFMisconception>? = null,
    val media: UMCFMediaCollection? = null,
    val children: List<UMCFContentNode>? = null,
    val tutoringConfig: UMCFTutoringConfig? = null,
)

/**
 * Learning objective.
 */
@Serializable
data class UMCFLearningObjective(
    val id: UMCFIdentifier,
    val statement: String,
    val abbreviatedStatement: String? = null,
    val bloomsLevel: String? = null,
)

/**
 * Time estimates for different audience levels.
 */
@Serializable
data class UMCFTimeEstimates(
    val overview: String? = null,
    val introductory: String? = null,
    val intermediate: String? = null,
    val advanced: String? = null,
    val graduate: String? = null,
    val research: String? = null,
)

/**
 * Tutoring configuration for a content node.
 */
@Serializable
data class UMCFTutoringConfig(
    val contentDepth: String? = null,
    val interactionMode: String? = null,
    val checkpointFrequency: String? = null,
    val adaptationRules: Map<String, String>? = null,
)

// =============================================================================
// TRANSCRIPT
// =============================================================================

/**
 * Transcript containing segments and metadata.
 */
@Serializable
data class UMCFTranscript(
    val segments: List<UMCFTranscriptSegment>? = null,
    val totalDuration: String? = null,
    val pronunciationGuide: Map<String, UMCFPronunciationEntry>? = null,
    val voiceProfile: UMCFVoiceProfile? = null,
)

/**
 * A single segment of transcript content.
 */
@Serializable
data class UMCFTranscriptSegment(
    val id: String,
    val type: String,
    val content: String,
    val speakingNotes: UMCFSpeakingNotes? = null,
    val checkpoint: UMCFCheckpoint? = null,
    val stoppingPoint: UMCFStoppingPoint? = null,
    val glossaryRefs: List<String>? = null,
    val alternativeExplanations: List<UMCFAlternativeExplanation>? = null,
)

/**
 * Pronunciation guide entry.
 */
@Serializable
data class UMCFPronunciationEntry(
    val ipa: String,
    val respelling: String? = null,
    val language: String? = null,
    val notes: String? = null,
)

/**
 * Voice profile settings.
 */
@Serializable
data class UMCFVoiceProfile(
    val tone: String? = null,
    val pace: String? = null,
    val accent: String? = null,
)

/**
 * Speaking notes for TTS.
 */
@Serializable
data class UMCFSpeakingNotes(
    val pace: String? = null,
    val emphasis: List<String>? = null,
    val pronunciation: Map<String, String>? = null,
    val emotionalTone: String? = null,
    val pauseAfter: String? = null,
)

/**
 * Checkpoint for comprehension checks.
 */
@Serializable
data class UMCFCheckpoint(
    val type: String? = null,
    val question: String? = null,
    val prompt: String? = null,
    val expectedResponse: UMCFExpectedResponse? = null,
    val celebrationMessage: String? = null,
    val expectedResponsePatterns: List<String>? = null,
    val fallbackBehavior: String? = null,
)

/**
 * Expected response for a checkpoint.
 */
@Serializable
data class UMCFExpectedResponse(
    val type: String? = null,
    val acceptablePatterns: List<String>? = null,
    val keywords: List<String>? = null,
)

/**
 * Stopping point in transcript flow.
 */
@Serializable
data class UMCFStoppingPoint(
    val type: String? = null,
    val promptForContinue: Boolean? = null,
    val suggestedPrompt: String? = null,
)

/**
 * Alternative explanation for a concept.
 */
@Serializable
data class UMCFAlternativeExplanation(
    val style: String? = null,
    val content: String? = null,
)

// =============================================================================
// MEDIA
// =============================================================================

/**
 * Collection of media assets for a content node.
 */
@Serializable
data class UMCFMediaCollection(
    val embedded: List<UMCFMediaAsset>? = null,
    val reference: List<UMCFMediaAsset>? = null,
    @SerialName("total_count")
    val totalCount: Int? = null,
)

/**
 * Individual media asset.
 */
@Serializable
data class UMCFMediaAsset(
    val id: String,
    val type: String,
    val url: String? = null,
    val localPath: String? = null,
    val title: String? = null,
    val alt: String? = null,
    val caption: String? = null,
    val mimeType: String? = null,
    val dimensions: UMCFDimensions? = null,
    val segmentTiming: UMCFSegmentTiming? = null,
    val latex: String? = null,
    val audioDescription: String? = null,
    val description: String? = null,
    val keywords: List<String>? = null,
)

/**
 * Dimensions for image assets.
 */
@Serializable
data class UMCFDimensions(
    val width: Int,
    val height: Int,
)

/**
 * Timing configuration for synchronized display.
 */
@Serializable
data class UMCFSegmentTiming(
    val startSegment: Int,
    val endSegment: Int,
    val displayMode: String? = null,
)

// =============================================================================
// EXAMPLES AND ASSESSMENTS
// =============================================================================

/**
 * Example demonstrating a concept.
 */
@Serializable
data class UMCFExample(
    val id: UMCFIdentifier,
    val type: String? = null,
    val title: String? = null,
    val content: String? = null,
    val explanation: String? = null,
    val codeLanguage: String? = null,
    val spokenContent: String? = null,
    val codeExplanation: List<UMCFCodeExplanation>? = null,
    val complexity: String? = null,
)

/**
 * Code explanation for a line range.
 */
@Serializable
data class UMCFCodeExplanation(
    val lineRange: String? = null,
    val explanation: String? = null,
)

/**
 * Assessment/quiz item.
 */
@Serializable
data class UMCFAssessment(
    val id: UMCFIdentifier,
    val type: String? = null,
    val question: String? = null,
    val prompt: String? = null,
    val options: List<UMCFAssessmentOption>? = null,
    val choices: List<UMCFAssessmentChoice>? = null,
    val correctAnswer: String? = null,
    val hint: String? = null,
    val feedback: UMCFFeedback? = null,
    val difficulty: Double? = null,
    val objectivesAssessed: List<String>? = null,
)

/**
 * Assessment option.
 */
@Serializable
data class UMCFAssessmentOption(
    val id: String,
    val text: String,
    val isCorrect: Boolean? = null,
)

/**
 * Assessment choice (alternative format).
 */
@Serializable
data class UMCFAssessmentChoice(
    val id: String,
    val text: String,
    val correct: Boolean? = null,
    val feedback: String? = null,
)

/**
 * Feedback for assessment responses.
 */
@Serializable
data class UMCFFeedback(
    val correct: String? = null,
    val incorrect: String? = null,
    val partial: String? = null,
)

/**
 * Misconception and its correction.
 */
@Serializable
data class UMCFMisconception(
    val id: UMCFIdentifier? = null,
    val trigger: List<String>? = null,
    val triggerPhrases: List<String>? = null,
    val misconception: String? = null,
    val correction: String? = null,
    val explanation: String? = null,
    val severity: String? = null,
    val remediationPath: UMCFRemediationPath? = null,
)

/**
 * Remediation path for a misconception.
 */
@Serializable
data class UMCFRemediationPath(
    val reviewTopics: List<String>? = null,
)

// =============================================================================
// GLOSSARY
// =============================================================================

/**
 * Glossary containing term definitions.
 */
@Serializable
data class UMCFGlossary(
    val terms: List<UMCFGlossaryTerm>? = null,
)

/**
 * Glossary term definition.
 */
@Serializable
data class UMCFGlossaryTerm(
    val term: String,
    val definition: String? = null,
    val pronunciation: String? = null,
    val spokenDefinition: String? = null,
    val examples: List<String>? = null,
    val relatedTerms: List<String>? = null,
    val simpleDefinition: String? = null,
)
