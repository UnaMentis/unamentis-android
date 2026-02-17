package com.unamentis.core.todo

import android.util.Log
import com.unamentis.data.local.dao.TodoDao
import com.unamentis.data.model.Todo
import com.unamentis.data.model.TodoItemSource
import com.unamentis.data.model.TodoItemType
import com.unamentis.data.model.TodoStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages CRUD operations, ordering, and archival for to-do items.
 *
 * This is the central manager for all todo operations, matching the iOS
 * TodoManager actor. It delegates persistence to [TodoDao] and provides
 * business logic for:
 * - Creating different types of todo items (manual, curriculum, auto-resume, reinforcement)
 * - Filtering and querying by status and type
 * - Drag-drop reordering
 * - Archival and restoration
 * - Auto-resume item management
 * - Curriculum suggestion integration
 *
 * @property todoDao Data access object for todo persistence
 * @property curriculumSuggestionService Service for fetching curriculum suggestions
 */
@Suppress("TooManyFunctions")
@Singleton
class TodoManager
    @Inject
    constructor(
        private val todoDao: TodoDao,
        private val curriculumSuggestionService: CurriculumSuggestionService,
    ) {
        init {
            // Wire up the callback so CurriculumSuggestionService can update todo items
            // without holding a direct reference to TodoManager (avoids circular dependency)
            curriculumSuggestionService.internalTodoUpdater = { id, curriculumIds ->
                updateSuggestedCurricula(id, curriculumIds)
            }
        }

        // region Create Operations

        /**
         * Create a new to-do item.
         *
         * @param title Title of the item
         * @param type Type of to-do item
         * @param source How the item was created
         * @param notes Optional notes
         * @return Created [Todo]
         */
        suspend fun createItem(
            title: String,
            type: TodoItemType = TodoItemType.LEARNING_TARGET,
            source: TodoItemSource = TodoItemSource.MANUAL,
            notes: String? = null,
        ): Todo {
            val maxSortOrder = todoDao.getMaxSortOrder() ?: -1

            val todo =
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    itemType = type,
                    source = source,
                    notes = notes,
                    sortOrder = maxSortOrder + 1,
                )

            todoDao.insert(todo)
            Log.d(TAG, "Created to-do item: $title [${type.name}]")

            // For learning targets, fetch curriculum suggestions asynchronously
            if (type == TodoItemType.LEARNING_TARGET) {
                curriculumSuggestionService.updateTodoWithSuggestions(todo)
            }

            return todo
        }

        /**
         * Create a curriculum-linked to-do item.
         *
         * @param title Title of the item
         * @param curriculumId ID of the linked curriculum
         * @param topicId Optional topic ID for topic-level items
         * @param granularity Granularity level (curriculum, module, topic)
         * @param source How the item was created
         * @return Created [Todo]
         */
        suspend fun createCurriculumItem(
            title: String,
            curriculumId: String,
            topicId: String? = null,
            granularity: String = "curriculum",
            source: TodoItemSource = TodoItemSource.MANUAL,
        ): Todo {
            val maxSortOrder = todoDao.getMaxSortOrder() ?: -1

            val itemType =
                when (granularity) {
                    "curriculum" -> TodoItemType.CURRICULUM
                    "module" -> TodoItemType.MODULE
                    "topic" -> TodoItemType.TOPIC
                    else -> TodoItemType.CURRICULUM
                }

            val todo =
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    itemType = itemType,
                    source = source,
                    curriculumId = curriculumId,
                    topicId = topicId,
                    granularity = granularity,
                    sortOrder = maxSortOrder + 1,
                )

            todoDao.insert(todo)
            Log.d(TAG, "Created curriculum to-do: $title [$granularity]")

            return todo
        }

        /**
         * Create an auto-resume to-do item.
         *
         * If an auto-resume item already exists for this topic, it will be updated.
         * Auto-resume items are placed at the top of the list (sortOrder = 0)
         * and existing items are shifted down.
         *
         * @param title Title (typically "Continue: [Topic Name]")
         * @param topicId ID of the topic to resume
         * @param segmentIndex Segment index to resume from
         * @param conversationContext Serialized conversation history for context
         * @return Created or updated [Todo]
         */
        suspend fun createAutoResumeItem(
            title: String,
            topicId: String,
            segmentIndex: Int,
            conversationContext: String?,
        ): Todo {
            // Check if we already have an auto-resume for this topic
            val existing = todoDao.getAutoResumeForTopic(topicId)
            if (existing != null) {
                val updated =
                    existing.copy(
                        resumeSegmentIndex = segmentIndex,
                        resumeConversationContext = conversationContext,
                        updatedAt = System.currentTimeMillis(),
                    )
                todoDao.update(updated)
                Log.d(TAG, "Updated existing auto-resume for topic: $topicId")
                return updated
            }

            // Shift existing items down before inserting at position 0
            todoDao.shiftSortOrdersDown(0)

            val todo =
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    itemType = TodoItemType.AUTO_RESUME,
                    source = TodoItemSource.AUTO_RESUME,
                    resumeTopicId = topicId,
                    resumeSegmentIndex = segmentIndex,
                    resumeConversationContext = conversationContext,
                    sortOrder = 0,
                )

            todoDao.insert(todo)
            Log.i(TAG, "Created auto-resume to-do for topic: $topicId at segment $segmentIndex")

            return todo
        }

        /**
         * Create a reinforcement/review to-do item.
         *
         * @param title Title describing what to review
         * @param notes Additional context
         * @param sessionId ID of the session where this was captured
         * @return Created [Todo]
         */
        suspend fun createReinforcementItem(
            title: String,
            notes: String? = null,
            sessionId: String? = null,
        ): Todo {
            val maxSortOrder = todoDao.getMaxSortOrder() ?: -1

            val todo =
                Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    itemType = TodoItemType.REINFORCEMENT,
                    source = TodoItemSource.REINFORCEMENT,
                    notes = notes,
                    sessionId = sessionId,
                    sortOrder = maxSortOrder + 1,
                )

            todoDao.insert(todo)
            Log.d(TAG, "Created reinforcement to-do: $title")

            return todo
        }

        // endregion

        // region Read Operations

        /**
         * Observe all active (non-archived, non-completed) to-do items sorted by sortOrder.
         *
         * @return Flow of active [Todo] items
         */
        fun observeActiveItems(): Flow<List<Todo>> {
            return todoDao.getActiveTodos()
        }

        /**
         * Fetch all active items as a one-time list.
         *
         * @return List of active [Todo] items sorted by sortOrder
         */
        suspend fun fetchActiveItems(): List<Todo> {
            return todoDao.getActiveTodosList()
        }

        /**
         * Observe completed to-do items.
         *
         * @return Flow of completed [Todo] items sorted by update date
         */
        fun observeCompletedItems(): Flow<List<Todo>> {
            return todoDao.getCompletedTodos()
        }

        /**
         * Observe archived to-do items.
         *
         * @return Flow of archived [Todo] items sorted by archive date
         */
        fun observeArchivedItems(): Flow<List<Todo>> {
            return todoDao.getArchivedTodos()
        }

        /**
         * Observe items of a specific type.
         *
         * @param type Type of items to observe
         * @return Flow of [Todo] items of the specified type
         */
        fun observeItems(ofType: TodoItemType): Flow<List<Todo>> {
            return todoDao.getTodosByType(ofType.name)
        }

        /**
         * Fetch a specific item by ID.
         *
         * @param id ID of the item
         * @return [Todo] or null if not found
         */
        suspend fun fetchItem(id: String): Todo? {
            return todoDao.getById(id)
        }

        /**
         * Observe all todos (unfiltered).
         *
         * @return Flow of all [Todo] items
         */
        fun observeAllItems(): Flow<List<Todo>> {
            return todoDao.getAllTodos()
        }

        // endregion

        // region Update Operations

        /**
         * Update item status.
         *
         * @param id ID of the item to update
         * @param status New status
         */
        suspend fun updateStatus(
            id: String,
            status: TodoStatus,
        ) {
            val todo = todoDao.getById(id) ?: return
            val now = System.currentTimeMillis()
            val updated =
                todo.copy(
                    status = status,
                    updatedAt = now,
                    completedAt = if (status == TodoStatus.COMPLETED) now else todo.completedAt,
                    archivedAt = if (status == TodoStatus.ARCHIVED) now else todo.archivedAt,
                )
            todoDao.update(updated)
            Log.d(TAG, "Updated status for '${todo.title}' to ${status.name}")
        }

        /**
         * Update item title and notes.
         *
         * @param id ID of the item to update
         * @param title New title (null to keep existing)
         * @param notes New notes
         */
        suspend fun updateItem(
            id: String,
            title: String? = null,
            notes: String? = null,
        ) {
            val todo = todoDao.getById(id) ?: return
            val updated =
                todo.copy(
                    title = title ?: todo.title,
                    notes = notes,
                    updatedAt = System.currentTimeMillis(),
                )
            todoDao.update(updated)
            Log.d(TAG, "Updated to-do item: ${updated.title}")
        }

        /**
         * Mark item as completed.
         *
         * @param id ID of the item to complete
         */
        suspend fun completeItem(id: String) {
            updateStatus(id, TodoStatus.COMPLETED)
        }

        /**
         * Archive an item.
         *
         * @param id ID of the item to archive
         */
        suspend fun archiveItem(id: String) {
            updateStatus(id, TodoStatus.ARCHIVED)
        }

        /**
         * Restore an archived item to active status.
         *
         * @param id ID of the item to restore
         */
        suspend fun restoreItem(id: String) {
            val todo = todoDao.getById(id) ?: return
            val updated =
                todo.copy(
                    status = TodoStatus.ACTIVE,
                    archivedAt = null,
                    updatedAt = System.currentTimeMillis(),
                )
            todoDao.update(updated)
            Log.d(TAG, "Restored to-do item: ${todo.title}")
        }

        // endregion

        // region Reordering Operations

        /**
         * Move an item to a new position (for drag-drop reordering).
         *
         * @param id ID of the item to move
         * @param newIndex New position index
         */
        suspend fun moveItem(
            id: String,
            newIndex: Int,
        ) {
            val items = todoDao.getActiveTodosList()
            val item = items.find { it.id == id } ?: return

            if (newIndex < 0 || newIndex >= items.size) {
                Log.w(TAG, "Invalid move index: $newIndex")
                return
            }

            val oldSortOrder = item.sortOrder
            val newSortOrder = newIndex

            if (oldSortOrder < newSortOrder) {
                // Moving down: shift items between old and new position up
                for (otherItem in items) {
                    if (otherItem.id != id &&
                        otherItem.sortOrder > oldSortOrder &&
                        otherItem.sortOrder <= newSortOrder
                    ) {
                        todoDao.update(otherItem.copy(sortOrder = otherItem.sortOrder - 1))
                    }
                }
            } else if (oldSortOrder > newSortOrder) {
                // Moving up: shift items between new and old position down
                for (otherItem in items) {
                    if (otherItem.id != id &&
                        otherItem.sortOrder >= newSortOrder &&
                        otherItem.sortOrder < oldSortOrder
                    ) {
                        todoDao.update(otherItem.copy(sortOrder = otherItem.sortOrder + 1))
                    }
                }
            }

            todoDao.update(
                item.copy(
                    sortOrder = newSortOrder,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            Log.d(TAG, "Moved '${item.title}' to position $newIndex")
        }

        /**
         * Reorder items based on an ordered list of IDs.
         *
         * @param orderedIds List of item IDs in desired order
         */
        suspend fun reorderItems(orderedIds: List<String>) {
            for ((index, itemId) in orderedIds.withIndex()) {
                val todo = todoDao.getById(itemId) ?: continue
                todoDao.update(todo.copy(sortOrder = index))
            }
            Log.d(TAG, "Reordered ${orderedIds.size} items")
        }

        // endregion

        // region Delete Operations

        /**
         * Permanently delete an item.
         *
         * @param id ID of the item to delete
         */
        suspend fun deleteItem(id: String) {
            val todo = todoDao.getById(id) ?: return
            todoDao.delete(todo)
            Log.i(TAG, "Deleted to-do item: ${todo.title}")
        }

        /**
         * Delete all completed items.
         */
        suspend fun deleteAllCompleted() {
            todoDao.deleteAllCompleted()
            Log.i(TAG, "Deleted all completed items")
        }

        // endregion

        // region Auto-Resume Specific

        /**
         * Remove auto-resume item for a topic (called when session completes normally).
         *
         * @param topicId ID of the topic
         */
        suspend fun clearAutoResume(topicId: String) {
            val item = todoDao.getAutoResumeForTopic(topicId)
            if (item != null) {
                todoDao.delete(item)
                Log.d(TAG, "Cleared auto-resume for topic: $topicId")
            }
        }

        /**
         * Get resume context for a topic.
         *
         * @param topicId ID of the topic
         * @return Pair of (segmentIndex, conversationContext) or null
         */
        suspend fun getResumeContext(topicId: String): ResumeContext? {
            val item = todoDao.getAutoResumeForTopic(topicId) ?: return null
            return ResumeContext(
                segmentIndex = item.resumeSegmentIndex,
                conversationContext = item.resumeConversationContext,
            )
        }

        // endregion

        // region Learning Target Specific

        /**
         * Update suggested curricula for a learning target.
         *
         * @param id ID of the learning target item
         * @param curriculumIds JSON-encoded list of suggested curriculum IDs
         */
        suspend fun updateSuggestedCurricula(
            id: String,
            curriculumIds: String,
        ) {
            val todo = todoDao.getById(id) ?: return
            if (todo.itemType != TodoItemType.LEARNING_TARGET) {
                Log.w(TAG, "Cannot set suggested curricula for non-learning-target item")
                return
            }

            todoDao.update(
                todo.copy(
                    suggestedCurriculumIds = curriculumIds,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            Log.d(TAG, "Updated suggested curricula for '${todo.title}'")
        }

        // endregion

        companion object {
            private const val TAG = "TodoManager"
        }
    }

/**
 * Resume context data for a topic.
 *
 * @property segmentIndex The segment index to resume from
 * @property conversationContext Serialized conversation context (JSON string)
 */
data class ResumeContext(
    val segmentIndex: Int,
    val conversationContext: String?,
)

/**
 * Errors that can occur during todo operations.
 */
sealed class TodoError(message: String) : Exception(message) {
    /** Item with the given ID was not found. */
    class ItemNotFound(id: String) : TodoError("To-do item not found: $id")

    /** The requested operation is invalid. */
    class InvalidOperation(message: String) : TodoError("Invalid operation: $message")

    /** Failed to save data. */
    class SaveFailed(message: String) : TodoError("Failed to save: $message")
}
