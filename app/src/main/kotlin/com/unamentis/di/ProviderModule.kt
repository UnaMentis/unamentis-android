package com.unamentis.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.config.ProviderDataStore
import com.unamentis.core.config.ServerConfigManager
import com.unamentis.core.device.DeviceCapabilityDetector
import com.unamentis.core.health.HealthMonitorConfig
import com.unamentis.core.health.ProviderHealthMonitor
import com.unamentis.data.model.LLMService
import com.unamentis.data.model.STTService
import com.unamentis.data.model.TTSService
import com.unamentis.services.llm.AnthropicLLMService
import com.unamentis.services.llm.ExecuTorchLLMService
import com.unamentis.services.llm.LLMBackend
import com.unamentis.services.llm.LLMBackendSelector
import com.unamentis.services.llm.MediaPipeLLMService
import com.unamentis.services.llm.ModelDownloadManager
import com.unamentis.services.llm.OnDeviceLLMService
import com.unamentis.services.llm.OpenAILLMService
import com.unamentis.services.llm.PatchPanelService
import com.unamentis.services.llm.SelfHostedLLMService
import com.unamentis.services.stt.AndroidSTTService
import com.unamentis.services.stt.DeepgramSTTService
import com.unamentis.services.stt.GLMASROnDeviceSTTService
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
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module providing provider service dependencies.
 *
 * Provides:
 * - STT services (Deepgram, Android)
 * - TTS services (ElevenLabs, Android)
 * - LLM services (OpenAI, Anthropic, OnDevice, PatchPanel)
 * - ProviderConfig for configuration management
 * - DeviceCapabilityDetector for hardware detection
 *
 * Note: OkHttpClient is provided by AppModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    /**
     * Provide the singleton DataStore for provider configuration.
     *
     * This ensures only one DataStore instance exists for the "provider_config" file,
     * preventing "multiple DataStores active for same file" errors in tests.
     */
    @Provides
    @Singleton
    fun provideProviderDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return ProviderDataStore.getInstance(context)
    }

    /**
     * Provide ProviderConfig for configuration management.
     */
    @Provides
    @Singleton
    fun provideProviderConfig(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
    ): ProviderConfig {
        return ProviderConfig(context, dataStore)
    }

    /**
     * Provide DeviceCapabilityDetector for hardware detection.
     */
    @Provides
    @Singleton
    fun provideDeviceCapabilityDetector(
        @ApplicationContext context: Context,
    ): DeviceCapabilityDetector {
        return DeviceCapabilityDetector(context)
    }

    /**
     * Provide ModelDownloadManager for on-device LLM models.
     */
    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        deviceCapabilityDetector: DeviceCapabilityDetector,
    ): ModelDownloadManager {
        return ModelDownloadManager(context, deviceCapabilityDetector)
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

    /**
     * Provide GLM-ASR On-Device STT service.
     *
     * Uses GLM-ASR-Nano-2512 for fully on-device speech recognition.
     * Features:
     * - Zero cost (no API fees)
     * - Complete privacy (audio never leaves device)
     * - Works offline
     *
     * Requirements:
     * - 8GB+ RAM (12GB recommended)
     * - Android 12+ (API 31+)
     * - ~2.4GB storage for models
     */
    @Provides
    @Singleton
    @Named("GLMASROnDeviceSTT")
    fun provideGLMASROnDeviceSTTService(
        @ApplicationContext context: Context,
    ): GLMASROnDeviceSTTService {
        return GLMASROnDeviceSTTService(context)
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
     * Provide Ollama/Self-Hosted LLM service.
     *
     * This is an optional provider that returns an LLMService if:
     * 1. Self-hosted mode is enabled in settings
     * 2. A server IP is configured
     * 3. An LLM-capable server is discovered and healthy
     *
     * Returns null if not configured or unavailable.
     * The service connects to Ollama (port 11434) or UnaMentis Gateway (port 8766).
     */
    @Provides
    @Singleton
    @Named("OllamaLLM")
    fun provideOllamaLLMService(
        serverConfigManager: ServerConfigManager,
        config: ProviderConfig,
        client: OkHttpClient,
    ): LLMService? {
        // Only enable if self-hosted is enabled in settings
        if (!config.isSelfHostedEnabled()) {
            Log.i("ProviderModule", "Self-hosted LLM disabled in settings")
            return null
        }

        // Try auto-discovery from ServerConfigManager
        val model = config.getSelectedOllamaModel()
        val service =
            SelfHostedLLMService.autoDiscover(
                serverConfigManager = serverConfigManager,
                model = model,
                client = client,
            )

        if (service != null) {
            Log.i("ProviderModule", "Ollama provider discovered: $model")
        } else {
            Log.i("ProviderModule", "No Ollama server available")
        }

        return service
    }

    /**
     * Provide OnDevice LLM service using llama.cpp.
     *
     * This provides fully offline LLM inference using:
     * - Ministral-3B-Instruct (primary, ~2.1GB) for 6GB+ RAM devices
     * - TinyLlama-1.1B-Chat (fallback, ~670MB) for 3GB+ RAM devices
     *
     * Models are downloaded on demand via ModelDownloadManager.
     */
    @Provides
    @Singleton
    @Named("OnDeviceLLM")
    fun provideOnDeviceLLMService(
        @ApplicationContext context: Context,
    ): LLMService {
        return OnDeviceLLMService(context)
    }

    // ==========================================
    // High-Performance LLM Backends
    // ==========================================

    /**
     * Provide MediaPipe LLM service for GPU-accelerated inference.
     *
     * Uses Google's MediaPipe LLM Inference API with OpenCL GPU acceleration.
     * Performance: 20-50 tok/sec on high-end devices.
     */
    @Provides
    @Singleton
    fun provideMediaPipeLLMService(
        @ApplicationContext context: Context,
    ): MediaPipeLLMService {
        return MediaPipeLLMService(context)
    }

    /**
     * Provide ExecuTorch LLM service for Qualcomm NPU acceleration.
     *
     * Uses PyTorch ExecuTorch with Qualcomm AI Engine Direct (QNN) backend.
     * Performance: 50+ tok/sec on Snapdragon 8 Gen2+ devices.
     */
    @Provides
    @Singleton
    fun provideExecuTorchLLMService(
        @ApplicationContext context: Context,
    ): ExecuTorchLLMService {
        return ExecuTorchLLMService(context)
    }

    /**
     * Provide LLM Backend Selector for automatic backend selection.
     *
     * Selects the optimal backend based on device capabilities:
     * 1. ExecuTorch + QNN (Snapdragon 8 Gen2+): 50+ tok/sec
     * 2. MediaPipe (OpenCL GPU): 20-30 tok/sec
     * 3. llama.cpp CPU fallback: 5-10 tok/sec
     */
    @Provides
    @Singleton
    fun provideLLMBackendSelector(
        deviceCapabilityDetector: DeviceCapabilityDetector,
        execuTorchProvider: Provider<ExecuTorchLLMService>,
        mediaPipeProvider: Provider<MediaPipeLLMService>,
        @Named("OnDeviceLLM") llamaCppProvider: Provider<LLMService>,
    ): LLMBackendSelector {
        return LLMBackendSelector(
            deviceCapabilityDetector = deviceCapabilityDetector,
            execuTorchProvider = execuTorchProvider,
            mediaPipeProvider = mediaPipeProvider,
            llamaCppProvider =
                Provider {
                    llamaCppProvider.get() as OnDeviceLLMService
                },
        )
    }

    /**
     * Provide the best available LLM backend for this device.
     *
     * Uses LLMBackendSelector to automatically choose:
     * - ExecuTorch (NPU) on flagship Qualcomm devices
     * - MediaPipe (GPU) on devices with OpenCL support
     * - llama.cpp (CPU) as fallback
     */
    @Provides
    @Singleton
    fun provideOptimalLLMBackend(selector: LLMBackendSelector): LLMBackend {
        return selector.getSelectedBackend()
    }

    /**
     * Provide PatchPanel LLM routing service.
     *
     * Routes to multiple LLM providers based on configuration:
     * - OpenAI (cloud, requires API key)
     * - Anthropic (cloud, requires API key)
     * - Ollama (self-hosted, free if enabled and server available)
     * - OnDevice (offline, uses optimal backend via LLMBackendSelector):
     *   - ExecuTorch (NPU) on Snapdragon 8 Gen2+ devices
     *   - MediaPipe (GPU) on devices with OpenCL support
     *   - llama.cpp (CPU) as fallback
     *
     * Note: OnDevice uses LLMBackendSelector to choose the fastest available backend.
     */
    @Provides
    @Singleton
    @Named("PatchPanelLLM")
    fun providePatchPanelService(
        @Named("OpenAILLM") openai: LLMService,
        @Named("AnthropicLLM") anthropic: LLMService,
        @Named("OllamaLLM") ollama: LLMService?,
        backendSelector: LLMBackendSelector,
        deviceCapabilityDetector: DeviceCapabilityDetector,
    ): LLMService {
        val providers =
            buildMap {
                put("OpenAI", openai)
                put("Anthropic", anthropic)

                // Add Ollama if discovered and healthy (self-hosted, free)
                if (ollama != null) {
                    Log.i("ProviderModule", "PatchPanel adding Ollama provider")
                    put("Ollama", ollama)
                }

                // Add OnDevice provider only if device supports it
                // Uses LLMBackendSelector to choose optimal backend (ExecuTorch > MediaPipe > llama.cpp)
                if (deviceCapabilityDetector.supportsOnDeviceLLM()) {
                    val backend = backendSelector.getSelectedBackend()
                    Log.i(
                        "ProviderModule",
                        "PatchPanel OnDevice backend: ${backend.backendName}",
                    )
                    put("OnDevice", backend)
                }
            }
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
     * Priority order (lower value = higher priority):
     * 1. GLM-ASR On-Device (on-device, highest quality) - if device supports and models downloaded
     * 2. Android STT (on-device, fallback) - always available
     * 3. Deepgram (cloud, paid) - if API key configured and healthy
     */
    @Provides
    @Singleton
    @Named("STTRouter")
    fun provideSTTProviderRouter(
        @Named("DeepgramSTT") deepgram: STTService,
        @Named("AndroidSTT") android: STTService,
        @Named("GLMASROnDeviceSTT") glmAsrOnDevice: GLMASROnDeviceSTTService,
        @Named("DeepgramHealthMonitor") deepgramHealthMonitor: ProviderHealthMonitor,
        config: ProviderConfig,
    ): STTProviderRouter {
        val router = STTProviderRouter()

        // Register GLM-ASR On-Device (highest quality on-device, if supported and models present)
        // This provides the best on-device STT with GLM-ASR-Nano-2512
        if (glmAsrOnDevice.isSupported() && glmAsrOnDevice.areModelsReady()) {
            Log.i(
                "ProviderModule",
                "Registering GLM-ASR On-Device STT (models ready)",
            )
            router.registerProvider(
                STTProviderRegistration(
                    service = glmAsrOnDevice,
                    priority = STTProviderPriority.ON_DEVICE,
                    healthMonitor = null,
                    requiresApiKey = false,
                    isOnDevice = true,
                ),
            )
        } else if (glmAsrOnDevice.isSupported()) {
            Log.i(
                "ProviderModule",
                "GLM-ASR On-Device STT supported but models not downloaded",
            )
        } else {
            Log.i(
                "ProviderModule",
                "GLM-ASR On-Device STT not supported on this device",
            )
        }

        // Register Android STT (on-device fallback, always available)
        // On-device doesn't need health monitoring
        router.registerProvider(
            STTProviderRegistration(
                service = android,
                // Use SELF_HOSTED priority if GLM-ASR is available, otherwise ON_DEVICE
                priority =
                    if (glmAsrOnDevice.isSupported() && glmAsrOnDevice.areModelsReady()) {
                        STTProviderPriority.SELF_HOSTED
                    } else {
                        STTProviderPriority.ON_DEVICE
                    },
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
     *
     * Reads the user's selected LLM provider from ProviderConfig and returns
     * the appropriate service:
     * - "OnDevice": Uses LLMBackendSelector to choose the best backend:
     *   - ExecuTorch (NPU) on Snapdragon 8 Gen2+ devices (50+ tok/sec)
     *   - MediaPipe (GPU) on devices with OpenCL support (20-30 tok/sec)
     *   - llama.cpp (CPU) as fallback (5-10 tok/sec)
     * - "OpenAI": Uses OpenAI API (requires API key)
     * - "Anthropic": Uses Anthropic API (requires API key)
     * - "Ollama": Uses self-hosted Ollama server (requires server configured)
     * - "PatchPanel" (default): Intelligent routing based on task type
     *
     * Note: Changes take effect on app restart since Hilt provides singletons.
     */
    @Provides
    @Singleton
    fun provideDefaultLLMService(
        @Named("PatchPanelLLM") patchPanel: LLMService,
        @Named("OpenAILLM") openai: LLMService,
        @Named("AnthropicLLM") anthropic: LLMService,
        @Named("OllamaLLM") ollama: LLMService?,
        backendSelector: LLMBackendSelector,
        config: ProviderConfig,
    ): LLMService {
        val selectedProvider = config.getLLMProvider()
        Log.i("ProviderModule", "Selected LLM provider: $selectedProvider")

        return when (selectedProvider) {
            "OnDevice" -> {
                // Use the backend selector to choose the optimal backend
                // (ExecuTorch > MediaPipe > llama.cpp based on device capabilities)
                val backend = backendSelector.getSelectedBackend()
                Log.i(
                    "ProviderModule",
                    "Using OnDevice LLM: ${backend.backendName} (${backend.expectedToksPerSec} tok/sec expected)",
                )
                backend
            }
            "OpenAI" -> {
                Log.i("ProviderModule", "Using OpenAI LLM")
                openai
            }
            "Anthropic" -> {
                Log.i("ProviderModule", "Using Anthropic LLM")
                anthropic
            }
            "Ollama" -> {
                if (ollama != null) {
                    Log.i("ProviderModule", "Using Ollama LLM (self-hosted)")
                    ollama
                } else {
                    // Fall back to PatchPanel if Ollama not available
                    Log.w("ProviderModule", "Ollama selected but not available, falling back to PatchPanel")
                    patchPanel
                }
            }
            else -> {
                // Default to PatchPanel for intelligent routing
                Log.i("ProviderModule", "Using PatchPanel LLM (intelligent routing)")
                patchPanel
            }
        }
    }
}
