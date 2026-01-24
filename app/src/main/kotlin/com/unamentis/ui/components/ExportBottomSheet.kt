package com.unamentis.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.unamentis.R
import com.unamentis.core.export.ExportFormat
import com.unamentis.core.export.ExportResult
import com.unamentis.ui.theme.IOSTypography
import java.io.File

/**
 * Bottom sheet for selecting export format and sharing session data.
 *
 * Features:
 * - Format selection (JSON, Text, Markdown, CSV)
 * - Direct share to other apps
 * - Preview of exported content
 *
 * @param onDismiss Called when the sheet is dismissed
 * @param onExport Called with the selected format to perform the export
 * @param exportResult The result of the export operation (for displaying content/errors)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    onDismiss: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    exportResult: ExportResult?,
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedFormat by remember { mutableStateOf(ExportFormat.TEXT) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.export_session_title),
                style = IOSTypography.title2,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Format selection
            Text(
                text = stringResource(R.string.export_select_format),
                style = IOSTypography.subheadline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormatCard(
                    format = ExportFormat.TEXT,
                    icon = Icons.Default.Description,
                    isSelected = selectedFormat == ExportFormat.TEXT,
                    onClick = { selectedFormat = ExportFormat.TEXT },
                    modifier = Modifier.weight(1f),
                )
                FormatCard(
                    format = ExportFormat.JSON,
                    icon = Icons.Default.Code,
                    isSelected = selectedFormat == ExportFormat.JSON,
                    onClick = { selectedFormat = ExportFormat.JSON },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormatCard(
                    format = ExportFormat.MARKDOWN,
                    icon = Icons.Default.Description,
                    isSelected = selectedFormat == ExportFormat.MARKDOWN,
                    onClick = { selectedFormat = ExportFormat.MARKDOWN },
                    modifier = Modifier.weight(1f),
                )
                FormatCard(
                    format = ExportFormat.CSV,
                    icon = Icons.Default.TableChart,
                    isSelected = selectedFormat == ExportFormat.CSV,
                    onClick = { selectedFormat = ExportFormat.CSV },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error display
            if (exportResult is ExportResult.Error) {
                Text(
                    text = exportResult.message,
                    style = IOSTypography.body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onExport(selectedFormat)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.export_preview))
                }

                Button(
                    onClick = {
                        onExport(selectedFormat)
                        // After export, share if successful
                        if (exportResult is ExportResult.Success) {
                            shareExportedContent(context, exportResult)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_share))
                }
            }

            // Preview area (if export result available)
            if (exportResult is ExportResult.Success) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.export_preview_format, exportResult.format.displayName),
                    style = IOSTypography.subheadline,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            exportResult.content.take(500) +
                                if (exportResult.content.length > 500) "\n..." else "",
                        style = IOSTypography.caption,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatCard(
    format: ExportFormat,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = format.displayName,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = format.displayName,
                style = IOSTypography.caption2,
                textAlign = TextAlign.Center,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

/**
 * Shares the exported content via Android's share sheet.
 */
private fun shareExportedContent(
    context: Context,
    result: ExportResult.Success,
) {
    val shareSubject = context.getString(R.string.export_share_subject)
    val shareChooserTitle = context.getString(R.string.export_share_chooser_title)

    try {
        // Write content to a temporary file
        val cacheDir = File(context.cacheDir, "exports")
        cacheDir.mkdirs()
        val file = File(cacheDir, result.suggestedFilename)
        file.writeText(result.content)

        // Create content URI via FileProvider
        val contentUri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

        // Create share intent
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = result.format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        // Launch share chooser
        context.startActivity(
            Intent.createChooser(shareIntent, shareChooserTitle),
        )
    } catch (e: Exception) {
        // Fall back to plain text share when file sharing fails
        android.util.Log.w("ExportBottomSheet", "File share failed, falling back to plain text: ${e.message}", e)
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, result.content)
                putExtra(Intent.EXTRA_SUBJECT, shareSubject)
            }
        context.startActivity(
            Intent.createChooser(shareIntent, shareChooserTitle),
        )
    }
}
