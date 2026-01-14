package com.unamentis.data.repository

import com.unamentis.data.local.dao.TopicProgressDao
import com.unamentis.data.local.entity.TopicProgressEntity
import com.unamentis.data.model.TopicProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for topic progress tracking.
 *
 * Responsibilities:
 * - Track user progress through topics
 * - Update mastery levels
 * - Record time spent on topics
 *
 * @property topicProgressDao Room DAO for topic progress data
 */
@Singleton
class TopicProgressRepository
    @Inject
    constructor(
        private val topicProgressDao: TopicProgressDao,
    ) {
        /**
         * Get progress for all topics in a curriculum.
         */
        fun getProgressByCurriculum(curriculumId: String): Flow<List<TopicProgress>> {
            return topicProgressDao.getProgressByCurriculum(curriculumId).map { entities ->
                entities.map { it.toModel() }
            }
        }

        /**
         * Get progress for a specific topic.
         */
        suspend fun getProgressByTopic(topicId: String): TopicProgress? {
            return topicProgressDao.getProgressByTopic(topicId)?.toModel()
        }

        /**
         * Save or update topic progress.
         */
        suspend fun saveProgress(progress: TopicProgress) {
            topicProgressDao.insertProgress(progress.toEntity())
        }

        /**
         * Update time spent on a topic.
         */
        suspend fun updateTimeSpent(
            topicId: String,
            additionalSeconds: Long,
        ) {
            topicProgressDao.updateTimeSpent(
                topicId = topicId,
                additionalSeconds = additionalSeconds,
                timestamp = System.currentTimeMillis(),
            )
        }

        /**
         * Update mastery level for a topic.
         */
        suspend fun updateMasteryLevel(
            topicId: String,
            masteryLevel: Float,
        ) {
            topicProgressDao.updateMasteryLevel(topicId, masteryLevel.coerceIn(0f, 1f))
        }

        /**
         * Delete progress for a topic.
         */
        suspend fun deleteProgress(topicId: String) {
            topicProgressDao.deleteProgress(topicId)
        }

        /**
         * Delete all progress.
         */
        suspend fun deleteAllProgress() {
            topicProgressDao.deleteAllProgress()
        }
    }

/**
 * Extension: Convert TopicProgressEntity to model.
 */
private fun TopicProgressEntity.toModel(): TopicProgress {
    return TopicProgress(
        topicId = topicId,
        curriculumId = curriculumId,
        timeSpentSeconds = timeSpentSeconds,
        masteryLevel = masteryLevel,
        lastAccessedAt = lastAccessedAt,
        completedSegments = completedSegments,
        currentSegmentId = currentSegmentId,
    )
}

/**
 * Extension: Convert TopicProgress model to entity.
 */
private fun TopicProgress.toEntity(): TopicProgressEntity {
    return TopicProgressEntity(
        topicId = topicId,
        curriculumId = curriculumId,
        timeSpentSeconds = timeSpentSeconds,
        masteryLevel = masteryLevel,
        lastAccessedAt = lastAccessedAt,
        completedSegments = completedSegments,
        currentSegmentId = currentSegmentId,
    )
}
