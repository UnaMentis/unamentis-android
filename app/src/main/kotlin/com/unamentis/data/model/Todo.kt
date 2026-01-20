package com.unamentis.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Todo item for task tracking.
 *
 * Features:
 * - Task description and notes
 * - Priority levels
 * - Status tracking (ACTIVE, COMPLETED, ARCHIVED)
 * - Context linking to sessions or topics
 * - Timestamps for creation and completion
 * - AI-suggested items with confidence and reasoning
 * - Due dates for time-sensitive tasks
 */
@Entity(tableName = "todos")
@Serializable
data class Todo(
    @PrimaryKey
    val id: String,
    /**
     * Task title/description.
     */
    val title: String,
    /**
     * Additional notes or details.
     */
    val notes: String? = null,
    /**
     * Priority level.
     */
    val priority: TodoPriority = TodoPriority.MEDIUM,
    /**
     * Current status.
     */
    val status: TodoStatus = TodoStatus.ACTIVE,
    /**
     * Optional session ID context.
     */
    val sessionId: String? = null,
    /**
     * Optional topic ID context.
     */
    val topicId: String? = null,
    /**
     * Creation timestamp (milliseconds since epoch).
     */
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Completion timestamp (null if not completed).
     */
    val completedAt: Long? = null,
    /**
     * Last updated timestamp.
     */
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Due date timestamp (null if no due date).
     */
    val dueDate: Long? = null,
    /**
     * Whether this todo was suggested by AI.
     */
    val isAISuggested: Boolean = false,
    /**
     * AI's reason for suggesting this todo (only if isAISuggested).
     */
    val suggestionReason: String? = null,
    /**
     * AI's confidence in this suggestion (0.0-1.0, only if isAISuggested).
     */
    val suggestionConfidence: Float? = null,
)

/**
 * Todo priority levels.
 */
enum class TodoPriority {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Todo status values.
 */
enum class TodoStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}
