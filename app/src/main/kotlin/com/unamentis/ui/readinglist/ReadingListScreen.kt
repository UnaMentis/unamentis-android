package com.unamentis.ui.readinglist

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.data.local.entity.ReadingListItemEntity
import com.unamentis.data.model.ReadingListStatus
import com.unamentis.ui.LocalScrollToTopHandler
import com.unamentis.ui.Routes
import kotlinx.coroutines.launch

/**
 * Main reading list screen showing all imported documents.
 *
 * Features:
 * - Filter tabs (Active, Completed, Archived)
 * - Swipe to delete/archive
 * - Quick-listen button per item
 * - FAB for import menu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(
    onNavigateToReader: (String) -> Unit = {},
    onNavigateToPlayback: (String) -> Unit = {},
    viewModel: ReadingListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Handle scroll-to-top events
    val scrollToTopHandler = LocalScrollToTopHandler.current
    LaunchedEffect(scrollToTopHandler) {
        scrollToTopHandler.scrollToTopEvents.collect { route ->
            if (route == Routes.READING_LIST) {
                coroutineScope.launch { listState.animateScrollToItem(0) }
            }
        }
    }

    // URL import bottom sheet
    if (uiState.showUrlImportSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideUrlImportSheet() },
            sheetState = sheetState,
        ) {
            URLImportSheet(
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onImport = { url -> viewModel.importWebArticle(url) },
                onCancel = { viewModel.hideUrlImportSheet() },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_list_title)) },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { viewModel.toggleImportMenu() },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Add content to reading list"
                        },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
                DropdownMenu(
                    expanded = uiState.showImportMenu,
                    onDismissRequest = { viewModel.dismissImportMenu() },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reading_list_import_url)) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        onClick = {
                            viewModel.dismissImportMenu()
                            viewModel.showUrlImportSheet()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reading_list_import_clipboard)) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentPaste, contentDescription = null)
                        },
                        onClick = { viewModel.dismissImportMenu() },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // Filter chips
            FilterRow(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) },
            )

            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                EmptyReadingListState(filter = uiState.selectedFilter)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = uiState.items,
                        key = { it.id },
                    ) { item ->
                        ReadingListItemRow(
                            item = item,
                            onTap = { onNavigateToReader(item.id) },
                            onListenTap = { onNavigateToPlayback(item.id) },
                            onDelete = { viewModel.deleteItem(item.id) },
                            onArchive = { viewModel.archiveItem(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: ReadingListFilter,
    onFilterSelected: (ReadingListFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReadingListFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        when (filter) {
                            ReadingListFilter.ACTIVE -> stringResource(R.string.reading_list_filter_active)
                            ReadingListFilter.COMPLETED -> stringResource(R.string.reading_list_filter_completed)
                            ReadingListFilter.ARCHIVED -> stringResource(R.string.reading_list_filter_archived)
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingListItemRow(
    item: ReadingListItemEntity,
    onTap: () -> Unit,
    onListenTap: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDelete()
                        true
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onArchive()
                        true
                    }
                    else -> false
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg",
            )
            val icon =
                when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Archive
                    else -> Icons.Default.Archive
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                contentAlignment =
                    if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    },
            ) {
                Icon(icon, contentDescription = null)
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onTap)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Source type icon
                Icon(
                    imageVector = sourceTypeIcon(item.sourceType),
                    contentDescription = item.sourceType,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title and metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (item.author != null) {
                        Text(
                            text = item.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Progress bar for in-progress items
                    val status = ReadingListStatus.fromRawValue(item.status)
                    if (status == ReadingListStatus.IN_PROGRESS && item.percentComplete > 0f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { item.percentComplete },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Listen button
                IconButton(
                    onClick = onListenTap,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Listen to ${item.title}"
                        },
                ) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // Status indicator for completed items
                val status = ReadingListStatus.fromRawValue(item.status)
                if (status == ReadingListStatus.COMPLETED) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.reading_list_status_completed),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyReadingListState(filter: ReadingListFilter) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Article,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text =
                    when (filter) {
                        ReadingListFilter.ACTIVE -> stringResource(R.string.reading_list_empty)
                        ReadingListFilter.COMPLETED -> stringResource(R.string.reading_list_empty_completed)
                        ReadingListFilter.ARCHIVED -> stringResource(R.string.reading_list_empty_archived)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reading_list_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private fun sourceTypeIcon(sourceType: String): ImageVector {
    return when (sourceType.lowercase()) {
        "url", "web_article" -> Icons.Default.Language
        "pdf" -> Icons.Default.Description
        "markdown", "md" -> Icons.Default.Article
        else -> Icons.Default.Description
    }
}
