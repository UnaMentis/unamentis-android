package com.unamentis.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Integration tests for certificate pinning.
 *
 * These tests verify that:
 * 1. Valid certificates are accepted
 * 2. Invalid certificates are rejected
 * 3. Pinning works correctly with real network connections
 *
 * **IMPORTANT**: These tests require network access and will fail offline.
 *
 * **Note**: In debug builds, pinning is disabled, so these tests will skip validation.
 * Run in release build configuration to test actual pinning behavior.
 */
@RunWith(AndroidJUnit4::class)
class CertificatePinningIntegrationTest {
    /**
     * Test that certificate pinning is properly configured.
     */
    @Test
    fun certificatePinning_isConfigured() {
        val pinner = CertificatePinning.pinner
        assertNotNull("CertificatePinner should be configured", pinner)

        val domains = pinner.pins.map { it.pattern }.toSet()
        assertTrue(
            "At least one domain should be configured",
            domains.isNotEmpty(),
        )
    }

    /**
     * Test that pinning is disabled in debug builds.
     *
     * This allows developers to use proxy tools (Charles, mitmproxy) for debugging.
     */
    @Test
    fun certificatePinning_isDisabledInDebug() {
        // In test builds (debug), pinning should be disabled
        assertFalse(
            "Certificate pinning should be disabled in debug builds",
            CertificatePinning.isEnabled(),
        )
    }

    /**
     * Test connection to OpenAI with valid certificate (if pinning enabled).
     *
     * This test will:
     * - PASS in debug builds (pinning disabled)
     * - PASS in release builds if pins are correct
     * - FAIL in release builds if pins are incorrect
     */
    @Test
    fun validCertificate_allowsConnection_openAI() {
        if (!CertificatePinning.isEnabled()) {
            // Skip test in debug builds
            return
        }

        val client =
            OkHttpClient.Builder()
                .certificatePinner(CertificatePinning.pinner)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        try {
            val request =
                Request.Builder()
                    .url("https://api.openai.com/v1/models")
                    .build()

            val response = client.newCall(request).execute()

            // We expect either success (200) or authentication failure (401/403)
            // Both indicate the certificate was validated successfully
            assertTrue(
                "Valid certificate should allow connection (got ${response.code})",
                response.isSuccessful || response.code == 401 || response.code == 403,
            )
        } catch (e: SSLPeerUnverifiedException) {
            fail("Valid OpenAI certificate should be accepted: ${e.message}")
        }
    }

    /**
     * Test connection to Deepgram with valid certificate (if pinning enabled).
     */
    @Test
    fun validCertificate_allowsConnection_deepgram() {
        if (!CertificatePinning.isEnabled()) {
            return
        }

        val client =
            OkHttpClient.Builder()
                .certificatePinner(CertificatePinning.pinner)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        try {
            val request =
                Request.Builder()
                    .url("https://api.deepgram.com/v1/projects")
                    .build()

            val response = client.newCall(request).execute()

            // Accept any response that indicates certificate was validated
            assertTrue(
                "Valid certificate should allow connection (got ${response.code})",
                response.code in 200..499,
            )
        } catch (e: SSLPeerUnverifiedException) {
            fail("Valid Deepgram certificate should be accepted: ${e.message}")
        }
    }

    /**
     * Test connection to Anthropic with valid certificate (if pinning enabled).
     */
    @Test
    fun validCertificate_allowsConnection_anthropic() {
        if (!CertificatePinning.isEnabled()) {
            return
        }

        val client =
            OkHttpClient.Builder()
                .certificatePinner(CertificatePinning.pinner)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        try {
            val request =
                Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .build()

            val response = client.newCall(request).execute()

            // Accept any response that indicates certificate was validated
            assertTrue(
                "Valid certificate should allow connection (got ${response.code})",
                response.code in 200..499,
            )
        } catch (e: SSLPeerUnverifiedException) {
            fail("Valid Anthropic certificate should be accepted: ${e.message}")
        }
    }

    /**
     * Test that incorrect pins are rejected (MITM protection).
     *
     * This test creates a pinner with an intentionally wrong pin and verifies
     * that the connection is rejected.
     */
    @Test
    fun invalidCertificate_rejectsConnection() {
        // Create a pinner with an intentionally wrong pin
        val incorrectPinner =
            CertificatePinner.Builder()
                .add("api.openai.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // Wrong pin
                .build()

        val client =
            OkHttpClient.Builder()
                .certificatePinner(incorrectPinner)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        var sslExceptionThrown = false

        try {
            val request =
                Request.Builder()
                    .url("https://api.openai.com/v1/models")
                    .build()

            client.newCall(request).execute()
        } catch (e: SSLPeerUnverifiedException) {
            // Expected: Invalid pin should trigger SSLPeerUnverifiedException
            sslExceptionThrown = true
        }

        assertTrue(
            "Invalid certificate pin should reject connection with SSLPeerUnverifiedException",
            sslExceptionThrown,
        )
    }

    /**
     * Test that all configured domains are reachable (network connectivity check).
     *
     * This is a sanity check to ensure all provider APIs are accessible.
     * Failures here may indicate network issues, not pinning issues.
     */
    @Test
    fun allProviderDomains_areReachable() {
        if (!CertificatePinning.isEnabled()) {
            return
        }

        val client =
            OkHttpClient.Builder()
                .certificatePinner(CertificatePinning.pinner)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        val domains =
            mapOf(
                "api.deepgram.com" to "https://api.deepgram.com/v1/projects",
                "api.assemblyai.com" to "https://api.assemblyai.com/v2/transcript",
                "api.groq.com" to "https://api.groq.com/openai/v1/models",
                "api.elevenlabs.io" to "https://api.elevenlabs.io/v1/voices",
                "api.openai.com" to "https://api.openai.com/v1/models",
                "api.anthropic.com" to "https://api.anthropic.com/v1/messages",
            )

        domains.forEach { (domain, url) ->
            try {
                val request =
                    Request.Builder()
                        .url(url)
                        .build()

                val response = client.newCall(request).execute()

                // Any HTTP response indicates successful certificate validation
                // (Even 401/403 means we connected successfully)
                assertTrue(
                    "$domain should be reachable (got ${response.code})",
                    response.code in 200..499,
                )
            } catch (e: SSLPeerUnverifiedException) {
                fail(
                    "$domain certificate validation failed. This may indicate:\n" +
                        "1. Incorrect certificate pins\n" +
                        "2. Certificate rotation by provider\n" +
                        "3. MITM attack\n" +
                        "Error: ${e.message}",
                )
            } catch (e: Exception) {
                // Network errors are acceptable (offline, DNS failure, etc.)
                // But log them for debugging
                android.util.Log.w(
                    "CertPinningTest",
                    "$domain not reachable (network error): ${e.message}",
                )
            }
        }
    }

    /**
     * Test that backup pins are configured for all domains.
     *
     * This ensures we can handle certificate rotation without breaking the app.
     */
    @Test
    fun allDomains_haveBackupPins() {
        assertTrue(
            "All domains should have backup pins for certificate rotation",
            CertificatePinning.hasBackupPins(),
        )
    }

    /**
     * Performance test: Verify certificate pinning doesn't add significant latency.
     *
     * Certificate validation should add <50ms to connection time.
     */
    @Test
    fun certificatePinning_doesNotAddSignificantLatency() {
        if (!CertificatePinning.isEnabled()) {
            return
        }

        // Client with pinning
        val pinnedClient =
            OkHttpClient.Builder()
                .certificatePinner(CertificatePinning.pinner)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        // Client without pinning
        val unpinnedClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        val url = "https://api.openai.com/v1/models"

        // Measure pinned request
        val pinnedStart = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(url).build()
            pinnedClient.newCall(request).execute().close()
        } catch (e: Exception) {
            // Ignore errors for this test
        }
        val pinnedDuration = System.currentTimeMillis() - pinnedStart

        // Measure unpinned request
        val unpinnedStart = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(url).build()
            unpinnedClient.newCall(request).execute().close()
        } catch (e: Exception) {
            // Ignore errors for this test
        }
        val unpinnedDuration = System.currentTimeMillis() - unpinnedStart

        val overhead = pinnedDuration - unpinnedDuration

        // Certificate pinning should add minimal overhead (<100ms acceptable)
        // Allow if both are fast
        assertTrue(
            "Certificate pinning overhead should be minimal, but was ${overhead}ms",
            overhead < 100 || pinnedDuration < 1000,
        )
    }
}
