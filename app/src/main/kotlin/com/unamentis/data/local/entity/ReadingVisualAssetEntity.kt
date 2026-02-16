package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for reading list visual assets (images, diagrams).
 *
 * Visual assets are associated with a reading list item and positioned
 * at specific chunks/pages within the document.
 *
 * @property id Unique identifier
 * @property readingListItemId Parent reading list item ID
 * @property chunkIndex Chunk index where this asset appears
 * @property pageIndex Page index within the original document
 * @property positionOnPage Relative vertical position on the page (0.0 to 1.0)
 * @property mimeType MIME type of the image (e.g., "image/png")
 * @property width Image width in pixels
 * @property height Image height in pixels
 * @property altText Accessibility description of the image
 * @property localPath Local file path for cached image data
 */
@Entity(
    tableName = "reading_visual_assets",
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
data class ReadingVisualAssetEntity(
    @PrimaryKey
    val id: String,
    val readingListItemId: String,
    val chunkIndex: Int = 0,
    val pageIndex: Int = 0,
    val positionOnPage: Float = 0.0f,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val altText: String? = null,
    val localPath: String? = null,
)
