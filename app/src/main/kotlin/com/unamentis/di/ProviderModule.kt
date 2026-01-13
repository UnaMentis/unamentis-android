package com.unamentis.di

import android.content.Context
import android.speech.tts.TextToSpeech
import com.unamentis.core.config.ProviderConfig
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.STTService
import com.unamentis.data.model.TTSService
import com.unamentis.services.llm.AnthropicLLMService
import com.unamentis.services.llm.OpenAILLMService
import com.unamentis.services.llm.PatchPanelService
import com.unamentis.services.stt.AndroidSTTService
import com.unamentis.services.stt.DeepgramSTTService
import com.unamentis.services.tts.AndroidTTSService
import com.unamentis.services.tts.ElevenLabsTTSService
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
        @ApplicationContext context: Context
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
        config: ProviderConfig
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
        @ApplicationContext context: Context
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
        config: ProviderConfig
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
        @ApplicationContext context: Context
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
        config: ProviderConfig
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
        config: ProviderConfig
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
        @Named("AnthropicLLM") anthropic: LLMService
    ): LLMService {
        val providers = mapOf(
            "OpenAI" to openai,
            "Anthropic" to anthropic
        )
        return PatchPanelService(providers)
    }

    /**
     * Provide default STT service based on configuration.
     */
    @Provides
    @Singleton
    fun provideDefaultSTTService(
        @Named("DeepgramSTT") deepgram: STTService,
        @Named("AndroidSTT") android: STTService,
        config: ProviderConfig
    ): STTService {
        // TODO: Read from config.selectedSTTProvider flow
        // For now, default to Deepgram
        return deepgram
    }

    /**
     * Provide default TTS service based on configuration.
     */
    @Provides
    @Singleton
    fun provideDefaultTTSService(
        @Named("ElevenLabsTTS") elevenlabs: TTSService,
        @Named("AndroidTTS") android: TTSService,
        config: ProviderConfig
    ): TTSService {
        // TODO: Read from config.selectedTTSProvider flow
        // For now, default to ElevenLabs
        return elevenlabs
    }

    /**
     * Provide default LLM service based on configuration.
     */
    @Provides
    @Singleton
    fun provideDefaultLLMService(
        @Named("PatchPanelLLM") patchPanel: LLMService,
        @Named("OpenAILLM") openai: LLMService,
        @Named("AnthropicLLM") anthropic: LLMService,
        config: ProviderConfig
    ): LLMService {
        // TODO: Read from config.selectedLLMProvider flow
        // For now, default to PatchPanel
        return patchPanel
    }
}
