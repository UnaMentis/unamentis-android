package com.unamentis.ui.readinglist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.services.readingplayback.ReadingChunkData
import com.unamentis.services.readingplayback.ReadingPlaybackService
import com.unamentis.services.readingplayback.ReadingPlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the reading playback screen.
 */
data class ReadingPlaybackUiState(
    val title: String = "",
    val author: String? = null,
    val currentChunkText: String = "",
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val progress: Float = 0f,
    val isLoading: Boolean = true,
    val isBookmarked: Boolean = false,
    val playbackState: ReadingPlaybackState = ReadingPlaybackState.Idle,
    val errorMessage: String? = null,
)

/**
 * ViewModel for the reading playback screen.
 *
 * Manages audio playback state, chunk display, and bookmark operations.
 */
@HiltViewModel
class ReadingPlaybackViewModel
    @Inject
    constructor(
        private val readingListManager: ReadingListManager,
        private val playbackService: ReadingPlaybackService,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ReadingPlaybackVM"
        }

        private val _uiState = MutableStateFlow(ReadingPlaybackUiState())
        val uiState: StateFlow<ReadingPlaybackUiState> = _uiState.asStateFlow()

        private var currentItemId: String? = null
        private var chunkDataList: List<ReadingChunkData> = emptyList()
        private var bookmarksByChunk: Map<Int, String> = emptyMap()

        init {
            observePlaybackState()
        }

        fun loadItem(itemId: String) {
            if (currentItemId == itemId && chunkDataList.isNotEmpty()) return
            currentItemId = itemId

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    val item = readingListManager.getItem(itemId)
                    val chunks = readingListManager.getChunksForItem(itemId)
                    val bookmarks = readingListManager.getBookmarksForItem(itemId).first()
                    bookmarksByChunk = bookmarks.associate { it.chunkIndex to it.id }

                    chunkDataList =
                        chunks.map { chunk ->
                            ReadingChunkData(
                                index = chunk.index,
                                text = chunk.text,
                                characterOffset = chunk.characterOffset,
                                estimatedDurationSeconds = chunk.estimatedDurationSeconds,
                            )
                        }

                    val startIndex = item?.currentChunkIndex ?: 0
                    val currentText = chunkDataList.getOrNull(startIndex)?.text ?: ""

                    _uiState.update {
                        it.copy(
                            title = item?.title ?: "",
                            author = item?.author,
                            currentChunkText = currentText,
                            currentChunkIndex = startIndex,
                            totalChunks = chunkDataList.size,
                            progress =
                                if (chunkDataList.isNotEmpty()) {
                                    (startIndex + 1).toFloat() / chunkDataList.size
                                } else {
                                    0f
                                },
                            isLoading = false,
                            isBookmarked = bookmarksByChunk.containsKey(startIndex),
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load item $itemId", e)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
            }
        }

        fun startPlayback() {
            val itemId = currentItemId ?: return
            if (chunkDataList.isEmpty()) return

            val startIndex = _uiState.value.currentChunkIndex

            viewModelScope.launch {
                try {
                    playbackService.startPlayback(itemId, chunkDataList, startIndex)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start playback", e)
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
            }
        }

        fun pausePlayback() {
            viewModelScope.launch { playbackService.pause() }
        }

        fun resumePlayback() {
            playbackService.resume()
        }

        fun stopPlayback() {
            viewModelScope.launch { playbackService.stopPlayback() }
        }

        fun skipForward() {
            viewModelScope.launch { playbackService.skipForward() }
        }

        fun skipBackward() {
            viewModelScope.launch { playbackService.skipBackward() }
        }

        fun toggleBookmark() {
            val itemId = currentItemId ?: return
            val chunkIndex = _uiState.value.currentChunkIndex

            viewModelScope.launch {
                try {
                    val existingBookmarkId = bookmarksByChunk[chunkIndex]
                    if (existingBookmarkId != null) {
                        readingListManager.removeBookmark(existingBookmarkId)
                        bookmarksByChunk = bookmarksByChunk - chunkIndex
                        _uiState.update { it.copy(isBookmarked = false) }
                    } else {
                        readingListManager.addBookmark(itemId, chunkIndex)
                        // Reload bookmarks to get the new ID
                        val bookmarks = readingListManager.getBookmarksForItem(itemId).first()
                        bookmarksByChunk = bookmarks.associate { it.chunkIndex to it.id }
                        _uiState.update { it.copy(isBookmarked = true) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle bookmark", e)
                }
            }
        }

        private fun observePlaybackState() {
            viewModelScope.launch {
                playbackService.state.collect { state ->
                    _uiState.update { it.copy(playbackState = state) }
                }
            }

            viewModelScope.launch {
                playbackService.currentChunkIndex.collect { chunkIndex ->
                    val chunkText = chunkDataList.getOrNull(chunkIndex)?.text ?: ""
                    val totalChunks = chunkDataList.size
                    val progress =
                        if (totalChunks > 0) {
                            (chunkIndex + 1).toFloat() / totalChunks
                        } else {
                            0f
                        }
                    _uiState.update {
                        it.copy(
                            currentChunkIndex = chunkIndex,
                            currentChunkText = chunkText,
                            progress = progress,
                            isBookmarked = bookmarksByChunk.containsKey(chunkIndex),
                        )
                    }
                }
            }
        }
    }
