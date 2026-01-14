package com.unamentis.data.model

import kotlinx.serialization.Serializable

/**
 * Represents the state of a voice session.
 *
 * The SessionManager uses these states to control the conversation flow
 * and UI feedback.
 */
enum class SessionState {
    /** Session is inactive */
    IDLE,

    /** User is speaking (VAD detected speech) */
    USER_SPEAKING,

    /** Processing user utterance with STT */
    PROCESSING_UTTERANCE,

    /** LLM is generating response */
    AI_THINKING,

    /** AI response is being spoken via TTS */
    AI_SPEAKING,

    /** User interrupted AI response (barge-in) */
    INTERRUPTED,

    /** Session is paused */
    PAUSED,

    /** Session encountered an error */
    ERROR,
}

/**
 * Represents a single entry in the conversation transcript.
 *
 * @property id Unique identifier for the entry
 * @property sessionId Parent session ID
 * @property role Who spoke ("user" or "assistant")
 * @property text Content of the utterance
 * @property timestamp Unix timestamp (milliseconds) of when the utterance occurred
 * @property metadata Optional metadata (e.g., latency, confidence scores)
 */
@Serializable
data class TranscriptEntry(
    val id: String,
    val sessionId: String,
    val role: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Represents a complete voice session.
 *
 * @property id Unique session identifier
 * @property topicId Optional curriculum topic being studied
 * @property curriculumId Optional curriculum this session belongs to
 * @property startTime Unix timestamp of session start
 * @property endTime Unix timestamp of session end (null if ongoing)
 * @property durationSeconds Total session duration
 * @property turnCount Number of conversation turns
 * @property interruptionCount Number of successful barge-ins
 * @property totalCost Total API cost in USD
 * @property transcript List of conversation entries
 */
@Serializable
data class Session(
    val id: String,
    val topicId: String? = null,
    val curriculumId: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val turnCount: Int = 0,
    val interruptionCount: Int = 0,
    val totalCost: Double = 0.0,
    val transcript: List<TranscriptEntry> = emptyList(),
)

/**
 * Represents user progress through a specific topic.
 *
 * @property topicId Topic identifier
 * @property curriculumId Curriculum identifier
 * @property timeSpentSeconds Total time spent on this topic
 * @property masteryLevel Estimated mastery (0.0 - 1.0)
 * @property lastAccessedAt Last time topic was studied (Unix timestamp)
 * @property completedSegments IDs of completed transcript segments
 * @property currentSegmentId ID of current segment (null if completed)
 */
@Serializable
data class TopicProgress(
    val topicId: String,
    val curriculumId: String,
    val timeSpentSeconds: Long = 0,
    val masteryLevel: Float = 0.0f,
    val lastAccessedAt: Long,
    val completedSegments: List<String> = emptyList(),
    val currentSegmentId: String? = null,
)
