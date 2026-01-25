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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.core.export.ExportResult
import com.unamentis.data.model.Session
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.ui.LocalScrollToTopHandler
import com.unamentis.ui.Routes
import com.unamentis.ui.components.BrandLogo
import com.unamentis.ui.components.ExportBottomSheet
import com.unamentis.ui.components.Size
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
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
                    title = {
                        Text(
                            stringResource(R.string.session_details_title),
                            style = IOSTypography.headline,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
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
                                        stringResource(R.string.unstar_session)
                                    } else {
                                        stringResource(R.string.star_session)
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
                            Icon(Icons.Default.IosShare, contentDescription = stringResource(R.string.export))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
                            placeholder = { Text(stringResource(R.string.search_sessions_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close_search),
                            )
                        }
                    },
                )
            } else {
                // List view top bar
                TopAppBar(
                    navigationIcon = {
                        BrandLogo(
                            size = Size.Compact,
                            modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                        )
                    },
                    title = { Text(stringResource(R.string.history_title), style = IOSTypography.headline) },
                    actions = {
                        // Search button
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                        }
                        // Sort button with dropdown
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
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
                                Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter))
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
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = {
                Text(stringResource(R.string.delete_session_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(uiState.selectedSession!!.id)
                        showDeleteDialog = false
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
 * Session list view with date grouping.
 *
 * Matches iOS grouped list style.
 */
@Composable
private fun SessionListView(
    sessions: List<Session>,
    onSessionClick: (Session) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) {
        // Empty state matching iOS ContentUnavailableView
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            HistoryEmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.no_sessions_yet),
                description = stringResource(R.string.start_session_hint),
            )
        }
    } else {
        // Group sessions by date
        val groupedSessions = groupSessionsByDate(sessions)

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    vertical = Dimensions.ScreenVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            groupedSessions.forEach { (dateKey, dateSessions) ->
                // Date section header - convert key to localized string
                item(key = "header_$dateKey") {
                    val dateLabel =
                        when (dateKey) {
                            "today" -> stringResource(R.string.history_today)
                            "yesterday" -> stringResource(R.string.history_yesterday)
                            else -> dateKey
                        }
                    DateSectionHeader(
                        title = dateLabel,
                        modifier = Modifier.padding(top = Dimensions.SpacingMedium),
                    )
                }

                // Sessions for this date
                items(
                    items = dateSessions,
                    key = { it.id },
                ) { session ->
                    SessionRow(
                        session = session,
                        onClick = { onSessionClick(session) },
                        modifier =
                            Modifier.padding(
                                horizontal = Dimensions.ScreenHorizontalPadding,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Session detail view with transcript.
 *
 * Matches iOS SessionDetailView with cards and transcript.
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
                text = stringResource(R.string.transcript_label),
                style = IOSTypography.headline,
            )
        }

        // Transcript entries
        if (transcript.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_transcript),
                    style = IOSTypography.body,
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
 * Transcript entry card.
 *
 * Matches iOS transcript bubble styling.
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
            text = if (isUser) stringResource(R.string.user_label) else stringResource(R.string.ai_tutor_label),
            style = IOSTypography.caption2,
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
                    style = IOSTypography.body,
                )
                Text(
                    text = formatTime(entry.timestamp),
                    style = IOSTypography.caption2,
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
                    text = stringResource(R.string.filter_sessions_title),
                    style = IOSTypography.title2,
                )
                if (filterState.hasActiveFilters()) {
                    TextButton(onClick = onClearFilters) {
                        Text(stringResource(R.string.clear_all))
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
                    Text(stringResource(R.string.starred_only_label))
                }
                Checkbox(
                    checked = filterState.starredOnly,
                    onCheckedChange = { onStarredOnlyChanged(it) },
                )
            }

            HorizontalDivider()

            // Date range filters
            Text(
                text = stringResource(R.string.date_range_label),
                style = IOSTypography.subheadline,
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
                            filterState.startDate?.let { formatDate(it) } ?: stringResource(R.string.start_date_label),
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
                                        contentDescription = stringResource(R.string.clear_start_date),
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
                            filterState.endDate?.let { formatDate(it) } ?: stringResource(R.string.end_date_label),
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
                                        contentDescription = stringResource(R.string.clear_end_date),
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
                        text = stringResource(R.string.min_duration_label),
                        style = IOSTypography.subheadline,
                    )
                    Text(
                        text =
                            if (durationSliderValue > 0) {
                                stringResource(
                                    R.string.min_duration_value,
                                    durationSliderValue.toInt(),
                                )
                            } else {
                                stringResource(R.string.min_duration_any)
                            },
                        style = IOSTypography.body,
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
                        text = stringResource(R.string.min_turns_label),
                        style = IOSTypography.subheadline,
                    )
                    Text(
                        text =
                            if (turnsSliderValue > 0) {
                                stringResource(
                                    R.string.min_turns_value,
                                    turnsSliderValue.toInt(),
                                )
                            } else {
                                stringResource(R.string.min_turns_any)
                            },
                        style = IOSTypography.body,
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
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
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
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
