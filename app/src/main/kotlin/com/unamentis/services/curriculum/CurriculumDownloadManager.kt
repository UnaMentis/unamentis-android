package com.unamentis.services.curriculum

import android.util.Log
import com.unamentis.data.model.Curriculum
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.repository.CurriculumRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Represents the download state of a curriculum or topic.
 */
sealed class DownloadState {
    /** Download has not been started. */
    data object NotStarted : DownloadState()

    /** Download is queued, waiting to start. */
    data object Queued : DownloadState()

    /** Download is actively in progress with a fractional progress value. */
    data class Downloading(val progress: Double) : DownloadState()

    /** Download completed successfully. */
    data object Completed : DownloadState()

    /** Download failed with an error message. */
    data class Failed(val message: String) : DownloadState()

    /** Download was paused by the user. */
    data object Paused : DownloadState()

    /** Whether the download is currently active (queued or downloading). */
    val isActive: Boolean
        get() = this is Queued || this is Downloading
}

/**
 * Information about a downloadable topic within a curriculum.
 *
 * @property id Unique topic identifier
 * @property title Human-readable topic title
 * @property description Brief topic description
 * @property orderIndex Sequential position in the curriculum
 * @property estimatedSize Estimated download size in bytes
 * @property hasAssets Whether the topic has visual assets
 * @property segmentCount Number of transcript segments
 * @property isSelected Whether the topic is selected for download
 * @property downloadState Current download state of this topic
 */
data class DownloadableTopicInfo(
    val id: String,
    val title: String,
    val description: String,
    val orderIndex: Int,
    val estimatedSize: Long = 0,
    val hasAssets: Boolean = false,
    val segmentCount: Int = 0,
    val isSelected: Boolean = true,
    val downloadState: DownloadState = DownloadState.NotStarted,
)

/**
 * Overall download progress for a curriculum.
 *
 * @property curriculumId Unique curriculum identifier
 * @property curriculumTitle Human-readable curriculum title
 * @property overallProgress Fractional progress from 0.0 to 1.0
 * @property topicsCompleted Number of topics downloaded so far
 * @property topicsTotal Total number of topics to download
 * @property bytesDownloaded Bytes downloaded so far
 * @property bytesTotal Total bytes expected
 * @property state Current download state
 * @property currentTopicTitle Title of the topic currently being downloaded
 * @property topics Per-topic download info
 */
data class CurriculumDownloadProgress(
    val curriculumId: String,
    val curriculumTitle: String,
    val overallProgress: Double = 0.0,
    val topicsCompleted: Int = 0,
    val topicsTotal: Int = 0,
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long = 0,
    val state: DownloadState = DownloadState.NotStarted,
    val currentTopicTitle: String? = null,
    val topics: List<DownloadableTopicInfo> = emptyList(),
)

/**
 * Manages curriculum downloads with progress tracking, cancellation, and retry support.
 *
 * Ported from iOS CurriculumDownloadManager. Provides:
 * - Topic-level granularity for selective downloads
 * - Real-time progress tracking via [StateFlow]
 * - Cancellation of active downloads
 * - Automatic retry with exponential backoff
 * - Asset caching during download
 *
 * @property apiClient API client for server communication
 * @property curriculumRepository Repository for persisting downloaded curricula
 * @property visualAssetCache Cache for downloaded visual assets
 */
@Singleton
class CurriculumDownloadManager
    @Inject
    constructor(
        private val apiClient: ApiClient,
        private val curriculumRepository: CurriculumRepository,
        private val visualAssetCache: VisualAssetCache,
    ) {
        companion object {
            private const val TAG = "CurriculumDownloadMgr"
            private const val MAX_RETRIES = 3
            private const val INITIAL_RETRY_DELAY_MS = 1000L
            private const val MAX_RETRY_DELAY_MS = 10000L
            private const val COMPLETED_DISPLAY_DELAY_MS = 2000L
        }

        private val _activeDownloads =
            MutableStateFlow<Map<String, CurriculumDownloadProgress>>(emptyMap())

        /** Observable map of active download progress keyed by curriculum ID. */
        val activeDownloads: StateFlow<Map<String, CurriculumDownloadProgress>> =
            _activeDownloads.asStateFlow()

        private val activeJobs = mutableMapOf<String, Job>()

        /**
         * Get current download progress for a specific curriculum.
         *
         * @param curriculumId The curriculum ID to query
         * @return Current progress or null if no active download
         */
        fun getProgress(curriculumId: String): CurriculumDownloadProgress? =
            _activeDownloads.value[curriculumId]

        /**
         * Check whether a download is currently active for the given curriculum.
         *
         * @param curriculumId The curriculum ID to check
         * @return true if the curriculum is being downloaded
         */
        fun isDownloading(curriculumId: String): Boolean =
            _activeDownloads.value[curriculumId]?.state?.isActive == true

        /**
         * Cancel an active download for the given curriculum.
         *
         * @param curriculumId The curriculum ID whose download should be cancelled
         */
        fun cancelDownload(curriculumId: String) {
            activeJobs[curriculumId]?.cancel()
            activeJobs.remove(curriculumId)

            _activeDownloads.update { map ->
                map - curriculumId
            }

            Log.i(TAG, "Cancelled download for curriculum: $curriculumId")
        }

        /**
         * Download selected topics from a curriculum with progress tracking.
         *
         * Fetches the full curriculum content from the server, filters to the selected
         * topics, caches visual assets, and saves the result to local storage.
         *
         * @param curriculumId Unique ID of the curriculum to download
         * @param curriculumTitle Human-readable title for progress display
         * @param selectedTopicIds Set of topic IDs to include; empty means all topics
         * @return The downloaded and saved [Curriculum]
         * @throws CurriculumDownloadException if the download fails after retries
         */
        suspend fun downloadCurriculum(
            curriculumId: String,
            curriculumTitle: String,
            selectedTopicIds: Set<String> = emptySet(),
        ): Curriculum =
            coroutineScope {
                Log.i(
                    TAG,
                    "Starting download for curriculum: $curriculumTitle " +
                        "with ${selectedTopicIds.size} selected topics",
                )

                // Initialize progress
                updateProgress(
                    CurriculumDownloadProgress(
                        curriculumId = curriculumId,
                        curriculumTitle = curriculumTitle,
                        topicsTotal = selectedTopicIds.size.coerceAtLeast(1),
                        state = DownloadState.Downloading(0.0),
                    ),
                )

                try {
                    val curriculum =
                        executeWithRetry(curriculumId) {
                            performDownload(curriculumId, curriculumTitle, selectedTopicIds)
                        }

                    // Mark complete
                    val totalTopics =
                        if (selectedTopicIds.isEmpty()) {
                            curriculum.topics.size
                        } else {
                            selectedTopicIds.size
                        }
                    updateProgress(
                        getOrCreateProgress(curriculumId, curriculumTitle).copy(
                            overallProgress = 1.0,
                            topicsCompleted = totalTopics,
                            state = DownloadState.Completed,
                            currentTopicTitle = null,
                        ),
                    )

                    // Remove from active downloads after a short display delay
                    delay(COMPLETED_DISPLAY_DELAY_MS)
                    _activeDownloads.update { map -> map - curriculumId }

                    Log.i(TAG, "Successfully downloaded curriculum: $curriculumTitle")
                    curriculum
                } catch (e: CancellationException) {
                    _activeDownloads.update { map -> map - curriculumId }
                    throw e
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unknown error"
                    updateProgress(
                        getOrCreateProgress(curriculumId, curriculumTitle).copy(
                            state = DownloadState.Failed(errorMessage),
                        ),
                    )
                    Log.e(TAG, "Download failed for curriculum: $curriculumTitle", e)
                    throw CurriculumDownloadException(
                        "Failed to download curriculum: $errorMessage",
                        e,
                    )
                }
            }

        /**
         * Perform the actual download, fetch, parse, cache, and save operations.
         */
        private suspend fun performDownload(
            curriculumId: String,
            curriculumTitle: String,
            selectedTopicIds: Set<String>,
        ): Curriculum {
            // Phase 1: Fetch from server (0.0 - 0.5)
            updateProgress(
                getOrCreateProgress(curriculumId, curriculumTitle).copy(
                    overallProgress = 0.1,
                    state = DownloadState.Downloading(0.1),
                ),
            )

            val curriculum =
                apiClient.getCurriculumFullWithAssets(curriculumId, selectedTopicIds)

            updateProgress(
                getOrCreateProgress(curriculumId, curriculumTitle).copy(
                    overallProgress = 0.5,
                    state = DownloadState.Downloading(0.5),
                ),
            )

            // Phase 2: Cache visual assets (0.5 - 0.8)
            val totalAssets = curriculum.topics.sumOf { it.visualAssets.size }
            var cachedAssets = 0

            for (topic in curriculum.topics) {
                for (asset in topic.visualAssets) {
                    val url = asset.url
                    if (url != null) {
                        try {
                            visualAssetCache.downloadAndCache(asset.id, url)
                            cachedAssets++

                            val assetProgress =
                                0.5 + (0.3 * cachedAssets.toDouble() / totalAssets.coerceAtLeast(1))
                            updateProgress(
                                getOrCreateProgress(curriculumId, curriculumTitle).copy(
                                    overallProgress = assetProgress,
                                    state = DownloadState.Downloading(assetProgress),
                                    currentTopicTitle = topic.title,
                                ),
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache asset ${asset.id}: ${e.message}")
                            // Continue - don't fail the download for individual asset failures
                        }
                    }
                }
            }

            // Phase 3: Save to local storage (0.8 - 1.0)
            updateProgress(
                getOrCreateProgress(curriculumId, curriculumTitle).copy(
                    overallProgress = 0.85,
                    state = DownloadState.Downloading(0.85),
                ),
            )

            curriculumRepository.saveCurriculum(curriculum)

            updateProgress(
                getOrCreateProgress(curriculumId, curriculumTitle).copy(
                    overallProgress = 0.95,
                    state = DownloadState.Downloading(0.95),
                ),
            )

            Log.i(
                TAG,
                "Downloaded ${curriculum.title} with " +
                    "${curriculum.topics.size} topics, $cachedAssets/$totalAssets assets cached",
            )

            return curriculum
        }

        /**
         * Execute an operation with exponential backoff retry.
         */
        private suspend fun <T> executeWithRetry(
            curriculumId: String,
            block: suspend () -> T,
        ): T {
            var lastException: Exception? = null
            var delayMs = INITIAL_RETRY_DELAY_MS

            repeat(MAX_RETRIES) { attempt ->
                try {
                    return block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < MAX_RETRIES - 1) {
                        Log.w(
                            TAG,
                            "Download attempt ${attempt + 1}/$MAX_RETRIES failed for " +
                                "$curriculumId, retrying in ${delayMs}ms: ${e.message}",
                        )
                        delay(delayMs)
                        delayMs = min(delayMs * 2, MAX_RETRY_DELAY_MS)
                    }
                }
            }

            throw lastException ?: CurriculumDownloadException("Max retries exceeded")
        }

        private fun updateProgress(progress: CurriculumDownloadProgress) {
            _activeDownloads.update { map ->
                map + (progress.curriculumId to progress)
            }
        }

        private fun getOrCreateProgress(
            curriculumId: String,
            curriculumTitle: String,
        ): CurriculumDownloadProgress =
            _activeDownloads.value[curriculumId]
                ?: CurriculumDownloadProgress(
                    curriculumId = curriculumId,
                    curriculumTitle = curriculumTitle,
                )
    }

/**
 * Exception thrown when a curriculum download fails.
 *
 * @property message Description of the failure
 * @property cause Optional underlying exception
 */
class CurriculumDownloadException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)
