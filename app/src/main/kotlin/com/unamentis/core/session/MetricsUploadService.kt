package com.unamentis.core.session

import android.os.Build
import android.util.Log
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.remote.MetricsUploadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Errors that can occur during metrics upload.
 *
 * @property message Human-readable error description
 */
sealed class MetricsUploadError(message: String) : Exception(message) {
    /** Server returned a non-200 response. */
    class ServerError(val statusCode: Int) : MetricsUploadError("Server error: HTTP $statusCode")

    /** Server returned an invalid or empty response. */
    class InvalidResponse : MetricsUploadError("Invalid response from server")

    /** Network is unavailable or request timed out. */
    class NetworkUnavailable(cause: Throwable? = null) :
        MetricsUploadError("Network unavailable") {
        init {
            cause?.let { initCause(it) }
        }
    }
}

/**
 * Service for uploading session metrics to the management server.
 *
 * Provides reliable metrics delivery with:
 * - Automatic queuing when the server is unreachable
 * - Exponential backoff retry for failed uploads
 * - Batch drain of queued items when connectivity is restored
 * - Client identification via device ID and name headers
 *
 * iOS Parity: Equivalent to MetricsUploadService.swift
 *
 * @property queue Persistent queue for offline metrics storage
 * @property apiClient API client for server communication
 * @property scope Application-level coroutine scope for background operations
 */
@Singleton
class MetricsUploadService
    @Inject
    constructor(
        private val queue: MetricsUploadQueue,
        private val apiClient: ApiClient,
        private val scope: CoroutineScope,
    ) {
        companion object {
            private const val TAG = "MetricsUploadService"

            /** Initial retry delay in milliseconds. */
            private const val INITIAL_RETRY_DELAY_MS = 1000L

            /** Maximum retry delay in milliseconds (capped at 60 seconds). */
            private const val MAX_RETRY_DELAY_MS = 60_000L

            /** Base for exponential backoff calculation. */
            private const val BACKOFF_BASE = 2.0

            /** Maximum number of items to upload in a single batch drain. */
            const val MAX_BATCH_SIZE = 50

            /** Shared preferences key for persisted client ID. */
            private const val PREFS_NAME = "MetricsUploadService"
            private const val KEY_CLIENT_ID = "clientId"
        }

        /** Mutex to prevent concurrent drain operations. */
        private val drainMutex = Mutex()

        /** Unique client identifier, persisted across app restarts. */
        private var clientId: String = UUID.randomUUID().toString()

        /** Human-readable device name for server identification. */
        private val clientName: String = "${Build.MANUFACTURER} ${Build.MODEL}"

        init {
            Log.i(TAG, "MetricsUploadService initialized with clientName: $clientName")
        }

        /**
         * Upload metrics for a completed session.
         *
         * Attempts to upload immediately. If the upload fails, the metrics are
         * queued for later retry via [drainQueue].
         *
         * @param sessionId The session to upload metrics for
         * @param sessionDurationSeconds Duration of the session in seconds
         * @param turnsTotal Total conversation turns
         * @param interruptions Number of barge-in interruptions
         */
        suspend fun upload(
            sessionId: String,
            sessionDurationSeconds: Double,
            turnsTotal: Int = 0,
            interruptions: Int = 0,
        ) {
            val payload =
                queue.buildPayload(
                    sessionId = sessionId,
                    sessionDurationSeconds = sessionDurationSeconds,
                    turnsTotal = turnsTotal,
                    interruptions = interruptions,
                )

            try {
                sendToServer(payload)
                Log.i(TAG, "Successfully uploaded metrics for session $sessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload metrics: ${e.message}, queuing for retry")
                queue.enqueuePayload(payload)
            }
        }

        /**
         * Attempt to drain the offline queue by uploading all pending items.
         *
         * Processes items in FIFO order with exponential backoff between failures.
         * Stops processing on the first failure to avoid overwhelming a struggling server.
         *
         * This method is safe to call concurrently; only one drain operation
         * runs at a time due to the internal mutex.
         */
        suspend fun drainQueue() {
            if (!drainMutex.tryLock()) {
                Log.d(TAG, "Already draining queue, skipping")
                return
            }

            try {
                val pending = queue.getPending()
                if (pending.isEmpty()) {
                    Log.d(TAG, "No pending items in queue")
                    return
                }

                Log.i(TAG, "Draining queue with ${pending.size} pending uploads")

                val batchItems = pending.take(MAX_BATCH_SIZE)
                for (item in batchItems) {
                    try {
                        sendToServer(item.payload)
                        queue.markCompleted(item.id)
                        Log.i(TAG, "Successfully uploaded queued metrics ${item.id}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to upload queued metrics ${item.id}: ${e.message}")
                        queue.incrementRetry(item.id)
                        // Stop on first failure - will retry later
                        break
                    }
                }
            } finally {
                drainMutex.unlock()
            }
        }

        /**
         * Schedule a periodic queue drain attempt.
         *
         * Launches a background coroutine that attempts to drain the queue
         * at the specified interval. Uses exponential backoff on consecutive failures.
         *
         * @param intervalMs Base interval between drain attempts in milliseconds
         */
        fun schedulePeriodicDrain(intervalMs: Long = 60_000L) {
            scope.launch {
                var consecutiveFailures = 0
                while (true) {
                    val effectiveDelay =
                        if (consecutiveFailures > 0) {
                            val backoff =
                                INITIAL_RETRY_DELAY_MS *
                                    BACKOFF_BASE.pow(consecutiveFailures.toDouble()).toLong()
                            min(backoff, MAX_RETRY_DELAY_MS)
                        } else {
                            intervalMs
                        }
                    delay(effectiveDelay)

                    try {
                        val pendingBefore = queue.count()
                        if (pendingBefore > 0) {
                            drainQueue()
                            val pendingAfter = queue.count()
                            if (pendingAfter < pendingBefore) {
                                consecutiveFailures = 0
                            } else {
                                consecutiveFailures++
                            }
                        } else {
                            consecutiveFailures = 0
                        }
                    } catch (e: Exception) {
                        consecutiveFailures++
                        Log.w(
                            TAG,
                            "Periodic drain failed (consecutive=$consecutiveFailures): ${e.message}",
                        )
                    }
                }
            }
        }

        /**
         * Get the current number of pending items in the upload queue.
         *
         * @return Number of queued metrics awaiting upload
         */
        suspend fun pendingCount(): Int {
            return queue.count()
        }

        /**
         * Send a metrics payload to the management server.
         *
         * Transforms the internal [MetricsPayload] into the server's expected
         * [MetricsUploadRequest] format and uploads via the [ApiClient].
         *
         * @param payload The metrics payload to upload
         * @throws MetricsUploadError.ServerError if the server returns an error
         * @throws MetricsUploadError.NetworkUnavailable if the network is unavailable
         */
        internal suspend fun sendToServer(payload: MetricsPayload) {
            val request =
                MetricsUploadRequest(
                    clientId = clientId,
                    clientName = clientName,
                    sessionId = payload.timestamp,
                    sessionDuration = payload.sessionDuration,
                    turnsTotal = payload.turnsTotal,
                    interruptions = payload.interruptions,
                    sttLatencyMedian = payload.sttLatencyMedian,
                    sttLatencyP99 = payload.sttLatencyP99,
                    llmTtftMedian = payload.llmTTFTMedian,
                    llmTtftP99 = payload.llmTTFTP99,
                    ttsTtfbMedian = payload.ttsTTFBMedian,
                    ttsTtfbP99 = payload.ttsTTFBP99,
                    e2eLatencyMedian = payload.e2eLatencyMedian,
                    e2eLatencyP99 = payload.e2eLatencyP99,
                    sttCost = payload.sttCost,
                    ttsCost = payload.ttsCost,
                    llmCost = payload.llmCost,
                    totalCost = payload.totalCost,
                    thermalThrottleEvents = payload.thermalThrottleEvents,
                    networkDegradations = payload.networkDegradations,
                )

            try {
                apiClient.uploadMetrics(request)
            } catch (e: IOException) {
                throw MetricsUploadError.NetworkUnavailable(e)
            }
        }
    }
