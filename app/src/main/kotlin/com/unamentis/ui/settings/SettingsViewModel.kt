package com.unamentis.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.config.ConfigurationPreset
import com.unamentis.core.config.ProviderConfig
import com.unamentis.core.config.RecordingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Responsibilities:
 * - Manage provider configuration
 * - Handle API key updates
 * - Apply configuration presets
 * - Expose settings state to UI
 *
 * @property providerConfig Provider configuration manager
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val providerConfig: ProviderConfig,
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
