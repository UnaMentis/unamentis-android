package com.unamentis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.config.ConfigurationPreset
import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.config.RecordingMode
import com.unamentis.core.device.DeviceCapabilityDetector
import com.unamentis.services.llm.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Responsibilities:
 * - Manage provider configuration
 * - Handle API key updates
 * - Apply configuration presets
 * - Manage on-device LLM model downloads
 * - Expose settings state to UI
 *
 * @property providerConfig Provider configuration manager
 * @property modelDownloadManager On-device LLM model download manager
 * @property deviceCapabilityDetector Device capability detector for LLM support
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val providerConfig: ProviderConfig,
        private val modelDownloadManager: ModelDownloadManager,
        private val deviceCapabilityDetector: DeviceCapabilityDetector,
    ) : ViewModel() {
        /**
         * Current configuration preset.
         */
        val currentPreset: StateFlow<ConfigurationPreset> =
            providerConfig.configurationPreset
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = ConfigurationPreset.FREE,
                )

        /**
         * Selected providers.
         */
        val selectedSTTProvider: StateFlow<String> =
            providerConfig.selectedSTTProvider
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "Android",
                )

        val selectedTTSProvider: StateFlow<String> =
            providerConfig.selectedTTSProvider
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "Android",
                )

        val selectedLLMProvider: StateFlow<String> =
            providerConfig.selectedLLMProvider
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "PatchPanel",
                )

        /**
         * Cost preference.
         */
        val costPreference: StateFlow<String> =
            providerConfig.costPreference
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "BALANCED",
                )

        /**
         * Recording mode.
         */
        val recordingMode: StateFlow<RecordingMode> =
            providerConfig.recordingMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = RecordingMode.VAD,
                )

        // ==================== Advanced Audio Settings ====================

        val sampleRate: StateFlow<Int> =
            providerConfig.sampleRate
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 48000)

        val enableVoiceProcessing: StateFlow<Boolean> =
            providerConfig.enableVoiceProcessing
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val enableEchoCancellation: StateFlow<Boolean> =
            providerConfig.enableEchoCancellation
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val enableNoiseSuppression: StateFlow<Boolean> =
            providerConfig.enableNoiseSuppression
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        // ==================== VAD Settings ====================

        val vadThreshold: StateFlow<Float> =
            providerConfig.vadThreshold
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

        val bargeInThreshold: StateFlow<Float> =
            providerConfig.bargeInThreshold
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)

        val enableBargeIn: StateFlow<Boolean> =
            providerConfig.enableBargeIn
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val silenceThresholdMs: StateFlow<Int> =
            providerConfig.silenceThresholdMs
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000)

        // ==================== LLM Settings ====================

        val llmTemperature: StateFlow<Float> =
            providerConfig.llmTemperature
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)

        val llmMaxTokens: StateFlow<Int> =
            providerConfig.llmMaxTokens
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1024)

        // ==================== TTS Settings ====================

        val ttsSpeakingRate: StateFlow<Float> =
            providerConfig.ttsSpeakingRate
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

        val ttsPlaybackSpeed: StateFlow<Float> =
            providerConfig.ttsPlaybackSpeed
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

        // ==================== Curriculum Settings ====================

        val autoContinueTopics: StateFlow<Boolean> =
            providerConfig.autoContinueTopics
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        // ==================== On-Device LLM Settings ====================

        /**
         * Whether device supports on-device LLM.
         */
        val supportsOnDeviceLLM: Boolean = deviceCapabilityDetector.supportsOnDeviceLLM()

        /**
         * Recommended on-device model for this device.
         */
        val recommendedModel: DeviceCapabilityDetector.OnDeviceModelSpec? =
            deviceCapabilityDetector.getRecommendedOnDeviceModel()

        /**
         * Device RAM in MB.
         */
        val deviceRamMB: Int = deviceCapabilityDetector.detect().totalRamMB

        /**
         * Download state for on-device LLM models.
         */
        val downloadState: StateFlow<ModelDownloadManager.DownloadState> =
            modelDownloadManager.downloadState
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    ModelDownloadManager.DownloadState.Idle,
                )

        /**
         * Available models with download status.
         */
        private val _availableModels =
            MutableStateFlow(modelDownloadManager.getAvailableModels())
        val availableModels: StateFlow<List<ModelDownloadManager.ModelInfo>> =
            _availableModels.asStateFlow()

        /**
         * Whether recommended model is downloaded.
         */
        val isRecommendedModelDownloaded: Boolean
            get() = modelDownloadManager.isRecommendedModelDownloaded()

        /**
         * Total storage used by models.
         */
        val totalStorageUsed: Long
            get() = modelDownloadManager.getTotalStorageUsed()

        /**
         * Available storage space.
         */
        val availableStorage: Long
            get() = modelDownloadManager.getAvailableStorage()

        /**
         * UI state combining all settings.
         */
        val uiState: StateFlow<SettingsUiState> =
            combine(
                currentPreset,
                selectedSTTProvider,
                selectedTTSProvider,
                selectedLLMProvider,
                combine(costPreference, recordingMode) { cost, mode -> cost to mode },
            ) { preset, stt, tts, llm, (cost, mode) ->
                SettingsUiState(
                    currentPreset = preset,
                    selectedSTTProvider = stt,
                    selectedTTSProvider = tts,
                    selectedLLMProvider = llm,
                    costPreference = cost,
                    recordingMode = mode,
                    hasDeepgramKey = providerConfig.getDeepgramApiKey() != null,
                    hasElevenLabsKey = providerConfig.getElevenLabsApiKey() != null,
                    hasOpenAIKey = providerConfig.getOpenAIApiKey() != null,
                    hasAnthropicKey = providerConfig.getAnthropicApiKey() != null,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsUiState(),
            )

        /**
         * Apply a configuration preset.
         */
        fun applyPreset(preset: ConfigurationPreset) {
            viewModelScope.launch {
                providerConfig.applyPreset(preset)
            }
        }

        /**
         * Set STT provider.
         */
        fun setSTTProvider(provider: String) {
            viewModelScope.launch {
                providerConfig.setSTTProvider(provider)
            }
        }

        /**
         * Set TTS provider.
         */
        fun setTTSProvider(provider: String) {
            viewModelScope.launch {
                providerConfig.setTTSProvider(provider)
            }
        }

        /**
         * Set LLM provider.
         */
        fun setLLMProvider(provider: String) {
            viewModelScope.launch {
                providerConfig.setLLMProvider(provider)
            }
        }

        /**
         * Set cost preference.
         */
        fun setCostPreference(preference: String) {
            viewModelScope.launch {
                providerConfig.setCostPreference(preference)
            }
        }

        /**
         * Set recording mode.
         */
        fun setRecordingMode(mode: RecordingMode) {
            viewModelScope.launch {
                providerConfig.setRecordingMode(mode)
            }
        }

        // ==================== Advanced Audio Settings Setters ====================

        fun setSampleRate(rate: Int) {
            viewModelScope.launch {
                providerConfig.setSampleRate(rate)
            }
        }

        fun setEnableVoiceProcessing(enabled: Boolean) {
            viewModelScope.launch {
                providerConfig.setEnableVoiceProcessing(enabled)
            }
        }

        fun setEnableEchoCancellation(enabled: Boolean) {
            viewModelScope.launch {
                providerConfig.setEnableEchoCancellation(enabled)
            }
        }

        fun setEnableNoiseSuppression(enabled: Boolean) {
            viewModelScope.launch {
                providerConfig.setEnableNoiseSuppression(enabled)
            }
        }

        // ==================== VAD Settings Setters ====================

        fun setVadThreshold(threshold: Float) {
            viewModelScope.launch {
                providerConfig.setVadThreshold(threshold)
            }
        }

        fun setBargeInThreshold(threshold: Float) {
            viewModelScope.launch {
                providerConfig.setBargeInThreshold(threshold)
            }
        }

        fun setEnableBargeIn(enabled: Boolean) {
            viewModelScope.launch {
                providerConfig.setEnableBargeIn(enabled)
            }
        }

        fun setSilenceThresholdMs(thresholdMs: Int) {
            viewModelScope.launch {
                providerConfig.setSilenceThresholdMs(thresholdMs)
            }
        }

        // ==================== LLM Settings Setters ====================

        fun setLlmTemperature(temperature: Float) {
            viewModelScope.launch {
                providerConfig.setLlmTemperature(temperature)
            }
        }

        fun setLlmMaxTokens(maxTokens: Int) {
            viewModelScope.launch {
                providerConfig.setLlmMaxTokens(maxTokens)
            }
        }

        // ==================== TTS Settings Setters ====================

        fun setTtsSpeakingRate(rate: Float) {
            viewModelScope.launch {
                providerConfig.setTtsSpeakingRate(rate)
            }
        }

        fun setTtsPlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                providerConfig.setTtsPlaybackSpeed(speed)
            }
        }

        // ==================== Curriculum Settings Setters ====================

        fun setAutoContinueTopics(enabled: Boolean) {
            viewModelScope.launch {
                providerConfig.setAutoContinueTopics(enabled)
            }
        }

        /**
         * Update Deepgram API key.
         */
        fun updateDeepgramApiKey(key: String) {
            providerConfig.setDeepgramApiKey(key)
        }

        /**
         * Update ElevenLabs API key.
         */
        fun updateElevenLabsApiKey(key: String) {
            providerConfig.setElevenLabsApiKey(key)
        }

        /**
         * Update OpenAI API key.
         */
        fun updateOpenAIApiKey(key: String) {
            providerConfig.setOpenAIApiKey(key)
        }

        /**
         * Update Anthropic API key.
         */
        fun updateAnthropicApiKey(key: String) {
            providerConfig.setAnthropicApiKey(key)
        }

        /**
         * Clear all API keys.
         */
        fun clearAllApiKeys() {
            providerConfig.clearAllApiKeys()
        }

        /**
         * Get API key (masked for display).
         */
        fun getDeepgramApiKey(): String? = providerConfig.getDeepgramApiKey()?.let { maskApiKey(it) }

        fun getElevenLabsApiKey(): String? = providerConfig.getElevenLabsApiKey()?.let { maskApiKey(it) }

        fun getOpenAIApiKey(): String? = providerConfig.getOpenAIApiKey()?.let { maskApiKey(it) }

        fun getAnthropicApiKey(): String? = providerConfig.getAnthropicApiKey()?.let { maskApiKey(it) }

        /**
         * Mask API key for display (show first 8 and last 4 characters).
         */
        private fun maskApiKey(key: String): String {
            return if (key.length > 12) {
                "${key.take(8)}...${key.takeLast(4)}"
            } else {
                "••••••••"
            }
        }

        // ==================== On-Device LLM Methods ====================

        /**
         * Download the recommended model for this device.
         */
        fun downloadRecommendedModel() {
            viewModelScope.launch {
                modelDownloadManager.downloadRecommendedModel()
                refreshAvailableModels()
            }
        }

        /**
         * Download a specific model.
         */
        fun downloadModel(spec: DeviceCapabilityDetector.OnDeviceModelSpec) {
            viewModelScope.launch {
                modelDownloadManager.downloadModel(spec)
                refreshAvailableModels()
            }
        }

        /**
         * Cancel the current download.
         */
        fun cancelDownload() {
            modelDownloadManager.cancelDownload()
        }

        /**
         * Delete a downloaded model.
         */
        fun deleteModel(spec: DeviceCapabilityDetector.OnDeviceModelSpec) {
            modelDownloadManager.deleteModel(spec)
            refreshAvailableModels()
        }

        /**
         * Refresh the available models list.
         */
        fun refreshAvailableModels() {
            _availableModels.value = modelDownloadManager.getAvailableModels()
        }
    }

/**
 * UI state for Settings screen.
 */
data class SettingsUiState(
    val currentPreset: ConfigurationPreset = ConfigurationPreset.FREE,
    val selectedSTTProvider: String = "Android",
    val selectedTTSProvider: String = "Android",
    val selectedLLMProvider: String = "PatchPanel",
    val costPreference: String = "COST",
    val recordingMode: RecordingMode = RecordingMode.VAD,
    val hasDeepgramKey: Boolean = false,
    val hasElevenLabsKey: Boolean = false,
    val hasOpenAIKey: Boolean = false,
    val hasAnthropicKey: Boolean = false,
)
