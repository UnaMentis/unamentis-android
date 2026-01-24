package com.unamentis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue

/**
 * Help bottom sheet for screen-specific help content.
 *
 * Matches iOS pattern of contextual help sheets that appear from the bottom.
 * Provides a consistent structure for displaying help information with
 * tips, instructions, and feature explanations.
 *
 * @param title The title of the help sheet
 * @param description A brief description of the screen or feature
 * @param tips List of helpful tips to display
 * @param onDismiss Callback when the sheet is dismissed
 * @param sheetState The state of the modal bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(
    title: String,
    description: String,
    tips: List<HelpTip>,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                    .padding(bottom = Dimensions.SpacingXXLarge)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = iOSBlue,
                    modifier = Modifier.size(Dimensions.IconSizeLarge),
                )
                Text(
                    text = title,
                    style = IOSTypography.title2,
                )
            }

            // Description
            Text(
                text = description,
                style = IOSTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // Tips section
            if (tips.isNotEmpty()) {
                Text(
                    text = "Tips",
                    style = IOSTypography.headline,
                )

                tips.forEach { tip ->
                    HelpTipRow(tip = tip)
                }
            }
        }
    }
}

/**
 * Data class representing a help tip.
 *
 * @param icon The icon to display (defaults to lightbulb)
 * @param title The title of the tip
 * @param description The detailed description
 */
data class HelpTip(
    val icon: ImageVector = Icons.Default.Lightbulb,
    val title: String,
    val description: String,
)

/**
 * Individual help tip row component.
 */
@Composable
private fun HelpTipRow(tip: HelpTip) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        Icon(
            imageVector = tip.icon,
            contentDescription = null,
            tint = iOSBlue,
            modifier = Modifier.size(Dimensions.IconSizeMedium),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
        ) {
            Text(
                text = tip.title,
                style = IOSTypography.subheadline,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tip.description,
                style = IOSTypography.footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Simple help sheet with just a list of bullet points.
 *
 * For simpler help content that doesn't need structured tips.
 *
 * @param title The title of the help sheet
 * @param points List of help points to display
 * @param onDismiss Callback when the sheet is dismissed
 * @param sheetState The state of the modal bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleHelpSheet(
    title: String,
    points: List<String>,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding)
                    .padding(bottom = Dimensions.SpacingXXLarge)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = iOSBlue,
                    modifier = Modifier.size(Dimensions.IconSizeLarge),
                )
                Text(
                    text = title,
                    style = IOSTypography.title2,
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))

            // Points
            points.forEach { point ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = iOSBlue,
                        modifier = Modifier.size(Dimensions.IconSizeSmall),
                    )
                    Text(
                        text = point,
                        style = IOSTypography.body,
                    )
                }
            }
        }
    }
}
