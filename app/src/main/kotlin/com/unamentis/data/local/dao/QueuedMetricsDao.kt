package com.unamentis.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unamentis.data.local.entity.QueuedMetricsEntity

/**
 * DAO for managing queued metrics in the Room database.
 *
 * Provides persistence for metrics that could not be uploaded
 * to the management server. Supports queue operations including
 * insertion, retrieval, retry tracking, and eviction.
 */
@Dao
interface QueuedMetricsDao {
    /**
     * Insert a metrics item into the queue.
     *
     * Uses REPLACE strategy to handle duplicate IDs gracefully.
     *
     * @param entity The queued metrics entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QueuedMetricsEntity)

    /**
     * Get all pending items that have not exceeded the maximum retry count.
     *
     * Items are ordered by queued time (oldest first) to ensure FIFO processing.
     *
     * @param maxRetries Maximum allowed retry attempts
     * @return List of pending queued metrics, ordered oldest first
     */
    @Query("SELECT * FROM queued_metrics WHERE retryCount < :maxRetries ORDER BY queuedAt ASC")
    suspend fun getPending(maxRetries: Int): List<QueuedMetricsEntity>

    /**
     * Get all items in the queue regardless of retry count.
     *
     * @return List of all queued metrics, ordered oldest first
     */
    @Query("SELECT * FROM queued_metrics ORDER BY queuedAt ASC")
    suspend fun getAll(): List<QueuedMetricsEntity>

    /**
     * Delete a specific item from the queue after successful upload.
     *
     * @param id The unique identifier of the item to remove
     */
    @Query("DELETE FROM queued_metrics WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Increment the retry count for a specific item.
     *
     * Called when an upload attempt fails, to track how many
     * times the item has been retried.
     *
     * @param id The unique identifier of the item
     */
    @Query("UPDATE queued_metrics SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: String)

    /**
     * Get the total number of items in the queue.
     *
     * @return Count of all queued metrics
     */
    @Query("SELECT COUNT(*) FROM queued_metrics")
    suspend fun count(): Int

    /**
     * Delete all items from the queue.
     */
    @Query("DELETE FROM queued_metrics")
    suspend fun deleteAll()

    /**
     * Delete the oldest items to keep the queue under a maximum size.
     *
     * Evicts the oldest items first (FIFO eviction) to maintain the
     * queue size constraint.
     *
     * @param keepCount The maximum number of items to retain
     */
    @Query(
        """
        DELETE FROM queued_metrics WHERE id IN (
            SELECT id FROM queued_metrics ORDER BY queuedAt ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM queued_metrics) - :keepCount)
        )
        """,
    )
    suspend fun evictOldest(keepCount: Int)
}
