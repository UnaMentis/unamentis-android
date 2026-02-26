package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for reading list items.
 *
 * Represents a document imported into the reading list for TTS playback
 * and AI-assisted reading. Maps to iOS ReadingListItem Core Data entity.
 *
 * @property id Unique identifier
 * @property title Document title
 * @property author Document author (if available)
 * @property sourceType How the content was imported (see [ReadingListSourceType])
 * @property status Current reading status (see [ReadingListStatus])
 * @property fileUrl Original file or web URL
 * @property fileHash SHA-256 hash of original content for deduplication
 * @property fileSizeBytes Size of original content in bytes
 * @property currentChunkIndex Current reading position (chunk index)
 * @property percentComplete Reading progress (0.0 to 1.0)
 * @property addedAt Timestamp when item was added
 * @property lastReadAt Timestamp of last reading session
 * @property completedAt Timestamp when reading was completed
 * @property audioPreGenStatus Status of audio pre-generation for TTS
 */
@Entity(tableName = "reading_list_items")
data class ReadingListItemEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String? = null,
    val sourceType: String = "text",
    val status: String = "unread",
    val fileUrl: String? = null,
    val fileHash: String? = null,
    val fileSizeBytes: Long = 0,
    val currentChunkIndex: Int = 0,
    val percentComplete: Float = 0.0f,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val completedAt: Long? = null,
    val audioPreGenStatus: String = "none",
)
