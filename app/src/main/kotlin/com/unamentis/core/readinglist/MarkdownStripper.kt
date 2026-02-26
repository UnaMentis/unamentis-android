package com.unamentis.core.readinglist

/**
 * Strips markdown formatting to produce plain text suitable for TTS.
 *
 * Implements a 17-step processing pipeline matching iOS MarkdownStripper.
 */
object MarkdownStripper {
    /**
     * Strip all markdown formatting from text.
     *
     * @param markdown Raw markdown text
     * @return Plain text with formatting removed
     */
    fun strip(markdown: String): String {
        var text = markdown

        // 1. Remove YAML front matter
        text = removeYamlFrontMatter(text)

        // 2. Remove HTML comments
        text = text.replace(Regex("<!--[\\s\\S]*?-->"), "")

        // 3. Remove fenced code blocks (preserve inner text)
        text = text.replace(Regex("```[^\\n]*\\n([\\s\\S]*?)```"), "$1")

        // 4. Remove images (keep alt text)
        text =
            text.replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)")) { match ->
                val alt = match.groupValues[1]
                if (alt.isNotBlank()) "$alt." else ""
            }

        // 5. Convert links to display text only
        text = text.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")

        // 6. Remove reference-style link definitions
        text = text.replace(Regex("^\\[\\^?[^\\]]+\\]:\\s+.*$", RegexOption.MULTILINE), "")

        // 7. Remove emphasis (bold, italic, strikethrough)
        text = text.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "$1") // ***bold italic***
        text = text.replace(Regex("___(.+?)___"), "$1") // ___bold italic___
        text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1") // **bold**
        text = text.replace(Regex("__(.+?)__"), "$1") // __bold__
        text = text.replace(Regex("\\*(.+?)\\*"), "$1") // *italic*
        text = text.replace(Regex("_(.+?)_"), "$1") // _italic_
        text = text.replace(Regex("~~(.+?)~~"), "$1") // ~~strikethrough~~

        // 8. Remove inline code backticks
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // 9. Convert headers to plain text with paragraph breaks
        text = text.replace(Regex("^#{1,6}\\s+(.*)$", RegexOption.MULTILINE), "\n$1\n")

        // 10. Remove blockquote markers
        text = text.replace(Regex("^>\\s?", RegexOption.MULTILINE), "")

        // 11. Remove horizontal rules
        text = text.replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "")

        // 12. Remove list markers
        text = text.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        text = text.replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")

        // 13. Remove footnote references
        text = text.replace(Regex("\\[\\^\\d+\\]"), "")

        // 14. Remove footnote definitions
        text = text.replace(Regex("^\\[\\^\\d+\\]:\\s+.*$", RegexOption.MULTILINE), "")

        // 15. Strip inline HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // 16. Decode HTML entities
        text = decodeHtmlEntities(text)

        // 17. Normalize whitespace
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        text = text.replace(Regex(" {2,}"), " ")
        text = text.trim()

        return text
    }

    private fun removeYamlFrontMatter(text: String): String {
        if (!text.startsWith("---")) return text
        val endIndex = text.indexOf("---", 3)
        if (endIndex == -1) return text
        return text.substring(endIndex + 3).trimStart()
    }

    private fun decodeHtmlEntities(text: String): String {
        var result = text
        result = result.replace("&amp;", "&")
        result = result.replace("&lt;", "<")
        result = result.replace("&gt;", ">")
        result = result.replace("&quot;", "\"")
        result = result.replace("&apos;", "'")
        result = result.replace("&nbsp;", " ")

        // Decode numeric entities (&#123; and &#x7B;)
        result =
            result.replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) String(Character.toChars(code)) else match.value
            }
        result =
            result.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) String(Character.toChars(code)) else match.value
            }

        return result
    }
}
