package com.unamentis.data.local.dao

import androidx.room.*
import com.unamentis.data.local.entity.TopicProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for topic progress tracking.
 *
 * Provides methods to query and update user progress through curriculum topics.
 */
@Dao
interface TopicProgressDao {

    /**
     * Get progress for all topics in a curriculum.
     *
     * @param curriculumId Curriculum identifier
     * @return Flow of progress list
     */
    @Query("SELECT * FROM topic_progress WHERE curriculumId = :curriculumId ORDER BY lastAccessedAt DESC")
    fun getProgressByCurriculum(curriculumId: String): Flow<List<TopicProgressEntity>>

    /**
     * Get progress for a specific topic.
     *
     * @param topicId Topic identifier
     * @return Topic progress or null if not started
     */
    @Query("SELECT * FROM topic_progress WHERE topicId = :topicId")
    suspend fun getProgressByTopic(topicId: String): TopicProgressEntity?

    /**
     * Insert or update topic progress.
     *
     * @param progress Progress to insert/update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: TopicProgressEntity)

    /**
     * Update time spent and last accessed timestamp.
     *
     * @param topicId Topic identifier
     * @param additionalSeconds Additional time to add
     * @param timestamp New last accessed timestamp
     */
    @Query(
        """
        UPDATE topic_progress
        SET timeSpentSeconds = timeSpentSeconds + :additionalSeconds,
            lastAccessedAt = :timestamp
        WHERE topicId = :topicId
        """
    )
    suspend fun updateTimeSpent(topicId: String, additionalSeconds: Long, timestamp: Long)

    /**
     * Update mastery level.
     *
     * @param topicId Topic identifier
     * @param masteryLevel New mastery level (0.0 - 1.0)
     */
    @Query("UPDATE topic_progress SET masteryLevel = :masteryLevel WHERE topicId = :topicId")
    suspend fun updateMasteryLevel(topicId: String, masteryLevel: Float)

    /**
     * Delete progress for a topic.
     *
     * @param topicId Topic identifier
     */
    @Query("DELETE FROM topic_progress WHERE topicId = :topicId")
    suspend fun deleteProgress(topicId: String)

    /**
     * Delete all progress.
     */
    @Query("DELETE FROM topic_progress")
    suspend fun deleteAllProgress()
}
