package com.unamentis.data.remote

import com.unamentis.BuildConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Unit tests for certificate pinning configuration.
 *
 * Validates that all provider domains are configured with proper certificate pins.
 *
 * Note: OkHttp's CertificatePinner.Pin class doesn't expose hostname in toString(),
 * so we test using the helper methods in CertificatePinning instead.
 */
class CertificatePinningTest {
    @Test
    fun certificatePinner_isNotNull() {
        assertNotNull("CertificatePinner should not be null", CertificatePinning.pinner)
    }

    @Test
    fun certificatePinner_hasPinsConfigured() {
        val pinner = CertificatePinning.pinner
        // The pinner should have pins configured
        assertNotNull("Pins should be configured", pinner.pins)
        assertTrue("Should have some pins configured", pinner.pins.isNotEmpty())
    }

    @Test
    fun certificatePinning_isDisabledInDebug() {
        // This test only applies to debug builds - skip in release builds
        assumeTrue("Test only runs in debug builds", BuildConfig.DEBUG)

        // In debug builds, pinning should be disabled
        // This allows testing with mock servers and proxy tools
        assertFalse(
            "Certificate pinning should be disabled in debug builds",
            CertificatePinning.isEnabled(),
        )
    }

    @Test
    fun certificatePinning_isEnabledInRelease() {
        // This test only applies to release builds - skip in debug builds
        assumeTrue("Test only runs in release builds", !BuildConfig.DEBUG)

        // In release builds, pinning should be enabled for security
        assertTrue(
            "Certificate pinning should be enabled in release builds",
            CertificatePinning.isEnabled(),
        )
    }

    @Test
    fun certificatePinning_getPinnedDomains_returnsAllDomains() {
        val domains = CertificatePinning.getPinnedDomains()

        assertEquals("Should have 6 pinned domains", 6, domains.size)

        // Verify all expected domains are present
        assertTrue("Should contain Deepgram", domains.contains("api.deepgram.com"))
        assertTrue("Should contain AssemblyAI", domains.contains("api.assemblyai.com"))
        assertTrue("Should contain Groq", domains.contains("api.groq.com"))
        assertTrue("Should contain ElevenLabs", domains.contains("api.elevenlabs.io"))
        assertTrue("Should contain OpenAI", domains.contains("api.openai.com"))
        assertTrue("Should contain Anthropic", domains.contains("api.anthropic.com"))
    }

    @Test
    fun certificatePinner_pinsUseSHA256() {
        val pinner = CertificatePinning.pinner

        // Each pin string should contain "sha256/" (OkHttp Pin.toString() format)
        pinner.pins.forEach { pin ->
            assertTrue(
                "Pin should use SHA-256 format",
                pin.toString().contains("sha256/"),
            )
        }
    }

    @Test
    fun certificatePinner_coversAllProviderTypes() {
        val domains = CertificatePinning.getPinnedDomains()

        // Verify we have at least:
        // - 3 STT providers (Deepgram, AssemblyAI, Groq)
        // - 2 TTS providers (Deepgram, ElevenLabs)
        // - 2 LLM providers (OpenAI, Anthropic)

        val sttDomains = listOf("api.deepgram.com", "api.assemblyai.com", "api.groq.com")
        val ttsDomains = listOf("api.deepgram.com", "api.elevenlabs.io")
        val llmDomains = listOf("api.openai.com", "api.anthropic.com")

        assertTrue(
            "All STT provider domains should be pinned",
            domains.containsAll(sttDomains),
        )

        assertTrue(
            "All TTS provider domains should be pinned",
            domains.containsAll(ttsDomains),
        )

        assertTrue(
            "All LLM provider domains should be pinned",
            domains.containsAll(llmDomains),
        )
    }

    @Test
    fun certificatePinner_doesNotPinLocalhost() {
        val domains = CertificatePinning.getPinnedDomains()

        // Localhost and local IP addresses should NOT be pinned
        // (These are for local development with management console and log server)
        assertFalse(
            "localhost should not be pinned",
            domains.contains("localhost"),
        )

        assertFalse(
            "10.0.2.2 should not be pinned",
            domains.contains("10.0.2.2"),
        )

        assertFalse(
            "127.0.0.1 should not be pinned",
            domains.contains("127.0.0.1"),
        )
    }

    @Test
    fun certificatePinner_doesNotPinOnDeviceServices() {
        val domains = CertificatePinning.getPinnedDomains()

        // On-device services don't need pinning:
        // - Android SpeechRecognizer (on-device STT)
        // - Android TextToSpeech (on-device TTS)
        // - llama.cpp (on-device LLM)
        // - Ollama (local server)

        // Verify we're only pinning external cloud APIs
        domains.forEach { domain ->
            assertTrue(
                "All pinned domains should be external cloud APIs, found: $domain",
                domain.startsWith("api."),
            )
        }
    }
}
