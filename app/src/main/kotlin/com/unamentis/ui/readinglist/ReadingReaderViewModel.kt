package com.unamentis.ui.readinglist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.data.local.entity.ReadingChunkEntity
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
 * UI state for the reading reader screen.
 */
data class ReadingReaderUiState(
    val title: String = "",
    val author: String? = null,
    val sourceType: String = "text",
    val chunks: List<ReadingChunkEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPlayingChunkIndex: Int = -1,
    val scrollPosition: Int = 0,
    val bookmarkedChunkIndices: Set<Int> = emptySet(),
    val errorMessage: String? = null,
)

/**
 * ViewModel for the reading reader screen.
 *
 * Manages document display, playback state observation, and bookmark operations.
 */
@HiltViewModel
class ReadingReaderViewModel
    @Inject
    constructor(
        private val readingListManager: ReadingListManager,
        private val playbackService: ReadingPlaybackService,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ReadingReaderVM"
        }

        private val _uiState = MutableStateFlow(ReadingReaderUiState())
        val uiState: StateFlow<ReadingReaderUiState> = _uiState.asStateFlow()

        private var currentItemId: String? = null

        /** Map from chunk index to bookmark ID for quick removal. */
        private var bookmarksByChunk: Map<Int, String> = emptyMap()

        init {
            observePlaybackState()
        }

        fun loadItem(itemId: String) {
            if (currentItemId == itemId && _uiState.value.chunks.isNotEmpty()) return
            currentItemId = itemId

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    val item = readingListManager.getItem(itemId)
                    val chunks = readingListManager.getChunksForItem(itemId)
                    val bookmarks = readingListManager.getBookmarksForItem(itemId).first()
                    bookmarksByChunk = bookmarks.associate { it.chunkIndex to it.id }
                    val bookmarkedIndices = bookmarksByChunk.keys

                    _uiState.update {
                        it.copy(
                            title = item?.title ?: "",
                            author = item?.author,
                            sourceType = item?.sourceType ?: "text",
                            chunks = chunks,
                            isLoading = false,
                            scrollPosition = item?.currentChunkIndex ?: 0,
                            bookmarkedChunkIndices = bookmarkedIndices,
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

        fun setScrollPosition(index: Int) {
            _uiState.update { it.copy(scrollPosition = index) }
        }

        fun startPlayback() {
            val itemId = currentItemId ?: return
            val chunks = _uiState.value.chunks
            if (chunks.isEmpty()) return

            val startIndex = _uiState.value.scrollPosition.coerceIn(0, chunks.size - 1)

            viewModelScope.launch {
                try {
                    val chunkData =
                        chunks.map { chunk ->
                            ReadingChunkData(
                                index = chunk.index,
                                text = chunk.text,
                                characterOffset = chunk.characterOffset,
                                estimatedDurationSeconds = chunk.estimatedDurationSeconds,
                            )
                        }
                    playbackService.startPlayback(itemId, chunkData, startIndex)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start playback", e)
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
            }
        }

        fun pausePlayback() {
            viewModelScope.launch {
                playbackService.pause()
            }
        }

        fun resumePlayback() {
            playbackService.resume()
        }

        fun stopPlayback() {
            viewModelScope.launch {
                playbackService.stopPlayback()
            }
        }

        fun skipForward() {
            viewModelScope.launch {
                playbackService.skipForward()
            }
        }

        fun skipBackward() {
            viewModelScope.launch {
                playbackService.skipBackward()
            }
        }

        fun toggleBookmark() {
            val itemId = currentItemId ?: return
            val chunkIndex =
                _uiState.value.currentPlayingChunkIndex.coerceAtLeast(
                    _uiState.value.scrollPosition,
                )

            viewModelScope.launch {
                try {
                    val existingBookmarkId = bookmarksByChunk[chunkIndex]
                    if (existingBookmarkId != null) {
                        readingListManager.removeBookmark(existingBookmarkId)
                        bookmarksByChunk = bookmarksByChunk - chunkIndex
                        _uiState.update {
                            it.copy(bookmarkedChunkIndices = it.bookmarkedChunkIndices - chunkIndex)
                        }
                    } else {
                        readingListManager.addBookmark(itemId, chunkIndex)
                        // Reload bookmarks to get the new ID
                        val bookmarks = readingListManager.getBookmarksForItem(itemId).first()
                        bookmarksByChunk = bookmarks.associate { it.chunkIndex to it.id }
                        _uiState.update {
                            it.copy(bookmarkedChunkIndices = bookmarksByChunk.keys)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle bookmark", e)
                }
            }
        }

        private fun observePlaybackState() {
            viewModelScope.launch {
                playbackService.state.collect { state ->
                    _uiState.update {
                        it.copy(
                            isPlaying = state is ReadingPlaybackState.Playing,
                            isPaused = state is ReadingPlaybackState.Paused,
                        )
                    }
                }
            }

            viewModelScope.launch {
                playbackService.currentChunkIndex.collect { chunkIndex ->
                    _uiState.update { it.copy(currentPlayingChunkIndex = chunkIndex) }
                }
            }
        }
    }
