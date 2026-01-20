package com.unamentis.core.export

/**
 * Supported export formats for sessions and analytics data.
 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
) {
    JSON("json", "application/json", "JSON"),
    TEXT("txt", "text/plain", "Plain Text"),
    MARKDOWN("md", "text/markdown", "Markdown"),
    CSV("csv", "text/csv", "CSV"),
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
