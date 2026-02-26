package com.unamentis.services.websearch

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web search service using the Brave Search API.
 *
 * Brave Search offers 2,000 free queries per month with no tracking
 * and an independent search index.
 *
 * API docs: https://api.search.brave.com/app/documentation/web-search
 *
 * @property apiKey Brave Search API key
 * @property client OkHttpClient for HTTP requests
 */
@Singleton
class BraveSearchService
    @Inject
    constructor(
        private val apiKey: String,
        private val client: OkHttpClient,
        private val baseUrl: String = DEFAULT_BASE_URL,
    ) : WebSearchProvider {
        private val jsonParser = Json { ignoreUnknownKeys = true }

        override suspend fun search(
            query: String,
            maxResults: Int,
        ): WebSearchResponse {
            if (apiKey.isBlank()) {
                throw WebSearchException.ApiKeyMissing()
            }

            val url =
                baseUrl.toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addQueryParameter("q", query)
                    ?.addQueryParameter("count", maxResults.coerceAtMost(MAX_RESULTS_LIMIT).toString())
                    ?.build()
                    ?: throw WebSearchException.RequestFailed("Invalid URL")

            val request =
                Request.Builder()
                    .url(url)
                    .header("X-Subscription-Token", apiKey)
                    .header("Accept", "application/json")
                    .get()
                    .build()

            Log.d(TAG, "Searching Brave: '$query' (max $maxResults results)")

            val response =
                try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    throw WebSearchException.RequestFailed(
                        e.message ?: "Unknown network error",
                        e,
                    )
                }

            return response.use { resp ->
                when (resp.code) {
                    HTTP_OK -> parseResponse(resp.body?.string(), query)
                    HTTP_RATE_LIMITED -> throw WebSearchException.RateLimited()
                    HTTP_PAYMENT_REQUIRED -> throw WebSearchException.QuotaExceeded()
                    else -> throw WebSearchException.RequestFailed("HTTP ${resp.code}")
                }
            }
        }

        /**
         * Parse the Brave Search API JSON response.
         */
        private fun parseResponse(
            body: String?,
            query: String,
        ): WebSearchResponse {
            val responseBody = body ?: throw WebSearchException.InvalidResponse()
            return parseResponseBody(responseBody, query)
        }

        /**
         * Parse a non-null response body into a [WebSearchResponse].
         */
        private fun parseResponseBody(
            body: String,
            query: String,
        ): WebSearchResponse {
            try {
                val json = jsonParser.parseToJsonElement(body).jsonObject

                val results = mutableListOf<WebSearchResult>()

                val webResults =
                    json["web"]?.jsonObject?.get("results")?.jsonArray

                webResults?.forEach { item ->
                    val obj = item.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@forEach
                    val url = obj["url"]?.jsonPrimitive?.content ?: return@forEach
                    val description = obj["description"]?.jsonPrimitive?.content ?: ""

                    results.add(
                        WebSearchResult(
                            title = title,
                            url = url,
                            description = description,
                        ),
                    )
                }

                val totalCount =
                    json["web"]?.jsonObject?.get("totalResults")?.jsonPrimitive?.int

                Log.i(TAG, "Brave search returned ${results.size} results for '$query'")

                return WebSearchResponse(
                    query = query,
                    results = results,
                    totalResults = totalCount,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Brave search response", e)
                throw WebSearchException.InvalidResponse()
            }
        }

        companion object {
            private const val TAG = "BraveSearchService"

            /** Brave Search API base URL. */
            const val DEFAULT_BASE_URL = "https://api.search.brave.com/res/v1/web/search"

            /** Maximum results the API supports. */
            private const val MAX_RESULTS_LIMIT = 20

            /** HTTP 200 OK. */
            private const val HTTP_OK = 200

            /** HTTP 402 Payment Required. */
            private const val HTTP_PAYMENT_REQUIRED = 402

            /** HTTP 429 Too Many Requests. */
            private const val HTTP_RATE_LIMITED = 429
        }
    }
