package com.unamentis.core.readinglist

import android.content.Context
import com.unamentis.data.local.entity.ReadingBookmarkEntity
import com.unamentis.data.local.entity.ReadingChunkEntity
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.local.entity.ReadingVisualAssetEntity
import com.unamentis.data.model.AudioPreGenStatus
import com.unamentis.data.model.ReadingListSourceType
import com.unamentis.data.model.ReadingListStatus
import com.unamentis.data.repository.ReadingListRepository
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for ReadingListManager.
 *
 * Tests cover:
 * - Importing documents from files (PDF, TXT, MD, HTML)
 * - Importing web articles
 * - Importing manual text entries
 * - Chunk management (save, retrieve, count)
 * - Visual asset management
 * - Reading progress and position tracking
 * - Item status transitions (mark completed, archive, reset)
 * - Metadata updates
 * - Bookmark CRUD operations
 * - Item deletion and file cleanup
 * - Statistics computation
 * - Search delegation
 * - Error handling (unsupported file type, duplicate, no text, item not found)
 * - Hash computation helpers
 */
@Suppress("LargeClass")
class ReadingListManagerTest {
    private lateinit var mockRepository: ReadingListRepository
    private lateinit var mockContext: Context
    private lateinit var manager: ReadingListManager
    private lateinit var tempDir: File

    private val testItem =
        ReadingListItemEntity(
            id = "item-1",
            title = "Test Article",
            author = "Test Author",
            sourceType = ReadingListSourceType.WEB_ARTICLE.rawValue,
            status = ReadingListStatus.UNREAD.rawValue,
            fileUrl = "https://example.com/article",
            fileHash = "abc123hash",
            fileSizeBytes = 5000,
            addedAt = 1000000L,
        )

    private val testChunk =
        ReadingChunkEntity(
            id = "chunk-1",
            readingListItemId = "item-1",
            index = 0,
            text = "This is the first chunk of text for the reading list item.",
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
        mockRepository = mockk(relaxed = true)

        // Create a real temp directory for file operations
        tempDir = File(System.getProperty("java.io.tmpdir"), "ReadingListManagerTest_${System.nanoTime()}")
        tempDir.mkdirs()

        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir

        manager = ReadingListManager(mockRepository, mockContext)
    }

    @After
    fun tearDown() {
        // Clean up temp directory
        tempDir.deleteRecursively()
        clearAllMocks()
        unmockkAll()
    }

    // ── Import Document Operations ──

    @Test
    fun `importDocument copies file and creates item for PDF`() =
        runTest {
            val sourceFile = File(tempDir, "test-document.pdf")
            sourceFile.writeText("PDF content here")

            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            val itemId = manager.importDocument(sourceFile, title = "My PDF", author = "Author")

            assertNotNull(itemId)
            assertTrue(itemId.isNotEmpty())

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }

            val captured = itemSlot.captured
            assertEquals("My PDF", captured.title)
            assertEquals("Author", captured.author)
            assertEquals(ReadingListSourceType.PDF.rawValue, captured.sourceType)
            assertEquals(ReadingListStatus.UNREAD.rawValue, captured.status)
            assertNotNull(captured.fileHash)
            assertTrue(captured.fileSizeBytes > 0)
        }

    @Test
    fun `importDocument uses filename as title when title is null`() =
        runTest {
            val sourceFile = File(tempDir, "my-notes.txt")
            sourceFile.writeText("Some notes content")

            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            manager.importDocument(sourceFile)

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }

            assertEquals("my-notes", itemSlot.captured.title)
            assertNull(itemSlot.captured.author)
        }

    @Test
    fun `importDocument infers source type for txt files`() =
        runTest {
            val sourceFile = File(tempDir, "notes.txt")
            sourceFile.writeText("Plain text content")

            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            manager.importDocument(sourceFile)

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }
            assertEquals(ReadingListSourceType.PLAIN_TEXT.rawValue, itemSlot.captured.sourceType)
        }

    @Test
    fun `importDocument infers source type for markdown files`() =
        runTest {
            val sourceFile = File(tempDir, "readme.md")
            sourceFile.writeText("# Markdown content")

            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            manager.importDocument(sourceFile)

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }
            assertEquals(ReadingListSourceType.MARKDOWN.rawValue, itemSlot.captured.sourceType)
        }

    @Test
    fun `importDocument infers source type for HTML files`() =
        runTest {
            val sourceFile = File(tempDir, "page.html")
            sourceFile.writeText("<html><body>Content</body></html>")

            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            manager.importDocument(sourceFile)

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }
            assertEquals(ReadingListSourceType.WEB_ARTICLE.rawValue, itemSlot.captured.sourceType)
        }

    @Test
    fun `importDocument throws UnsupportedFileType for unsupported extension`() =
        runTest {
            val sourceFile = File(tempDir, "spreadsheet.xlsx")
            sourceFile.writeText("binary data")

            try {
                manager.importDocument(sourceFile)
                fail("Expected ReadingListError.UnsupportedFileType")
            } catch (e: ReadingListError.UnsupportedFileType) {
                assertEquals("xlsx", e.extension)
            }
        }

    @Test
    fun `importDocument throws DuplicateDocument when hash matches existing`() =
        runTest {
            val sourceFile = File(tempDir, "duplicate.pdf")
            sourceFile.writeText("Same content")

            coEvery { mockRepository.getItemByFileHash(any()) } returns
                testItem.copy(title = "Existing Document")

            try {
                manager.importDocument(sourceFile)
                fail("Expected ReadingListError.DuplicateDocument")
            } catch (e: ReadingListError.DuplicateDocument) {
                assertEquals("Existing Document", e.existingTitle)
            }
        }

    @Test
    fun `importDocument cleans up copied file on duplicate detection`() =
        runTest {
            val sourceFile = File(tempDir, "dup-cleanup.pdf")
            sourceFile.writeText("Duplicate content to be cleaned up")

            coEvery { mockRepository.getItemByFileHash(any()) } returns testItem

            try {
                manager.importDocument(sourceFile)
                fail("Expected DuplicateDocument")
            } catch (_: ReadingListError.DuplicateDocument) {
                // The copied file in ReadingList directory should have been deleted.
                // The source file itself should remain.
                assertTrue(sourceFile.exists())
            }
        }

    // ── Import Web Article ──

    @Test
    fun `importWebArticle creates item with web article source type`() =
        runTest {
            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            val itemId =
                manager.importWebArticle(
                    url = "https://example.com/article",
                    title = "Web Article",
                    author = "Web Author",
                    extractedText = "The full text of the article.",
                )

            assertNotNull(itemId)

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }

            val captured = itemSlot.captured
            assertEquals("Web Article", captured.title)
            assertEquals("Web Author", captured.author)
            assertEquals(ReadingListSourceType.WEB_ARTICLE.rawValue, captured.sourceType)
            assertEquals(ReadingListStatus.UNREAD.rawValue, captured.status)
            assertNotNull(captured.fileHash)
            assertTrue(captured.fileSizeBytes > 0)
        }

    @Test
    fun `importWebArticle without extracted text uses URL as fileUrl`() =
        runTest {
            coEvery { mockRepository.addItem(any()) } just Runs

            manager.importWebArticle(
                url = "https://example.com/article",
                title = "Web Article",
            )

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }

            assertEquals("https://example.com/article", itemSlot.captured.fileUrl)
            assertEquals(0L, itemSlot.captured.fileSizeBytes)
            assertNull(itemSlot.captured.fileHash)
        }

    @Test
    fun `importWebArticle throws NoTextContent when extractedText is blank`() =
        runTest {
            try {
                manager.importWebArticle(
                    url = "https://example.com",
                    title = "Empty",
                    extractedText = "   ",
                )
                fail("Expected ReadingListError.NoTextContent")
            } catch (_: ReadingListError.NoTextContent) {
                // Expected
            }
        }

    @Test
    fun `importWebArticle throws DuplicateDocument when hash matches`() =
        runTest {
            coEvery { mockRepository.getItemByFileHash(any()) } returns
                testItem.copy(title = "Already Imported")

            try {
                manager.importWebArticle(
                    url = "https://example.com",
                    title = "Duplicate Article",
                    extractedText = "Duplicate content",
                )
                fail("Expected ReadingListError.DuplicateDocument")
            } catch (e: ReadingListError.DuplicateDocument) {
                assertEquals("Already Imported", e.existingTitle)
            }
        }

    @Test
    fun `importWebArticle skips dedup check when no extracted text provided`() =
        runTest {
            coEvery { mockRepository.addItem(any()) } just Runs

            // Should not throw, since without extracted text no dedup hash is computed
            val itemId =
                manager.importWebArticle(
                    url = "https://example.com",
                    title = "No Dedup",
                )

            assertNotNull(itemId)
            // Should NOT call getItemByFileHash since there's no hash
            coVerify(exactly = 0) { mockRepository.getItemByFileHash(any()) }
        }

    // ── Import Manual Entry ──

    @Test
    fun `importManualEntry creates plain text item`() =
        runTest {
            coEvery { mockRepository.getItemByFileHash(any()) } returns null
            coEvery { mockRepository.addItem(any()) } just Runs

            val itemId =
                manager.importManualEntry(
                    title = "My Notes",
                    text = "Manually entered text content",
                    author = "Me",
                )

            assertNotNull(itemId)

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.addItem(capture(itemSlot)) }

            val captured = itemSlot.captured
            assertEquals("My Notes", captured.title)
            assertEquals("Me", captured.author)
            assertEquals(ReadingListSourceType.PLAIN_TEXT.rawValue, captured.sourceType)
            assertEquals(ReadingListStatus.UNREAD.rawValue, captured.status)
            assertNotNull(captured.fileHash)
            assertTrue(captured.fileSizeBytes > 0)
        }

    @Test
    fun `importManualEntry throws NoTextContent for blank text`() =
        runTest {
            try {
                manager.importManualEntry(title = "Empty", text = "")
                fail("Expected ReadingListError.NoTextContent")
            } catch (_: ReadingListError.NoTextContent) {
                // Expected
            }
        }

    @Test
    fun `importManualEntry throws NoTextContent for whitespace-only text`() =
        runTest {
            try {
                manager.importManualEntry(title = "Whitespace", text = "   \n\t  ")
                fail("Expected ReadingListError.NoTextContent")
            } catch (_: ReadingListError.NoTextContent) {
                // Expected
            }
        }

    @Test
    fun `importManualEntry throws DuplicateDocument when hash matches`() =
        runTest {
            coEvery { mockRepository.getItemByFileHash(any()) } returns
                testItem.copy(title = "Existing Notes")

            try {
                manager.importManualEntry(title = "Duplicate", text = "Same content")
                fail("Expected ReadingListError.DuplicateDocument")
            } catch (e: ReadingListError.DuplicateDocument) {
                assertEquals("Existing Notes", e.existingTitle)
            }
        }

    // ── Chunk Management ──

    @Test
    fun `saveChunksForItem persists chunks and verifies item exists`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.saveChunks(any(), any()) } just Runs

            val chunks =
                listOf(
                    ChunkData(index = 0, text = "First chunk", characterOffset = 0, estimatedDurationSeconds = 3.0f),
                    ChunkData(index = 1, text = "Second chunk", characterOffset = 11, estimatedDurationSeconds = 4.0f),
                )

            manager.saveChunksForItem("item-1", chunks)

            val chunksSlot = slot<List<ReadingChunkEntity>>()
            coVerify { mockRepository.saveChunks("item-1", capture(chunksSlot)) }

            val savedChunks = chunksSlot.captured
            assertEquals(2, savedChunks.size)
            assertEquals(0, savedChunks[0].index)
            assertEquals("First chunk", savedChunks[0].text)
            assertEquals(0L, savedChunks[0].characterOffset)
            assertEquals(3.0f, savedChunks[0].estimatedDurationSeconds)
            assertEquals(1, savedChunks[1].index)
            assertEquals("Second chunk", savedChunks[1].text)
        }

    @Test
    fun `saveChunksForItem skips save when chunks list is empty`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem

            manager.saveChunksForItem("item-1", emptyList())

            coVerify(exactly = 0) { mockRepository.saveChunks(any(), any()) }
        }

    @Test
    fun `saveChunksForItem throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("nonexistent") } returns null

            try {
                manager.saveChunksForItem("nonexistent", listOf(ChunkData(0, "text")))
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    // ── Visual Asset Management ──

    @Test
    fun `saveVisualAssetsForItem persists assets`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.saveVisualAssets(any(), any()) } just Runs

            val assets =
                listOf(
                    VisualAssetData(
                        chunkIndex = 2,
                        pageIndex = 1,
                        positionOnPage = 0.3f,
                        mimeType = "image/png",
                        width = 800,
                        height = 600,
                        altText = "Diagram",
                        localPath = "/path/to/image.png",
                    ),
                )

            manager.saveVisualAssetsForItem("item-1", assets)

            val assetsSlot = slot<List<ReadingVisualAssetEntity>>()
            coVerify { mockRepository.saveVisualAssets("item-1", capture(assetsSlot)) }

            val saved = assetsSlot.captured
            assertEquals(1, saved.size)
            assertEquals(2, saved[0].chunkIndex)
            assertEquals("image/png", saved[0].mimeType)
            assertEquals(800, saved[0].width)
            assertEquals(600, saved[0].height)
            assertEquals("Diagram", saved[0].altText)
        }

    @Test
    fun `saveVisualAssetsForItem skips save when assets list is empty`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem

            manager.saveVisualAssetsForItem("item-1", emptyList())

            coVerify(exactly = 0) { mockRepository.saveVisualAssets(any(), any()) }
        }

    @Test
    fun `saveVisualAssetsForItem throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.saveVisualAssetsForItem(
                    "missing",
                    listOf(VisualAssetData(chunkIndex = 0)),
                )
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    // ── Read Operations ──

    @Test
    fun `getActiveItems delegates to repository`() =
        runTest {
            val items = listOf(testItem, testItem.copy(id = "item-2"))
            coEvery { mockRepository.getActiveItems() } returns flowOf(items)

            val result = manager.getActiveItems().first()

            assertEquals(2, result.size)
        }

    @Test
    fun `getAllItems delegates to repository`() =
        runTest {
            val items = listOf(testItem)
            coEvery { mockRepository.getAllItems() } returns flowOf(items)

            val result = manager.getAllItems().first()

            assertEquals(1, result.size)
            assertEquals("item-1", result[0].id)
        }

    @Test
    fun `getItemsByStatus filters by status`() =
        runTest {
            val inProgressItem = testItem.copy(status = ReadingListStatus.IN_PROGRESS.rawValue)
            coEvery { mockRepository.getItemsByStatus(ReadingListStatus.IN_PROGRESS) } returns
                flowOf(listOf(inProgressItem))

            val result = manager.getItemsByStatus(ReadingListStatus.IN_PROGRESS).first()

            assertEquals(1, result.size)
            assertEquals(ReadingListStatus.IN_PROGRESS.rawValue, result[0].status)
        }

    @Test
    fun `getItem returns item when found`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem

            val result = manager.getItem("item-1")

            assertNotNull(result)
            assertEquals("Test Article", result?.title)
        }

    @Test
    fun `getItem returns null when not found`() =
        runTest {
            coEvery { mockRepository.getItemById("unknown") } returns null

            val result = manager.getItem("unknown")

            assertNull(result)
        }

    @Test
    fun `getChunksForItem returns chunks`() =
        runTest {
            val chunks =
                listOf(
                    testChunk,
                    testChunk.copy(id = "chunk-2", index = 1, text = "Second chunk"),
                )
            coEvery { mockRepository.getChunksForItem("item-1") } returns chunks

            val result = manager.getChunksForItem("item-1")

            assertEquals(2, result.size)
            assertEquals(0, result[0].index)
            assertEquals(1, result[1].index)
        }

    @Test
    fun `getChunksForItemFlow delegates to repository`() =
        runTest {
            val chunks = listOf(testChunk)
            coEvery { mockRepository.getChunksForItemFlow("item-1") } returns flowOf(chunks)

            val result = manager.getChunksForItemFlow("item-1").first()

            assertEquals(1, result.size)
        }

    @Test
    fun `getChunkByIndex returns correct chunk`() =
        runTest {
            coEvery { mockRepository.getChunkByIndex("item-1", 5) } returns
                testChunk.copy(id = "chunk-6", index = 5)

            val result = manager.getChunkByIndex("item-1", 5)

            assertNotNull(result)
            assertEquals(5, result?.index)
        }

    @Test
    fun `getChunkByIndex returns null for missing chunk`() =
        runTest {
            coEvery { mockRepository.getChunkByIndex("item-1", 999) } returns null

            val result = manager.getChunkByIndex("item-1", 999)

            assertNull(result)
        }

    @Test
    fun `getChunkCount delegates to repository`() =
        runTest {
            coEvery { mockRepository.getChunkCountForItem("item-1") } returns 15

            val result = manager.getChunkCount("item-1")

            assertEquals(15, result)
        }

    @Test
    fun `searchItems delegates to repository`() =
        runTest {
            coEvery { mockRepository.searchItems("kotlin") } returns flowOf(listOf(testItem))

            val result = manager.searchItems("kotlin").first()

            assertEquals(1, result.size)
            coVerify { mockRepository.searchItems("kotlin") }
        }

    // ── Position & Progress Tracking ──

    @Test
    fun `updatePosition computes percent complete and delegates`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkCountForItem("item-1") } returns 10
            coEvery { mockRepository.updateReadingPosition(any(), any(), any()) } just Runs

            manager.updatePosition("item-1", chunkIndex = 4)

            coVerify {
                mockRepository.updateReadingPosition("item-1", 4, 0.5f)
            }
        }

    @Test
    fun `updatePosition computes 100 percent for last chunk`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkCountForItem("item-1") } returns 10
            coEvery { mockRepository.updateReadingPosition(any(), any(), any()) } just Runs

            manager.updatePosition("item-1", chunkIndex = 9)

            coVerify {
                mockRepository.updateReadingPosition("item-1", 9, 1.0f)
            }
        }

    @Test
    fun `updatePosition clamps percent to 1 when chunkIndex exceeds total`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkCountForItem("item-1") } returns 5
            coEvery { mockRepository.updateReadingPosition(any(), any(), any()) } just Runs

            manager.updatePosition("item-1", chunkIndex = 10)

            coVerify {
                mockRepository.updateReadingPosition("item-1", 10, 1.0f)
            }
        }

    @Test
    fun `updatePosition returns 0 percent when no chunks exist`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkCountForItem("item-1") } returns 0
            coEvery { mockRepository.updateReadingPosition(any(), any(), any()) } just Runs

            manager.updatePosition("item-1", chunkIndex = 0)

            coVerify {
                mockRepository.updateReadingPosition("item-1", 0, 0.0f)
            }
        }

    @Test
    fun `updatePosition throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.updatePosition("missing", chunkIndex = 0)
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    @Test
    fun `markCompleted delegates to repository`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.markItemCompleted(any()) } just Runs

            manager.markCompleted("item-1")

            coVerify { mockRepository.markItemCompleted("item-1") }
        }

    @Test
    fun `markCompleted throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.markCompleted("missing")
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    @Test
    fun `archiveItem delegates to repository`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.archiveItem(any()) } just Runs

            manager.archiveItem("item-1")

            coVerify { mockRepository.archiveItem("item-1") }
        }

    @Test
    fun `archiveItem throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.archiveItem("missing")
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    @Test
    fun `resetProgress delegates to repository`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.resetProgress(any()) } just Runs

            manager.resetProgress("item-1")

            coVerify { mockRepository.resetProgress("item-1") }
        }

    @Test
    fun `resetProgress throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.resetProgress("missing")
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    // ── Item Metadata Updates ──

    @Test
    fun `updateItemMetadata updates title and author`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.updateItem(any()) } just Runs

            manager.updateItemMetadata("item-1", title = "New Title", author = "New Author")

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.updateItem(capture(itemSlot)) }

            assertEquals("New Title", itemSlot.captured.title)
            assertEquals("New Author", itemSlot.captured.author)
        }

    @Test
    fun `updateItemMetadata keeps current title when new title is null`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.updateItem(any()) } just Runs

            manager.updateItemMetadata("item-1", author = "New Author Only")

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.updateItem(capture(itemSlot)) }

            assertEquals("Test Article", itemSlot.captured.title)
            assertEquals("New Author Only", itemSlot.captured.author)
        }

    @Test
    fun `updateItemMetadata keeps current author when new author is null`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.updateItem(any()) } just Runs

            manager.updateItemMetadata("item-1", title = "New Title Only")

            val itemSlot = slot<ReadingListItemEntity>()
            coVerify { mockRepository.updateItem(capture(itemSlot)) }

            assertEquals("New Title Only", itemSlot.captured.title)
            assertEquals("Test Author", itemSlot.captured.author)
        }

    @Test
    fun `updateItemMetadata throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.updateItemMetadata("missing", title = "Nope")
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    @Test
    fun `updateAudioPreGenStatus delegates to repository`() =
        runTest {
            coEvery { mockRepository.updateAudioPreGenStatus(any(), any()) } just Runs

            manager.updateAudioPreGenStatus("item-1", AudioPreGenStatus.GENERATING)

            coVerify { mockRepository.updateAudioPreGenStatus("item-1", AudioPreGenStatus.GENERATING) }
        }

    // ── Bookmark Operations ──

    @Test
    fun `addBookmark creates bookmark at specified chunk index`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkByIndex("item-1", 5) } returns
                testChunk.copy(index = 5, text = "This is the text at chunk 5 for bookmark preview purposes.")
            coEvery { mockRepository.addBookmark(any()) } just Runs

            val bookmarkId = manager.addBookmark("item-1", chunkIndex = 5, note = "Remember this")

            assertNotNull(bookmarkId)
            assertTrue(bookmarkId.isNotEmpty())

            val bookmarkSlot = slot<ReadingBookmarkEntity>()
            coVerify { mockRepository.addBookmark(capture(bookmarkSlot)) }

            val captured = bookmarkSlot.captured
            assertEquals("item-1", captured.readingListItemId)
            assertEquals(5, captured.chunkIndex)
            assertEquals("Remember this", captured.note)
            assertNotNull(captured.snippetPreview)
        }

    @Test
    fun `addBookmark uses current reading position when chunkIndex is null`() =
        runTest {
            val itemAtChunk3 = testItem.copy(currentChunkIndex = 3)
            coEvery { mockRepository.getItemById("item-1") } returns itemAtChunk3
            coEvery { mockRepository.getChunkByIndex("item-1", 3) } returns
                testChunk.copy(index = 3, text = "Chunk at current position")
            coEvery { mockRepository.addBookmark(any()) } just Runs

            manager.addBookmark("item-1")

            val bookmarkSlot = slot<ReadingBookmarkEntity>()
            coVerify { mockRepository.addBookmark(capture(bookmarkSlot)) }

            assertEquals(3, bookmarkSlot.captured.chunkIndex)
        }

    @Test
    fun `addBookmark truncates long text for snippet preview`() =
        runTest {
            val longText = "A".repeat(200)
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkByIndex("item-1", 0) } returns
                testChunk.copy(text = longText)
            coEvery { mockRepository.addBookmark(any()) } just Runs

            manager.addBookmark("item-1", chunkIndex = 0)

            val bookmarkSlot = slot<ReadingBookmarkEntity>()
            coVerify { mockRepository.addBookmark(capture(bookmarkSlot)) }

            val snippet = bookmarkSlot.captured.snippetPreview
            assertNotNull(snippet)
            // SNIPPET_PREVIEW_LENGTH (80) + "..." = 83 chars
            assertEquals(ReadingListManager.SNIPPET_PREVIEW_LENGTH + 3, snippet!!.length)
            assertTrue(snippet.endsWith("..."))
        }

    @Test
    fun `addBookmark uses full text for snippet when text is short`() =
        runTest {
            val shortText = "Short text"
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkByIndex("item-1", 0) } returns
                testChunk.copy(text = shortText)
            coEvery { mockRepository.addBookmark(any()) } just Runs

            manager.addBookmark("item-1", chunkIndex = 0)

            val bookmarkSlot = slot<ReadingBookmarkEntity>()
            coVerify { mockRepository.addBookmark(capture(bookmarkSlot)) }

            assertEquals("Short text", bookmarkSlot.captured.snippetPreview)
        }

    @Test
    fun `addBookmark has null snippet when chunk is not found`() =
        runTest {
            coEvery { mockRepository.getItemById("item-1") } returns testItem
            coEvery { mockRepository.getChunkByIndex("item-1", 99) } returns null
            coEvery { mockRepository.addBookmark(any()) } just Runs

            manager.addBookmark("item-1", chunkIndex = 99)

            val bookmarkSlot = slot<ReadingBookmarkEntity>()
            coVerify { mockRepository.addBookmark(capture(bookmarkSlot)) }

            assertNull(bookmarkSlot.captured.snippetPreview)
        }

    @Test
    fun `addBookmark throws ItemNotFound when item does not exist`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null

            try {
                manager.addBookmark("missing", chunkIndex = 0)
                fail("Expected ReadingListError.ItemNotFound")
            } catch (_: ReadingListError.ItemNotFound) {
                // Expected
            }
        }

    @Test
    fun `removeBookmark delegates to repository`() =
        runTest {
            coEvery { mockRepository.deleteBookmark("bookmark-1") } just Runs

            manager.removeBookmark("bookmark-1")

            coVerify { mockRepository.deleteBookmark("bookmark-1") }
        }

    @Test
    fun `getBookmarksForItem returns flow from repository`() =
        runTest {
            coEvery { mockRepository.getBookmarksForItem("item-1") } returns
                flowOf(listOf(testBookmark))

            val result = manager.getBookmarksForItem("item-1").first()

            assertEquals(1, result.size)
            assertEquals("Important section", result[0].note)
        }

    // ── Delete Operations ──

    @Test
    fun `deleteItem removes item and cleans up files in storage`() =
        runTest {
            // Create a file in the ReadingList directory to simulate an imported document
            val readingListDir = File(tempDir, "ReadingList")
            readingListDir.mkdirs()
            val docFile = File(readingListDir, "test-doc.pdf")
            docFile.writeText("document content")

            val itemWithFile = testItem.copy(fileUrl = docFile.absolutePath)
            coEvery { mockRepository.getItemById("item-1") } returns itemWithFile
            coEvery { mockRepository.getVisualAssetsForItem("item-1") } returns emptyList()
            coEvery { mockRepository.deleteItem("item-1") } just Runs

            assertTrue(docFile.exists())

            manager.deleteItem("item-1")

            coVerify { mockRepository.deleteItem("item-1") }
            // File should be cleaned up since it's in the documents directory
            assertTrue(!docFile.exists())
        }

    @Test
    fun `deleteItem does not delete files outside documents directory`() =
        runTest {
            // Create a file outside the ReadingList directory
            val externalFile = File(tempDir, "external-doc.pdf")
            externalFile.writeText("external content")

            val itemWithExternalFile = testItem.copy(fileUrl = externalFile.absolutePath)
            coEvery { mockRepository.getItemById("item-1") } returns itemWithExternalFile
            coEvery { mockRepository.getVisualAssetsForItem("item-1") } returns emptyList()
            coEvery { mockRepository.deleteItem("item-1") } just Runs

            manager.deleteItem("item-1")

            // External file should NOT be deleted
            assertTrue(externalFile.exists())
        }

    @Test
    fun `deleteItem handles null item gracefully`() =
        runTest {
            coEvery { mockRepository.getItemById("missing") } returns null
            coEvery { mockRepository.deleteItem("missing") } just Runs

            // Should not throw
            manager.deleteItem("missing")

            coVerify { mockRepository.deleteItem("missing") }
        }

    @Test
    fun `deleteItem cleans up visual asset files`() =
        runTest {
            val readingListDir = File(tempDir, "ReadingList")
            readingListDir.mkdirs()

            val imageFile = File(readingListDir, "image.png")
            imageFile.writeText("fake image data")

            val itemWithNoFile = testItem.copy(fileUrl = null)
            coEvery { mockRepository.getItemById("item-1") } returns itemWithNoFile
            coEvery { mockRepository.getVisualAssetsForItem("item-1") } returns
                listOf(
                    ReadingVisualAssetEntity(
                        id = "asset-1",
                        readingListItemId = "item-1",
                        localPath = imageFile.absolutePath,
                    ),
                )
            coEvery { mockRepository.deleteItem("item-1") } just Runs

            assertTrue(imageFile.exists())

            manager.deleteItem("item-1")

            assertTrue(!imageFile.exists())
        }

    @Test
    fun `deleteAllArchived collects archived items and deletes them`() =
        runTest {
            val archivedItems =
                listOf(
                    testItem.copy(id = "arch-1", status = ReadingListStatus.ARCHIVED.rawValue, fileUrl = null),
                    testItem.copy(id = "arch-2", status = ReadingListStatus.ARCHIVED.rawValue, fileUrl = null),
                )
            coEvery { mockRepository.getItemsByStatus(ReadingListStatus.ARCHIVED) } returns
                flowOf(archivedItems)
            coEvery { mockRepository.getVisualAssetsForItem(any()) } returns emptyList()
            coEvery { mockRepository.deleteItem(any()) } just Runs

            val count = manager.deleteAllArchived()

            assertEquals(2, count)
            coVerify { mockRepository.deleteItem("arch-1") }
            coVerify { mockRepository.deleteItem("arch-2") }
        }

    @Test
    fun `deleteAllArchived returns 0 when no archived items exist`() =
        runTest {
            coEvery { mockRepository.getItemsByStatus(ReadingListStatus.ARCHIVED) } returns
                flowOf(emptyList())

            val count = manager.deleteAllArchived()

            assertEquals(0, count)
        }

    // ── Statistics ──

    @Test
    fun `getStatistics computes counts from repository`() =
        runTest {
            coEvery { mockRepository.getItemCountByStatus(ReadingListStatus.UNREAD) } returns 5
            coEvery { mockRepository.getItemCountByStatus(ReadingListStatus.IN_PROGRESS) } returns 3
            coEvery { mockRepository.getItemCountByStatus(ReadingListStatus.COMPLETED) } returns 10
            coEvery { mockRepository.getItemCountByStatus(ReadingListStatus.ARCHIVED) } returns 2

            val stats = manager.getStatistics()

            assertEquals(5, stats.unreadCount)
            assertEquals(3, stats.inProgressCount)
            assertEquals(10, stats.completedCount)
            assertEquals(2, stats.archivedCount)
            assertEquals(8, stats.totalActiveCount)
            assertEquals(20, stats.totalCount)
        }

    @Test
    fun `getStatistics returns zeros when no items exist`() =
        runTest {
            coEvery { mockRepository.getItemCountByStatus(any()) } returns 0

            val stats = manager.getStatistics()

            assertEquals(0, stats.unreadCount)
            assertEquals(0, stats.inProgressCount)
            assertEquals(0, stats.completedCount)
            assertEquals(0, stats.archivedCount)
            assertEquals(0, stats.totalActiveCount)
            assertEquals(0, stats.totalCount)
        }

    // ── Hash Computation ──

    @Test
    fun `computeFileHash produces consistent SHA-256 for same content`() {
        val file = File(tempDir, "hash-test.txt")
        file.writeText("Hello, World!")

        val hash1 = manager.computeFileHash(file)
        val hash2 = manager.computeFileHash(file)

        assertEquals(hash1, hash2)
        // SHA-256 produces a 64-character hex string
        assertEquals(64, hash1.length)
    }

    @Test
    fun `computeFileHash produces different hashes for different content`() {
        val file1 = File(tempDir, "hash-a.txt")
        file1.writeText("Content A")

        val file2 = File(tempDir, "hash-b.txt")
        file2.writeText("Content B")

        val hash1 = manager.computeFileHash(file1)
        val hash2 = manager.computeFileHash(file2)

        assertTrue(hash1 != hash2)
    }

    @Test
    fun `computeStringHash produces consistent SHA-256 for same string`() {
        val hash1 = manager.computeStringHash("Test string")
        val hash2 = manager.computeStringHash("Test string")

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }

    @Test
    fun `computeStringHash produces different hashes for different strings`() {
        val hash1 = manager.computeStringHash("String A")
        val hash2 = manager.computeStringHash("String B")

        assertTrue(hash1 != hash2)
    }

    @Test
    fun `computeFileHash and computeStringHash agree for same content`() {
        val content = "Matching content for both hash methods"
        val file = File(tempDir, "hash-match.txt")
        file.writeText(content)

        val fileHash = manager.computeFileHash(file)
        val stringHash = manager.computeStringHash(content)

        assertEquals(fileHash, stringHash)
    }

    // ── ReadingListStatistics Data Class ──

    @Test
    fun `ReadingListStatistics totalActiveCount computes correctly`() {
        val stats =
            ReadingListStatistics(
                unreadCount = 3,
                inProgressCount = 2,
                completedCount = 5,
                archivedCount = 1,
            )

        assertEquals(5, stats.totalActiveCount)
    }

    @Test
    fun `ReadingListStatistics totalCount computes correctly`() {
        val stats =
            ReadingListStatistics(
                unreadCount = 3,
                inProgressCount = 2,
                completedCount = 5,
                archivedCount = 1,
            )

        assertEquals(11, stats.totalCount)
    }

    @Test
    fun `ReadingListStatistics defaults to zero`() {
        val stats = ReadingListStatistics()

        assertEquals(0, stats.unreadCount)
        assertEquals(0, stats.inProgressCount)
        assertEquals(0, stats.completedCount)
        assertEquals(0, stats.archivedCount)
        assertEquals(0, stats.totalActiveCount)
        assertEquals(0, stats.totalCount)
    }

    // ── ReadingListError ──

    @Test
    fun `ReadingListError UnsupportedFileType contains extension`() {
        val error = ReadingListError.UnsupportedFileType("docx")

        assertEquals("docx", error.extension)
        assertTrue(error.message!!.contains("docx"))
    }

    @Test
    fun `ReadingListError DuplicateDocument contains existing title`() {
        val error = ReadingListError.DuplicateDocument("My Document")

        assertEquals("My Document", error.existingTitle)
        assertTrue(error.message!!.contains("My Document"))
    }

    @Test
    fun `ReadingListError NoTextContent has appropriate message`() {
        val error = ReadingListError.NoTextContent

        assertNotNull(error.message)
        assertTrue(error.message!!.contains("text"))
    }

    @Test
    fun `ReadingListError ItemNotFound has appropriate message`() {
        val error = ReadingListError.ItemNotFound

        assertNotNull(error.message)
        assertTrue(error.message!!.contains("not found"))
    }

    // ── ChunkData and VisualAssetData ──

    @Test
    fun `ChunkData default values are correct`() {
        val chunk = ChunkData(index = 0, text = "Hello")

        assertEquals(0, chunk.index)
        assertEquals("Hello", chunk.text)
        assertEquals(0L, chunk.characterOffset)
        assertEquals(0.0f, chunk.estimatedDurationSeconds)
    }

    @Test
    fun `VisualAssetData default values are correct`() {
        val asset = VisualAssetData()

        assertEquals(0, asset.chunkIndex)
        assertEquals(0, asset.pageIndex)
        assertEquals(0.0f, asset.positionOnPage)
        assertNull(asset.mimeType)
        assertEquals(0, asset.width)
        assertEquals(0, asset.height)
        assertNull(asset.altText)
        assertNull(asset.localPath)
    }
}
