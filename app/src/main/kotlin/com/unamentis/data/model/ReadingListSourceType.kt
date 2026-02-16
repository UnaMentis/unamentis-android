package com.unamentis.data.model

/**
 * Source type for reading list items.
 *
 * Indicates how the content was originally imported into the reading list.
 */
enum class ReadingListSourceType(val rawValue: String) {
    PDF("pdf"),
    PLAIN_TEXT("text"),
    MARKDOWN("markdown"),
    WEB_ARTICLE("web_article"),
    ;

    companion object {
        /**
         * Create from raw string value.
         */
        fun fromRawValue(value: String): ReadingListSourceType {
            return entries.firstOrNull { it.rawValue == value } ?: PLAIN_TEXT
        }

        /**
         * Infer source type from a file extension.
         */
        fun fromFileExtension(extension: String): ReadingListSourceType {
            return when (extension.lowercase()) {
                "pdf" -> PDF
                "txt" -> PLAIN_TEXT
                "md", "markdown" -> MARKDOWN
                "html", "htm" -> WEB_ARTICLE
                else -> PLAIN_TEXT
            }
        }

        /**
         * Infer source type from a URL string.
         */
        fun fromUrl(url: String): ReadingListSourceType {
            val lower = url.lowercase()
            return when {
                lower.endsWith(".pdf") -> PDF
                lower.endsWith(".md") || lower.endsWith(".markdown") -> MARKDOWN
                lower.endsWith(".txt") -> PLAIN_TEXT
                lower.startsWith("http://") || lower.startsWith("https://") -> WEB_ARTICLE
                else -> PLAIN_TEXT
            }
        }
    }
}
