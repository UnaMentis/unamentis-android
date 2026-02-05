package com.unamentis.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.data.model.SessionState
import com.unamentis.ui.components.BrandLogo
import com.unamentis.ui.components.LatencyBadge
import com.unamentis.ui.components.SessionStatusBadge
import com.unamentis.ui.components.Size
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue

/**
 * Session screen - Main voice conversation interface.
 *
 * Matches iOS SessionView layout:
 * - Background gradient
 * - Topic progress bar (curriculum mode)
 * - Session status badge
 * - Transcript display with glass background
 * - Audio level VU meter (when active)
 * - Control bar (curriculum or freeform mode)
 *
 * @param initialCurriculumId Optional curriculum ID from deep link
 * @param initialTopicId Optional topic ID from deep link
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
                viewModel.startSession()
            }
        }

    val onStartWithPermission: () -> Unit = {
        if (hasMicPermission) {
            viewModel.startSession()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Help sheet state
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Background gradient (matching iOS)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    ),
                            ),
                    ),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top app bar
            SessionTopBar(
                sessionState = uiState.sessionState,
                isSessionActive = uiState.isSessionActive,
                latencyMs = uiState.metrics.e2eLatency.toInt(),
                onHelpClick = { showHelp = true },
            )

            // Main content area
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.ScreenHorizontalPadding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                ) {
                    // Topic progress bar (curriculum mode only)
                    if (uiState.isCurriculumMode && uiState.totalSegments > 0) {
                        TopicProgressBar(
                            completedSegments = uiState.currentSegmentIndex,
                            totalSegments = uiState.totalSegments,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    // Session status badge
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SessionStatusBadge(
                            state = uiState.sessionState,
                            modifier =
                                Modifier.padding(
                                    top = if (uiState.isCurriculumMode) 4.dp else 12.dp,
                                ),
                        )
                    }

                    // Transcript display (takes most of the space)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                // Add bottom padding when session active for control bar
                                .padding(bottom = if (uiState.isSessionActive) 140.dp else 0.dp),
                    ) {
                        TranscriptDisplay(
                            entries = uiState.transcript,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Idle state: Large start button
        if (!uiState.isSessionActive && !uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SessionStartButton(
                    onClick = onStartWithPermission,
                    isLoading = uiState.isLoading,
                )
            }
        }

        // Active session: Bottom control area
        AnimatedVisibility(
            visible = uiState.isSessionActive,
            enter =
                fadeIn() +
                    slideInVertically(
                        animationSpec = spring(),
                        initialOffsetY = { it },
                    ),
            exit =
                fadeOut() +
                    slideOutVertically(
                        animationSpec = spring(),
                        targetOffsetY = { it },
                    ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                        .padding(bottom = Dimensions.ControlBarBottomPadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                // Audio level VU meter
                AudioLevelView(
                    level = uiState.audioLevel,
                    state = uiState.sessionState,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Control bar based on mode
                if (uiState.isCurriculumMode) {
                    CurriculumSessionControlBar(
                        isPaused = uiState.sessionState == SessionState.PAUSED,
                        isMuted = uiState.isMuted,
                        currentSegmentIndex = uiState.currentSegmentIndex,
                        hasNextTopic = uiState.hasNextTopic,
                        onPauseChanged = { paused ->
                            if (paused) viewModel.pauseSession() else viewModel.resumeSession()
                        },
                        onMuteChanged = { muted -> viewModel.setMuted(muted) },
                        onStop = { viewModel.stopSession() },
                        onGoBack = { viewModel.goBackSegment() },
                        onReplay = { viewModel.replayTopic() },
                        onNextTopic = { viewModel.nextTopic() },
                    )
                } else {
                    // Freeform mode controls
                    FreeformSessionControlBar(
                        isPaused = uiState.sessionState == SessionState.PAUSED,
                        isMuted = uiState.isMuted,
                        onPauseChanged = { paused ->
                            if (paused) viewModel.pauseSession() else viewModel.resumeSession()
                        },
                        onMuteChanged = { muted -> viewModel.setMuted(muted) },
                        onStop = { viewModel.stopSession() },
                    )
                }
            }
        }
    }

    // Help sheet
    if (showHelp) {
        SessionHelpSheet(onDismiss = { showHelp = false })
    }
}

/**
 * Top app bar with session info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UnusedParameter") // sessionState reserved for future status display
private fun SessionTopBar(
    sessionState: SessionState,
    isSessionActive: Boolean,
    latencyMs: Int,
    onHelpClick: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            if (isSessionActive) {
                // Show latency badge when active
                LatencyBadge(
                    latencyMs = latencyMs,
                    modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                )
            } else {
                // Show brand logo when idle
                BrandLogo(
                    size = Size.Compact,
                    modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.tab_session),
                style = IOSTypography.headline,
            )
        },
        actions = {
            IconButton(onClick = onHelpClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Session help",
                )
            }
        },
    )
}

/**
 * Large session start button for idle state.
 *
 * Matches iOS SessionControlButton with 80pt size when inactive.
 */
@Composable
private fun SessionStartButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val buttonSize = Dimensions.SessionButtonSizeIdle
    val iconSize = Dimensions.SessionButtonIconSizeIdle

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .size(buttonSize)
                .shadow(10.dp, CircleShape)
                .semantics { contentDescription = "Start session" },
        shape = CircleShape,
        color = iOSBlue,
    ) {
        Box(
            modifier = Modifier.size(buttonSize),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * Session help sheet with tips and status explanations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionHelpSheet(onDismiss: () -> Unit) {
    com.unamentis.ui.components.HelpSheet(
        title = "Voice Session Help",
        description = "Have natural voice conversations with your AI tutor. The session adapts to your learning needs.",
        tips =
            listOf(
                com.unamentis.ui.components.HelpTip(
                    title = "Speak naturally",
                    description =
                        "Talk just like you would with a teacher. " +
                            "Ask questions, share thoughts, or request explanations.",
                ),
                com.unamentis.ui.components.HelpTip(
                    title = "Interrupt anytime",
                    description = "Start speaking while the AI is talking to interrupt and ask a follow-up question.",
                ),
                com.unamentis.ui.components.HelpTip(
                    title = "Session states",
                    description = "Green = listening, Blue = AI speaking, Orange = thinking, Gray = ready.",
                ),
                com.unamentis.ui.components.HelpTip(
                    title = "Slide to stop",
                    description = "In curriculum mode, slide the stop button to prevent accidental session ends.",
                ),
            ),
        onDismiss = onDismiss,
    )
}
