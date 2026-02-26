package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for reading list bookmarks.
 *
 * A bookmark marks a position within a reading list item that
 * the user wants to return to, optionally with a note.
 *
 * @property id Unique identifier
 * @property readingListItemId Parent reading list item ID
 * @property chunkIndex Chunk index where the bookmark is placed
 * @property note Optional user note for this bookmark
 * @property snippetPreview Short text preview around the bookmark position
 * @property createdAt Timestamp when the bookmark was created
 */
@Entity(
    tableName = "reading_bookmarks",
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
data class ReadingBookmarkEntity(
    @PrimaryKey
    val id: String,
    val readingListItemId: String,
    val chunkIndex: Int,
    val note: String? = null,
    val snippetPreview: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
