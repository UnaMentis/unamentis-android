package com.unamentis.ui.curriculum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.data.model.Curriculum
import com.unamentis.data.remote.CurriculumSummary
import com.unamentis.data.repository.ConnectionState
import com.unamentis.data.repository.CurriculumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Curriculum screen.
 *
 * Responsibilities:
 * - Manage curriculum list (server + local)
 * - Handle curriculum downloads with progress tracking
 * - Manage search and filtering
 * - Expose curriculum state to UI
 * - Handle server connection state
 *
 * Server connectivity:
 * - Connects to management console on port 8766 (same as iOS app)
 * - Shows connection status to user
 * - Gracefully handles server unavailability
 *
 * @property curriculumRepository Repository for curriculum data
 */
@HiltViewModel
class CurriculumViewModel
    @Inject
    constructor(
        private val curriculumRepository: CurriculumRepository,
    ) : ViewModel() {
        /**
         * Search query state.
         */
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        /**
         * Selected tab (Server or Local).
         */
        private val _selectedTab = MutableStateFlow(CurriculumTab.SERVER)
        val selectedTab: StateFlow<CurriculumTab> = _selectedTab.asStateFlow()

        /**
         * Loading state (from repository).
         */
        val isLoading: StateFlow<Boolean> = curriculumRepository.isLoading

        /**
         * Error state (from repository).
         */
        val error: StateFlow<String?> = curriculumRepository.lastError

        /**
         * Connection state (from repository).
         */
        val connectionState: StateFlow<ConnectionState> = curriculumRepository.connectionState

        /**
         * Download progress by curriculum ID (0.0 to 1.0).
         */
        private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
        val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

        /**
         * Server curricula summaries (available for download).
         */
        val serverCurriculaSummaries: StateFlow<List<CurriculumSummary>> =
            curriculumRepository.serverCurriculaSummaries

        /**
         * Local curricula list (downloaded).
         */
        private val localCurricula: StateFlow<List<Curriculum>> =
            curriculumRepository
                .getLocalCurricula()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Combined UI state.
         */
        val uiState: StateFlow<CurriculumUiState> =
            combine(
                combine(selectedTab, serverCurriculaSummaries, localCurricula) { tab, server, local ->
                    Triple(tab, server, local)
                },
                combine(searchQuery, isLoading, error, downloadProgress) { query, loading, errorMsg, progress ->
                    CurriculumUiStatePartial(query, loading, errorMsg, progress)
                },
                connectionState,
            ) { (tab, serverSummaries, local), partial, connState ->
                // Filter local curricula by search
                val filteredLocal =
                    local.filter { curriculum ->
                        if (partial.searchQuery.isBlank()) {
                            true
                        } else {
                            curriculum.title.contains(partial.searchQuery, ignoreCase = true) ||
                                curriculum.topics.any { it.title.contains(partial.searchQuery, ignoreCase = true) }
                        }
                    }

                // Filter server summaries by search
                val filteredServer =
                    serverSummaries.filter { summary ->
                        if (partial.searchQuery.isBlank()) {
                            true
                        } else {
                            summary.title.contains(partial.searchQuery, ignoreCase = true) ||
                                summary.description.contains(partial.searchQuery, ignoreCase = true) ||
                                summary.keywords.any { it.contains(partial.searchQuery, ignoreCase = true) }
                        }
                    }

                // Get IDs of locally downloaded curricula
                val downloadedIds = local.map { it.id }.toSet()

                CurriculumUiState(
                    selectedTab = tab,
                    serverCurricula = filteredServer,
                    localCurricula = filteredLocal,
                    downloadedCurriculumIds = downloadedIds,
                    searchQuery = partial.searchQuery,
                    isLoading = partial.isLoading,
                    error = partial.error,
                    downloadProgress = partial.downloadProgress,
                    connectionState = connState,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CurriculumUiState(),
            )

        /**
         * Helper data class for partial UI state.
         */
        private data class CurriculumUiStatePartial(
            val searchQuery: String,
            val isLoading: Boolean,
            val error: String?,
            val downloadProgress: Map<String, Float>,
        )

        /**
         * Refresh curriculum lists from server.
         *
         * Fetches available curricula from the management console.
         * If the server is not running, shows an appropriate error message.
         */
        fun refresh() {
            viewModelScope.launch {
                curriculumRepository.refreshCurricula()
            }
        }

        /**
         * Check server connection.
         *
         * Verifies connectivity to the management console.
         */
        fun checkConnection() {
            viewModelScope.launch {
                curriculumRepository.checkServerConnection()
            }
        }

        /**
         * Download a curriculum from server.
         *
         * Fetches the full curriculum content from the management console
         * and saves it to local storage.
         *
         * @param curriculumId The curriculum ID to download
         */
        fun downloadCurriculum(curriculumId: String) {
            viewModelScope.launch {
                try {
                    curriculumRepository.downloadCurriculum(curriculumId)
                        .collect { progress ->
                            _downloadProgress.value = _downloadProgress.value + (curriculumId to progress)
                            if (progress >= 1.0f) {
                                // Remove from progress map after short delay
                                kotlinx.coroutines.delay(1000)
                                _downloadProgress.value = _downloadProgress.value - curriculumId
                            }
                        }
                } catch (e: Exception) {
                    // Download failed, remove from progress map - error is handled by repository
                    android.util.Log.w("CurriculumViewModel", "Download failed for $curriculumId: ${e.message}", e)
                    _downloadProgress.value = _downloadProgress.value - curriculumId
                }
            }
        }

        /**
         * Delete a local curriculum.
         */
        fun deleteCurriculum(curriculumId: String) {
            viewModelScope.launch {
                curriculumRepository.deleteCurriculum(curriculumId)
            }
        }

        /**
         * Update search query.
         */
        fun updateSearchQuery(query: String) {
            _searchQuery.value = query
        }

        /**
         * Select tab (Server or Local).
         */
        fun selectTab(tab: CurriculumTab) {
            _selectedTab.value = tab
        }

        /**
         * Clear error message.
         */
        fun clearError() {
            curriculumRepository.clearError()
        }

        init {
            // Auto-refresh on initialization (will show error if server not running)
            refresh()
        }
    }

/**
 * Curriculum tab selection.
 */
enum class CurriculumTab {
    /** Server tab - shows curricula available for download */
    SERVER,

    /** Local tab - shows downloaded curricula */
    LOCAL,

    /** Modules tab - shows specialized training modules like Knowledge Bowl */
    MODULES,
}

/**
 * UI state for Curriculum screen.
 */
data class CurriculumUiState(
    val selectedTab: CurriculumTab = CurriculumTab.SERVER,
    val serverCurricula: List<CurriculumSummary> = emptyList(),
    val localCurricula: List<Curriculum> = emptyList(),
    val downloadedCurriculumIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadProgress: Map<String, Float> = emptyMap(),
    val connectionState: ConnectionState = ConnectionState.Unknown,
) {
    /**
     * Check if a curriculum is already downloaded.
     */
    fun isDownloaded(curriculumId: String): Boolean = curriculumId in downloadedCurriculumIds

    /**
     * Check if a curriculum is currently downloading.
     */
    fun isDownloading(curriculumId: String): Boolean = curriculumId in downloadProgress

    /**
     * Get download progress for a curriculum (0.0 to 1.0).
     */
    fun getProgress(curriculumId: String): Float = downloadProgress[curriculumId] ?: 0f

    /**
     * Check if server is connected.
     */
    val isServerConnected: Boolean
        get() = connectionState is ConnectionState.Connected

    /**
     * Check if server connection failed.
     */
    val isServerFailed: Boolean
        get() = connectionState is ConnectionState.Failed

    /**
     * Get server connection error message.
     */
    val serverErrorMessage: String?
        get() = (connectionState as? ConnectionState.Failed)?.message
}
