# UnaMentis Android Client - Feature List

> **Document Purpose**: This document catalogs all features implemented in the UnaMentis Android client. It serves as a reference for maintaining feature parity across clients and provides sufficient detail for Claude Code to implement features in other clients.
>
> **Last Updated**: 2026-01-13
> **Maintainers**: Update this document with each feature addition, modification, or removal.

---

## Table of Contents

1. [Core Voice AI Pipeline](#1-core-voice-ai-pipeline)
2. [Voice Activity Detection (VAD)](#2-voice-activity-detection-vad)
3. [Speech-to-Text (STT)](#3-speech-to-text-stt)
4. [Text-to-Speech (TTS)](#4-text-to-speech-tts)
5. [Large Language Model (LLM) Integration](#5-large-language-model-llm-integration)
6. [Curriculum Engine](#6-curriculum-engine)
7. [Telemetry & Cost Tracking](#7-telemetry--cost-tracking)
8. [User Interface](#8-user-interface)
9. [Data Persistence](#9-data-persistence)
10. [Networking & API](#10-networking--api)
11. [Device Capabilities & Thermal Management](#11-device-capabilities--thermal-management)
12. [Accessibility](#12-accessibility)
13. [Security](#13-security)
14. [Background Processing](#14-background-processing)
15. [Testing Infrastructure](#15-testing-infrastructure)

---

## 1. Core Voice AI Pipeline

### Session Manager

**Purpose**: Orchestrates the entire voice tutoring session lifecycle with state-based turn management.

**Key Files**:
- [SessionManager.kt](../app/src/main/kotlin/com/unamentis/core/session/SessionManager.kt)

**State Machine (8 states)**:
| State | Description |
|-------|-------------|
| `IDLE` | No active session, ready to start |
| `USER_SPEAKING` | User is actively speaking, VAD detected speech |
| `PROCESSING_UTTERANCE` | Converting user speech to text via STT |
| `AI_THINKING` | LLM generating response |
| `AI_SPEAKING` | TTS playing AI response |
| `INTERRUPTED` | User barge-in during AI speech |
| `PAUSED` | Session temporarily paused |
| `ERROR` | Error state requiring recovery |

**Capabilities**:
- Turn-taking with 1.5s silence detection threshold
- 600ms barge-in confirmation window for user interruption
- Conversation history tracking with message context
- Real-time metrics collection for latency and quality
- Session persistence to local database
- Curriculum integration for guided learning

**Implementation Notes**:
- Uses Kotlin coroutines with StateFlow for reactive state updates
- All state transitions logged for debugging
- Metrics emitted on each turn completion

---

### Audio Engine

**Purpose**: Provides low-latency audio capture and playback using native Oboe library.

**Key Files**:
- [AudioEngine.kt](../app/src/main/kotlin/com/unamentis/core/audio/AudioEngine.kt)
- Native C++ in `app/src/main/cpp/`

**Capabilities**:
- 16kHz sample rate, mono, 16-bit PCM
- Real-time audio level monitoring (RMS and peak values)
- Concurrent capture and playback
- Configurable buffer sizes for latency tuning
- JNI bridge to Oboe C++ library

**Configuration**:
```kotlin
data class AudioConfig(
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val framesPerBuffer: Int = 512
)
```

---

## 2. Voice Activity Detection (VAD)

**Purpose**: Detect when user starts/stops speaking to enable turn-taking.

**Key Files**:
- [SileroVADService.kt](../app/src/main/kotlin/com/unamentis/services/vad/SileroVADService.kt)
- [SileroOnnxVADService.kt](../app/src/main/kotlin/com/unamentis/services/vad/SileroOnnxVADService.kt)
- [SimpleVADService.kt](../app/src/main/kotlin/com/unamentis/services/vad/SimpleVADService.kt)

### Implementations

| Implementation | Engine | Use Case |
|----------------|--------|----------|
| Silero TFLite | TensorFlow Lite | Primary, NNAPI accelerated |
| Silero ONNX | ONNX Runtime | Alternative, 2-thread inference |
| Simple RMS | Energy-based | Fallback for minimum devices |

**Interface**:
```kotlin
interface VADService {
    suspend fun initialize()
    fun processAudio(audioData: ShortArray): Float  // Returns speech probability 0.0-1.0
    fun release()
}
```

**Configuration**:
- Frame size: 512 samples (32ms at 16kHz)
- Speech threshold: 0.5 (configurable)
- Silence duration: 1.5s before end-of-speech

---

## 3. Speech-to-Text (STT)

**Purpose**: Convert user speech to text for LLM processing.

**Key Files**:
- [DeepgramSTTService.kt](../app/src/main/kotlin/com/unamentis/services/stt/DeepgramSTTService.kt)
- [AndroidSTTService.kt](../app/src/main/kotlin/com/unamentis/services/stt/AndroidSTTService.kt)

### Providers

| Provider | Type | Model | Cost |
|----------|------|-------|------|
| Deepgram | Cloud | nova-3 | Paid API |
| Android Native | On-device | SpeechRecognizer | Free |

**Interface**:
```kotlin
interface STTService {
    fun transcribe(audioStream: Flow<ByteArray>): Flow<STTResult>
}

data class STTResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float,
    val latencyMs: Long
)
```

**Deepgram Features**:
- WebSocket streaming for real-time transcription
- Partial results for early processing
- Latency tracking per utterance

**Android Native Features**:
- No API costs
- Works offline
- Uses system SpeechRecognizer

---

## 4. Text-to-Speech (TTS)

**Purpose**: Convert AI responses to speech for playback.

**Key Files**:
- [ElevenLabsTTSService.kt](../app/src/main/kotlin/com/unamentis/services/tts/ElevenLabsTTSService.kt)
- [AndroidTTSService.kt](../app/src/main/kotlin/com/unamentis/services/tts/AndroidTTSService.kt)

### Providers

| Provider | Type | Quality | Cost |
|----------|------|---------|------|
| ElevenLabs | Cloud | High (natural) | Paid API |
| Android Native | On-device | Standard | Free |

**Interface**:
```kotlin
interface TTSService {
    fun synthesize(text: String, voiceId: String?): Flow<TTSAudioChunk>
}

data class TTSAudioChunk(
    val audioData: ByteArray,
    val isFirst: Boolean,
    val isLast: Boolean
)
```

**ElevenLabs Features**:
- WebSocket streaming synthesis
- Multiple voice options
- TTFB (Time-To-First-Byte) tracking

**Android Native Features**:
- No API costs
- Works offline
- Locale support (defaults to US English)

---

## 5. Large Language Model (LLM) Integration

**Purpose**: Generate AI tutor responses using various LLM providers.

**Key Files**:
- [OpenAILLMService.kt](../app/src/main/kotlin/com/unamentis/services/llm/OpenAILLMService.kt)
- [AnthropicLLMService.kt](../app/src/main/kotlin/com/unamentis/services/llm/AnthropicLLMService.kt)
- [PatchPanelService.kt](../app/src/main/kotlin/com/unamentis/core/routing/PatchPanelService.kt)

### Providers

| Provider | Models | Streaming |
|----------|--------|-----------|
| OpenAI | GPT-4o, GPT-4o-mini | SSE |
| Anthropic | Claude 3.5 Sonnet | SSE |

**Interface**:
```kotlin
interface LLMService {
    fun generateResponse(
        messages: List<LLMMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): Flow<LLMToken>
}

data class LLMToken(
    val text: String,
    val isFirst: Boolean,
    val isLast: Boolean
)
```

### Patch Panel (Intelligent Routing)

**Purpose**: Select optimal LLM provider based on context.

**Routing Factors**:
| Factor | Options |
|--------|---------|
| Task Type | TUTORING, PLANNING, SUMMARIZATION, ASSESSMENT, SIMPLE_RESPONSE |
| Device Tier | FLAGSHIP, STANDARD, MINIMUM |
| Network Quality | EXCELLENT, GOOD, POOR, OFFLINE |
| Cost Preference | QUALITY, BALANCED, COST |

**Behavior**:
- Routes complex tasks to larger models (GPT-4o, Claude)
- Routes simple tasks to faster/cheaper models (GPT-4o-mini)
- Falls back to alternative providers on failure
- Considers device thermal state

---

## 6. Curriculum Engine

**Purpose**: Manage structured learning content and track progress.

**Key Files**:
- [CurriculumEngine.kt](../app/src/main/kotlin/com/unamentis/core/curriculum/CurriculumEngine.kt)
- [Curriculum.kt](../app/src/main/kotlin/com/unamentis/data/model/Curriculum.kt)

### Data Model

```kotlin
data class Curriculum(
    val id: String,
    val title: String,
    val description: String,
    val version: String,
    val topics: List<Topic>
)

data class Topic(
    val id: String,
    val title: String,
    val learningObjectives: List<String>,
    val segments: List<TranscriptSegment>,
    val documents: List<Document>,
    val visualAssets: List<VisualAsset>
)

data class TranscriptSegment(
    val id: String,
    val text: String,
    val stoppingPoint: StoppingPoint?  // CHECKPOINT, QUIZ, REVIEW
)
```

### Progress Tracking

```kotlin
data class TopicProgress(
    val topicId: String,
    val masteryLevel: Float,      // 0.0 - 1.0
    val timeSpentSeconds: Long,
    val completedSegments: List<String>,
    val lastAccessed: Instant
)
```

**Capabilities**:
- UMCF (Una Mentis Curriculum Format) parsing
- Topic-by-topic navigation
- Segment progression with stopping points
- Mastery tracking per topic
- Visual asset overlay during sessions
- Automatic advancement on completion

---

## 7. Telemetry & Cost Tracking

**Purpose**: Measure performance metrics and track API costs.

**Key Files**:
- [TelemetryEngine.kt](../app/src/main/kotlin/com/unamentis/core/telemetry/TelemetryEngine.kt)
- [Telemetry.kt](../app/src/main/kotlin/com/unamentis/data/model/Telemetry.kt)

### Metrics Collected

| Metric | Target | Description |
|--------|--------|-------------|
| E2E Latency | <500ms median | User speech end to AI speech start |
| STT Latency | <200ms | Speech-to-text processing time |
| LLM TTFT | <300ms | Time to first token from LLM |
| TTS TTFB | <150ms | Time to first audio byte from TTS |

### Cost Tracking

```kotlin
data class CostRecord(
    val provider: String,      // "openai", "anthropic", "deepgram", "elevenlabs"
    val amount: Double,        // USD
    val metadata: Map<String, String>  // tokens, characters, etc.
)
```

**Capabilities**:
- Real-time latency aggregation (median, P99)
- Per-turn and per-session cost summaries
- Export to server for analytics
- Provider-specific cost calculation

---

## 8. User Interface

**Purpose**: Provide intuitive, accessible interface for voice tutoring.

**Key Files**:
- [SessionScreen.kt](../app/src/main/kotlin/com/unamentis/ui/session/SessionScreen.kt)
- [CurriculumScreen.kt](../app/src/main/kotlin/com/unamentis/ui/curriculum/CurriculumScreen.kt)
- [TodoScreen.kt](../app/src/main/kotlin/com/unamentis/ui/todo/TodoScreen.kt)
- [HistoryScreen.kt](../app/src/main/kotlin/com/unamentis/ui/history/HistoryScreen.kt)
- [AnalyticsScreen.kt](../app/src/main/kotlin/com/unamentis/ui/analytics/AnalyticsScreen.kt)
- [SettingsScreen.kt](../app/src/main/kotlin/com/unamentis/ui/settings/SettingsScreen.kt)

### Screen Overview

| Screen | Purpose |
|--------|---------|
| **Session** | Active voice tutoring with transcript display |
| **Curriculum** | Browse/search curricula and topics |
| **Todo** | Task management (manual + curriculum-generated) |
| **History** | Past session review with transcripts |
| **Analytics** | Performance metrics and cost breakdown |
| **Settings** | Provider config, audio settings, preferences |

### Session Screen Features

- Real-time transcript with reverse scroll (newest at bottom)
- Session state indicator (turn count, status)
- Audio level visualization
- Slide-to-stop gesture for session termination
- Mute/pause toggle
- Metrics display (latency, cost)
- Microphone permission handling

### Custom Components

| Component | Purpose |
|-----------|---------|
| `SlideToStopButton` | Gesture-based session termination |
| `TranscriptBubble` | User/AI message display |
| `SessionControlBar` | Start/pause/resume/stop controls |
| `VisualAssetView` | Curriculum image overlay |
| `AudioLevelVisualization` | Real-time audio feedback |

### Theming (Material Design 3)

```kotlin
// Color scheme
Primary: #1976D2 (Blue)
Secondary: #388E3C (Green)
Tertiary: #F57C00 (Orange)
Error: #D32F2F (Red)
```

---

## 9. Data Persistence

**Purpose**: Store sessions, curricula, progress, and settings locally.

**Key Files**:
- [AppDatabase.kt](../app/src/main/kotlin/com/unamentis/data/local/AppDatabase.kt)
- DAOs in `app/src/main/kotlin/com/unamentis/data/local/dao/`

### Database Entities

| Entity | Purpose |
|--------|---------|
| `SessionEntity` | Session records with full transcripts |
| `CurriculumEntity` | Downloaded curriculum content |
| `TopicProgressEntity` | Learning progress per topic |
| `TodoEntity` | Task list items |
| `SettingsEntity` | User preferences |

### Repository Pattern

```kotlin
// Example repository interface
interface SessionRepository {
    suspend fun saveSession(session: Session)
    suspend fun getSession(id: String): Session?
    fun getAllSessions(): Flow<List<Session>>
    suspend fun deleteSession(id: String)
}
```

---

## 10. Networking & API

**Purpose**: Communicate with management console and external services.

**Key Files**:
- [ApiClient.kt](../app/src/main/kotlin/com/unamentis/data/remote/ApiClient.kt)
- [RemoteLogger.kt](../app/src/main/kotlin/com/unamentis/core/config/RemoteLogger.kt)

### REST Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/curricula` | GET | List all curricula |
| `/api/curricula/{id}` | GET | Get curriculum details |
| `/api/curricula/{id}/full-with-assets` | GET | Download with visual assets |
| `/api/curricula/{id}/topics/{topicId}/transcript` | GET | Get topic transcript |
| `/api/metrics` | POST | Upload session metrics |

### Client Headers

```
X-Client-ID: <device-uuid>
X-Client-Name: <device-model>
X-Client-Platform: Android
X-Client-Version: <app-version>
```

### Server Configuration

| Server | Default Port | Emulator Access |
|--------|--------------|-----------------|
| Management Console | 8766 | `10.0.2.2:8766` |
| Log Server | 8765 | `10.0.2.2:8765` |

---

## 11. Device Capabilities & Thermal Management

**Purpose**: Adapt behavior based on device capabilities and thermal state.

**Key Files**:
- [DeviceCapabilityDetector.kt](../app/src/main/kotlin/com/unamentis/core/config/DeviceCapabilityDetector.kt)
- [ThermalMonitor.kt](../app/src/main/kotlin/com/unamentis/core/config/ThermalMonitor.kt)

### Device Tiers

| Tier | RAM | Cores | Behavior |
|------|-----|-------|----------|
| FLAGSHIP | 8GB+ | 8+ | Full on-device AI |
| STANDARD | 4-8GB | 4+ | Limited on-device |
| MINIMUM | <4GB | <4 | Cloud-primary |

### Thermal States & Responses

| State | Response |
|-------|----------|
| NONE/LIGHT | Normal operation |
| MODERATE | Consider on-device TTS |
| SEVERE | Switch to on-device STT + TTS |
| CRITICAL | Pause session |
| EMERGENCY | Force stop |

### Capability Detection

- RAM (total and available)
- CPU core count
- NNAPI support
- Vulkan support
- Android API level

---

## 12. Accessibility

**Purpose**: Ensure app is usable by users with disabilities.

**Key Files**:
- Throughout UI components
- [AccessibilityChecker.kt](../app/src/main/kotlin/com/unamentis/ui/util/AccessibilityChecker.kt)

### Features

| Feature | Implementation |
|---------|----------------|
| Screen Reader | TalkBack support with content descriptions |
| Font Scaling | sp units, supports 1.5x-2x scaling |
| Touch Targets | 48dp minimum |
| Motion Reduction | Respects system preference |
| Color Contrast | WCAG AA (4.5:1 normal, 3.0:1 large) |

### Semantic Properties

```kotlin
Modifier.semantics {
    contentDescription = "Start tutoring session"
    role = Role.Button
}
```

---

## 13. Security

**Purpose**: Protect user data and API credentials.

**Key Files**:
- [APIKeyManager.kt](../app/src/main/kotlin/com/unamentis/core/config/APIKeyManager.kt)
- ProGuard rules in `app/proguard-rules.pro`

### Security Measures

| Measure | Implementation |
|---------|----------------|
| API Key Storage | EncryptedSharedPreferences |
| Network | HTTPS for all external APIs |
| Code Obfuscation | ProGuard/R8 in release builds |
| Debug Logging | Stripped in production |

---

## 14. Background Processing

**Purpose**: Maintain session audio when app is backgrounded.

**Key Files**:
- [SessionForegroundService.kt](../app/src/main/kotlin/com/unamentis/services/SessionForegroundService.kt)

### Foreground Service

- `START_STICKY` for persistence
- Notification with session controls (pause/resume/stop)
- Session metadata display
- Auto-cleanup on session end
- Audio focus management

---

## 15. Testing Infrastructure

**Purpose**: Ensure code quality and prevent regressions.

### Test Categories

| Category | Count | Framework |
|----------|-------|-----------|
| Unit Tests | 72+ | JUnit 5 |
| UI Tests | 142+ | Compose Test |
| Navigation Tests | 18+ | Compose Navigation |
| Performance Benchmarks | 14+ | Microbenchmark |
| Memory Tests | 6+ | LeakCanary/Custom |

### Testing Philosophy

- **Real over Mock**: Only mock paid external APIs
- **In-memory Room**: Use real database with in-memory backing
- **Real services**: Use actual service implementations
- **Coverage**: All screens, all state transitions

---

## Feature Summary

| Category | Feature Count |
|----------|---------------|
| UI Screens | 6 |
| LLM Providers | 2 (OpenAI, Anthropic) |
| STT Providers | 2 (Deepgram, Android) |
| TTS Providers | 2 (ElevenLabs, Android) |
| VAD Implementations | 3 |
| Database Entities | 5 |
| REST Endpoints | 5 |
| Test Suites | 5 |

---

## Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-01-13 | Initial feature list created | Claude Code |

---

## Notes for Porting to Other Clients

When implementing these features in another client:

1. **Architecture**: Follow MVVM with repository pattern
2. **State Management**: Use reactive streams (Flow/StateFlow equivalent)
3. **Provider Abstraction**: All external services behind interfaces
4. **Telemetry**: Emit same metric types for cross-client analytics
5. **API Compatibility**: Use same REST endpoints and headers
6. **Testing**: Maintain "real over mock" philosophy
