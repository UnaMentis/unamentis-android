package com.unamentis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.util.safeProgress

/**
 * iOS-styled card component.
 *
 * Matches iOS pattern: RoundedRectangle(cornerRadius: 12).fill(.systemGray6)
 * Uses surfaceVariant for consistent light/dark mode appearance.
 *
 * @param modifier Modifier to apply to the card
 * @param onClick Optional click handler - if null, card is non-clickable
 * @param content Content to display inside the card
 */
@Composable
fun IOSCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimensions.CardCornerRadius)
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CardElevation),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.CardPadding),
                content = content,
            )
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CardElevation),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.CardPadding),
                content = content,
            )
        }
    }
}

/**
 * iOS-styled status badge with capsule shape and glass effect.
 *
 * Matches iOS pattern: Capsule().fill(.ultraThinMaterial)
 * Used for session status indicators, metrics, and tags.
 *
 * @param text Badge text content
 * @param icon Optional icon to display before text
 * @param color Color for the icon (text uses onSurface)
 * @param modifier Modifier to apply to the badge
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    GlassCapsule(
        modifier =
            modifier.semantics {
                contentDescription = text
            },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.BadgePaddingHorizontal,
                    vertical = Dimensions.BadgePaddingVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(Dimensions.StatusDotSize),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Status badge with a colored dot indicator instead of an icon.
 *
 * Matches iOS session status pattern with colored status dot.
 *
 * @param text Badge text content
 * @param dotColor Color of the status dot
 * @param modifier Modifier to apply to the badge
 */
@Composable
fun StatusBadgeWithDot(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    GlassCapsule(
        modifier =
            modifier.semantics {
                contentDescription = text
            },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.BadgePaddingHorizontal,
                    vertical = Dimensions.BadgePaddingVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            // Colored status dot
            androidx.compose.foundation.Canvas(modifier = Modifier.size(Dimensions.StatusDotSize)) {
                drawCircle(color = dotColor)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * iOS-styled glass card for floating UI elements.
 *
 * Uses glass morphism styling for a frosted glass effect.
 * Ideal for floating cards, overlays, and modal-like content.
 *
 * @param modifier Modifier to apply to the card
 * @param content Content to display inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        cornerRadius = Dimensions.CardCornerRadius,
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            content = content,
        )
    }
}

/**
 * iOS-styled progress bar.
 *
 * Matches iOS pattern: 4pt height, 2pt corner radius
 * Uses rounded caps for smooth appearance.
 *
 * @param progress Current progress value (0.0 to 1.0)
 * @param modifier Modifier to apply to the progress bar
 * @param color Progress indicator color
 * @param trackColor Background track color
 */
@Composable
fun IOSProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(
        progress = { safeProgress(progress) },
        modifier =
            modifier
                .height(Dimensions.ProgressBarHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.ProgressBarCornerRadius)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
    )
}

/**
 * iOS-styled indeterminate progress bar.
 *
 * @param modifier Modifier to apply to the progress bar
 * @param color Progress indicator color
 * @param trackColor Background track color
 */
@Composable
fun IOSProgressBarIndeterminate(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(
        modifier =
            modifier
                .height(Dimensions.ProgressBarHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.ProgressBarCornerRadius)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
    )
}
