package com.unamentis.core.tools.handlers

import com.unamentis.core.tools.LLMToolCall
import com.unamentis.core.tools.ToolExecutionContext
import com.unamentis.services.websearch.WebSearchException
import com.unamentis.services.websearch.WebSearchProvider
import com.unamentis.services.websearch.WebSearchResponse
import com.unamentis.services.websearch.WebSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WebSearchToolHandler.
 *
 * Tests tool definition, argument parsing, error handling,
 * and search result formatting.
 */
class WebSearchToolHandlerTest {
    private lateinit var mockProvider: WebSearchProvider
    private lateinit var handler: WebSearchToolHandler

    @Before
    fun setup() {
        mockProvider = mockk()
        handler = WebSearchToolHandler(mockProvider)
    }

    // region Tool Definition

    @Test
    fun `tool name is web_search`() {
        assertEquals("web_search", handler.toolName)
    }

    @Test
    fun `definition has correct name`() {
        val def = handler.getDefinition()
        assertEquals("web_search", def.name)
    }

    @Test
    fun `definition has query as required parameter`() {
        val def = handler.getDefinition()
        assertTrue(def.parameters.properties.containsKey("query"))
        assertTrue(def.parameters.required.contains("query"))
    }

    @Test
    fun `definition has num_results as optional parameter`() {
        val def = handler.getDefinition()
        assertTrue(def.parameters.properties.containsKey("num_results"))
        assertFalse(def.parameters.required.contains("num_results"))
    }

    @Test
    fun `definition has description`() {
        val def = handler.getDefinition()
        assertTrue(def.description.isNotEmpty())
        assertTrue(def.description.contains("Search the web"))
    }

    // endregion

    // region Successful Search

    @Test
    fun `handle returns formatted results on successful search`() =
        runTest {
            val expectedResponse =
                WebSearchResponse(
                    query = "kotlin android",
                    results =
                        listOf(
                            WebSearchResult("Result 1", "https://example.com/1", "Description 1"),
                            WebSearchResult("Result 2", "https://example.com/2", "Description 2"),
                        ),
                )

            coEvery { mockProvider.search("kotlin android", 5) } returns expectedResponse

            val call = createToolCall(query = "kotlin android")
            val result = handler.handle(call, ToolExecutionContext())

            assertFalse(result.isError)
            assertTrue(result.content.contains("Search results for: kotlin android"))
            assertTrue(result.content.contains("Result 1"))
            assertTrue(result.content.contains("Result 2"))
        }

    @Test
    fun `handle passes custom num_results to provider`() =
        runTest {
            coEvery { mockProvider.search("test", 3) } returns
                WebSearchResponse("test", emptyList())

            val call = createToolCall(query = "test", numResults = 3)
            handler.handle(call, ToolExecutionContext())

            coVerify { mockProvider.search("test", 3) }
        }

    @Test
    fun `handle uses default of 5 when num_results not provided`() =
        runTest {
            coEvery { mockProvider.search("test", 5) } returns
                WebSearchResponse("test", emptyList())

            val call = createToolCall(query = "test")
            handler.handle(call, ToolExecutionContext())

            coVerify { mockProvider.search("test", 5) }
        }

    @Test
    fun `handle clamps num_results to max of 10`() =
        runTest {
            coEvery { mockProvider.search("test", 10) } returns
                WebSearchResponse("test", emptyList())

            val call = createToolCall(query = "test", numResults = 50)
            handler.handle(call, ToolExecutionContext())

            coVerify { mockProvider.search("test", 10) }
        }

    @Test
    fun `handle clamps num_results to min of 1`() =
        runTest {
            coEvery { mockProvider.search("test", 1) } returns
                WebSearchResponse("test", emptyList())

            val call = createToolCall(query = "test", numResults = 0)
            handler.handle(call, ToolExecutionContext())

            coVerify { mockProvider.search("test", 1) }
        }

    // endregion

    // region Argument Validation

    @Test
    fun `handle returns error when query is missing`() =
        runTest {
            val call =
                LLMToolCall(
                    id = "call-1",
                    name = "web_search",
                    arguments = buildJsonObject { },
                )

            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Missing required argument: query"))
        }

    @Test
    fun `handle returns error when query is blank`() =
        runTest {
            val call = createToolCall(query = "   ")
            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Missing required argument: query"))
        }

    // endregion

    // region Error Handling

    @Test
    fun `handle returns friendly error on ApiKeyMissing`() =
        runTest {
            coEvery { mockProvider.search(any(), any()) } throws
                WebSearchException.ApiKeyMissing()

            val call = createToolCall(query = "test")
            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Brave Search API key"))
        }

    @Test
    fun `handle returns friendly error on RateLimited`() =
        runTest {
            coEvery { mockProvider.search(any(), any()) } throws
                WebSearchException.RateLimited()

            val call = createToolCall(query = "test")
            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("rate limit"))
        }

    @Test
    fun `handle returns friendly error on QuotaExceeded`() =
        runTest {
            coEvery { mockProvider.search(any(), any()) } throws
                WebSearchException.QuotaExceeded()

            val call = createToolCall(query = "test")
            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("quota exceeded"))
        }

    @Test
    fun `handle returns error on RequestFailed`() =
        runTest {
            coEvery { mockProvider.search(any(), any()) } throws
                WebSearchException.RequestFailed("timeout")

            val call = createToolCall(query = "test")
            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Search failed"))
        }

    @Test
    fun `handle returns error on unexpected exception`() =
        runTest {
            coEvery { mockProvider.search(any(), any()) } throws
                RuntimeException("Unexpected error")

            val call = createToolCall(query = "test")
            val result = handler.handle(call, ToolExecutionContext())

            assertTrue(result.isError)
            assertTrue(result.content.contains("Unexpected error"))
        }

    // endregion

    // region Helpers

    private fun createToolCall(
        query: String,
        numResults: Int? = null,
    ): LLMToolCall {
        val args =
            buildJsonObject {
                put("query", query)
                if (numResults != null) {
                    put("num_results", numResults)
                }
            }

        return LLMToolCall(
            id = "call-${System.nanoTime()}",
            name = "web_search",
            arguments = args,
        )
    }

    // endregion
}
