package com.unamentis.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGray

/**
 * Topic progress bar showing progress through a curriculum topic.
 *
 * Matches iOS TopicProgressBar with a thin progress track and segment counter.
 * Used during curriculum playback to show how far through the lesson the user is.
 *
 * @param completedSegments Number of segments completed
 * @param totalSegments Total number of segments in topic
 * @param modifier Modifier to apply
 */
@Composable
fun TopicProgressBar(
    completedSegments: Int,
    totalSegments: Int,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (totalSegments > 0) {
            completedSegments.toFloat() / totalSegments.toFloat()
        } else {
            0f
        }

    val progressPercentage = (progress * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "topicProgress",
    )

    val accessibilityDesc =
        stringResource(
            R.string.topic_progress,
            completedSegments,
            totalSegments,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = accessibilityDesc },
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
    ) {
        // Progress bar track
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.TopicProgressHeight)
                    .clip(RoundedCornerShape(Dimensions.TopicProgressCornerRadius))
                    .background(iOSGray.copy(alpha = 0.2f)),
        ) {
            // Progress fill
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = animatedProgress)
                        .height(Dimensions.TopicProgressHeight)
                        .clip(RoundedCornerShape(Dimensions.TopicProgressCornerRadius))
                        .background(iOSBlue),
            )
        }

        // Progress text (minimal, right-aligned like iOS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "$completedSegments/$totalSegments",
                style = IOSTypography.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact topic progress indicator for tight spaces.
 *
 * Shows just the segment count without the progress bar.
 *
 * @param completedSegments Number of segments completed
 * @param totalSegments Total number of segments in topic
 * @param modifier Modifier to apply
 */
@Composable
fun TopicProgressIndicator(
    completedSegments: Int,
    totalSegments: Int,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (totalSegments > 0) {
            completedSegments.toFloat() / totalSegments.toFloat()
        } else {
            0f
        }

    val progressPercentage = (progress * 100).toInt()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        // Mini progress bar
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(iOSGray.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = progress)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(iOSBlue),
            )
        }

        Text(
            text = "$progressPercentage%",
            style = IOSTypography.caption2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
