package com.unamentis.di

import android.content.Context
import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.health.HealthMonitorConfig
import com.unamentis.core.health.ProviderHealthMonitor
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.STTService
import com.unamentis.data.model.TTSService
import com.unamentis.services.llm.AnthropicLLMService
import com.unamentis.services.llm.OpenAILLMService
import com.unamentis.services.llm.PatchPanelService
import com.unamentis.services.stt.AndroidSTTService
import com.unamentis.services.stt.DeepgramSTTService
import com.unamentis.services.stt.STTProviderPriority
import com.unamentis.services.stt.STTProviderRegistration
import com.unamentis.services.stt.STTProviderRouter
import com.unamentis.services.tts.AndroidTTSService
import com.unamentis.services.tts.ElevenLabsTTSService
import com.unamentis.services.tts.TTSProviderPriority
import com.unamentis.services.tts.TTSProviderRegistration
import com.unamentis.services.tts.TTSProviderRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing provider service dependencies.
 *
 * Provides:
 * - STT services (Deepgram, Android)
 * - TTS services (ElevenLabs, Android)
 * - LLM services (OpenAI, Anthropic, PatchPanel)
 * - ProviderConfig for configuration management
 *
 * Note: OkHttpClient is provided by AppModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    /**
     * Provide ProviderConfig for configuration management.
     */
    @Provides
    @Singleton
    fun provideProviderConfig(
        @ApplicationContext context: Context,
    ): ProviderConfig {
        return ProviderConfig(context)
    }

    // STT Service Providers

    /**
     * Provide Deepgram STT service.
     */
    @Provides
    @Singleton
    @Named("DeepgramSTT")
    fun provideDeepgramSTTService(
        client: OkHttpClient,
        config: ProviderConfig,
    ): STTService {
        val apiKey = config.getDeepgramApiKey() ?: ""
        return DeepgramSTTService(apiKey = apiKey, client = client)
    }

    /**
     * Provide Android STT service.
     */
    @Provides
    @Singleton
    @Named("AndroidSTT")
    fun provideAndroidSTTService(
        @ApplicationContext context: Context,
    ): STTService {
        return AndroidSTTService(context)
    }

    // TTS Service Providers

    /**
     * Provide ElevenLabs TTS service.
     */
    @Provides
    @Singleton
    @Named("ElevenLabsTTS")
    fun provideElevenLabsTTSService(
        client: OkHttpClient,
        config: ProviderConfig,
    ): TTSService {
        val apiKey = config.getElevenLabsApiKey() ?: ""
        return ElevenLabsTTSService(apiKey = apiKey, client = client)
    }

    /**
     * Provide Android TTS service.
     */
    @Provides
    @Singleton
    @Named("AndroidTTS")
    fun provideAndroidTTSService(
        @ApplicationContext context: Context,
    ): TTSService {
        return AndroidTTSService(context)
    }

    // LLM Service Providers

    /**
     * Provide OpenAI LLM service.
     */
    @Provides
    @Singleton
    @Named("OpenAILLM")
    fun provideOpenAILLMService(
        client: OkHttpClient,
        config: ProviderConfig,
    ): LLMService {
        val apiKey = config.getOpenAIApiKey() ?: ""
        return OpenAILLMService(apiKey = apiKey, client = client)
    }

    /**
     * Provide Anthropic LLM service.
     */
    @Provides
    @Singleton
    @Named("AnthropicLLM")
    fun provideAnthropicLLMService(
        client: OkHttpClient,
        config: ProviderConfig,
    ): LLMService {
        val apiKey = config.getAnthropicApiKey() ?: ""
        return AnthropicLLMService(apiKey = apiKey, client = client)
    }

    /**
     * Provide PatchPanel LLM routing service.
     */
    @Provides
    @Singleton
    @Named("PatchPanelLLM")
    fun providePatchPanelService(
        @Named("OpenAILLM") openai: LLMService,
        @Named("AnthropicLLM") anthropic: LLMService,
    ): LLMService {
        val providers =
            mapOf(
                "OpenAI" to openai,
                "Anthropic" to anthropic,
            )
        return PatchPanelService(providers)
    }

    // Health Monitors

    /**
     * Provide Deepgram health monitor.
     */
    @Provides
    @Singleton
    @Named("DeepgramHealthMonitor")
    fun provideDeepgramHealthMonitor(client: OkHttpClient): ProviderHealthMonitor {
        return ProviderHealthMonitor(
            config =
                HealthMonitorConfig(
                    healthEndpoint = "https://api.deepgram.com/v1/health",
                    // Check every minute
                    checkIntervalMs = 60_000L,
                    unhealthyThreshold = 3,
                    healthyThreshold = 2,
                ),
            client = client,
            providerName = "Deepgram",
        )
    }

    /**
     * Provide ElevenLabs health monitor.
     */
    @Provides
    @Singleton
    @Named("ElevenLabsHealthMonitor")
    fun provideElevenLabsHealthMonitor(client: OkHttpClient): ProviderHealthMonitor {
        return ProviderHealthMonitor(
            config =
                HealthMonitorConfig(
                    healthEndpoint = "https://api.elevenlabs.io/v1/user",
                    // Check every minute
                    checkIntervalMs = 60_000L,
                    unhealthyThreshold = 3,
                    healthyThreshold = 2,
                ),
            client = client,
            providerName = "ElevenLabs",
        )
    }

    // Provider Routers

    /**
     * Provide STT provider router with automatic failover.
     *
     * Priority order:
     * 1. Android STT (on-device, free) - always available
     * 2. Deepgram (cloud, paid) - if API key configured and healthy
     */
    @Provides
    @Singleton
    @Named("STTRouter")
    fun provideSTTProviderRouter(
        @Named("DeepgramSTT") deepgram: STTService,
        @Named("AndroidSTT") android: STTService,
        @Named("DeepgramHealthMonitor") deepgramHealthMonitor: ProviderHealthMonitor,
        config: ProviderConfig,
    ): STTProviderRouter {
        val router = STTProviderRouter()

        // Register Android STT (on-device, always available)
        // On-device doesn't need health monitoring
        router.registerProvider(
            STTProviderRegistration(
                service = android,
                priority = STTProviderPriority.ON_DEVICE,
                healthMonitor = null,
                requiresApiKey = false,
                isOnDevice = true,
            ),
        )

        // Register Deepgram (cloud, primary when API key available)
        val hasDeepgramKey = !config.getDeepgramApiKey().isNullOrBlank()
        if (hasDeepgramKey) {
            router.registerProvider(
                STTProviderRegistration(
                    service = deepgram,
                    priority = STTProviderPriority.CLOUD_PRIMARY,
                    healthMonitor = deepgramHealthMonitor,
                    requiresApiKey = true,
                    isOnDevice = false,
                ),
            )
        }

        return router
    }

    /**
     * Provide TTS provider router with automatic failover.
     *
     * Priority order:
     * 1. ElevenLabs (cloud, highest quality) - if API key configured and healthy
     * 2. Android TTS (on-device, free) - always available
     */
    @Provides
    @Singleton
    @Named("TTSRouter")
    fun provideTTSProviderRouter(
        @Named("ElevenLabsTTS") elevenlabs: TTSService,
        @Named("AndroidTTS") android: TTSService,
        @Named("ElevenLabsHealthMonitor") elevenLabsHealthMonitor: ProviderHealthMonitor,
        config: ProviderConfig,
    ): TTSProviderRouter {
        val router = TTSProviderRouter()

        // Register ElevenLabs (cloud, primary when API key available)
        val hasElevenLabsKey = !config.getElevenLabsApiKey().isNullOrBlank()
        if (hasElevenLabsKey) {
            router.registerProvider(
                TTSProviderRegistration(
                    service = elevenlabs,
                    priority = TTSProviderPriority.CLOUD_PRIMARY,
                    healthMonitor = elevenLabsHealthMonitor,
                    requiresApiKey = true,
                    isOnDevice = false,
                ),
            )
        }

        // Register Android TTS (on-device, always available as fallback)
        // On-device doesn't need health monitoring
        router.registerProvider(
            TTSProviderRegistration(
                service = android,
                priority = TTSProviderPriority.ON_DEVICE,
                healthMonitor = null,
                requiresApiKey = false,
                isOnDevice = true,
            ),
        )

        return router
    }

    /**
     * Provide default STT service based on configuration.
     *
     * Uses the STT router for automatic failover support.
     * Falls back to simple provider selection if router is not desired.
     */
    @Provides
    @Singleton
    fun provideDefaultSTTService(
        @Named("STTRouter") router: STTProviderRouter,
        @Named("DeepgramSTT") _deepgram: STTService,
        @Named("AndroidSTT") _android: STTService,
        _config: ProviderConfig,
    ): STTService {
        // Use the router for automatic failover
        // The router will select the best provider based on priority and health
        return router
    }

    /**
     * Provide default TTS service based on configuration.
     *
     * Uses the TTS router for automatic failover support.
     * Falls back to simple provider selection if router is not desired.
     */
    @Provides
    @Singleton
    fun provideDefaultTTSService(
        @Named("TTSRouter") router: TTSProviderRouter,
        @Named("ElevenLabsTTS") _elevenlabs: TTSService,
        @Named("AndroidTTS") _android: TTSService,
        _config: ProviderConfig,
    ): TTSService {
        // Use the router for automatic failover
        // The router will select the best provider based on priority and health
        return router
    }

    /**
     * Provide default LLM service based on configuration.
     */
    @Provides
    @Singleton
    fun provideDefaultLLMService(
        @Named("PatchPanelLLM") patchPanel: LLMService,
        @Named("OpenAILLM") _openai: LLMService,
        @Named("AnthropicLLM") _anthropic: LLMService,
        _config: ProviderConfig,
    ): LLMService {
        // TODO: Read from config.selectedLLMProvider flow
        // For now, default to PatchPanel
        return patchPanel
    }
}
