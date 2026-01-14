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
     * Get all todos as a Flow.
     */
    @Query("SELECT * FROM todos ORDER BY createdAt DESC")
    fun getAllTodos(): Flow<List<Todo>>

    /**
     * Get todo by ID.
     */
    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): Todo?

    /**
     * Insert todo.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: Todo)

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
     * Delete all todos.
     */
    @Query("DELETE FROM todos")
    suspend fun deleteAll()
}
