package com.unamentis.data.repository

import android.util.Log
import com.unamentis.data.local.dao.ReadingListDao
import com.unamentis.data.local.entity.ReadingBookmarkEntity
import com.unamentis.data.local.entity.ReadingChunkEntity
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.local.entity.ReadingVisualAssetEntity
import com.unamentis.data.model.AudioPreGenStatus
import com.unamentis.data.model.ReadingListStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading list data management.
 *
 * Responsibilities:
 * - CRUD operations for reading list items, chunks, bookmarks, and visual assets
 * - Reading progress tracking
 * - Content deduplication via file hash
 * - Search and filtering
 *
 * @property readingListDao Room DAO for reading list data
 */
@Singleton
class ReadingListRepository
    @Inject
    constructor(
        private val readingListDao: ReadingListDao,
    ) {
        companion object {
            private const val TAG = "ReadingListRepository"
        }

        // ── Item operations ──

        /**
         * Get all reading list items as a Flow.
         */
        fun getAllItems(): Flow<List<ReadingListItemEntity>> {
            return readingListDao.getAllItems()
        }

        /**
         * Get active (non-archived) reading list items.
         */
        fun getActiveItems(): Flow<List<ReadingListItemEntity>> {
            return readingListDao.getActiveItems()
        }

        /**
         * Get reading list items by status.
         */
        fun getItemsByStatus(status: ReadingListStatus): Flow<List<ReadingListItemEntity>> {
            return readingListDao.getItemsByStatus(status.rawValue)
        }

        /**
         * Get a specific reading list item by ID.
         */
        suspend fun getItemById(id: String): ReadingListItemEntity? {
            return readingListDao.getItemById(id)
        }

        /**
         * Check if content with the given hash already exists.
         */
        suspend fun getItemByFileHash(hash: String): ReadingListItemEntity? {
            return readingListDao.getItemByFileHash(hash)
        }

        /**
         * Add a new reading list item.
         */
        suspend fun addItem(item: ReadingListItemEntity) {
            readingListDao.insertItem(item)
            Log.d(TAG, "Added reading list item: ${item.id} (${item.title})")
        }

        /**
         * Update an existing reading list item.
         */
        suspend fun updateItem(item: ReadingListItemEntity) {
            readingListDao.updateItem(item)
        }

        /**
         * Update reading position for an item.
         */
        suspend fun updateReadingPosition(
            itemId: String,
            chunkIndex: Int,
            percentComplete: Float,
        ) {
            readingListDao.updateReadingPosition(
                id = itemId,
                chunkIndex = chunkIndex,
                percentComplete = percentComplete,
                lastReadAt = System.currentTimeMillis(),
            )
        }

        /**
         * Mark an item as completed.
         */
        suspend fun markItemCompleted(itemId: String) {
            updateReadingPosition(itemId, chunkIndex = Int.MAX_VALUE, percentComplete = 1.0f)
        }

        /**
         * Archive an item.
         */
        suspend fun archiveItem(itemId: String) {
            val item = readingListDao.getItemById(itemId) ?: return
            readingListDao.updateItem(item.copy(status = ReadingListStatus.ARCHIVED.rawValue))
        }

        /**
         * Reset reading progress for an item.
         */
        suspend fun resetProgress(itemId: String) {
            readingListDao.updateReadingPosition(
                id = itemId,
                chunkIndex = 0,
                percentComplete = 0.0f,
                lastReadAt = System.currentTimeMillis(),
            )
            val item = readingListDao.getItemById(itemId) ?: return
            readingListDao.updateItem(
                item.copy(
                    status = ReadingListStatus.UNREAD.rawValue,
                    completedAt = null,
                ),
            )
        }

        /**
         * Update audio pre-generation status for an item.
         */
        suspend fun updateAudioPreGenStatus(
            itemId: String,
            status: AudioPreGenStatus,
        ) {
            readingListDao.updateAudioPreGenStatus(itemId, status.rawValue)
        }

        /**
         * Delete a reading list item and all associated data.
         */
        suspend fun deleteItem(itemId: String) {
            readingListDao.deleteItemWithAllData(itemId)
            Log.d(TAG, "Deleted reading list item: $itemId")
        }

        /**
         * Delete all reading list items.
         */
        suspend fun deleteAllItems() {
            readingListDao.deleteAllItems()
            Log.d(TAG, "Deleted all reading list items")
        }

        /**
         * Search reading list items by title or author.
         */
        fun searchItems(query: String): Flow<List<ReadingListItemEntity>> {
            return readingListDao.searchItems(query)
        }

        /**
         * Get total item count.
         */
        suspend fun getItemCount(): Int {
            return readingListDao.getItemCount()
        }

        /**
         * Get item count by status.
         */
        suspend fun getItemCountByStatus(status: ReadingListStatus): Int {
            return readingListDao.getItemCountByStatus(status.rawValue)
        }

        // ── Chunk operations ──

        /**
         * Get all chunks for a reading list item.
         */
        suspend fun getChunksForItem(itemId: String): List<ReadingChunkEntity> {
            return readingListDao.getChunksForItem(itemId)
        }

        /**
         * Get chunks for a reading list item as a Flow.
         */
        fun getChunksForItemFlow(itemId: String): Flow<List<ReadingChunkEntity>> {
            return readingListDao.getChunksForItemFlow(itemId)
        }

        /**
         * Get a specific chunk by item ID and index.
         */
        suspend fun getChunkByIndex(
            itemId: String,
            index: Int,
        ): ReadingChunkEntity? {
            return readingListDao.getChunkByIndex(itemId, index)
        }

        /**
         * Get chunk count for an item.
         */
        suspend fun getChunkCountForItem(itemId: String): Int {
            return readingListDao.getChunkCountForItem(itemId)
        }

        /**
         * Save chunks for a reading list item (replaces existing chunks).
         */
        suspend fun saveChunks(
            itemId: String,
            chunks: List<ReadingChunkEntity>,
        ) {
            readingListDao.deleteChunksForItem(itemId)
            readingListDao.insertChunks(chunks)
            Log.d(TAG, "Saved ${chunks.size} chunks for item: $itemId")
        }

        // ── Bookmark operations ──

        /**
         * Get all bookmarks for a reading list item.
         */
        fun getBookmarksForItem(itemId: String): Flow<List<ReadingBookmarkEntity>> {
            return readingListDao.getBookmarksForItem(itemId)
        }

        /**
         * Add a bookmark.
         */
        suspend fun addBookmark(bookmark: ReadingBookmarkEntity) {
            readingListDao.insertBookmark(bookmark)
            Log.d(TAG, "Added bookmark ${bookmark.id} at chunk ${bookmark.chunkIndex}")
        }

        /**
         * Delete a bookmark.
         */
        suspend fun deleteBookmark(bookmarkId: String) {
            readingListDao.deleteBookmarkById(bookmarkId)
        }

        // ── Visual asset operations ──

        /**
         * Get all visual assets for a reading list item.
         */
        suspend fun getVisualAssetsForItem(itemId: String): List<ReadingVisualAssetEntity> {
            return readingListDao.getVisualAssetsForItem(itemId)
        }

        /**
         * Get visual assets for a specific chunk.
         */
        suspend fun getVisualAssetsForChunk(
            itemId: String,
            chunkIndex: Int,
        ): List<ReadingVisualAssetEntity> {
            return readingListDao.getVisualAssetsForChunk(itemId, chunkIndex)
        }

        /**
         * Save visual assets for a reading list item (replaces existing).
         */
        suspend fun saveVisualAssets(
            itemId: String,
            assets: List<ReadingVisualAssetEntity>,
        ) {
            readingListDao.deleteVisualAssetsForItem(itemId)
            readingListDao.insertVisualAssets(assets)
            Log.d(TAG, "Saved ${assets.size} visual assets for item: $itemId")
        }
    }
