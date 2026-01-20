package com.unamentis.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.core.config.RecordingMode
import com.unamentis.data.model.SessionState
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.ui.components.GlassCapsule
import com.unamentis.ui.theme.AssistantBubbleDark
import com.unamentis.ui.theme.AssistantBubbleLight
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.UserBubbleDark
import com.unamentis.ui.theme.UserBubbleLight
import java.text.SimpleDateFormat
import java.util.*

/**
 * Session screen - Main voice conversation interface.
 *
 * Features:
 * - Transcript display with reverse scroll (newest at bottom)
 * - Session controls (start, pause, resume, stop)
 * - Real-time status indicator
 * - Turn counter
 * - Latency metrics display
 *
 * Layout:
 * - Transcript (LazyColumn, reverse layout)
 * - Status bar (session state, turn count)
 * - Control bar (action buttons)
 *
 * @param initialCurriculumId Optional curriculum ID from deep link to start session with
 * @param initialTopicId Optional topic ID from deep link to start session with
 */
@Composable
fun SessionScreen(
    initialCurriculumId: String? = null,
    initialTopicId: String? = null,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    // Handle deep link parameters if provided
    LaunchedEffect(initialCurriculumId, initialTopicId) {
        if (initialCurriculumId != null || initialTopicId != null) {
            viewModel.setInitialContext(initialCurriculumId, initialTopicId)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Track if we have microphone permission
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    // Permission request launcher
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasMicPermission = isGranted
            if (isGranted) {
                // Permission granted, start the session
                viewModel.startSession()
            }
        }

    // Function to handle start with permission check
    val onStartWithPermission: () -> Unit = {
        if (hasMicPermission) {
            viewModel.startSession()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            SessionTopBar(
                sessionState = uiState.sessionState,
                turnCount = uiState.turnCount,
                isSessionActive = uiState.isSessionActive,
            )
        },
        bottomBar = {
            SessionControlBar(
                uiState = uiState,
                onStart = onStartWithPermission,
                onPause = { viewModel.pauseSession() },
                onResume = { viewModel.resumeSession() },
                onStop = { viewModel.stopSession() },
                onStartManualRecording = { viewModel.startManualRecording() },
                onStopManualRecording = { viewModel.stopManualRecording() },
                onToggleManualRecording = { viewModel.toggleManualRecording() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Status indicator
            SessionStatusIndicator(
                statusMessage = uiState.statusMessage,
                sessionState = uiState.sessionState,
            )

            // Transcript display
            TranscriptDisplay(
                transcript = uiState.transcript,
                modifier = Modifier.weight(1f),
            )

            // Metrics display (when session active)
            if (uiState.isSessionActive) {
                MetricsDisplay(
                    metrics = uiState.metrics,
                )
            }
        }
    }
}

/**
 * Top app bar with session info.
 * Uses iOS-style glass capsule badge for session state (matching iOS ultraThinMaterial).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(
    sessionState: SessionState,
    turnCount: Int,
    isSessionActive: Boolean,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Session",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (isSessionActive) {
                    Text(
                        text = "$turnCount turns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            // Session state indicator - iOS-style glass capsule with colored dot
            SessionStateBadge(
                state = sessionState,
                modifier = Modifier.padding(end = Dimensions.SpacingLarge),
            )
        },
    )
}

/**
 * iOS-style session state badge with glass effect and colored dot.
 * Matches iOS pattern: Capsule().fill(.ultraThinMaterial) with status dot.
 */
@Composable
private fun SessionStateBadge(
    state: SessionState,
    modifier: Modifier = Modifier,
) {
    val stateColor = getStateColor(state)
    val statusText = getStateDisplayName(state)

    GlassCapsule(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .padding(
                        horizontal = Dimensions.BadgePaddingHorizontal,
                        vertical = Dimensions.BadgePaddingVertical,
                    )
                    .semantics { contentDescription = "Session status: $statusText" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            // Colored status dot
            Canvas(modifier = Modifier.size(Dimensions.StatusDotSize)) {
                drawCircle(color = stateColor)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Get display name for session state.
 */
private fun getStateDisplayName(state: SessionState): String {
    return when (state) {
        SessionState.IDLE -> "Ready"
        SessionState.USER_SPEAKING -> "Listening"
        SessionState.PROCESSING_UTTERANCE -> "Processing"
        SessionState.AI_THINKING -> "Thinking"
        SessionState.AI_SPEAKING -> "Speaking"
        SessionState.INTERRUPTED -> "Interrupted"
        SessionState.PAUSED -> "Paused"
        SessionState.ERROR -> "Error"
    }
}

/**
 * Status indicator showing current state message.
 */
@Composable
private fun SessionStatusIndicator(
    statusMessage: String,
    sessionState: SessionState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = getStateIcon(sessionState),
                contentDescription = null,
                tint = getStateColor(sessionState),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Transcript display with reverse scroll.
 * Uses iOS-matching spacing: 20dp horizontal padding, 12dp vertical spacing.
 */
@Composable
private fun TranscriptDisplay(
    transcript: List<TranscriptEntry>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    if (transcript.isEmpty()) {
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
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "Start a session to begin",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            contentPadding = PaddingValues(vertical = Dimensions.SpacingLarge),
        ) {
            items(
                items = transcript,
                key = { it.id },
            ) { entry ->
                TranscriptBubble(entry = entry)
            }
        }
    }
}

/**
 * Individual transcript bubble.
 * Uses iOS-style corner radius (16dp) and padding (12dp).
 */
@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val isUser = entry.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start

    val bubbleColor =
        if (isUser) {
            if (MaterialTheme.colorScheme.surface == MaterialTheme.colorScheme.background) {
                UserBubbleLight
            } else {
                UserBubbleDark
            }
        } else {
            if (MaterialTheme.colorScheme.surface == MaterialTheme.colorScheme.background) {
                AssistantBubbleLight
            } else {
                AssistantBubbleDark
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        // Role label
        Text(
            text = if (isUser) "You" else "AI Tutor",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.BubblePadding,
                    vertical = Dimensions.SpacingXSmall,
                ),
        )

        // Message bubble
        Surface(
            shape =
                RoundedCornerShape(
                    topStart = Dimensions.BubbleCornerRadius,
                    topEnd = Dimensions.BubbleCornerRadius,
                    bottomStart = if (isUser) Dimensions.BubbleCornerRadius else Dimensions.SpacingXSmall,
                    bottomEnd = if (isUser) Dimensions.SpacingXSmall else Dimensions.BubbleCornerRadius,
                ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = Dimensions.BubbleMaxWidth),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.BubblePadding),
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // Timestamp
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = Dimensions.SpacingXSmall),
                )
            }
        }
    }
}

/**
 * Metrics display showing latency stats.
 */
@Composable
private fun MetricsDisplay(metrics: com.unamentis.core.session.SessionMetrics) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricItem(label = "TTFT", value = "${metrics.llmTTFT}ms")
            MetricItem(label = "TTFB", value = "${metrics.ttsTTFB}ms")
            MetricItem(label = "E2E", value = "${metrics.e2eLatency}ms")
        }
    }
}

/**
 * Individual metric item.
 */
@Composable
private fun MetricItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Control bar with session action buttons.
 * Uses iOS-matching spacing: 16dp padding, 12dp spacing.
 */
@Composable
private fun SessionControlBar(
    uiState: SessionUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStartManualRecording: () -> Unit,
    onStopManualRecording: () -> Unit,
    onToggleManualRecording: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.ControlBarPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Manual recording button for PTT/Toggle modes (shown when session is active)
            if (uiState.isSessionActive &&
                uiState.recordingMode != RecordingMode.VAD &&
                uiState.sessionState in listOf(SessionState.IDLE, SessionState.USER_SPEAKING)
            ) {
                MicrophoneButton(
                    recordingMode = uiState.recordingMode,
                    isRecording = uiState.isManuallyRecording,
                    onStartRecording = onStartManualRecording,
                    onStopRecording = onStopManualRecording,
                    onToggleRecording = onToggleManualRecording,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            // Session control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Start button
                if (uiState.canStart) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start session")
                        Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                        Text("Start Session")
                    }
                }

                // Pause button
                if (uiState.canPause) {
                    FilledTonalButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause session")
                        Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                        Text("Pause")
                    }
                }

                // Resume button
                if (uiState.canResume) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume session")
                        Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                        Text("Resume")
                    }
                }

                // Stop button
                if (uiState.canStop) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop session")
                        Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

/**
 * Microphone button for manual recording modes (PTT and Toggle).
 */
@Composable
private fun MicrophoneButton(
    recordingMode: RecordingMode,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonColor =
        if (isRecording) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    val contentDescription =
        when {
            recordingMode == RecordingMode.PUSH_TO_TALK && isRecording -> "Release to send"
            recordingMode == RecordingMode.PUSH_TO_TALK -> "Hold to speak"
            recordingMode == RecordingMode.TOGGLE && isRecording -> "Tap to stop recording"
            else -> "Tap to start recording"
        }

    val gestureModifier =
        when (recordingMode) {
            RecordingMode.PUSH_TO_TALK ->
                Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartRecording()
                            tryAwaitRelease()
                            onStopRecording()
                        },
                    )
                }
            RecordingMode.TOGGLE ->
                Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleRecording() },
                    )
                }
            RecordingMode.VAD -> Modifier
        }

    Surface(
        modifier =
            modifier
                .size(72.dp)
                .then(gestureModifier),
        shape = CircleShape,
        color = buttonColor,
        shadowElevation = if (isRecording) 8.dp else 4.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.MicNone else Icons.Default.Mic,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/**
 * Get color for session state.
 */
@Composable
private fun getStateColor(state: SessionState): androidx.compose.ui.graphics.Color {
    return when (state) {
        SessionState.IDLE -> MaterialTheme.colorScheme.primary
        SessionState.USER_SPEAKING -> MaterialTheme.colorScheme.secondary
        SessionState.PROCESSING_UTTERANCE -> MaterialTheme.colorScheme.tertiary
        SessionState.AI_THINKING -> MaterialTheme.colorScheme.tertiary
        SessionState.AI_SPEAKING -> MaterialTheme.colorScheme.primary
        SessionState.INTERRUPTED -> MaterialTheme.colorScheme.error
        SessionState.PAUSED -> MaterialTheme.colorScheme.outline
        SessionState.ERROR -> MaterialTheme.colorScheme.error
    }
}

/**
 * Get icon for session state.
 */
private fun getStateIcon(state: SessionState): androidx.compose.ui.graphics.vector.ImageVector {
    return when (state) {
        SessionState.IDLE -> Icons.Default.Mic
        SessionState.USER_SPEAKING -> Icons.Default.MicNone
        SessionState.PROCESSING_UTTERANCE -> Icons.Default.HourglassBottom
        SessionState.AI_THINKING -> Icons.Default.Psychology
        SessionState.AI_SPEAKING -> Icons.Default.VolumeUp
        SessionState.INTERRUPTED -> Icons.Default.Stop
        SessionState.PAUSED -> Icons.Default.Pause
        SessionState.ERROR -> Icons.Default.Error
    }
}

/**
 * Format timestamp for display.
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
