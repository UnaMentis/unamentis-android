package com.unamentis.di

import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.tools.ToolHandler
import com.unamentis.core.tools.handlers.ContextExpansionToolHandler
import com.unamentis.core.tools.handlers.MarkForReviewToolHandler
import com.unamentis.core.tools.handlers.TodoToolHandler
import com.unamentis.core.tools.handlers.WebSearchToolHandler
import com.unamentis.services.websearch.BraveSearchService
import com.unamentis.services.websearch.WebSearchProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for LLM tool system dependencies.
 *
 * This module binds tool handlers into a Set that is injected into
 * the [ToolCallProcessor]. To add a new tool:
 *
 * 1. Create a class implementing [ToolHandler]
 * 2. Add a @Binds @IntoSet method in this module
 *
 * The processor will automatically discover and register all handlers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {
    /**
     * Binds the add_todo tool handler.
     */
    @Binds
    @IntoSet
    abstract fun bindTodoToolHandler(handler: TodoToolHandler): ToolHandler

    /**
     * Binds the mark_for_review tool handler.
     */
    @Binds
    @IntoSet
    abstract fun bindMarkForReviewToolHandler(handler: MarkForReviewToolHandler): ToolHandler

    /**
     * Binds the expand_context tool handler.
     */
    @Binds
    @IntoSet
    abstract fun bindContextExpansionToolHandler(handler: ContextExpansionToolHandler): ToolHandler

    /**
     * Binds the web_search tool handler.
     */
    @Binds
    @IntoSet
    abstract fun bindWebSearchToolHandler(handler: WebSearchToolHandler): ToolHandler

    companion object {
        /**
         * Provides the web search provider (Brave Search).
         *
         * Uses the Brave Search API key from ProviderConfig.
         * The service gracefully handles missing API keys by
         * throwing [WebSearchException.ApiKeyMissing] at search time.
         */
        @Provides
        @Singleton
        fun provideWebSearchProvider(
            config: ProviderConfig,
            client: OkHttpClient,
        ): WebSearchProvider {
            val apiKey = config.getBraveSearchApiKey() ?: ""
            return BraveSearchService(
                apiKey = apiKey,
                client = client,
            )
        }
    }
}
