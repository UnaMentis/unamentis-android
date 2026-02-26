package com.unamentis.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing queued metrics that failed to upload.
 *
 * Metrics are persisted to survive app restarts, ensuring no telemetry
 * data is lost when the management server is temporarily unavailable.
 *
 * @property id Unique identifier for the queued item
 * @property payloadJson JSON-serialized metrics payload
 * @property queuedAt Unix timestamp of when the item was queued
 * @property retryCount Number of upload attempts made so far
 */
@Entity(tableName = "queued_metrics")
data class QueuedMetricsEntity(
    @PrimaryKey
    val id: String,
    val payloadJson: String,
    val queuedAt: Long,
    val retryCount: Int = 0,
)
