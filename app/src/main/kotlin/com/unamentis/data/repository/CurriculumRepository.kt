package com.unamentis.data.repository

import android.util.Log
import com.unamentis.data.local.dao.CurriculumDao
import com.unamentis.data.local.entity.CurriculumEntity
import com.unamentis.data.model.Curriculum
import com.unamentis.data.remote.ApiClient
import com.unamentis.data.remote.CurriculumSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection state for server communication.
 */
sealed class ConnectionState {
    /** Connection state unknown (not yet checked) */
    data object Unknown : ConnectionState()

    /** Checking connection to server */
    data object Checking : ConnectionState()

    /** Successfully connected to server */
    data object Connected : ConnectionState()

    /** Failed to connect to server */
    data class Failed(val message: String) : ConnectionState()
}

/**
 * Repository for curriculum data management.
 *
 * Responsibilities:
 * - Store downloaded curricula locally
 * - Retrieve curriculum content from server (management console)
 * - Manage curriculum metadata
 * - Handle curriculum downloads with progress
 *
 * Server connectivity:
 * - Management console at port 8766 (same server as iOS app)
 * - Uses ApiClient for all HTTP communication
 * - Android emulator accesses host via 10.0.2.2
 *
 * @property curriculumDao Room DAO for curriculum data
 * @property apiClient API client for server communication
 */
@Singleton
class CurriculumRepository
    @Inject
    constructor(
        private val curriculumDao: CurriculumDao,
        private val apiClient: ApiClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        companion object {
            private const val TAG = "CurriculumRepository"
        }

        // Cache for server curricula summaries (available for download)
        private val _serverCurriculaSummaries = MutableStateFlow<List<CurriculumSummary>>(emptyList())
        val serverCurriculaSummaries: StateFlow<List<CurriculumSummary>> = _serverCurriculaSummaries.asStateFlow()

        // Connection state
        private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unknown)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        // Last error message
        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        // Loading state
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        /**
         * Get all downloaded curricula (local).
         */
        fun getAllCurricula(): Flow<List<Curriculum>> {
            return curriculumDao.getAllCurricula().map { entities ->
                entities.mapNotNull { entity ->
                    try {
                        json.decodeFromString<Curriculum>(entity.contentJson)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse curriculum ${entity.id}: ${e.message}")
                        null
                    }
                }
            }
        }

        /**
         * Get local curricula (alias for getAllCurricula).
         */
        fun getLocalCurricula(): Flow<List<Curriculum>> = getAllCurricula()

        /**
         * Get server curricula summaries as a Flow.
         * Call [refreshCurricula] first to populate this.
         */
        fun getServerCurriculaSummariesFlow(): Flow<List<CurriculumSummary>> = _serverCurriculaSummaries

        /**
         * Refresh curricula list from server.
         *
         * Fetches the list of available curricula from the management console.
         * The management console runs on port 8766 (same as iOS app).
         *
         * @return true if refresh succeeded, false if server unreachable
         */
        suspend fun refreshCurricula(): Boolean {
            _isLoading.value = true
            _connectionState.value = ConnectionState.Checking
            _lastError.value = null

            return try {
                Log.d(TAG, "Fetching curricula from management console...")
                val summaries = apiClient.getCurricula()
                _serverCurriculaSummaries.value = summaries
                _connectionState.value = ConnectionState.Connected
                Log.i(TAG, "Successfully fetched ${summaries.size} curricula from server")
                true
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("Connection refused") == true ->
                        "Server not running. Start the management console on port 8766."
                    e.message?.contains("timeout") == true ->
                        "Connection timed out. Check network and server status."
                    e.message?.contains("Unable to resolve host") == true ->
                        "Cannot reach server. Check network connection."
                    else -> "Failed to connect: ${e.message}"
                }
                Log.e(TAG, "Failed to fetch curricula: $errorMsg", e)
                _connectionState.value = ConnectionState.Failed(errorMsg)
                _lastError.value = errorMsg
                false
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Download a curriculum from the server.
         *
         * Fetches the full curriculum content including all topics and assets
         * from the management console, then saves it to local storage.
         *
         * @param curriculumId The curriculum ID to download
         * @return Flow emitting download progress (0.0 to 1.0)
         */
        fun downloadCurriculum(curriculumId: String): Flow<Float> =
            flow {
                emit(0.0f)
                _lastError.value = null

                try {
                    Log.d(TAG, "Starting download for curriculum: $curriculumId")
                    emit(0.1f)

                    // Fetch full curriculum with assets from server
                    val curriculum = apiClient.getCurriculumFullWithAssets(curriculumId)
                    emit(0.5f)

                    Log.d(TAG, "Downloaded curriculum: ${curriculum.title} with ${curriculum.topics.size} topics")

                    // Save to local storage
                    saveCurriculum(curriculum)
                    emit(0.9f)

                    Log.i(TAG, "Successfully saved curriculum ${curriculum.id} to local storage")
                    emit(1.0f)
                } catch (e: Exception) {
                    val errorMsg = "Download failed: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    _lastError.value = errorMsg

                    // Emit completion even on error (UI will check lastError)
                    emit(1.0f)
                }
            }

        /**
         * Check if the server is reachable.
         *
         * Attempts to fetch the curricula list to verify connectivity.
         * Updates [connectionState] with the result.
         *
         * @return true if server is reachable
         */
        suspend fun checkServerConnection(): Boolean {
            return refreshCurricula()
        }

        /**
         * Get a specific curriculum by ID from local storage.
         */
        suspend fun getCurriculumById(curriculumId: String): Curriculum? {
            return curriculumDao.getCurriculumById(curriculumId)?.let { entity ->
                try {
                    json.decodeFromString<Curriculum>(entity.contentJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse curriculum $curriculumId: ${e.message}")
                    null
                }
            }
        }

        /**
         * Check if a curriculum is downloaded locally.
         */
        suspend fun isDownloaded(curriculumId: String): Boolean {
            return curriculumDao.getCurriculumById(curriculumId) != null
        }

        /**
         * Save a curriculum to local storage.
         */
        suspend fun saveCurriculum(curriculum: Curriculum) {
            val entity =
                CurriculumEntity(
                    id = curriculum.id,
                    title = curriculum.title,
                    description = curriculum.description,
                    version = curriculum.version,
                    contentJson = json.encodeToString(curriculum),
                    downloadedAt = System.currentTimeMillis(),
                    lastAccessedAt = System.currentTimeMillis(),
                    topicCount = curriculum.topics.size,
                )
            curriculumDao.insertCurriculum(entity)
        }

        /**
         * Update last accessed timestamp for a curriculum.
         */
        suspend fun updateLastAccessed(curriculumId: String) {
            curriculumDao.updateLastAccessed(curriculumId, System.currentTimeMillis())
        }

        /**
         * Delete a curriculum from local storage.
         */
        suspend fun deleteCurriculum(curriculumId: String) {
            curriculumDao.deleteCurriculum(curriculumId)
        }

        /**
         * Delete all downloaded curricula.
         */
        suspend fun deleteAllCurricula() {
            curriculumDao.deleteAllCurricula()
        }

        /**
         * Clear error state.
         */
        fun clearError() {
            _lastError.value = null
        }
    }
