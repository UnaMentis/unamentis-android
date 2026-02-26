package com.unamentis.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.unamentis.data.local.entity.ReadingBookmarkEntity
import com.unamentis.data.local.entity.ReadingChunkEntity
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.local.entity.ReadingVisualAssetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for reading list operations.
 *
 * Manages CRUD operations for reading list items and their associated
 * chunks, bookmarks, and visual assets. Child entities use CASCADE delete
 * via foreign keys, so deleting an item removes all its children.
 */
@Dao
interface ReadingListDao {
    // ── Item operations ──

    /**
     * Get all reading list items ordered by status priority then added date.
     */
    @Query(
        """
        SELECT * FROM reading_list_items
        ORDER BY
            CASE status
                WHEN 'in_progress' THEN 0
                WHEN 'unread' THEN 1
                WHEN 'completed' THEN 2
                WHEN 'archived' THEN 3
                ELSE 4
            END,
            addedAt DESC
        """,
    )
    fun getAllItems(): Flow<List<ReadingListItemEntity>>

    /**
     * Get active (non-archived) reading list items.
     */
    @Query(
        """
        SELECT * FROM reading_list_items
        WHERE status != 'archived'
        ORDER BY
            CASE status
                WHEN 'in_progress' THEN 0
                WHEN 'unread' THEN 1
                WHEN 'completed' THEN 2
                ELSE 3
            END,
            addedAt DESC
        """,
    )
    fun getActiveItems(): Flow<List<ReadingListItemEntity>>

    /**
     * Get reading list items by status.
     */
    @Query("SELECT * FROM reading_list_items WHERE status = :status ORDER BY addedAt DESC")
    fun getItemsByStatus(status: String): Flow<List<ReadingListItemEntity>>

    /**
     * Get a specific reading list item by ID.
     */
    @Query("SELECT * FROM reading_list_items WHERE id = :id")
    suspend fun getItemById(id: String): ReadingListItemEntity?

    /**
     * Get a reading list item by file hash (for deduplication).
     */
    @Query("SELECT * FROM reading_list_items WHERE fileHash = :hash LIMIT 1")
    suspend fun getItemByFileHash(hash: String): ReadingListItemEntity?

    /**
     * Insert or replace a reading list item.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ReadingListItemEntity)

    /**
     * Update an existing reading list item.
     */
    @Update
    suspend fun updateItem(item: ReadingListItemEntity)

    /**
     * Update reading position for an item.
     */
    @Query(
        """
        UPDATE reading_list_items
        SET currentChunkIndex = :chunkIndex,
            percentComplete = :percentComplete,
            lastReadAt = :lastReadAt,
            status = CASE
                WHEN :percentComplete >= 1.0 THEN 'completed'
                WHEN :percentComplete > 0.0 THEN 'in_progress'
                ELSE status
            END,
            completedAt = CASE
                WHEN :percentComplete >= 1.0 THEN :lastReadAt
                ELSE completedAt
            END
        WHERE id = :id
        """,
    )
    suspend fun updateReadingPosition(
        id: String,
        chunkIndex: Int,
        percentComplete: Float,
        lastReadAt: Long,
    )

    /**
     * Update the audio pre-generation status for an item.
     */
    @Query("UPDATE reading_list_items SET audioPreGenStatus = :status WHERE id = :id")
    suspend fun updateAudioPreGenStatus(
        id: String,
        status: String,
    )

    /**
     * Delete a reading list item (cascades to chunks, bookmarks, assets).
     */
    @Query("DELETE FROM reading_list_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    /**
     * Delete all reading list items.
     */
    @Query("DELETE FROM reading_list_items")
    suspend fun deleteAllItems()

    /**
     * Search reading list items by title or author.
     */
    @Query(
        """
        SELECT * FROM reading_list_items
        WHERE title LIKE '%' || :query || '%'
           OR author LIKE '%' || :query || '%'
        ORDER BY addedAt DESC
        """,
    )
    fun searchItems(query: String): Flow<List<ReadingListItemEntity>>

    /**
     * Get total item count.
     */
    @Query("SELECT COUNT(*) FROM reading_list_items")
    suspend fun getItemCount(): Int

    /**
     * Get item count by status.
     */
    @Query("SELECT COUNT(*) FROM reading_list_items WHERE status = :status")
    suspend fun getItemCountByStatus(status: String): Int

    // ── Chunk operations ──

    /**
     * Get all chunks for a reading list item, ordered by index.
     */
    @Query("SELECT * FROM reading_chunks WHERE readingListItemId = :itemId ORDER BY `index` ASC")
    suspend fun getChunksForItem(itemId: String): List<ReadingChunkEntity>

    /**
     * Get all chunks for a reading list item as a Flow.
     */
    @Query("SELECT * FROM reading_chunks WHERE readingListItemId = :itemId ORDER BY `index` ASC")
    fun getChunksForItemFlow(itemId: String): Flow<List<ReadingChunkEntity>>

    /**
     * Get a specific chunk by ID.
     */
    @Query("SELECT * FROM reading_chunks WHERE id = :id")
    suspend fun getChunkById(id: String): ReadingChunkEntity?

    /**
     * Get a chunk by item ID and index.
     */
    @Query("SELECT * FROM reading_chunks WHERE readingListItemId = :itemId AND `index` = :index")
    suspend fun getChunkByIndex(
        itemId: String,
        index: Int,
    ): ReadingChunkEntity?

    /**
     * Get the total number of chunks for an item.
     */
    @Query("SELECT COUNT(*) FROM reading_chunks WHERE readingListItemId = :itemId")
    suspend fun getChunkCountForItem(itemId: String): Int

    /**
     * Insert a single chunk.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: ReadingChunkEntity)

    /**
     * Insert multiple chunks.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ReadingChunkEntity>)

    /**
     * Delete all chunks for an item.
     */
    @Query("DELETE FROM reading_chunks WHERE readingListItemId = :itemId")
    suspend fun deleteChunksForItem(itemId: String)

    // ── Bookmark operations ──

    /**
     * Get all bookmarks for a reading list item, ordered by chunk index.
     */
    @Query("SELECT * FROM reading_bookmarks WHERE readingListItemId = :itemId ORDER BY chunkIndex ASC")
    fun getBookmarksForItem(itemId: String): Flow<List<ReadingBookmarkEntity>>

    /**
     * Get a specific bookmark by ID.
     */
    @Query("SELECT * FROM reading_bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: String): ReadingBookmarkEntity?

    /**
     * Insert a bookmark.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: ReadingBookmarkEntity)

    /**
     * Delete a bookmark by ID.
     */
    @Query("DELETE FROM reading_bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: String)

    /**
     * Delete all bookmarks for an item.
     */
    @Query("DELETE FROM reading_bookmarks WHERE readingListItemId = :itemId")
    suspend fun deleteBookmarksForItem(itemId: String)

    // ── Visual asset operations ──

    /**
     * Get all visual assets for a reading list item.
     */
    @Query(
        """
        SELECT * FROM reading_visual_assets
        WHERE readingListItemId = :itemId
        ORDER BY chunkIndex ASC, positionOnPage ASC
        """,
    )
    suspend fun getVisualAssetsForItem(itemId: String): List<ReadingVisualAssetEntity>

    /**
     * Get visual assets for a specific chunk.
     */
    @Query(
        """
        SELECT * FROM reading_visual_assets
        WHERE readingListItemId = :itemId AND chunkIndex = :chunkIndex
        ORDER BY positionOnPage ASC
        """,
    )
    suspend fun getVisualAssetsForChunk(
        itemId: String,
        chunkIndex: Int,
    ): List<ReadingVisualAssetEntity>

    /**
     * Insert a visual asset.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisualAsset(asset: ReadingVisualAssetEntity)

    /**
     * Insert multiple visual assets.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisualAssets(assets: List<ReadingVisualAssetEntity>)

    /**
     * Delete all visual assets for an item.
     */
    @Query("DELETE FROM reading_visual_assets WHERE readingListItemId = :itemId")
    suspend fun deleteVisualAssetsForItem(itemId: String)

    // ── Transaction operations ──

    /**
     * Delete a reading list item with all associated data.
     *
     * Note: Foreign key CASCADE handles child deletion automatically,
     * but this explicit transaction ensures atomicity.
     */
    @Transaction
    suspend fun deleteItemWithAllData(itemId: String) {
        deleteVisualAssetsForItem(itemId)
        deleteBookmarksForItem(itemId)
        deleteChunksForItem(itemId)
        deleteItemById(itemId)
    }
}
