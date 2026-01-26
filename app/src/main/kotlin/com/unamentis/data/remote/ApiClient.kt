package com.unamentis.data.remote

import android.content.Context
import android.os.Build
import android.util.Log
import com.unamentis.BuildConfig
import com.unamentis.data.model.Curriculum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import kotlin.math.min

/**
 * Configuration for API client.
 *
 * @property tokenProvider Function to get current access token (null if not logged in)
 * @property onTokenExpired Callback when token is expired (401 response)
 * @property logServerUrlProvider Provider function for log server base URL
 * @property managementUrlProvider Provider function for management console base URL
 */
data class ApiClientConfig(
    val tokenProvider: (suspend () -> String?)? = null,
    val onTokenExpired: (suspend () -> Unit)? = null,
    val logServerUrlProvider: () -> String = { DEFAULT_LOG_SERVER_URL },
    val managementUrlProvider: () -> String = { DEFAULT_MANAGEMENT_URL },
) {
    companion object {
        /** Default management URL for Android emulator */
        const val DEFAULT_MANAGEMENT_URL = "http://10.0.2.2:8766"

        /** Default log server URL for Android emulator */
        const val DEFAULT_LOG_SERVER_URL = "http://10.0.2.2:8765"
    }
}

/**
 * API client for communicating with the UnaMentis management console.
 *
 * Handles all HTTP requests to the server including:
 * - Authentication (login, register, token refresh)
 * - Curriculum management
 * - Session management
 * - TTS operations
 * - System health checks
 * - Metrics and logging
 *
 * Features:
 * - Bearer token authentication
 * - Automatic retry with exponential backoff
 * - Request/response logging
 * - Client identification headers
 *
 * @property _context Application context for device info
 * @property okHttpClient HTTP client instance
 * @property json JSON serializer
 * @property config Configuration for URLs and authentication callbacks
 */
class ApiClient(
    @Suppress("unused") private val _context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val config: ApiClientConfig = ApiClientConfig(),
) {
    companion object {
        private const val TAG = "ApiClient"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 10000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private val tokenProvider get() = config.tokenProvider
    private val onTokenExpired get() = config.onTokenExpired
    private val logServerUrl get() = config.logServerUrlProvider()
    private val managementUrl get() = config.managementUrlProvider()

    private val deviceId: String by lazy {
        UUID.randomUUID().toString()
    }

    private val deviceName: String by lazy {
        "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    // =============================================================================
    // AUTHENTICATION ENDPOINTS (01-AUTHENTICATION.md)
    // =============================================================================

    /**
     * Register a new user account.
     *
     * POST /api/auth/register
     *
     * @param request Registration details
     * @return Authentication tokens on success
     */
    suspend fun register(request: RegisterRequest): ApiResult<AuthTokens> = post("/api/auth/register", request, requiresAuth = false)

    /**
     * Login with email and password.
     *
     * POST /api/auth/login
     *
     * @param request Login credentials
     * @return Authentication tokens on success
     */
    suspend fun login(request: LoginRequest): ApiResult<AuthTokens> = post("/api/auth/login", request, requiresAuth = false)

    /**
     * Refresh access token using refresh token.
     *
     * POST /api/auth/refresh
     *
     * @param request Refresh token
     * @return New authentication tokens on success
     */
    suspend fun refreshToken(request: RefreshTokenRequest): ApiResult<AuthTokens> = post("/api/auth/refresh", request, requiresAuth = false)

    /**
     * Logout and revoke refresh token.
     *
     * POST /api/auth/logout
     */
    suspend fun logout(): ApiResult<Unit> = postEmpty("/api/auth/logout")

    /**
     * Get current user profile.
     *
     * GET /api/auth/me
     *
     * @return User profile
     */
    suspend fun getCurrentUser(): ApiResult<User> = get("/api/auth/me")

    /**
     * Update current user profile.
     *
     * PATCH /api/auth/me
     *
     * @param request Profile updates
     * @return Updated user profile
     */
    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<User> = patch("/api/auth/me", request)

    /**
     * Change password.
     *
     * POST /api/auth/password
     *
     * @param request Current and new password
     */
    suspend fun changePassword(request: ChangePasswordRequest): ApiResult<Unit> = postEmpty("/api/auth/password", request)

    /**
     * Get list of registered devices.
     *
     * GET /api/auth/devices
     *
     * @return List of devices
     */
    suspend fun getDevices(): ApiResult<List<Device>> = get("/api/auth/devices")

    /**
     * Remove a registered device.
     *
     * DELETE /api/auth/devices/{id}
     *
     * @param deviceId Device ID to remove
     */
    suspend fun removeDevice(deviceId: String): ApiResult<Unit> = delete("/api/auth/devices/$deviceId")

    /**
     * Get list of active sessions.
     *
     * GET /api/auth/sessions
     *
     * @return List of user sessions
     */
    suspend fun getUserSessions(): ApiResult<List<UserSession>> = get("/api/auth/sessions")

    /**
     * Terminate a user session.
     *
     * DELETE /api/auth/sessions/{id}
     *
     * @param sessionId Session ID to terminate
     */
    suspend fun terminateUserSession(sessionId: String): ApiResult<Unit> = delete("/api/auth/sessions/$sessionId")

    // =============================================================================
    // CURRICULUM ENDPOINTS (02-CURRICULA.md)
    // =============================================================================

    /**
     * Get list of available curricula.
     *
     * GET /api/curricula
     *
     * @return List of curriculum summaries
     */
    suspend fun getCurricula(): ApiResult<List<CurriculumSummary>> {
        // Server returns {"curricula": [...]} wrapper, not a direct array
        return when (val result = get<CurriculaResponse>("/api/curricula")) {
            is ApiResult.Success -> ApiResult.Success(result.data.curricula)
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }
    }

    /**
     * Get list of archived curricula.
     *
     * GET /api/curricula/archived
     *
     * @return List of archived curricula
     */
    suspend fun getArchivedCurricula(): ApiResult<List<ArchivedCurriculum>> = get("/api/curricula/archived")

    /**
     * Get curriculum details.
     *
     * GET /api/curricula/{id}
     *
     * @param id Curriculum ID
     * @return Curriculum summary
     */
    suspend fun getCurriculum(id: String): ApiResult<CurriculumSummary> = get("/api/curricula/$id")

    /**
     * Get full curriculum with all content and assets.
     *
     * GET /api/curricula/{id}/full-with-assets
     *
     * @param id Curriculum ID
     * @return Complete curriculum with assets
     */
    suspend fun getCurriculumFullWithAssets(id: String): Curriculum =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("$managementUrl/api/curricula/$id/full-with-assets")
                    .get()
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("API error: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                // The response has "curriculum" and "assets" fields, we extract curriculum
                val fullResponse = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(body)
                val curriculumJson = json.encodeToString(fullResponse["curriculum"])
                json.decodeFromString<Curriculum>(curriculumJson)
            }
        }

    /**
     * Update curriculum metadata.
     *
     * PUT /api/curricula/{id}
     *
     * @param id Curriculum ID
     * @param request Updated metadata
     * @return Updated curriculum summary
     */
    suspend fun updateCurriculum(
        id: String,
        request: UpdateCurriculumRequest,
    ): ApiResult<CurriculumSummary> = put("/api/curricula/$id", request)

    /**
     * Delete a curriculum permanently.
     *
     * DELETE /api/curricula/{id}
     *
     * @param id Curriculum ID
     */
    suspend fun deleteCurriculum(id: String): ApiResult<Unit> = delete("/api/curricula/$id")

    /**
     * Archive a curriculum.
     *
     * POST /api/curricula/{id}/archive
     *
     * @param id Curriculum ID
     */
    suspend fun archiveCurriculum(id: String): ApiResult<ArchivedCurriculum> = postEmpty("/api/curricula/$id/archive")

    /**
     * Unarchive a curriculum.
     *
     * POST /api/curricula/archived/{file}/unarchive
     *
     * @param file Archived file name
     * @return Restored curriculum summary
     */
    suspend fun unarchiveCurriculum(file: String): ApiResult<CurriculumSummary> = postEmpty("/api/curricula/archived/$file/unarchive")

    /**
     * Delete an archived curriculum.
     *
     * DELETE /api/curricula/archived/{file}
     *
     * @param file Archived file name
     */
    suspend fun deleteArchivedCurriculum(file: String): ApiResult<Unit> = delete("/api/curricula/archived/$file")

    /**
     * Reload all curricula from disk.
     *
     * POST /api/curricula/reload
     */
    suspend fun reloadCurricula(): ApiResult<List<CurriculumSummary>> = postEmpty("/api/curricula/reload")

    /**
     * Get topic transcript.
     *
     * GET /api/curricula/{id}/topics/{topicId}/transcript
     *
     * @param curriculumId Curriculum ID
     * @param topicId Topic ID
     * @return Transcript text
     */
    suspend fun getTopicTranscript(
        curriculumId: String,
        topicId: String,
    ): ApiResult<String> = get("/api/curricula/$curriculumId/topics/$topicId/transcript")

    /**
     * Preload all assets for a curriculum.
     *
     * POST /api/curricula/{id}/preload-assets
     *
     * @param id Curriculum ID
     */
    suspend fun preloadAssets(id: String): ApiResult<Unit> = postEmpty("/api/curricula/$id/preload-assets")

    /**
     * Stream audio for a topic.
     *
     * GET /api/curricula/{id}/topics/{topicId}/stream-audio
     *
     * Note: Returns audio stream URL. The actual streaming should be handled
     * by the audio playback system.
     *
     * @param curriculumId Curriculum ID
     * @param topicId Topic ID
     * @return Audio stream URL
     */
    suspend fun getTopicAudioStreamUrl(
        curriculumId: String,
        topicId: String,
    ): String = "$managementUrl/api/curricula/$curriculumId/topics/$topicId/stream-audio"

    /**
     * Upload a visual asset for a topic.
     *
     * POST /api/curricula/{id}/topics/{topicId}/assets
     *
     * @param curriculumId Curriculum ID
     * @param topicId Topic ID
     * @param asset Asset upload data
     * @return Created asset metadata
     */
    suspend fun uploadAsset(
        curriculumId: String,
        topicId: String,
        asset: VisualAssetUpload,
    ): ApiResult<VisualAssetMetadata> = post("/api/curricula/$curriculumId/topics/$topicId/assets", asset)

    /**
     * Update an existing visual asset.
     *
     * PATCH /api/curricula/{id}/topics/{topicId}/assets/{assetId}
     *
     * @param curriculumId Curriculum ID
     * @param topicId Topic ID
     * @param assetId Asset ID
     * @param update Asset update data
     * @return Updated asset metadata
     */
    suspend fun updateAsset(
        curriculumId: String,
        topicId: String,
        assetId: String,
        update: UpdateAssetRequest,
    ): ApiResult<VisualAssetMetadata> = patch("/api/curricula/$curriculumId/topics/$topicId/assets/$assetId", update)

    /**
     * Delete a visual asset.
     *
     * DELETE /api/curricula/{id}/topics/{topicId}/assets/{assetId}
     *
     * @param curriculumId Curriculum ID
     * @param topicId Topic ID
     * @param assetId Asset ID
     */
    suspend fun deleteAsset(
        curriculumId: String,
        topicId: String,
        assetId: String,
    ): ApiResult<Unit> = delete("/api/curricula/$curriculumId/topics/$topicId/assets/$assetId")

    /**
     * Import a curriculum from a file.
     *
     * POST /api/curricula
     *
     * Note: For file uploads, use multipart form data. This method accepts
     * the file as a byte array.
     *
     * @param fileName Original file name
     * @param fileData File content
     * @return Imported curriculum summary
     */
    suspend fun importCurriculum(
        fileName: String,
        fileData: ByteArray,
    ): ApiResult<CurriculumSummary> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody =
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "file",
                            fileName,
                            fileData.toRequestBody("application/octet-stream".toMediaType()),
                        )
                        .build()

                val request =
                    Request.Builder()
                        .url("$managementUrl/api/curricula")
                        .post(requestBody)
                        .addClientHeaders()
                        .addAuthHeader()
                        .build()

                executeRequest(request)
            } catch (e: IOException) {
                Log.e(TAG, "Network error importing curriculum: ${e.message}", e)
                ApiResult.NetworkError(e.message ?: "Network error", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error importing curriculum: ${e.message}", e)
                ApiResult.NetworkError(e.message ?: "Import error", e)
            }
        }

    // =============================================================================
    // SESSION ENDPOINTS (03-SESSIONS.md / FOV Context)
    // =============================================================================

    /**
     * Create a new session.
     *
     * POST /api/sessions
     *
     * @param request Session creation request
     * @return Created session
     */
    suspend fun createSession(request: CreateSessionRequest): ApiResult<ApiSession> = post("/api/sessions", request)

    /**
     * Get list of sessions.
     *
     * GET /api/sessions
     *
     * @param filter Optional filter parameters
     * @return List of sessions
     */
    suspend fun getSessions(filter: SessionFilter? = null): ApiResult<List<ApiSession>> {
        val queryParams =
            buildList {
                filter?.status?.let { add("status=$it") }
                filter?.curriculumId?.let { add("curriculum_id=$it") }
                filter?.limit?.let { add("limit=$it") }
                filter?.offset?.let { add("offset=$it") }
            }
        val query = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
        return get("/api/sessions$query")
    }

    /**
     * Get session details.
     *
     * GET /api/sessions/{id}
     *
     * @param id Session ID
     * @return Session details
     */
    suspend fun getSession(id: String): ApiResult<ApiSession> = get("/api/sessions/$id")

    /**
     * Delete a session.
     *
     * DELETE /api/sessions/{id}
     *
     * @param id Session ID
     */
    suspend fun deleteSession(id: String): ApiResult<Unit> = delete("/api/sessions/$id")

    /**
     * Start a session.
     *
     * POST /api/sessions/{id}/start
     *
     * @param id Session ID
     * @return Updated session
     */
    suspend fun startSession(id: String): ApiResult<ApiSession> = postEmpty("/api/sessions/$id/start")

    /**
     * Pause a session.
     *
     * POST /api/sessions/{id}/pause
     *
     * @param id Session ID
     * @return Updated session
     */
    suspend fun pauseSession(id: String): ApiResult<ApiSession> = postEmpty("/api/sessions/$id/pause")

    /**
     * Resume a session.
     *
     * POST /api/sessions/{id}/resume
     *
     * @param id Session ID
     * @return Updated session
     */
    suspend fun resumeSession(id: String): ApiResult<ApiSession> = postEmpty("/api/sessions/$id/resume")

    /**
     * End a session.
     *
     * POST /api/sessions/{id}/end
     *
     * @param id Session ID
     * @return Updated session
     */
    suspend fun endSession(id: String): ApiResult<ApiSession> = postEmpty("/api/sessions/$id/end")

    /**
     * Change the current topic in a session.
     *
     * PUT /api/sessions/{id}/topic
     *
     * @param id Session ID
     * @param request Topic change request
     * @return Updated session
     */
    suspend fun changeSessionTopic(
        id: String,
        request: ChangeTopicRequest,
    ): ApiResult<ApiSession> = put("/api/sessions/$id/topic", request)

    /**
     * Set playback position in a session.
     *
     * PUT /api/sessions/{id}/position
     *
     * @param id Session ID
     * @param request Position request
     * @return Updated session
     */
    suspend fun setSessionPosition(
        id: String,
        request: SetPositionRequest,
    ): ApiResult<ApiSession> = put("/api/sessions/$id/position", request)

    /**
     * Add a conversation turn to a session.
     *
     * POST /api/sessions/{id}/turns
     *
     * @param id Session ID
     * @param request Turn data
     * @return Created turn
     */
    suspend fun addConversationTurn(
        id: String,
        request: AddTurnRequest,
    ): ApiResult<ConversationTurn> = post("/api/sessions/$id/turns", request)

    /**
     * Record a barge-in event.
     *
     * POST /api/sessions/{id}/barge-in
     *
     * @param id Session ID
     * @param request Barge-in data
     */
    suspend fun recordBargeIn(
        id: String,
        request: BargeInRequest,
    ): ApiResult<Unit> = postEmpty("/api/sessions/$id/barge-in", request)

    /**
     * Get FOV context for a session.
     *
     * GET /api/sessions/{id}/context
     *
     * @param id Session ID
     * @return FOV context
     */
    suspend fun getSessionContext(id: String): ApiResult<FOVContext> = get("/api/sessions/$id/context")

    /**
     * Build LLM context for a session.
     *
     * POST /api/sessions/{id}/context/build
     *
     * @param id Session ID
     * @param request Build parameters
     * @return Built context
     */
    suspend fun buildLLMContext(
        id: String,
        request: BuildContextRequest,
    ): ApiResult<FOVContext> = post("/api/sessions/$id/context/build", request)

    /**
     * Get all messages in a session.
     *
     * GET /api/sessions/{id}/messages
     *
     * @param id Session ID
     * @return List of conversation turns
     */
    suspend fun getSessionMessages(id: String): ApiResult<List<ConversationTurn>> = get("/api/sessions/$id/messages")

    /**
     * Record a session signal.
     *
     * POST /api/sessions/{id}/signals
     *
     * @param id Session ID
     * @param signal Signal data
     */
    suspend fun recordSignal(
        id: String,
        signal: SessionSignal,
    ): ApiResult<Unit> = postEmpty("/api/sessions/$id/signals", signal)

    /**
     * Get timeline events for a session.
     *
     * GET /api/sessions/{id}/events
     *
     * @param id Session ID
     * @return List of timeline events
     */
    suspend fun getSessionEvents(id: String): ApiResult<List<TimelineEvent>> = get("/api/sessions/$id/events")

    /**
     * Get session debug information.
     *
     * GET /api/sessions/{id}/debug
     *
     * @param id Session ID
     * @return Debug info
     */
    suspend fun getSessionDebug(id: String): ApiResult<SessionDebugInfo> = get("/api/sessions/$id/debug")

    /**
     * Check FOV system health.
     *
     * GET /api/fov/health
     *
     * @return FOV health status
     */
    suspend fun checkFOVHealth(): ApiResult<FOVHealthResponse> = get("/api/fov/health")

    // =============================================================================
    // TTS ENDPOINTS (04-TTS.md)
    // =============================================================================

    /**
     * Generate TTS audio.
     *
     * POST /api/tts
     *
     * @param request TTS request
     * @return TTS response with audio URL or binary
     */
    suspend fun generateTTS(request: TTSRequest): ApiResult<TTSResponse> = post("/api/tts", request)

    /**
     * Get TTS cache statistics.
     *
     * GET /api/tts/cache/stats
     *
     * @return Cache statistics
     */
    suspend fun getTTSCacheStats(): ApiResult<TTSCacheStats> = get("/api/tts/cache/stats")

    /**
     * Clear entire TTS cache.
     *
     * DELETE /api/tts/cache
     */
    suspend fun clearTTSCache(): ApiResult<Unit> = delete("/api/tts/cache")

    /**
     * Evict expired TTS cache entries.
     *
     * DELETE /api/tts/cache/expired
     */
    suspend fun evictExpiredTTSCache(): ApiResult<Unit> = delete("/api/tts/cache/expired")

    /**
     * Evict TTS cache entries using LRU.
     *
     * POST /api/tts/cache/evict
     *
     * @param request Bytes to free
     */
    suspend fun evictLRUCache(request: CacheEvictRequest): ApiResult<Unit> = postEmpty("/api/tts/cache/evict", request)

    /**
     * Prefetch topic audio.
     *
     * POST /api/tts/prefetch/topic
     *
     * @param request Prefetch request
     * @return Prefetch job status
     */
    suspend fun prefetchTopicAudio(request: PrefetchTopicRequest): ApiResult<PrefetchJobStatus> = post("/api/tts/prefetch/topic", request)

    /**
     * Get prefetch job status.
     *
     * GET /api/tts/prefetch/status/{jobId}
     *
     * @param jobId Job ID
     * @return Job status
     */
    suspend fun getPrefetchStatus(jobId: String): ApiResult<PrefetchJobStatus> = get("/api/tts/prefetch/status/$jobId")

    /**
     * Cancel prefetch job.
     *
     * DELETE /api/tts/prefetch/{jobId}
     *
     * @param jobId Job ID
     */
    suspend fun cancelPrefetch(jobId: String): ApiResult<Unit> = delete("/api/tts/prefetch/$jobId")

    /**
     * Get all TTS profiles.
     *
     * GET /api/tts/profiles
     *
     * @return List of TTS profiles
     */
    suspend fun getTTSProfiles(): ApiResult<List<TTSProfile>> = get("/api/tts/profiles")

    /**
     * Create a TTS profile.
     *
     * POST /api/tts/profiles
     *
     * @param request Profile creation request
     * @return Created profile
     */
    suspend fun createTTSProfile(request: CreateTTSProfileRequest): ApiResult<TTSProfile> = post("/api/tts/profiles", request)

    /**
     * Get a TTS profile.
     *
     * GET /api/tts/profiles/{id}
     *
     * @param id Profile ID
     * @return TTS profile
     */
    suspend fun getTTSProfile(id: String): ApiResult<TTSProfile> = get("/api/tts/profiles/$id")

    /**
     * Update a TTS profile.
     *
     * PUT /api/tts/profiles/{id}
     *
     * @param id Profile ID
     * @param request Profile updates
     * @return Updated profile
     */
    suspend fun updateTTSProfile(
        id: String,
        request: UpdateTTSProfileRequest,
    ): ApiResult<TTSProfile> = put("/api/tts/profiles/$id", request)

    /**
     * Delete a TTS profile.
     *
     * DELETE /api/tts/profiles/{id}
     *
     * @param id Profile ID
     */
    suspend fun deleteTTSProfile(id: String): ApiResult<Unit> = delete("/api/tts/profiles/$id")

    /**
     * Set a TTS profile as default.
     *
     * POST /api/tts/profiles/{id}/set-default
     *
     * @param id Profile ID
     * @return Updated profile
     */
    suspend fun setDefaultTTSProfile(id: String): ApiResult<TTSProfile> = postEmpty("/api/tts/profiles/$id/set-default")

    /**
     * Preview a TTS profile.
     *
     * POST /api/tts/profiles/{id}/preview
     *
     * @param id Profile ID
     * @param request Preview text
     * @return TTS response
     */
    suspend fun previewTTSProfile(
        id: String,
        request: PreviewProfileRequest,
    ): ApiResult<TTSResponse> = post("/api/tts/profiles/$id/preview", request)

    /**
     * Duplicate a TTS profile.
     *
     * POST /api/tts/profiles/{id}/duplicate
     *
     * @param id Profile ID
     * @return Duplicated profile
     */
    suspend fun duplicateTTSProfile(id: String): ApiResult<TTSProfile> = postEmpty("/api/tts/profiles/$id/duplicate")

    /**
     * Export a TTS profile to JSON.
     *
     * GET /api/tts/profiles/{id}/export
     *
     * @param id Profile ID
     * @return Profile JSON string
     */
    suspend fun exportTTSProfile(id: String): ApiResult<String> = get("/api/tts/profiles/$id/export")

    /**
     * Import a TTS profile from JSON.
     *
     * POST /api/tts/profiles/import
     *
     * @param profileJson Profile JSON string
     * @return Imported profile
     */
    suspend fun importTTSProfile(profileJson: String): ApiResult<TTSProfile> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = profileJson.toRequestBody(JSON_MEDIA_TYPE)
                val request =
                    Request.Builder()
                        .url("$managementUrl/api/tts/profiles/import")
                        .post(requestBody)
                        .addClientHeaders()
                        .addAuthHeader()
                        .build()

                executeRequest(request)
            } catch (e: IOException) {
                Log.e(TAG, "Network error importing TTS profile: ${e.message}", e)
                ApiResult.NetworkError(e.message ?: "Network error", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error importing TTS profile: ${e.message}", e)
                ApiResult.NetworkError(e.message ?: "Import error", e)
            }
        }

    /**
     * Get a cached TTS entry by hash.
     *
     * GET /api/tts/cache
     *
     * @param hash Cache entry hash
     * @return Cache entry with audio URL
     */
    suspend fun getTTSCacheEntry(hash: String): ApiResult<TTSCacheEntry> = get("/api/tts/cache?hash=$hash")

    /**
     * Store a TTS cache entry.
     *
     * PUT /api/tts/cache
     *
     * @param entry Cache entry to store
     * @return Stored cache entry
     */
    suspend fun storeTTSCacheEntry(entry: TTSCacheEntry): ApiResult<TTSCacheEntry> = put("/api/tts/cache", entry)

    // =============================================================================
    // IMPORT ENDPOINTS (05-IMPORT.md)
    // =============================================================================

    /**
     * Get available import sources.
     *
     * GET /api/import/sources
     *
     * @return List of import sources
     */
    suspend fun getImportSources(): ApiResult<List<ImportSource>> = get("/api/import/sources")

    /**
     * Get import source details.
     *
     * GET /api/import/sources/{id}
     *
     * @param id Source ID
     * @return Import source details
     */
    suspend fun getImportSource(id: String): ApiResult<ImportSource> = get("/api/import/sources/$id")

    /**
     * Browse courses from an import source.
     *
     * GET /api/import/sources/{id}/courses
     *
     * @param id Source ID
     * @return List of courses
     */
    suspend fun browseCourses(id: String): ApiResult<List<ImportCourse>> = get("/api/import/sources/$id/courses")

    /**
     * Search courses from an import source.
     *
     * GET /api/import/sources/{id}/search
     *
     * @param id Source ID
     * @param query Search query
     * @return Search results
     */
    suspend fun searchCourses(
        id: String,
        query: String,
    ): ApiResult<CourseSearchResult> = get("/api/import/sources/$id/search?q=$query")

    /**
     * Get course preview.
     *
     * GET /api/import/sources/{id}/courses/{courseId}
     *
     * @param sourceId Source ID
     * @param courseId Course ID
     * @return Course details
     */
    suspend fun getCoursePreview(
        sourceId: String,
        courseId: String,
    ): ApiResult<ImportCourse> = get("/api/import/sources/$sourceId/courses/$courseId")

    /**
     * Start an import job.
     *
     * POST /api/import/jobs
     *
     * @param request Import job request
     * @return Import job status
     */
    suspend fun startImportJob(request: StartImportJobRequest): ApiResult<ImportJob> = post("/api/import/jobs", request)

    /**
     * Get list of import jobs.
     *
     * GET /api/import/jobs
     *
     * @return List of import jobs
     */
    suspend fun getImportJobs(): ApiResult<List<ImportJob>> = get("/api/import/jobs")

    /**
     * Get import job status.
     *
     * GET /api/import/jobs/{id}
     *
     * @param id Job ID
     * @return Job status
     */
    suspend fun getImportJob(id: String): ApiResult<ImportJob> = get("/api/import/jobs/$id")

    /**
     * Cancel an import job.
     *
     * DELETE /api/import/jobs/{id}
     *
     * @param id Job ID
     */
    suspend fun cancelImportJob(id: String): ApiResult<Unit> = delete("/api/import/jobs/$id")

    /**
     * Get import system status.
     *
     * GET /api/import/status
     *
     * @return Import system status
     */
    suspend fun getImportStatus(): ApiResult<ImportSystemStatus> = get("/api/import/status")

    // =============================================================================
    // MEDIA ENDPOINTS (06-MEDIA.md)
    // =============================================================================

    /**
     * Get media capabilities.
     *
     * GET /api/media/capabilities
     *
     * @return Media capabilities
     */
    suspend fun getMediaCapabilities(): ApiResult<MediaCapabilities> = get("/api/media/capabilities")

    /**
     * Validate diagram syntax.
     *
     * POST /api/media/diagrams/validate
     *
     * @param request Diagram to validate
     * @return Validation result
     */
    suspend fun validateDiagram(request: DiagramRequest): ApiResult<DiagramValidation> = post("/api/media/diagrams/validate", request)

    /**
     * Render a diagram.
     *
     * POST /api/media/diagrams/render
     *
     * @param request Diagram to render
     * @return Rendered media
     */
    suspend fun renderDiagram(request: DiagramRequest): ApiResult<RenderedMedia> = post("/api/media/diagrams/render", request)

    /**
     * Validate formula syntax.
     *
     * POST /api/media/formulas/validate
     *
     * @param request Formula to validate
     * @return Validation result
     */
    suspend fun validateFormula(request: FormulaRequest): ApiResult<FormulaValidation> = post("/api/media/formulas/validate", request)

    /**
     * Render a formula.
     *
     * POST /api/media/formulas/render
     *
     * @param request Formula to render
     * @return Rendered media
     */
    suspend fun renderFormula(request: FormulaRequest): ApiResult<RenderedMedia> = post("/api/media/formulas/render", request)

    /**
     * Get available map styles.
     *
     * GET /api/media/maps/styles
     *
     * @return List of map styles
     */
    suspend fun getMapStyles(): ApiResult<List<String>> = get("/api/media/maps/styles")

    /**
     * Render a map.
     *
     * POST /api/media/maps/render
     *
     * @param request Map to render
     * @return Rendered media
     */
    suspend fun renderMap(request: MapRequest): ApiResult<RenderedMedia> = post("/api/media/maps/render", request)

    // =============================================================================
    // SYSTEM ENDPOINTS (07-SYSTEM.md)
    // =============================================================================

    /**
     * Check basic health.
     *
     * GET /health
     *
     * @return Health status
     */
    suspend fun checkHealth(): ApiResult<HealthCheck> = get("/health", requiresAuth = false)

    /**
     * Get system metrics.
     *
     * GET /api/system/metrics
     *
     * @return System metrics
     */
    suspend fun getSystemMetrics(): ApiResult<SystemMetrics> = get("/api/system/metrics")

    /**
     * Get system snapshot.
     *
     * GET /api/system/snapshot
     *
     * @return System snapshot
     */
    suspend fun getSystemSnapshot(): ApiResult<SystemSnapshot> = get("/api/system/snapshot")

    /**
     * Get list of services.
     *
     * GET /api/services
     *
     * @return List of services
     */
    suspend fun getServices(): ApiResult<List<ServiceInfo>> = get("/api/services")

    /**
     * Start a service.
     *
     * POST /api/services/{id}/start
     *
     * @param id Service ID
     * @return Updated service info
     */
    suspend fun startService(id: String): ApiResult<ServiceInfo> = postEmpty("/api/services/$id/start")

    /**
     * Stop a service.
     *
     * POST /api/services/{id}/stop
     *
     * @param id Service ID
     * @return Updated service info
     */
    suspend fun stopService(id: String): ApiResult<ServiceInfo> = postEmpty("/api/services/$id/stop")

    /**
     * Restart a service.
     *
     * POST /api/services/{id}/restart
     *
     * @param id Service ID
     * @return Updated service info
     */
    suspend fun restartService(id: String): ApiResult<ServiceInfo> = postEmpty("/api/services/$id/restart")

    /**
     * Start all services.
     *
     * POST /api/services/start-all
     *
     * @return List of service info
     */
    suspend fun startAllServices(): ApiResult<List<ServiceInfo>> = postEmpty("/api/services/start-all")

    /**
     * Stop all services.
     *
     * POST /api/services/stop-all
     *
     * @return List of service info
     */
    suspend fun stopAllServices(): ApiResult<List<ServiceInfo>> = postEmpty("/api/services/stop-all")

    /**
     * Get list of AI models.
     *
     * GET /api/models
     *
     * @return List of models
     */
    suspend fun getModels(): ApiResult<List<ModelInfo>> = get("/api/models")

    /**
     * Load a model into memory.
     *
     * POST /api/models/{id}/load
     *
     * @param id Model ID
     * @return Updated model info
     */
    suspend fun loadModel(id: String): ApiResult<ModelInfo> = postEmpty("/api/models/$id/load")

    /**
     * Unload a model from memory.
     *
     * POST /api/models/{id}/unload
     *
     * @param id Model ID
     * @return Updated model info
     */
    suspend fun unloadModel(id: String): ApiResult<ModelInfo> = postEmpty("/api/models/$id/unload")

    /**
     * Pull/download a model.
     *
     * POST /api/models/pull
     *
     * @param request Model to pull
     * @return Model info
     */
    suspend fun pullModel(request: PullModelRequest): ApiResult<ModelInfo> = post("/api/models/pull", request)

    /**
     * Get model capabilities.
     *
     * GET /api/models/capabilities
     *
     * @return Model capabilities
     */
    suspend fun getModelCapabilities(): ApiResult<ModelCapabilities> = get("/api/models/capabilities")

    /**
     * Get idle status.
     *
     * GET /api/system/idle/status
     *
     * @return Idle status
     */
    suspend fun getIdleStatus(): ApiResult<IdleStatus> = get("/api/system/idle/status")

    /**
     * Configure idle behavior.
     *
     * POST /api/system/idle/config
     *
     * @param request Idle configuration
     * @return Updated idle status
     */
    suspend fun configureIdle(request: IdleConfigRequest): ApiResult<IdleStatus> = post("/api/system/idle/config", request)

    /**
     * Keep system awake.
     *
     * POST /api/system/idle/keep-awake
     */
    suspend fun keepAwake(): ApiResult<Unit> = postEmpty("/api/system/idle/keep-awake")

    /**
     * Unload all models.
     *
     * POST /api/system/unload-models
     */
    suspend fun unloadAllModels(): ApiResult<Unit> = postEmpty("/api/system/unload-models")

    /**
     * Get connected clients.
     *
     * GET /api/clients
     *
     * @return List of connected clients
     */
    suspend fun getClients(): ApiResult<List<ConnectedClient>> = get("/api/clients")

    /**
     * Get aggregated statistics.
     *
     * GET /api/stats
     *
     * @return Aggregated stats
     */
    suspend fun getStats(): ApiResult<AggregatedStats> = get("/api/stats")

    /**
     * Get hourly historical metrics.
     *
     * GET /api/system/history/hourly
     *
     * @return Hourly metrics
     */
    suspend fun getHourlyMetrics(): ApiResult<HistoricalMetrics> = get("/api/system/history/hourly")

    /**
     * Get daily historical metrics.
     *
     * GET /api/system/history/daily
     *
     * @return Daily metrics
     */
    suspend fun getDailyMetrics(): ApiResult<HistoricalMetrics> = get("/api/system/history/daily")

    // =============================================================================
    // CLIENT/TELEMETRY ENDPOINTS
    // =============================================================================

    /**
     * Upload session metrics.
     *
     * POST /api/metrics
     *
     * @param metrics Metrics to upload
     * @return Upload response
     */
    suspend fun uploadMetrics(metrics: MetricsUploadRequest): MetricsUploadResponse =
        withContext(Dispatchers.IO) {
            val bodyJson = json.encodeToString(metrics)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request.Builder()
                    .url("$managementUrl/api/metrics")
                    .post(requestBody)
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Metrics upload failed: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                json.decodeFromString<MetricsUploadResponse>(body)
            }
        }

    /**
     * Send client heartbeat.
     *
     * POST /api/clients/heartbeat
     *
     * @param heartbeat Heartbeat data
     * @return Heartbeat response
     */
    suspend fun sendHeartbeat(heartbeat: ClientHeartbeat): HeartbeatResponse =
        withContext(Dispatchers.IO) {
            val bodyJson = json.encodeToString(heartbeat)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request.Builder()
                    .url("$managementUrl/api/clients/heartbeat")
                    .post(requestBody)
                    .addClientHeaders()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Heartbeat failed: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                json.decodeFromString<HeartbeatResponse>(body)
            }
        }

    /**
     * Send log entry to log server.
     *
     * POST /log
     *
     * @param logEntry Log entry to send
     */
    suspend fun sendLog(logEntry: LogEntry) =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = json.encodeToString(logEntry)
                val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

                val request =
                    Request.Builder()
                        .url("$logServerUrl/log")
                        .post(requestBody)
                        .build()

                okHttpClient.newCall(request).execute().use { response ->
                    // Silently fail - don't want logging to crash the app
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Log upload failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // Silently fail logging errors - don't want logging to crash the app
                Log.w(TAG, "Failed to send log: ${e.message}", e)
            }
        }

    // =============================================================================
    // PRIVATE HELPER METHODS
    // =============================================================================

    /**
     * Perform GET request with automatic retry.
     */
    private suspend inline fun <reified T> get(
        path: String,
        requiresAuth: Boolean = true,
    ): ApiResult<T> =
        executeWithRetry {
            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .get()
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Perform POST request with body.
     */
    private suspend inline fun <reified T, reified R> post(
        path: String,
        body: T,
        requiresAuth: Boolean = true,
    ): ApiResult<R> =
        executeWithRetry {
            val bodyJson = json.encodeToString(body)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .post(requestBody)
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Perform POST request with empty or typed body, returning typed or Unit response.
     */
    private suspend inline fun <reified R> postEmpty(
        path: String,
        requiresAuth: Boolean = true,
    ): ApiResult<R> =
        executeWithRetry {
            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .post("".toRequestBody(JSON_MEDIA_TYPE))
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Perform POST request with body, returning typed response.
     */
    private suspend inline fun <reified T, reified R> postEmpty(
        path: String,
        body: T,
        requiresAuth: Boolean = true,
    ): ApiResult<R> =
        executeWithRetry {
            val bodyJson = json.encodeToString(body)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .post(requestBody)
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Perform PUT request.
     */
    private suspend inline fun <reified T, reified R> put(
        path: String,
        body: T,
        requiresAuth: Boolean = true,
    ): ApiResult<R> =
        executeWithRetry {
            val bodyJson = json.encodeToString(body)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .put(requestBody)
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Perform PATCH request.
     */
    private suspend inline fun <reified T, reified R> patch(
        path: String,
        body: T,
        requiresAuth: Boolean = true,
    ): ApiResult<R> =
        executeWithRetry {
            val bodyJson = json.encodeToString(body)
            val requestBody = bodyJson.toRequestBody(JSON_MEDIA_TYPE)

            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .patch(requestBody)
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Perform DELETE request.
     */
    private suspend inline fun <reified R> delete(
        path: String,
        requiresAuth: Boolean = true,
    ): ApiResult<R> =
        executeWithRetry {
            val request =
                Request.Builder()
                    .url("$managementUrl$path")
                    .delete()
                    .addClientHeaders()
                    .apply { if (requiresAuth) addAuthHeader() }
                    .build()

            executeRequest(request)
        }

    /**
     * Execute request and parse response.
     */
    private suspend inline fun <reified T> executeRequest(request: Request): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    handleResponse<T>(response.code, bodyString, response.isSuccessful)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error: ${e.message}", e)
                ApiResult.NetworkError(e.message ?: "Network error", e)
            } catch (e: Exception) {
                Log.e(TAG, "Request error: ${e.message}", e)
                ApiResult.NetworkError(e.message ?: "Request error", e)
            }
        }

    /**
     * Handle HTTP response and convert to ApiResult.
     */
    private suspend inline fun <reified T> handleResponse(
        code: Int,
        bodyString: String?,
        isSuccessful: Boolean,
    ): ApiResult<T> =
        when {
            code == 401 -> handleUnauthorized(bodyString)
            !isSuccessful -> handleError(bodyString, code)
            else -> handleSuccess(bodyString)
        }

    /**
     * Handle 401 Unauthorized response.
     */
    private suspend fun <T> handleUnauthorized(bodyString: String?): ApiResult<T> {
        onTokenExpired?.invoke()
        val error = parseApiError(bodyString, "Unauthorized", "UNAUTHORIZED")
        return ApiResult.Error(error, 401)
    }

    /**
     * Handle error response (non-2xx status codes).
     */
    private fun <T> handleError(
        bodyString: String?,
        code: Int,
    ): ApiResult<T> {
        val error = parseApiError(bodyString, "Unknown error", "HTTP_$code")
        return ApiResult.Error(error, code)
    }

    /**
     * Handle successful response.
     */
    private inline fun <reified T> handleSuccess(bodyString: String?): ApiResult<T> {
        if (T::class == Unit::class) {
            @Suppress("UNCHECKED_CAST")
            return ApiResult.Success(Unit as T)
        }
        val data =
            bodyString?.let { json.decodeFromString<T>(it) }
                ?: throw IOException("Empty response body")
        return ApiResult.Success(data)
    }

    /**
     * Parse API error from response body, falling back to defaults if parsing fails.
     */
    private fun parseApiError(
        bodyString: String?,
        defaultMessage: String,
        defaultCode: String,
    ): ApiError =
        bodyString?.let {
            try {
                json.decodeFromString<ApiError>(it)
            } catch (_: Exception) {
                // Failed to parse as ApiError, use raw string as message
                ApiError(it.ifEmpty { defaultMessage }, defaultCode)
            }
        } ?: ApiError(defaultMessage, defaultCode)

    /**
     * Execute with exponential backoff retry.
     */
    private suspend fun <T> executeWithRetry(
        maxRetries: Int = MAX_RETRIES,
        block: suspend () -> ApiResult<T>,
    ): ApiResult<T> {
        var lastResult: ApiResult<T>? = null
        var delayMs = INITIAL_RETRY_DELAY_MS

        repeat(maxRetries) { attempt ->
            val result = block()

            when (result) {
                is ApiResult.Success -> return result
                is ApiResult.Error -> {
                    // Don't retry client errors (4xx) except for rate limiting (429)
                    if (result.httpCode in 400..499 && result.httpCode != 429) {
                        return result
                    }
                    lastResult = result
                }
                is ApiResult.NetworkError -> {
                    lastResult = result
                }
            }

            if (attempt < maxRetries - 1) {
                Log.w(TAG, "Request failed (attempt ${attempt + 1}/$maxRetries), retrying in ${delayMs}ms")
                delay(delayMs)
                delayMs = min(delayMs * 2, MAX_RETRY_DELAY_MS)
            }
        }

        return lastResult ?: ApiResult.NetworkError("Max retries exceeded")
    }

    /**
     * Add client identification headers to request.
     */
    private fun Request.Builder.addClientHeaders(): Request.Builder {
        return this
            .addHeader("X-Client-ID", deviceId)
            .addHeader("X-Client-Name", deviceName)
            .addHeader("X-Client-Platform", "Android")
            .addHeader("X-Client-Version", BuildConfig.VERSION_NAME)
    }

    /**
     * Add authorization header to request.
     */
    private suspend fun Request.Builder.addAuthHeader(): Request.Builder {
        val token = tokenProvider?.invoke()
        return if (token != null) {
            addHeader("Authorization", "Bearer $token")
        } else {
            this
        }
    }
}
