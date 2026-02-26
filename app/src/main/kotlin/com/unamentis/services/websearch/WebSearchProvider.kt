package com.unamentis.services.websearch

/**
 * A single web search result.
 *
 * @property title Title of the web page
 * @property url URL of the result
 * @property description Snippet/description of the result
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val description: String,
) {
    /**
     * Formatted string for LLM context.
     */
    val formatted: String
        get() = "[$title]($url)\n$description"
}

/**
 * Response from a web search query.
 *
 * @property query The original query
 * @property results Search results
 * @property totalResults Total number of results available (may be more than returned)
 */
data class WebSearchResponse(
    val query: String,
    val results: List<WebSearchResult>,
    val totalResults: Int? = null,
) {
    /**
     * Format all results for LLM consumption.
     *
     * Produces a numbered list with titles, URLs, and descriptions
     * suitable for including in LLM context.
     */
    val formattedForLLM: String
        get() {
            if (results.isEmpty()) {
                return "No results found for: $query"
            }

            return buildString {
                append("Search results for: $query\n\n")
                results.forEachIndexed { index, result ->
                    append("${index + 1}. ${result.title}\n")
                    append("   URL: ${result.url}\n")
                    append("   ${result.description}\n\n")
                }
            }
        }
}

/**
 * Interface for web search service implementations.
 *
 * Implementations provide web search capability using different
 * search engines (e.g., Brave Search API).
 */
interface WebSearchProvider {
    /**
     * Perform a web search.
     *
     * @param query Search query string
     * @param maxResults Maximum number of results to return
     * @return Search response with results
     * @throws WebSearchException if the search fails
     */
    suspend fun search(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): WebSearchResponse

    companion object {
        /** Default number of search results to return. */
        const val DEFAULT_MAX_RESULTS = 5
    }
}

/**
 * Errors that can occur during web search.
 */
sealed class WebSearchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** API key is missing or not configured. */
    class ApiKeyMissing : WebSearchException("Web search API key not configured")

    /** The HTTP request to the search API failed. */
    class RequestFailed(detail: String, cause: Throwable? = null) :
        WebSearchException("Search request failed: $detail", cause)

    /** The search API returned an invalid response. */
    class InvalidResponse : WebSearchException("Invalid response from search API")

    /** The search API rate limit was exceeded. */
    class RateLimited : WebSearchException("Search rate limit exceeded")

    /** The search API quota was exceeded. */
    class QuotaExceeded : WebSearchException("Search quota exceeded")
}
