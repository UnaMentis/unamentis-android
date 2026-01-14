package com.unamentis.core.curriculum

import android.util.Log
import com.unamentis.data.model.*
import com.unamentis.data.repository.CurriculumRepository
import com.unamentis.data.repository.TopicProgressRepository
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Curriculum progress engine managing topic navigation and mastery tracking.
 *
 * Responsibilities:
 * - Load curriculum and topics
 * - Track progress through transcript segments
 * - Handle stopping points (checkpoints, quizzes, reviews)
 * - Update mastery levels based on interaction
 * - Provide context for LLM system prompts
 *
 * Navigation:
 * - Segment-by-segment progression through topics
 * - Automatic advancement on completion
 * - Stopping points pause automatic progression
 * - User can manually navigate topics
 *
 * Mastery Tracking:
 * - Starts at 0.0 (no exposure)
 * - Increases with successful interactions
 * - Decreases with errors or confusion
 * - Range: 0.0 to 1.0
 *
 * @property curriculumRepository Repository for curriculum data
 * @property topicProgressRepository Repository for progress tracking
 */
@Singleton
class CurriculumEngine
    @Inject
    constructor(
        private val curriculumRepository: CurriculumRepository,
        private val topicProgressRepository: TopicProgressRepository,
    ) {
        private val _currentCurriculum = MutableStateFlow<Curriculum?>(null)
        val currentCurriculum: StateFlow<Curriculum?> = _currentCurriculum.asStateFlow()

        private val _currentTopic = MutableStateFlow<Topic?>(null)
        val currentTopic: StateFlow<Topic?> = _currentTopic.asStateFlow()

        private val _currentSegmentIndex = MutableStateFlow(0)
        val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()

        private val _topicProgress = MutableStateFlow<TopicProgress?>(null)
        val topicProgress: StateFlow<TopicProgress?> = _topicProgress.asStateFlow()

        /**
         * Load a curriculum and optionally start at a specific topic.
         */
        suspend fun loadCurriculum(
            curriculumId: String,
            topicId: String? = null,
        ) {
            Log.i("CurriculumEngine", "Loading curriculum: $curriculumId")

            val curriculum = curriculumRepository.getCurriculumById(curriculumId)
            if (curriculum == null) {
                Log.e("CurriculumEngine", "Curriculum not found: $curriculumId")
                return
            }

            _currentCurriculum.value = curriculum

            // Load first topic or specified topic
            val targetTopicId = topicId ?: curriculum.topics.firstOrNull()?.id
            if (targetTopicId != null) {
                loadTopic(targetTopicId)
            }

            Log.i("CurriculumEngine", "Curriculum loaded: ${curriculum.title}")
        }

        /**
         * Load a specific topic.
         */
        suspend fun loadTopic(topicId: String) {
            val curriculum = _currentCurriculum.value
            if (curriculum == null) {
                Log.e("CurriculumEngine", "No curriculum loaded")
                return
            }

            val topic = curriculum.topics.find { it.id == topicId }
            if (topic == null) {
                Log.e("CurriculumEngine", "Topic not found: $topicId")
                return
            }

            _currentTopic.value = topic

            // Load or create progress
            val existingProgress = topicProgressRepository.getProgressByTopic(topicId)
            val progress =
                existingProgress ?: TopicProgress(
                    curriculumId = curriculum.id,
                    topicId = topicId,
                    completedSegments = emptyList(),
                    masteryLevel = 0.0f,
                    lastAccessedAt = System.currentTimeMillis(),
                    currentSegmentId = topic.transcript.firstOrNull()?.id,
                )

            _topicProgress.value = progress

            // Find segment index from segment ID
            val segmentIndex =
                if (progress.currentSegmentId != null) {
                    topic.transcript.indexOfFirst { it.id == progress.currentSegmentId }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }
            _currentSegmentIndex.value = segmentIndex

            Log.i("CurriculumEngine", "Topic loaded: ${topic.title} (segment $segmentIndex)")
        }

        /**
         * Advance to the next segment.
         */
        suspend fun advanceSegment() {
            val topic = _currentTopic.value ?: return
            val progress = _topicProgress.value ?: return

            val nextIndex = _currentSegmentIndex.value + 1

            if (nextIndex >= topic.transcript.size) {
                // Topic complete
                Log.i("CurriculumEngine", "Topic completed: ${topic.title}")
                markTopicComplete()
                return
            }

            // Check if next segment is a stopping point
            val nextSegment = topic.transcript[nextIndex]
            if (nextSegment.stoppingPoint != null) {
                Log.i("CurriculumEngine", "Stopping point reached: ${nextSegment.stoppingPoint.type}")
                // Don't auto-advance, wait for user action
                _currentSegmentIndex.value = nextIndex
                updateProgress()
                return
            }

            // Advance to next segment
            _currentSegmentIndex.value = nextIndex
            updateProgress()

            Log.i("CurriculumEngine", "Advanced to segment $nextIndex")
        }

        /**
         * Go back to the previous segment.
         */
        suspend fun previousSegment() {
            val previousIndex = _currentSegmentIndex.value - 1
            if (previousIndex < 0) return

            _currentSegmentIndex.value = previousIndex
            updateProgress()

            Log.i("CurriculumEngine", "Moved back to segment $previousIndex")
        }

        /**
         * Jump to a specific segment.
         */
        suspend fun goToSegment(segmentIndex: Int) {
            val topic = _currentTopic.value ?: return

            if (segmentIndex < 0 || segmentIndex >= topic.transcript.size) {
                Log.w("CurriculumEngine", "Invalid segment index: $segmentIndex")
                return
            }

            _currentSegmentIndex.value = segmentIndex
            updateProgress()

            Log.i("CurriculumEngine", "Jumped to segment $segmentIndex")
        }

        /**
         * Mark the current segment as completed.
         */
        suspend fun markSegmentComplete() {
            val topic = _currentTopic.value ?: return
            val progress = _topicProgress.value ?: return
            val segmentIndex = _currentSegmentIndex.value
            val segment = topic.transcript.getOrNull(segmentIndex) ?: return

            val updatedCompleted = progress.completedSegments.toMutableList()
            if (segment.id !in updatedCompleted) {
                updatedCompleted.add(segment.id)
            }

            _topicProgress.value =
                progress.copy(
                    completedSegments = updatedCompleted,
                    lastAccessedAt = System.currentTimeMillis(),
                )

            updateProgress()
        }

        /**
         * Mark the current topic as complete.
         */
        private suspend fun markTopicComplete() {
            val progress = _topicProgress.value ?: return

            _topicProgress.value =
                progress.copy(
                    masteryLevel = 1.0f,
                    lastAccessedAt = System.currentTimeMillis(),
                )

            updateProgress()

            // Auto-load next topic if available
            val curriculum = _currentCurriculum.value ?: return
            val currentTopicIndex = curriculum.topics.indexOfFirst { it.id == progress.topicId }
            if (currentTopicIndex != -1 && currentTopicIndex + 1 < curriculum.topics.size) {
                val nextTopic = curriculum.topics[currentTopicIndex + 1]
                loadTopic(nextTopic.id)
            }
        }

        /**
         * Update mastery level based on interaction quality.
         *
         * @param delta Change in mastery (-1.0 to 1.0)
         */
        suspend fun updateMastery(delta: Float) {
            val progress = _topicProgress.value ?: return

            val newMastery = (progress.masteryLevel + delta).coerceIn(0.0f, 1.0f)

            _topicProgress.value =
                progress.copy(
                    masteryLevel = newMastery,
                    lastAccessedAt = System.currentTimeMillis(),
                )

            updateProgress()

            Log.d("CurriculumEngine", "Mastery updated: ${progress.masteryLevel} -> $newMastery")
        }

        /**
         * Get current curriculum context for LLM system prompt.
         */
        fun getCurrentContext(): CurriculumContext? {
            val topic = _currentTopic.value ?: return null
            val segmentIndex = _currentSegmentIndex.value

            if (segmentIndex >= topic.transcript.size) return null

            val segment = topic.transcript[segmentIndex]

            // Look up visual asset if referenced
            val visualAsset =
                segment.visualAssetId?.let { assetId ->
                    topic.visualAssets.find { it.id == assetId }
                }

            return CurriculumContext(
                topicTitle = topic.title,
                segmentIndex = segmentIndex,
                totalSegments = topic.transcript.size,
                learningObjectives = topic.learningObjectives,
                currentSegment = segment,
                visualAsset = visualAsset,
            )
        }

        /**
         * Get current segment.
         */
        fun getCurrentSegment(): TranscriptSegment? {
            val topic = _currentTopic.value ?: return null
            val index = _currentSegmentIndex.value
            return topic.transcript.getOrNull(index)
        }

        /**
         * Check if at a stopping point.
         */
        fun isAtStoppingPoint(): Boolean {
            return getCurrentSegment()?.stoppingPoint != null
        }

        /**
         * Get stopping point at current segment.
         */
        fun getCurrentStoppingPoint(): StoppingPoint? {
            return getCurrentSegment()?.stoppingPoint
        }

        /**
         * Resume from stopping point (user acknowledged).
         */
        suspend fun resumeFromStoppingPoint() {
            val segment = getCurrentSegment()
            if (segment?.stoppingPoint != null) {
                Log.i("CurriculumEngine", "Resuming from stopping point")
                advanceSegment()
            }
        }

        /**
         * Update progress in database.
         */
        private suspend fun updateProgress() {
            val topic = _currentTopic.value ?: return
            val progress = _topicProgress.value ?: return
            val currentSegment = topic.transcript.getOrNull(_currentSegmentIndex.value)

            val updatedProgress =
                progress.copy(
                    currentSegmentId = currentSegment?.id,
                    lastAccessedAt = System.currentTimeMillis(),
                )
            _topicProgress.value = updatedProgress
            topicProgressRepository.saveProgress(updatedProgress)
        }

        /**
         * Get completion percentage for current topic.
         */
        fun getTopicCompletionPercentage(): Float {
            val topic = _currentTopic.value ?: return 0f
            val progress = _topicProgress.value ?: return 0f

            if (topic.transcript.isEmpty()) return 0f

            val completedCount = progress.completedSegments.size
            val totalCount = topic.transcript.size

            return (completedCount.toFloat() / totalCount.toFloat()) * 100f
        }

        /**
         * Get all topics in current curriculum with progress.
         */
        suspend fun getAllTopicsWithProgress(): List<Pair<Topic, TopicProgress?>> {
            val curriculum = _currentCurriculum.value ?: return emptyList()

            return curriculum.topics.map { topic ->
                val progress = topicProgressRepository.getProgressByTopic(topic.id)
                topic to progress
            }
        }

        /**
         * Clear current curriculum and topic.
         */
        fun clear() {
            _currentCurriculum.value = null
            _currentTopic.value = null
            _currentSegmentIndex.value = 0
            _topicProgress.value = null
        }
    }

/**
 * Curriculum context provided to LLM for system prompt.
 */
data class CurriculumContext(
    val topicTitle: String,
    val segmentIndex: Int,
    val totalSegments: Int,
    val learningObjectives: List<String>,
    val currentSegment: TranscriptSegment,
    val visualAsset: VisualAsset?,
)
