package com.unamentis.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unamentis.R
import com.unamentis.data.model.SessionState
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSCyan
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSPurple
import com.unamentis.ui.theme.iOSRed
import com.unamentis.ui.theme.iOSYellow

/**
 * Audio level visualization (VU meter).
 *
 * Matches iOS AudioLevelView with 20 animated bars that respond to audio levels.
 * Color scheme varies based on session state:
 * - AI speaking/thinking: Blue color scheme (blue → cyan → purple)
 * - User speaking: Green color scheme (green → yellow → red)
 *
 * @param level Audio level in dB (typically -60 to 0)
 * @param state Current session state for color scheme selection
 * @param modifier Modifier to apply
 */
@Composable
fun AudioLevelView(
    level: Float,
    state: SessionState,
    modifier: Modifier = Modifier,
) {
    val barCount = Dimensions.AudioLevelBarCount
    val isAIAudio = state == SessionState.AI_SPEAKING || state == SessionState.AI_THINKING

    // Convert dB to 0-1 range (-60dB to 0dB)
    val normalizedLevel = ((level + 60f) / 60f).coerceIn(0f, 1f)
    val percentage = (normalizedLevel * 100).toInt()

    val accessibilityDesc =
        if (isAIAudio) {
            stringResource(R.string.audio_level_ai_percent, percentage)
        } else {
            stringResource(R.string.audio_level_user_percent, percentage)
        }

    Row(
        modifier =
            modifier
                .height(Dimensions.VuMeterHeight)
                .semantics {
                    contentDescription = accessibilityDesc
                },
        horizontalArrangement = Arrangement.spacedBy(Dimensions.VuMeterBarSpacing),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(barCount) { index ->
            AudioLevelBar(
                index = index,
                barCount = barCount,
                normalizedLevel = normalizedLevel,
                isAIAudio = isAIAudio,
            )
        }
    }
}

/**
 * Individual bar in the audio level view.
 */
@Composable
private fun AudioLevelBar(
    index: Int,
    barCount: Int,
    normalizedLevel: Float,
    isAIAudio: Boolean,
) {
    val threshold = index.toFloat() / barCount.toFloat()
    val isActive = normalizedLevel > threshold

    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.2f,
        animationSpec = tween(durationMillis = 100),
        label = "barScale",
    )

    val color = getBarColor(index, barCount, isAIAudio)

    Surface(
        modifier =
            Modifier
                .width(Dimensions.VuMeterBarWidth)
                .height(Dimensions.VuMeterHeight)
                .scale(scaleX = 1f, scaleY = targetScale),
        shape = RoundedCornerShape(Dimensions.AudioLevelBarCornerRadius),
        color = color,
    ) {}
}

/**
 * Get color for a bar based on its position and audio source.
 *
 * Matches iOS color scheme:
 * - AI audio: Blue (0-60%) → Cyan (60-80%) → Purple (80-100%)
 * - User audio: Green (0-60%) → Yellow (60-80%) → Red (80-100%)
 */
private fun getBarColor(
    index: Int,
    barCount: Int,
    isAIAudio: Boolean,
): Color {
    val ratio = index.toFloat() / barCount.toFloat()

    return if (isAIAudio) {
        when {
            ratio < 0.6f -> iOSBlue
            ratio < 0.8f -> iOSCyan
            else -> iOSPurple
        }
    } else {
        when {
            ratio < 0.6f -> iOSGreen
            ratio < 0.8f -> iOSYellow
            else -> iOSRed
        }
    }
}
