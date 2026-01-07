# Certificate Pinning Implementation Plan

**Status**: ❌ Not Implemented
**Priority**: HIGH (Security hardening requirement for production)
**Estimated Time**: 1 day
**Phase**: Phase 6 (Polish & Testing)

## Overview

Certificate pinning is a security mechanism that prevents man-in-the-middle (MITM) attacks by validating that the server's SSL/TLS certificate matches an expected certificate or public key. This is **required for production** to protect API keys and user data.

## Current State

**Gap Identified**: The specification requires certificate pinning for all provider APIs, but it is not currently implemented.

**Impact**: Without certificate pinning, the app is vulnerable to MITM attacks where an attacker could intercept API requests, steal API keys, or inject malicious responses.

## Specification Requirements

From `ANDROID_PORT_SPECIFICATION.md` (Section 8.5 - Security Hardening):

> **Certificate Pinning**: Pin SSL/TLS certificates for all provider APIs to prevent man-in-the-middle attacks. Use OkHttp's `CertificatePinner` to validate server certificates against known good certificates.

**Providers Requiring Pinning**:
1. **STT Providers**:
   - Deepgram: `api.deepgram.com`
   - AssemblyAI: `api.assemblyai.com`
   - Groq: `api.groq.com`

2. **TTS Providers**:
   - ElevenLabs: `api.elevenlabs.io`
   - Deepgram: `api.deepgram.com`

3. **LLM Providers**:
   - OpenAI: `api.openai.com`
   - Anthropic: `api.anthropic.com`

4. **Management Console**:
   - Local server: `localhost` (development only, no pinning needed)

**Note**: Android SpeechRecognizer, Android TextToSpeech, Ollama (local), and llama.cpp (on-device) do not require pinning as they don't communicate over the network.

## Implementation Strategy

### 1. Certificate Collection

We need to obtain the SHA-256 hash of the public key for each provider's certificate.

**Method 1: Using OpenSSL (Recommended)**
```bash
# Example for OpenAI
echo | openssl s_client -connect api.openai.com:443 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64

# Repeat for each provider
```

**Method 2: Using OkHttp (Programmatic)**
```kotlin
// Temporary code to extract pins during development
val client = OkHttpClient.Builder()
    .eventListenerFactory { connection ->
        object : EventListener() {
            override fun callEnd(call: Call) {
                val peerCertificates = connection.handshake()?.peerCertificates
                peerCertificates?.forEach { cert ->
                    println("sha256/${CertificatePinner.pin(cert)}")
                }
            }
        }
    }
    .build()
```

**Best Practice**: Pin **at least 2 certificates** per domain:
1. Current certificate
2. Backup certificate (in case provider rotates certificates)

### 2. CertificatePinner Configuration

Create `app/src/main/kotlin/com/unamentis/data/remote/CertificatePinning.kt`:

```kotlin
package com.unamentis.data.remote

import okhttp3.CertificatePinner

/**
 * Certificate pinning configuration for all provider APIs.
 *
 * Pins are SHA-256 hashes of public keys to prevent MITM attacks.
 * Each domain has at least 2 pins (current + backup).
 */
object CertificatePinning {

    /**
     * OkHttp CertificatePinner configured for all provider APIs.
     */
    val pinner: CertificatePinner = CertificatePinner.Builder()
        // Deepgram (STT + TTS)
        .add("api.deepgram.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // Current
        .add("api.deepgram.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup

        // AssemblyAI (STT)
        .add("api.assemblyai.com", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=") // Current
        .add("api.assemblyai.com", "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=") // Backup

        // Groq (STT)
        .add("api.groq.com", "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=") // Current
        .add("api.groq.com", "sha256/FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=") // Backup

        // ElevenLabs (TTS)
        .add("api.elevenlabs.io", "sha256/GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG=") // Current
        .add("api.elevenlabs.io", "sha256/HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH=") // Backup

        // OpenAI (LLM)
        .add("api.openai.com", "sha256/IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII=") // Current
        .add("api.openai.com", "sha256/JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ=") // Backup

        // Anthropic (LLM)
        .add("api.anthropic.com", "sha256/KKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK=") // Current
        .add("api.anthropic.com", "sha256/LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL=") // Backup

        .build()

    /**
     * Returns true if certificate pinning should be enabled.
     * Disabled in debug builds to allow local testing with proxy tools.
     */
    fun isEnabled(): Boolean {
        return !BuildConfig.DEBUG
    }
}
```

**Note**: The placeholder hashes (`AAAA...`, `BBBB...`, etc.) must be replaced with actual certificate pins obtained in Step 1.

### 3. OkHttpClient Integration

Update `app/src/main/kotlin/com/unamentis/data/remote/ApiClient.kt`:

```kotlin
package com.unamentis.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(ClientHeaderInterceptor()) // Existing
        .apply {
            // Only enable certificate pinning in release builds
            if (CertificatePinning.isEnabled()) {
                certificatePinner(CertificatePinning.pinner)
            }
        }
        .build()

    // Provider-specific clients inherit pinning from baseClient
    val deepgramClient: OkHttpClient = baseClient.newBuilder().build()
    val assemblyAIClient: OkHttpClient = baseClient.newBuilder().build()
    val groqClient: OkHttpClient = baseClient.newBuilder().build()
    val elevenLabsClient: OkHttpClient = baseClient.newBuilder().build()
    val openAIClient: OkHttpClient = baseClient.newBuilder().build()
    val anthropicClient: OkHttpClient = baseClient.newBuilder().build()
}
```

**Key Decision**: Disable pinning in debug builds to allow:
- Testing with proxy tools (Charles, mitmproxy)
- Local testing with self-signed certificates
- Easier debugging

### 4. BuildConfig Configuration

Ensure `BuildConfig.DEBUG` is properly configured in `app/build.gradle.kts`:

```kotlin
android {
    buildTypes {
        debug {
            isDebuggable = true
            // Certificate pinning disabled in debug builds
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Certificate pinning ENABLED in release builds
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 5. Error Handling

Certificate pinning failures will throw `SSLPeerUnverifiedException`. We need to handle this gracefully:

**Update Provider Services**:
```kotlin
// Example in DeepgramSTTService.kt
override suspend fun transcribe(audioStream: Flow<ByteArray>): Flow<STTResult> = flow {
    try {
        // ... existing WebSocket logic
    } catch (e: SSLPeerUnverifiedException) {
        logger.error("Certificate pinning failed for Deepgram: ${e.message}")
        emit(STTResult.Error("Certificate validation failed. Please check your network connection."))
    } catch (e: Exception) {
        // ... existing error handling
    }
}
```

**User-Facing Message**:
- Don't expose technical details ("Certificate pinning failed")
- Use generic message ("Network security validation failed")
- Log detailed error for debugging

### 6. Testing Strategy

#### 6.1 Unit Tests

Create `app/src/test/kotlin/com/unamentis/data/remote/CertificatePinningTest.kt`:

```kotlin
package com.unamentis.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.security.cert.CertificateException

/**
 * Tests certificate pinning configuration.
 */
class CertificatePinningTest {

    @Test
    fun certificatePinner_hasAllProviders() {
        val pinner = CertificatePinning.pinner

        // Verify all provider domains are configured
        assertTrue(pinner.pins.any { it.pattern == "api.deepgram.com" })
        assertTrue(pinner.pins.any { it.pattern == "api.assemblyai.com" })
        assertTrue(pinner.pins.any { it.pattern == "api.groq.com" })
        assertTrue(pinner.pins.any { it.pattern == "api.elevenlabs.io" })
        assertTrue(pinner.pins.any { it.pattern == "api.openai.com" })
        assertTrue(pinner.pins.any { it.pattern == "api.anthropic.com" })
    }

    @Test
    fun certificatePinner_hasBackupPins() {
        val pinner = CertificatePinning.pinner

        // Each domain should have at least 2 pins (current + backup)
        pinner.pins.groupBy { it.pattern }.forEach { (domain, pins) ->
            assertTrue("$domain should have at least 2 pins", pins.size >= 2)
        }
    }

    @Test
    fun certificatePinning_isDisabledInDebug() {
        // In test builds (debug), pinning should be disabled
        assertFalse(CertificatePinning.isEnabled())
    }
}
```

#### 6.2 Integration Tests

Create `app/src/androidTest/kotlin/com/unamentis/data/remote/CertificatePinningIntegrationTest.kt`:

```kotlin
package com.unamentis.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.cert.CertificateException

/**
 * Integration tests for certificate pinning.
 *
 * These tests verify that pinning works correctly in release builds.
 */
@RunWith(AndroidJUnit4::class)
class CertificatePinningIntegrationTest {

    @Test
    fun validCertificate_allowsConnection() {
        // Create client with pinning enabled
        val client = OkHttpClient.Builder()
            .certificatePinner(CertificatePinning.pinner)
            .build()

        // Attempt connection to OpenAI (with valid certificate)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .build()

        val response = client.newCall(request).execute()
        assertTrue("Valid certificate should allow connection", response.isSuccessful || response.code == 401)
    }

    @Test(expected = CertificateException::class)
    fun invalidCertificate_rejectsConnection() {
        // Create client with incorrect pin (simulates MITM attack)
        val incorrectPinner = okhttp3.CertificatePinner.Builder()
            .add("api.openai.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // Wrong pin
            .build()

        val client = OkHttpClient.Builder()
            .certificatePinner(incorrectPinner)
            .build()

        // This should throw SSLPeerUnverifiedException
        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .build()

        client.newCall(request).execute()
    }
}
```

**Note**: These tests require network access and will fail if run offline.

#### 6.3 Manual Testing

**Test Scenarios**:
1. **Normal Operation**: Run app with certificate pinning enabled, verify all providers work
2. **MITM Simulation**: Use proxy tool (Charles, mitmproxy) to intercept traffic, verify connection fails
3. **Certificate Rotation**: Temporarily remove backup pin, verify app handles certificate rotation gracefully

**Test Checklist**:
- [ ] Debug build: Certificate pinning disabled, proxy tools work
- [ ] Release build: Certificate pinning enabled, proxy tools fail
- [ ] Each provider can establish connection successfully
- [ ] Invalid certificate triggers appropriate error message
- [ ] Error logged for debugging (not shown to user)

### 7. Monitoring & Maintenance

#### 7.1 Certificate Rotation Monitoring

**Problem**: If a provider rotates their certificate and we only have the old pin, the app will break.

**Solution**: Use **certificate transparency logs** to monitor for certificate changes:
- Set up monitoring via [crt.sh](https://crt.sh) or similar service
- Alert when provider issues new certificate
- Update pins in app before old certificate expires

#### 7.2 Graceful Degradation

**Option**: Allow certificate pinning to be remotely disabled via server configuration.

```kotlin
// In ApiClient.kt
.apply {
    // Check remote config for emergency pinning disable
    if (CertificatePinning.isEnabled() && !remoteConfig.isPinningDisabled()) {
        certificatePinner(CertificatePinning.pinner)
    }
}
```

**Use Case**: If a provider rotates certificates unexpectedly and the app breaks, we can push a server config update to temporarily disable pinning while we release an app update with new pins.

#### 7.3 Backup Plan

**Always maintain at least 2 pins per domain**:
1. Current certificate
2. Next/backup certificate (from provider documentation)

**Providers typically publish**:
- Current certificate chain
- Backup certificate chain (for rotation)
- Certificate transparency logs

## Implementation Checklist

### Phase 1: Certificate Collection (2-3 hours)
- [ ] Extract SHA-256 pins for Deepgram (current + backup)
- [ ] Extract SHA-256 pins for AssemblyAI (current + backup)
- [ ] Extract SHA-256 pins for Groq (current + backup)
- [ ] Extract SHA-256 pins for ElevenLabs (current + backup)
- [ ] Extract SHA-256 pins for OpenAI (current + backup)
- [ ] Extract SHA-256 pins for Anthropic (current + backup)
- [ ] Document pin sources (where they came from)
- [ ] Verify each pin with test connection

### Phase 2: Code Implementation (3-4 hours)
- [ ] Create `CertificatePinning.kt` with all pins
- [ ] Update `ApiClient.kt` to use `CertificatePinner`
- [ ] Add `BuildConfig.DEBUG` check to disable in debug builds
- [ ] Update all provider services to handle `SSLPeerUnverifiedException`
- [ ] Add user-facing error messages
- [ ] Add detailed logging for pinning failures

### Phase 3: Testing (2-3 hours)
- [ ] Write unit tests for `CertificatePinning`
- [ ] Write integration tests for valid/invalid certificates
- [ ] Manual test: Debug build with proxy tools (should work)
- [ ] Manual test: Release build with proxy tools (should fail)
- [ ] Manual test: Each provider in release build (should work)
- [ ] Verify error messages are user-friendly

### Phase 4: Documentation (1 hour)
- [ ] Document certificate pinning in README
- [ ] Document how to update pins (for future developers)
- [ ] Document emergency disable procedure
- [ ] Add certificate rotation monitoring to operations runbook

## Success Criteria

✅ **Certificate pinning implemented**: All 6 provider APIs have at least 2 pins each
✅ **Tests passing**: Unit tests and integration tests pass
✅ **Debug builds unaffected**: Can still use proxy tools for debugging
✅ **Release builds protected**: MITM attacks are prevented
✅ **Error handling**: Graceful user-facing error messages
✅ **Documentation**: Clear instructions for future maintenance

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Provider rotates certificate | App breaks | Pin 2 certificates per domain, monitor cert logs |
| Incorrect pin | Connection fails | Test each pin before deployment |
| Emergency certificate rotation | App unusable | Implement remote config disable flag |
| Pinning too strict | Breaks legitimate traffic | Disable in debug builds |

## References

- [OkHttp Certificate Pinning Guide](https://square.github.io/okhttp/features/https/)
- [Android Security Best Practices](https://developer.android.com/training/articles/security-tips)
- [OWASP Certificate Pinning](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- `ANDROID_PORT_SPECIFICATION.md` Section 8.5 (Security Hardening)

## Estimated Timeline

| Phase | Time |
|-------|------|
| Certificate Collection | 2-3 hours |
| Code Implementation | 3-4 hours |
| Testing | 2-3 hours |
| Documentation | 1 hour |
| **Total** | **8-11 hours (1 day)** |

---

**Next Steps**: Once JDK is installed and the app can compile, implement certificate pinning following this plan before production release.
