package com.unamentis.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.core.export.ExportResult
import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.ui.LocalScrollToTopHandler
import com.unamentis.ui.Routes
import com.unamentis.ui.components.ExportBottomSheet
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.theme.Dimensions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen - Session history and playback.
 *
 * Features:
 * - Session list with timestamps
 * - Session detail view with full transcript
 * - Export functionality (JSON, text)
 * - Metrics summary per session
 * - Delete session action
 *
 * Layout:
 * - Session list (or empty state)
 * - Detail view with transcript
 * - Export and delete actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    initialSessionId: String? = null,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showExportSheet by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    // List states for scroll-to-top
    val listState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Handle scroll-to-top events
    val scrollToTopHandler = LocalScrollToTopHandler.current
    LaunchedEffect(scrollToTopHandler) {
        scrollToTopHandler.scrollToTopEvents.collect { route ->
            if (route == Routes.HISTORY) {
                coroutineScope.launch {
                    if (uiState.selectedSession != null) {
                        detailListState.animateScrollToItem(0)
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            }
        }
    }

    // Handle deep link to specific session
    LaunchedEffect(initialSessionId, uiState.sessions) {
        if (initialSessionId != null && uiState.selectedSession == null && uiState.sessions.isNotEmpty()) {
            viewModel.selectSession(initialSessionId)
        }
    }

    Scaffold(
        topBar = {
            if (uiState.selectedSession != null) {
                // Detail view top bar
                TopAppBar(
                    title = { Text("Session Details") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Star/Unstar button
                        IconButton(
                            onClick = {
                                viewModel.toggleStarred(uiState.selectedSession!!.id)
                            },
                        ) {
                            Icon(
                                imageVector =
                                    if (uiState.selectedSession!!.isStarred) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.StarOutline
                                    },
                                contentDescription =
                                    if (uiState.selectedSession!!.isStarred) {
                                        "Unstar session"
                                    } else {
                                        "Star session"
                                    },
                                tint =
                                    if (uiState.selectedSession!!.isStarred) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                        IconButton(onClick = { showExportSheet = true }) {
                            Icon(Icons.Default.IosShare, contentDescription = "Export")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                )
            } else if (showSearchBar) {
                // Search bar mode
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search sessions...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearchBar = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                // List view top bar
                TopAppBar(
                    title = { Text("History") },
                    actions = {
                        // Search button
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        // Sort button with dropdown
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SessionSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = uiState.sortOrder == order,
                                                    onClick = null,
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(order.displayName)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        // Filter button with badge if filters active
                        Box {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                            if (uiState.isFiltering) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        if (uiState.selectedSession != null) {
            // Detail view
            SessionDetailView(
                session = uiState.selectedSession!!,
                transcript = uiState.transcript,
                listState = detailListState,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            // List view
            SessionListView(
                sessions = uiState.sessions,
                onSessionClick = { viewModel.selectSession(it.id) },
                listState = listState,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }

    // Export bottom sheet
    if (showExportSheet && uiState.selectedSession != null) {
        ExportBottomSheet(
            onDismiss = {
                showExportSheet = false
                exportResult = null
            },
            onExport = { format ->
                exportResult = viewModel.exportSession(format)
            },
            exportResult = exportResult,
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && uiState.selectedSession != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session?") },
            text = {
                Text(
                    "This will permanently delete the session and its transcript. " +
                        "This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(uiState.selectedSession!!.id)
                        showDeleteDialog = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        HistoryFilterSheet(
            filterState = uiState.filterState,
            onDismiss = { showFilterSheet = false },
            onStarredOnlyChanged = { viewModel.setStarredOnly(it) },
            onDateRangeChanged = { start, end -> viewModel.setDateRange(start, end) },
            onMinDurationChanged = { viewModel.setMinDuration(it) },
            onMinTurnsChanged = { viewModel.setMinTurns(it) },
            onClearFilters = { viewModel.clearFilters() },
        )
    }
}

/**
 * Session list view.
 */
@Composable
private fun SessionListView(
    sessions: List<Session>,
    onSessionClick: (Session) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "No sessions yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Start a session to see it here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.ScreenVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            items(
                items = sessions,
                key = { it.id },
            ) { session ->
                SessionCard(
                    session = session,
                    onClick = { onSessionClick(session) },
                )
            }
        }
    }
}

/**
 * Individual session card in list.
 */
@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit,
) {
    IOSCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            // Title, star, and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                ) {
                    Text(
                        text = session.curriculumId?.let { "Curriculum Session" } ?: "Free Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (session.isStarred) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Text(
                    text = formatDate(session.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Metadata
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
            ) {
                MetadataChip(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    text = "${session.turnCount} turns",
                )

                session.endTime?.let { endTime ->
                    val durationMinutes = ((endTime - session.startTime) / 1000 / 60).toInt()
                    MetadataChip(
                        icon = Icons.Default.Timer,
                        text = "${durationMinutes}min",
                    )
                }
            }

            // Topic if present
            session.topicId?.let {
                Text(
                    text = "Topic: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Metadata chip component.
 */
@Composable
private fun MetadataChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.IconSizeSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Session detail view with transcript.
 */
@Composable
private fun SessionDetailView(
    session: Session,
    transcript: List<TranscriptEntry>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                horizontal = Dimensions.ScreenHorizontalPadding,
                vertical = Dimensions.ScreenVerticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
    ) {
        // Session info card
        item {
            SessionInfoCard(session = session)
        }

        // Transcript header
        item {
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Transcript entries
        if (transcript.isEmpty()) {
            item {
                Text(
                    text = "No transcript available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimensions.SpacingXXLarge),
                )
            }
        } else {
            items(
                items = transcript,
                key = { it.id },
            ) { entry ->
                TranscriptEntryCard(entry = entry)
            }
        }
    }
}

/**
 * Session info summary card.
 */
@Composable
private fun SessionInfoCard(session: Session) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Text(
                text = "Session Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            InfoRow(label = "Session ID", value = session.id.take(8) + "...")
            session.curriculumId?.let {
                InfoRow(label = "Curriculum ID", value = it)
            }
            session.topicId?.let {
                InfoRow(label = "Topic ID", value = it)
            }
            InfoRow(
                label = "Start Time",
                value = formatDateTime(session.startTime),
            )
            session.endTime?.let {
                InfoRow(
                    label = "End Time",
                    value = formatDateTime(it),
                )
                val durationMinutes = ((it - session.startTime) / 1000 / 60).toInt()
                InfoRow(label = "Duration", value = "$durationMinutes minutes")
            }
            InfoRow(label = "Total Turns", value = session.turnCount.toString())
        }
    }
}

/**
 * Info row component.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Transcript entry card.
 */
@Composable
private fun TranscriptEntryCard(entry: TranscriptEntry) {
    val isUser = entry.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        // Role label
        Text(
            text = if (isUser) "You" else "AI Tutor",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimensions.SpacingMedium, vertical = Dimensions.SpacingXSmall),
        )

        // Message card
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(Dimensions.BubbleCornerRadius),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                ),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.BubblePadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = formatTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Format timestamp as date.
 */
private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Format timestamp as date and time.
 */
private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Format timestamp as time only.
 */
private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Filter bottom sheet for history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterSheet(
    filterState: SessionFilterState,
    onDismiss: () -> Unit,
    onStarredOnlyChanged: (Boolean) -> Unit,
    onDateRangeChanged: (Long?, Long?) -> Unit,
    onMinDurationChanged: (Int?) -> Unit,
    onMinTurnsChanged: (Int?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var durationSliderValue by remember { mutableFloatStateOf(filterState.minDurationMinutes?.toFloat() ?: 0f) }
    var turnsSliderValue by remember { mutableFloatStateOf(filterState.minTurns?.toFloat() ?: 0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                    .padding(bottom = Dimensions.SpacingXXLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filter Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (filterState.hasActiveFilters()) {
                    TextButton(onClick = onClearFilters) {
                        Text("Clear All")
                    }
                }
            }

            HorizontalDivider()

            // Starred only toggle
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onStarredOnlyChanged(!filterState.starredOnly) }
                        .padding(vertical = Dimensions.SpacingSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Starred only")
                }
                Checkbox(
                    checked = filterState.starredOnly,
                    onCheckedChange = { onStarredOnlyChanged(it) },
                )
            }

            HorizontalDivider()

            // Date range filters
            Text(
                text = "Date Range",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                // Start date chip
                FilterChip(
                    selected = filterState.startDate != null,
                    onClick = { showStartDatePicker = true },
                    label = {
                        Text(
                            filterState.startDate?.let { formatDate(it) } ?: "Start Date",
                        )
                    },
                    modifier = Modifier.weight(1f),
                    trailingIcon =
                        if (filterState.startDate != null) {
                            {
                                IconButton(
                                    onClick = { onDateRangeChanged(null, filterState.endDate) },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear start date",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )

                // End date chip
                FilterChip(
                    selected = filterState.endDate != null,
                    onClick = { showEndDatePicker = true },
                    label = {
                        Text(
                            filterState.endDate?.let { formatDate(it) } ?: "End Date",
                        )
                    },
                    modifier = Modifier.weight(1f),
                    trailingIcon =
                        if (filterState.endDate != null) {
                            {
                                IconButton(
                                    onClick = { onDateRangeChanged(filterState.startDate, null) },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear end date",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            }

            HorizontalDivider()

            // Minimum duration slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Minimum Duration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (durationSliderValue > 0) "${durationSliderValue.toInt()} min" else "Any",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                Slider(
                    value = durationSliderValue,
                    onValueChange = { durationSliderValue = it },
                    onValueChangeFinished = {
                        onMinDurationChanged(if (durationSliderValue > 0) durationSliderValue.toInt() else null)
                    },
                    valueRange = 0f..120f,
                    steps = 11,
                )
            }

            HorizontalDivider()

            // Minimum turns slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Minimum Turns",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (turnsSliderValue > 0) "${turnsSliderValue.toInt()} turns" else "Any",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                Slider(
                    value = turnsSliderValue,
                    onValueChange = { turnsSliderValue = it },
                    onValueChangeFinished = {
                        onMinTurnsChanged(if (turnsSliderValue > 0) turnsSliderValue.toInt() else null)
                    },
                    valueRange = 0f..100f,
                    steps = 9,
                )
            }
        }
    }

    // Start date picker dialog
    if (showStartDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = filterState.startDate,
            )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            onDateRangeChanged(selectedDate, filterState.endDate)
                        }
                        showStartDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // End date picker dialog
    if (showEndDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = filterState.endDate,
            )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDate ->
                            onDateRangeChanged(filterState.startDate, selectedDate)
                        }
                        showEndDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
