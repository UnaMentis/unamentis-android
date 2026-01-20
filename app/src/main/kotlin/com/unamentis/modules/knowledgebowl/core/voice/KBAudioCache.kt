package com.unamentis.modules.knowledgebowl.core.voice

import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client-side cache for pre-fetched KB TTS audio.
 *
 * Fetches and caches audio for KB questions to achieve zero-latency
 * playback during practice sessions.
 *
 * @property serverHost Server hostname
 * @property serverPort Server port (default: 8766)
 * @property moduleId Module identifier
 * @property maxCacheSize Maximum cache size in bytes (default: 50MB)
 */
@Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "NestedBlockDepth",
    "ReturnCount",
)
class KBAudioCache(
    private val serverHost: String,
    private val serverPort: Int = 8766,
    private val moduleId: String = "knowledge-bowl",
    private val maxCacheSize: Int = 50 * 1024 * 1024,
) {
    companion object {
        private const val TAG = "KBAudioCache"
        private const val DEFAULT_SAMPLE_RATE = 24000

        // Valid feedback types for audio (security: prevent path traversal)
        private val VALID_FEEDBACK_TYPES = setOf("correct", "incorrect")
    }

    // In-memory cache of audio data
    private val cache = mutableMapOf<String, KBCachedAudio>()
    private val cacheMutex = Mutex()

    // Questions currently being prefetched
    private val prefetchInProgress = mutableSetOf<String>()
    private val prefetchMutex = Mutex()

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    // Cache key generation

    private fun cacheKey(
        questionId: String,
        segment: KBSegmentType,
        hintIndex: Int = 0,
    ): String =
        if (segment == KBSegmentType.HINT) {
            "$questionId:${segment.value}:$hintIndex"
        } else {
            "$questionId:${segment.value}"
        }

    // Public API

    /**
     * Get audio for a question segment, fetching from server if not cached.
     *
     * @param questionId Question ID
     * @param segment Segment type (question, answer, hint, explanation)
     * @param hintIndex Hint index (for hint segments)
     * @return Cached audio or null if not available
     */
    suspend fun getAudio(
        questionId: String,
        segment: KBSegmentType,
        hintIndex: Int = 0,
    ): KBCachedAudio? {
        val key = cacheKey(questionId, segment, hintIndex)

        // Check cache first
        cacheMutex.withLock {
            cache[key]?.let {
                Log.d(TAG, "Cache hit: $key")
                return it
            }
        }

        // Fetch from server
        Log.d(TAG, "Cache miss, fetching: $key")
        return fetchAudio(questionId, segment, hintIndex)
    }

    /**
     * Check if audio is cached without fetching.
     */
    suspend fun hasAudio(
        questionId: String,
        segment: KBSegmentType,
        hintIndex: Int = 0,
    ): Boolean {
        val key = cacheKey(questionId, segment, hintIndex)
        return cacheMutex.withLock { cache.containsKey(key) }
    }

    /**
     * Prefetch audio for multiple questions.
     *
     * @param questionIds List of question IDs to prefetch
     * @param segments Segment types to prefetch
     */
    suspend fun prefetchQuestions(
        questionIds: List<String>,
        segments: List<KBSegmentType> =
            listOf(
                KBSegmentType.QUESTION,
                KBSegmentType.ANSWER,
                KBSegmentType.EXPLANATION,
            ),
    ) {
        for (questionId in questionIds) {
            // Skip if already being prefetched
            val shouldSkip =
                prefetchMutex.withLock {
                    if (prefetchInProgress.contains(questionId)) {
                        true
                    } else {
                        prefetchInProgress.add(questionId)
                        false
                    }
                }

            if (shouldSkip) continue

            try {
                for (segment in segments) {
                    // Skip if already cached
                    val key = cacheKey(questionId, segment)
                    val isCached = cacheMutex.withLock { cache.containsKey(key) }
                    if (isCached) continue

                    // Fetch in background
                    try {
                        fetchAudio(questionId, segment)
                    } catch (e: Exception) {
                        Log.w(TAG, "Prefetch failed for $questionId/$segment: ${e.message}")
                    }
                }
            } finally {
                prefetchMutex.withLock {
                    prefetchInProgress.remove(questionId)
                }
            }
        }
    }

    /**
     * Warm cache at session start (first N questions).
     *
     * @param questions List of questions to warm cache for
     * @param lookahead Number of questions to warm (default: 5)
     */
    suspend fun warmCache(
        questions: List<KBQuestion>,
        lookahead: Int = 5,
    ) {
        val questionsToWarm = questions.take(lookahead)
        val questionIds = questionsToWarm.map { it.id }

        Log.i(TAG, "Warming cache for ${questionIds.size} questions")
        prefetchQuestions(questionIds)
    }

    /**
     * Prefetch next questions from current position.
     *
     * @param questions Full list of questions
     * @param currentIndex Current question index
     * @param lookahead Number of questions to prefetch ahead
     */
    suspend fun prefetchUpcoming(
        questions: List<KBQuestion>,
        currentIndex: Int,
        lookahead: Int = 3,
    ) {
        val startIndex = currentIndex + 1
        val endIndex = minOf(startIndex + lookahead, questions.size)

        if (startIndex >= questions.size) return

        val upcoming = questions.subList(startIndex, endIndex)
        val questionIds = upcoming.map { it.id }

        Log.d(TAG, "Prefetching ${questionIds.size} upcoming questions")
        prefetchQuestions(questionIds)
    }

    /**
     * Clear the cache.
     */
    suspend fun clear() {
        cacheMutex.withLock {
            cache.clear()
        }
        Log.i(TAG, "Cache cleared")
    }

    /**
     * Get current cache size in bytes.
     */
    suspend fun cacheSize(): Int = cacheMutex.withLock { cache.values.sumOf { it.data.size } }

    /**
     * Get cache entry count.
     */
    suspend fun entryCount(): Int = cacheMutex.withLock { cache.size }

    /**
     * Get feedback audio (correct/incorrect).
     *
     * @param feedbackType Type of feedback ("correct" or "incorrect")
     * @return Cached audio or null if not available
     */
    suspend fun getFeedbackAudio(feedbackType: String): KBCachedAudio? {
        // Validate feedback type against allowlist to prevent path traversal
        if (feedbackType !in VALID_FEEDBACK_TYPES) {
            Log.w(TAG, "Invalid feedback type requested: $feedbackType")
            return null
        }

        val key = "feedback:$feedbackType"

        // Check cache
        cacheMutex.withLock {
            cache[key]?.let { return it }
        }

        // Fetch from server
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://$serverHost:$serverPort/api/kb/feedback/$feedbackType"
                val request = Request.Builder().url(url).build()

                val response = httpClient.newCall(request).execute()

                if (response.code == 404) {
                    return@withContext null
                }

                if (!response.isSuccessful) {
                    Log.e(TAG, "Server error: ${response.code}")
                    return@withContext null
                }

                val data = response.body?.bytes() ?: return@withContext null
                val duration = response.header("X-KB-Duration-Seconds")?.toDoubleOrNull() ?: 0.5
                val sampleRate = response.header("X-KB-Sample-Rate")?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE

                val cached = KBCachedAudio(data, duration, sampleRate)

                cacheMutex.withLock {
                    cache[key] = cached
                }

                evictIfNeeded()

                cached
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch feedback audio: ${e.message}")
                null
            }
        }
    }

    // Private server communication

    private suspend fun fetchAudio(
        questionId: String,
        segment: KBSegmentType,
        hintIndex: Int = 0,
    ): KBCachedAudio? =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuestionId = URLEncoder.encode(questionId, "UTF-8")
                val encodedSegment = URLEncoder.encode(segment.value, "UTF-8")

                var url = "http://$serverHost:$serverPort/api/kb/audio/$encodedQuestionId/$encodedSegment"
                url += "?module_id=$moduleId"
                if (segment == KBSegmentType.HINT) {
                    url += "&hint_index=$hintIndex"
                }

                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                // 404 means audio not pre-generated yet
                if (response.code == 404) {
                    Log.d(TAG, "Audio not available: $questionId/$segment")
                    return@withContext null
                }

                if (!response.isSuccessful) {
                    Log.e(TAG, "Server error: ${response.code}")
                    return@withContext null
                }

                val data = response.body?.bytes() ?: return@withContext null

                // Parse duration from header
                val duration = response.header("X-KB-Duration-Seconds")?.toDoubleOrNull() ?: 0.0

                // Parse sample rate from header
                val sampleRate = response.header("X-KB-Sample-Rate")?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE

                val cached = KBCachedAudio(data, duration, sampleRate)

                // Store in cache
                val key = cacheKey(questionId, segment, hintIndex)
                cacheMutex.withLock {
                    cache[key] = cached
                }

                // Evict if over size limit
                evictIfNeeded()

                Log.d(TAG, "Cached audio: $key (${data.size} bytes)")
                cached
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch audio: ${e.message}")
                null
            }
        }

    // Cache management

    private suspend fun evictIfNeeded() {
        cacheMutex.withLock {
            var currentSize = cache.values.sumOf { it.data.size }

            if (currentSize <= maxCacheSize) {
                return
            }

            // Sort entries by cachedAt (oldest first) for LRU eviction
            val sortedEntries = cache.entries.sortedBy { it.value.cachedAt }

            val keysToRemove = mutableListOf<String>()
            for ((key, entry) in sortedEntries) {
                if (currentSize <= maxCacheSize) break
                keysToRemove.add(key)
                currentSize -= entry.data.size
            }

            for (key in keysToRemove) {
                cache.remove(key)
            }

            Log.i(TAG, "Evicted ${keysToRemove.size} oldest entries to stay under size limit")
        }
    }
}

/**
 * Errors for KBAudioCache operations.
 */
sealed class KBAudioCacheError : Exception() {
    data object InvalidURL : KBAudioCacheError() {
        override val message = "Invalid server URL"
    }

    data object InvalidResponse : KBAudioCacheError() {
        override val message = "Invalid response from server"
    }

    data class ServerError(val code: Int) : KBAudioCacheError() {
        override val message = "Server error: $code"
    }
}
