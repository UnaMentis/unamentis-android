package com.unamentis.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
            } else {
                // List view top bar
                TopAppBar(
                    title = { Text("History") },
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title, star, and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    modifier = Modifier.padding(vertical = 32.dp),
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // Message card
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
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
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
