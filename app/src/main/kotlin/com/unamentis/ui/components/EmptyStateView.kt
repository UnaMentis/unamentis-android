package com.unamentis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography

/**
 * Empty state view matching iOS ContentUnavailableView.
 *
 * Displays a centered message with icon, title, description, and optional action button.
 * Used when lists are empty, searches return no results, or content is unavailable.
 *
 * iOS Reference: ContentUnavailableView(title:systemImage:description:actions:)
 *
 * @param icon The icon to display (SF Symbols equivalent)
 * @param title The main title text
 * @param description Optional description text
 * @param modifier Modifier to apply to the container
 * @param iconTint Color for the icon (defaults to secondary text color)
 * @param actionButton Optional action button composable
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    actionButton: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                    .semantics { contentDescription = title + (description?.let { ". $it" } ?: "") },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.EmptyStateIconSize),
                tint = iconTint,
            )

            // Title
            Text(
                text = title,
                style = IOSTypography.title3,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            // Description (optional)
            if (description != null) {
                Text(
                    text = description,
                    style = IOSTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Action button (optional)
            if (actionButton != null) {
                Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
                actionButton()
            }
        }
    }
}

/**
 * Empty state view with a primary action button.
 *
 * Convenience wrapper that includes a standard styled action button.
 *
 * @param icon The icon to display
 * @param title The main title text
 * @param description Optional description text
 * @param actionLabel The label for the action button
 * @param onAction Callback when action button is clicked
 * @param modifier Modifier to apply to the container
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    EmptyStateView(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier,
        actionButton = {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
            ) {
                Text(actionLabel)
            }
        },
    )
}

/**
 * Search empty state view.
 *
 * Specialized empty state for search results when no matches are found.
 * Matches iOS pattern: ContentUnavailableView.search(text:)
 *
 * @param searchQuery The search query that returned no results
 * @param modifier Modifier to apply to the container
 */
@Composable
fun SearchEmptyStateView(
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    EmptyStateView(
        icon = Icons.Default.Search,
        title = "No Results",
        description = "No results for \"$searchQuery\"",
        modifier = modifier,
    )
}

/**
 * Loading empty state view.
 *
 * Displays while content is being loaded. Shows a progress indicator
 * instead of an icon.
 *
 * @param message Loading message to display
 * @param modifier Modifier to apply to the container
 */
@Composable
fun LoadingStateView(
    message: String = "Loading...",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(Dimensions.EmptyStateIconSize / 2),
            )
            Text(
                text = message,
                style = IOSTypography.subheadline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Error state view.
 *
 * Displays when an error occurs, with optional retry action.
 *
 * @param icon The error icon to display
 * @param title Error title
 * @param description Error description or message
 * @param onRetry Optional retry callback
 * @param modifier Modifier to apply to the container
 */
@Composable
fun ErrorStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    EmptyStateView(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier,
        iconTint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
        actionButton =
            if (onRetry != null) {
                {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(Dimensions.ButtonCornerRadius),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Try Again")
                    }
                }
            } else {
                null
            },
    )
}
