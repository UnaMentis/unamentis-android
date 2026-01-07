package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for tracking user progress through topics.
 *
 * @property topicId Topic identifier (primary key)
 * @property curriculumId Curriculum identifier
 * @property timeSpentSeconds Total time spent on this topic
 * @property masteryLevel Estimated mastery (0.0 - 1.0)
 * @property lastAccessedAt Last time topic was studied (Unix timestamp)
 * @property completedSegments List of completed segment IDs
 * @property currentSegmentId ID of current segment (null if completed)
 */
@Entity(tableName = "topic_progress")
data class TopicProgressEntity(
    @PrimaryKey
    val topicId: String,
    val curriculumId: String,
    val timeSpentSeconds: Long = 0,
    val masteryLevel: Float = 0.0f,
    val lastAccessedAt: Long,
    val completedSegments: List<String> = emptyList(),
    val currentSegmentId: String? = null
)
