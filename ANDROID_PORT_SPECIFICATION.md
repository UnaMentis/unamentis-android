# UnaMentis Android Port Specification

**Version:** 1.0
**Date:** January 2026
**Status:** Planning
**Target Platform:** Android 14+ (API 34+)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Why Feature Parity Matters](#2-why-feature-parity-matters)
3. [iOS App Architecture Overview](#3-ios-app-architecture-overview)
4. [Technology Mapping: iOS to Android](#4-technology-mapping-ios-to-android)
5. [Core Features Specification](#5-core-features-specification)
6. [UI/UX Specification](#6-uiux-specification)
7. [Server Communication](#7-server-communication)
8. [Performance Requirements](#8-performance-requirements)
9. [Native Android Optimizations](#9-native-android-optimizations)
10. [Device Capability Tiers](#10-device-capability-tiers)
11. [Accessibility Requirements](#11-accessibility-requirements)
12. [Implementation Roadmap](#12-implementation-roadmap)
13. [Testing Strategy](#13-testing-strategy)
14. [Development Environment Setup](#14-development-environment-setup)
15. [MCP Server Integration](#15-mcp-server-integration)
16. [Build Automation Scripts](#16-build-automation-scripts)
17. [Round-Trip Development Workflow](#17-round-trip-development-workflow)
18. [Definition of Done](#18-definition-of-done)
19. [Server API Reference](#19-server-api-reference)

---

## 1. Executive Summary

UnaMentis is a voice AI tutoring application that enables 60-90+ minute voice-based learning sessions with sub-500ms latency. This document specifies the requirements for porting the iOS application to Android while:

1. **Maintaining strict feature parity** with the iOS app
2. **Leveraging native Android capabilities** for optimal performance
3. **Preserving the same user experience** across platforms
4. **Meeting identical performance targets** for latency, stability, and resource usage

### Core Value Proposition

UnaMentis differentiates through:
- **Voice-first interaction** with natural conversation flow
- **Sub-500ms response latency** for fluid dialogue
- **90-minute session stability** without crashes or degradation
- **Multi-provider flexibility** for STT, TTS, and LLM services
- **Curriculum-driven learning** with structured content and progress tracking
- **Cost transparency** with real-time API cost tracking

---

## 2. Why Feature Parity Matters

### 2.1 Business Rationale

**Cross-Platform Consistency:**
- Users may switch between iOS and Android devices
- Learning progress must sync seamlessly across platforms
- Marketing and documentation should describe one product, not two
- Support teams need consistent behavior to troubleshoot

**Brand Identity:**
- UnaMentis should feel identical regardless of platform
- UI patterns, animations, and interactions must match
- Voice tutoring quality cannot vary by device OS

**Development Efficiency:**
- Shared server infrastructure and APIs
- Common curriculum format (UMCF)
- Unified telemetry and analytics pipeline
- Single source of truth for business logic

### 2.2 Technical Rationale

**Server Compatibility:**
- Both apps communicate with the same Management Console (port 8766)
- Identical REST API contracts and WebSocket protocols
- Shared curriculum database and content format
- Common metrics upload format for analytics

**Provider Integration:**
- Same STT providers (Deepgram, AssemblyAI, Groq, self-hosted)
- Same TTS providers (ElevenLabs, Deepgram, self-hosted)
- Same LLM providers (OpenAI, Anthropic, self-hosted)
- Identical API contracts and authentication patterns

### 2.3 What "Feature Parity" Means

| Aspect | Requirement |
|--------|-------------|
| **Functionality** | Every iOS feature must exist on Android |
| **UI/UX** | Same screens, flows, and interaction patterns |
| **Performance** | Same latency, stability, and resource targets |
| **Accessibility** | Same accessibility support (TalkBack vs VoiceOver) |
| **Offline** | Same offline capabilities with on-device models |

### 2.4 Platform-Specific Excellence

Feature parity does NOT mean ignoring platform strengths. Android should leverage:

- **Android Speech Services** for on-device STT (equivalent to Apple Speech)
- **Android TTS Engine** for on-device synthesis (equivalent to AVSpeechSynthesizer)
- **TensorFlow Lite / NNAPI** for on-device ML (equivalent to Core ML)
- **Material Design 3** for native look-and-feel (equivalent to SwiftUI styling)
- **Jetpack Compose** for modern declarative UI (equivalent to SwiftUI)
- **Kotlin Coroutines** for async operations (equivalent to Swift async/await)
- **Room Database** for local persistence (equivalent to Core Data)

---

## 3. iOS App Architecture Overview

Understanding the iOS architecture is essential for creating an equivalent Android implementation.

### 3.1 Architecture Pattern

**iOS:** MVVM + Actor-based concurrency (Swift 6.0)

```
┌─────────────────────────────────────────────────────────────────┐
│                         iOS Architecture                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   UI Layer (SwiftUI)                                            │
│   ├── Views (@MainActor)                                        │
│   ├── ViewModels (@MainActor, @Observable)                      │
│   └── Navigation (NavigationStack, TabView)                     │
│                                                                  │
│   Service Layer (Actors)                                        │
│   ├── SessionManager (orchestrates voice sessions)              │
│   ├── AudioEngine (audio I/O, VAD integration)                  │
│   ├── STT/TTS/LLM Services (provider implementations)           │
│   └── CurriculumEngine (content management)                     │
│                                                                  │
│   Core Layer                                                    │
│   ├── PatchPanelService (intelligent LLM routing)               │
│   ├── TelemetryEngine (metrics, costs, latency)                 │
│   ├── PersistenceController (Core Data)                         │
│   └── APIKeyManager (Keychain storage)                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Directory Structure (iOS)

```
UnaMentis/
├── Core/                           # Business logic
│   ├── Audio/                      # AudioEngine, VAD integration
│   ├── Session/                    # SessionManager
│   ├── Curriculum/                 # CurriculumEngine, progress tracking
│   ├── Routing/                    # PatchPanel LLM routing
│   ├── Persistence/                # Core Data stack
│   ├── Telemetry/                  # Metrics and cost tracking
│   ├── Config/                     # API keys, server config
│   └── Logging/                    # Remote log handler
├── Services/                       # External integrations
│   ├── STT/                        # Speech-to-text providers
│   ├── TTS/                        # Text-to-speech providers
│   ├── LLM/                        # Language model providers
│   ├── VAD/                        # Voice activity detection
│   └── Embeddings/                 # Text embeddings
├── UI/                             # SwiftUI views
│   ├── Session/                    # Main voice session UI
│   ├── Curriculum/                 # Content browsing
│   ├── TodoList/                   # Task management
│   ├── History/                    # Session history
│   ├── Analytics/                  # Telemetry dashboard
│   ├── Settings/                   # Configuration
│   └── Onboarding/                 # First-run experience
└── Intents/                        # Siri/Shortcuts integration
```

### 3.3 Key Components

| Component | iOS Implementation | Lines of Code |
|-----------|-------------------|---------------|
| SessionManager | Actor with state machine | ~1,600 |
| AudioEngine | AVAudioEngine wrapper | ~630 |
| SessionView | Main voice UI | ~3,000 |
| SettingsView | Configuration UI | ~2,200 |
| CurriculumView | Content browser | ~1,300 |
| TelemetryEngine | Metrics collection | ~800 |

---

## 4. Technology Mapping: iOS to Android

### 4.1 Core Technologies

| iOS Technology | Android Equivalent | Notes |
|----------------|-------------------|-------|
| Swift 6.0 | Kotlin 2.0+ | Similar modern language features |
| SwiftUI | Jetpack Compose | Declarative UI frameworks |
| Combine | Kotlin Flow | Reactive streams |
| async/await | Kotlin Coroutines | Structured concurrency |
| Actors | Mutex/synchronized or custom | Thread-safe state isolation |
| Core Data | Room Database | Local persistence |
| Keychain | EncryptedSharedPreferences | Secure credential storage |
| AVAudioEngine | AudioRecord/AudioTrack + Oboe | Audio I/O |
| Core ML | TensorFlow Lite + NNAPI | On-device ML |
| URLSession | OkHttp/Retrofit | Networking |
| WebSocket (URLSession) | OkHttp WebSocket | Real-time communication |

### 4.2 UI Components

| iOS Component | Android Equivalent |
|---------------|-------------------|
| NavigationStack | NavHost (Navigation Compose) |
| TabView | BottomNavigation + NavHost |
| NavigationSplitView | ListDetailPaneScaffold |
| List | LazyColumn |
| Sheet/FullScreenCover | ModalBottomSheet / Dialog |
| @State | remember { mutableStateOf() } |
| @StateObject | viewModel() |
| @EnvironmentObject | CompositionLocal / Hilt |
| @Published | StateFlow / MutableState |

### 4.3 Service Providers

| iOS Provider | Android Equivalent |
|--------------|-------------------|
| Apple Speech (SFSpeechRecognizer) | Android SpeechRecognizer |
| AVSpeechSynthesizer | Android TextToSpeech |
| Silero VAD (Core ML) | Silero VAD (TFLite) |
| llama.cpp (Swift) | llama.cpp (JNI/NDK) |
| LiveKit SDK | LiveKit Android SDK |

### 4.4 Platform Services

| iOS Feature | Android Equivalent |
|-------------|-------------------|
| Siri Shortcuts | Google Assistant Actions / Shortcuts |
| Spotlight Search | App Search (AppSearchManager) |
| Background Audio | Foreground Service with notification |
| Haptic Feedback | Vibrator / HapticFeedbackConstants |
| Dynamic Type | Font scaling (sp units) |
| VoiceOver | TalkBack |

---

## 5. Core Features Specification

### 5.1 Voice AI Tutoring Pipeline

The core tutoring loop must be identical:

```
User speaks → VAD detects speech → STT transcribes →
LLM generates response → TTS synthesizes → Audio plays
```

**Latency Budget (same as iOS):**
- STT: <300ms median
- LLM First Token: <200ms median
- TTS TTFB: <200ms median
- **E2E Turn: <500ms median, <1000ms P99**

### 5.2 Speech-to-Text (STT) Providers

Must support all iOS providers:

| Provider | Type | API | Priority |
|----------|------|-----|----------|
| Deepgram Nova-3 | Cloud (WebSocket) | wss://api.deepgram.com | Primary |
| AssemblyAI | Cloud (WebSocket) | wss://api.assemblyai.com | Secondary |
| Groq Whisper | Cloud (REST) | api.groq.com | Free tier |
| Android SpeechRecognizer | On-device | Android SDK | Offline |
| GLM-ASR | Self-hosted | HTTP/WebSocket | Custom server |

**Android-Specific Implementation:**
```kotlin
// Use Android's native SpeechRecognizer for on-device STT
class AndroidSTTService : STTService {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    override fun startStreaming(): Flow<STTResult> = callbackFlow {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(RESULTS_RECOGNITION)?.firstOrNull()
                trySend(STTResult(text ?: "", isFinal = true))
            }
            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(RESULTS_RECOGNITION)?.firstOrNull()
                trySend(STTResult(text ?: "", isFinal = false))
            }
            // ... other callbacks
        })
        speechRecognizer.startListening(intent)
        awaitClose { speechRecognizer.stopListening() }
    }
}
```

### 5.3 Text-to-Speech (TTS) Providers

Must support all iOS providers:

| Provider | Type | API | Priority |
|----------|------|-----|----------|
| ElevenLabs | Cloud (WebSocket) | wss://api.elevenlabs.io | Primary |
| Deepgram Aura-2 | Cloud (WebSocket) | wss://api.deepgram.com | Secondary |
| Android TTS | On-device | Android SDK | Offline |
| Piper/VibeVoice | Self-hosted | HTTP | Custom server |

**Android-Specific Implementation:**
```kotlin
// Use Android's native TextToSpeech for on-device TTS
class AndroidTTSService : TTSService {
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun synthesize(text: String): Flow<TTSAudioChunk> = callbackFlow {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                trySend(TTSAudioChunk(isFirst = true))
            }
            override fun onDone(utteranceId: String) {
                close()
            }
            // ... other callbacks
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        awaitClose { tts.stop() }
    }
}
```

### 5.4 Language Model (LLM) Providers

Must support all iOS providers:

| Provider | Type | API | Models |
|----------|------|-----|--------|
| OpenAI | Cloud (SSE) | api.openai.com | GPT-4o, GPT-4o-mini |
| Anthropic | Cloud (SSE) | api.anthropic.com | Claude 3.5 Sonnet/Haiku |
| Ollama | Self-hosted | localhost:11434 | Qwen, Llama, Mistral |
| llama.cpp | On-device | JNI | Ministral-3B |

**On-Device LLM via llama.cpp:**
```kotlin
// JNI wrapper for llama.cpp
class OnDeviceLLMService : LLMService {
    private external fun loadModel(modelPath: String): Long
    private external fun generateToken(contextPtr: Long, prompt: String): String
    private external fun freeModel(contextPtr: Long)

    companion object {
        init {
            System.loadLibrary("llama")
        }
    }

    override fun streamCompletion(messages: List<LLMMessage>): Flow<LLMToken> = flow {
        val prompt = formatMessages(messages)
        var token: String
        do {
            token = generateToken(contextPtr, prompt)
            emit(LLMToken(content = token, isDone = token.isEmpty()))
        } while (token.isNotEmpty())
    }
}
```

### 5.5 Voice Activity Detection (VAD)

**Must use Silero VAD for consistency:**

```kotlin
// Silero VAD via TensorFlow Lite
class SileroVADService : VADService {
    private val interpreter: Interpreter
    private val inputBuffer = FloatArray(512)  // 32ms at 16kHz

    init {
        val model = loadModelFile("silero_vad.tflite")
        interpreter = Interpreter(model)
    }

    override fun processAudio(samples: FloatArray): VADResult {
        interpreter.run(samples, outputBuffer)
        val probability = outputBuffer[0]
        return VADResult(
            isSpeech = probability > threshold,
            confidence = probability
        )
    }
}
```

### 5.6 Session Management

The SessionManager must implement the same state machine:

```kotlin
enum class SessionState {
    IDLE,
    USER_SPEAKING,
    PROCESSING_UTTERANCE,
    AI_THINKING,
    AI_SPEAKING,
    INTERRUPTED,
    PAUSED,
    ERROR
}

class SessionManager(
    private val audioEngine: AudioEngine,
    private val sttService: STTService,
    private val llmService: LLMService,
    private val ttsService: TTSService,
    private val telemetry: TelemetryEngine
) {
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    // Conversation history
    private val conversationHistory = mutableListOf<LLMMessage>()

    // Turn-taking with 1.5s silence threshold
    private val silenceThresholdMs = 1500L

    suspend fun startSession(topic: Topic? = null) {
        // Initialize services, start audio capture, begin VAD monitoring
    }

    suspend fun stopSession() {
        // Clean up all async tasks, persist session to database
    }

    // Barge-in handling with 600ms confirmation window
    private suspend fun handleInterruption() {
        // Stop TTS playback, transition to USER_SPEAKING
    }
}
```

### 5.7 Curriculum Engine

Must support the UMCF (Una Mentis Curriculum Format):

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
    val orderIndex: Int,
    val transcript: List<TranscriptSegment>,
    val learningObjectives: List<String>,
    val documents: List<Document>,
    val visualAssets: List<VisualAsset>
)

data class TranscriptSegment(
    val id: String,
    val type: String,  // "content", "checkpoint", "activity"
    val content: String,
    val spokenText: String?,  // TTS-optimized version
    val stoppingPoint: StoppingPoint?
)
```

### 5.8 Progress Tracking

```kotlin
@Entity(tableName = "topic_progress")
data class TopicProgress(
    @PrimaryKey val topicId: String,
    val curriculumId: String,
    val timeSpentSeconds: Long,
    val masteryLevel: Float,  // 0.0 - 1.0
    val lastAccessedAt: Long,
    val completedSegments: List<String>
)
```

### 5.9 Telemetry Engine

Must track identical metrics:

```kotlin
class TelemetryEngine {
    // Latency tracking
    fun recordLatency(type: LatencyType, durationMs: Long)

    // Cost tracking
    fun recordCost(provider: String, costUsd: Double)

    // Session metrics
    fun getSessionMetrics(): SessionMetrics

    // Export for server upload
    fun exportMetrics(): MetricsSnapshot
}

data class SessionMetrics(
    val sttMedianLatencyMs: Long,
    val sttP99LatencyMs: Long,
    val llmMedianLatencyMs: Long,
    val llmP99LatencyMs: Long,
    val ttsMedianLatencyMs: Long,
    val ttsP99LatencyMs: Long,
    val e2eMedianLatencyMs: Long,
    val e2eP99LatencyMs: Long,
    val totalCostUsd: Double,
    val sessionDurationSeconds: Long,
    val turnCount: Int,
    val interruptionCount: Int
)
```

### 5.10 Patch Panel (LLM Routing)

Intelligent routing based on task type and conditions:

```kotlin
class PatchPanelService(
    private val endpoints: List<LLMEndpoint>,
    private val routingTable: RoutingTable
) {
    fun selectEndpoint(
        taskType: LLMTaskType,
        context: RoutingContext
    ): LLMEndpoint {
        // Evaluate routing rules
        val matchingRules = routingTable.rules.filter { rule ->
            rule.conditions.all { it.matches(context) }
        }

        // Return highest priority matching endpoint
        return matchingRules
            .sortedByDescending { it.priority }
            .firstOrNull()?.endpoint
            ?: endpoints.first()
    }
}

enum class LLMTaskType {
    TUTORING,           // Main conversation
    PLANNING,           // Session planning
    SUMMARIZATION,      // Content summarization
    ASSESSMENT,         // Quiz/evaluation
    SIMPLE_RESPONSE     // Quick acknowledgments
}
```

---

## 6. UI/UX Specification

### 6.1 Navigation Structure

**6 Primary Tabs (identical to iOS):**

```
┌─────────────────────────────────────────────────────────────────┐
│                        UnaMentis                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   [Session] [Curriculum] [To-Do] [History] [Analytics] [Settings]│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 Screen Specifications

#### 6.2.1 Session Screen (Main Voice Interface)

**Purpose:** Primary voice conversation interface

**Components:**
- Transcript display (scrolling list of user/AI messages)
- Audio level visualization (waveform or VU meter)
- Visual asset overlay (images, diagrams for curriculum)
- Session control bar (mute, pause, slide-to-stop)
- Status indicator (listening, thinking, speaking)

**Key Interactions:**
- Tap anywhere to start session
- Slide-to-stop gesture for ending session
- Tap mute button to toggle microphone
- Tap pause button to pause/resume

**Compose Implementation:**
```kotlin
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val transcript by viewModel.transcript.collectAsState()

    Scaffold(
        bottomBar = {
            SessionControlBar(
                isMuted = state.isMuted,
                isPaused = state.isPaused,
                onMuteToggle = viewModel::toggleMute,
                onPauseToggle = viewModel::togglePause,
                onStop = viewModel::stopSession
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Status indicator
            SessionStatusBanner(state = state.sessionState)

            // Transcript
            LazyColumn(
                reverseLayout = true,
                modifier = Modifier.weight(1f)
            ) {
                items(transcript) { entry ->
                    TranscriptBubble(entry = entry)
                }
            }

            // Visual asset overlay (if curriculum mode)
            state.currentVisualAsset?.let { asset ->
                VisualAssetView(asset = asset)
            }
        }
    }
}
```

#### 6.2.2 Curriculum Screen

**Purpose:** Browse and import curriculum content

**Components:**
- Curriculum list with search
- Curriculum detail view with topics
- Topic detail with learning objectives
- Server browser for remote import
- Download progress indicator

**Adaptive Layout (phone vs tablet):**
```kotlin
@Composable
fun CurriculumScreen() {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
        // Tablet: List-detail layout
        ListDetailPaneScaffold(
            listPane = { CurriculumList() },
            detailPane = { CurriculumDetail() }
        )
    } else {
        // Phone: Navigation-based
        NavHost(navController, startDestination = "list") {
            composable("list") { CurriculumList() }
            composable("detail/{id}") { CurriculumDetail() }
        }
    }
}
```

#### 6.2.3 To-Do Screen

**Purpose:** Task management for learning goals

**Components:**
- Filter tabs (Active, Completed, Archived)
- Todo item rows with status, type, source
- Add/edit todo sheet
- Resume from context badge

#### 6.2.4 History Screen

**Purpose:** Session history and playback

**Components:**
- Session list with date, duration, cost
- Session detail with full transcript
- Export functionality (JSON, text)
- Metrics summary per session

#### 6.2.5 Analytics Screen

**Purpose:** Telemetry dashboard

**Components:**
- Quick stats cards (E2E latency, cost, turns)
- Latency breakdown charts (STT, LLM, TTS)
- Cost breakdown by provider
- Quality metrics (interruptions, thermal events)
- Session history trends

#### 6.2.6 Settings Screen

**Purpose:** App configuration

**Sections:**
1. **API Providers** - Manage API keys and select providers
2. **Audio Settings** - Sample rate, buffer size, voice processing
3. **VAD Settings** - Threshold, sensitivity, silence duration
4. **Server Configuration** - Self-hosted server setup
5. **Telemetry** - Logging, metrics export
6. **Presets** - Quick configuration (Balanced, Low Latency, Cost Optimized)
7. **Debug** - Developer tools, device metrics

#### 6.2.7 Onboarding Screen

**Purpose:** First-run experience

**Pages (TabLayout with ViewPager):**
1. Welcome - App introduction
2. Structured Learning - Curriculum features
3. Offline Mode - On-device capabilities
4. Voice Control - Hands-free operation

### 6.3 Custom Components

#### 6.3.1 SlideToStopButton

```kotlin
@Composable
fun SlideToStopButton(
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val trackWidth = 200.dp
    val thumbSize = 56.dp
    val completionThreshold = 0.8f

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(thumbSize)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        // Instruction text
        Text(
            text = "Slide to stop",
            modifier = Modifier.align(Alignment.Center),
            alpha = 1f - (offsetX / maxOffset)
        )

        // Draggable thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(thumbSize)
                .background(Color.Red, CircleShape)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX / maxOffset > completionThreshold) {
                                onStop()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffset)
                        }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
```

#### 6.3.2 TranscriptBubble

```kotlin
@Composable
fun TranscriptBubble(
    entry: TranscriptEntry,
    modifier: Modifier = Modifier
) {
    val isUser = entry.role == "user"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = entry.text,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
```

### 6.4 Theming

**Material Design 3 with custom colors:**

```kotlin
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),        // Blue
    secondary = Color(0xFF388E3C),       // Green
    tertiary = Color(0xFFF57C00),        // Orange
    error = Color(0xFFD32F2F),           // Red
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFFFB74D),
    error = Color(0xFFEF5350),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5)
)
```

### 6.5 Animations

Match iOS animation patterns:

```kotlin
// Spring animation for session controls
val animatedOffset by animateFloatAsState(
    targetValue = if (isDragging) offset else 0f,
    animationSpec = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessLow
    )
)

// Scale animation for buttons
val scale by animateFloatAsState(
    targetValue = if (isPressed) 1.1f else 1.0f,
    animationSpec = spring(dampingRatio = 0.5f)
)
```

---

## 7. Server Communication

### 7.1 REST API Endpoints

**Base URL:** `http://{host}:8766` (Management Console)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/curricula` | GET | List all curricula |
| `/api/curricula/{id}` | GET | Get curriculum details |
| `/api/curricula/{id}/full-with-assets` | GET | Download curriculum with assets |
| `/api/curricula/{id}/topics/{topicId}/transcript` | GET | Get topic transcript |
| `/api/metrics` | POST | Upload session metrics |

### 7.2 WebSocket Connections

**Deepgram STT:**
```kotlin
val client = OkHttpClient()
val request = Request.Builder()
    .url("wss://api.deepgram.com/v1/listen?model=nova-2&smart_format=true")
    .addHeader("Authorization", "Token $apiKey")
    .build()

val webSocket = client.newWebSocket(request, object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        val response = Json.decodeFromString<DeepgramResponse>(text)
        // Emit STT result
    }
})
```

**ElevenLabs TTS:**
```kotlin
val request = Request.Builder()
    .url("wss://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream-input")
    .addHeader("xi-api-key", apiKey)
    .build()

val webSocket = client.newWebSocket(request, object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        // Play audio chunk
    }
})
```

### 7.3 Client Identification Headers

All requests include:
```
X-Client-ID: {device UUID}
X-Client-Name: {device model}
X-Client-Platform: Android
X-Client-Version: {app version}
```

### 7.4 Error Handling

Implement same error types as iOS:

```kotlin
sealed class NetworkError : Exception() {
    data class ConnectionFailed(override val message: String) : NetworkError()
    object AuthenticationFailed : NetworkError()
    data class RateLimited(val retryAfterSeconds: Int?) : NetworkError()
    object QuotaExceeded : NetworkError()
    data class ServerError(val statusCode: Int, override val message: String?) : NetworkError()
}
```

---

## 8. Performance Requirements

### 8.1 Latency Targets (Same as iOS)

| Component | Target (Median) | Acceptable (P99) |
|-----------|----------------|------------------|
| STT | <300ms | <1000ms |
| LLM First Token | <200ms | <500ms |
| TTS TTFB | <200ms | <400ms |
| **E2E Turn** | **<500ms** | **<1000ms** |

### 8.2 Stability Targets

- **90-min Sessions:** 100% completion rate without crashes
- **Memory Growth:** <50MB over 90 minutes
- **Thermal Throttle:** <3 events per 90-min session
- **Interruption Success:** >90% successful barge-ins

### 8.3 Resource Usage

| Resource | Target |
|----------|--------|
| Battery drain | <15%/hour during active session |
| Memory (app) | <300MB baseline |
| Memory growth | <50MB over 90 minutes |
| CPU (idle) | <5% when paused |
| CPU (active) | <40% average during session |

### 8.4 Cost Targets

- **Balanced Preset:** <$3/hour per user
- **Cost-Optimized:** <$1.50/hour per user

---

## 9. Native Android Optimizations

### 9.1 Audio Pipeline (Oboe Library)

Use Google's Oboe library for lowest-latency audio:

```kotlin
class AudioEngine {
    private external fun nativeCreateStream(
        sampleRate: Int,
        channelCount: Int,
        callback: AudioCallback
    ): Long

    private external fun nativeStartStream(streamPtr: Long)
    private external fun nativeStopStream(streamPtr: Long)

    companion object {
        init {
            System.loadLibrary("audio_engine")
        }
    }
}
```

**C++ (JNI):**
```cpp
#include <oboe/Oboe.h>

class AudioCallback : public oboe::AudioStreamCallback {
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *stream,
        void *audioData,
        int32_t numFrames
    ) override {
        // Process audio with minimal latency
        return oboe::DataCallbackResult::Continue;
    }
};
```

### 9.2 Neural Network Acceleration (NNAPI)

```kotlin
// Use NNAPI delegate for TensorFlow Lite models
val options = Interpreter.Options().apply {
    addDelegate(NnApiDelegate())
}
val interpreter = Interpreter(modelBuffer, options)
```

### 9.3 Foreground Service for Background Audio

```kotlin
class SessionForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UnaMentis Session Active")
            .setContentText("Tap to return to session")
            .setSmallIcon(R.drawable.ic_mic)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }
}
```

### 9.4 Memory-Mapped Model Loading

```kotlin
// Memory-map large model files for efficient loading
val modelFile = File(context.filesDir, "llama-3b.gguf")
val fileChannel = FileInputStream(modelFile).channel
val mappedBuffer = fileChannel.map(
    FileChannel.MapMode.READ_ONLY,
    0,
    modelFile.length()
)
```

### 9.5 Thermal Monitoring

```kotlin
class ThermalMonitor(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun getCurrentThermalStatus(): ThermalStatus {
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NOMINAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.FAIR
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.FAIR
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SERIOUS
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
            else -> ThermalStatus.NOMINAL
        }
    }

    fun addThermalListener(listener: (ThermalStatus) -> Unit) {
        powerManager.addThermalStatusListener(executor) { status ->
            listener(mapThermalStatus(status))
        }
    }
}
```

---

## 10. Device Capability Tiers

### 10.1 Tier Definitions (Match iOS)

**Tier 1: Flagship**
- Snapdragon 8 Gen 2+ or equivalent
- 12GB+ RAM
- Full on-device capabilities

**Tier 2: Standard**
- Snapdragon 8 Gen 1+ or equivalent
- 8GB+ RAM
- Reduced on-device capabilities

**Minimum Supported:**
- Snapdragon 7 Gen 1+ or equivalent
- 6GB+ RAM
- Cloud-primary with limited on-device

### 10.2 Capability Detection

```kotlin
object DeviceCapabilityDetector {
    fun detectTier(context: Context): DeviceTier {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024)
        val cpuCores = Runtime.getRuntime().availableProcessors()

        return when {
            totalRamGb >= 12 && cpuCores >= 8 -> DeviceTier.FLAGSHIP
            totalRamGb >= 8 && cpuCores >= 6 -> DeviceTier.STANDARD
            totalRamGb >= 6 -> DeviceTier.MINIMUM
            else -> DeviceTier.UNSUPPORTED
        }
    }
}
```

### 10.3 Dynamic Fallback

Same triggers as iOS:
- Thermal throttling → Reduce on-device model size
- Memory pressure → Unload optional models
- Low battery → Disable on-device LLM
- High inference latency → Fall back to cloud

---

## 11. Accessibility Requirements

### 11.1 TalkBack Support

All interactive elements must have content descriptions:

```kotlin
@Composable
fun SessionControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Button
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null  // Handled by semantics
        )
    }
}
```

### 11.2 Dynamic Font Scaling

Use `sp` units for all text:

```kotlin
Text(
    text = "Session Active",
    style = MaterialTheme.typography.headlineMedium,
    // Typography uses sp units automatically
)
```

### 11.3 Minimum Touch Targets

Ensure 48dp minimum touch targets (Android guideline):

```kotlin
IconButton(
    onClick = onClick,
    modifier = Modifier.size(48.dp)  // Minimum touch target
) {
    Icon(...)
}
```

### 11.4 Reduce Motion

Respect system animation settings:

```kotlin
@Composable
fun AnimatedComponent() {
    val reduceMotion = LocalReduceMotion.current

    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = if (reduceMotion) {
            snap()  // No animation
        } else {
            spring(dampingRatio = 0.7f)
        }
    )
}
```

---

## 12. Implementation Roadmap

### Phase 1: Foundation (Weeks 1-3)

- [ ] Project setup (Kotlin, Compose, Hilt, Room)
- [ ] Core data models and Room entities
- [ ] API client and networking layer
- [ ] Basic navigation structure

### Phase 2: Audio Pipeline (Weeks 4-5)

- [ ] Oboe audio engine integration
- [ ] VAD service (Silero TFLite)
- [ ] Audio level monitoring
- [ ] Basic recording/playback

### Phase 3: Provider Integration (Weeks 6-8)

- [ ] STT providers (Deepgram, AssemblyAI, Android Speech)
- [ ] TTS providers (ElevenLabs, Deepgram, Android TTS)
- [ ] LLM providers (OpenAI, Anthropic, self-hosted)
- [ ] Provider routing (Patch Panel)

### Phase 4: Session Management (Weeks 9-10)

- [ ] SessionManager state machine
- [ ] Conversation history
- [ ] Turn-taking logic
- [ ] Barge-in handling
- [ ] Session persistence

### Phase 5: UI Implementation (Weeks 11-14)

- [ ] Session screen
- [ ] Curriculum browser
- [ ] Settings screens
- [ ] Analytics dashboard
- [ ] Onboarding flow

### Phase 6: Polish & Testing (Weeks 15-16)

- [ ] Performance optimization
- [ ] Accessibility audit
- [ ] 90-minute stability testing
- [ ] Memory leak detection
- [ ] Thermal management

---

## 13. Testing Strategy

### 13.1 Unit Tests

- All ViewModels
- All Services
- All data transformations
- Provider routing logic

### 13.2 Integration Tests

- Audio pipeline end-to-end
- Server communication
- Database operations
- Provider failover

### 13.3 UI Tests

- Navigation flows
- Accessibility compliance
- Orientation changes
- Different screen sizes

### 13.4 Performance Tests

- Latency benchmarks
- Memory profiling (90-minute sessions)
- Thermal behavior
- Battery consumption

### 13.5 Device Testing Matrix

| Device | Tier | Priority |
|--------|------|----------|
| Pixel 8 Pro | Flagship | High |
| Samsung S24 Ultra | Flagship | High |
| Pixel 7a | Standard | High |
| Samsung A54 | Standard | Medium |
| Various tablets | Mixed | Medium |

---

## 14. Development Environment Setup

This section provides comprehensive instructions for setting up the Android development environment to achieve full round-trip development parity with the iOS workflow.

### 14.1 Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| Android Studio | Ladybug (2024.2.1) or newer | IDE and SDK Manager |
| Android SDK | API 34 (Android 14) | Target platform |
| Android NDK | 26.x or newer | Native code (Oboe, llama.cpp) |
| JDK | 17+ | Gradle and Kotlin compilation |
| Kotlin | 2.0+ | Primary language |
| Node.js | 20+ | MCP server runtime |
| Python | 3.12+ | Log server |

### 14.2 SDK Manager Configuration

Install via Android Studio SDK Manager:

```
SDK Platforms:
- Android 14.0 (API 34) - Full platform
- Android 13.0 (API 33) - For backward compatibility testing

SDK Tools:
- Android SDK Build-Tools 34.0.0
- Android SDK Command-line Tools
- Android Emulator
- Android SDK Platform-Tools
- NDK (Side by side) 26.x
- CMake 3.22.1+
```

### 14.3 Environment Variables

Add to `~/.zshrc` or `~/.bashrc`:

```bash
# Android SDK
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.3.11579264

# Path additions
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

### 14.4 Emulator Configuration

**Recommended Device Profiles:**

| Profile | Use Case | Specs |
|---------|----------|-------|
| Pixel 8 Pro API 34 | Primary development | 12GB RAM, Play Store |
| Pixel Fold API 34 | Foldable testing | For adaptive layouts |
| Pixel Tablet API 34 | Tablet testing | Large screen |
| Medium Phone API 34 | Standard tier testing | 8GB RAM |

**Performance Settings:**

```
Graphics: Hardware - GLES 2.0+
RAM: 4096 MB (minimum)
VM Heap: 512 MB
Internal Storage: 8 GB
Multi-Core CPU: 4 cores
```

**Network Configuration for Log Server:**

The emulator can access host machine via `10.0.2.2`. Configure the app to use:
- Log server URL: `http://10.0.2.2:8765`
- Management console: `http://10.0.2.2:8766`

### 14.5 Gradle Configuration

**Project-level build.gradle.kts:**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}
```

**App-level build.gradle.kts additions for linting:**

```kotlin
plugins {
    // ... existing plugins
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

ktlint {
    android = true
    outputToConsole = true
    ignoreFailures = false
}

detekt {
    config.setFrom(files("$rootDir/config/detekt.yml"))
    buildUponDefaultConfig = true
}
```

---

## 15. MCP Server Integration

MCP (Model Context Protocol) servers enable Claude Code to perform autonomous build, test, and UI automation cycles. This is essential for the same round-trip development experience as iOS.

### 15.1 Required MCP Servers

| Server | Purpose | iOS Equivalent |
|--------|---------|----------------|
| mobile-mcp | Device/emulator control, UI automation | ios-simulator-mcp |
| gradle-mcp-server | Build and test automation | XcodeBuildMCP |

### 15.2 Installation

**mobile-mcp (via npx):**
```bash
# No installation needed - runs via npx
# Verify Node.js 20+ is installed
node --version
```

**gradle-mcp-server:**
```bash
# Download the JAR
mkdir -p ~/mcp-servers/gradle-mcp-server
curl -L -o ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar \
  https://github.com/IlyaGulya/gradle-mcp-server/releases/latest/download/gradle-mcp-server-all.jar

# Verify Java 17+ is installed
java --version
```

### 15.3 Configuration

Create or update `.mcp.json` in project root:

```json
{
  "mcpServers": {
    "mobile-mcp": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@mobilenext/mobile-mcp@latest"]
    },
    "gradle-mcp-server": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar"]
    }
  }
}
```

### 15.4 Verification

```bash
claude mcp list
# Expected output:
# mobile-mcp: Connected
# gradle-mcp-server: Connected
```

If not connected, restart Claude Code session.

### 15.5 Tool Reference

**mobile-mcp Tools:**

| Tool | Description |
|------|-------------|
| `mobile_list_available_devices` | List connected devices and emulators |
| `mobile_get_screen_size` | Get device screen dimensions |
| `mobile_get_orientation` | Get current screen orientation |
| `mobile_set_orientation` | Set portrait/landscape |
| `mobile_take_screenshot` | Capture screen image |
| `mobile_save_screenshot` | Save screenshot to file |
| `mobile_list_elements_on_screen` | Get UI accessibility tree |
| `mobile_click_on_screen_at_coordinates` | Tap at x,y |
| `mobile_double_tap_on_screen` | Double tap |
| `mobile_long_press_on_screen_at_coordinates` | Long press |
| `mobile_swipe_on_screen` | Swipe gesture |
| `mobile_type_keys` | Type text input |
| `mobile_press_button` | Press hardware button (HOME, BACK, etc.) |
| `mobile_open_url` | Open URL in browser |
| `mobile_install_app` | Install APK |
| `mobile_uninstall_app` | Remove app |
| `mobile_launch_app` | Start app by package |
| `mobile_terminate_app` | Stop running app |
| `mobile_list_apps` | List installed packages |

**gradle-mcp-server Tools:**

| Tool | Description |
|------|-------------|
| `get_gradle_project_info` | Get build structure, tasks, environment |
| `execute_gradle_task` | Run any Gradle task (build, clean, etc.) |
| `run_gradle_tests` | Execute tests with structured results |

### 15.6 iOS to Android Tool Mapping

| iOS Tool | Android Equivalent |
|----------|-------------------|
| `mcp__ios-simulator__ui_tap` | `mobile_click_on_screen_at_coordinates` |
| `mcp__ios-simulator__ui_swipe` | `mobile_swipe_on_screen` |
| `mcp__ios-simulator__ui_type` | `mobile_type_keys` |
| `mcp__ios-simulator__screenshot` | `mobile_take_screenshot` |
| `mcp__ios-simulator__ui_describe_all` | `mobile_list_elements_on_screen` |
| `mcp__XcodeBuildMCP__build_sim` | `execute_gradle_task` (assembleDebug) |
| `mcp__XcodeBuildMCP__test_sim` | `run_gradle_tests` |
| `mcp__XcodeBuildMCP__install_app_sim` | `mobile_install_app` |
| `mcp__XcodeBuildMCP__launch_app_sim` | `mobile_launch_app` |

---

## 16. Build Automation Scripts

Scripts provide consistent, reproducible build and test operations. Located in `scripts/` directory.

### 16.1 Script Overview

| Script | Purpose | iOS Equivalent |
|--------|---------|----------------|
| `build.sh` | Build debug APK | xcodebuild build |
| `test-quick.sh` | Unit tests only | test-quick.sh |
| `test-all.sh` | Unit + instrumented tests | test-all.sh |
| `lint.sh` | ktlint + detekt | lint.sh |
| `format.sh` | Auto-format code | format.sh |
| `health-check.sh` | Lint + quick tests | health-check.sh |
| `install-emulator.sh` | Install APK on emulator | - |
| `launch-emulator.sh` | Start emulator by name | - |

### 16.2 Script Implementations

**build.sh:**
```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "Building debug APK..."
./gradlew assembleDebug --console=plain

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "Build complete: $APK_PATH"
    echo "Size: $(du -h "$APK_PATH" | cut -f1)"
else
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi
```

**test-quick.sh:**
```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "Running unit tests..."
./gradlew test --console=plain

echo "Unit tests passed!"
```

**test-all.sh:**
```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "Running all tests (unit + instrumented)..."
echo "Ensure an emulator is running for instrumented tests."

./gradlew test --console=plain
./gradlew connectedAndroidTest --console=plain

echo "All tests passed!"
```

**lint.sh:**
```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "Running ktlint..."
./gradlew ktlintCheck --console=plain

echo "Running detekt..."
./gradlew detekt --console=plain

echo "Lint checks passed!"
```

**format.sh:**
```bash
#!/bin/bash
cd "$(dirname "$0")/.."

echo "Formatting code with ktlint..."
./gradlew ktlintFormat --console=plain

echo "Formatting complete!"
```

**health-check.sh:**
```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "=== Health Check ==="
echo ""

echo "Step 1/2: Lint checks..."
./scripts/lint.sh

echo ""
echo "Step 2/2: Quick tests..."
./scripts/test-quick.sh

echo ""
echo "=== Health check passed! ==="
```

**install-emulator.sh:**
```bash
#!/bin/bash
set -e
cd "$(dirname "$0")/.."

APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"

if [ ! -f "$APK_PATH" ]; then
    echo "APK not found: $APK_PATH"
    echo "Run ./scripts/build.sh first"
    exit 1
fi

echo "Installing $APK_PATH..."
adb install -r "$APK_PATH"

echo "Installation complete!"
```

**launch-emulator.sh:**
```bash
#!/bin/bash
set -e

EMULATOR_NAME="${1:-Pixel_8_Pro_API_34}"

echo "Starting emulator: $EMULATOR_NAME"
$ANDROID_HOME/emulator/emulator -avd "$EMULATOR_NAME" -no-snapshot-load &

echo "Waiting for emulator to boot..."
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'

echo "Emulator ready!"
```

### 16.3 Making Scripts Executable

```bash
chmod +x scripts/*.sh
```

---

## 17. Round-Trip Development Workflow

This workflow enables autonomous build-test-debug cycles using MCP tools, matching the iOS development experience.

### 17.1 The Complete Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    Round-Trip Workflow                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   1. Build                                                   │
│      └─► gradle-mcp-server: execute_gradle_task             │
│          (assembleDebug)                                     │
│                        ↓                                     │
│   2. Install                                                 │
│      └─► mobile-mcp: mobile_install_app                     │
│                        ↓                                     │
│   3. Launch                                                  │
│      └─► mobile-mcp: mobile_launch_app                      │
│                        ↓                                     │
│   4. Capture Logs                                            │
│      └─► Log server (:8765) or adb logcat                   │
│                        ↓                                     │
│   5. Screenshot                                              │
│      └─► mobile-mcp: mobile_take_screenshot                 │
│                        ↓                                     │
│   6. Interact                                                │
│      └─► mobile-mcp: mobile_click, swipe, type              │
│                        ↓                                     │
│   7. Debug & Iterate                                         │
│      └─► Analyze logs, fix issues, repeat                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 17.2 Log Server Integration

The Python log server from iOS works identically for Android.

**Starting the Log Server:**
```bash
python3 scripts/log_server.py &
curl -s http://localhost:8765/health  # Returns "OK"
```

**Android App Log Client:**

```kotlin
// In your logging module
object RemoteLogger {
    private val client = OkHttpClient()
    private val logServerUrl = "http://10.0.2.2:8765/log"  // Host from emulator

    fun log(level: String, message: String, label: String = "App") {
        val json = JSONObject().apply {
            put("level", level)
            put("message", message)
            put("label", label)
            put("timestamp", System.currentTimeMillis())
        }

        val request = Request.Builder()
            .url(logServerUrl)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* ignore */ }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
```

**Debugging Flow:**
```bash
# 1. Clear logs
curl -s -X POST http://localhost:8765/clear

# 2. Reproduce the issue in the app

# 3. Fetch logs
curl -s http://localhost:8765/logs | python3 -m json.tool

# 4. Open web interface for real-time viewing
open http://localhost:8765/
```

### 17.3 Logcat Integration

For native Android logging:

```bash
# Filter to app logs only
adb logcat -v time -s UnaMentis:V *:S

# Clear and start fresh
adb logcat -c && adb logcat -v time -s UnaMentis:V *:S

# Save to file
adb logcat -v time -s UnaMentis:V *:S > debug_session.log
```

### 17.4 MCP Workflow Example

```
Claude Code session:

1. "Build the app"
   → execute_gradle_task(task: "assembleDebug")

2. "Install on the emulator"
   → mobile_install_app(app_path: "app/build/outputs/apk/debug/app-debug.apk")

3. "Launch the app"
   → mobile_launch_app(package_name: "com.unamentis")

4. "Take a screenshot"
   → mobile_take_screenshot()

5. "Tap the Start Session button"
   → mobile_list_elements_on_screen()  # Find coordinates
   → mobile_click_on_screen_at_coordinates(x: 200, y: 400)

6. "Type hello"
   → mobile_type_keys(text: "hello")
```

---

## 18. Definition of Done

This section establishes the mandatory quality gates that must pass before any implementation is considered complete. This matches the iOS project's rigor.

### 18.1 The Golden Rule

**NO IMPLEMENTATION IS COMPLETE UNTIL TESTS PASS.**

### 18.2 Pre-Commit Checklist

```bash
./scripts/health-check.sh
```

This runs:
1. `./scripts/lint.sh` - ktlint + detekt must pass with zero violations
2. `./scripts/test-quick.sh` - All unit tests must pass

### 18.3 What "Complete" Means

1. Code is written and compiles without errors
2. `./scripts/lint.sh` passes with zero violations
3. `./scripts/test-quick.sh` passes with ALL tests green
4. You have ACTUALLY RUN these commands and verified the results

### 18.4 Failure Mode to Avoid

**WRONG:** Write code, see it compiles, tell user "implementation is complete"
**RIGHT:** Write code, run tests, verify all pass, THEN tell user "implementation is complete"

### 18.5 For Significant Changes

Run the full test suite:

```bash
./scripts/test-all.sh
```

This includes instrumented tests that run on the emulator.

### 18.6 Testing Philosophy: Real Over Mock

**Only mock paid external APIs:**
- OpenAI, Anthropic (LLM)
- Deepgram, ElevenLabs (STT/TTS)
- Any API that costs money per request

**Use real implementations for:**
- Internal services (SessionManager, TelemetryEngine)
- Room database (use in-memory configuration)
- File operations (use temp directories)
- Local computations

### 18.7 When Mocking is Required

Mocks must be faithful:
- Return data in exact API format
- Simulate all error conditions
- Match realistic performance (TTFT, inter-token delays)
- Validate inputs like real API
- Track token counts accurately

### 18.8 Commit Convention

Follow Conventional Commits:

```
feat: Add new curriculum browser
fix: Resolve audio playback stutter
docs: Update API integration guide
test: Add SessionManager unit tests
refactor: Extract audio processing logic
perf: Optimize TTS buffer handling
ci: Add instrumented test workflow
chore: Update dependencies
```

**BEFORE EVERY COMMIT:**
```bash
./scripts/health-check.sh
```

Do NOT commit if the health check fails. Fix the issues first.

---

## 19. Server API Reference

This section documents all server APIs that the Android app must integrate with for full feature parity with iOS.

### 19.1 Server Overview

| Server | Port | Purpose |
|--------|------|---------|
| Log Server | 8765 | Remote logging and debugging |
| Management Console | 8766 | Curriculum, metrics, clients, configuration |
| Operations Console | 3000 | Web interface for curriculum management |

**Network Configuration from Emulator:**
- Emulator accesses host via `10.0.2.2`
- Log server: `http://10.0.2.2:8765`
- Management console: `http://10.0.2.2:8766`

### 19.2 Log Server API (Port 8765)

The log server provides centralized logging for debugging across iOS and Android apps.

#### POST /log

Submit a log entry.

**Request:**
```json
{
  "level": "INFO",
  "message": "Session started",
  "label": "SessionManager",
  "timestamp": 1704067200000,
  "file": "SessionManager.kt",
  "function": "startSession",
  "line": 142,
  "metadata": {
    "session_id": "abc123",
    "topic_id": "topic-001"
  }
}
```

**Response:** `200 OK` with `{"status": "ok"}`

**Log Levels:** TRACE, DEBUG, INFO, NOTICE, WARNING, ERROR, CRITICAL

#### GET /logs

Retrieve all logs in buffer.

**Query Parameters:**
- `level` - Filter by minimum level (e.g., `?level=WARNING`)
- `label` - Filter by label (e.g., `?label=SessionManager`)
- `limit` - Max entries to return (default: all)

**Response:**
```json
[
  {
    "id": "log-001",
    "timestamp": "2024-01-01T12:00:00Z",
    "level": "INFO",
    "label": "SessionManager",
    "message": "Session started",
    "file": "SessionManager.kt",
    "function": "startSession",
    "line": 142,
    "metadata": {},
    "client_id": "device-uuid",
    "client_name": "Pixel 8 Pro"
  }
]
```

#### POST /clear

Clear the log buffer.

**Response:** `200 OK` with `{"status": "cleared", "count": 150}`

#### GET /health

Health check endpoint.

**Response:** `OK` (plain text)

#### GET /

Web interface for viewing logs in browser.

### 19.3 Management Console API (Port 8766)

#### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "uptime_seconds": 3600,
  "version": "1.0.0"
}
```

### 19.4 Curriculum API

#### GET /api/curricula

List all available curricula.

**Response:**
```json
[
  {
    "id": "mit-physics-8.01",
    "title": "MIT Physics 8.01",
    "description": "Classical Mechanics",
    "version": "1.0.0",
    "topic_count": 36,
    "total_duration": "PT40H",
    "difficulty": "intermediate",
    "age_range": "18+",
    "keywords": ["physics", "mechanics"],
    "has_visual_assets": true,
    "visual_asset_count": 150
  }
]
```

#### GET /api/curricula/{curriculum_id}

Get curriculum detail with topic list.

**Response:**
```json
{
  "id": "mit-physics-8.01",
  "title": "MIT Physics 8.01",
  "description": "Classical Mechanics",
  "version": "1.0.0",
  "difficulty": "intermediate",
  "age_range": "18+",
  "duration": "PT40H",
  "keywords": ["physics"],
  "topics": [
    {
      "id": "topic-001",
      "title": "Introduction to Mechanics",
      "order_index": 0,
      "description": "Overview of classical mechanics",
      "duration": "PT1H",
      "segment_count": 25,
      "visual_asset_count": 5
    }
  ],
  "glossary_terms": [],
  "learning_objectives": []
}
```

#### GET /api/curricula/{curriculum_id}/full-with-assets

Download complete curriculum with all assets for offline use.

**Response:**
```json
{
  "curriculum": { /* full UMCF document */ },
  "assets": [
    {
      "id": "asset-001",
      "topic_id": "topic-001",
      "filename": "diagram.png",
      "mime_type": "image/png",
      "size_bytes": 45000,
      "data_base64": "iVBORw0KGgo..."
    }
  ]
}
```

#### GET /api/curricula/{curriculum_id}/topics/{topic_id}/transcript

Get topic transcript with segments.

**Response:**
```json
{
  "topic_id": "topic-001",
  "title": "Introduction to Mechanics",
  "segments": [
    {
      "id": "seg-001",
      "type": "content",
      "content": "Welcome to this lecture on classical mechanics.",
      "spoken_text": "Welcome to this lecture on classical mechanics.",
      "stopping_point": null,
      "visual_asset_id": null
    },
    {
      "id": "seg-002",
      "type": "checkpoint",
      "content": "Let's verify your understanding.",
      "stopping_point": {
        "type": "quiz",
        "prompt": "What is the first law of motion?",
        "expected_concepts": ["inertia", "rest", "motion"]
      }
    }
  ]
}
```

#### POST /api/curricula/reload

Reload curricula from disk.

**Response:** `200 OK` with `{"status": "reloaded", "count": 5}`

### 19.5 Metrics API

#### POST /api/metrics

Upload session metrics.

**Request:**
```json
{
  "client_id": "device-uuid",
  "client_name": "Pixel 8 Pro",
  "session_id": "session-001",
  "session_duration": 3600.0,
  "turns_total": 45,
  "interruptions": 3,
  "stt_latency_median": 250.0,
  "stt_latency_p99": 800.0,
  "llm_ttft_median": 180.0,
  "llm_ttft_p99": 450.0,
  "tts_ttfb_median": 150.0,
  "tts_ttfb_p99": 350.0,
  "e2e_latency_median": 480.0,
  "e2e_latency_p99": 950.0,
  "stt_cost": 0.25,
  "tts_cost": 0.15,
  "llm_cost": 1.50,
  "total_cost": 1.90,
  "thermal_throttle_events": 0,
  "network_degradations": 1
}
```

**Response:** `200 OK` with `{"status": "received", "id": "metrics-001"}`

#### GET /api/metrics

Retrieve metrics history.

**Query Parameters:**
- `client_id` - Filter by client
- `limit` - Max entries (default: 100)

### 19.6 Clients API

#### POST /api/clients/heartbeat

Register client and send heartbeat.

**Request Headers:**
```
X-Client-ID: device-uuid
X-Client-Name: Pixel 8 Pro
X-Client-Platform: Android
X-Client-Version: 1.0.0
```

**Request Body:**
```json
{
  "device_model": "Pixel 8 Pro",
  "os_version": "Android 14",
  "app_version": "1.0.0",
  "status": "active",
  "current_session_id": "session-001",
  "config": {
    "stt_provider": "deepgram",
    "tts_provider": "elevenlabs",
    "llm_provider": "openai"
  }
}
```

**Response:**
```json
{
  "status": "ok",
  "server_time": "2024-01-01T12:00:00Z",
  "config_updates": null
}
```

#### GET /api/clients

List connected clients.

**Response:**
```json
[
  {
    "id": "device-uuid",
    "name": "Pixel 8 Pro",
    "device_model": "Pixel 8 Pro",
    "os_version": "Android 14",
    "app_version": "1.0.0",
    "status": "online",
    "last_seen": "2024-01-01T12:00:00Z",
    "current_session_id": "session-001"
  }
]
```

### 19.7 WebSocket API

Connect to `/ws` for real-time updates.

**Message Types (Server → Client):**
- `log` - New log entry
- `metrics` - Metrics update
- `client_status` - Client connected/disconnected
- `curriculum_update` - Curriculum reloaded

**Example:**
```json
{
  "type": "log",
  "data": { /* log entry */ },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### 19.8 Android Implementation

**Kotlin HTTP Client Setup:**

```kotlin
class ApiClient(
    private val logServerUrl: String = "http://10.0.2.2:8765",
    private val managementUrl: String = "http://10.0.2.2:8766"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-Client-ID", deviceId)
                .addHeader("X-Client-Name", deviceName)
                .addHeader("X-Client-Platform", "Android")
                .addHeader("X-Client-Version", BuildConfig.VERSION_NAME)
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun getCurricula(): List<CurriculumSummary> {
        val request = Request.Builder()
            .url("$managementUrl/api/curricula")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("API error: ${response.code}")
                val json = response.body?.string() ?: throw IOException("Empty response")
                Json.decodeFromString(json)
            }
        }
    }

    suspend fun uploadMetrics(metrics: SessionMetrics) {
        val json = Json.encodeToString(metrics)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$managementUrl/api/metrics")
            .post(body)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("ApiClient", "Metrics upload failed: ${response.code}")
                }
            }
        }
    }
}
```

**Remote Logger Implementation:**

```kotlin
object RemoteLogger {
    private val client = OkHttpClient()
    private val logServerUrl = "http://10.0.2.2:8765/log"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun log(
        level: String,
        message: String,
        label: String = "App",
        file: String = "",
        function: String = "",
        line: Int = 0,
        metadata: Map<String, Any> = emptyMap()
    ) {
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("level", level)
                    put("message", message)
                    put("label", label)
                    put("timestamp", System.currentTimeMillis())
                    put("file", file)
                    put("function", function)
                    put("line", line)
                    put("metadata", JSONObject(metadata))
                }

                val request = Request.Builder()
                    .url(logServerUrl)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                // Silently fail - don't want logging to crash the app
            }
        }
    }

    // Convenience methods
    fun trace(message: String, label: String = "App") = log("TRACE", message, label)
    fun debug(message: String, label: String = "App") = log("DEBUG", message, label)
    fun info(message: String, label: String = "App") = log("INFO", message, label)
    fun warning(message: String, label: String = "App") = log("WARNING", message, label)
    fun error(message: String, label: String = "App") = log("ERROR", message, label)
}
```

### 19.9 Error Handling

All API calls should handle these error scenarios:

| HTTP Code | Meaning | Action |
|-----------|---------|--------|
| 200 | Success | Process response |
| 400 | Bad Request | Check request format |
| 401 | Unauthorized | Re-authenticate |
| 404 | Not Found | Resource doesn't exist |
| 429 | Rate Limited | Retry with backoff |
| 500 | Server Error | Retry with backoff |
| Network Error | No connectivity | Queue for retry |

**Retrofit Error Handling:**

```kotlin
sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val code: Int, val message: String) : ApiResult<T>()
    data class NetworkError<T>(val exception: IOException) : ApiResult<T>()
}

suspend fun <T> safeApiCall(call: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(call())
    } catch (e: HttpException) {
        ApiResult.Error(e.code(), e.message())
    } catch (e: IOException) {
        ApiResult.NetworkError(e)
    }
}
```

---

## Appendix A: File Structure (Proposed)

```
app/
├── src/main/
│   ├── kotlin/com/unamentis/
│   │   ├── core/
│   │   │   ├── audio/
│   │   │   │   ├── AudioEngine.kt
│   │   │   │   └── AudioConfig.kt
│   │   │   ├── session/
│   │   │   │   ├── SessionManager.kt
│   │   │   │   └── SessionState.kt
│   │   │   ├── curriculum/
│   │   │   │   ├── CurriculumEngine.kt
│   │   │   │   └── ProgressTracker.kt
│   │   │   ├── routing/
│   │   │   │   ├── PatchPanelService.kt
│   │   │   │   └── RoutingTable.kt
│   │   │   ├── telemetry/
│   │   │   │   └── TelemetryEngine.kt
│   │   │   └── config/
│   │   │       ├── ApiKeyManager.kt
│   │   │       └── ServerConfig.kt
│   │   ├── services/
│   │   │   ├── stt/
│   │   │   │   ├── STTService.kt
│   │   │   │   ├── DeepgramSTTService.kt
│   │   │   │   ├── AssemblyAISTTService.kt
│   │   │   │   └── AndroidSTTService.kt
│   │   │   ├── tts/
│   │   │   │   ├── TTSService.kt
│   │   │   │   ├── ElevenLabsTTSService.kt
│   │   │   │   └── AndroidTTSService.kt
│   │   │   ├── llm/
│   │   │   │   ├── LLMService.kt
│   │   │   │   ├── OpenAILLMService.kt
│   │   │   │   └── AnthropicLLMService.kt
│   │   │   └── vad/
│   │   │       └── SileroVADService.kt
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── dao/
│   │   │   │   └── entities/
│   │   │   ├── remote/
│   │   │   │   ├── ApiClient.kt
│   │   │   │   └── dto/
│   │   │   └── repository/
│   │   │       ├── CurriculumRepository.kt
│   │   │       └── SessionRepository.kt
│   │   ├── ui/
│   │   │   ├── session/
│   │   │   │   ├── SessionScreen.kt
│   │   │   │   └── SessionViewModel.kt
│   │   │   ├── curriculum/
│   │   │   │   ├── CurriculumScreen.kt
│   │   │   │   └── CurriculumViewModel.kt
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── SettingsViewModel.kt
│   │   │   ├── analytics/
│   │   │   │   └── AnalyticsScreen.kt
│   │   │   ├── components/
│   │   │   │   ├── SlideToStopButton.kt
│   │   │   │   ├── TranscriptBubble.kt
│   │   │   │   └── SessionControlBar.kt
│   │   │   └── theme/
│   │   │       ├── Theme.kt
│   │   │       ├── Color.kt
│   │   │       └── Typography.kt
│   │   ├── di/
│   │   │   └── AppModule.kt
│   │   └── UnaMentisApp.kt
│   ├── cpp/
│   │   ├── audio_engine.cpp
│   │   └── llama_jni.cpp
│   └── res/
│       ├── values/
│       │   ├── strings.xml
│       │   └── themes.xml
│       └── drawable/
└── build.gradle.kts
```

---

## Appendix B: Dependencies

```kotlin
// build.gradle.kts (app)
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // TensorFlow Lite (VAD, embeddings)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // DataStore (preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Security (encrypted preferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
```

---

*Document created: January 2026*
*Last updated: January 2026*
