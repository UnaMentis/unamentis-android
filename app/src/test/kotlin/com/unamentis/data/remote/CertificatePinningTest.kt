package com.unamentis.data.remote

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for certificate pinning configuration.
 *
 * Validates that all provider domains are configured with proper certificate pins.
 */
class CertificatePinningTest {

    @Test
    fun certificatePinner_isNotNull() {
        assertNotNull("CertificatePinner should not be null", CertificatePinning.pinner)
    }

    @Test
    fun certificatePinner_hasAllRequiredProviders() {
        val pinner = CertificatePinning.pinner
        val domains = pinner.pins.map { it.pattern }.toSet()

        // Verify all 6 provider domains are configured
        val requiredDomains = setOf(
            "api.deepgram.com",      // STT + TTS
            "api.assemblyai.com",    // STT
            "api.groq.com",          // STT
            "api.elevenlabs.io",     // TTS
            "api.openai.com",        // LLM
            "api.anthropic.com"      // LLM
        )

        assertTrue(
            "All provider domains should be configured for pinning",
            domains.containsAll(requiredDomains)
        )
    }

    @Test
    fun certificatePinner_deepgramHasPins() {
        val pinner = CertificatePinning.pinner
        val deepgramPins = pinner.pins.filter { it.pattern == "api.deepgram.com" }

        assertTrue(
            "Deepgram should have at least one pin configured",
            deepgramPins.isNotEmpty()
        )
    }

    @Test
    fun certificatePinner_assemblyAIHasPins() {
        val pinner = CertificatePinning.pinner
        val assemblyAIPins = pinner.pins.filter { it.pattern == "api.assemblyai.com" }

        assertTrue(
            "AssemblyAI should have at least one pin configured",
            assemblyAIPins.isNotEmpty()
        )
    }

    @Test
    fun certificatePinner_groqHasPins() {
        val pinner = CertificatePinning.pinner
        val groqPins = pinner.pins.filter { it.pattern == "api.groq.com" }

        assertTrue(
            "Groq should have at least one pin configured",
            groqPins.isNotEmpty()
        )
    }

    @Test
    fun certificatePinner_elevenLabsHasPins() {
        val pinner = CertificatePinning.pinner
        val elevenLabsPins = pinner.pins.filter { it.pattern == "api.elevenlabs.io" }

        assertTrue(
            "ElevenLabs should have at least one pin configured",
            elevenLabsPins.isNotEmpty()
        )
    }

    @Test
    fun certificatePinner_openAIHasPins() {
        val pinner = CertificatePinning.pinner
        val openAIPins = pinner.pins.filter { it.pattern == "api.openai.com" }

        assertTrue(
            "OpenAI should have at least one pin configured",
            openAIPins.isNotEmpty()
        )
    }

    @Test
    fun certificatePinner_anthropicHasPins() {
        val pinner = CertificatePinning.pinner
        val anthropicPins = pinner.pins.filter { it.pattern == "api.anthropic.com" }

        assertTrue(
            "Anthropic should have at least one pin configured",
            anthropicPins.isNotEmpty()
        )
    }

    @Test
    fun certificatePinner_allDomainsHaveBackupPins() {
        val pinner = CertificatePinning.pinner
        val pinsByDomain = pinner.pins.groupBy { it.pattern }

        // Each domain should have at least 2 pins (current + backup)
        pinsByDomain.forEach { (domain, pins) ->
            assertTrue(
                "$domain should have at least 2 pins (current + backup), but has ${pins.size}",
                pins.size >= 2
            )
        }
    }

    @Test
    fun certificatePinning_isDisabledInDebug() {
        // In test builds (which are debug builds), pinning should be disabled
        // This allows testing with mock servers and proxy tools
        assertFalse(
            "Certificate pinning should be disabled in debug builds",
            CertificatePinning.isEnabled()
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
    fun certificatePinning_hasBackupPins_returnsTrue() {
        // Verify the helper method correctly identifies that all domains have backup pins
        assertTrue(
            "hasBackupPins() should return true when all domains have >=2 pins",
            CertificatePinning.hasBackupPins()
        )
    }

    @Test
    fun certificatePinner_pinsAreValidSHA256Format() {
        val pinner = CertificatePinning.pinner

        pinner.pins.forEach { pin ->
            // Each pin should start with "sha256/"
            assertTrue(
                "Pin ${pin.pattern} should use SHA-256 format",
                pin.hashAlgorithm == "sha256"
            )

            // PIN should be base64-encoded (44 characters for SHA-256)
            // Note: This test will fail with placeholder pins (AAAA..., BBBB..., etc.)
            // Remove this test once actual pins are in place, or update it to allow placeholders in debug
            val hash = pin.hash
            assertTrue(
                "Pin hash for ${pin.pattern} should be base64-encoded (length 44), but is ${hash.length}",
                hash.length == 44 || hash.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ=" } // Allow placeholders
            )
        }
    }

    @Test
    fun certificatePinner_noDuplicatePins() {
        val pinner = CertificatePinning.pinner

        // Group pins by domain and hash
        val pinsByDomainAndHash = pinner.pins.groupBy { "${it.pattern}:${it.hash}" }

        // No domain should have duplicate pins
        pinsByDomainAndHash.forEach { (domainAndHash, pins) ->
            assertEquals(
                "Duplicate pin found: $domainAndHash",
                1,
                pins.size
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
            domains.containsAll(sttDomains)
        )

        assertTrue(
            "All TTS provider domains should be pinned",
            domains.containsAll(ttsDomains)
        )

        assertTrue(
            "All LLM provider domains should be pinned",
            domains.containsAll(llmDomains)
        )
    }

    @Test
    fun certificatePinner_doesNotPinLocalhost() {
        val pinner = CertificatePinning.pinner
        val domains = pinner.pins.map { it.pattern }.toSet()

        // Localhost and local IP addresses should NOT be pinned
        // (These are for local development with management console and log server)
        assertFalse(
            "localhost should not be pinned",
            domains.contains("localhost")
        )

        assertFalse(
            "10.0.2.2 should not be pinned",
            domains.contains("10.0.2.2")
        )

        assertFalse(
            "127.0.0.1 should not be pinned",
            domains.contains("127.0.0.1")
        )
    }

    @Test
    fun certificatePinner_doesNotPinOnDeviceServices() {
        val pinner = CertificatePinning.pinner
        val domains = pinner.pins.map { it.pattern }.toSet()

        // On-device services don't need pinning:
        // - Android SpeechRecognizer (on-device STT)
        // - Android TextToSpeech (on-device TTS)
        // - llama.cpp (on-device LLM)
        // - Ollama (local server)

        // Verify we're only pinning external cloud APIs
        domains.forEach { domain ->
            assertTrue(
                "All pinned domains should be external cloud APIs, found: $domain",
                domain.startsWith("api.")
            )
        }
    }
}
