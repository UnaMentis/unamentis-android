package com.unamentis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Data class representing a swipe action.
 *
 * @param icon The icon to display
 * @param label Accessibility label for the action
 * @param backgroundColor Background color of the action
 * @param iconTint Tint color for the icon
 * @param onClick Callback when the action is triggered
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val backgroundColor: Color,
    val iconTint: Color = Color.White,
    val onClick: () -> Unit,
)

/**
 * Swipeable list item with trailing actions.
 *
 * Matches iOS pattern of swiping left to reveal action buttons.
 * Supports multiple actions with colored backgrounds.
 *
 * @param modifier Modifier to apply to the container
 * @param trailingActions List of actions to show when swiping left
 * @param leadingActions List of actions to show when swiping right (optional)
 * @param content The main content of the list item
 */
@Composable
fun SwipeableListItem(
    modifier: Modifier = Modifier,
    trailingActions: List<SwipeAction> = emptyList(),
    leadingActions: List<SwipeAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidth = with(density) { Dimensions.SwipeActionThreshold.toPx() }
    val trailingMaxOffset = -(actionWidth * trailingActions.size)
    val leadingMaxOffset = actionWidth * leadingActions.size

    var offsetX by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val draggableState =
        rememberDraggableState { delta ->
            val newOffset = (offsetX + delta).coerceIn(trailingMaxOffset, leadingMaxOffset)
            offsetX = newOffset
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clipToBounds(),
    ) {
        // Trailing actions (revealed when swiping left)
        if (trailingActions.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.End,
            ) {
                trailingActions.forEach { action ->
                    SwipeActionButton(
                        action = action,
                        visible = offsetX < 0,
                    )
                }
            }
        }

        // Leading actions (revealed when swiping right)
        if (leadingActions.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterStart),
            ) {
                leadingActions.forEach { action ->
                    SwipeActionButton(
                        action = action,
                        visible = offsetX > 0,
                    )
                }
            }
        }

        // Main content
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = {
                            coroutineScope.launch {
                                // Snap to nearest position
                                val threshold = actionWidth / 2
                                offsetX =
                                    when {
                                        offsetX < -threshold * trailingActions.size -> trailingMaxOffset
                                        offsetX > threshold * leadingActions.size -> leadingMaxOffset
                                        else -> 0f
                                    }
                            }
                        },
                    ),
        ) {
            content()
        }
    }
}

/**
 * Individual swipe action button.
 */
@Composable
private fun SwipeActionButton(
    action: SwipeAction,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
    ) {
        Box(
            modifier =
                Modifier
                    .width(Dimensions.SwipeActionThreshold)
                    .fillMaxHeight()
                    .background(action.backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = action.onClick) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = action.iconTint,
                    modifier = Modifier.size(Dimensions.SwipeActionIconSize),
                )
            }
        }
    }
}

/**
 * Pre-built delete swipe action.
 *
 * @param label Localized label for the action
 * @param onClick Callback when delete is triggered
 */
fun deleteSwipeAction(
    label: String,
    onClick: () -> Unit,
) = SwipeAction(
    icon = Icons.Default.Delete,
    label = label,
    backgroundColor = iOSRed,
    onClick = onClick,
)

/**
 * Pre-built star/favorite swipe action.
 *
 * @param starLabel Localized label for star action
 * @param unstarLabel Localized label for unstar action
 * @param isStarred Current starred state
 * @param onClick Callback when star is triggered
 */
fun starSwipeAction(
    starLabel: String,
    unstarLabel: String,
    isStarred: Boolean,
    onClick: () -> Unit,
) = SwipeAction(
    icon = Icons.Default.Star,
    label = if (isStarred) unstarLabel else starLabel,
    backgroundColor = iOSOrange,
    onClick = onClick,
)

/**
 * Pre-built edit swipe action.
 *
 * @param label Localized label for the action
 * @param onClick Callback when edit is triggered
 */
fun editSwipeAction(
    label: String,
    onClick: () -> Unit,
) = SwipeAction(
    icon = Icons.Default.Edit,
    label = label,
    backgroundColor = iOSBlue,
    onClick = onClick,
)

/**
 * Pre-built archive swipe action.
 *
 * @param label Localized label for the action
 * @param onClick Callback when archive is triggered
 */
fun archiveSwipeAction(
    label: String,
    onClick: () -> Unit,
) = SwipeAction(
    icon = Icons.Default.Archive,
    label = label,
    backgroundColor = iOSGreen,
    onClick = onClick,
)

/**
 * Simple swipeable row for common delete use case.
 *
 * @param onDelete Callback when delete is triggered
 * @param modifier Modifier to apply
 * @param content The main content of the row
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val deleteLabel = stringResource(R.string.delete)
    SwipeableListItem(
        modifier = modifier,
        trailingActions = listOf(deleteSwipeAction(label = deleteLabel, onClick = onDelete)),
        content = content,
    )
}
