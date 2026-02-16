package com.unamentis.core.readinglist

import android.content.Context
import android.util.Log
import com.unamentis.data.local.entity.ReadingBookmarkEntity
import com.unamentis.data.local.entity.ReadingChunkEntity
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.local.entity.ReadingVisualAssetEntity
import com.unamentis.data.model.AudioPreGenStatus
import com.unamentis.data.model.ReadingListSourceType
import com.unamentis.data.model.ReadingListStatus
import com.unamentis.data.repository.ReadingListRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages reading list business logic.
 *
 * Responsibilities:
 * - Import documents with text extraction and chunking
 * - CRUD operations for reading items
 * - Position tracking and auto-save
 * - Bookmark management
 * - Statistics computation
 *
 * This class coordinates between the repository layer and any text processing
 * or web fetching services. Import-time chunking ensures low-latency TTS
 * playback by pre-segmenting text during import, not at playback time.
 *
 * @property repository Reading list data repository
 * @property context Application context for file operations
 */
@Singleton
class ReadingListManager
    @Inject
    constructor(
        private val repository: ReadingListRepository,
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "ReadingListManager"

            /** Directory name within app files for reading list documents. */
            private const val READING_LIST_DIR = "ReadingList"

            /** Subdirectory for extracted images. */
            private const val IMAGES_DIR = "Images"

            /** Maximum snippet preview length for bookmarks. */
            internal const val SNIPPET_PREVIEW_LENGTH = 80
        }

        /** Directory for storing imported documents. */
        private val documentsDirectory: File by lazy {
            File(context.filesDir, READING_LIST_DIR).also { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        }

        /** Directory for storing extracted images. */
        private val imagesDirectory: File by lazy {
            File(documentsDirectory, IMAGES_DIR).also { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        }

        // ── Import Operations ──

        /**
         * Import a document file into the reading list.
         *
         * Copies the file to app storage, computes a hash for deduplication,
         * and creates a reading list item. The caller is responsible for
         * triggering text extraction and chunking separately via [processImportedItem].
         *
         * @param sourceFile Source file to import
         * @param title Optional custom title (defaults to filename without extension)
         * @param author Optional author name
         * @return ID of the created reading list item
         * @throws ReadingListError.UnsupportedFileType if the file extension is not supported
         * @throws ReadingListError.DuplicateDocument if content with same hash already exists
         */
        suspend fun importDocument(
            sourceFile: File,
            title: String? = null,
            author: String? = null,
        ): String {
            val extension = sourceFile.extension.lowercase()
            val sourceType = inferSourceType(extension)

            if (sourceType == null) {
                Log.w(TAG, "Unsupported file type: .$extension")
                throw ReadingListError.UnsupportedFileType(extension)
            }

            Log.i(TAG, "Importing document: ${sourceFile.name} [${sourceType.rawValue}]")

            // Copy file to app storage
            val destinationFile = copyFileToStorage(sourceFile)

            // Compute hash for deduplication
            val fileHash = computeFileHash(destinationFile)

            // Check for duplicates
            val existingItem = repository.getItemByFileHash(fileHash)
            if (existingItem != null) {
                Log.w(TAG, "Duplicate document detected: ${existingItem.title}")
                destinationFile.delete()
                throw ReadingListError.DuplicateDocument(existingItem.title)
            }

            // Create the reading list item
            val itemId = UUID.randomUUID().toString()
            val itemTitle = title ?: sourceFile.nameWithoutExtension
            val item =
                ReadingListItemEntity(
                    id = itemId,
                    title = itemTitle,
                    author = author,
                    sourceType = sourceType.rawValue,
                    status = ReadingListStatus.UNREAD.rawValue,
                    fileUrl = destinationFile.absolutePath,
                    fileHash = fileHash,
                    fileSizeBytes = destinationFile.length(),
                    addedAt = System.currentTimeMillis(),
                )

            repository.addItem(item)
            Log.i(TAG, "Imported document: $itemTitle (id=$itemId)")

            return itemId
        }

        /**
         * Import a web article from a URL into the reading list.
         *
         * Creates a reading list item for the URL. The caller is responsible
         * for fetching and extracting the article content, then calling
         * [saveChunksForItem] with the extracted text chunks.
         *
         * @param url Web page URL
         * @param title Title of the article
         * @param author Optional author name
         * @param extractedText Optional pre-extracted text content (for dedup hash)
         * @return ID of the created reading list item
         * @throws ReadingListError.DuplicateDocument if content with same hash already exists
         * @throws ReadingListError.NoTextContent if extractedText is provided but empty
         */
        suspend fun importWebArticle(
            url: String,
            title: String,
            author: String? = null,
            extractedText: String? = null,
        ): String {
            Log.i(TAG, "Importing web article: $url")

            if (extractedText != null && extractedText.isBlank()) {
                throw ReadingListError.NoTextContent
            }

            // Compute hash for dedup if text is available
            val fileHash =
                extractedText?.let {
                    computeStringHash(it)
                }

            // Check for duplicates
            if (fileHash != null) {
                val existingItem = repository.getItemByFileHash(fileHash)
                if (existingItem != null) {
                    Log.w(TAG, "Duplicate article detected: ${existingItem.title}")
                    throw ReadingListError.DuplicateDocument(existingItem.title)
                }
            }

            // Save extracted text to a file if provided
            val fileUrl: String?
            val fileSizeBytes: Long
            if (extractedText != null) {
                val textFile = File(documentsDirectory, "${UUID.randomUUID()}_article.txt")
                textFile.writeText(extractedText)
                fileUrl = textFile.absolutePath
                fileSizeBytes = textFile.length()
            } else {
                fileUrl = url
                fileSizeBytes = 0L
            }

            val itemId = UUID.randomUUID().toString()
            val item =
                ReadingListItemEntity(
                    id = itemId,
                    title = title,
                    author = author,
                    sourceType = ReadingListSourceType.WEB_ARTICLE.rawValue,
                    status = ReadingListStatus.UNREAD.rawValue,
                    fileUrl = fileUrl,
                    fileHash = fileHash,
                    fileSizeBytes = fileSizeBytes,
                    addedAt = System.currentTimeMillis(),
                )

            repository.addItem(item)
            Log.i(TAG, "Imported web article: $title (id=$itemId)")

            return itemId
        }

        /**
         * Import content from manual text entry (clipboard, typed text, etc.).
         *
         * @param title Title for the reading list item
         * @param text Content text to import
         * @param author Optional author name
         * @return ID of the created reading list item
         * @throws ReadingListError.NoTextContent if text is blank
         * @throws ReadingListError.DuplicateDocument if content with same hash already exists
         */
        suspend fun importManualEntry(
            title: String,
            text: String,
            author: String? = null,
        ): String {
            if (text.isBlank()) {
                throw ReadingListError.NoTextContent
            }

            Log.i(TAG, "Importing manual entry: $title")

            // Compute hash for dedup
            val fileHash = computeStringHash(text)

            // Check for duplicates
            val existingItem = repository.getItemByFileHash(fileHash)
            if (existingItem != null) {
                Log.w(TAG, "Duplicate content detected: ${existingItem.title}")
                throw ReadingListError.DuplicateDocument(existingItem.title)
            }

            // Save text to file
            val textFile = File(documentsDirectory, "${UUID.randomUUID()}_manual.txt")
            textFile.writeText(text)

            val itemId = UUID.randomUUID().toString()
            val item =
                ReadingListItemEntity(
                    id = itemId,
                    title = title,
                    author = author,
                    sourceType = ReadingListSourceType.PLAIN_TEXT.rawValue,
                    status = ReadingListStatus.UNREAD.rawValue,
                    fileUrl = textFile.absolutePath,
                    fileHash = fileHash,
                    fileSizeBytes = textFile.length(),
                    addedAt = System.currentTimeMillis(),
                )

            repository.addItem(item)
            Log.i(TAG, "Imported manual entry: $title (id=$itemId)")

            return itemId
        }

        // ── Chunk Management ──

        /**
         * Save pre-processed text chunks for a reading list item.
         *
         * This is called after text extraction and chunking have been performed
         * (by a separate text processing service). Updates the item status to
         * indicate the content is ready for playback.
         *
         * @param itemId ID of the reading list item
         * @param chunks List of chunk data to save
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun saveChunksForItem(
            itemId: String,
            chunks: List<ChunkData>,
        ) {
            val item =
                repository.getItemById(itemId)
                    ?: throw ReadingListError.ItemNotFound

            if (chunks.isEmpty()) {
                Log.w(TAG, "No chunks to save for item: ${item.title}")
                return
            }

            val chunkEntities =
                chunks.map { chunkData ->
                    ReadingChunkEntity(
                        id = UUID.randomUUID().toString(),
                        readingListItemId = itemId,
                        index = chunkData.index,
                        text = chunkData.text,
                        characterOffset = chunkData.characterOffset,
                        estimatedDurationSeconds = chunkData.estimatedDurationSeconds,
                    )
                }

            repository.saveChunks(itemId, chunkEntities)
            Log.i(TAG, "Saved ${chunks.size} chunks for item: ${item.title}")
        }

        /**
         * Save visual assets extracted from a document.
         *
         * @param itemId ID of the reading list item
         * @param assets List of visual asset data to save
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun saveVisualAssetsForItem(
            itemId: String,
            assets: List<VisualAssetData>,
        ) {
            repository.getItemById(itemId) ?: throw ReadingListError.ItemNotFound

            if (assets.isEmpty()) return

            val assetEntities =
                assets.map { assetData ->
                    ReadingVisualAssetEntity(
                        id = UUID.randomUUID().toString(),
                        readingListItemId = itemId,
                        chunkIndex = assetData.chunkIndex,
                        pageIndex = assetData.pageIndex,
                        positionOnPage = assetData.positionOnPage,
                        mimeType = assetData.mimeType,
                        width = assetData.width,
                        height = assetData.height,
                        altText = assetData.altText,
                        localPath = assetData.localPath,
                    )
                }

            repository.saveVisualAssets(itemId, assetEntities)
            Log.i(TAG, "Saved ${assets.size} visual assets for item: $itemId")
        }

        // ── Read Operations ──

        /**
         * Get all active (non-archived) reading list items as a reactive Flow.
         */
        fun getActiveItems(): Flow<List<ReadingListItemEntity>> {
            return repository.getActiveItems()
        }

        /**
         * Get all reading list items as a reactive Flow.
         */
        fun getAllItems(): Flow<List<ReadingListItemEntity>> {
            return repository.getAllItems()
        }

        /**
         * Get reading list items filtered by status.
         *
         * @param status Status to filter by
         */
        fun getItemsByStatus(status: ReadingListStatus): Flow<List<ReadingListItemEntity>> {
            return repository.getItemsByStatus(status)
        }

        /**
         * Get a specific reading list item by ID.
         *
         * @param itemId Item ID
         * @return The item entity, or null if not found
         */
        suspend fun getItem(itemId: String): ReadingListItemEntity? {
            return repository.getItemById(itemId)
        }

        /**
         * Get all chunks for a reading list item.
         *
         * @param itemId Item ID
         * @return List of chunks ordered by index
         */
        suspend fun getChunksForItem(itemId: String): List<ReadingChunkEntity> {
            return repository.getChunksForItem(itemId)
        }

        /**
         * Get chunks for a reading list item as a reactive Flow.
         *
         * @param itemId Item ID
         */
        fun getChunksForItemFlow(itemId: String): Flow<List<ReadingChunkEntity>> {
            return repository.getChunksForItemFlow(itemId)
        }

        /**
         * Get a specific chunk by item ID and index.
         *
         * @param itemId Item ID
         * @param index Chunk index
         * @return The chunk entity, or null if not found
         */
        suspend fun getChunkByIndex(
            itemId: String,
            index: Int,
        ): ReadingChunkEntity? {
            return repository.getChunkByIndex(itemId, index)
        }

        /**
         * Get the total number of chunks for an item.
         *
         * @param itemId Item ID
         */
        suspend fun getChunkCount(itemId: String): Int {
            return repository.getChunkCountForItem(itemId)
        }

        /**
         * Search reading list items by title or author.
         *
         * @param query Search query
         */
        fun searchItems(query: String): Flow<List<ReadingListItemEntity>> {
            return repository.searchItems(query)
        }

        // ── Position & Progress Tracking ──

        /**
         * Update reading position for an item.
         *
         * Computes percent complete based on the current chunk index relative
         * to total chunks. Automatically transitions item status between
         * unread, in_progress, and completed.
         *
         * @param itemId Item ID
         * @param chunkIndex Current chunk index
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun updatePosition(
            itemId: String,
            chunkIndex: Int,
        ) {
            repository.getItemById(itemId) ?: throw ReadingListError.ItemNotFound

            val totalChunks = repository.getChunkCountForItem(itemId)
            val percentComplete =
                if (totalChunks > 0) {
                    (chunkIndex + 1).toFloat() / totalChunks.toFloat()
                } else {
                    0.0f
                }.coerceIn(0.0f, 1.0f)

            repository.updateReadingPosition(itemId, chunkIndex, percentComplete)
            Log.d(TAG, "Updated position for item $itemId to chunk $chunkIndex (${"%.0f".format(percentComplete * 100)}%)")
        }

        /**
         * Mark an item as completed.
         *
         * @param itemId Item ID
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun markCompleted(itemId: String) {
            repository.getItemById(itemId) ?: throw ReadingListError.ItemNotFound
            repository.markItemCompleted(itemId)
            Log.i(TAG, "Completed reading: $itemId")
        }

        /**
         * Archive an item, removing it from the active list.
         *
         * @param itemId Item ID
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun archiveItem(itemId: String) {
            repository.getItemById(itemId) ?: throw ReadingListError.ItemNotFound
            repository.archiveItem(itemId)
            Log.i(TAG, "Archived reading: $itemId")
        }

        /**
         * Reset reading progress for an item back to the beginning.
         *
         * @param itemId Item ID
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun resetProgress(itemId: String) {
            repository.getItemById(itemId) ?: throw ReadingListError.ItemNotFound
            repository.resetProgress(itemId)
            Log.i(TAG, "Reset progress for: $itemId")
        }

        // ── Item Metadata Updates ──

        /**
         * Update metadata for a reading list item (title and/or author).
         *
         * @param itemId Item ID
         * @param title New title (null to keep current)
         * @param author New author (null to keep current)
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun updateItemMetadata(
            itemId: String,
            title: String? = null,
            author: String? = null,
        ) {
            val item =
                repository.getItemById(itemId)
                    ?: throw ReadingListError.ItemNotFound

            val updatedItem =
                item.copy(
                    title = title ?: item.title,
                    author = author ?: item.author,
                )
            repository.updateItem(updatedItem)
        }

        /**
         * Update the audio pre-generation status for an item.
         *
         * @param itemId Item ID
         * @param status New audio pre-generation status
         */
        suspend fun updateAudioPreGenStatus(
            itemId: String,
            status: AudioPreGenStatus,
        ) {
            repository.updateAudioPreGenStatus(itemId, status)
        }

        // ── Bookmark Operations ──

        /**
         * Add a bookmark at a specific position in a reading item.
         *
         * If no chunk index is provided, bookmarks the current reading position.
         * Extracts a text snippet preview from the chunk for display purposes.
         *
         * @param itemId Item ID
         * @param chunkIndex Chunk index to bookmark (null = current position)
         * @param note Optional user note
         * @return ID of the created bookmark
         * @throws ReadingListError.ItemNotFound if the item does not exist
         */
        suspend fun addBookmark(
            itemId: String,
            chunkIndex: Int? = null,
            note: String? = null,
        ): String {
            val item =
                repository.getItemById(itemId)
                    ?: throw ReadingListError.ItemNotFound

            val targetIndex = chunkIndex ?: item.currentChunkIndex

            // Get snippet preview from the chunk text
            val snippetPreview =
                repository.getChunkByIndex(itemId, targetIndex)?.let { chunk ->
                    chunk.text.take(SNIPPET_PREVIEW_LENGTH).let { preview ->
                        if (chunk.text.length > SNIPPET_PREVIEW_LENGTH) "$preview..." else preview
                    }
                }

            val bookmarkId = UUID.randomUUID().toString()
            val bookmark =
                ReadingBookmarkEntity(
                    id = bookmarkId,
                    readingListItemId = itemId,
                    chunkIndex = targetIndex,
                    note = note,
                    snippetPreview = snippetPreview,
                    createdAt = System.currentTimeMillis(),
                )

            repository.addBookmark(bookmark)
            Log.d(TAG, "Added bookmark at chunk $targetIndex for item $itemId")

            return bookmarkId
        }

        /**
         * Remove a bookmark.
         *
         * @param bookmarkId Bookmark ID to remove
         */
        suspend fun removeBookmark(bookmarkId: String) {
            repository.deleteBookmark(bookmarkId)
        }

        /**
         * Get all bookmarks for a reading list item as a reactive Flow.
         *
         * @param itemId Item ID
         */
        fun getBookmarksForItem(itemId: String): Flow<List<ReadingBookmarkEntity>> {
            return repository.getBookmarksForItem(itemId)
        }

        // ── Delete Operations ──

        /**
         * Delete a reading list item and its associated files.
         *
         * Removes the item from the database and cleans up any associated
         * files (document copy, extracted images) from local storage.
         *
         * @param itemId Item ID to delete
         */
        suspend fun deleteItem(itemId: String) {
            val item = repository.getItemById(itemId)

            // Clean up associated files
            if (item != null) {
                deleteItemFiles(item)
            }

            repository.deleteItem(itemId)
            Log.i(TAG, "Deleted reading item: $itemId")
        }

        /**
         * Delete all archived items and their associated files.
         *
         * @return Number of items deleted
         */
        suspend fun deleteAllArchived(): Int {
            // Collect current archived items to clean up files
            val archivedItems = mutableListOf<ReadingListItemEntity>()
            repository.getItemsByStatus(ReadingListStatus.ARCHIVED).collect { items ->
                archivedItems.addAll(items)
                return@collect
            }

            // Note: Flow.collect above returns immediately for Room Flows on first emission
            archivedItems.forEach { item ->
                deleteItemFiles(item)
                repository.deleteItem(item.id)
            }

            Log.i(TAG, "Deleted ${archivedItems.size} archived items")
            return archivedItems.size
        }

        // ── Statistics ──

        /**
         * Compute reading list statistics.
         *
         * @return Statistics about the reading list
         */
        suspend fun getStatistics(): ReadingListStatistics {
            val unreadCount =
                repository.getItemCountByStatus(ReadingListStatus.UNREAD)
            val inProgressCount =
                repository.getItemCountByStatus(ReadingListStatus.IN_PROGRESS)
            val completedCount =
                repository.getItemCountByStatus(ReadingListStatus.COMPLETED)
            val archivedCount =
                repository.getItemCountByStatus(ReadingListStatus.ARCHIVED)

            return ReadingListStatistics(
                unreadCount = unreadCount,
                inProgressCount = inProgressCount,
                completedCount = completedCount,
                archivedCount = archivedCount,
            )
        }

        // ── Internal Helpers ──

        /**
         * Copy a file to the reading list documents directory.
         */
        private fun copyFileToStorage(sourceFile: File): File {
            val destinationFile =
                File(documentsDirectory, "${UUID.randomUUID()}_${sourceFile.name}")
            sourceFile.copyTo(destinationFile, overwrite = false)
            return destinationFile
        }

        /**
         * Compute SHA-256 hash of a file for deduplication.
         */
        internal fun computeFileHash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /**
         * Compute SHA-256 hash of a string for deduplication.
         */
        internal fun computeStringHash(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(text.toByteArray(Charsets.UTF_8))
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /**
         * Infer source type from a file extension.
         *
         * @return Source type, or null if unsupported
         */
        private fun inferSourceType(extension: String): ReadingListSourceType? {
            return when (extension.lowercase()) {
                "pdf" -> ReadingListSourceType.PDF
                "txt" -> ReadingListSourceType.PLAIN_TEXT
                "md", "markdown" -> ReadingListSourceType.MARKDOWN
                "html", "htm" -> ReadingListSourceType.WEB_ARTICLE
                else -> null
            }
        }

        /**
         * Delete associated files for a reading list item.
         */
        private suspend fun deleteItemFiles(item: ReadingListItemEntity) {
            // Delete the document file if it's a local path
            item.fileUrl?.let { url ->
                val file = File(url)
                if (file.exists() && file.absolutePath.startsWith(documentsDirectory.absolutePath)) {
                    file.delete()
                }
            }

            // Delete associated image files
            val assets = repository.getVisualAssetsForItem(item.id)
            assets.forEach { asset ->
                asset.localPath?.let { path ->
                    val imageFile = File(path)
                    if (imageFile.exists()) {
                        imageFile.delete()
                    }
                }
            }
        }
    }

/**
 * Data class representing a text chunk before persistence.
 *
 * Used as input to [ReadingListManager.saveChunksForItem] during
 * the import/processing pipeline.
 *
 * @property index Zero-based position in the reading order
 * @property text Chunk text content
 * @property characterOffset Character offset from the start of the full document
 * @property estimatedDurationSeconds Estimated TTS playback duration in seconds
 */
data class ChunkData(
    val index: Int,
    val text: String,
    val characterOffset: Long = 0,
    val estimatedDurationSeconds: Float = 0.0f,
)

/**
 * Data class representing a visual asset before persistence.
 *
 * Used as input to [ReadingListManager.saveVisualAssetsForItem] during
 * the import/processing pipeline.
 *
 * @property chunkIndex Chunk index where this asset appears
 * @property pageIndex Page index within the original document
 * @property positionOnPage Relative vertical position on the page (0.0 to 1.0)
 * @property mimeType MIME type of the image (e.g., "image/png")
 * @property width Image width in pixels
 * @property height Image height in pixels
 * @property altText Accessibility description of the image
 * @property localPath Local file path for the saved image data
 */
data class VisualAssetData(
    val chunkIndex: Int = 0,
    val pageIndex: Int = 0,
    val positionOnPage: Float = 0.0f,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val altText: String? = null,
    val localPath: String? = null,
)

/**
 * Reading list statistics computed from all items.
 *
 * @property unreadCount Number of unread items
 * @property inProgressCount Number of items currently being read
 * @property completedCount Number of completed items
 * @property archivedCount Number of archived items
 */
data class ReadingListStatistics(
    val unreadCount: Int = 0,
    val inProgressCount: Int = 0,
    val completedCount: Int = 0,
    val archivedCount: Int = 0,
) {
    /** Total number of active (non-archived) items. */
    val totalActiveCount: Int
        get() = unreadCount + inProgressCount

    /** Total number of all items across all statuses. */
    val totalCount: Int
        get() = unreadCount + inProgressCount + completedCount + archivedCount
}

/**
 * Errors that can occur during reading list operations.
 */
sealed class ReadingListError(
    message: String,
) : Exception(message) {
    /**
     * The file type is not supported for import.
     *
     * @property extension The unsupported file extension
     */
    class UnsupportedFileType(val extension: String) :
        ReadingListError("Unsupported file type: .$extension. Supported types: PDF, TXT, MD")

    /**
     * A document with identical content has already been imported.
     *
     * @property existingTitle Title of the existing document
     */
    class DuplicateDocument(val existingTitle: String) :
        ReadingListError("This document has already been imported: $existingTitle")

    /** No readable text could be extracted from the content. */
    data object NoTextContent :
        ReadingListError("No readable text could be extracted from this document")

    /** The specified reading list item was not found. */
    data object ItemNotFound :
        ReadingListError("Reading list item not found")
}
