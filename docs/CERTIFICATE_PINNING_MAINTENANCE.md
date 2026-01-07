# Certificate Pinning Maintenance Guide

**Status**: ✅ Implemented
**Last Updated**: 2026-01-06

---

## Overview

This guide explains how to maintain certificate pins for the UnaMentis Android app. Certificate pinning is a security mechanism that prevents man-in-the-middle (MITM) attacks by validating that provider API certificates match expected values.

---

## Quick Reference

| Provider | Domain | Service Type | Pin Count |
|----------|--------|--------------|-----------|
| Deepgram | api.deepgram.com | STT + TTS | 2 |
| AssemblyAI | api.assemblyai.com | STT | 2 |
| Groq | api.groq.com | STT | 2 |
| ElevenLabs | api.elevenlabs.io | TTS | 2 |
| OpenAI | api.openai.com | LLM | 2 |
| Anthropic | api.anthropic.com | LLM | 2 |

**Total**: 6 domains, 12 pins (2 per domain)

---

## Extracting Certificate Pins

### Automated Method (Recommended)

Use the provided script to extract pins from all providers:

```bash
cd /Users/cygoerdt/unamentis-android
./scripts/extract-certificate-pins.sh
```

This script will:
1. Connect to each provider API
2. Extract the current certificate
3. Compute the SHA-256 hash of the public key
4. Generate Kotlin code snippet for `CertificatePinning.kt`

### Manual Method

To extract a pin for a single domain:

```bash
# Example: Extract pin for api.openai.com
echo | openssl s_client -connect api.openai.com:443 -servername api.openai.com 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | \
  openssl enc -base64
```

Output will be a base64-encoded SHA-256 hash like:
```
AbCdEfGhIjKlMnOpQrStUvWxYz0123456789ABCDEF=
```

Use this in `CertificatePinning.kt` as:
```kotlin
.add("api.openai.com", "sha256/AbCdEfGhIjKlMnOpQrStUvWxYz0123456789ABCDEF=")
```

---

## Updating Pins

### When to Update

Update pins when:
1. **Certificate Rotation**: Provider issues a new certificate
2. **Expiration**: Current certificate is approaching expiration
3. **Security Alert**: Provider notifies of certificate update
4. **App Fails**: Certificate pinning failures in production

### How to Update

1. **Extract New Pin**:
   ```bash
   ./scripts/extract-certificate-pins.sh
   ```

2. **Update CertificatePinning.kt**:
   ```kotlin
   // app/src/main/kotlin/com/unamentis/data/remote/CertificatePinning.kt

   .add("api.openai.com", "sha256/NEW_PRIMARY_PIN_HERE")
   .add("api.openai.com", "sha256/OLD_PRIMARY_PIN_HERE") // Becomes backup
   ```

3. **Run Tests**:
   ```bash
   ./scripts/test-quick.sh
   ```

4. **Test on Device** (Release Build):
   ```bash
   ./gradlew assembleRelease
   adb install app/build/outputs/apk/release/app-release.apk
   ```

5. **Verify Connection**:
   - Launch app
   - Trigger provider API call
   - Check logs for certificate validation

6. **Deploy Update**:
   - Create new app version
   - Submit to Google Play
   - Monitor crash reports for SSL failures

### Best Practices

✅ **Always maintain 2 pins per domain** (current + backup)
✅ **Update pins BEFORE old certificate expires**
✅ **Test in staging environment first**
✅ **Monitor certificate transparency logs**
✅ **Document pin source** (date extracted, certificate expiry)

❌ **Never remove the only pin** for a domain
❌ **Don't update pins without testing**
❌ **Don't ignore SSL errors in production**

---

## Monitoring Certificate Rotation

### Certificate Transparency Logs

Monitor these services for certificate changes:

1. **crt.sh**: https://crt.sh
   - Search for domain (e.g., "api.openai.com")
   - Set up alerts for new certificates

2. **Google Transparency Report**: https://transparencyreport.google.com/https/certificates
   - View certificate history
   - Track expiration dates

3. **SSL Labs**: https://www.ssllabs.com/ssltest/
   - Comprehensive certificate analysis
   - Security grade and recommendations

### Setting Up Alerts

**Method 1: Certificate Monitoring Service**
- Use services like SSL Mate, SSL Monitor, or Uptime Robot
- Configure alerts for certificate changes
- Get notified 30 days before expiration

**Method 2: Manual Checks**
```bash
# Check certificate expiration
echo | openssl s_client -connect api.openai.com:443 -servername api.openai.com 2>/dev/null | \
  openssl x509 -noout -dates

# Output:
# notBefore=Nov  1 00:00:00 2025 GMT
# notAfter=Nov  1 23:59:59 2026 GMT
```

**Method 3: Automated Script**
```bash
#!/bin/bash
# check-cert-expiry.sh

domains=(
    "api.deepgram.com"
    "api.assemblyai.com"
    "api.groq.com"
    "api.elevenlabs.io"
    "api.openai.com"
    "api.anthropic.com"
)

for domain in "${domains[@]}"; do
    expiry=$(echo | openssl s_client -connect "$domain:443" -servername "$domain" 2>/dev/null | \
        openssl x509 -noout -enddate | cut -d= -f2)

    echo "$domain expires: $expiry"
done
```

---

## Emergency: Certificate Rotation Breaks App

If a provider rotates their certificate unexpectedly and the app breaks:

### Immediate Response (Same Day)

1. **Verify the Issue**:
   ```bash
   # Check if certificate changed
   ./scripts/extract-certificate-pins.sh
   ```

2. **Extract New Pin**:
   - Run the script above
   - Note the new pin

3. **Create Hotfix Branch**:
   ```bash
   git checkout -b hotfix/cert-pinning-update
   ```

4. **Update CertificatePinning.kt**:
   ```kotlin
   // Add new pin, keep old pin temporarily
   .add("api.openai.com", "sha256/NEW_PIN_HERE")
   .add("api.openai.com", "sha256/OLD_PIN_HERE")
   ```

5. **Test Immediately**:
   ```bash
   ./gradlew assembleRelease
   # Install on device and test
   ```

6. **Deploy Emergency Update**:
   - Increment version number
   - Create release build
   - Submit to Google Play (emergency review)

### Graceful Degradation (If Possible)

If you implemented remote config for emergency pinning disable:

1. **Disable Pinning via Server**:
   ```kotlin
   // In ApiClient.kt
   if (CertificatePinning.isEnabled() && !remoteConfig.isPinningDisabled()) {
       certificatePinner(CertificatePinning.pinner)
   }
   ```

2. **Push Server Config Update**:
   ```json
   {
     "certificate_pinning_enabled": false
   }
   ```

3. **Update Pins at Leisure**:
   - Extract new pins
   - Update app code
   - Deploy new version
   - Re-enable pinning via server config

---

## Testing Certificate Pinning

### Unit Tests

Run unit tests to verify configuration:

```bash
./scripts/test-quick.sh
```

Tests verify:
- All 6 domains are configured
- Each domain has at least 2 pins
- Pins are in correct SHA-256 format
- No duplicate pins
- Localhost is not pinned

### Integration Tests

Run integration tests on device/emulator:

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.unamentis.data.remote.CertificatePinningIntegrationTest
```

Tests verify:
- Valid certificates are accepted
- Invalid certificates are rejected
- All provider APIs are reachable
- Pinning doesn't add significant latency

### Manual Testing

1. **Debug Build** (Pinning Disabled):
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
   - Use Charles Proxy or mitmproxy
   - Verify connections work (no pinning)

2. **Release Build** (Pinning Enabled):
   ```bash
   ./gradlew assembleRelease
   adb install app/build/outputs/apk/release/app-release.apk
   ```
   - Use Charles Proxy or mitmproxy
   - Verify connections FAIL (pinning active)
   - Remove proxy, verify connections work

---

## Troubleshooting

### App Crashes with SSL Errors

**Symptom**: `javax.net.ssl.SSLPeerUnverifiedException: Certificate pinning failure!`

**Causes**:
1. Certificate rotated by provider
2. Incorrect pin in configuration
3. Man-in-the-middle attack (rare)

**Solutions**:
1. Check if certificate changed: `./scripts/extract-certificate-pins.sh`
2. Compare new pin with configured pin in `CertificatePinning.kt`
3. Update pins if different
4. Redeploy app

### Some Providers Work, Others Fail

**Symptom**: Deepgram works but OpenAI fails

**Causes**:
1. Different certificate rotation schedules
2. Incorrect pin for failing provider
3. Network routing issues

**Solutions**:
1. Extract pins for all providers: `./scripts/extract-certificate-pins.sh`
2. Update only the failing provider's pins
3. Test each provider individually

### Pinning Disabled in Release Build

**Symptom**: Proxy tools work in release build

**Causes**:
1. `BuildConfig.DEBUG` is true in release
2. `CertificatePinning.isEnabled()` returns false

**Solutions**:
1. Verify build type: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep BuildConfig`
2. Check `app/build.gradle.kts` for correct `buildTypes` configuration
3. Clean and rebuild: `./gradlew clean assembleRelease`

### Certificate Expiry Warnings

**Symptom**: Provider's certificate expires in <30 days

**Actions**:
1. Monitor certificate transparency logs for new certificate
2. Extract new pin once new certificate is issued
3. Update app with new pin as backup
4. Deploy update BEFORE old certificate expires

---

## Backup Pins

### Where to Find Backup Pins

Backup pins come from the next certificate in the provider's rotation:

1. **Provider Documentation**:
   - Some providers publish upcoming certificates
   - Check API documentation or security pages

2. **Certificate Transparency Logs**:
   - Search crt.sh for domain
   - Look for recently issued certificates
   - Extract pin from upcoming certificate

3. **Certificate Chain**:
   ```bash
   # View full certificate chain
   echo | openssl s_client -connect api.openai.com:443 -servername api.openai.com -showcerts

   # Extract intermediate certificate pin
   # (Some providers pin intermediate certs as backup)
   ```

### Example: Adding Backup Pin

```kotlin
// Primary pin (current certificate)
.add("api.openai.com", "sha256/ABC123...")

// Backup pin (next certificate or intermediate cert)
.add("api.openai.com", "sha256/XYZ789...")
```

---

## Security Considerations

### Why Certificate Pinning?

Certificate pinning prevents:
- **Man-in-the-Middle Attacks**: Attacker cannot intercept API calls
- **Compromised Certificate Authorities**: Even if CA is compromised, we only trust specific certificates
- **API Key Theft**: Protects API keys sent in HTTPS requests

### When Pinning Fails

If certificate pinning fails:
1. **Log the error** (detailed for debugging)
2. **Show user-friendly message** ("Network security validation failed")
3. **Do NOT continue** with insecure connection
4. **Do NOT expose** technical details to users

Example error handling:
```kotlin
} catch (e: SSLPeerUnverifiedException) {
    logger.error("Certificate pinning failed: ${e.message}")
    emit(STTResult.Error("Network security validation failed. Please check your connection."))
}
```

---

## References

- [OkHttp Certificate Pinning](https://square.github.io/okhttp/features/https/)
- [Android Security Best Practices](https://developer.android.com/training/articles/security-tips)
- [OWASP Certificate Pinning](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- [Certificate Transparency](https://certificate.transparency.dev/)

---

## Changelog

| Date | Action | Provider | Notes |
|------|--------|----------|-------|
| 2026-01-06 | Initial setup | All | Placeholder pins, awaiting extraction |
| TBD | Extract pins | All | Run `extract-certificate-pins.sh` |
| TBD | First deployment | All | Monitor for SSL errors |

---

**Last Updated**: 2026-01-06
**Next Review**: When extracting actual certificate pins
