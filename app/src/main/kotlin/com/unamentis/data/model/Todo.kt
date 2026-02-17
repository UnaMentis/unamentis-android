package com.unamentis.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Todo item for task tracking.
 *
 * Features:
 * - Task description and notes
 * - Priority ordering for drag-drop reordering
 * - Status tracking (ACTIVE, COMPLETED, ARCHIVED)
 * - Item types (curriculum, module, topic, learning target, reinforcement, auto-resume)
 * - Source tracking (manual, voice, auto-resume, reinforcement)
 * - Context linking to sessions, topics, and curricula
 * - Auto-resume support with segment index and conversation context
 * - Curriculum suggestion links for learning targets
 * - Timestamps for creation, completion, and archival
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
     * Priority level (used for legacy sorting).
     */
    val priority: TodoPriority = TodoPriority.MEDIUM,
    /**
     * Ordering index for drag-drop reordering (lower = higher priority).
     */
    val sortOrder: Int = 0,
    /**
     * Current status.
     */
    val status: TodoStatus = TodoStatus.ACTIVE,
    /**
     * Type of todo item.
     */
    val itemType: TodoItemType = TodoItemType.LEARNING_TARGET,
    /**
     * How this item was created.
     */
    val source: TodoItemSource = TodoItemSource.MANUAL,
    /**
     * Optional session ID context.
     */
    val sessionId: String? = null,
    /**
     * Optional topic ID context.
     */
    val topicId: String? = null,
    /**
     * Optional curriculum ID for curriculum-linked items.
     */
    val curriculumId: String? = null,
    /**
     * Granularity level for curriculum items (curriculum, module, topic).
     */
    val granularity: String? = null,
    /**
     * Topic ID for auto-resume items (the topic to resume).
     */
    val resumeTopicId: String? = null,
    /**
     * Segment index to resume from for auto-resume items.
     */
    val resumeSegmentIndex: Int = 0,
    /**
     * Serialized conversation context for auto-resume items (JSON string).
     */
    val resumeConversationContext: String? = null,
    /**
     * Suggested curriculum IDs for learning target items (JSON-encoded list).
     */
    val suggestedCurriculumIds: String? = null,
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
     * Archived timestamp (null if not archived).
     */
    val archivedAt: Long? = null,
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
    /** Not yet started, visible in the main list. */
    ACTIVE,

    /** Currently being worked on. */
    IN_PROGRESS,

    /** Finished. */
    COMPLETED,

    /** Archived (kept permanently but hidden from main list). */
    ARCHIVED,
    ;

    /**
     * Whether the item is considered active (visible in main list).
     */
    val isActive: Boolean
        get() = this == ACTIVE || this == IN_PROGRESS
}

/**
 * Type of todo item, matching iOS TodoItemType.
 */
enum class TodoItemType {
    /** Full curriculum to study. */
    CURRICULUM,

    /** Module within a curriculum. */
    MODULE,

    /** Specific topic. */
    TOPIC,

    /** User-defined learning goal. */
    LEARNING_TARGET,

    /** Captured during voice session for review. */
    REINFORCEMENT,

    /** Mid-session resume point. */
    AUTO_RESUME,
    ;

    /**
     * Whether this type links to curriculum content.
     */
    val isLinkedToCurriculum: Boolean
        get() =
            when (this) {
                CURRICULUM, MODULE, TOPIC, AUTO_RESUME -> true
                LEARNING_TARGET, REINFORCEMENT -> false
            }
}

/**
 * How a todo item was created, matching iOS TodoItemSource.
 */
enum class TodoItemSource {
    /** User manually added via UI. */
    MANUAL,

    /** Created via voice command/LLM tool call. */
    VOICE,

    /** Auto-created when stopping mid-session. */
    AUTO_RESUME,

    /** Captured during voice session as review item. */
    REINFORCEMENT,
}
