package com.unamentis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed
import com.unamentis.ui.theme.monospacedDigit

/**
 * Metrics badge for displaying real-time metrics in toolbars.
 *
 * Matches iOS pattern of showing live metrics in a compact badge format.
 * Uses glass morphism styling and color-coded values based on thresholds.
 *
 * @param value The metric value to display
 * @param label The label for the metric (e.g., "ms", "E2E")
 * @param icon Optional icon to display
 * @param modifier Modifier to apply
 * @param threshold Threshold configuration for color coding
 */
@Composable
fun MetricsBadge(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    threshold: MetricThreshold? = null,
) {
    val color =
        threshold?.getColor(value.toIntOrNull() ?: 0)
            ?: MaterialTheme.colorScheme.onSurface

    GlassCapsule(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .padding(
                        horizontal = Dimensions.BadgePaddingHorizontal,
                        vertical = Dimensions.BadgePaddingVertical,
                    )
                    .semantics { contentDescription = "$label: $value" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(Dimensions.IconSizeSmall),
                )
            }
            Text(
                text = value,
                style = IOSTypography.caption.monospacedDigit(),
                color = color,
            )
            Text(
                text = label,
                style = IOSTypography.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Latency metrics badge specifically for E2E latency display.
 *
 * Automatically color-codes based on latency thresholds:
 * - Green: < 500ms (good)
 * - Orange: 500-1000ms (acceptable)
 * - Red: > 1000ms (poor)
 *
 * @param latencyMs The latency value in milliseconds
 * @param modifier Modifier to apply
 */
@Composable
fun LatencyBadge(
    latencyMs: Int,
    modifier: Modifier = Modifier,
) {
    MetricsBadge(
        value = latencyMs.toString(),
        label = "ms",
        icon = Icons.Default.Timer,
        modifier = modifier,
        threshold = MetricThreshold.Latency,
    )
}

/**
 * Turn count badge for session toolbar.
 *
 * @param turnCount The number of turns in the session
 * @param modifier Modifier to apply
 */
@Composable
fun TurnCountBadge(
    turnCount: Int,
    modifier: Modifier = Modifier,
) {
    GlassCapsule(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .padding(
                        horizontal = Dimensions.BadgePaddingHorizontal,
                        vertical = Dimensions.BadgePaddingVertical,
                    )
                    .semantics { contentDescription = "$turnCount turns" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        ) {
            Text(
                text = turnCount.toString(),
                style = IOSTypography.caption.monospacedDigit(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "turns",
                style = IOSTypography.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Threshold configuration for metric color coding.
 *
 * @param good Values below this threshold are green
 * @param acceptable Values below this threshold are orange
 * @param poor Values at or above this threshold are red
 */
data class MetricThreshold(
    val good: Int,
    val acceptable: Int,
) {
    fun getColor(value: Int) =
        when {
            value < good -> iOSGreen
            value < acceptable -> iOSOrange
            else -> iOSRed
        }

    companion object {
        /** Standard latency thresholds (500ms good, 1000ms acceptable) */
        val Latency = MetricThreshold(good = 500, acceptable = 1000)

        /** Processing time thresholds (200ms good, 500ms acceptable) */
        val Processing = MetricThreshold(good = 200, acceptable = 500)

        /** TTFT thresholds (300ms good, 600ms acceptable) */
        val TTFT = MetricThreshold(good = 300, acceptable = 600)
    }
}
