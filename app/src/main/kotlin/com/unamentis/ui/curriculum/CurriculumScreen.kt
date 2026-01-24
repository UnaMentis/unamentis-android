package com.unamentis.ui.curriculum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Topic
import com.unamentis.data.remote.CurriculumSummary
import com.unamentis.data.repository.ConnectionState
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGray
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSRed
import com.unamentis.ui.util.safeProgress
import kotlinx.coroutines.launch

/**
 * Curriculum screen - Browse and download curricula.
 *
 * Matches iOS CurriculumView with:
 * - Tab selection (Server/Local)
 * - Search and filtering
 * - Download management with progress
 * - Curriculum detail view with topics
 * - Server connection status display
 * - Bottom sheet for topic details
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
    var selectedTopic by remember { mutableStateOf<Topic?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                )
                Tab(
                    selected = uiState.selectedTab == CurriculumTab.LOCAL,
                    onClick = { viewModel.selectTab(CurriculumTab.LOCAL) },
                    text = { Text(stringResource(R.string.curriculum_downloaded)) },
                    icon = { Icon(Icons.Default.Storage, contentDescription = null) },
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
                    onTopicClick = { topic -> selectedTopic = topic },
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

    // Topic detail bottom sheet
    if (selectedTopic != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedTopic = null },
            sheetState = sheetState,
        ) {
            TopicDetailSheet(
                topic = selectedTopic!!,
                onStartLesson = {
                    scope.launch {
                        sheetState.hide()
                        val topicId = selectedTopic?.id
                        selectedTopic = null
                        selectedCurriculum?.let { curriculum ->
                            onNavigateToSession(curriculum.id, topicId)
                        }
                    }
                },
            )
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
                placeholder = { Text(stringResource(R.string.search_curricula)) },
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
                        text = stringResource(R.string.connecting_to_server),
                        style = IOSTypography.caption,
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
                        tint = iOSGreen,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.connected_to_console),
                        style = IOSTypography.caption,
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
                            tint = iOSRed,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = connectionState.message,
                            style = IOSTypography.caption,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.refresh))
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
                    tint = iOSRed,
                )
                Text(
                    text = message,
                    style = IOSTypography.subheadline,
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
        EmptyStateView(
            icon = Icons.Default.Cloud,
            title =
                if (uiState.isServerFailed) {
                    stringResource(R.string.server_not_available)
                } else {
                    stringResource(R.string.no_curricula_on_server)
                },
            subtitle =
                if (uiState.isServerFailed) {
                    stringResource(R.string.start_management_console)
                } else {
                    null
                },
            onRefresh = onRefresh,
        )
    } else {
        // Server curricula list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            items(
                items = uiState.serverCurricula,
                key = { it.id },
            ) { summary ->
                ServerCurriculumRow(
                    summary = summary,
                    isDownloaded = uiState.isDownloaded(summary.id),
                    isDownloading = uiState.isDownloading(summary.id),
                    downloadProgress = uiState.getProgress(summary.id),
                    onDownload = { onDownload(summary) },
                )
                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            start = Dimensions.CurriculumIconSize + Dimensions.SpacingMedium,
                        ),
                    color = iOSGray.copy(alpha = 0.2f),
                )
            }
        }
    }
}

/**
 * Server curriculum row matching iOS style.
 */
@Composable
private fun ServerCurriculumRow(
    summary: CurriculumSummary,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownload: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Curriculum icon
            Box(
                modifier =
                    Modifier
                        .size(Dimensions.CurriculumIconSize)
                        .clip(CircleShape)
                        .background(iOSBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = iOSBlue,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = summary.title,
                    style = IOSTypography.headline,
                )
                Text(
                    text = "${summary.topicCount} topics • v${summary.version}",
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.description.isNotBlank()) {
                    Text(
                        text = summary.description,
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            // Action button
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        progress = { safeProgress(downloadProgress) },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
                isDownloaded -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = iOSGreen,
                    )
                }
                else -> {
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download",
                            tint = iOSBlue,
                        )
                    }
                }
            }
        }

        // Download progress bar
        AnimatedVisibility(
            visible = isDownloading,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            DownloadProgressBar(
                progress = downloadProgress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 8.dp,
                            start = Dimensions.CurriculumIconSize + Dimensions.SpacingMedium,
                        ),
            )
        }
    }
}

/**
 * Download progress bar matching iOS style (8pt height, 4pt corner radius).
 */
@Composable
private fun DownloadProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(Dimensions.DownloadProgressHeight)
                .clip(RoundedCornerShape(Dimensions.DownloadProgressCornerRadius))
                .background(iOSGray.copy(alpha = 0.2f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction = safeProgress(progress))
                    .height(Dimensions.DownloadProgressHeight)
                    .clip(RoundedCornerShape(Dimensions.DownloadProgressCornerRadius))
                    .background(iOSBlue),
        )
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
        EmptyStateView(
            icon = Icons.Default.Storage,
            title = stringResource(R.string.no_downloaded_curricula),
            subtitle = stringResource(R.string.download_from_server),
            onRefresh = null,
        )
    } else {
        // Local curricula list using CurriculumRow
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            items(
                items = uiState.localCurricula,
                key = { it.id },
            ) { curriculum ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CurriculumRow(
                            curriculum = curriculum,
                            onClick = { onCurriculumClick(curriculum) },
                        )
                    }
                    IconButton(onClick = { onDelete(curriculum) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = iOSRed,
                        )
                    }
                }
                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            start = Dimensions.CurriculumIconSize + Dimensions.SpacingMedium,
                        ),
                    color = iOSGray.copy(alpha = 0.2f),
                )
            }
        }
    }
}

/**
 * Empty state view.
 */
@Composable
private fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onRefresh: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.EmptyStateIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = title,
                style = IOSTypography.headline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRefresh != null) {
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh))
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
    onTopicClick: (Topic) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Detail top bar
        TopAppBar(
            title = { Text(curriculum.title, style = IOSTypography.headline) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        // Topic list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingLarge,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            // Curriculum info section
            item {
                Column(
                    modifier = Modifier.padding(bottom = Dimensions.SpacingLarge),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                ) {
                    if (curriculum.description.isNotEmpty()) {
                        Text(
                            text = curriculum.description,
                            style = IOSTypography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.curriculum_topic_count, curriculum.topics.size),
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // Topics header
            item {
                Text(
                    text = stringResource(R.string.topics),
                    style = IOSTypography.title3,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = Dimensions.SpacingSmall),
                )
            }

            // Topic rows
            items(
                items = curriculum.topics.sortedBy { it.orderIndex },
                key = { it.id },
            ) { topic ->
                // TODO: Get status from progress tracker
                TopicRow(
                    topic = topic,
                    status = TopicStatus.NOT_STARTED,
                    onClick = { onTopicClick(topic) },
                )
                HorizontalDivider(
                    modifier =
                        Modifier.padding(
                            start = Dimensions.StatusIconSize + Dimensions.SpacingMedium,
                        ),
                    color = iOSGray.copy(alpha = 0.2f),
                )
            }
        }
    }
}

/**
 * Topic detail bottom sheet.
 */
@Composable
private fun TopicDetailSheet(
    topic: Topic,
    onStartLesson: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Dimensions.ScreenHorizontalPadding)
                .padding(bottom = Dimensions.ControlBarBottomPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing),
    ) {
        // Title
        Text(
            text = topic.title,
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )

        // Progress card
        // TODO: Get status, time, and mastery from progress tracker
        ProgressCard(
            status = TopicStatus.NOT_STARTED,
            timeSpentSeconds = 0.0,
            mastery = 0f,
        )

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            StartLessonButton(
                onClick = onStartLesson,
                modifier = Modifier.weight(1f),
            )
        }

        // Overview
        topic.description?.let { description ->
            if (description.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                ) {
                    Text(
                        text = stringResource(R.string.overview),
                        style = IOSTypography.headline,
                    )
                    Text(
                        text = description,
                        style = IOSTypography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Learning objectives
        LearningObjectivesList(objectives = topic.learningObjectives)

        // Segment count
        Text(
            text = "${topic.transcript.size} segments",
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.semantics {
                    contentDescription = "${topic.transcript.size} segments in this topic"
                },
        )
    }
}
