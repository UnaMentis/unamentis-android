package com.unamentis.ui.curriculum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Topic
import com.unamentis.data.remote.CurriculumSummary
import com.unamentis.data.repository.ConnectionState
import com.unamentis.ui.components.IOSCard
import com.unamentis.ui.components.IOSProgressBar
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.util.safeProgress

/**
 * Curriculum screen - Browse and download curricula.
 *
 * Features:
 * - Tab selection (Server/Local)
 * - Search and filtering
 * - Download management with progress
 * - Curriculum detail view with topics
 * - Server connection status display
 * - Adaptive layout for phone vs tablet
 *
 * Server connectivity:
 * - Connects to management console on port 8766 (same as iOS app)
 * - Shows connection status in UI
 * - Gracefully handles server unavailability
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumScreen(
    initialCurriculumId: String? = null,
    viewModel: CurriculumViewModel = hiltViewModel(),
    onNavigateToSession: (String, String?) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCurriculum by remember { mutableStateOf<Curriculum?>(null) }

    // Handle deep link to specific curriculum
    LaunchedEffect(initialCurriculumId, uiState.localCurricula) {
        if (initialCurriculumId != null && selectedCurriculum == null) {
            val curriculum = uiState.localCurricula.find { it.id == initialCurriculumId }
            if (curriculum != null) {
                selectedCurriculum = curriculum
            }
        }
    }

    Scaffold(
        topBar = {
            CurriculumTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Tab selection
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == CurriculumTab.SERVER,
                    onClick = { viewModel.selectTab(CurriculumTab.SERVER) },
                    text = { Text(stringResource(R.string.curriculum_server)) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = "Server curricula") },
                )
                Tab(
                    selected = uiState.selectedTab == CurriculumTab.LOCAL,
                    onClick = { viewModel.selectTab(CurriculumTab.LOCAL) },
                    text = { Text(stringResource(R.string.curriculum_downloaded)) },
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Local curricula") },
                )
            }

            // Connection status banner (for server tab)
            if (uiState.selectedTab == CurriculumTab.SERVER) {
                ConnectionStatusBanner(
                    connectionState = uiState.connectionState,
                    onRetry = { viewModel.refresh() },
                )
            }

            // Error display
            if (uiState.error != null) {
                ErrorBanner(
                    message = uiState.error!!,
                    onDismiss = { viewModel.clearError() },
                )
            }

            // Content
            if (selectedCurriculum != null) {
                // Detail view (for local curricula)
                CurriculumDetailView(
                    curriculum = selectedCurriculum!!,
                    onBack = { selectedCurriculum = null },
                    onStartSession = { topicId ->
                        onNavigateToSession(selectedCurriculum!!.id, topicId)
                    },
                )
            } else {
                // List view - different for server vs local
                when (uiState.selectedTab) {
                    CurriculumTab.SERVER -> {
                        ServerCurriculaListView(
                            uiState = uiState,
                            onDownload = { viewModel.downloadCurriculum(it.id) },
                            onRefresh = { viewModel.refresh() },
                        )
                    }
                    CurriculumTab.LOCAL -> {
                        LocalCurriculaListView(
                            uiState = uiState,
                            onCurriculumClick = { selectedCurriculum = it },
                            onDelete = { viewModel.deleteCurriculum(it.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top app bar with search field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurriculumTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search curricula...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    )
}

/**
 * Connection status banner for server tab.
 */
@Composable
private fun ConnectionStatusBanner(
    connectionState: ConnectionState,
    onRetry: () -> Unit,
) {
    when (connectionState) {
        is ConnectionState.Checking -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Connecting to server...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        is ConnectionState.Connected -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Connected to management console",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        is ConnectionState.Failed -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
            }
        }
        is ConnectionState.Unknown -> {
            // Don't show anything
        }
    }
}

/**
 * Error banner display.
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

/**
 * Server curricula list view (shows CurriculumSummary items).
 */
@Composable
private fun ServerCurriculaListView(
    uiState: CurriculumUiState,
    onDownload: (CurriculumSummary) -> Unit,
    onRefresh: () -> Unit,
) {
    if (uiState.isLoading && uiState.serverCurricula.isEmpty()) {
        // Loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (uiState.serverCurricula.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text =
                        if (uiState.isServerFailed) {
                            "Server not available"
                        } else {
                            "No curricula available on server"
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.isServerFailed) {
                    Text(
                        text = "Start the management console on port 8766",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
        }
    } else {
        // Server curricula list - iOS-style spacing
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            items(
                items = uiState.serverCurricula,
                key = { it.id },
            ) { summary ->
                ServerCurriculumCard(
                    summary = summary,
                    isDownloaded = uiState.isDownloaded(summary.id),
                    isDownloading = uiState.isDownloading(summary.id),
                    downloadProgress = uiState.getProgress(summary.id),
                    onDownload = { onDownload(summary) },
                )
            }
        }
    }
}

/**
 * Server curriculum card (from CurriculumSummary).
 * Uses iOS-style card with 12dp corner radius and 16dp padding.
 */
@Composable
private fun ServerCurriculumCard(
    summary: CurriculumSummary,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${summary.topicCount} topics • v${summary.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Action button
                when {
                    isDownloading -> {
                        CircularProgressIndicator(
                            progress = { safeProgress(downloadProgress) },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    isDownloaded -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    else -> {
                        IconButton(onClick = onDownload) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                            )
                        }
                    }
                }
            }

            // Description
            if (summary.description.isNotBlank()) {
                Text(
                    text = summary.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            // Download progress bar - iOS-style 4pt height
            if (isDownloading) {
                IOSProgressBar(
                    progress = downloadProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Local curricula list view (shows full Curriculum items).
 */
@Composable
private fun LocalCurriculaListView(
    uiState: CurriculumUiState,
    onCurriculumClick: (Curriculum) -> Unit,
    onDelete: (Curriculum) -> Unit,
) {
    if (uiState.localCurricula.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "No downloaded curricula",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Download curricula from the Server tab",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // Local curricula list - iOS-style spacing
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            items(
                items = uiState.localCurricula,
                key = { it.id },
            ) { curriculum ->
                LocalCurriculumCard(
                    curriculum = curriculum,
                    onClick = { onCurriculumClick(curriculum) },
                    onDelete = { onDelete(curriculum) },
                )
            }
        }
    }
}

/**
 * Local curriculum card (full Curriculum with click to view details).
 * Uses iOS-style card with 12dp corner radius.
 */
@Composable
private fun LocalCurriculumCard(
    curriculum: Curriculum,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    IOSCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curriculum.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${curriculum.topics.size} topics • v${curriculum.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Curriculum detail view showing topics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurriculumDetailView(
    curriculum: Curriculum,
    onBack: () -> Unit,
    onStartSession: (String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Detail top bar
        TopAppBar(
            title = { Text(curriculum.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        // Topic list - iOS-style spacing
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Curriculum metadata
            item {
                IOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    ) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${curriculum.topics.size} topics",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Version ${curriculum.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Topics header
            item {
                Text(
                    text = "Topics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Topic cards
            items(
                items = curriculum.topics,
                key = { it.id },
            ) { topic ->
                TopicCard(
                    topic = topic,
                    onStartSession = { onStartSession(topic.id) },
                )
            }
        }
    }
}

/**
 * Individual topic card.
 * Uses iOS-style card with 12dp corner radius.
 */
@Composable
private fun TopicCard(
    topic: Topic,
    onStartSession: () -> Unit,
) {
    IOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onStartSession) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }
            }

            // Learning objectives
            if (topic.learningObjectives.isNotEmpty()) {
                Text(
                    text = "Learning Objectives:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                topic.learningObjectives.take(3).forEach { objective ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = objective,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (topic.learningObjectives.size > 3) {
                    Text(
                        text = "+${topic.learningObjectives.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            // Segment count
            Text(
                text = "${topic.transcript.size} segments",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
