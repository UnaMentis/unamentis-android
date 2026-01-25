package com.unamentis.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.unamentis.ui.theme.Dimensions

/**
 * Standardized top app bar for UnaMentis screens.
 *
 * Provides iOS-parity toolbar styling with:
 * - BrandLogo in the leading position (for main screens)
 * - Back button (for detail screens)
 * - Optional help button
 * - Consistent styling across the app
 *
 * @param title The title to display
 * @param modifier Modifier to apply to the app bar
 * @param showBackButton Whether to show a back button instead of the logo
 * @param onBackClick Callback when back button is clicked (required if showBackButton is true)
 * @param showHelpButton Whether to show the help button
 * @param onHelpClick Callback when help button is clicked
 * @param actions Additional action buttons to display
 * @param colors Custom colors for the app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnaMentisTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    showHelpButton: Boolean = false,
    onHelpClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(title) },
        navigationIcon = {
            if (showBackButton && onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            } else {
                BrandLogo(
                    size = Size.Compact,
                    modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                )
            }
        },
        actions = {
            actions()
            if (showHelpButton && onHelpClick != null) {
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = colors,
    )
}

/**
 * Variant of UnaMentisTopAppBar with custom title content.
 *
 * Use this when you need more control over the title layout,
 * such as including subtitles or custom content.
 *
 * @param title Composable content for the title area
 * @param modifier Modifier to apply to the app bar
 * @param showBackButton Whether to show a back button instead of the logo
 * @param onBackClick Callback when back button is clicked
 * @param showHelpButton Whether to show the help button
 * @param onHelpClick Callback when help button is clicked
 * @param actions Additional action buttons to display
 * @param colors Custom colors for the app bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnaMentisTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    showHelpButton: Boolean = false,
    onHelpClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = {
            if (showBackButton && onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            } else {
                BrandLogo(
                    size = Size.Compact,
                    modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                )
            }
        },
        actions = {
            actions()
            if (showHelpButton && onHelpClick != null) {
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = colors,
    )
}
