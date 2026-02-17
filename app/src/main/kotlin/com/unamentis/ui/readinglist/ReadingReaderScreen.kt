package com.unamentis.ui.readinglist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import kotlinx.coroutines.launch

/**
 * Full-text reader screen for reading list items.
 *
 * Shows the complete document as scrollable text with chunk-based
 * highlighting. Includes a floating control bar at the bottom for
 * playback controls and bookmarks.
 *
 * Maps to iOS ReadingReaderView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingReaderScreen(
    itemId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPlayback: (String) -> Unit = {},
    viewModel: ReadingReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }

    // Auto-scroll to current chunk when playing
    LaunchedEffect(uiState.currentPlayingChunkIndex) {
        if (uiState.isPlaying && uiState.currentPlayingChunkIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.currentPlayingChunkIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopPlayback()
                            onNavigateBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.done),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.chunks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reading_list_error_no_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Main content - chunk list
                // Space for floating control bar at bottom
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp),
                ) {
                    // Header section
                    item {
                        ReaderHeader(
                            title = uiState.title,
                            author = uiState.author,
                            chunkCount = uiState.chunks.size,
                        )
                    }

                    // Chunk content
                    itemsIndexed(uiState.chunks) { index, chunk ->
                        ChunkTextView(
                            text = chunk.text,
                            index = index,
                            isCurrentChunk = index == uiState.currentPlayingChunkIndex && uiState.isPlaying,
                            isBookmarked = uiState.bookmarkedChunkIndices.contains(index),
                            onTap = { viewModel.setScrollPosition(index) },
                        )
                    }

                    // Bottom spacer
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Floating bottom control bar
                ReaderControlBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isPlaying = uiState.isPlaying,
                    isPaused = uiState.isPaused,
                    currentChunkIndex = uiState.currentPlayingChunkIndex.coerceAtLeast(0),
                    totalChunks = uiState.chunks.size,
                    onPlay = { viewModel.startPlayback() },
                    onPause = { viewModel.pausePlayback() },
                    onResume = { viewModel.resumePlayback() },
                    onStop = { viewModel.stopPlayback() },
                    onSkipForward = { viewModel.skipForward() },
                    onSkipBackward = { viewModel.skipBackward() },
                    onBookmark = { viewModel.toggleBookmark() },
                    isBookmarked =
                        uiState.bookmarkedChunkIndices.contains(
                            uiState.currentPlayingChunkIndex.coerceAtLeast(0),
                        ),
                    onNavigateToPlayback = { onNavigateToPlayback(itemId) },
                )
            }
        }
    }
}

@Composable
private fun ReaderHeader(
    title: String,
    author: String?,
    chunkCount: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )

        if (author != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.reading_list_chunk_count, chunkCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
    }
}

@Composable
private fun ChunkTextView(
    text: String,
    index: Int,
    isCurrentChunk: Boolean,
    isBookmarked: Boolean,
    onTap: () -> Unit,
) {
    val chunkDescription = stringResource(R.string.reading_list_chunk_of, index + 1)
    val backgroundColor =
        when {
            isCurrentChunk -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics { contentDescription = chunkDescription },
    ) {
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
            )

            if (isBookmarked) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = stringResource(R.string.reading_list_bookmark_added),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ReaderControlBar(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isPaused: Boolean,
    currentChunkIndex: Int,
    totalChunks: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onBookmark: () -> Unit,
    isBookmarked: Boolean,
    onNavigateToPlayback: () -> Unit,
) {
    val progress =
        if (totalChunks > 0) {
            (currentChunkIndex + 1).toFloat() / totalChunks.toFloat()
        } else {
            0f
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color =
                    if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Section info
            Text(
                text =
                    stringResource(
                        R.string.reading_list_section_of,
                        currentChunkIndex + 1,
                        totalChunks,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPlaying || isPaused) {
                    IconButton(onClick = onSkipBackward) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.reading_list_skip_back),
                        )
                    }

                    IconButton(
                        onClick = if (isPlaying) onPause else onResume,
                        modifier =
                            Modifier.semantics {
                                contentDescription = if (isPlaying) "Pause" else "Resume"
                            },
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = onSkipForward) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.reading_list_skip_forward),
                        )
                    }

                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.stop),
                        )
                    }
                } else {
                    // Not playing - show Listen button and bookmark
                    IconButton(
                        onClick = onNavigateToPlayback,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Listen to article"
                            },
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = onPlay) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.reading_list_listen_from_here),
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = onBookmark) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.reading_list_bookmark_action),
                            tint =
                                if (isBookmarked) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}
