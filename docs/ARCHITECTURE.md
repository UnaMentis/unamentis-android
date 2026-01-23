# UnaMentis Android Architecture

This document provides a comprehensive overview of the UnaMentis Android application architecture, design decisions, and implementation patterns.

## Table of Contents

- [Overview](#overview)
- [Architecture Patterns](#architecture-patterns)
- [Layer Architecture](#layer-architecture)
- [Voice AI Pipeline](#voice-ai-pipeline)
- [Provider System](#provider-system)
- [Data Flow](#data-flow)
- [Key Components](#key-components)
- [Native Integration](#native-integration)
- [Dependency Injection](#dependency-injection)
- [Threading Model](#threading-model)
- [Error Handling](#error-handling)
- [Performance Considerations](#performance-considerations)

---

## Overview

UnaMentis Android is built using modern Android development practices with a focus on:

- **Clean Architecture** — Separation of concerns with distinct layers
- **Reactive Programming** — Kotlin Coroutines and Flow for async operations
- **Declarative UI** — Jetpack Compose with Material Design 3
- **Testability** — Dependency injection and interface-based design
- **Performance** — Native audio processing with Oboe for low latency

### Technology Stack

| Category | Technology | Purpose |
|----------|------------|---------|
| Language | Kotlin 2.0 | Primary development language |
| UI Framework | Jetpack Compose | Declarative UI toolkit |
| DI Framework | Hilt | Dependency injection |
| Async | Coroutines/Flow | Reactive programming |
| Database | Room | Local persistence |
| Networking | OkHttp/Retrofit | HTTP/WebSocket clients |
| Audio | Oboe (C++/JNI) | Low-latency audio I/O |
| ML Runtime | ONNX/TFLite | On-device inference |

---

## Architecture Patterns

### MVVM with Clean Architecture

The application follows MVVM (Model-View-ViewModel) pattern with Clean Architecture principles:

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │  Composable │  │  Composable │  │  Composable │             │
│  │   Screens   │  │  Components │  │   Dialogs   │             │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │
│         │                │                │                     │
│         └────────────────┼────────────────┘                     │
│                          ▼                                      │
│                  ┌───────────────┐                              │
│                  │   ViewModels  │  ← Hilt injection            │
│                  │  (StateFlow)  │                              │
│                  └───────┬───────┘                              │
├──────────────────────────┼──────────────────────────────────────┤
│                   Domain Layer                                   │
│                          ▼                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │  Use Cases  │  │  Services   │  │  Managers   │             │
│  │  (optional) │  │             │  │             │             │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │
│         │                │                │                     │
│         └────────────────┼────────────────┘                     │
│                          ▼                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                    Data Layer                                    │
│                          ▼                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ Repositories│  │ Data Sources│  │   Models    │             │
│  │             │  │ (Room, API) │  │             │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

### Key Principles

1. **Unidirectional Data Flow** — State flows down, events flow up
2. **Single Source of Truth** — Repository pattern for data management
3. **Separation of Concerns** — Each layer has distinct responsibilities
4. **Dependency Inversion** — Depend on abstractions, not implementations

---

## Layer Architecture

### UI Layer

The UI layer consists of Compose screens, components, and ViewModels.

```kotlin
// Screen Composable
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    SessionContent(
        state = state,
        onStartSession = viewModel::startSession,
        onStopSession = viewModel::stopSession,
        onMuteToggle = viewModel::toggleMute
    )
}

// ViewModel
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val telemetryEngine: TelemetryEngine
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    fun startSession(topic: Topic? = null) {
        viewModelScope.launch {
            sessionManager.startSession(topic)
        }
    }
}

// UI State
data class SessionUiState(
    val sessionState: SessionState = SessionState.IDLE,
    val transcript: List<TranscriptEntry> = emptyList(),
    val isMuted: Boolean = false,
    val isPaused: Boolean = false,
    val currentVisualAsset: VisualAsset? = null,
    val metrics: SessionMetrics? = null
)
```

### Domain Layer

The domain layer contains business logic, services, and managers.

```kotlin
// Session Manager - Core orchestration
class SessionManager(
    private val audioEngine: AudioEngine,
    private val vadService: VADService,
    private val sttService: STTService,
    private val llmService: LLMService,
    private val ttsService: TTSService,
    private val telemetryEngine: TelemetryEngine
) {
    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcript: StateFlow<List<TranscriptEntry>> = _transcript.asStateFlow()

    suspend fun startSession(topic: Topic? = null) {
        _state.value = SessionState.LISTENING
        startAudioCapture()
        startVADMonitoring()
    }
}

// Telemetry Engine - Metrics collection
class TelemetryEngine {
    private val latencies = mutableMapOf<LatencyType, MutableList<Long>>()
    private val costs = mutableMapOf<String, Double>()

    fun recordLatency(type: LatencyType, durationMs: Long) {
        latencies.getOrPut(type) { mutableListOf() }.add(durationMs)
    }

    fun recordCost(provider: String, costUsd: Double) {
        costs[provider] = (costs[provider] ?: 0.0) + costUsd
    }

    fun getSessionMetrics(): SessionMetrics {
        return SessionMetrics(
            sttMedianLatencyMs = calculateMedian(latencies[LatencyType.STT]),
            llmMedianLatencyMs = calculateMedian(latencies[LatencyType.LLM]),
            // ... other metrics
        )
    }
}
```

### Data Layer

The data layer handles persistence, API communication, and data models.

```kotlin
// Repository
class SessionRepository(
    private val sessionDao: SessionDao,
    private val transcriptDao: TranscriptDao
) {
    suspend fun saveSession(session: Session): Long {
        return sessionDao.insert(session.toEntity())
    }

    fun getSessions(): Flow<List<Session>> {
        return sessionDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSessionWithTranscript(sessionId: Long): SessionWithTranscript {
        val session = sessionDao.getById(sessionId)
        val entries = transcriptDao.getBySessionId(sessionId)
        return SessionWithTranscript(session.toDomain(), entries.map { it.toDomain() })
    }
}

// Room Database
@Database(
    entities = [
        SessionEntity::class,
        TranscriptEntryEntity::class,
        CurriculumEntity::class,
        TopicEntity::class,
        TopicProgressEntity::class,
        TodoEntity::class
    ],
    version = 2
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun curriculumDao(): CurriculumDao
    abstract fun topicProgressDao(): TopicProgressDao
    abstract fun todoDao(): TodoDao
}

// API Client
class ManagementApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val baseUrl = "http://10.0.2.2:8766"

    suspend fun getCurricula(): List<CurriculumSummary> {
        val request = Request.Builder()
            .url("$baseUrl/api/curricula")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.message)
                }
                json.decodeFromString(response.body!!.string())
            }
        }
    }
}
```

---

## Voice AI Pipeline

### Pipeline Overview

The voice AI pipeline processes audio through multiple stages:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Voice AI Pipeline                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐     │
│  │  Audio   │   │   VAD    │   │   STT    │   │   LLM    │     │
│  │ Capture  │──▶│  Silero  │──▶│ Provider │──▶│ Provider │     │
│  │  (Oboe)  │   │  (ONNX)  │   │          │   │          │     │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘     │
│                                                      │          │
│  ┌──────────┐   ┌──────────┐                        │          │
│  │  Audio   │   │   TTS    │◀───────────────────────┘          │
│  │ Playback │◀──│ Provider │                                    │
│  │  (Oboe)  │   │          │                                    │
│  └──────────┘   └──────────┘                                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### State Machine

The SessionManager implements a state machine for managing the conversation:

```kotlin
enum class SessionState {
    IDLE,              // No active session
    LISTENING,         // Waiting for user speech
    USER_SPEAKING,     // User is speaking (VAD active)
    PROCESSING,        // STT processing utterance
    AI_THINKING,       // LLM generating response
    AI_SPEAKING,       // TTS playing response
    INTERRUPTED,       // User interrupted AI
    PAUSED,            // Session paused
    ERROR              // Error state
}

// State transitions
sealed class SessionEvent {
    object Start : SessionEvent()
    object Stop : SessionEvent()
    object SpeechDetected : SessionEvent()
    object SilenceDetected : SessionEvent()
    data class TranscriptReceived(val text: String) : SessionEvent()
    data class ResponseGenerated(val text: String) : SessionEvent()
    object PlaybackComplete : SessionEvent()
    object Interrupt : SessionEvent()
    object Pause : SessionEvent()
    object Resume : SessionEvent()
    data class Error(val exception: Throwable) : SessionEvent()
}
```

### Latency Budget

Each stage has a target latency to achieve sub-500ms E2E response:

| Stage | Target (Median) | Target (P99) |
|-------|----------------|--------------|
| Audio Capture → VAD | <10ms | <20ms |
| VAD Detection | <5ms | <10ms |
| STT Transcription | <300ms | <1000ms |
| LLM First Token | <200ms | <500ms |
| TTS TTFB | <200ms | <400ms |
| **End-to-End** | **<500ms** | **<1000ms** |

---

## Provider System

### Provider Architecture

The provider system uses interfaces for abstraction and dependency injection for flexibility:

```kotlin
// STT Provider Interface
interface STTService {
    val isAvailable: Boolean
    val name: String

    suspend fun transcribe(audioData: ByteArray): STTResult
    fun streamTranscribe(audioFlow: Flow<ByteArray>): Flow<STTResult>
    suspend fun close()
}

// Provider Implementation
class DeepgramSTTService(
    private val apiKey: String,
    private val okHttpClient: OkHttpClient
) : STTService {

    override val isAvailable: Boolean = true
    override val name: String = "Deepgram Nova-3"

    private var webSocket: WebSocket? = null

    override fun streamTranscribe(audioFlow: Flow<ByteArray>): Flow<STTResult> = callbackFlow {
        val request = Request.Builder()
            .url("wss://api.deepgram.com/v1/listen?model=nova-3&smart_format=true")
            .addHeader("Authorization", "Token $apiKey")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val response = json.decodeFromString<DeepgramResponse>(text)
                trySend(response.toSTTResult())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })

        audioFlow.collect { audio ->
            webSocket?.send(ByteString.of(*audio))
        }

        awaitClose { webSocket?.close(1000, "Stream ended") }
    }
}
```

### PatchPanel Routing

The PatchPanel service intelligently routes requests to the optimal provider:

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
            rule.conditions.all { condition ->
                when (condition) {
                    is TaskTypeCondition -> condition.matches(taskType)
                    is CostThresholdCondition -> condition.matches(context.currentCost)
                    is LatencyCondition -> condition.matches(context.recentLatency)
                    is AvailabilityCondition -> condition.matches(endpoints)
                }
            }
        }

        return matchingRules
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { rule ->
                endpoints.find { it.name == rule.endpoint && it.isAvailable }
            }
            ?: endpoints.first { it.isAvailable }
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

## Data Flow

### Reactive Streams

Data flows through the application using Kotlin Flow:

```
┌────────────────────────────────────────────────────────────────┐
│                      Reactive Data Flow                         │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  AudioEngine ──────────────────────────────────────────────┐   │
│       │                                                     │   │
│       ▼                                                     │   │
│  Flow<ByteArray>                                           │   │
│       │                                                     │   │
│       ├──▶ VADService ──▶ Flow<VADResult>                  │   │
│       │                         │                           │   │
│       ▼                         ▼                           │   │
│  STTService ◀──── speech events │                          │   │
│       │                                                     │   │
│       ▼                                                     │   │
│  Flow<STTResult>                                           │   │
│       │                                                     │   │
│       ▼                                                     │   │
│  SessionManager                                            │   │
│       │                                                     │   │
│       ▼                                                     │   │
│  StateFlow<SessionState>  ──────────────────────────────┐  │   │
│  StateFlow<List<TranscriptEntry>>                       │  │   │
│       │                                                  │  │   │
│       ▼                                                  ▼  │   │
│  SessionViewModel                                    TTS  │   │
│       │                                               │   │   │
│       ▼                                               ▼   │   │
│  StateFlow<SessionUiState> ──────▶ SessionScreen ◀── Audio │   │
│                                                             │   │
└─────────────────────────────────────────────────────────────┘   │
```

### State Management Pattern

```kotlin
// ViewModel exposes StateFlow
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    // Combine multiple flows into UI state
    val state: StateFlow<SessionUiState> = combine(
        sessionManager.state,
        sessionManager.transcript,
        sessionManager.metrics,
        sessionManager.visualAsset
    ) { state, transcript, metrics, asset ->
        SessionUiState(
            sessionState = state,
            transcript = transcript,
            metrics = metrics,
            currentVisualAsset = asset
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SessionUiState()
    )
}

// Composable observes state
@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    when (state.sessionState) {
        SessionState.IDLE -> IdleContent(onStart = viewModel::startSession)
        SessionState.LISTENING -> ListeningContent(state)
        SessionState.AI_SPEAKING -> SpeakingContent(state)
        // ... other states
    }
}
```

---

## Key Components

### AudioEngine

The AudioEngine wraps the native Oboe library for low-latency audio:

```kotlin
class AudioEngine(private val context: Context) {

    private external fun nativeCreate(): Long
    private external fun nativeStart(ptr: Long)
    private external fun nativeStop(ptr: Long)
    private external fun nativeDestroy(ptr: Long)
    private external fun nativeSetCallback(ptr: Long, callback: AudioCallback)
    private external fun nativeWriteAudio(ptr: Long, data: FloatArray)
    private external fun nativeGetInputLevel(ptr: Long): Float

    private var nativePtr: Long = 0
    private val _audioData = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)
    val audioData: Flow<FloatArray> = _audioData.asSharedFlow()

    fun start() {
        nativePtr = nativeCreate()
        nativeSetCallback(nativePtr, object : AudioCallback {
            override fun onAudioReady(data: FloatArray) {
                _audioData.tryEmit(data)
            }
        })
        nativeStart(nativePtr)
    }

    fun playAudio(data: FloatArray) {
        nativeWriteAudio(nativePtr, data)
    }

    fun getInputLevel(): Float = nativeGetInputLevel(nativePtr)

    fun stop() {
        nativeStop(nativePtr)
        nativeDestroy(nativePtr)
        nativePtr = 0
    }

    companion object {
        init {
            System.loadLibrary("audio_engine")
        }
    }
}
```

### SileroVADService

Voice Activity Detection using Silero model with ONNX Runtime:

```kotlin
class SileroVADService(
    private val context: Context,
    private val threshold: Float = 0.5f
) : VADService {

    private lateinit var ortSession: OrtSession
    private val ortEnvironment = OrtEnvironment.getEnvironment()

    // Model state (Silero VAD is stateful)
    private var h = FloatArray(128)
    private var c = FloatArray(128)
    private var lastSr = 16000L

    fun initialize() {
        val modelBytes = context.assets.open("silero_vad.onnx").readBytes()
        ortSession = ortEnvironment.createSession(modelBytes)
    }

    override fun processAudio(samples: FloatArray): VADResult {
        // Prepare inputs
        val inputTensor = OnnxTensor.createTensor(
            ortEnvironment,
            FloatBuffer.wrap(samples),
            longArrayOf(1, samples.size.toLong())
        )
        val hTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(h), longArrayOf(2, 1, 64))
        val cTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(c), longArrayOf(2, 1, 64))
        val srTensor = OnnxTensor.createTensor(ortEnvironment, longArrayOf(lastSr), longArrayOf(1))

        // Run inference
        val inputs = mapOf(
            "input" to inputTensor,
            "h" to hTensor,
            "c" to cTensor,
            "sr" to srTensor
        )
        val results = ortSession.run(inputs)

        // Extract outputs
        val output = (results[0].value as Array<*>)[0] as FloatArray
        val probability = output[0]

        // Update state
        h = (results[1].value as Array<*>).flatMap { (it as FloatArray).toList() }.toFloatArray()
        c = (results[2].value as Array<*>).flatMap { (it as FloatArray).toList() }.toFloatArray()

        return VADResult(
            isSpeech = probability > threshold,
            confidence = probability
        )
    }
}
```

### CurriculumEngine

Manages curriculum content and learning progress:

```kotlin
class CurriculumEngine(
    private val curriculumRepository: CurriculumRepository,
    private val progressRepository: ProgressRepository
) {
    private val _currentTopic = MutableStateFlow<Topic?>(null)
    val currentTopic: StateFlow<Topic?> = _currentTopic.asStateFlow()

    private var currentSegmentIndex = 0

    suspend fun loadTopic(topicId: String) {
        val topic = curriculumRepository.getTopic(topicId)
        val progress = progressRepository.getProgress(topicId)

        _currentTopic.value = topic
        currentSegmentIndex = progress?.lastSegmentIndex ?: 0
    }

    fun getCurrentSegment(): TranscriptSegment? {
        return _currentTopic.value?.transcript?.getOrNull(currentSegmentIndex)
    }

    fun advanceToNextSegment(): TranscriptSegment? {
        val topic = _currentTopic.value ?: return null
        if (currentSegmentIndex < topic.transcript.lastIndex) {
            currentSegmentIndex++
            return getCurrentSegment()
        }
        return null
    }

    suspend fun saveProgress() {
        val topic = _currentTopic.value ?: return
        progressRepository.saveProgress(
            TopicProgress(
                topicId = topic.id,
                lastSegmentIndex = currentSegmentIndex,
                completedSegments = (0 until currentSegmentIndex).map { topic.transcript[it].id },
                timeSpentSeconds = calculateTimeSpent(),
                masteryLevel = calculateMastery()
            )
        )
    }
}
```

---

## Native Integration

### JNI Bridge

The native audio engine is accessed through JNI:

```cpp
// audio_engine_jni.cpp
#include <jni.h>
#include <oboe/Oboe.h>
#include "audio_engine.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeCreate(JNIEnv *env, jobject thiz) {
    auto *engine = new AudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeStart(JNIEnv *env, jobject thiz, jlong ptr) {
    auto *engine = reinterpret_cast<AudioEngine*>(ptr);
    engine->start();
}

JNIEXPORT void JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeSetCallback(
    JNIEnv *env, jobject thiz, jlong ptr, jobject callback
) {
    auto *engine = reinterpret_cast<AudioEngine*>(ptr);

    // Store global reference to callback
    JavaVM *jvm;
    env->GetJavaVM(&jvm);
    jobject globalCallback = env->NewGlobalRef(callback);

    engine->setAudioCallback([jvm, globalCallback](float *data, int numFrames) {
        JNIEnv *callbackEnv;
        jvm->AttachCurrentThread(&callbackEnv, nullptr);

        jclass callbackClass = callbackEnv->GetObjectClass(globalCallback);
        jmethodID methodId = callbackEnv->GetMethodID(
            callbackClass, "onAudioReady", "([F)V"
        );

        jfloatArray jData = callbackEnv->NewFloatArray(numFrames);
        callbackEnv->SetFloatArrayRegion(jData, 0, numFrames, data);
        callbackEnv->CallVoidMethod(globalCallback, methodId, jData);

        callbackEnv->DeleteLocalRef(jData);
    });
}

}
```

### CMake Configuration

```cmake
# CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project(audio_engine)

find_package(oboe REQUIRED CONFIG)

add_library(audio_engine SHARED
    audio_engine.cpp
    audio_engine_jni.cpp
)

target_link_libraries(audio_engine
    oboe::oboe
    android
    log
)
```

---

## Dependency Injection

### DataStore Singleton Pattern

To prevent "multiple DataStores active for same file" errors (especially in instrumented tests), DataStore instances are provided as singletons through Hilt:

```kotlin
// ProviderDataStore.kt - Singleton holder
object ProviderDataStore {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "provider_config",
    )

    fun getInstance(context: Context): DataStore<Preferences> = context.dataStore
}

// ProviderModule.kt - Hilt provider
@Provides
@Singleton
fun provideProviderDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
    return ProviderDataStore.getInstance(context)
}

// ProviderConfig.kt - Constructor injection
class ProviderConfig(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = ProviderDataStore.getInstance(context),
)
```

This pattern ensures only one DataStore instance exists per file, even when multiple components request it.

### Hilt Modules

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "unamentis.db"
        ).build()
    }

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideCurriculumDao(db: AppDatabase): CurriculumDao = db.curriculumDao()
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAudioEngine(@ApplicationContext context: Context): AudioEngine {
        return AudioEngine(context)
    }

    @Provides
    @Singleton
    fun provideVADService(@ApplicationContext context: Context): VADService {
        return SileroVADService(context).also { it.initialize() }
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        audioEngine: AudioEngine,
        vadService: VADService,
        sttService: STTService,
        llmService: LLMService,
        ttsService: TTSService,
        telemetryEngine: TelemetryEngine
    ): SessionManager {
        return SessionManager(
            audioEngine, vadService, sttService,
            llmService, ttsService, telemetryEngine
        )
    }
}
```

---

## Threading Model

### Coroutine Dispatchers

```kotlin
// CPU-bound work (LLM processing, VAD inference)
withContext(Dispatchers.Default) {
    vadService.processAudio(samples)
}

// IO-bound work (network, database)
withContext(Dispatchers.IO) {
    curriculumRepository.downloadCurriculum(id)
}

// UI updates (StateFlow emissions collected on Main)
viewModelScope.launch {
    sessionManager.state.collect { state ->
        _uiState.update { it.copy(sessionState = state) }
    }
}

// Native audio (runs on native audio thread, callbacks bridged to Kotlin)
audioEngine.audioData.collect { samples ->
    // Process audio on caller's dispatcher
}
```

### Thread Safety

```kotlin
class SessionManager {
    // Mutex for state transitions
    private val stateMutex = Mutex()

    suspend fun startSession() {
        stateMutex.withLock {
            if (_state.value != SessionState.IDLE) {
                throw IllegalStateException("Session already active")
            }
            _state.value = SessionState.LISTENING
        }
        // Continue with non-critical initialization
    }

    // Atomic state updates
    private val _conversationHistory = MutableStateFlow<List<Message>>(emptyList())

    fun addMessage(message: Message) {
        _conversationHistory.update { it + message }
    }
}
```

---

## Error Handling

### Error Types

```kotlin
sealed class AppError : Exception() {
    // Network errors
    sealed class Network : AppError() {
        data class ConnectionFailed(override val message: String) : Network()
        object Timeout : Network()
        data class HttpError(val code: Int, override val message: String?) : Network()
    }

    // Provider errors
    sealed class Provider : AppError() {
        object AuthenticationFailed : Provider()
        data class RateLimited(val retryAfterSeconds: Int?) : Provider()
        object QuotaExceeded : Provider()
        data class ServiceUnavailable(val provider: String) : Provider()
    }

    // Audio errors
    sealed class Audio : AppError() {
        object MicrophonePermissionDenied : Audio()
        object AudioEngineFailure : Audio()
        object PlaybackFailure : Audio()
    }

    // Session errors
    sealed class Session : AppError() {
        object AlreadyActive : Session()
        object NotActive : Session()
        data class StateMachineViolation(val from: SessionState, val event: SessionEvent) : Session()
    }
}
```

### Error Handling Strategy

```kotlin
class SessionManager {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 16)
    val errors: Flow<AppError> = _errors.asSharedFlow()

    private suspend fun processUtterance(text: String) {
        try {
            val response = llmService.generateResponse(conversationHistory + Message.User(text))
            // Process response
        } catch (e: AppError.Provider.RateLimited) {
            // Retry with exponential backoff
            delay(e.retryAfterSeconds?.times(1000L) ?: 5000L)
            processUtterance(text)
        } catch (e: AppError.Provider.ServiceUnavailable) {
            // Failover to backup provider
            val backup = patchPanel.getBackupEndpoint(e.provider)
            processWithProvider(text, backup)
        } catch (e: AppError) {
            _errors.emit(e)
            _state.value = SessionState.ERROR
        }
    }
}

// ViewModel error handling
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            sessionManager.errors.collect { error ->
                _uiState.update {
                    it.copy(error = error.toUserMessage())
                }
            }
        }
    }
}
```

---

## Performance Considerations

### Memory Management

```kotlin
// Use weak references for long-lived callbacks
class AudioCallback(listener: AudioListener) {
    private val listenerRef = WeakReference(listener)

    fun onAudioReady(data: FloatArray) {
        listenerRef.get()?.onAudio(data)
    }
}

// Limit collection sizes
class TelemetryEngine {
    private val latencies = object : LinkedHashMap<Long, Long>() {
        override fun removeEldestEntry(eldest: Map.Entry<Long, Long>): Boolean {
            return size > MAX_LATENCY_SAMPLES
        }
    }

    companion object {
        private const val MAX_LATENCY_SAMPLES = 1000
    }
}

// Release resources in lifecycle callbacks
class SessionManager : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        audioEngine.stop()
        vadService.close()
        coroutineScope.cancel()
    }
}
```

### Battery Optimization

```kotlin
// Reduce audio processing when not actively speaking
class AudioEngine {
    fun setLowPowerMode(enabled: Boolean) {
        if (enabled) {
            // Reduce sample rate, increase buffer size
            reconfigureStream(
                sampleRate = 8000,
                framesPerBuffer = 1024
            )
        } else {
            // Full quality
            reconfigureStream(
                sampleRate = 16000,
                framesPerBuffer = 512
            )
        }
    }
}

// Thermal monitoring
class ThermalMonitor(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun observeThermalState(): Flow<ThermalState> = callbackFlow {
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            trySend(status.toThermalState())
        }
        powerManager.addThermalStatusListener(Executors.newSingleThreadExecutor(), listener)
        awaitClose { powerManager.removeThermalStatusListener(listener) }
    }
}
```

### Profiling

```kotlin
// Built-in performance tracking
class TelemetryEngine {
    inline fun <T> measureLatency(type: LatencyType, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            recordLatency(type, durationMs)
        }
    }
}

// Usage
val result = telemetryEngine.measureLatency(LatencyType.STT) {
    sttService.transcribe(audioData)
}
```

---

## Server Synchronization

### Architecture Overview

UnaMentis uses a local-first architecture with server synchronization:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Local-First Sync Architecture                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │   Local DB   │────▶│ Sync Engine  │────▶│   Server     │    │
│  │    (Room)    │◀────│              │◀────│   (REST)     │    │
│  └──────────────┘     └──────────────┘     └──────────────┘    │
│         │                    │                    │             │
│         ▼                    ▼                    ▼             │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │   UI Layer   │     │  WebSocket   │     │  Auth Token  │    │
│  │  (Compose)   │     │   Client     │     │   Storage    │    │
│  └──────────────┘     └──────────────┘     └──────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Components

```kotlin
// Secure Token Storage
class SecureTokenStorage(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "auth_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuthToken(token: String) { ... }
    fun getAuthToken(): String? { ... }
    fun clearTokens() { ... }
}

// WebSocket Client for real-time events
class WebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val tokenStorage: SecureTokenStorage
) {
    fun connect(url: String): Flow<WebSocketMessage>
    fun send(message: WebSocketMessage)
    fun disconnect()
}

// Auth Repository
class AuthRepository(
    private val apiClient: ApiClient,
    private val tokenStorage: SecureTokenStorage
) {
    suspend fun login(credentials: Credentials): Result<AuthToken>
    suspend fun logout()
    fun isAuthenticated(): Boolean
    fun getAuthToken(): String?
}
```

### Sync Flow

1. **Local Operations First** — All writes go to local Room database immediately
2. **Background Sync** — Changes are queued and synced to server when connected
3. **Conflict Resolution** — Server timestamp wins for conflicting changes
4. **Offline Support** — App remains fully functional without network

---

## Further Reading

- [DEV_ENVIRONMENT.md](DEV_ENVIRONMENT.md) — Development environment setup
- [TESTING.md](TESTING.md) — Testing strategies and best practices
- [MCP_SETUP.md](MCP_SETUP.md) — MCP server configuration
- [ANDROID_PORT_SPECIFICATION.md](../ANDROID_PORT_SPECIFICATION.md) — Complete feature specification
