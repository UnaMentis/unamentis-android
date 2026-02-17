package com.unamentis.core.readinglist

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches web articles and extracts their content.
 *
 * Validates URLs, fetches HTML, detects encoding, and delegates
 * to [HTMLArticleExtractor] for content extraction.
 *
 * Maps to iOS WebArticleFetcher.
 */
@Singleton
class WebArticleFetcher
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        companion object {
            private const val TAG = "WebArticleFetcher"
            private const val MAX_CONTENT_SIZE = 5 * 1024 * 1024 // 5 MB
            private const val MIN_CONTENT_LENGTH = 100
            private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        /** Valid content types for article fetching. */
        private val validContentTypes =
            setOf(
                "text/html",
                "application/xhtml+xml",
                "application/xhtml",
                "text/plain",
            )

        /**
         * Fetched web article content.
         */
        data class WebArticleContent(
            val url: String,
            val title: String,
            val author: String? = null,
            val text: String,
            val fetchedAt: Long = System.currentTimeMillis(),
        )

        /**
         * Fetch and extract content from a URL.
         *
         * @param url URL to fetch
         * @return Extracted article content
         * @throws WebFetchException if fetching or extraction fails
         */
        suspend fun fetch(url: String): WebArticleContent {
            validateUrl(url)
            Log.i(TAG, "Fetching article from: $url")

            val response = executeRequest(url)
            return response.use { resp ->
                validateResponseStatus(resp)
                validateResponseContentType(resp)
                val html = readResponseBody(resp)
                extractArticle(html, url)
            }
        }

        private fun validateUrl(url: String) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw WebFetchException.InvalidUrl("URL must start with http:// or https://")
            }
        }

        private fun executeRequest(url: String): okhttp3.Response {
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,text/plain")
                    .build()
            return try {
                okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                throw WebFetchException.NetworkError("Failed to fetch URL: ${e.message}")
            }
        }

        private fun validateResponseStatus(resp: okhttp3.Response) {
            if (!resp.isSuccessful) {
                throw WebFetchException.HttpError(resp.code, "HTTP ${resp.code}: ${resp.message}")
            }
        }

        private fun validateResponseContentType(resp: okhttp3.Response) {
            val contentType = resp.header("Content-Type") ?: "text/html"
            val mimeType = contentType.split(";").firstOrNull()?.trim()?.lowercase() ?: ""
            if (validContentTypes.none { mimeType.contains(it) }) {
                throw WebFetchException.InvalidContentType(mimeType)
            }
        }

        private fun readResponseBody(resp: okhttp3.Response): String {
            val contentType = resp.header("Content-Type") ?: "text/html"
            val charset = detectCharset(contentType)
            val bodyBytes =
                resp.body?.bytes()
                    ?: throw WebFetchException.EmptyResponse()
            if (bodyBytes.size > MAX_CONTENT_SIZE) {
                throw WebFetchException.ContentTooLarge(bodyBytes.size.toLong())
            }
            return String(bodyBytes, charset)
        }

        private fun extractArticle(
            html: String,
            url: String,
        ): WebArticleContent {
            val article =
                HTMLArticleExtractor.extract(html, url)
                    ?: throw WebFetchException.ExtractionFailed("Could not extract article content")
            if (article.text.length < MIN_CONTENT_LENGTH) {
                throw WebFetchException.ExtractionFailed(
                    "Extracted content too short (${article.text.length} chars)",
                )
            }
            Log.i(TAG, "Extracted ${article.text.length} chars from $url")
            return WebArticleContent(
                url = url,
                title = article.title,
                author = article.author,
                text = article.text,
            )
        }

        private fun detectCharset(contentType: String): Charset {
            // Try to extract charset from Content-Type header
            val charsetMatch =
                Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                    .find(contentType)
            val charsetName = charsetMatch?.groupValues?.get(1)

            if (charsetName != null) {
                return try {
                    Charset.forName(charsetName)
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
            }

            return Charsets.UTF_8
        }
    }

/**
 * Errors specific to web article fetching.
 */
sealed class WebFetchException(message: String) : Exception(message) {
    class InvalidUrl(message: String) : WebFetchException(message)

    class NetworkError(message: String) : WebFetchException(message)

    class HttpError(val statusCode: Int, message: String) : WebFetchException(message)

    class InvalidContentType(val contentType: String) :
        WebFetchException("Invalid content type: $contentType")

    class ContentTooLarge(val size: Long) :
        WebFetchException("Content too large: $size bytes")

    class EmptyResponse : WebFetchException("Empty response body")

    class ExtractionFailed(message: String) : WebFetchException(message)
}
