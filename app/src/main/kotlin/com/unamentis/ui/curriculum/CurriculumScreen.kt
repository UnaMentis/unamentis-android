package com.unamentis.ui.curriculum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Topic

/**
 * Curriculum screen - Browse and download curricula.
 *
 * Features:
 * - Tab selection (Server/Local)
 * - Search and filtering
 * - Download management with progress
 * - Curriculum detail view with topics
 * - Adaptive layout for phone vs tablet
 *
 * Layout:
 * - Top bar with search
 * - Tab row (Server/Local)
 * - Curriculum list or detail view
 * - Pull-to-refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumScreen(
    viewModel: CurriculumViewModel = hiltViewModel(),
    onNavigateToSession: (String, String?) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCurriculum by remember { mutableStateOf<Curriculum?>(null) }

    Scaffold(
        topBar = {
            CurriculumTopBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab selection
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                Tab(
                    selected = uiState.selectedTab == CurriculumTab.SERVER,
                    onClick = { viewModel.selectTab(CurriculumTab.SERVER) },
                    text = { Text("Server") },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == CurriculumTab.LOCAL,
                    onClick = { viewModel.selectTab(CurriculumTab.LOCAL) },
                    text = { Text("Local") },
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) }
                )
            }

            // Error display
            if (uiState.error != null) {
                ErrorBanner(
                    message = uiState.error!!,
                    onDismiss = { viewModel.clearError() }
                )
            }

            // Content
            if (selectedCurriculum != null) {
                // Detail view
                CurriculumDetailView(
                    curriculum = selectedCurriculum!!,
                    onBack = { selectedCurriculum = null },
                    onStartSession = { topicId ->
                        onNavigateToSession(selectedCurriculum!!.id, topicId)
                    }
                )
            } else {
                // List view
                CurriculumListView(
                    uiState = uiState,
                    onCurriculumClick = { selectedCurriculum = it },
                    onDownload = { viewModel.downloadCurriculum(it.id) },
                    onDelete = { viewModel.deleteCurriculum(it.id) },
                    onRefresh = { viewModel.refresh() }
                )
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
    onSearchQueryChange: (String) -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search curricula...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    )
}

/**
 * Error banner display.
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

/**
 * Curriculum list view with pull-to-refresh.
 */
@Composable
private fun CurriculumListView(
    uiState: CurriculumUiState,
    onCurriculumClick: (Curriculum) -> Unit,
    onDownload: (Curriculum) -> Unit,
    onDelete: (Curriculum) -> Unit,
    onRefresh: () -> Unit
) {
    if (uiState.isLoading && uiState.curricula.isEmpty()) {
        // Loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (uiState.curricula.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (uiState.selectedTab == CurriculumTab.SERVER) {
                        Icons.Default.Cloud
                    } else {
                        Icons.Default.Storage
                    },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = if (uiState.selectedTab == CurriculumTab.SERVER) {
                        "No server curricula available"
                    } else {
                        "No downloaded curricula"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.selectedTab == CurriculumTab.SERVER) {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh")
                    }
                }
            }
        }
    } else {
        // Curriculum list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = uiState.curricula,
                key = { it.id }
            ) { curriculum ->
                CurriculumCard(
                    curriculum = curriculum,
                    isLocal = uiState.selectedTab == CurriculumTab.LOCAL,
                    downloadProgress = uiState.downloadProgress[curriculum.id],
                    onClick = { onCurriculumClick(curriculum) },
                    onDownload = { onDownload(curriculum) },
                    onDelete = { onDelete(curriculum) }
                )
            }
        }
    }
}

/**
 * Individual curriculum card.
 */
@Composable
private fun CurriculumCard(
    curriculum: Curriculum,
    isLocal: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curriculum.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${curriculum.topics.size} topics • v${curriculum.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action button
                if (downloadProgress != null) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(24.dp)
                    )
                } else if (isLocal) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download"
                        )
                    }
                }
            }

            // Download progress bar
            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
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
    onStartSession: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Detail top bar
        TopAppBar(
            title = { Text(curriculum.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        // Topic list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Curriculum metadata
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${curriculum.topics.size} topics",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Version ${curriculum.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Topic cards
            items(
                items = curriculum.topics,
                key = { it.id }
            ) { topic ->
                TopicCard(
                    topic = topic,
                    onStartSession = { onStartSession(topic.id) }
                )
            }
        }
    }
}

/**
 * Individual topic card.
 */
@Composable
private fun TopicCard(
    topic: Topic,
    onStartSession: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = onStartSession) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
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
                    fontWeight = FontWeight.Bold
                )
                topic.learningObjectives.take(3).forEach { objective ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = objective,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (topic.learningObjectives.size > 3) {
                    Text(
                        text = "+${topic.learningObjectives.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            // Segment count
            Text(
                text = "${topic.transcript.size} segments",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
