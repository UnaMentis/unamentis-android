package com.unamentis.core.curriculum

import com.unamentis.data.model.*
import com.unamentis.data.repository.CurriculumRepository
import com.unamentis.data.repository.TopicProgressRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CurriculumEngine.
 *
 * Tests topic navigation, progress tracking, and mastery updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurriculumEngineTest {
    private lateinit var curriculumRepository: CurriculumRepository
    private lateinit var topicProgressRepository: TopicProgressRepository
    private lateinit var curriculumEngine: CurriculumEngine

    private val testCurriculum =
        Curriculum(
            id = "curriculum-1",
            title = "Introduction to Physics",
            description = "Basic physics concepts",
            version = "1.0",
            topics =
                listOf(
                    Topic(
                        id = "topic-1",
                        title = "Newton's Laws",
                        orderIndex = 0,
                        transcript =
                            listOf(
                                TranscriptSegment(
                                    id = "seg-1",
                                    type = "content",
                                    content = "Let's learn about Newton's first law.",
                                    spokenText = null,
                                    stoppingPoint = null,
                                    visualAssetId = null,
                                ),
                                TranscriptSegment(
                                    id = "seg-2",
                                    type = "content",
                                    content = "An object at rest stays at rest.",
                                    spokenText = null,
                                    stoppingPoint = null,
                                    visualAssetId = null,
                                ),
                                TranscriptSegment(
                                    id = "seg-3",
                                    type = "checkpoint",
                                    content = "Let's test your understanding.",
                                    spokenText = null,
                                    stoppingPoint =
                                        StoppingPoint(
                                            type = "quiz",
                                            prompt = "What is Newton's First Law?",
                                        ),
                                    visualAssetId = null,
                                ),
                            ),
                        description = "Laws of motion",
                        learningObjectives = listOf("Understand F=ma", "Apply to real scenarios"),
                    ),
                    Topic(
                        id = "topic-2",
                        title = "Energy and Work",
                        orderIndex = 1,
                        transcript =
                            listOf(
                                TranscriptSegment(
                                    id = "seg-4",
                                    type = "content",
                                    content = "Energy is the capacity to do work.",
                                    spokenText = null,
                                    stoppingPoint = null,
                                    visualAssetId = null,
                                ),
                            ),
                        description = "Energy concepts",
                        learningObjectives = listOf("Define kinetic energy", "Calculate work"),
                    ),
                ),
        )

    @Before
    fun setup() {
        curriculumRepository = mockk(relaxed = true)
        topicProgressRepository = mockk(relaxed = true)

        coEvery { curriculumRepository.getCurriculumById(any()) } returns testCurriculum
        coEvery { topicProgressRepository.getProgressByTopic(any()) } returns null
        coEvery { topicProgressRepository.saveProgress(any()) } just Runs

        curriculumEngine =
            CurriculumEngine(
                curriculumRepository = curriculumRepository,
                topicProgressRepository = topicProgressRepository,
            )
    }

    @After
    fun teardown() {
        curriculumEngine.clear()
    }

    @Test
    fun `loadCurriculum loads curriculum and first topic`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            assertEquals(testCurriculum, curriculumEngine.currentCurriculum.value)
            assertEquals("topic-1", curriculumEngine.currentTopic.value?.id)
            assertEquals(0, curriculumEngine.currentSegmentIndex.value)
        }

    @Test
    fun `loadCurriculum with topicId loads specific topic`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1", topicId = "topic-2")

            assertEquals("topic-2", curriculumEngine.currentTopic.value?.id)
        }

    @Test
    fun `loadTopic creates new progress if none exists`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            val progress = curriculumEngine.topicProgress.value
            assertNotNull(progress)
            assertEquals("topic-1", progress!!.topicId)
            assertEquals(0.0f, progress.masteryLevel, 0.001f)
        }

    @Test
    fun `loadTopic loads existing progress`() =
        runTest {
            val existingProgress =
                TopicProgress(
                    curriculumId = "curriculum-1",
                    topicId = "topic-1",
                    completedSegments = listOf("seg-1", "seg-2"),
                    masteryLevel = 0.75f,
                    lastAccessedAt = System.currentTimeMillis(),
                    currentSegmentId = "seg-3",
                )

            coEvery {
                topicProgressRepository.getProgressByTopic("topic-1")
            } returns existingProgress

            curriculumEngine.loadCurriculum("curriculum-1")

            assertEquals(2, curriculumEngine.currentSegmentIndex.value)
            assertEquals(0.75f, curriculumEngine.topicProgress.value?.masteryLevel ?: 0f, 0.001f)
        }

    @Test
    fun `advanceSegment moves to next segment`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            assertEquals(0, curriculumEngine.currentSegmentIndex.value)

            curriculumEngine.advanceSegment()

            assertEquals(1, curriculumEngine.currentSegmentIndex.value)
            coVerify { topicProgressRepository.saveProgress(any()) }
        }

    @Test
    fun `advanceSegment stops at stopping point`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            // Advance to segment 1
            curriculumEngine.advanceSegment()
            assertEquals(1, curriculumEngine.currentSegmentIndex.value)

            // Advance to segment 2 (has stopping point)
            curriculumEngine.advanceSegment()
            assertEquals(2, curriculumEngine.currentSegmentIndex.value)

            // Should be at stopping point
            assertTrue(curriculumEngine.isAtStoppingPoint())
            assertNotNull(curriculumEngine.getCurrentStoppingPoint())
            assertEquals("quiz", curriculumEngine.getCurrentStoppingPoint()?.type)
        }

    @Test
    fun `resumeFromStoppingPoint advances past stopping point`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            // Jump to stopping point
            curriculumEngine.goToSegment(2)
            assertTrue(curriculumEngine.isAtStoppingPoint())

            // Resume
            curriculumEngine.resumeFromStoppingPoint()

            // Should have advanced past (to end of topic in this case)
            assertFalse(curriculumEngine.isAtStoppingPoint())
        }

    @Test
    fun `advanceSegment at end of topic marks topic complete and loads next`() =
        runTest {
            // Capture saved progress to verify mastery was set to 1.0
            val savedProgresses = mutableListOf<TopicProgress>()
            coEvery { topicProgressRepository.saveProgress(capture(savedProgresses)) } just Runs

            curriculumEngine.loadCurriculum("curriculum-1")

            // Jump to last segment
            val lastIndex = testCurriculum.topics[0].transcript.size - 1
            curriculumEngine.goToSegment(lastIndex)

            // Advance past end
            curriculumEngine.advanceSegment()

            // Should auto-load next topic
            assertEquals("topic-2", curriculumEngine.currentTopic.value?.id)

            // Verify that topic-1 was saved with mastery 1.0 before auto-loading topic-2
            val topic1Progress = savedProgresses.find { it.topicId == "topic-1" && it.masteryLevel == 1.0f }
            assertNotNull("Topic 1 should have been saved with mastery 1.0", topic1Progress)
        }

    @Test
    fun `previousSegment moves back`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            curriculumEngine.goToSegment(2)
            assertEquals(2, curriculumEngine.currentSegmentIndex.value)

            curriculumEngine.previousSegment()
            assertEquals(1, curriculumEngine.currentSegmentIndex.value)
        }

    @Test
    fun `previousSegment at index 0 does nothing`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            assertEquals(0, curriculumEngine.currentSegmentIndex.value)

            curriculumEngine.previousSegment()

            assertEquals(0, curriculumEngine.currentSegmentIndex.value)
        }

    @Test
    fun `goToSegment jumps to specific segment`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            curriculumEngine.goToSegment(2)

            assertEquals(2, curriculumEngine.currentSegmentIndex.value)
            coVerify { topicProgressRepository.saveProgress(any()) }
        }

    @Test
    fun `goToSegment with invalid index does nothing`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            curriculumEngine.goToSegment(999)

            assertEquals(0, curriculumEngine.currentSegmentIndex.value)
        }

    @Test
    fun `markSegmentComplete adds to completed list`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            curriculumEngine.markSegmentComplete()

            val progress = curriculumEngine.topicProgress.value
            assertTrue(progress!!.completedSegments.contains("seg-1"))
        }

    @Test
    fun `updateMastery increases mastery level`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            val initialMastery = curriculumEngine.topicProgress.value?.masteryLevel ?: 0f

            curriculumEngine.updateMastery(0.25f)

            val newMastery = curriculumEngine.topicProgress.value?.masteryLevel ?: 0f
            assertTrue(newMastery > initialMastery)
            assertEquals(0.25f, newMastery, 0.001f)
        }

    @Test
    fun `updateMastery decreases mastery level`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            // Set initial mastery
            curriculumEngine.updateMastery(0.5f)

            // Decrease
            curriculumEngine.updateMastery(-0.2f)

            val mastery = curriculumEngine.topicProgress.value?.masteryLevel ?: 0f
            assertEquals(0.3f, mastery, 0.001f)
        }

    @Test
    fun `updateMastery clamps to 0-1 range`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            // Try to go above 1.0
            curriculumEngine.updateMastery(2.0f)
            assertEquals(1.0f, curriculumEngine.topicProgress.value?.masteryLevel ?: 0f, 0.001f)

            // Try to go below 0.0
            curriculumEngine.updateMastery(-5.0f)
            assertEquals(0.0f, curriculumEngine.topicProgress.value?.masteryLevel ?: 0f, 0.001f)
        }

    @Test
    fun `getCurrentContext returns curriculum context`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            val context = curriculumEngine.getCurrentContext()

            assertNotNull(context)
            assertEquals("Newton's Laws", context!!.topicTitle)
            assertEquals(0, context.segmentIndex)
            assertEquals(3, context.totalSegments)
            assertEquals(2, context.learningObjectives.size)
        }

    @Test
    fun `getCurrentSegment returns current segment`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            val segment = curriculumEngine.getCurrentSegment()

            assertNotNull(segment)
            assertEquals("seg-1", segment!!.id)
            assertEquals("Let's learn about Newton's first law.", segment.content)
        }

    @Test
    fun `getTopicCompletionPercentage calculates correctly`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            // No segments completed
            assertEquals(0f, curriculumEngine.getTopicCompletionPercentage(), 0.001f)

            // Mark 1 of 3 segments complete
            curriculumEngine.markSegmentComplete()
            assertEquals(33.33f, curriculumEngine.getTopicCompletionPercentage(), 1f)

            // Advance and mark another
            curriculumEngine.advanceSegment()
            curriculumEngine.markSegmentComplete()
            assertEquals(66.66f, curriculumEngine.getTopicCompletionPercentage(), 1f)
        }

    @Test
    fun `clear resets all state`() =
        runTest {
            curriculumEngine.loadCurriculum("curriculum-1")

            curriculumEngine.clear()

            assertNull(curriculumEngine.currentCurriculum.value)
            assertNull(curriculumEngine.currentTopic.value)
            assertEquals(0, curriculumEngine.currentSegmentIndex.value)
            assertNull(curriculumEngine.topicProgress.value)
        }

    @Test
    fun `curriculum with no topics handles gracefully`() =
        runTest {
            val emptyCurriculum = testCurriculum.copy(topics = emptyList())
            coEvery { curriculumRepository.getCurriculumById(any()) } returns emptyCurriculum

            curriculumEngine.loadCurriculum("empty-curriculum")

            assertEquals(emptyCurriculum, curriculumEngine.currentCurriculum.value)
            assertNull(curriculumEngine.currentTopic.value)
        }
}
