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

    // EncryptedSharedPreferences for API keys
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Preference keys
    private object PreferenceKeys {
        val SELECTED_STT_PROVIDER = stringPreferencesKey("selected_stt_provider")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")
        val SELECTED_LLM_PROVIDER = stringPreferencesKey("selected_llm_provider")
        val COST_PREFERENCE = stringPreferencesKey("cost_preference")
        val CONFIGURATION_PRESET = stringPreferencesKey("configuration_preset")
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
     * Get selected STT provider name.
     */
    val selectedSTTProvider: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SELECTED_STT_PROVIDER] ?: "Deepgram"
    }

    /**
     * Get selected TTS provider name.
     */
    val selectedTTSProvider: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SELECTED_TTS_PROVIDER] ?: "ElevenLabs"
    }

    /**
     * Get selected LLM provider name.
     */
    val selectedLLMProvider: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SELECTED_LLM_PROVIDER] ?: "PatchPanel"
    }

    /**
     * Get cost preference.
     */
    val costPreference: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferenceKeys.COST_PREFERENCE] ?: "BALANCED"
    }

    /**
     * Get configuration preset.
     */
    val configurationPreset: Flow<ConfigurationPreset> = context.dataStore.data.map { prefs ->
        val presetName = prefs[PreferenceKeys.CONFIGURATION_PRESET] ?: "BALANCED"
        ConfigurationPreset.valueOf(presetName)
    }

    /**
     * Set selected STT provider.
     */
    suspend fun setSTTProvider(providerName: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SELECTED_STT_PROVIDER] = providerName
        }
    }

    /**
     * Set selected TTS provider.
     */
    suspend fun setTTSProvider(providerName: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.SELECTED_TTS_PROVIDER] = providerName
        }
    }

    /**
     * Set selected LLM provider.
     */
    suspend fun setLLMProvider(providerName: String) {
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
     * Apply a configuration preset.
     */
    suspend fun applyPreset(preset: ConfigurationPreset) {
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
 * - BALANCED: Balance of quality, cost, and latency
 * - LOW_LATENCY: Minimize latency for real-time interaction
 * - COST_OPTIMIZED: Minimize cost while maintaining quality
 * - OFFLINE: Use only on-device providers
 */
enum class ConfigurationPreset(
    val sttProvider: String,
    val ttsProvider: String,
    val llmProvider: String,
    val costPreference: String
) {
    BALANCED(
        sttProvider = "Deepgram",
        ttsProvider = "ElevenLabs",
        llmProvider = "PatchPanel",
        costPreference = "BALANCED"
    ),
    LOW_LATENCY(
        sttProvider = "Deepgram",
        ttsProvider = "ElevenLabs",
        llmProvider = "OpenAI",
        costPreference = "QUALITY"
    ),
    COST_OPTIMIZED(
        sttProvider = "Android",
        ttsProvider = "Android",
        llmProvider = "PatchPanel",
        costPreference = "COST"
    ),
    OFFLINE(
        sttProvider = "Android",
        ttsProvider = "Android",
        llmProvider = "OnDevice",
        costPreference = "COST"
    )
}
