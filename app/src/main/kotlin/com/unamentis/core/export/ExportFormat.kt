package com.unamentis.core.export

import androidx.annotation.StringRes
import com.unamentis.R

/**
 * Supported export formats for sessions and analytics data.
 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    @StringRes val labelResId: Int,
) {
    JSON("json", "application/json", R.string.export_format_json),
    TEXT("txt", "text/plain", R.string.export_format_text),
    MARKDOWN("md", "text/markdown", R.string.export_format_markdown),
    CSV("csv", "text/csv", R.string.export_format_csv),
}

/**
 * Result of an export operation.
 */
sealed class ExportResult {
    /**
     * Export was successful.
     *
     * @param content The exported content as a string
     * @param format The format used for export
     * @param suggestedFilename A suggested filename for saving
     */
    data class Success(
        val content: String,
        val format: ExportFormat,
        val suggestedFilename: String,
    ) : ExportResult()

    /**
     * Export failed with an error.
     *
     * @param message Error message describing what went wrong
     */
    data class Error(val message: String) : ExportResult()
}
