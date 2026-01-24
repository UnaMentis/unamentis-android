package com.unamentis.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unamentis.data.model.Session
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =============================================================================
// DATE SECTION HEADER (matching iOS grouped list sections)
// =============================================================================

/**
 * Section header for date grouping in session list.
 *
 * Matches iOS List section headers with uppercase caption style.
 *
 * @param title Section title (e.g., "Today", "Yesterday", "January 15, 2024")
 * @param modifier Modifier to apply
 */
@Composable
fun DateSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = IOSTypography.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.SpacingLarge,
                    vertical = Dimensions.SpacingSmall,
                ),
    )
}

// =============================================================================
// SESSION ROW (matching iOS SessionRowView)
// =============================================================================

/**
 * Session row for history list.
 *
 * Matches iOS SessionRowView with:
 * - Headline font for title
 * - Caption metadata (duration, turns, cost)
 * - Chevron indicator
 *
 * @param session Session to display
 * @param onClick Click handler
 * @param modifier Modifier to apply
 */
@Composable
fun SessionRow(
    session: Session,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = session.curriculumId?.let { "Curriculum Session" } ?: "Free Session"
    val durationMinutes =
        session.endTime?.let {
            ((it - session.startTime) / 1000 / 60).toInt()
        } ?: 0

    val accessibilityLabel =
        buildString {
            append(title)
            if (session.isStarred) append(", starred")
            append(", ${session.turnCount} turns")
            if (durationMinutes > 0) append(", $durationMinutes minutes")
        }

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityLabel },
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                // Title row with star
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                ) {
                    Text(
                        text = title,
                        style = IOSTypography.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (session.isStarred) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
                ) {
                    // Duration
                    if (durationMinutes > 0) {
                        SessionMetadataLabel(
                            icon = Icons.Default.Timer,
                            text = "${durationMinutes}min",
                        )
                    }

                    // Turn count
                    SessionMetadataLabel(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        text = "${session.turnCount} turns",
                    )
                }

                // Topic if present
                session.topicId?.let { topicId ->
                    Text(
                        text = topicId,
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Time label
            Text(
                text = formatTimeOnly(session.startTime),
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Metadata label with icon and text.
 *
 * Matches iOS Label with systemImage.
 */
@Composable
private fun SessionMetadataLabel(
    icon: ImageVector,
    text: String,
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
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =============================================================================
// EMPTY STATE (matching iOS ContentUnavailableView)
// =============================================================================

/**
 * Empty state for history list.
 *
 * Matches iOS ContentUnavailableView with icon, title, and description.
 *
 * @param icon Icon to display
 * @param title Title text
 * @param description Description text
 * @param modifier Modifier to apply
 */
@Composable
fun HistoryEmptyState(
    icon: ImageVector = Icons.Default.History,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
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
    }
}

// =============================================================================
// SESSION INFO CARD (matching iOS detail view)
// =============================================================================

/**
 * Session info card for detail view.
 *
 * Matches iOS glass card with session metadata.
 *
 * @param session Session to display
 * @param modifier Modifier to apply
 */
@Composable
fun SessionInfoCard(
    session: Session,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Text(
                text = "Session Information",
                style = IOSTypography.headline,
            )

            InfoRow(label = "Session ID", value = session.id.take(8) + "...")
            session.curriculumId?.let {
                InfoRow(label = "Curriculum", value = it)
            }
            session.topicId?.let {
                InfoRow(label = "Topic", value = it)
            }
            InfoRow(
                label = "Started",
                value = formatDateTime(session.startTime),
            )
            session.endTime?.let { endTime ->
                InfoRow(
                    label = "Ended",
                    value = formatDateTime(endTime),
                )
                val durationMinutes = ((endTime - session.startTime) / 1000 / 60).toInt()
                InfoRow(label = "Duration", value = "$durationMinutes minutes")
            }
            InfoRow(label = "Total Turns", value = session.turnCount.toString())
        }
    }
}

/**
 * Info row with label and value.
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = IOSTypography.caption,
        )
    }
}

// =============================================================================
// DATE FORMATTING UTILITIES
// =============================================================================

/**
 * Format timestamp as relative date string.
 *
 * Returns "Today", "Yesterday", or formatted date.
 * Matches iOS date grouping behavior.
 */
fun formatRelativeDate(timestamp: Long): String {
    val date = Date(timestamp)
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()

    calendar.time = date
    val dateDay = calendar.get(Calendar.DAY_OF_YEAR)
    val dateYear = calendar.get(Calendar.YEAR)

    val todayDay = today.get(Calendar.DAY_OF_YEAR)
    val todayYear = today.get(Calendar.YEAR)

    return when {
        dateYear == todayYear && dateDay == todayDay -> "Today"
        dateYear == todayYear && dateDay == todayDay - 1 -> "Yesterday"
        else -> {
            val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
            formatter.format(date)
        }
    }
}

/**
 * Format timestamp as time only.
 */
private fun formatTimeOnly(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Format timestamp as full date and time.
 */
private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/**
 * Group sessions by date.
 *
 * Returns a map of date label to sessions, ordered by most recent first.
 */
fun groupSessionsByDate(sessions: List<Session>): Map<String, List<Session>> {
    return sessions
        .sortedByDescending { it.startTime }
        .groupBy { formatRelativeDate(it.startTime) }
}
