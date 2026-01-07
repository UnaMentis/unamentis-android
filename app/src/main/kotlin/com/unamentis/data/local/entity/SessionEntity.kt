package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing session history.
 *
 * @property id Session identifier (primary key)
 * @property topicId Optional topic being studied
 * @property curriculumId Optional curriculum this session belongs to
 * @property startTime Unix timestamp of session start
 * @property endTime Unix timestamp of session end (null if ongoing)
 * @property durationSeconds Total session duration
 * @property turnCount Number of conversation turns
 * @property interruptionCount Number of successful barge-ins
 * @property totalCost Total API cost in USD
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val topicId: String? = null,
    val curriculumId: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val turnCount: Int = 0,
    val interruptionCount: Int = 0,
    val totalCost: Double = 0.0
)

/**
 * Room entity for storing transcript entries.
 *
 * @property id Entry identifier (primary key)
 * @property sessionId Parent session ID (foreign key)
 * @property role Who spoke ("user" or "assistant")
 * @property text Content of the utterance
 * @property timestamp Unix timestamp of when the utterance occurred
 * @property metadata Optional metadata as JSON
 */
@Entity(tableName = "transcript_entries")
data class TranscriptEntryEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)
