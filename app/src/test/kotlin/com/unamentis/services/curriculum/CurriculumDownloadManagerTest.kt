package com.unamentis.services.curriculum

import app.cash.turbine.test
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Topic
import com.unamentis.data.model.TranscriptSegment
import com.unamentis.data.model.VisualAsset
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.repository.CurriculumRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [CurriculumDownloadManager].
 *
 * Uses MockK for external dependencies (ApiClient, CurriculumRepository, VisualAssetCache)
 * since they require network, database, and filesystem infrastructure respectively.
 */
class CurriculumDownloadManagerTest {
    private lateinit var apiClient: ApiClient
    private lateinit var curriculumRepository: CurriculumRepository
    private lateinit var visualAssetCache: VisualAssetCache
    private lateinit var downloadManager: CurriculumDownloadManager

    @Before
    fun setup() {
        apiClient = mockk(relaxed = true)
        curriculumRepository = mockk(relaxed = true)
        visualAssetCache = mockk(relaxed = true)
        downloadManager =
            CurriculumDownloadManager(
                apiClient = apiClient,
                curriculumRepository = curriculumRepository,
                visualAssetCache = visualAssetCache,
            )
    }

    // region Data Model Tests

    @Test
    fun `DownloadState NotStarted is not active`() {
        assertFalse(DownloadState.NotStarted.isActive)
    }

    @Test
    fun `DownloadState Queued is active`() {
        assertTrue(DownloadState.Queued.isActive)
    }

    @Test
    fun `DownloadState Downloading is active`() {
        assertTrue(DownloadState.Downloading(0.5).isActive)
    }

    @Test
    fun `DownloadState Completed is not active`() {
        assertFalse(DownloadState.Completed.isActive)
    }

    @Test
    fun `DownloadState Failed is not active`() {
        assertFalse(DownloadState.Failed("error").isActive)
    }

    @Test
    fun `DownloadState Paused is not active`() {
        assertFalse(DownloadState.Paused.isActive)
    }

    @Test
    fun `DownloadableTopicInfo has sensible defaults`() {
        val info =
            DownloadableTopicInfo(
                id = "topic-1",
                title = "Test Topic",
                description = "A test topic",
                orderIndex = 0,
            )
        assertEquals(0L, info.estimatedSize)
        assertFalse(info.hasAssets)
        assertEquals(0, info.segmentCount)
        assertTrue(info.isSelected)
        assertEquals(DownloadState.NotStarted, info.downloadState)
    }

    @Test
    fun `CurriculumDownloadProgress has sensible defaults`() {
        val progress =
            CurriculumDownloadProgress(
                curriculumId = "c-1",
                curriculumTitle = "Test",
            )
        assertEquals(0.0, progress.overallProgress, 0.001)
        assertEquals(0, progress.topicsCompleted)
        assertEquals(0, progress.topicsTotal)
        assertEquals(0L, progress.bytesDownloaded)
        assertEquals(0L, progress.bytesTotal)
        assertEquals(DownloadState.NotStarted, progress.state)
        assertNull(progress.currentTopicTitle)
        assertTrue(progress.topics.isEmpty())
    }

    // endregion

    // region Initial State Tests

    @Test
    fun `activeDownloads is initially empty`() {
        assertTrue(downloadManager.activeDownloads.value.isEmpty())
    }

    @Test
    fun `getProgress returns null for unknown curriculum`() {
        assertNull(downloadManager.getProgress("unknown-id"))
    }

    @Test
    fun `isDownloading returns false for unknown curriculum`() {
        assertFalse(downloadManager.isDownloading("unknown-id"))
    }

    // endregion

    // region Happy Path Download Tests

    @Test
    fun `downloadCurriculum returns curriculum on success`() =
        runTest {
            val curriculum = createTestCurriculum(topicCount = 2)

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } returns curriculum

            val result =
                downloadManager.downloadCurriculum(
                    curriculumId = curriculum.id,
                    curriculumTitle = curriculum.title,
                )

            assertEquals(curriculum.id, result.id)
            assertEquals(curriculum.title, result.title)
            assertEquals(2, result.topics.size)
        }

    @Test
    fun `downloadCurriculum saves curriculum to repository`() =
        runTest {
            val curriculum = createTestCurriculum()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } returns curriculum

            downloadManager.downloadCurriculum(
                curriculumId = curriculum.id,
                curriculumTitle = curriculum.title,
            )

            coVerify(exactly = 1) {
                curriculumRepository.saveCurriculum(curriculum)
            }
        }

    @Test
    fun `downloadCurriculum caches visual assets`() =
        runTest {
            val curriculum = createTestCurriculumWithAssets()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } returns curriculum

            downloadManager.downloadCurriculum(
                curriculumId = curriculum.id,
                curriculumTitle = curriculum.title,
            )

            // Verify each asset with a URL was cached
            coVerify(exactly = 1) {
                visualAssetCache.downloadAndCache("asset-1", "https://example.com/asset1.png")
            }
            coVerify(exactly = 1) {
                visualAssetCache.downloadAndCache("asset-2", "https://example.com/asset2.png")
            }
        }

    @Test
    fun `downloadCurriculum with selected topics passes them to API`() =
        runTest {
            val selectedIds = setOf("topic-1", "topic-3")
            val curriculum = createTestCurriculum(topicCount = 2)

            coEvery {
                apiClient.getCurriculumFullWithAssets("c-1", selectedIds)
            } returns curriculum

            downloadManager.downloadCurriculum(
                curriculumId = "c-1",
                curriculumTitle = "Test",
                selectedTopicIds = selectedIds,
            )

            coVerify {
                apiClient.getCurriculumFullWithAssets("c-1", selectedIds)
            }
        }

    // endregion

    // region Progress Tracking Tests

    @Test
    fun `downloadCurriculum tracks progress through StateFlow`() =
        runTest {
            val curriculum = createTestCurriculum()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } coAnswers {
                // Small delay to allow progress tracking
                delay(50)
                curriculum
            }

            downloadManager.activeDownloads.test {
                // Initial state
                assertEquals(emptyMap<String, CurriculumDownloadProgress>(), awaitItem())

                // Start download in background
                val downloadJob =
                    async {
                        downloadManager.downloadCurriculum(
                            curriculumId = curriculum.id,
                            curriculumTitle = curriculum.title,
                        )
                    }

                // Should see progress updates
                var sawDownloading = false
                var sawCompleted = false

                // Collect items until download is done
                while (!sawCompleted) {
                    val map = awaitItem()
                    val progress = map[curriculum.id]
                    if (progress != null) {
                        when (progress.state) {
                            is DownloadState.Downloading -> sawDownloading = true
                            is DownloadState.Completed -> sawCompleted = true
                            else -> {}
                        }
                    }
                    // If map becomes empty after completion (cleaned up), that's fine too
                    if (map.isEmpty() && sawCompleted) break
                    if (map.isEmpty() && sawDownloading) {
                        // Completed and cleaned up
                        sawCompleted = true
                        break
                    }
                }

                assertTrue("Should have seen Downloading state", sawDownloading)

                downloadJob.await()
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `downloadCurriculum removes progress after completion delay`() =
        runTest {
            val curriculum = createTestCurriculum()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } returns curriculum

            downloadManager.downloadCurriculum(
                curriculumId = curriculum.id,
                curriculumTitle = curriculum.title,
            )

            // After download completes (including the 2s display delay),
            // the progress should be removed
            assertNull(downloadManager.getProgress(curriculum.id))
        }

    @Test
    fun `isDownloading returns true during active download`() =
        runTest {
            val curriculum = createTestCurriculum()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } coAnswers {
                // Check mid-download
                delay(100)
                curriculum
            }

            val downloadJob =
                async {
                    downloadManager.downloadCurriculum(
                        curriculumId = curriculum.id,
                        curriculumTitle = curriculum.title,
                    )
                }

            // Give it a moment to start
            delay(10)
            // During download, isDownloading should be true
            assertTrue(downloadManager.isDownloading(curriculum.id))

            downloadJob.await()
        }

    // endregion

    // region Cancel Tests

    @Test
    fun `cancelDownload removes progress from activeDownloads`() =
        runTest {
            val curriculum = createTestCurriculum()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } coAnswers {
                delay(5000) // Long delay to simulate slow download
                curriculum
            }

            val downloadJob =
                async {
                    try {
                        downloadManager.downloadCurriculum(
                            curriculumId = curriculum.id,
                            curriculumTitle = curriculum.title,
                        )
                    } catch (_: CancellationException) {
                        // Expected
                    } catch (_: Exception) {
                        // May wrap cancellation
                    }
                }

            delay(10) // Let it start
            downloadManager.cancelDownload(curriculum.id)

            // Progress should be removed
            assertNull(downloadManager.getProgress(curriculum.id))
            assertFalse(downloadManager.isDownloading(curriculum.id))

            downloadJob.cancel()
        }

    @Test
    fun `cancelDownload for unknown curriculum is a no-op`() {
        // Should not throw
        downloadManager.cancelDownload("nonexistent")
        assertNull(downloadManager.getProgress("nonexistent"))
    }

    // endregion

    // region Error Handling Tests

    @Test
    fun `downloadCurriculum throws CurriculumDownloadException on API failure`() =
        runTest {
            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } throws IOException("Network unreachable")

            var thrownException: CurriculumDownloadException? = null
            try {
                downloadManager.downloadCurriculum(
                    curriculumId = "c-1",
                    curriculumTitle = "Test",
                )
            } catch (e: CurriculumDownloadException) {
                thrownException = e
            }

            assertNotNull("Should throw CurriculumDownloadException", thrownException)
            assertTrue(
                "Exception message should contain error details",
                thrownException!!.message.contains("Network unreachable"),
            )
        }

    @Test
    fun `downloadCurriculum sets Failed state on error`() =
        runTest {
            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } throws IOException("Connection refused")

            try {
                downloadManager.downloadCurriculum(
                    curriculumId = "c-1",
                    curriculumTitle = "Test",
                )
            } catch (_: CurriculumDownloadException) {
                // Expected
            }

            val progress = downloadManager.getProgress("c-1")
            assertNotNull("Progress should still exist after failure", progress)
            assertTrue(
                "State should be Failed",
                progress!!.state is DownloadState.Failed,
            )
        }

    @Test
    fun `downloadCurriculum retries on transient failure then succeeds`() =
        runTest {
            val curriculum = createTestCurriculum()
            var callCount = 0

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } answers {
                callCount++
                if (callCount < 3) {
                    throw IOException("Transient error $callCount")
                }
                curriculum
            }

            val result =
                downloadManager.downloadCurriculum(
                    curriculumId = curriculum.id,
                    curriculumTitle = curriculum.title,
                )

            assertEquals(curriculum.id, result.id)
            assertEquals(3, callCount) // 2 failures + 1 success
        }

    @Test
    fun `downloadCurriculum fails after max retries exhausted`() =
        runTest {
            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } throws IOException("Persistent failure")

            var thrownException: CurriculumDownloadException? = null
            try {
                downloadManager.downloadCurriculum(
                    curriculumId = "c-1",
                    curriculumTitle = "Test",
                )
            } catch (e: CurriculumDownloadException) {
                thrownException = e
            }

            assertNotNull("Should throw after max retries", thrownException)

            // API should have been called MAX_RETRIES times (3)
            coVerify(exactly = 3) {
                apiClient.getCurriculumFullWithAssets(any(), any())
            }
        }

    @Test
    fun `downloadCurriculum continues when individual asset download fails`() =
        runTest {
            val curriculum = createTestCurriculumWithAssets()

            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } returns curriculum

            // First asset fails, second succeeds
            coEvery {
                visualAssetCache.downloadAndCache("asset-1", any())
            } throws IOException("Asset download failed")

            coEvery {
                visualAssetCache.downloadAndCache("asset-2", any())
            } returns ByteArray(100)

            val result =
                downloadManager.downloadCurriculum(
                    curriculumId = curriculum.id,
                    curriculumTitle = curriculum.title,
                )

            // Download should complete despite one asset failure
            assertEquals(curriculum.id, result.id)

            // Both assets should have been attempted
            coVerify { visualAssetCache.downloadAndCache("asset-1", any()) }
            coVerify { visualAssetCache.downloadAndCache("asset-2", any()) }

            // Curriculum should still be saved
            coVerify { curriculumRepository.saveCurriculum(curriculum) }
        }

    @Test
    fun `downloadCurriculum rethrows CancellationException without wrapping`() =
        runTest {
            coEvery {
                apiClient.getCurriculumFullWithAssets(any(), any())
            } throws CancellationException("Cancelled")

            var caughtCancellation = false
            try {
                downloadManager.downloadCurriculum(
                    curriculumId = "c-1",
                    curriculumTitle = "Test",
                )
            } catch (_: CancellationException) {
                caughtCancellation = true
            } catch (_: CurriculumDownloadException) {
                // Should NOT be wrapped
            }

            assertTrue("Should throw CancellationException directly", caughtCancellation)
        }

    // endregion

    // region CurriculumDownloadException Tests

    @Test
    fun `CurriculumDownloadException stores message`() {
        val exception = CurriculumDownloadException("Download failed")
        assertEquals("Download failed", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `CurriculumDownloadException stores cause`() {
        val cause = IOException("Network error")
        val exception = CurriculumDownloadException("Download failed", cause)
        assertEquals("Download failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    // endregion

    // region Helper Functions

    private fun createTestCurriculum(
        id: String = "c-1",
        title: String = "Test Curriculum",
        topicCount: Int = 1,
    ): Curriculum {
        val topics =
            (0 until topicCount).map { index ->
                Topic(
                    id = "topic-$index",
                    title = "Topic ${index + 1}",
                    orderIndex = index,
                    transcript =
                        listOf(
                            TranscriptSegment(
                                id = "seg-$index",
                                type = "content",
                                content = "Test content for topic ${index + 1}",
                            ),
                        ),
                )
            }

        return Curriculum(
            id = id,
            title = title,
            description = "A test curriculum",
            version = "1.0.0",
            topics = topics,
        )
    }

    private fun createTestCurriculumWithAssets(): Curriculum {
        val topics =
            listOf(
                Topic(
                    id = "topic-0",
                    title = "Topic 1",
                    orderIndex = 0,
                    transcript =
                        listOf(
                            TranscriptSegment(
                                id = "seg-0",
                                type = "content",
                                content = "Content with assets",
                            ),
                        ),
                    visualAssets =
                        listOf(
                            VisualAsset(
                                id = "asset-1",
                                filename = "asset1.png",
                                mimeType = "image/png",
                                url = "https://example.com/asset1.png",
                            ),
                            VisualAsset(
                                id = "asset-2",
                                filename = "asset2.png",
                                mimeType = "image/png",
                                url = "https://example.com/asset2.png",
                            ),
                        ),
                ),
            )

        return Curriculum(
            id = "c-assets",
            title = "Curriculum With Assets",
            description = "Has visual assets",
            version = "1.0.0",
            topics = topics,
        )
    }

    // endregion
}
