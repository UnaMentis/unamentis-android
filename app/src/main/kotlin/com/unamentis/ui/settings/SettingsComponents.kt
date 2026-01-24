package com.unamentis.ui.settings

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGray
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSRed

// =============================================================================
// SETTINGS SECTION (matching iOS List Section)
// =============================================================================

/**
 * iOS-style settings section with header, content, and footer.
 *
 * Matches iOS List Section styling with:
 * - Uppercase header text (caption, gray)
 * - Grouped content with rounded corners
 * - Optional footer text (caption, gray)
 *
 * @param header Section header text (will be uppercased)
 * @param footer Optional footer description text
 * @param modifier Modifier to apply
 * @param content Section content
 */
@Composable
fun SettingsSection(
    header: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        // Header (uppercase, like iOS)
        Text(
            text = header.uppercase(),
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimensions.SpacingLarge),
        )

        // Content container with iOS card styling
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.CardCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            Column {
                content()
            }
        }

        // Footer (if provided)
        footer?.let {
            Text(
                text = it,
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimensions.SpacingLarge),
            )
        }
    }
}

// =============================================================================
// SETTINGS ROW (matching iOS NavigationLink row)
// =============================================================================

/**
 * iOS-style settings row with navigation chevron.
 *
 * Matches iOS NavigationLink styling with:
 * - Leading icon (optional)
 * - Title and subtitle
 * - Trailing value/detail (optional)
 * - Chevron indicator
 *
 * @param title Row title
 * @param onClick Click handler
 * @param modifier Modifier to apply
 * @param icon Leading icon (optional)
 * @param iconTint Icon color
 * @param subtitle Subtitle text (optional)
 * @param trailingContent Trailing content before chevron (optional)
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = iOSBlue,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
            }

            // Title and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = IOSTypography.body,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Trailing content
            trailingContent?.invoke()

            // Chevron
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        // Divider (indented from leading icon)
        if (showDivider) {
            HorizontalDivider(
                modifier =
                    Modifier.padding(
                        start = if (icon != null) 52.dp else Dimensions.SpacingLarge,
                    ),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// SETTINGS TOGGLE (matching iOS Toggle)
// =============================================================================

/**
 * iOS-style settings toggle row.
 *
 * Matches iOS Toggle styling with:
 * - Title and description
 * - Trailing switch
 * - Optional leading icon
 *
 * @param title Toggle title
 * @param checked Current toggle state
 * @param onCheckedChange Toggle change handler
 * @param modifier Modifier to apply
 * @param description Description text (optional)
 * @param icon Leading icon (optional)
 * @param iconTint Icon color
 * @param enabled Whether the toggle is enabled
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = iOSBlue,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val accessibilityLabel =
        buildString {
            append(title)
            if (checked) append(", enabled") else append(", disabled")
        }

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    )
                    .semantics { contentDescription = accessibilityLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) iconTint else iOSGray,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
            }

            // Title and description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = IOSTypography.body,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                description?.let {
                    Text(
                        text = it,
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Switch
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier =
                    Modifier.padding(
                        start = if (icon != null) 52.dp else Dimensions.SpacingLarge,
                    ),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// SETTINGS SLIDER (matching iOS Slider with value display)
// =============================================================================

/**
 * iOS-style settings slider row.
 *
 * Matches iOS Slider styling with:
 * - Title and current value display on same line
 * - Slider control
 * - Optional description below
 *
 * @param title Slider title
 * @param value Current slider value
 * @param onValueChange Value change handler
 * @param valueRange Range for slider
 * @param modifier Modifier to apply
 * @param valueFormatter Format function for display value
 * @param description Description text (optional)
 * @param steps Number of discrete steps (optional, 0 for continuous)
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { "%.2f".format(it) },
    description: String? = null,
    steps: Int = 0,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    ),
        ) {
            // Title and value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = IOSTypography.body,
                )
                Text(
                    text = valueFormatter(value),
                    style = IOSTypography.body,
                    color = iOSBlue,
                )
            }

            // Slider
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
            )

            // Description
            description?.let {
                Text(
                    text = it,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// API KEY ROW (matching iOS APIKeyRow)
// =============================================================================

/**
 * API provider category for icon display.
 */
enum class ApiCategory(
    val icon: ImageVector,
    val color: Color,
    val shortLabel: String,
) {
    STT(Icons.Default.Circle, iOSBlue, "STT"),
    TTS(Icons.Default.Circle, iOSGreen, "TTS"),
    LLM(Icons.Default.Circle, iOSOrange, "LLM"),
}

/**
 * iOS-style API key row with category icons.
 *
 * Matches iOS APIKeyRow with:
 * - Category icons (colored dots)
 * - Provider name
 * - Category labels
 * - Status indicator (checkmark or dashed circle)
 *
 * @param name Provider name
 * @param categories List of API categories this provider supports
 * @param isConfigured Whether API key is configured
 * @param onClick Click handler
 * @param modifier Modifier to apply
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun ApiKeyRow(
    name: String,
    categories: List<ApiCategory>,
    isConfigured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val statusText = if (isConfigured) "Ready" else "Not set"
    val statusColor = if (isConfigured) iOSGreen else iOSOrange
    val accessibilityLabel =
        "$name, ${categories.joinToString(" and ") { it.shortLabel }}, $statusText"

    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    )
                    .semantics { contentDescription = accessibilityLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Category icons (44dp wide, left-aligned like iOS)
            Row(
                modifier = Modifier.width(44.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                categories.forEach { category ->
                    Box(
                        modifier =
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(category.color),
                    )
                }
            }

            // Name and category labels
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = IOSTypography.body,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Category labels
                    Text(
                        text = categories.joinToString(" + ") { it.shortLabel },
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "•",
                        style = IOSTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Status
                    Text(
                        text = statusText,
                        style = IOSTypography.caption,
                        color = statusColor,
                    )
                }
            }

            // Status icon
            Icon(
                imageVector =
                    if (isConfigured) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Circle
                    },
                contentDescription = statusText,
                tint = if (isConfigured) iOSGreen else iOSGray,
                modifier = Modifier.size(20.dp),
            )

            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// CONNECTION STATUS (matching iOS ServerConnectionStatus)
// =============================================================================

/**
 * Connection status enum matching iOS ServerConnectionStatus.
 */
enum class ConnectionStatus(
    val color: Color,
    val label: String,
) {
    NOT_CONFIGURED(iOSGray, "Not configured"),
    CHECKING(iOSOrange, "Checking..."),
    CONNECTED(iOSGreen, "Connected"),
    FAILED(iOSRed, "Failed"),
}

/**
 * iOS-style connection status row.
 *
 * Shows connection status with colored dot and label.
 *
 * @param title Row title
 * @param status Connection status
 * @param modifier Modifier to apply
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun ConnectionStatusRow(
    title: String,
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = IOSTypography.body,
                modifier = Modifier.weight(1f),
            )

            // Status indicator
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(status.color),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = status.label,
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// SETTINGS VALUE ROW (matching iOS HStack with label and value)
// =============================================================================

/**
 * iOS-style settings value row for displaying key-value pairs.
 *
 * @param label Row label
 * @param value Row value
 * @param modifier Modifier to apply
 * @param valueColor Value text color
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun SettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = IOSTypography.body,
            )
            Text(
                text = value,
                style = IOSTypography.body,
                color = valueColor,
            )
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = Dimensions.SpacingLarge),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// PROVIDER PICKER ROW (matching iOS Picker)
// =============================================================================

/**
 * iOS-style provider picker row.
 *
 * Shows current selection with chevron for navigation to picker.
 *
 * @param title Row title
 * @param selectedValue Current selected value
 * @param onClick Click handler to open picker
 * @param modifier Modifier to apply
 * @param icon Leading icon (optional)
 * @param iconTint Icon color
 * @param showDivider Whether to show bottom divider
 */
@Composable
fun ProviderPickerRow(
    title: String,
    selectedValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = iOSBlue,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = Dimensions.SpacingLarge,
                        vertical = Dimensions.SpacingMedium,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
            }

            // Title
            Text(
                text = title,
                style = IOSTypography.body,
                modifier = Modifier.weight(1f),
            )

            // Selected value
            Text(
                text = selectedValue,
                style = IOSTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Chevron
            Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier =
                    Modifier.padding(
                        start = if (icon != null) 52.dp else Dimensions.SpacingLarge,
                    ),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

// =============================================================================
// SECTION HEADER (for inline section headers)
// =============================================================================

/**
 * iOS-style section header text.
 *
 * @param text Header text
 * @param modifier Modifier to apply
 */
@Composable
fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = IOSTypography.caption,
        fontWeight = FontWeight.Medium,
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
