package com.unamentis.core.readinglist

import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

/**
 * Extracts article content from HTML documents.
 *
 * Uses Jsoup to parse HTML and extract the main content area,
 * stripping navigation, ads, and non-content elements.
 *
 * Maps to iOS HTMLArticleExtractor (Mozilla Readability algorithm).
 */
object HTMLArticleExtractor {
    private const val TAG = "HTMLArticleExtractor"

    /** Non-content CSS selectors to remove. */
    private val REMOVE_SELECTORS =
        listOf(
            "nav", "footer", "header", "aside",
            ".sidebar", ".navigation", ".nav", ".menu",
            ".comments", ".comment", ".ad", ".ads", ".advertisement",
            ".social", ".share", ".related", ".recommended",
            "[role=navigation]", "[role=banner]", "[role=complementary]",
            "script", "style", "noscript", "iframe",
        )

    /** Content area CSS selectors (in priority order). */
    private val CONTENT_SELECTORS =
        listOf(
            "article",
            "[role=main]",
            "main",
            ".post-content",
            ".entry-content",
            ".article-content",
            ".content",
            ".post",
            ".entry",
        )

    /**
     * Extracted article content.
     */
    data class ArticleContent(
        val title: String,
        val author: String? = null,
        val text: String,
    )

    /**
     * Extract article content from raw HTML.
     *
     * @param html Raw HTML string
     * @param baseUrl Base URL for resolving relative links
     * @return Extracted article content, or null if extraction fails
     */
    fun extract(
        html: String,
        baseUrl: String? = null,
    ): ArticleContent? {
        return try {
            val doc =
                if (baseUrl != null) {
                    Jsoup.parse(html, baseUrl)
                } else {
                    Jsoup.parse(html)
                }

            val title = extractTitle(doc)
            val author = extractAuthor(doc)

            // Remove non-content elements
            REMOVE_SELECTORS.forEach { selector ->
                try {
                    doc.select(selector).remove()
                } catch (e: Exception) {
                    // Ignore invalid selectors
                }
            }

            // Try to find main content area
            val contentText = extractContentArea(doc)

            if (contentText.isNullOrBlank() || contentText.length < 100) {
                Log.w(TAG, "Content extraction produced too little text (${contentText?.length ?: 0} chars)")
                return null
            }

            val cleanedText = postProcess(contentText)

            ArticleContent(
                title = title,
                author = author,
                text = cleanedText,
            )
        } catch (e: Exception) {
            Log.e(TAG, "HTML extraction failed: ${e.message}", e)
            null
        }
    }

    private fun extractTitle(doc: Document): String {
        // Try og:title, then <title> tag, then first h1
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
        if (!ogTitle.isNullOrBlank()) return ogTitle

        val titleTag = doc.title()
        if (titleTag.isNotBlank()) return titleTag

        return doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: "Untitled"
    }

    private fun extractAuthor(doc: Document): String? {
        // Try meta author tag
        val metaAuthor = doc.selectFirst("meta[name=author]")?.attr("content")
        if (!metaAuthor.isNullOrBlank()) return metaAuthor

        // Try og:article:author
        val ogAuthor = doc.selectFirst("meta[property=article:author]")?.attr("content")
        if (!ogAuthor.isNullOrBlank()) return ogAuthor

        return null
    }

    private fun extractContentArea(doc: Document): String? {
        // Try each content selector in priority order
        for (selector in CONTENT_SELECTORS) {
            try {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    val text = cleanHtmlToText(element.html())
                    if (text.length >= 100) {
                        Log.d(TAG, "Found content via selector: $selector")
                        return text
                    }
                }
            } catch (e: Exception) {
                // Continue to next selector
            }
        }

        // Fallback: use body text
        val bodyText = cleanHtmlToText(doc.body()?.html() ?: "")
        return bodyText.ifBlank { null }
    }

    private fun cleanHtmlToText(html: String): String {
        // Convert HTML to plain text, preserving paragraph breaks
        val clean = Jsoup.clean(html, Safelist.none())
        return clean
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
    }

    private fun postProcess(text: String): String {
        return text
            // Remove footnote markers like [1], [2]
            .replace(Regex("\\[\\d+\\]"), "")
            // Collapse multiple newlines
            .replace(Regex("\\n{3,}"), "\n\n")
            // Collapse multiple spaces
            .replace(Regex(" {2,}"), " ")
            .trim()
    }
}
