package com.unamentis.ui.session

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed
import kotlin.math.roundToInt

/**
 * Slide to stop button for curriculum mode sessions.
 *
 * Matches iOS pattern of requiring user to drag thumb across track to confirm stop.
 * This prevents accidental stops during tutoring sessions.
 *
 * @param onStop Callback when slide action completes
 * @param modifier Modifier to apply
 */
@Composable
fun SlideToStopButton(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current

    val thumbSize = Dimensions.SlideThumbSize
    val trackPadding = Dimensions.SlideTrackPadding
    val maxTrackWidth = Dimensions.SlideTrackMaxWidth
    val completionThreshold = 0.85f

    val trackWidthPx = with(density) { maxTrackWidth.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val trackPaddingPx = with(density) { trackPadding.toPx() }
    val maxDragDistance = trackWidthPx - thumbSizePx - (trackPaddingPx * 2)

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val completionProgress =
        if (maxDragDistance > 0) {
            (dragOffset / maxDragDistance).coerceIn(0f, 1f)
        } else {
            0f
        }

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "thumbScale",
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "shadowElevation",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(thumbSize + trackPadding * 2)
                .semantics { contentDescription = "Slide to stop session" },
    ) {
        // Track background
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = maxTrackWidth, height = thumbSize + trackPadding * 2)
                    .clip(RoundedCornerShape(50))
                    .background(iOSRed.copy(alpha = 0.15f)),
        )

        // Progress fill
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(
                        width = with(density) { (dragOffset + thumbSizePx + trackPaddingPx).toDp() },
                        height = thumbSize + trackPadding * 2,
                    )
                    .clip(RoundedCornerShape(50))
                    .background(iOSRed.copy(alpha = 0.3f)),
        )

        // Instruction text (fades as user slides)
        Text(
            text = stringResource(R.string.slide_to_stop),
            style = IOSTypography.subheadline,
            fontWeight = FontWeight.Medium,
            color = iOSRed.copy(alpha = 1f - completionProgress),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = maxTrackWidth.toPx() / 4 },
        )

        // Thumb
        Surface(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((trackPaddingPx + dragOffset).roundToInt(), 0) }
                    .size(thumbSize)
                    .scale(thumbScale)
                    .shadow(shadowElevation, CircleShape)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                            },
                            onDragEnd = {
                                isDragging = false
                                // Calculate completion progress fresh here instead of using
                                // captured value - the captured val would be stale since
                                // pointerInput(Unit) doesn't re-execute on state changes
                                val currentProgress =
                                    if (maxDragDistance > 0) {
                                        (dragOffset / maxDragDistance).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                if (currentProgress >= completionThreshold) {
                                    // Trigger haptic feedback and complete
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onStop()
                                }
                                // Reset position with spring animation
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDragDistance)
                            },
                        )
                    },
            shape = CircleShape,
            color = iOSRed,
        ) {
            Box(
                modifier = Modifier.size(thumbSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Mute button for session microphone.
 *
 * When muted, the user can listen without triggering speech detection.
 *
 * @param isMuted Whether the microphone is muted
 * @param onMuteChanged Callback when mute state changes
 * @param modifier Modifier to apply
 */
@Composable
fun SessionMuteButton(
    isMuted: Boolean,
    onMuteChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val buttonSize = Dimensions.SessionMuteButtonSize

    val backgroundColor by animateColorAsState(
        targetValue = if (isMuted) iOSRed.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
        label = "muteBgColor",
    )

    val iconColor by animateColorAsState(
        targetValue = if (isMuted) iOSRed else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "muteIconColor",
    )

    val contentDesc =
        if (isMuted) {
            stringResource(R.string.microphone_muted)
        } else {
            stringResource(R.string.microphone_on)
        }

    IconButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onMuteChanged(!isMuted)
        },
        modifier =
            modifier
                .size(buttonSize)
                .semantics { contentDescription = contentDesc },
    ) {
        Surface(
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            color = backgroundColor,
        ) {
            Box(
                modifier = Modifier.size(buttonSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Pause/Resume button for sessions.
 *
 * @param isPaused Whether the session is paused
 * @param onPauseChanged Callback when pause state changes
 * @param modifier Modifier to apply
 */
@Composable
fun SessionPauseButton(
    isPaused: Boolean,
    onPauseChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val buttonSize = Dimensions.SessionPauseButtonSize

    val contentDesc =
        if (isPaused) {
            stringResource(R.string.resume_session)
        } else {
            stringResource(R.string.pause_session)
        }

    IconButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPauseChanged(!isPaused)
        },
        modifier =
            modifier
                .size(buttonSize)
                .shadow(6.dp, CircleShape)
                .semantics { contentDescription = contentDesc },
    ) {
        Surface(
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            color = iOSBlue,
        ) {
            Box(
                modifier = Modifier.size(buttonSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Go back one segment button for curriculum playback.
 *
 * @param isEnabled Whether the button is enabled (can go back)
 * @param onClick Callback when button is tapped
 * @param modifier Modifier to apply
 */
@Composable
fun GoBackSegmentButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    CurriculumNavigationButton(
        icon = Icons.Default.Replay10,
        label = stringResource(R.string.go_back_segment),
        isEnabled = isEnabled,
        backgroundColor = if (isEnabled) iOSBlue.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
        iconTint = if (isEnabled) iOSBlue else Color.Gray.copy(alpha = 0.5f),
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
    )
}

/**
 * Replay topic button for curriculum playback.
 *
 * @param onClick Callback when button is tapped
 * @param modifier Modifier to apply
 */
@Composable
fun ReplayTopicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    CurriculumNavigationButton(
        icon = Icons.Default.Refresh,
        label = stringResource(R.string.replay_topic),
        isEnabled = true,
        backgroundColor = iOSOrange.copy(alpha = 0.15f),
        iconTint = iOSOrange,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
    )
}

/**
 * Next topic button for curriculum playback.
 *
 * @param isEnabled Whether the button is enabled (has next topic)
 * @param onClick Callback when button is tapped
 * @param modifier Modifier to apply
 */
@Composable
fun NextTopicButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    CurriculumNavigationButton(
        icon = Icons.AutoMirrored.Filled.ArrowForward,
        label = stringResource(R.string.next_topic),
        isEnabled = isEnabled,
        backgroundColor = if (isEnabled) iOSGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
        iconTint = if (isEnabled) iOSGreen else Color.Gray.copy(alpha = 0.5f),
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier,
    )
}

/**
 * Reusable curriculum navigation button.
 */
@Composable
private fun CurriculumNavigationButton(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonSize = Dimensions.CurriculumNavButtonSize

    IconButton(
        onClick = onClick,
        enabled = isEnabled,
        modifier =
            modifier
                .size(buttonSize)
                .semantics { contentDescription = label },
    ) {
        Surface(
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            color = backgroundColor,
        ) {
            Box(
                modifier = Modifier.size(buttonSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Control bar for freeform session mode.
 *
 * Contains mute button, pause button, and slide-to-stop.
 *
 * @param isPaused Whether the session is paused
 * @param isMuted Whether the microphone is muted
 * @param onPauseChanged Callback when pause state changes
 * @param onMuteChanged Callback when mute state changes
 * @param onStop Callback when stop is triggered
 * @param modifier Modifier to apply
 */
@Composable
fun FreeformSessionControlBar(
    isPaused: Boolean,
    isMuted: Boolean,
    onPauseChanged: (Boolean) -> Unit,
    onMuteChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding, vertical = Dimensions.SpacingMedium),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionMuteButton(
                isMuted = isMuted,
                onMuteChanged = onMuteChanged,
            )

            SessionPauseButton(
                isPaused = isPaused,
                onPauseChanged = onPauseChanged,
            )

            SlideToStopButton(
                onStop = onStop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Control bar for curriculum session mode.
 *
 * Contains curriculum navigation buttons plus standard session controls.
 *
 * @param isPaused Whether the session is paused
 * @param isMuted Whether the microphone is muted
 * @param currentSegmentIndex Current segment index in topic
 * @param hasNextTopic Whether there is a next topic available
 * @param onPauseChanged Callback when pause state changes
 * @param onMuteChanged Callback when mute state changes
 * @param onStop Callback when stop is triggered
 * @param onGoBack Callback when go-back is triggered
 * @param onReplay Callback when replay is triggered
 * @param onNextTopic Callback when next-topic is triggered
 * @param modifier Modifier to apply
 */
@Composable
fun CurriculumSessionControlBar(
    isPaused: Boolean,
    isMuted: Boolean,
    currentSegmentIndex: Int,
    hasNextTopic: Boolean,
    onPauseChanged: (Boolean) -> Unit,
    onMuteChanged: (Boolean) -> Unit,
    onStop: () -> Unit,
    onGoBack: () -> Unit,
    onReplay: () -> Unit,
    onNextTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 2.dp,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.ScreenHorizontalPadding,
                    vertical = Dimensions.SpacingMedium,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Navigation controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(Dimensions.ScreenHorizontalPadding, Alignment.CenterHorizontally),
            ) {
                GoBackSegmentButton(
                    isEnabled = currentSegmentIndex > 0,
                    onClick = onGoBack,
                )

                ReplayTopicButton(
                    onClick = onReplay,
                )

                NextTopicButton(
                    isEnabled = hasNextTopic,
                    onClick = onNextTopic,
                )
            }

            // Standard session controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionMuteButton(
                    isMuted = isMuted,
                    onMuteChanged = onMuteChanged,
                )

                SessionPauseButton(
                    isPaused = isPaused,
                    onPauseChanged = onPauseChanged,
                )

                SlideToStopButton(
                    onStop = onStop,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
