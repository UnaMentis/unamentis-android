package com.unamentis.core.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Provider configuration management with secure API key storage.
 *
 * Responsibilities:
 * - Store and retrieve API keys (encrypted)
 * - Store provider preferences (DataStore)
 * - Provide configuration presets
 *
 * Security:
 * - API keys stored in EncryptedSharedPreferences
 * - Provider preferences in DataStore (non-sensitive)
 *
 * @property context Application context
 */
class ProviderConfig(private val context: Context) {
    // DataStore for non-sensitive preferences
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "provider_config")

    // Regular SharedPreferences for synchronous access (same underlying file as DataStore)
    private val syncPrefs by lazy {
        context.getSharedPreferences("provider_config_sync", Context.MODE_PRIVATE)
    }

    // EncryptedSharedPreferences for API keys
    private val encryptedPrefs by lazy {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        EncryptedSharedPreferences.create(
            context,
            "api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Preference keys
    private object PreferenceKeys {
        val SELECTED_STT_PROVIDER = stringPreferencesKey("selected_stt_provider")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")
        val SELECTED_LLM_PROVIDER = stringPreferencesKey("selected_llm_provider")
        val COST_PREFERENCE = stringPreferencesKey("cost_preference")
        val CONFIGURATION_PRESET = stringPreferencesKey("configuration_preset")
        val RECORDING_MODE = stringPreferencesKey("recording_mode")

        // Advanced audio settings
        val SAMPLE_RATE = stringPreferencesKey("sample_rate")
        val ENABLE_VOICE_PROCESSING = stringPreferencesKey("enable_voice_processing")
        val ENABLE_ECHO_CANCELLATION = stringPreferencesKey("enable_echo_cancellation")
        val ENABLE_NOISE_SUPPRESSION = stringPreferencesKey("enable_noise_suppression")

        // VAD settings
        val VAD_THRESHOLD = stringPreferencesKey("vad_threshold")
        val BARGE_IN_THRESHOLD = stringPreferencesKey("barge_in_threshold")
        val ENABLE_BARGE_IN = stringPreferencesKey("enable_barge_in")
        val SILENCE_THRESHOLD_MS = stringPreferencesKey("silence_threshold_ms")

        // LLM settings
        val LLM_TEMPERATURE = stringPreferencesKey("llm_temperature")
        val LLM_MAX_TOKENS = stringPreferencesKey("llm_max_tokens")

        // TTS settings
        val TTS_SPEAKING_RATE = stringPreferencesKey("tts_speaking_rate")
        val TTS_PLAYBACK_SPEED = stringPreferencesKey("tts_playback_speed")

        // Curriculum playback
        val AUTO_CONTINUE_TOPICS = stringPreferencesKey("auto_continue_topics")
    }

    // API key storage keys
    private object ApiKeyKeys {
        const val DEEPGRAM_API_KEY = "deepgram_api_key"
        const val ASSEMBLYAI_API_KEY = "assemblyai_api_key"
        const val GROQ_API_KEY = "groq_api_key"
        const val ELEVENLABS_API_KEY = "elevenlabs_api_key"
        const val OPENAI_API_KEY = "openai_api_key"
        const val ANTHROPIC_API_KEY = "anthropic_api_key"
    }

    /**
     * Get selected STT provider name (as Flow for reactive updates).
     * Defaults to "Android" for free on-device recognition.
     */
    val selectedSTTProvider: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.SELECTED_STT_PROVIDER] ?: "Android"
        }

    /**
     * Get selected TTS provider name (as Flow for reactive updates).
     * Defaults to "Android" for free on-device speech synthesis.
     */
    val selectedTTSProvider: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.SELECTED_TTS_PROVIDER] ?: "Android"
        }

    /**
     * Get selected LLM provider name (as Flow for reactive updates).
     */
    val selectedLLMProvider: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.SELECTED_LLM_PROVIDER] ?: "PatchPanel"
        }

    // Synchronous getters for Hilt dependency injection
    // These use SharedPreferences directly for synchronous access

    /**
     * Get STT provider synchronously (for dependency injection).
     * Defaults to "Android" for free on-device recognition.
     */
    fun getSTTProvider(): String {
        return syncPrefs.getString(PreferenceKeys.SELECTED_STT_PROVIDER.name, "Android") ?: "Android"
    }

    /**
     * Get TTS provider synchronously (for dependency injection).
     * Defaults to "Android" for free on-device speech synthesis.
     */
    fun getTTSProvider(): String {
        return syncPrefs.getString(PreferenceKeys.SELECTED_TTS_PROVIDER.name, "Android") ?: "Android"
    }

    /**
     * Get LLM provider synchronously (for dependency injection).
     */
    fun getLLMProvider(): String {
        return syncPrefs.getString(PreferenceKeys.SELECTED_LLM_PROVIDER.name, "PatchPanel") ?: "PatchPanel"
    }

    /**
     * Get cost preference.
     */
    val costPreference: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.COST_PREFERENCE] ?: "BALANCED"
        }

    /**
     * Get configuration preset.
     */
    val configurationPreset: Flow<ConfigurationPreset> =
        context.dataStore.data.map { prefs ->
            val presetName = prefs[PreferenceKeys.CONFIGURATION_PRESET] ?: "FREE"
            try {
                ConfigurationPreset.valueOf(presetName)
            } catch (_: IllegalArgumentException) {
                // Handle old preset names (e.g., "BALANCED" -> "FREE")
                ConfigurationPreset.FREE
            }
        }

    /**
     * Get recording mode (VAD, PUSH_TO_TALK, or TOGGLE).
     * Defaults to VAD for automatic voice detection.
     */
    val recordingMode: Flow<RecordingMode> =
        context.dataStore.data.map { prefs ->
            val modeName = prefs[PreferenceKeys.RECORDING_MODE] ?: "VAD"
            try {
                RecordingMode.valueOf(modeName)
            } catch (_: IllegalArgumentException) {
                RecordingMode.VAD
            }
        }

    /**
     * Get recording mode synchronously (for dependency injection).
     */
    fun getRecordingMode(): RecordingMode {
        val modeName = syncPrefs.getString(PreferenceKeys.RECORDING_MODE.name, "VAD") ?: "VAD"
        return try {
            RecordingMode.valueOf(modeName)
        } catch (_: IllegalArgumentException) {
            RecordingMode.VAD
        }
    }

    // ==================== Advanced Audio Settings ====================

    /**
     * Sample rate for audio recording (16000, 24000, or 48000 Hz).
     */
    val sampleRate: Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.SAMPLE_RATE]?.toIntOrNull() ?: 48000
        }

    /**
     * Enable voice processing (enhances voice clarity).
     */
    val enableVoiceProcessing: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.ENABLE_VOICE_PROCESSING]?.toBoolean() ?: true
        }

    /**
     * Enable echo cancellation.
     */
    val enableEchoCancellation: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.ENABLE_ECHO_CANCELLATION]?.toBoolean() ?: true
        }

    /**
     * Enable noise suppression.
     */
    val enableNoiseSuppression: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.ENABLE_NOISE_SUPPRESSION]?.toBoolean() ?: true
        }

    // ==================== VAD Settings ====================

    /**
     * VAD detection threshold (0.3 - 0.9).
     */
    val vadThreshold: Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.VAD_THRESHOLD]?.toFloatOrNull() ?: 0.5f
        }

    /**
     * Barge-in (interruption) threshold (0.5 - 0.95).
     */
    val bargeInThreshold: Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.BARGE_IN_THRESHOLD]?.toFloatOrNull() ?: 0.7f
        }

    /**
     * Enable barge-in (speaking interrupts AI).
     */
    val enableBargeIn: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.ENABLE_BARGE_IN]?.toBoolean() ?: true
        }

    /**
     * Silence threshold in milliseconds (how long silence before end of turn).
     */
    val silenceThresholdMs: Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.SILENCE_THRESHOLD_MS]?.toIntOrNull() ?: 1000
        }

    // ==================== LLM Settings ====================

    /**
     * LLM temperature (0.0 - 1.0).
     */
    val llmTemperature: Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.LLM_TEMPERATURE]?.toFloatOrNull() ?: 0.7f
        }

    /**
     * LLM max tokens (256 - 4096).
     */
    val llmMaxTokens: Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.LLM_MAX_TOKENS]?.toIntOrNull() ?: 1024
        }

    // ==================== TTS Settings ====================

    /**
     * TTS speaking rate (0.5 - 2.0).
     */
    val ttsSpeakingRate: Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.TTS_SPEAKING_RATE]?.toFloatOrNull() ?: 1.0f
        }

    /**
     * TTS playback speed multiplier (0.5 - 2.0).
     */
    val ttsPlaybackSpeed: Flow<Float> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.TTS_PLAYBACK_SPEED]?.toFloatOrNull() ?: 1.0f
        }

    // ==================== Curriculum Playback ====================

    /**
     * Auto-continue to next topic when current one finishes.
     */
    val autoContinueTopics: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PreferenceKeys.AUTO_CONTINUE_TOPICS]?.toBoolean() ?: true
        }

    /**
     * Set selected STT provider.
     */
    suspend fun setSTTProvider(providerName: String) {
        // Write to sync prefs for immediate availability
        syncPrefs.edit().putString(PreferenceKeys.SELECTED_STT_PROVIDER.name, providerName).apply()
        // Also write to DataStore for reactive updates
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SELECTED_STT_PROVIDER] = providerName
        }
    }

    /**
     * Set selected TTS provider.
     */
    suspend fun setTTSProvider(providerName: String) {
        // Write to sync prefs for immediate availability
        syncPrefs.edit().putString(PreferenceKeys.SELECTED_TTS_PROVIDER.name, providerName).apply()
        // Also write to DataStore for reactive updates
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SELECTED_TTS_PROVIDER] = providerName
        }
    }

    /**
     * Set selected LLM provider.
     */
    suspend fun setLLMProvider(providerName: String) {
        // Write to sync prefs for immediate availability
        syncPrefs.edit().putString(PreferenceKeys.SELECTED_LLM_PROVIDER.name, providerName).apply()
        // Also write to DataStore for reactive updates
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SELECTED_LLM_PROVIDER] = providerName
        }
    }

    /**
     * Set cost preference.
     */
    suspend fun setCostPreference(preference: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.COST_PREFERENCE] = preference
        }
    }

    /**
     * Set recording mode.
     */
    suspend fun setRecordingMode(mode: RecordingMode) {
        // Write to sync prefs for immediate availability
        syncPrefs.edit().putString(PreferenceKeys.RECORDING_MODE.name, mode.name).apply()
        // Also write to DataStore for reactive updates
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.RECORDING_MODE] = mode.name
        }
    }

    // ==================== Advanced Audio Settings Setters ====================

    /**
     * Set sample rate.
     */
    suspend fun setSampleRate(sampleRate: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SAMPLE_RATE] = sampleRate.toString()
        }
    }

    /**
     * Set voice processing enabled.
     */
    suspend fun setEnableVoiceProcessing(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ENABLE_VOICE_PROCESSING] = enabled.toString()
        }
    }

    /**
     * Set echo cancellation enabled.
     */
    suspend fun setEnableEchoCancellation(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ENABLE_ECHO_CANCELLATION] = enabled.toString()
        }
    }

    /**
     * Set noise suppression enabled.
     */
    suspend fun setEnableNoiseSuppression(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ENABLE_NOISE_SUPPRESSION] = enabled.toString()
        }
    }

    // ==================== VAD Settings Setters ====================

    /**
     * Set VAD detection threshold.
     */
    suspend fun setVadThreshold(threshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.VAD_THRESHOLD] = threshold.toString()
        }
    }

    /**
     * Set barge-in threshold.
     */
    suspend fun setBargeInThreshold(threshold: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.BARGE_IN_THRESHOLD] = threshold.toString()
        }
    }

    /**
     * Set barge-in enabled.
     */
    suspend fun setEnableBargeIn(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ENABLE_BARGE_IN] = enabled.toString()
        }
    }

    /**
     * Set silence threshold in milliseconds.
     */
    suspend fun setSilenceThresholdMs(thresholdMs: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SILENCE_THRESHOLD_MS] = thresholdMs.toString()
        }
    }

    // ==================== LLM Settings Setters ====================

    /**
     * Set LLM temperature.
     */
    suspend fun setLlmTemperature(temperature: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.LLM_TEMPERATURE] = temperature.toString()
        }
    }

    /**
     * Set LLM max tokens.
     */
    suspend fun setLlmMaxTokens(maxTokens: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.LLM_MAX_TOKENS] = maxTokens.toString()
        }
    }

    // ==================== TTS Settings Setters ====================

    /**
     * Set TTS speaking rate.
     */
    suspend fun setTtsSpeakingRate(rate: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.TTS_SPEAKING_RATE] = rate.toString()
        }
    }

    /**
     * Set TTS playback speed.
     */
    suspend fun setTtsPlaybackSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.TTS_PLAYBACK_SPEED] = speed.toString()
        }
    }

    // ==================== Curriculum Settings Setters ====================

    /**
     * Set auto-continue topics enabled.
     */
    suspend fun setAutoContinueTopics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.AUTO_CONTINUE_TOPICS] = enabled.toString()
        }
    }

    /**
     * Apply a configuration preset.
     */
    suspend fun applyPreset(preset: ConfigurationPreset) {
        // Write to sync prefs for immediate availability
        syncPrefs.edit()
            .putString(PreferenceKeys.SELECTED_STT_PROVIDER.name, preset.sttProvider)
            .putString(PreferenceKeys.SELECTED_TTS_PROVIDER.name, preset.ttsProvider)
            .putString(PreferenceKeys.SELECTED_LLM_PROVIDER.name, preset.llmProvider)
            .apply()
        // Also write to DataStore for reactive updates
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.CONFIGURATION_PRESET] = preset.name
            prefs[PreferenceKeys.SELECTED_STT_PROVIDER] = preset.sttProvider
            prefs[PreferenceKeys.SELECTED_TTS_PROVIDER] = preset.ttsProvider
            prefs[PreferenceKeys.SELECTED_LLM_PROVIDER] = preset.llmProvider
            prefs[PreferenceKeys.COST_PREFERENCE] = preset.costPreference
        }
    }

    // API Key Management

    /**
     * Get Deepgram API key.
     */
    fun getDeepgramApiKey(): String? {
        return encryptedPrefs.getString(ApiKeyKeys.DEEPGRAM_API_KEY, null)
    }

    /**
     * Set Deepgram API key.
     */
    fun setDeepgramApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ApiKeyKeys.DEEPGRAM_API_KEY, apiKey).apply()
    }

    /**
     * Get AssemblyAI API key.
     */
    fun getAssemblyAIApiKey(): String? {
        return encryptedPrefs.getString(ApiKeyKeys.ASSEMBLYAI_API_KEY, null)
    }

    /**
     * Set AssemblyAI API key.
     */
    fun setAssemblyAIApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ApiKeyKeys.ASSEMBLYAI_API_KEY, apiKey).apply()
    }

    /**
     * Get Groq API key.
     */
    fun getGroqApiKey(): String? {
        return encryptedPrefs.getString(ApiKeyKeys.GROQ_API_KEY, null)
    }

    /**
     * Set Groq API key.
     */
    fun setGroqApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ApiKeyKeys.GROQ_API_KEY, apiKey).apply()
    }

    /**
     * Get ElevenLabs API key.
     */
    fun getElevenLabsApiKey(): String? {
        return encryptedPrefs.getString(ApiKeyKeys.ELEVENLABS_API_KEY, null)
    }

    /**
     * Set ElevenLabs API key.
     */
    fun setElevenLabsApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ApiKeyKeys.ELEVENLABS_API_KEY, apiKey).apply()
    }

    /**
     * Get OpenAI API key.
     */
    fun getOpenAIApiKey(): String? {
        return encryptedPrefs.getString(ApiKeyKeys.OPENAI_API_KEY, null)
    }

    /**
     * Set OpenAI API key.
     */
    fun setOpenAIApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ApiKeyKeys.OPENAI_API_KEY, apiKey).apply()
    }

    /**
     * Get Anthropic API key.
     */
    fun getAnthropicApiKey(): String? {
        return encryptedPrefs.getString(ApiKeyKeys.ANTHROPIC_API_KEY, null)
    }

    /**
     * Set Anthropic API key.
     */
    fun setAnthropicApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(ApiKeyKeys.ANTHROPIC_API_KEY, apiKey).apply()
    }

    /**
     * Clear all API keys.
     */
    fun clearAllApiKeys() {
        encryptedPrefs.edit().clear().apply()
    }
}

/**
 * Configuration presets for different use cases.
 *
 * Each preset configures providers optimized for:
 * - FREE: Use free on-device services (default for new users)
 * - PREMIUM: High-quality cloud services (Deepgram, ElevenLabs)
 * - LOW_LATENCY: Minimize latency for real-time interaction
 * - OFFLINE: Use only on-device providers (same as FREE)
 */
enum class ConfigurationPreset(
    val sttProvider: String,
    val ttsProvider: String,
    val llmProvider: String,
    val costPreference: String,
) {
    FREE(
        sttProvider = "Android",
        ttsProvider = "Android",
        llmProvider = "PatchPanel",
        costPreference = "COST",
    ),
    PREMIUM(
        sttProvider = "Deepgram",
        ttsProvider = "ElevenLabs",
        llmProvider = "PatchPanel",
        costPreference = "QUALITY",
    ),
    LOW_LATENCY(
        sttProvider = "Deepgram",
        ttsProvider = "ElevenLabs",
        llmProvider = "OpenAI",
        costPreference = "QUALITY",
    ),
    COST_OPTIMIZED(
        sttProvider = "Android",
        ttsProvider = "Android",
        llmProvider = "PatchPanel",
        costPreference = "COST",
    ),
    OFFLINE(
        sttProvider = "Android",
        ttsProvider = "Android",
        llmProvider = "OnDevice",
        costPreference = "COST",
    ),
}

/**
 * Recording mode options for how the app captures user speech.
 *
 * - VAD: Voice Activity Detection - automatic speech detection (default)
 * - PUSH_TO_TALK: Hold-to-record - press and hold to record
 * - TOGGLE: Tap-to-toggle - tap once to start, tap again to stop
 */
enum class RecordingMode(
    val displayName: String,
    val description: String,
) {
    VAD(
        displayName = "Auto (VAD)",
        description = "Automatically detects when you speak",
    ),
    PUSH_TO_TALK(
        displayName = "Push to Talk",
        description = "Hold the mic button to record",
    ),
    TOGGLE(
        displayName = "Toggle",
        description = "Tap to start/stop recording",
    ),
}
