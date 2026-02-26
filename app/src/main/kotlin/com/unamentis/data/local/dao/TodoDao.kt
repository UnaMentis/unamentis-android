package com.unamentis.data.local.dao

import androidx.room.*
import com.unamentis.data.model.Todo
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Todo operations.
 */
@Dao
interface TodoDao {
    /**
     * Get all todos as a Flow, ordered by due date (nulls last), then by creation date.
     */
    @Query(
        """
        SELECT * FROM todos
        ORDER BY
            CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END,
            dueDate ASC,
            createdAt DESC
        """,
    )
    fun getAllTodos(): Flow<List<Todo>>

    /**
     * Get todo by ID.
     */
    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): Todo?

    /**
     * Get all active (non-archived, non-completed) todos sorted by sortOrder.
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE status NOT IN ('ARCHIVED', 'COMPLETED')
        ORDER BY sortOrder ASC
        """,
    )
    fun getActiveTodos(): Flow<List<Todo>>

    /**
     * Get all active todos as a list (non-Flow, for one-time queries).
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE status NOT IN ('ARCHIVED', 'COMPLETED')
        ORDER BY sortOrder ASC
        """,
    )
    suspend fun getActiveTodosList(): List<Todo>

    /**
     * Get completed todos sorted by update date descending.
     */
    @Query("SELECT * FROM todos WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun getCompletedTodos(): Flow<List<Todo>>

    /**
     * Get archived todos sorted by archivedAt date descending.
     */
    @Query("SELECT * FROM todos WHERE status = 'ARCHIVED' ORDER BY archivedAt DESC")
    fun getArchivedTodos(): Flow<List<Todo>>

    /**
     * Get todos by item type, sorted by sortOrder.
     */
    @Query("SELECT * FROM todos WHERE itemType = :itemType ORDER BY sortOrder ASC")
    fun getTodosByType(itemType: String): Flow<List<Todo>>

    /**
     * Get auto-resume todo for a specific topic.
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE itemType = 'AUTO_RESUME' AND resumeTopicId = :topicId
        LIMIT 1
        """,
    )
    suspend fun getAutoResumeForTopic(topicId: String): Todo?

    /**
     * Get the maximum sortOrder value among all todos.
     */
    @Query("SELECT MAX(sortOrder) FROM todos")
    suspend fun getMaxSortOrder(): Int?

    /**
     * Increment sortOrder for all items with sortOrder >= startIndex.
     * Used when inserting a new item at a specific position.
     */
    @Query("UPDATE todos SET sortOrder = sortOrder + 1 WHERE sortOrder >= :startIndex")
    suspend fun shiftSortOrdersDown(startIndex: Int)

    /**
     * Get all AI-suggested todos.
     */
    @Query("SELECT * FROM todos WHERE isAISuggested = 1 ORDER BY suggestionConfidence DESC")
    fun getAISuggestedTodos(): Flow<List<Todo>>

    /**
     * Get todos with due dates.
     */
    @Query("SELECT * FROM todos WHERE dueDate IS NOT NULL ORDER BY dueDate ASC")
    fun getTodosWithDueDates(): Flow<List<Todo>>

    /**
     * Get overdue todos (due date is before the given timestamp).
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE dueDate IS NOT NULL AND dueDate < :timestamp AND status = 'ACTIVE'
        ORDER BY dueDate ASC
        """,
    )
    fun getOverdueTodos(timestamp: Long): Flow<List<Todo>>

    /**
     * Get all active todos with due dates (for notification worker).
     * Returns a list, not a Flow, for one-time queries.
     */
    @Query("SELECT * FROM todos WHERE dueDate IS NOT NULL AND status = 'ACTIVE' ORDER BY dueDate ASC")
    suspend fun getActiveTodosWithDueDates(): List<Todo>

    /**
     * Get todos due within the next N days.
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE dueDate IS NOT NULL
        AND dueDate >= :startTimestamp
        AND dueDate <= :endTimestamp
        AND status = 'ACTIVE'
        ORDER BY dueDate ASC
        """,
    )
    fun getTodosDueBetween(
        startTimestamp: Long,
        endTimestamp: Long,
    ): Flow<List<Todo>>

    /**
     * Get todos by status.
     */
    @Query("SELECT * FROM todos WHERE status = :status ORDER BY createdAt DESC")
    fun getTodosByStatus(status: String): Flow<List<Todo>>

    /**
     * Insert todo.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: Todo)

    /**
     * Insert multiple todos.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<Todo>)

    /**
     * Update todo.
     */
    @Update
    suspend fun update(todo: Todo)

    /**
     * Delete todo.
     */
    @Delete
    suspend fun delete(todo: Todo)

    /**
     * Delete todo by ID.
     */
    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete multiple todos by IDs.
     */
    @Query("DELETE FROM todos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Delete all completed todos.
     */
    @Query("DELETE FROM todos WHERE status = 'COMPLETED'")
    suspend fun deleteAllCompleted()

    /**
     * Delete all AI-suggested todos that were not accepted.
     */
    @Query("DELETE FROM todos WHERE isAISuggested = 1 AND status = 'ACTIVE'")
    suspend fun deleteUnacceptedSuggestions()

    /**
     * Delete all todos.
     */
    @Query("DELETE FROM todos")
    suspend fun deleteAll()

    /**
     * Update status for multiple todos.
     */
    @Query("UPDATE todos SET status = :status, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateStatusForIds(
        ids: List<String>,
        status: String,
        updatedAt: Long,
    )

    /**
     * Mark todo as completed.
     */
    @Query("UPDATE todos SET status = 'COMPLETED', completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markCompleted(
        id: String,
        completedAt: Long,
        updatedAt: Long,
    )

    /**
     * Count of all active todos.
     */
    @Query("SELECT COUNT(*) FROM todos WHERE status NOT IN ('ARCHIVED', 'COMPLETED')")
    fun getActiveCount(): Flow<Int>
}
