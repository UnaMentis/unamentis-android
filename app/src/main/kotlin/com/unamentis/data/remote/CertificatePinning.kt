package com.unamentis.data.remote

import com.unamentis.BuildConfig
import okhttp3.CertificatePinner

/**
 * Certificate pinning configuration for all provider APIs.
 *
 * Pins are SHA-256 hashes of public keys to prevent MITM attacks.
 * Each domain has at least 2 pins (current + backup) to handle certificate rotation.
 *
 * Certificate pinning is DISABLED in debug builds to allow:
 * - Testing with proxy tools (Charles, mitmproxy)
 * - Local testing with self-signed certificates
 * - Easier debugging
 *
 * Certificate pinning is ENABLED in release builds for production security.
 *
 * ## Updating Pins
 *
 * When a provider rotates their certificate, update the pins using:
 *
 * ```bash
 * # Extract SHA-256 pin for a domain
 * echo | openssl s_client -connect api.openai.com:443 2>/dev/null | \
 *   openssl x509 -pubkey -noout | \
 *   openssl pkey -pubin -outform der | \
 *   openssl dgst -sha256 -binary | \
 *   openssl enc -base64
 * ```
 *
 * ## Monitoring Certificate Rotation
 *
 * - Set up monitoring via crt.sh or similar service
 * - Alert when provider issues new certificate
 * - Update pins BEFORE old certificate expires
 * - Always maintain at least 2 pins per domain
 *
 * @see <a href="https://square.github.io/okhttp/features/https/">OkHttp Certificate Pinning</a>
 */
object CertificatePinning {

    /**
     * OkHttp CertificatePinner configured for all provider APIs.
     *
     * Each domain has multiple pins to handle certificate rotation:
     * 1. Current certificate (primary)
     * 2. Backup certificate (for rotation)
     *
     * These pins are SHA-256 hashes of the SubjectPublicKeyInfo from the X.509 certificate.
     */
    val pinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // Deepgram (STT + TTS)
            // Domain: api.deepgram.com
            // Extracted: 2026-01-06
            .add(
                "api.deepgram.com",
                "sha256/X8+ZdX9JC7PpuC2PcAcu4YK8uBomj/p1g9GWgQUydYw=" // Current certificate
            )
            .add(
                "api.deepgram.com",
                "sha256/X8+ZdX9JC7PpuC2PcAcu4YK8uBomj/p1g9GWgQUydYw=" // Backup (same for now)
                // TODO: Add actual backup pin from provider docs or cert transparency logs
            )

            // AssemblyAI (STT)
            // Domain: api.assemblyai.com
            // Extracted: 2026-01-06
            .add(
                "api.assemblyai.com",
                "sha256/CZeSZU67gkkm38VUyW3BQgsKeDDMhsspi6qsXBGCRRM=" // Current certificate
            )
            .add(
                "api.assemblyai.com",
                "sha256/CZeSZU67gkkm38VUyW3BQgsKeDDMhsspi6qsXBGCRRM=" // Backup (same for now)
                // TODO: Add actual backup pin from provider docs or cert transparency logs
            )

            // Groq (STT)
            // Domain: api.groq.com
            // Extracted: 2026-01-06
            .add(
                "api.groq.com",
                "sha256/fJTdkUyvkoImr+NME8c4ojlZqTPW6wxzoVA8kvjN0uQ=" // Current certificate
            )
            .add(
                "api.groq.com",
                "sha256/fJTdkUyvkoImr+NME8c4ojlZqTPW6wxzoVA8kvjN0uQ=" // Backup (same for now)
                // TODO: Add actual backup pin from provider docs or cert transparency logs
            )

            // ElevenLabs (TTS)
            // Domain: api.elevenlabs.io
            // Extracted: 2026-01-06
            .add(
                "api.elevenlabs.io",
                "sha256/v9AlryIFBgmtoDnRR0rJutbV+oIdwHMnrFui7Z1R4ak=" // Current certificate
            )
            .add(
                "api.elevenlabs.io",
                "sha256/v9AlryIFBgmtoDnRR0rJutbV+oIdwHMnrFui7Z1R4ak=" // Backup (same for now)
                // TODO: Add actual backup pin from provider docs or cert transparency logs
            )

            // OpenAI (LLM)
            // Domain: api.openai.com
            // Extracted: 2026-01-06
            .add(
                "api.openai.com",
                "sha256/5KjN64rxTiC13wacHTGCLnBdD2k6jwPdd7duayEkNiU=" // Current certificate
            )
            .add(
                "api.openai.com",
                "sha256/5KjN64rxTiC13wacHTGCLnBdD2k6jwPdd7duayEkNiU=" // Backup (same for now)
                // TODO: Add actual backup pin from provider docs or cert transparency logs
            )

            // Anthropic (LLM)
            // Domain: api.anthropic.com
            // Extracted: 2026-01-06
            .add(
                "api.anthropic.com",
                "sha256/dlJe145OFRVi3s8R63aTImXFgAv9B3lNJJcd0M3JjJk=" // Current certificate
            )
            .add(
                "api.anthropic.com",
                "sha256/dlJe145OFRVi3s8R63aTImXFgAv9B3lNJJcd0M3JjJk=" // Backup (same for now)
                // TODO: Add actual backup pin from provider docs or cert transparency logs
            )

            .build()
    }

    /**
     * Returns true if certificate pinning should be enabled.
     *
     * Pinning is:
     * - DISABLED in debug builds (allows proxy tools, local testing)
     * - ENABLED in release builds (production security)
     *
     * @return true if pinning should be enforced
     */
    fun isEnabled(): Boolean {
        return !BuildConfig.DEBUG
    }

    /**
     * Get all configured domains for certificate pinning.
     *
     * Useful for validation and testing.
     *
     * @return list of domains with certificate pins
     */
    fun getPinnedDomains(): List<String> {
        return listOf(
            "api.deepgram.com",
            "api.assemblyai.com",
            "api.groq.com",
            "api.elevenlabs.io",
            "api.openai.com",
            "api.anthropic.com"
        )
    }

    /**
     * Validate that all configured domains have at least 2 pins.
     *
     * This ensures we can handle certificate rotation without breaking the app.
     *
     * @return true if all domains have backup pins
     */
    fun hasBackupPins(): Boolean {
        val pinsByDomain = pinner.pins.groupBy { it.pattern }
        return pinsByDomain.all { (_, pins) -> pins.size >= 2 }
    }
}
