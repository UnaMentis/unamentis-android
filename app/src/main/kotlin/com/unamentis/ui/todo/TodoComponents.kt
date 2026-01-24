package com.unamentis.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.unamentis.data.model.TodoPriority
import com.unamentis.data.model.TodoStatus
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGray
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed

// =============================================================================
// STATUS ICON (matching iOS TodoItemRow status indicator)
// =============================================================================

/**
 * Status icon for todo items.
 *
 * Matches iOS TodoItemRow status icons:
 * - Pending: gray circle outline
 * - In Progress: blue half-filled circle
 * - Completed: green checkmark circle
 * - Archived: purple archive box
 *
 * @param status Todo status
 * @param modifier Modifier to apply
 */
@Composable
fun TodoStatusIcon(
    status: TodoStatus,
    modifier: Modifier = Modifier,
) {
    val (icon, color) =
        when (status) {
            TodoStatus.ACTIVE -> Icons.Default.Circle to iOSGray
            TodoStatus.COMPLETED -> Icons.Default.CheckCircle to iOSGreen
            TodoStatus.ARCHIVED -> Icons.Default.Archive to Color(0xFF9B59B6) // iOS purple
        }

    Icon(
        imageVector = icon,
        contentDescription = status.name.lowercase(),
        tint = color,
        modifier = modifier.size(24.dp),
    )
}

// =============================================================================
// PRIORITY BADGE (matching iOS TypeBadge capsule style)
// =============================================================================

/**
 * Priority badge with iOS capsule style.
 *
 * Matches iOS TypeBadge with:
 * - Capsule shape
 * - 15% opacity background
 * - Colored text
 *
 * @param priority Todo priority
 * @param modifier Modifier to apply
 */
@Composable
fun TodoPriorityBadge(
    priority: TodoPriority,
    modifier: Modifier = Modifier,
) {
    val (color, text) =
        when (priority) {
            TodoPriority.HIGH -> iOSRed to "High"
            TodoPriority.MEDIUM -> iOSOrange to "Medium"
            TodoPriority.LOW -> iOSGray to "Low"
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.CapsuleCornerRadius),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            style = IOSTypography.caption2,
            color = color,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.BadgePaddingHorizontal / 2,
                    vertical = Dimensions.BadgePaddingVertical / 2,
                ),
        )
    }
}

// =============================================================================
// EMPTY STATE (matching iOS ContentUnavailableView)
// =============================================================================

/**
 * Empty state for todo list.
 *
 * Matches iOS ContentUnavailableView with icon, title, and description.
 *
 * @param icon Icon to display
 * @param title Title text
 * @param description Description text
 * @param modifier Modifier to apply
 */
@Composable
fun TodoEmptyState(
    icon: ImageVector = Icons.Outlined.ChecklistRtl,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
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

        Text(
            text = description,
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        action?.invoke()
    }
}

// =============================================================================
// SOURCE BADGE (matching iOS source indicator)
// =============================================================================

/**
 * Source indicator badge.
 *
 * Shows where the todo originated from (manual, AI, curriculum).
 *
 * @param source Source label
 * @param icon Source icon
 * @param modifier Modifier to apply
 */
@Composable
fun TodoSourceBadge(
    source: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = source,
            style = IOSTypography.caption2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =============================================================================
// DATE LABEL (matching iOS date formatting)
// =============================================================================

/**
 * Date label with icon.
 *
 * @param icon Icon to display
 * @param text Date text
 * @param isOverdue Whether the date is overdue
 * @param modifier Modifier to apply
 */
@Composable
fun TodoDateLabel(
    icon: ImageVector,
    text: String,
    isOverdue: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val textColor =
        if (isOverdue) {
            iOSRed
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = textColor,
        )
        Text(
            text = text,
            style = IOSTypography.caption2,
            color = textColor,
        )
    }
}

// =============================================================================
// AI SUGGESTION BADGE (matching iOS AI indicator)
// =============================================================================

/**
 * AI suggestion confidence badge.
 *
 * @param confidence Confidence value 0.0-1.0
 * @param modifier Modifier to apply
 */
@Composable
fun AISuggestionBadge(
    confidence: Float? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(iOSBlue),
        )
        Text(
            text = "AI Suggested",
            style = IOSTypography.caption2,
            color = iOSBlue,
        )
        if (confidence != null) {
            Text(
                text = "(${(confidence * 100).toInt()}%)",
                style = IOSTypography.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
