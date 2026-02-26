package com.unamentis.ui.readinglist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.model.ReadingListStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filter options for the reading list.
 */
enum class ReadingListFilter {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

/**
 * UI state for the reading list screen.
 */
data class ReadingListUiState(
    val items: List<ReadingListItemEntity> = emptyList(),
    val selectedFilter: ReadingListFilter = ReadingListFilter.ACTIVE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showUrlImportSheet: Boolean = false,
    val showImportMenu: Boolean = false,
)

/**
 * ViewModel for the reading list screen.
 *
 * Manages list display, filtering, and import operations.
 */
@HiltViewModel
class ReadingListViewModel
    @Inject
    constructor(
        private val readingListManager: ReadingListManager,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ReadingListVM"
        }

        private val _uiState = MutableStateFlow(ReadingListUiState())
        val uiState: StateFlow<ReadingListUiState> = _uiState.asStateFlow()

        init {
            loadItems()
        }

        fun setFilter(filter: ReadingListFilter) {
            _uiState.update { it.copy(selectedFilter = filter) }
            loadItems()
        }

        fun loadItems() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val filter = _uiState.value.selectedFilter
                    val items =
                        when (filter) {
                            ReadingListFilter.ACTIVE -> readingListManager.getActiveItems().first()
                            ReadingListFilter.COMPLETED ->
                                readingListManager.getItemsByStatus(ReadingListStatus.COMPLETED).first()
                            ReadingListFilter.ARCHIVED ->
                                readingListManager.getItemsByStatus(ReadingListStatus.ARCHIVED).first()
                        }
                    _uiState.update { it.copy(items = items, isLoading = false) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load items", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            }
        }

        fun deleteItem(itemId: String) {
            viewModelScope.launch {
                try {
                    readingListManager.deleteItem(itemId)
                    loadItems()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete item $itemId", e)
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
            }
        }

        fun completeItem(itemId: String) {
            viewModelScope.launch {
                try {
                    readingListManager.markCompleted(itemId)
                    loadItems()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to complete item $itemId", e)
                }
            }
        }

        fun archiveItem(itemId: String) {
            viewModelScope.launch {
                try {
                    readingListManager.archiveItem(itemId)
                    loadItems()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to archive item $itemId", e)
                }
            }
        }

        fun importWebArticle(url: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    readingListManager.importWebArticle(url, url)
                    _uiState.update { it.copy(showUrlImportSheet = false, isLoading = false) }
                    loadItems()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to import URL", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            }
        }

        fun showUrlImportSheet() {
            _uiState.update { it.copy(showUrlImportSheet = true) }
        }

        fun hideUrlImportSheet() {
            _uiState.update { it.copy(showUrlImportSheet = false) }
        }

        fun toggleImportMenu() {
            _uiState.update { it.copy(showImportMenu = !it.showImportMenu) }
        }

        fun dismissImportMenu() {
            _uiState.update { it.copy(showImportMenu = false) }
        }

        fun clearError() {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }
