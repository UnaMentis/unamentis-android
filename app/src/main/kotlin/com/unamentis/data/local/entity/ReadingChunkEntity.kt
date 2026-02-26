package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for reading list chunks.
 *
 * A chunk is a segment of a reading list item's text, sized for
 * TTS playback. Chunks are ordered by [index] and linked to their
 * parent [ReadingListItemEntity].
 *
 * @property id Unique identifier
 * @property readingListItemId Parent reading list item ID
 * @property index Zero-based position in the reading order
 * @property text Chunk text content
 * @property characterOffset Character offset from the start of the full document
 * @property estimatedDurationSeconds Estimated TTS playback duration
 * @property cachedAudioSampleRate Sample rate of cached audio (0 if not cached)
 */
@Entity(
    tableName = "reading_chunks",
    foreignKeys = [
        ForeignKey(
            entity = ReadingListItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["readingListItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("readingListItemId")],
)
data class ReadingChunkEntity(
    @PrimaryKey
    val id: String,
    val readingListItemId: String,
    val index: Int,
    val text: String,
    val characterOffset: Long = 0,
    val estimatedDurationSeconds: Float = 0.0f,
    val cachedAudioSampleRate: Double = 0.0,
)
