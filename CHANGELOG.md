# Changelog

All notable changes to the UnaMentis Android project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Comprehensive README documentation with feature overview
- CONTRIBUTING.md with detailed contribution guidelines
- ARCHITECTURE.md with in-depth technical documentation
- QUICK_START.md for rapid developer onboarding
- CHANGELOG.md for tracking project changes

---

## [0.1.0] - 2026-01-19

### Added

#### Foundation (Phase 1)
- Project structure with Kotlin 2.0 and Jetpack Compose
- Gradle build configuration with version catalog
- Hilt dependency injection setup
- Room database with entities and DAOs:
  - SessionEntity, TranscriptEntryEntity
  - CurriculumEntity, TopicEntity
  - TopicProgressEntity
- Core data models:
  - Curriculum, Topic, TranscriptSegment
  - Session, TranscriptEntry
  - Provider configurations
- API client with OkHttp and Retrofit
- Certificate pinning for secure connections
- Build automation scripts:
  - `build.sh`, `test-quick.sh`, `test-all.sh`
  - `lint.sh`, `format.sh`, `health-check.sh`
  - `launch-emulator.sh`, `install-emulator.sh`
- ktlint and detekt code quality configuration

#### Audio Pipeline (Phase 2)
- Native Oboe audio engine with JNI bridge
- C++ audio processing with low-latency I/O
- Silero VAD integration via ONNX Runtime
- Audio utilities (RMS, peak detection)
- AudioEngine Kotlin wrapper

#### Provider Integration (Phase 3)
- STT provider abstraction and implementations:
  - DeepgramSTTService (WebSocket)
  - AssemblyAISTTService (WebSocket)
  - AndroidSTTService (on-device)
- TTS provider abstraction and implementations:
  - ElevenLabsTTSService (WebSocket)
  - AndroidTTSService (on-device)
- LLM provider abstraction and implementations:
  - OpenAILLMService (streaming SSE)
  - AnthropicLLMService (streaming SSE)
  - OnDeviceLLMService (llama.cpp via JNI)
- PatchPanel service for intelligent routing
- ModelDownloadManager for GGUF model downloads
- Provider configuration management

#### Session Management (Phase 4)
- SessionManager with state machine
- Turn-taking logic with 1.5s silence detection
- Barge-in handling with 600ms confirmation
- Conversation history management
- Session persistence and recovery
- CurriculumEngine for content management
- Progress tracking with TopicProgress

#### UI Implementation (Phase 5)
- Material Design 3 theme with light/dark modes
- Navigation with 6 primary screens:
  - SessionScreen (main voice interface)
  - CurriculumScreen (content browser)
  - HistoryScreen (session history)
  - AnalyticsScreen (metrics dashboard)
  - SettingsScreen (configuration)
  - TodoScreen (task management placeholder)
- Custom Compose components:
  - TranscriptBubble
  - SessionControlBar
  - SlideToStopButton
- ViewModels with StateFlow
- Adaptive layouts for different screen sizes

#### Testing & Polish (Phase 6)
- Unit test infrastructure with Robolectric
- Test utilities and helpers
- Mock implementations for providers
- Integration with MCP servers:
  - mobile-mcp for emulator automation
  - gradle-mcp-server for build automation
- Log server for remote debugging
- Comprehensive documentation

### Technical Details

#### Dependencies
- Kotlin 2.0.21
- Jetpack Compose BOM 2024.12.01
- Hilt 2.54
- Room 2.6.1
- OkHttp 4.12.0
- TensorFlow Lite 2.16.1
- ONNX Runtime 1.17.0
- Oboe 1.8.0

#### Build Requirements
- Android Studio Ladybug 2024.2.1+
- JDK 17+
- Android SDK API 34
- NDK 26.x
- CMake 3.22.1+

#### Server Synchronization (Phase 7)
- Server-based session synchronization with local-first architecture
- Real-time WebSocket communication for session events
- Authentication token management with secure storage
- Session data sync with conflict resolution
- Transcript synchronization between devices
- Server configuration management

### Known Issues
- LiveKit provider integration pending

---

## Version History

| Version | Date | Status |
|---------|------|--------|
| 0.1.0 | 2026-01-19 | Released |

---

## Release Process

1. Update version in `app/build.gradle.kts`
2. Update this CHANGELOG with release date
3. Create git tag: `git tag -a v0.1.0 -m "Release v0.1.0"`
4. Push tag: `git push origin v0.1.0`
5. Build release APK: `./gradlew assembleRelease`

---

## Links

- [README](README.md)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development Environment](docs/DEV_ENVIRONMENT.md)
