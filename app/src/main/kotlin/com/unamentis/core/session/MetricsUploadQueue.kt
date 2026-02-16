package com.unamentis.core.session

import android.util.Log
import com.unamentis.core.telemetry.LatencyStats
import com.unamentis.core.telemetry.ProviderTypeCostBreakdown
import com.unamentis.core.telemetry.TelemetryEngine
import com.unamentis.data.local.dao.QueuedMetricsDao
import com.unamentis.data.local.entity.QueuedMetricsEntity
import com.unamentis.data.model.LatencyType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializable payload representing metrics for upload to the management server.
 *
 * This structure mirrors the iOS MetricsSnapshot payload format and matches
 * the server's expected POST /api/metrics request body.
 *
 * @property timestamp ISO 8601 timestamp of when the metrics were captured
 * @property sessionDuration Duration of the session in seconds
 * @property turnsTotal Total number of conversation turns
 * @property interruptions Number of barge-in interruptions
 * @property sttLatencyMedian Median STT latency in milliseconds
 * @property sttLatencyP99 P99 STT latency in milliseconds
 * @property llmTTFTMedian Median LLM time-to-first-token in milliseconds
 * @property llmTTFTP99 P99 LLM time-to-first-token in milliseconds
 * @property ttsTTFBMedian Median TTS time-to-first-byte in milliseconds
 * @property ttsTTFBP99 P99 TTS time-to-first-byte in milliseconds
 * @property e2eLatencyMedian Median end-to-end turn latency in milliseconds
 * @property e2eLatencyP99 P99 end-to-end turn latency in milliseconds
 * @property sttCost Total STT cost in USD
 * @property ttsCost Total TTS cost in USD
 * @property llmCost Total LLM cost in USD
 * @property totalCost Total session cost in USD
 * @property thermalThrottleEvents Number of thermal throttle events
 * @property networkDegradations Number of network degradation events
 */
@Serializable
data class MetricsPayload(
    val timestamp: String,
    val sessionDuration: Double,
    val turnsTotal: Int = 0,
    val interruptions: Int = 0,
    val sttLatencyMedian: Double = 0.0,
    val sttLatencyP99: Double = 0.0,
    val llmTTFTMedian: Double = 0.0,
    val llmTTFTP99: Double = 0.0,
    val ttsTTFBMedian: Double = 0.0,
    val ttsTTFBP99: Double = 0.0,
    val e2eLatencyMedian: Double = 0.0,
    val e2eLatencyP99: Double = 0.0,
    val sttCost: Double = 0.0,
    val ttsCost: Double = 0.0,
    val llmCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val thermalThrottleEvents: Int = 0,
    val networkDegradations: Int = 0,
)

/**
 * Persistent queue for metrics that could not be uploaded to the management server.
 *
 * Uses Room database for persistence so queued items survive app restarts.
 * Supports queue management with maximum size enforcement and FIFO eviction.
 *
 * iOS Parity: Equivalent to MetricsUploadQueue.swift
 *
 * @property queuedMetricsDao Room DAO for persistence operations
 * @property telemetryEngine TelemetryEngine for reading current metrics
 * @property json JSON serializer for payload encoding/decoding
 */
@Singleton
class MetricsUploadQueue
    @Inject
    constructor(
        private val queuedMetricsDao: QueuedMetricsDao,
        private val telemetryEngine: TelemetryEngine,
        private val json: Json,
    ) {
        companion object {
            private const val TAG = "MetricsUploadQueue"

            /** Maximum number of items allowed in the queue. Oldest items are evicted when exceeded. */
            const val MAX_QUEUE_SIZE = 100

            /** Maximum number of retry attempts before an item is considered expired. */
            const val MAX_RETRIES = 5
        }

        /**
         * Enqueue metrics from the current session for later upload.
         *
         * Transforms the TelemetryEngine data into a [MetricsPayload] and persists
         * it to the Room database. If the queue exceeds [MAX_QUEUE_SIZE], the oldest
         * items are evicted.
         *
         * @param sessionId The session ID to read metrics from
         * @param sessionDurationSeconds Duration of the session in seconds
         * @param turnsTotal Total conversation turns in the session
         * @param interruptions Number of barge-in interruptions
         */
        suspend fun enqueue(
            sessionId: String,
            sessionDurationSeconds: Double,
            turnsTotal: Int = 0,
            interruptions: Int = 0,
        ) {
            val payload =
                buildPayload(
                    sessionId = sessionId,
                    sessionDurationSeconds = sessionDurationSeconds,
                    turnsTotal = turnsTotal,
                    interruptions = interruptions,
                )
            enqueuePayload(payload)
        }

        /**
         * Enqueue a pre-built metrics payload for later upload.
         *
         * @param payload The metrics payload to enqueue
         */
        suspend fun enqueuePayload(payload: MetricsPayload) {
            val entity =
                QueuedMetricsEntity(
                    id = UUID.randomUUID().toString(),
                    payloadJson = json.encodeToString(payload),
                    queuedAt = System.currentTimeMillis(),
                    retryCount = 0,
                )

            queuedMetricsDao.insert(entity)

            // Evict oldest items if over capacity
            val currentCount = queuedMetricsDao.count()
            if (currentCount > MAX_QUEUE_SIZE) {
                queuedMetricsDao.evictOldest(MAX_QUEUE_SIZE)
                Log.w(TAG, "Queue exceeded max size, trimmed to $MAX_QUEUE_SIZE")
            }

            Log.i(TAG, "Enqueued metrics, queue size: ${queuedMetricsDao.count()}")
        }

        /**
         * Get all pending items that have not exceeded the maximum retry count.
         *
         * @return List of pending items as pairs of (id, payload)
         */
        suspend fun getPending(): List<QueuedItem> {
            return queuedMetricsDao.getPending(MAX_RETRIES).map { entity ->
                QueuedItem(
                    id = entity.id,
                    payload = json.decodeFromString<MetricsPayload>(entity.payloadJson),
                    queuedAt = entity.queuedAt,
                    retryCount = entity.retryCount,
                )
            }
        }

        /**
         * Mark an item as completed (successfully uploaded) and remove from queue.
         *
         * @param id The unique identifier of the item to remove
         */
        suspend fun markCompleted(id: String) {
            queuedMetricsDao.deleteById(id)
        }

        /**
         * Increment the retry count for a failed upload attempt.
         *
         * @param id The unique identifier of the item
         */
        suspend fun incrementRetry(id: String) {
            queuedMetricsDao.incrementRetryCount(id)
        }

        /**
         * Get the current queue size.
         *
         * @return Number of items in the queue
         */
        suspend fun count(): Int {
            return queuedMetricsDao.count()
        }

        /**
         * Clear all items from the queue.
         */
        suspend fun clear() {
            queuedMetricsDao.deleteAll()
        }

        /**
         * Build a [MetricsPayload] from current TelemetryEngine data.
         *
         * Reads latency statistics and cost breakdowns from the TelemetryEngine
         * for the given session and transforms them into the server's expected format.
         *
         * @param sessionId The session to read metrics from
         * @param sessionDurationSeconds Duration of the session in seconds
         * @param turnsTotal Total conversation turns
         * @param interruptions Number of barge-in interruptions
         * @return Metrics payload ready for serialization
         */
        internal fun buildPayload(
            sessionId: String,
            sessionDurationSeconds: Double,
            turnsTotal: Int = 0,
            interruptions: Int = 0,
        ): MetricsPayload {
            val sttStats: LatencyStats =
                telemetryEngine.getLatencyStats(sessionId, LatencyType.STT)
            val llmStats: LatencyStats =
                telemetryEngine.getLatencyStats(sessionId, LatencyType.LLM_TTFT)
            val ttsStats: LatencyStats =
                telemetryEngine.getLatencyStats(sessionId, LatencyType.TTS_TTFB)
            val e2eStats: LatencyStats =
                telemetryEngine.getLatencyStats(sessionId, LatencyType.E2E_TURN)
            val costBreakdown: ProviderTypeCostBreakdown =
                telemetryEngine.getCostBreakdownByType(sessionId)

            return MetricsPayload(
                timestamp = formatIso8601(Date()),
                sessionDuration = sessionDurationSeconds,
                turnsTotal = turnsTotal,
                interruptions = interruptions,
                sttLatencyMedian = sttStats.median.toDouble(),
                sttLatencyP99 = sttStats.p99.toDouble(),
                llmTTFTMedian = llmStats.median.toDouble(),
                llmTTFTP99 = llmStats.p99.toDouble(),
                ttsTTFBMedian = ttsStats.median.toDouble(),
                ttsTTFBP99 = ttsStats.p99.toDouble(),
                e2eLatencyMedian = e2eStats.median.toDouble(),
                e2eLatencyP99 = e2eStats.p99.toDouble(),
                sttCost = costBreakdown.sttCost,
                ttsCost = costBreakdown.ttsCost,
                llmCost = costBreakdown.llmCost,
                totalCost = costBreakdown.totalCost,
            )
        }

        /**
         * Format a [Date] as ISO 8601 string.
         */
        private fun formatIso8601(date: Date): String {
            val formatter =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            return formatter.format(date)
        }
    }

/**
 * Represents a queued metrics item with its metadata.
 *
 * @property id Unique identifier of the queued item
 * @property payload The deserialized metrics payload
 * @property queuedAt Unix timestamp of when the item was queued
 * @property retryCount Number of upload attempts made so far
 */
data class QueuedItem(
    val id: String,
    val payload: MetricsPayload,
    val queuedAt: Long,
    val retryCount: Int,
)
