package com.unamentis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unamentis.data.model.SessionState
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.SessionStateAISpeaking
import com.unamentis.ui.theme.SessionStateAIThinking
import com.unamentis.ui.theme.SessionStateError
import com.unamentis.ui.theme.SessionStateIdle
import com.unamentis.ui.theme.SessionStateInterrupted
import com.unamentis.ui.theme.SessionStatePaused
import com.unamentis.ui.theme.SessionStateProcessing
import com.unamentis.ui.theme.SessionStateUserSpeaking

/**
 * Animated status dot with optional pulse effect.
 *
 * Matches iOS pattern of using a colored dot with pulsing animation
 * to indicate active states like recording, speaking, or processing.
 *
 * @param color The color of the status dot
 * @param modifier Modifier to apply
 * @param isPulsing Whether to show the pulse animation
 * @param pulseColor Color for the pulse effect (defaults to same as dot)
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
    pulseColor: Color = color,
) {
    if (isPulsing) {
        PulsingStatusDot(
            color = color,
            pulseColor = pulseColor,
            modifier = modifier,
        )
    } else {
        Canvas(modifier = modifier.size(Dimensions.StatusDotSize)) {
            drawCircle(color = color)
        }
    }
}

/**
 * Status dot with continuous pulse animation.
 *
 * The pulse expands outward from the center with decreasing opacity.
 */
@Composable
private fun PulsingStatusDot(
    color: Color,
    pulseColor: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier.size(Dimensions.StatusDotSize * 2),
        contentAlignment = Alignment.Center,
    ) {
        // Pulse ring
        Canvas(modifier = Modifier.size(Dimensions.StatusDotSize * scale)) {
            drawCircle(
                color = pulseColor.copy(alpha = alpha),
                style = Stroke(width = 2f),
            )
        }
        // Core dot
        Canvas(modifier = Modifier.size(Dimensions.StatusDotSize)) {
            drawCircle(color = color)
        }
    }
}

/**
 * Session status badge with colored dot and label.
 *
 * Matches iOS SessionView status badge pattern with glass background
 * and animated status dot.
 *
 * @param state The current session state
 * @param modifier Modifier to apply
 */
@Composable
fun SessionStatusBadge(
    state: SessionState,
    modifier: Modifier = Modifier,
) {
    val color = getSessionStateColor(state)
    val label = getSessionStateLabel(state)
    val shouldPulse = shouldStatePulse(state)

    GlassCapsule(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .padding(
                        horizontal = Dimensions.BadgePaddingHorizontal,
                        vertical = Dimensions.BadgePaddingVertical,
                    )
                    .semantics { contentDescription = "Session status: $label" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            StatusDot(
                color = color,
                isPulsing = shouldPulse,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Compact session status indicator showing just the dot.
 *
 * For use in tight spaces where a full badge won't fit.
 *
 * @param state The current session state
 * @param modifier Modifier to apply
 */
@Composable
fun SessionStatusDot(
    state: SessionState,
    modifier: Modifier = Modifier,
) {
    val color = getSessionStateColor(state)
    val shouldPulse = shouldStatePulse(state)
    val label = getSessionStateLabel(state)

    StatusDot(
        color = color,
        isPulsing = shouldPulse,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/**
 * Get the color for a session state.
 *
 * Matches iOS SessionView.swift statusColor mapping.
 */
@Composable
fun getSessionStateColor(state: SessionState): Color {
    return when (state) {
        SessionState.IDLE -> SessionStateIdle
        SessionState.USER_SPEAKING -> SessionStateUserSpeaking
        SessionState.PROCESSING_UTTERANCE -> SessionStateProcessing
        SessionState.AI_THINKING -> SessionStateAIThinking
        SessionState.AI_SPEAKING -> SessionStateAISpeaking
        SessionState.INTERRUPTED -> SessionStateInterrupted
        SessionState.PAUSED -> SessionStatePaused
        SessionState.ERROR -> SessionStateError
    }
}

/**
 * Get the display label for a session state.
 */
fun getSessionStateLabel(state: SessionState): String {
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
 * Determine if a session state should show pulse animation.
 *
 * Active states (speaking, listening, thinking) pulse to indicate activity.
 */
private fun shouldStatePulse(state: SessionState): Boolean {
    return when (state) {
        SessionState.USER_SPEAKING,
        SessionState.AI_SPEAKING,
        SessionState.AI_THINKING,
        SessionState.PROCESSING_UTTERANCE,
        -> true
        else -> false
    }
}

/**
 * Connection status indicator.
 *
 * Shows connectivity state with appropriate icon and color.
 *
 * @param isConnected Whether connected
 * @param modifier Modifier to apply
 */
@Composable
fun ConnectionStatusDot(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color =
        if (isConnected) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.error
        }
    val label = if (isConnected) "Connected" else "Disconnected"

    // Pulse when disconnected to draw attention
    StatusDot(
        color = color,
        isPulsing = !isConnected,
        modifier = modifier.semantics { contentDescription = label },
    )
}
