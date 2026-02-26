package com.unamentis.data.repository

import com.unamentis.data.local.dao.ReadingListDao
import com.unamentis.data.local.entity.ReadingBookmarkEntity
import com.unamentis.data.local.entity.ReadingChunkEntity
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.local.entity.ReadingVisualAssetEntity
import com.unamentis.data.model.AudioPreGenStatus
import com.unamentis.data.model.ReadingListStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ReadingListRepository.
 *
 * Tests cover:
 * - Item CRUD operations
 * - Reading position tracking
 * - Chunk management
 * - Bookmark management
 * - Visual asset management
 * - Search and filtering
 */
class ReadingListRepositoryTest {
    private lateinit var mockDao: ReadingListDao
    private lateinit var repository: ReadingListRepository

    private val testItem =
        ReadingListItemEntity(
            id = "item-1",
            title = "Test Article",
            author = "Test Author",
            sourceType = "web_article",
            status = "unread",
            fileUrl = "https://example.com/article",
            fileSizeBytes = 5000,
            addedAt = 1000000L,
        )

    private val testChunk =
        ReadingChunkEntity(
            id = "chunk-1",
            readingListItemId = "item-1",
            index = 0,
            text = "This is the first chunk of text.",
            characterOffset = 0,
            estimatedDurationSeconds = 5.0f,
        )

    private val testBookmark =
        ReadingBookmarkEntity(
            id = "bookmark-1",
            readingListItemId = "item-1",
            chunkIndex = 3,
            note = "Important section",
            snippetPreview = "...key concept here...",
            createdAt = 2000000L,
        )

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        repository = ReadingListRepository(mockDao)
    }

    // ── Item operations ──

    @Test
    fun `getAllItems returns flow from DAO`() =
        runTest {
            val items = listOf(testItem, testItem.copy(id = "item-2", title = "Another"))
            coEvery { mockDao.getAllItems() } returns flowOf(items)

            val result = repository.getAllItems().first()

            assertEquals(2, result.size)
            assertEquals("item-1", result[0].id)
            assertEquals("item-2", result[1].id)
        }

    @Test
    fun `getActiveItems excludes archived items`() =
        runTest {
            val activeItems = listOf(testItem)
            coEvery { mockDao.getActiveItems() } returns flowOf(activeItems)

            val result = repository.getActiveItems().first()

            assertEquals(1, result.size)
            assertEquals("unread", result[0].status)
        }

    @Test
    fun `getItemsByStatus delegates to DAO with raw value`() =
        runTest {
            coEvery { mockDao.getItemsByStatus("in_progress") } returns
                flowOf(listOf(testItem.copy(status = "in_progress")))

            val result = repository.getItemsByStatus(ReadingListStatus.IN_PROGRESS).first()

            assertEquals(1, result.size)
            coVerify { mockDao.getItemsByStatus("in_progress") }
        }

    @Test
    fun `getItemById returns item when found`() =
        runTest {
            coEvery { mockDao.getItemById("item-1") } returns testItem

            val result = repository.getItemById("item-1")

            assertNotNull(result)
            assertEquals("Test Article", result?.title)
        }

    @Test
    fun `getItemById returns null when not found`() =
        runTest {
            coEvery { mockDao.getItemById("unknown") } returns null

            val result = repository.getItemById("unknown")

            assertNull(result)
        }

    @Test
    fun `addItem inserts via DAO`() =
        runTest {
            coEvery { mockDao.insertItem(any()) } just Runs

            repository.addItem(testItem)

            coVerify { mockDao.insertItem(testItem) }
        }

    @Test
    fun `getItemByFileHash enables deduplication`() =
        runTest {
            val itemWithHash = testItem.copy(fileHash = "abc123")
            coEvery { mockDao.getItemByFileHash("abc123") } returns itemWithHash

            val result = repository.getItemByFileHash("abc123")

            assertNotNull(result)
            assertEquals("item-1", result?.id)
        }

    // ── Reading position ──

    @Test
    fun `updateReadingPosition delegates correctly`() =
        runTest {
            val idSlot = slot<String>()
            val chunkSlot = slot<Int>()
            val percentSlot = slot<Float>()
            val timeSlot = slot<Long>()

            coEvery {
                mockDao.updateReadingPosition(
                    capture(idSlot),
                    capture(chunkSlot),
                    capture(percentSlot),
                    capture(timeSlot),
                )
            } just Runs

            repository.updateReadingPosition("item-1", chunkIndex = 5, percentComplete = 0.5f)

            assertEquals("item-1", idSlot.captured)
            assertEquals(5, chunkSlot.captured)
            assertEquals(0.5f, percentSlot.captured)
            assertTrue(timeSlot.captured > 0)
        }

    @Test
    fun `archiveItem updates status to archived`() =
        runTest {
            coEvery { mockDao.getItemById("item-1") } returns testItem
            val itemSlot = slot<ReadingListItemEntity>()
            coEvery { mockDao.updateItem(capture(itemSlot)) } just Runs

            repository.archiveItem("item-1")

            assertEquals("archived", itemSlot.captured.status)
        }

    @Test
    fun `resetProgress sets position to zero and status to unread`() =
        runTest {
            coEvery { mockDao.getItemById("item-1") } returns
                testItem.copy(status = "in_progress", percentComplete = 0.5f)
            coEvery {
                mockDao.updateReadingPosition(any(), any(), any(), any())
            } just Runs
            val itemSlot = slot<ReadingListItemEntity>()
            coEvery { mockDao.updateItem(capture(itemSlot)) } just Runs

            repository.resetProgress("item-1")

            coVerify {
                mockDao.updateReadingPosition("item-1", 0, 0.0f, any())
            }
            assertEquals("unread", itemSlot.captured.status)
            assertNull(itemSlot.captured.completedAt)
        }

    @Test
    fun `updateAudioPreGenStatus delegates with raw value`() =
        runTest {
            coEvery { mockDao.updateAudioPreGenStatus(any(), any()) } just Runs

            repository.updateAudioPreGenStatus("item-1", AudioPreGenStatus.GENERATING)

            coVerify { mockDao.updateAudioPreGenStatus("item-1", "generating") }
        }

    // ── Delete operations ──

    @Test
    fun `deleteItem calls deleteItemWithAllData`() =
        runTest {
            coEvery { mockDao.deleteItemWithAllData("item-1") } just Runs

            repository.deleteItem("item-1")

            coVerify { mockDao.deleteItemWithAllData("item-1") }
        }

    @Test
    fun `deleteAllItems clears everything`() =
        runTest {
            coEvery { mockDao.deleteAllItems() } just Runs

            repository.deleteAllItems()

            coVerify { mockDao.deleteAllItems() }
        }

    // ── Search ──

    @Test
    fun `searchItems delegates query to DAO`() =
        runTest {
            coEvery { mockDao.searchItems("kotlin") } returns flowOf(listOf(testItem))

            val result = repository.searchItems("kotlin").first()

            assertEquals(1, result.size)
            coVerify { mockDao.searchItems("kotlin") }
        }

    // ── Chunk operations ──

    @Test
    fun `getChunksForItem returns chunks in order`() =
        runTest {
            val chunks =
                listOf(
                    testChunk,
                    testChunk.copy(id = "chunk-2", index = 1, text = "Second chunk."),
                )
            coEvery { mockDao.getChunksForItem("item-1") } returns chunks

            val result = repository.getChunksForItem("item-1")

            assertEquals(2, result.size)
            assertEquals(0, result[0].index)
            assertEquals(1, result[1].index)
        }

    @Test
    fun `saveChunks replaces existing chunks`() =
        runTest {
            val chunks =
                listOf(
                    testChunk,
                    testChunk.copy(id = "chunk-2", index = 1),
                )
            coEvery { mockDao.deleteChunksForItem("item-1") } just Runs
            coEvery { mockDao.insertChunks(any()) } just Runs

            repository.saveChunks("item-1", chunks)

            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                mockDao.deleteChunksForItem("item-1")
                mockDao.insertChunks(chunks)
            }
        }

    @Test
    fun `getChunkByIndex returns correct chunk`() =
        runTest {
            coEvery { mockDao.getChunkByIndex("item-1", 3) } returns
                testChunk.copy(id = "chunk-4", index = 3)

            val result = repository.getChunkByIndex("item-1", 3)

            assertNotNull(result)
            assertEquals(3, result?.index)
        }

    // ── Bookmark operations ──

    @Test
    fun `getBookmarksForItem returns flow`() =
        runTest {
            coEvery { mockDao.getBookmarksForItem("item-1") } returns flowOf(listOf(testBookmark))

            val result = repository.getBookmarksForItem("item-1").first()

            assertEquals(1, result.size)
            assertEquals("Important section", result[0].note)
        }

    @Test
    fun `addBookmark inserts via DAO`() =
        runTest {
            coEvery { mockDao.insertBookmark(any()) } just Runs

            repository.addBookmark(testBookmark)

            coVerify { mockDao.insertBookmark(testBookmark) }
        }

    @Test
    fun `deleteBookmark removes by ID`() =
        runTest {
            coEvery { mockDao.deleteBookmarkById("bookmark-1") } just Runs

            repository.deleteBookmark("bookmark-1")

            coVerify { mockDao.deleteBookmarkById("bookmark-1") }
        }

    // ── Visual asset operations ──

    @Test
    fun `getVisualAssetsForItem returns assets`() =
        runTest {
            val asset =
                ReadingVisualAssetEntity(
                    id = "asset-1",
                    readingListItemId = "item-1",
                    chunkIndex = 2,
                    mimeType = "image/png",
                    width = 800,
                    height = 600,
                    altText = "A diagram",
                )
            coEvery { mockDao.getVisualAssetsForItem("item-1") } returns listOf(asset)

            val result = repository.getVisualAssetsForItem("item-1")

            assertEquals(1, result.size)
            assertEquals("image/png", result[0].mimeType)
        }

    @Test
    fun `saveVisualAssets replaces existing`() =
        runTest {
            val assets =
                listOf(
                    ReadingVisualAssetEntity(
                        id = "asset-1",
                        readingListItemId = "item-1",
                        mimeType = "image/jpeg",
                    ),
                )
            coEvery { mockDao.deleteVisualAssetsForItem("item-1") } just Runs
            coEvery { mockDao.insertVisualAssets(any()) } just Runs

            repository.saveVisualAssets("item-1", assets)

            coVerify(ordering = io.mockk.Ordering.ORDERED) {
                mockDao.deleteVisualAssetsForItem("item-1")
                mockDao.insertVisualAssets(assets)
            }
        }

    // ── Count operations ──

    @Test
    fun `getItemCount returns count from DAO`() =
        runTest {
            coEvery { mockDao.getItemCount() } returns 42

            val result = repository.getItemCount()

            assertEquals(42, result)
        }
}
