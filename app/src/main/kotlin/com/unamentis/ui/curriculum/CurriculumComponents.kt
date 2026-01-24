package com.unamentis.ui.curriculum

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.data.model.Curriculum
import com.unamentis.data.model.Topic
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGray
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange

// =============================================================================
// TOPIC STATUS (matching iOS TopicStatus enum)
// =============================================================================

/**
 * Topic status matching iOS TopicStatus.
 */
enum class TopicStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    REVIEWING,
    ;

    val displayName: String
        get() =
            when (this) {
                NOT_STARTED -> "Not Started"
                IN_PROGRESS -> "In Progress"
                COMPLETED -> "Completed"
                REVIEWING -> "Reviewing"
            }

    val accessibilityDescription: String
        get() =
            when (this) {
                NOT_STARTED -> "not started"
                IN_PROGRESS -> "in progress"
                COMPLETED -> "completed"
                REVIEWING -> "needs review"
            }
}

// =============================================================================
// STATUS ICON (matching iOS StatusIcon)
// =============================================================================

/**
 * Status icon for topic progress.
 *
 * Matches iOS StatusIcon with 32x32 circle and status-specific icon/color:
 * - Not Started: gray circle outline
 * - In Progress: blue clock
 * - Completed: green checkmark
 * - Reviewing: orange refresh
 *
 * @param status Topic status to display
 * @param modifier Modifier to apply
 * @param size Icon size (default 32.dp matching iOS)
 */
@Composable
fun StatusIcon(
    status: TopicStatus,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Dimensions.StatusIconSize,
) {
    val iconColor by animateColorAsState(
        targetValue =
            when (status) {
                TopicStatus.NOT_STARTED -> iOSGray
                TopicStatus.IN_PROGRESS -> iOSBlue
                TopicStatus.COMPLETED -> iOSGreen
                TopicStatus.REVIEWING -> iOSOrange
            },
        animationSpec = tween(durationMillis = 200),
        label = "statusIconColor",
    )

    val icon: ImageVector =
        when (status) {
            TopicStatus.NOT_STARTED -> Icons.Default.Circle
            TopicStatus.IN_PROGRESS -> Icons.Default.Schedule
            TopicStatus.COMPLETED -> Icons.Default.CheckCircle
            TopicStatus.REVIEWING -> Icons.Default.Refresh
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(iOSGray.copy(alpha = 0.1f))
                .semantics {
                    contentDescription = "Status: ${status.accessibilityDescription}"
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

// =============================================================================
// CURRICULUM ROW (matching iOS CurriculumRow)
// =============================================================================

/**
 * Curriculum list row matching iOS CurriculumRow.
 *
 * Layout:
 * - 44x44 blue circle with book icon
 * - Title (headline), summary (caption, 2 lines), topic count (caption2)
 * - Chevron indicator
 *
 * @param curriculum Curriculum to display
 * @param onClick Click handler
 * @param modifier Modifier to apply
 */
@Composable
fun CurriculumRow(
    curriculum: Curriculum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicCount = curriculum.topics.size
    val accessibilityLabel = "${curriculum.title}, $topicCount topics"

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        // Curriculum icon - 44x44 blue circle
        Box(
            modifier =
                Modifier
                    .size(Dimensions.CurriculumIconSize)
                    .clip(CircleShape)
                    .background(iOSBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
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
                text = curriculum.title,
                style = IOSTypography.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (curriculum.description.isNotEmpty()) {
                Text(
                    text = curriculum.description,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = stringResource(R.string.curriculum_topic_count, topicCount),
                style = IOSTypography.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// =============================================================================
// TOPIC ROW (matching iOS TopicRow)
// =============================================================================

/**
 * Topic list row matching iOS TopicRow.
 *
 * Layout:
 * - Status icon (32x32)
 * - Title (headline), outline (caption, 2 lines), time spent (caption2)
 * - Chevron indicator
 *
 * @param topic Topic to display
 * @param status Topic progress status
 * @param timeSpentSeconds Time spent on topic in seconds
 * @param mastery Mastery level 0.0-1.0
 * @param onClick Click handler
 * @param modifier Modifier to apply
 */
@Composable
fun TopicRow(
    topic: Topic,
    status: TopicStatus = TopicStatus.NOT_STARTED,
    timeSpentSeconds: Double = 0.0,
    mastery: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masteryPercent = (mastery * 100).toInt()
    val accessibilityLabel =
        buildString {
            append(topic.title)
            append(", Status: ${status.accessibilityDescription}")
            append(", $masteryPercent percent mastery")
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp)
                .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        // Status icon
        StatusIcon(status = status)

        // Text content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = topic.title,
                style = IOSTypography.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            topic.description?.let { description ->
                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (timeSpentSeconds > 0) {
                Text(
                    text = formatTimeSpent(timeSpentSeconds),
                    style = IOSTypography.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Format time spent as human-readable string.
 * Matches iOS formatTime implementation.
 */
private fun formatTimeSpent(seconds: Double): String {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return if (hours > 0) {
        "${hours}h ${minutes}m spent"
    } else {
        "${minutes}m spent"
    }
}

// =============================================================================
// MASTERY INDICATOR (matching iOS TopicDetailView mastery display)
// =============================================================================

/**
 * Mastery percentage indicator.
 *
 * Shows mastery as a percentage with label, matching iOS TopicDetailView style.
 *
 * @param mastery Mastery level 0.0-1.0
 * @param modifier Modifier to apply
 */
@Composable
fun MasteryIndicator(
    mastery: Float,
    modifier: Modifier = Modifier,
) {
    val masteryPercent = (mastery * 100).toInt()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$masteryPercent%",
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.mastery),
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// =============================================================================
// PROGRESS CARD (matching iOS TopicDetailView progress section)
// =============================================================================

/**
 * Progress card showing status, time spent, and mastery.
 *
 * Matches iOS TopicDetailView progress section with glass background.
 *
 * @param status Topic status
 * @param timeSpentSeconds Time spent in seconds
 * @param mastery Mastery level 0.0-1.0
 * @param modifier Modifier to apply
 */
@Composable
fun ProgressCard(
    status: TopicStatus,
    timeSpentSeconds: Double,
    mastery: Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.SpacingLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Status and time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                StatusIcon(
                    status = status,
                    size = Dimensions.StatusIconSizeLarge,
                )

                Column {
                    Text(
                        text = status.displayName,
                        style = IOSTypography.headline,
                    )
                    if (timeSpentSeconds > 0) {
                        Text(
                            text = formatTimeSpent(timeSpentSeconds),
                            style = IOSTypography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Mastery
            MasteryIndicator(mastery = mastery)
        }
    }
}

// =============================================================================
// ACTION BUTTONS (matching iOS TopicDetailView buttons)
// =============================================================================

/**
 * Start Lesson button matching iOS style.
 *
 * Blue button with waveform icon and "Start Lesson" text.
 *
 * @param onClick Click handler
 * @param modifier Modifier to apply
 */
@Composable
fun StartLessonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = iOSBlue,
                contentColor = Color.White,
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.start_lesson),
            style = IOSTypography.subheadline,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// =============================================================================
// LEARNING OBJECTIVES LIST (matching iOS TopicDetailView)
// =============================================================================

/**
 * Learning objectives list with checkmark bullets.
 *
 * Matches iOS TopicDetailView learning objectives section.
 *
 * @param objectives List of learning objectives
 * @param modifier Modifier to apply
 */
@Composable
fun LearningObjectivesList(
    objectives: List<String>,
    modifier: Modifier = Modifier,
) {
    if (objectives.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        Text(
            text = stringResource(R.string.learning_objectives),
            style = IOSTypography.headline,
        )

        objectives.forEach { objective ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = iOSGreen,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = objective,
                    style = IOSTypography.body,
                )
            }
        }
    }
}
