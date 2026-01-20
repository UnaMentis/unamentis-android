package com.unamentis.data.repository

import com.unamentis.data.local.dao.TopicProgressDao
import com.unamentis.data.local.entity.TopicProgressEntity
import com.unamentis.data.model.TopicProgress
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
 * Unit tests for TopicProgressRepository.
 *
 * Tests cover:
 * - Progress retrieval by curriculum and topic
 * - Progress saving and updating
 * - Time spent tracking
 * - Mastery level updates
 * - Deletion operations
 */
class TopicProgressRepositoryTest {
    private lateinit var mockDao: TopicProgressDao
    private lateinit var repository: TopicProgressRepository

    private val testProgress =
        TopicProgressEntity(
            topicId = "topic-1",
            curriculumId = "curriculum-1",
            timeSpentSeconds = 3600L,
            masteryLevel = 0.75f,
            lastAccessedAt = System.currentTimeMillis(),
            completedSegments = listOf("seg-1", "seg-2"),
            currentSegmentId = "seg-3",
        )

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        repository = TopicProgressRepository(mockDao)
    }

    // MARK: - Get Progress Tests

    @Test
    fun `getProgressByCurriculum returns mapped progress list`() =
        runTest {
            val entities =
                listOf(
                    testProgress,
                    testProgress.copy(topicId = "topic-2", masteryLevel = 0.5f),
                )
            coEvery { mockDao.getProgressByCurriculum("curriculum-1") } returns flowOf(entities)

            val progress = repository.getProgressByCurriculum("curriculum-1").first()

            assertEquals(2, progress.size)
            assertEquals("topic-1", progress[0].topicId)
            assertEquals(0.75f, progress[0].masteryLevel)
            assertEquals("topic-2", progress[1].topicId)
            assertEquals(0.5f, progress[1].masteryLevel)
        }

    @Test
    fun `getProgressByCurriculum returns empty list when no progress`() =
        runTest {
            coEvery { mockDao.getProgressByCurriculum("curriculum-1") } returns flowOf(emptyList())

            val progress = repository.getProgressByCurriculum("curriculum-1").first()

            assertTrue(progress.isEmpty())
        }

    @Test
    fun `getProgressByTopic returns mapped progress`() =
        runTest {
            coEvery { mockDao.getProgressByTopic("topic-1") } returns testProgress

            val progress = repository.getProgressByTopic("topic-1")

            assertNotNull(progress)
            assertEquals("topic-1", progress?.topicId)
            assertEquals("curriculum-1", progress?.curriculumId)
            assertEquals(3600L, progress?.timeSpentSeconds)
            assertEquals(0.75f, progress?.masteryLevel)
        }

    @Test
    fun `getProgressByTopic returns null when not found`() =
        runTest {
            coEvery { mockDao.getProgressByTopic("unknown-topic") } returns null

            val progress = repository.getProgressByTopic("unknown-topic")

            assertNull(progress)
        }

    // MARK: - Save Progress Tests

    @Test
    fun `saveProgress inserts entity via DAO`() =
        runTest {
            val entitySlot = slot<TopicProgressEntity>()
            coEvery { mockDao.insertProgress(capture(entitySlot)) } just Runs

            val progress =
                TopicProgress(
                    topicId = "topic-new",
                    curriculumId = "curriculum-1",
                    timeSpentSeconds = 1800L,
                    masteryLevel = 0.25f,
                    lastAccessedAt = System.currentTimeMillis(),
                    completedSegments = listOf("seg-1"),
                    currentSegmentId = "seg-2",
                )

            repository.saveProgress(progress)

            coVerify { mockDao.insertProgress(any()) }
            assertEquals("topic-new", entitySlot.captured.topicId)
            assertEquals("curriculum-1", entitySlot.captured.curriculumId)
            assertEquals(1800L, entitySlot.captured.timeSpentSeconds)
            assertEquals(0.25f, entitySlot.captured.masteryLevel)
        }

    @Test
    fun `saveProgress converts model to entity correctly`() =
        runTest {
            val entitySlot = slot<TopicProgressEntity>()
            coEvery { mockDao.insertProgress(capture(entitySlot)) } just Runs

            val completedSegments = listOf("seg-a", "seg-b", "seg-c")
            val progress =
                TopicProgress(
                    topicId = "topic-test",
                    curriculumId = "curriculum-test",
                    timeSpentSeconds = 7200L,
                    masteryLevel = 0.9f,
                    lastAccessedAt = 12345678L,
                    completedSegments = completedSegments,
                    currentSegmentId = "seg-d",
                )

            repository.saveProgress(progress)

            val captured = entitySlot.captured
            assertEquals("topic-test", captured.topicId)
            assertEquals("curriculum-test", captured.curriculumId)
            assertEquals(7200L, captured.timeSpentSeconds)
            assertEquals(0.9f, captured.masteryLevel)
            assertEquals(12345678L, captured.lastAccessedAt)
            assertEquals(completedSegments, captured.completedSegments)
            assertEquals("seg-d", captured.currentSegmentId)
        }

    // MARK: - Update Time Spent Tests

    @Test
    fun `updateTimeSpent calls DAO with correct parameters`() =
        runTest {
            val topicIdSlot = slot<String>()
            val secondsSlot = slot<Long>()
            val timestampSlot = slot<Long>()

            coEvery {
                mockDao.updateTimeSpent(
                    topicId = capture(topicIdSlot),
                    additionalSeconds = capture(secondsSlot),
                    timestamp = capture(timestampSlot),
                )
            } just Runs

            repository.updateTimeSpent("topic-1", 300L)

            assertEquals("topic-1", topicIdSlot.captured)
            assertEquals(300L, secondsSlot.captured)
            assertTrue(timestampSlot.captured > 0)
        }

    @Test
    fun `updateTimeSpent records current timestamp`() =
        runTest {
            val timestampSlot = slot<Long>()
            val beforeTime = System.currentTimeMillis()

            coEvery {
                mockDao.updateTimeSpent(
                    topicId = any(),
                    additionalSeconds = any(),
                    timestamp = capture(timestampSlot),
                )
            } just Runs

            repository.updateTimeSpent("topic-1", 60L)

            val afterTime = System.currentTimeMillis()
            assertTrue(timestampSlot.captured >= beforeTime)
            assertTrue(timestampSlot.captured <= afterTime)
        }

    // MARK: - Update Mastery Tests

    @Test
    fun `updateMasteryLevel calls DAO with correct values`() =
        runTest {
            val topicIdSlot = slot<String>()
            val masterySlot = slot<Float>()

            coEvery {
                mockDao.updateMasteryLevel(capture(topicIdSlot), capture(masterySlot))
            } just Runs

            repository.updateMasteryLevel("topic-1", 0.85f)

            assertEquals("topic-1", topicIdSlot.captured)
            assertEquals(0.85f, masterySlot.captured)
        }

    @Test
    fun `updateMasteryLevel clamps to valid range - high value`() =
        runTest {
            val masterySlot = slot<Float>()

            coEvery {
                mockDao.updateMasteryLevel(any(), capture(masterySlot))
            } just Runs

            repository.updateMasteryLevel("topic-1", 1.5f)

            assertEquals(1.0f, masterySlot.captured)
        }

    @Test
    fun `updateMasteryLevel clamps to valid range - low value`() =
        runTest {
            val masterySlot = slot<Float>()

            coEvery {
                mockDao.updateMasteryLevel(any(), capture(masterySlot))
            } just Runs

            repository.updateMasteryLevel("topic-1", -0.5f)

            assertEquals(0.0f, masterySlot.captured)
        }

    @Test
    fun `updateMasteryLevel preserves valid values`() =
        runTest {
            val masterySlot = slot<Float>()

            coEvery {
                mockDao.updateMasteryLevel(any(), capture(masterySlot))
            } just Runs

            repository.updateMasteryLevel("topic-1", 0.5f)
            assertEquals(0.5f, masterySlot.captured)

            repository.updateMasteryLevel("topic-1", 0.0f)
            assertEquals(0.0f, masterySlot.captured)

            repository.updateMasteryLevel("topic-1", 1.0f)
            assertEquals(1.0f, masterySlot.captured)
        }

    // MARK: - Delete Tests

    @Test
    fun `deleteProgress calls DAO with correct topic ID`() =
        runTest {
            val topicIdSlot = slot<String>()
            coEvery { mockDao.deleteProgress(capture(topicIdSlot)) } just Runs

            repository.deleteProgress("topic-to-delete")

            assertEquals("topic-to-delete", topicIdSlot.captured)
        }

    @Test
    fun `deleteAllProgress calls DAO deleteAllProgress`() =
        runTest {
            coEvery { mockDao.deleteAllProgress() } just Runs

            repository.deleteAllProgress()

            coVerify { mockDao.deleteAllProgress() }
        }

    // MARK: - Entity to Model Conversion Tests

    @Test
    fun `progress entity converts to model with all fields`() =
        runTest {
            val entity =
                TopicProgressEntity(
                    topicId = "conv-topic",
                    curriculumId = "conv-curriculum",
                    timeSpentSeconds = 9000L,
                    masteryLevel = 0.65f,
                    lastAccessedAt = 999999L,
                    completedSegments = listOf("x", "y", "z"),
                    currentSegmentId = "w",
                )

            coEvery { mockDao.getProgressByTopic("conv-topic") } returns entity

            val progress = repository.getProgressByTopic("conv-topic")

            assertNotNull(progress)
            assertEquals("conv-topic", progress?.topicId)
            assertEquals("conv-curriculum", progress?.curriculumId)
            assertEquals(9000L, progress?.timeSpentSeconds)
            assertEquals(0.65f, progress?.masteryLevel)
            assertEquals(999999L, progress?.lastAccessedAt)
            assertEquals(listOf("x", "y", "z"), progress?.completedSegments)
            assertEquals("w", progress?.currentSegmentId)
        }

    @Test
    fun `progress list maintains order from DAO`() =
        runTest {
            val entities =
                listOf(
                    testProgress.copy(topicId = "first", masteryLevel = 0.1f),
                    testProgress.copy(topicId = "second", masteryLevel = 0.2f),
                    testProgress.copy(topicId = "third", masteryLevel = 0.3f),
                )
            coEvery { mockDao.getProgressByCurriculum("curriculum-1") } returns flowOf(entities)

            val progress = repository.getProgressByCurriculum("curriculum-1").first()

            assertEquals(3, progress.size)
            assertEquals("first", progress[0].topicId)
            assertEquals("second", progress[1].topicId)
            assertEquals("third", progress[2].topicId)
        }
}
