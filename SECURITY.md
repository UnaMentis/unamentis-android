# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 0.1.x   | :white_check_mark: |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

### How to Report

1. **Do NOT** create a public GitHub issue for security vulnerabilities
2. Email security concerns to: [security@unamentis.com](mailto:security@unamentis.com)
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested fixes (optional)

### What to Expect

- **Acknowledgment**: Within 48 hours of your report
- **Initial Assessment**: Within 5 business days
- **Resolution Timeline**: Depends on severity
  - Critical: 24-72 hours
  - High: 1-2 weeks
  - Medium: 2-4 weeks
  - Low: Next release cycle

### Disclosure Policy

- We follow responsible disclosure practices
- We will coordinate with you on disclosure timing
- Credit will be given to reporters (unless anonymity is requested)

---

## Security Measures

### Data Protection

#### API Keys and Credentials
- API keys stored in Android EncryptedSharedPreferences
- Keys never logged or transmitted except to their intended service
- No credentials committed to version control

#### Network Security
- HTTPS enforced for all API communications
- Certificate pinning implemented for critical endpoints
- WebSocket connections use secure protocols (wss://)

#### Local Storage
- Room database for structured data
- Sensitive data encrypted at rest
- Conversation transcripts stored locally only

### Audio Privacy

- Microphone access requires explicit user permission
- Audio processed locally by VAD before any transmission
- No audio stored permanently without user consent
- Foreground service notification when recording

### Third-Party Services

| Service | Data Shared | Purpose |
|---------|-------------|---------|
| Deepgram | Audio stream | Speech-to-text |
| ElevenLabs | Text content | Text-to-speech |
| OpenAI | Conversation text | AI responses |
| Anthropic | Conversation text | AI responses |

Users control which services receive their data through provider configuration.

---

## Security Best Practices for Contributors

### Code Review Requirements

All security-relevant changes require:
- Review by at least one maintainer
- No secrets in code or commits
- Input validation for all user data
- Error messages that don't leak sensitive info

### Dependency Management

- Regular dependency updates via Dependabot
- Security audit before adding new dependencies
- Prefer well-maintained, widely-used libraries

### Secure Coding Guidelines

```kotlin
// DO: Validate input
fun processApiKey(key: String): Boolean {
    require(key.isNotBlank()) { "API key cannot be blank" }
    require(key.length >= 32) { "API key too short" }
    return true
}

// DON'T: Log sensitive data
fun authenticate(apiKey: String) {
    // BAD: Log.d("Auth", "Using key: $apiKey")
    // GOOD: Log.d("Auth", "Authenticating...")
}

// DO: Use parameterized queries
@Query("SELECT * FROM sessions WHERE id = :sessionId")
fun getSession(sessionId: Long): Session

// DON'T: String concatenation in queries
// BAD: "SELECT * FROM sessions WHERE id = $sessionId"
```

---

## Known Security Considerations

### Current Limitations

1. **Debug Builds**: Include additional logging that may expose sensitive info
2. **Emulator**: Uses special network addresses that bypass some security
3. **Log Server**: Debug tool not intended for production

### Production Recommendations

- Use release builds only
- Enable ProGuard/R8 obfuscation
- Configure network security config
- Remove debug logging
- Disable log server connectivity

---

## Security Audit History

| Date | Scope | Findings | Status |
|------|-------|----------|--------|
| 2026-01 | Initial review | Certificate pinning implemented | Complete |

---

## Contact

For security inquiries: [security@unamentis.com](mailto:security@unamentis.com)

For general questions: [GitHub Issues](https://github.com/owner/unamentis-android/issues)
