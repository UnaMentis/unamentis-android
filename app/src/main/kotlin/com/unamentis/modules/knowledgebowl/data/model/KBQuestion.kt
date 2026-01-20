package com.unamentis.modules.knowledgebowl.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Knowledge Bowl question with answer, metadata, and suitability flags.
 *
 * @property id Unique question identifier
 * @property text The question text
 * @property answer Answer with primary and acceptable alternatives
 * @property domain Academic domain (Science, Math, etc.)
 * @property subdomain Optional subdomain for finer categorization
 * @property difficulty Difficulty level (Overview to Research)
 * @property gradeLevel Target grade level
 * @property suitability Which round types this question is suitable for
 * @property estimatedReadTimeSeconds Estimated time to read the question aloud
 * @property audioAssetId Reference to pre-recorded audio file (optional)
 * @property mcqOptions MCQ options for written round (A, B, C, D)
 * @property source Source attribution for CC-licensed content
 * @property sourceAttribution Full attribution text
 * @property tags Tags for filtering and categorization
 */
@Serializable
data class KBQuestion(
    val id: String,
    val text: String,
    val answer: KBAnswer,
    val domain: KBDomain,
    val subdomain: String? = null,
    val difficulty: KBDifficulty = KBDifficulty.VARSITY,
    @SerialName("grade_level")
    val gradeLevel: KBGradeLevel = KBGradeLevel.HIGH_SCHOOL,
    val suitability: KBSuitability = KBSuitability(),
    @SerialName("estimated_read_time_seconds")
    val estimatedReadTimeSeconds: Float? = null,
    @SerialName("audio_asset_id")
    val audioAssetId: String? = null,
    @SerialName("mcq_options")
    val mcqOptions: List<String>? = null,
    val source: String? = null,
    @SerialName("source_attribution")
    val sourceAttribution: String? = null,
    val tags: List<String>? = null,
) {
    /**
     * Get the estimated read time, computing from word count if not provided.
     */
    val estimatedReadTime: Float
        get() = estimatedReadTimeSeconds ?: computeReadTime()

    /**
     * Compute read time based on word count.
     * Average speech rate: ~150 words per minute = 2.5 words per second.
     */
    private fun computeReadTime(): Float {
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount / 2.5f
    }

    /**
     * Check if this question is suitable for written rounds.
     */
    val isSuitableForWritten: Boolean
        get() = suitability.forWritten

    /**
     * Check if this question is suitable for oral rounds.
     */
    val isSuitableForOral: Boolean
        get() = suitability.forOral

    /**
     * Check if this question has MCQ options.
     */
    val hasMcqOptions: Boolean
        get() = !mcqOptions.isNullOrEmpty() && mcqOptions.size >= 2
}

/**
 * Container for bundled questions (loaded from JSON).
 *
 * @property version Bundle version string
 * @property generatedAt Timestamp when the bundle was generated
 * @property questions List of questions in the bundle
 */
@Serializable
data class KBQuestionBundle(
    val version: String = "1.0.0",
    @SerialName("generated_at")
    val generatedAt: Long? = null,
    val questions: List<KBQuestion>,
)
