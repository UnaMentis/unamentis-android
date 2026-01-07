package com.unamentis.core.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ProviderConfig.
 *
 * Tests configuration management, API key storage, and preset application.
 *
 * Uses Robolectric for Android context and DataStore testing.
 */
@RunWith(RobolectricTestRunner::class)
class ProviderConfigTest {

    private lateinit var context: Context
    private lateinit var providerConfig: ProviderConfig

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        providerConfig = ProviderConfig(context)
    }

    @After
    fun teardown() {
        // Clear all data after each test
        providerConfig.clearAllApiKeys()
    }

    @Test
    fun `default STT provider is Deepgram`() = runBlocking {
        val provider = providerConfig.selectedSTTProvider.first()
        assertEquals("Deepgram", provider)
    }

    @Test
    fun `default TTS provider is ElevenLabs`() = runBlocking {
        val provider = providerConfig.selectedTTSProvider.first()
        assertEquals("ElevenLabs", provider)
    }

    @Test
    fun `default LLM provider is PatchPanel`() = runBlocking {
        val provider = providerConfig.selectedLLMProvider.first()
        assertEquals("PatchPanel", provider)
    }

    @Test
    fun `default cost preference is BALANCED`() = runBlocking {
        val preference = providerConfig.costPreference.first()
        assertEquals("BALANCED", preference)
    }

    @Test
    fun `can set and retrieve STT provider`() = runBlocking {
        providerConfig.setSTTProvider("Android")
        val provider = providerConfig.selectedSTTProvider.first()
        assertEquals("Android", provider)
    }

    @Test
    fun `can set and retrieve TTS provider`() = runBlocking {
        providerConfig.setTTSProvider("Android")
        val provider = providerConfig.selectedTTSProvider.first()
        assertEquals("Android", provider)
    }

    @Test
    fun `can set and retrieve LLM provider`() = runBlocking {
        providerConfig.setLLMProvider("OpenAI")
        val provider = providerConfig.selectedLLMProvider.first()
        assertEquals("OpenAI", provider)
    }

    @Test
    fun `can set and retrieve cost preference`() = runBlocking {
        providerConfig.setCostPreference("QUALITY")
        val preference = providerConfig.costPreference.first()
        assertEquals("QUALITY", preference)
    }

    @Test
    fun `can store and retrieve Deepgram API key`() {
        val testKey = "test_deepgram_key_12345"
        providerConfig.setDeepgramApiKey(testKey)
        val retrievedKey = providerConfig.getDeepgramApiKey()
        assertEquals(testKey, retrievedKey)
    }

    @Test
    fun `can store and retrieve OpenAI API key`() {
        val testKey = "sk-test-openai-key-67890"
        providerConfig.setOpenAIApiKey(testKey)
        val retrievedKey = providerConfig.getOpenAIApiKey()
        assertEquals(testKey, retrievedKey)
    }

    @Test
    fun `can store and retrieve Anthropic API key`() {
        val testKey = "sk-ant-test-key-abcdef"
        providerConfig.setAnthropicApiKey(testKey)
        val retrievedKey = providerConfig.getAnthropicApiKey()
        assertEquals(testKey, retrievedKey)
    }

    @Test
    fun `can store and retrieve ElevenLabs API key`() {
        val testKey = "elevenlabs_test_key_xyz"
        providerConfig.setElevenLabsApiKey(testKey)
        val retrievedKey = providerConfig.getElevenLabsApiKey()
        assertEquals(testKey, retrievedKey)
    }

    @Test
    fun `clearAllApiKeys removes all stored keys`() {
        providerConfig.setDeepgramApiKey("key1")
        providerConfig.setOpenAIApiKey("key2")
        providerConfig.setAnthropicApiKey("key3")

        providerConfig.clearAllApiKeys()

        assertNull(providerConfig.getDeepgramApiKey())
        assertNull(providerConfig.getOpenAIApiKey())
        assertNull(providerConfig.getAnthropicApiKey())
    }

    @Test
    fun `BALANCED preset configures correct providers`() = runBlocking {
        providerConfig.applyPreset(ConfigurationPreset.BALANCED)

        assertEquals("Deepgram", providerConfig.selectedSTTProvider.first())
        assertEquals("ElevenLabs", providerConfig.selectedTTSProvider.first())
        assertEquals("PatchPanel", providerConfig.selectedLLMProvider.first())
        assertEquals("BALANCED", providerConfig.costPreference.first())
    }

    @Test
    fun `LOW_LATENCY preset configures correct providers`() = runBlocking {
        providerConfig.applyPreset(ConfigurationPreset.LOW_LATENCY)

        assertEquals("Deepgram", providerConfig.selectedSTTProvider.first())
        assertEquals("ElevenLabs", providerConfig.selectedTTSProvider.first())
        assertEquals("OpenAI", providerConfig.selectedLLMProvider.first())
        assertEquals("QUALITY", providerConfig.costPreference.first())
    }

    @Test
    fun `COST_OPTIMIZED preset configures correct providers`() = runBlocking {
        providerConfig.applyPreset(ConfigurationPreset.COST_OPTIMIZED)

        assertEquals("Android", providerConfig.selectedSTTProvider.first())
        assertEquals("Android", providerConfig.selectedTTSProvider.first())
        assertEquals("PatchPanel", providerConfig.selectedLLMProvider.first())
        assertEquals("COST", providerConfig.costPreference.first())
    }

    @Test
    fun `OFFLINE preset configures correct providers`() = runBlocking {
        providerConfig.applyPreset(ConfigurationPreset.OFFLINE)

        assertEquals("Android", providerConfig.selectedSTTProvider.first())
        assertEquals("Android", providerConfig.selectedTTSProvider.first())
        assertEquals("OnDevice", providerConfig.selectedLLMProvider.first())
        assertEquals("COST", providerConfig.costPreference.first())
    }

    @Test
    fun `configuration preset persists across instances`() = runBlocking {
        providerConfig.applyPreset(ConfigurationPreset.LOW_LATENCY)

        // Create new instance with same context
        val newConfig = ProviderConfig(context)
        val preset = newConfig.configurationPreset.first()

        assertEquals(ConfigurationPreset.LOW_LATENCY, preset)
    }

    @Test
    fun `API keys persist across instances`() {
        val testKey = "persistent_test_key"
        providerConfig.setOpenAIApiKey(testKey)

        // Create new instance with same context
        val newConfig = ProviderConfig(context)
        val retrievedKey = newConfig.getOpenAIApiKey()

        assertEquals(testKey, retrievedKey)
    }
}
