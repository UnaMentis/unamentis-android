package com.unamentis.ui.curriculum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.data.model.Curriculum
import com.unamentis.data.repository.CurriculumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
         * Loading state.
         */
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        /**
         * Error state.
         */
        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        /**
         * Download progress by curriculum ID (0.0 to 1.0).
         */
        private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
        val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

        /**
         * Server curricula list.
         */
        private val serverCurricula: StateFlow<List<Curriculum>> =
            curriculumRepository
                .getServerCurricula()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Local curricula list.
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
         * Using nested combines since Kotlin Flow only supports up to 5 arguments.
         */
        val uiState: StateFlow<CurriculumUiState> =
            combine(
                combine(selectedTab, serverCurricula, localCurricula) { tab, server, local ->
                    Triple(tab, server, local)
                },
                combine(searchQuery, isLoading, error, downloadProgress) { query, loading, errorMsg, progress ->
                    CurriculumUiStatePartial(query, loading, errorMsg, progress)
                },
            ) { (tab, server, local), partial ->
                val displayedCurricula =
                    when (tab) {
                        CurriculumTab.SERVER -> server
                        CurriculumTab.LOCAL -> local
                    }.filter { curriculum ->
                        if (partial.searchQuery.isBlank()) {
                            true
                        } else {
                            curriculum.title.contains(partial.searchQuery, ignoreCase = true) ||
                                curriculum.topics.any { it.title.contains(partial.searchQuery, ignoreCase = true) }
                        }
                    }

                CurriculumUiState(
                    selectedTab = tab,
                    curricula = displayedCurricula,
                    searchQuery = partial.searchQuery,
                    isLoading = partial.isLoading,
                    error = partial.error,
                    downloadProgress = partial.downloadProgress,
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
         * Refresh curriculum lists.
         */
        fun refresh() {
            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                try {
                    curriculumRepository.refreshCurricula()
                } catch (e: Exception) {
                    _error.value = "Failed to refresh: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Download a curriculum from server.
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
                    _error.value = "Download failed: ${e.message}"
                    _downloadProgress.value = _downloadProgress.value - curriculumId
                }
            }
        }

        /**
         * Delete a local curriculum.
         */
        fun deleteCurriculum(curriculumId: String) {
            viewModelScope.launch {
                try {
                    curriculumRepository.deleteCurriculum(curriculumId)
                } catch (e: Exception) {
                    _error.value = "Delete failed: ${e.message}"
                }
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
            _error.value = null
        }

        init {
            // Auto-refresh on initialization
            refresh()
        }
    }

/**
 * Curriculum tab selection.
 */
enum class CurriculumTab {
    SERVER,
    LOCAL,
}

/**
 * UI state for Curriculum screen.
 */
data class CurriculumUiState(
    val selectedTab: CurriculumTab = CurriculumTab.SERVER,
    val curricula: List<Curriculum> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadProgress: Map<String, Float> = emptyMap(),
)
