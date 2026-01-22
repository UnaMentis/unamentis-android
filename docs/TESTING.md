# UnaMentis Android - Testing Guide

## Testing Philosophy

**Core Principle**: Real implementations, no mocks (except for paid external APIs).

### Why No Mocks?

- **Real behavior**: Tests use actual implementations
- **Integration confidence**: Catches real issues
- **Simple**: No complex mocking infrastructure
- **Honest**: Tests reflect actual system behavior

### Test Doubles

Only use simple test doubles when necessary:
- Fake implementations for expensive operations (paid APIs)
- Stubs for unavoidable external dependencies (network, APIs)
- **Never** complex mocking frameworks like Mockito for internal services

---

## Test Structure

### Directory Layout

```
app/src/
├── test/kotlin/com/unamentis/          # Unit tests (JVM) - 100+ tests
│   ├── core/
│   │   ├── audio/AudioEngineTest.kt    # Audio capture, VAD
│   │   ├── session/SessionManagerTest.kt
│   │   ├── curriculum/CurriculumEngineTest.kt
│   │   └── device/DeviceCapabilityDetectorTest.kt
│   ├── data/
│   │   ├── model/                      # Data model tests
│   │   ├── remote/                     # API client tests
│   │   │   └── CertificatePinningTest.kt  # Certificate validation
│   │   └── local/                      # Database tests
│   ├── services/
│   │   ├── llm/PatchPanelServiceTest.kt
│   │   ├── stt/STTProviderRouterTest.kt
│   │   └── tts/TTSProviderRouterTest.kt
│   └── helpers/                        # Test utilities
│       ├── MockServices.kt             # Faithful mocks for paid APIs
│       └── TestDataFactory.kt          # Test data creation
│
├── androidTest/kotlin/com/unamentis/   # Instrumented tests - 172+ tests
│   ├── ui/                             # Compose UI tests (142 tests)
│   │   ├── session/SessionScreenTest.kt
│   │   ├── curriculum/CurriculumScreenTest.kt
│   │   ├── settings/SettingsScreenTest.kt
│   │   ├── analytics/AnalyticsScreenTest.kt
│   │   ├── history/HistoryScreenTest.kt
│   │   └── todo/TodoScreenTest.kt
│   ├── data/
│   │   ├── local/AppDatabaseTest.kt    # Room database tests
│   │   └── remote/CertificatePinningIntegrationTest.kt
│   ├── benchmark/                      # Performance benchmarks (14 tests)
│   │   ├── SessionBenchmarkTest.kt
│   │   └── MemoryProfilingTest.kt
│   └── NavigationFlowTest.kt           # Navigation tests (18 tests)
```

### Test Categories

**Unit Tests** (`test/`) - Single component, fast
- Target: <100ms per test
- Run on every save
- No Android framework dependencies
- Use JUnit 5 + Kotlin coroutines test

**Instrumented Tests** (`androidTest/`) - Multiple components, real device
- Target: <1s per test
- Requires emulator or device
- May use Room, Compose UI, network
- Run before commit

**Benchmark Tests** (`androidTest/benchmark/`) - Performance validation
- Target: Track regressions
- Measure latency, memory, CPU
- Run before release

---

## Running Tests

### Quick Tests (Unit only)

```bash
./scripts/test-quick.sh

# Or directly:
./gradlew test --console=plain
```

### All Tests (Unit + Instrumented)

```bash
./scripts/test-all.sh

# Or directly:
./gradlew test connectedAndroidTest --console=plain
```

### Specific Tests

```bash
# Single test class
./gradlew test --tests "com.unamentis.core.audio.AudioEngineTest"

# Single test method
./gradlew test --tests "com.unamentis.core.audio.AudioEngineTest.testVADDetection"

# Tests matching pattern
./gradlew test --tests "*SessionManager*"

# Instrumented tests for specific class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.unamentis.ui.session.SessionScreenTest
```

### With Coverage

```bash
./gradlew test jacocoTestReport
# Report at: app/build/reports/jacoco/
```

---

## Writing Tests

### Example: Unit Test

```kotlin
package com.unamentis.core.audio

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AudioEngineTest {

    private lateinit var audioEngine: AudioEngine
    private lateinit var testConfig: AudioEngineConfig

    @BeforeEach
    fun setUp() {
        testConfig = AudioEngineConfig(
            sampleRate = 16000,
            bufferSize = 512,
            enableVoiceProcessing = false  // Faster for tests
        )

        // Real AudioEngine, not a mock!
        audioEngine = AudioEngine(
            config = testConfig,
            vadService = SileroVAD(),  // Real VAD
            telemetry = TelemetryEngine()
        )
    }

    @Test
    fun `VAD detects speech in test audio`() = runTest {
        audioEngine.configure(testConfig)
        audioEngine.start()

        // Load test audio file
        val testAudio = loadTestAudio("speech-sample.wav")

        // Process through real pipeline
        var detectedSpeech = false
        for (buffer in testAudio.buffers) {
            val vadResult = audioEngine.processBuffer(buffer)
            if (vadResult.isSpeech) {
                detectedSpeech = true
                break
            }
        }

        assertTrue(detectedSpeech, "VAD should detect speech in test audio")
    }
}
```

### Example: Instrumented Test (Compose UI)

```kotlin
package com.unamentis.ui.session

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class SessionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sessionScreen_displaysStartButton() {
        composeTestRule.setContent {
            SessionScreen(
                viewModel = SessionViewModel(/* real dependencies */)
            )
        }

        composeTestRule
            .onNodeWithText("Start Session")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun sessionScreen_startsSessionOnButtonClick() {
        composeTestRule.setContent {
            SessionScreen(viewModel = viewModel)
        }

        composeTestRule
            .onNodeWithText("Start Session")
            .performClick()

        composeTestRule
            .onNodeWithText("Listening...")
            .assertIsDisplayed()
    }
}
```

### Example: Room Database Test

```kotlin
package com.unamentis.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var curriculumDao: CurriculumDao

    @Before
    fun setUp() {
        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        curriculumDao = database.curriculumDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveCurriculum() = runTest {
        val curriculum = Curriculum(
            id = 1,
            name = "Test Curriculum",
            description = "Test description"
        )

        curriculumDao.insert(curriculum)

        val retrieved = curriculumDao.getById(1)
        assertEquals("Test Curriculum", retrieved?.name)
    }
}
```

---

## Mock Services

### When Mocking is VALID

Mocks are only acceptable for:

1. **Paid third-party APIs** (LLM, STT, TTS, Embeddings)
   - These cost money per request
   - Would make CI/CD expensive
   - Rate limiting could break builds

2. **APIs requiring credentials we don't have**
   - Interim situation during development
   - Should be replaced with real tests once credentials exist

3. **Unreliable external services**
   - Services with unpredictable uptime
   - But only if local alternatives don't exist

### When Mocking is NOT ACCEPTABLE

Do NOT mock:

1. **Internal services** (TelemetryEngine, SessionManager, etc.)
   - Use the real implementation
   - These are free to run and deterministic

2. **Room database**
   - Use `Room.inMemoryDatabaseBuilder()`

3. **File system operations**
   - Use temp directories, clean up after

4. **Free external APIs**
   - If it doesn't cost money and doesn't require credentials, test against the real thing

5. **Local computations**
   - Cosine similarity, text chunking, etc. should always be tested with real implementations

### Mock Requirements (When Mocking is Necessary)

When you must mock, the mock must be **faithful and realistic**:

1. **Reproduce real API behavior**
   - Return data in the exact same format
   - Emit tokens/chunks at realistic intervals
   - Track input/output token counts accurately

2. **Simulate all error conditions the real API produces**
   - Rate limiting (with retry-after values)
   - Authentication failures
   - Network timeouts
   - Invalid request errors
   - Content filtering
   - Context length exceeded
   - Quota exceeded

3. **Validate inputs like the real API**
   - Check that requests are well-formed
   - Throw appropriate errors for malformed requests

4. **Match realistic performance characteristics**
   - Simulate TTFT (time to first token)
   - Simulate inter-token streaming delays
   - Optionally respect rate limits in stress tests

See `app/src/test/kotlin/com/unamentis/helpers/MockServices.kt` for reference implementations.

---

## On-Device LLM Testing

The OnDeviceLLMService uses llama.cpp via JNI for local inference. Unlike cloud LLM providers, this is a **real implementation that does not require mocking**.

### Testing Philosophy for OnDevice LLM

1. **Unit Tests** - Test Kotlin logic without loading models
   - Prompt formatting (Mistral vs ChatML templates)
   - Device capability detection
   - Model path resolution
   - Metrics calculation

2. **Instrumented Tests** - Test JNI integration with lightweight models
   - Native library loading
   - Model loading/unloading
   - Generation flow with small test prompts

### Example: Unit Test (No Model Required)

```kotlin
@Test
fun `formatPrompt uses Mistral template for Ministral model`() {
    val service = OnDeviceLLMService(context)
    service.currentModelPath = "/path/to/ministral-3b-instruct.gguf"

    val messages = listOf(
        LLMMessage("system", "You are helpful."),
        LLMMessage("user", "Hello")
    )

    val prompt = service.formatPrompt(messages)

    assertTrue(prompt.contains("[INST]"))
    assertTrue(prompt.contains("[/INST]"))
}
```

### Example: Instrumented Test (JNI Verification)

```kotlin
@Test
fun nativeLibrary_loadsSuccessfully() {
    // Verify llama_inference.so loads without crashing
    val service = OnDeviceLLMService(context)
    // If we get here, the native library loaded
    assertTrue(true)
}

@Test
fun modelLoading_failsGracefullyForMissingFile() = runBlocking {
    val service = OnDeviceLLMService(context)
    val result = service.loadModel(
        OnDeviceLLMService.ModelConfig("/nonexistent/model.gguf")
    )
    assertFalse(result)
}
```

### Model Files for Testing

For CI/CD, we do NOT include GGUF models in the repository (too large). Instead:

1. **Unit tests** - Don't require model files (test formatting, routing, metrics)
2. **Instrumented tests** - Test JNI loading and graceful failure handling
3. **Manual testing** - Download models via Settings > On-Device AI

### Performance Benchmarks

When testing on-device inference performance:

```kotlin
@Test
fun `TTFT under 2 seconds on flagship device`() = runBlocking {
    // Only run on devices with 8GB+ RAM
    val detector = DeviceCapabilityDetector(context)
    val capabilities = detector.detect()
    val ramGB = capabilities.totalRamMB / 1024
    assumeTrue(ramGB >= 8)

    val service = OnDeviceLLMService(context)
    service.loadModel(ModelConfig(modelPath))

    val startTime = System.currentTimeMillis()
    var firstTokenTime: Long? = null

    service.streamCompletion(messages, 0.7f, 50).collect { token ->
        if (firstTokenTime == null && token.content.isNotEmpty()) {
            firstTokenTime = System.currentTimeMillis() - startTime
        }
    }

    assertNotNull(firstTokenTime)
    assertTrue(firstTokenTime!! < 2000, "TTFT was ${firstTokenTime}ms")
}
```

---

## Test Data

### Test Fixtures

Located in `app/src/test/resources/`:

- `speech-sample.wav` - Clean speech for VAD testing
- `hello-world.wav` - "Hello world" for STT testing
- `noisy-speech.wav` - Background noise testing

### Test Configurations

```kotlin
object TestConfigurations {
    // Fast configuration for unit tests
    val unit = AudioEngineConfig(
        sampleRate = 16000,
        bufferSize = 512,
        enableVoiceProcessing = false,
        vadThreshold = 0.5f
    )

    // Real-world config for integration tests
    val integration = AudioEngineConfig(
        sampleRate = 48000,
        bufferSize = 1024,
        enableVoiceProcessing = true,
        vadThreshold = 0.6f
    )
}
```

---

## Performance Testing

### Latency Tests

```kotlin
@Test
fun `E2E latency under 500ms`() = runTest {
    val startTime = System.currentTimeMillis()

    // Process full turn
    sessionManager.processUserUtterance("What is AI?")

    val latency = System.currentTimeMillis() - startTime

    assertTrue(
        latency < 500,
        "E2E latency should be under 500ms, was ${latency}ms"
    )
}
```

### Memory Tests

```kotlin
@Test
fun `memory growth under 50MB over session`() = runTest {
    val runtime = Runtime.getRuntime()
    val initialMemory = runtime.totalMemory() - runtime.freeMemory()

    // Simulate 90-minute session
    repeat(90) {
        sessionManager.simulateOneMinute()
    }

    val finalMemory = runtime.totalMemory() - runtime.freeMemory()
    val growth = finalMemory - initialMemory

    assertTrue(
        growth < 50_000_000,
        "Memory growth should be under 50MB, was ${growth / 1_000_000}MB"
    )
}
```

---

## Continuous Integration

Tests run automatically on:
- Every push to `main` or `develop`
- Every pull request
- Nightly (full instrumented suite)

See `.github/workflows/android.yml` for configuration.

### CI Environment

- **Runner**: Ubuntu Latest / macOS (for emulator)
- **JDK**: 17 (Temurin)
- **Emulator**: Pixel 6, API 34
- **Timeout**: 30 minutes

---

## Best Practices

### DO

- Use real implementations
- Test actual behavior
- Keep tests fast (unit <100ms)
- Use descriptive test names (backtick style in Kotlin)
- Test edge cases
- Use test fixtures
- Run tests before committing
- Clean up resources in `@After`

### DON'T

- Use complex mocking frameworks for internal services
- Mock everything
- Write brittle tests
- Test implementation details
- Ignore flaky tests
- Skip setup/teardown
- Commit failing tests
- Leave test data behind

---

## Debugging Tests

### Print Debug Info

```kotlin
@Test
fun `test with debug info`() = runTest {
    println("Buffer size: ${buffer.size}")
    println("VAD threshold: ${config.vadThreshold}")
    // ... test code
}
```

### Run with Verbose Output

```bash
./gradlew test --info
./gradlew test --debug
```

### View Test Reports

```bash
# After running tests
open app/build/reports/tests/testDebugUnitTest/index.html
```

---

## Coverage Targets

| Category | Target | Current |
|----------|--------|---------|
| Unit Tests | >80% line coverage | 100+ tests |
| Integration Tests | Critical paths covered | 29+ tests |
| UI Tests | All screens, key flows | 142 tests (all 6 screens) |
| Navigation Tests | Tab switching, deep links | 18 tests |
| Benchmark Tests | Performance baselines | 14 tests |
| **Total** | **272+ tests** | ✅ Exceeds target |

---

## Troubleshooting

### Issue: Tests timeout

**Solution**: Increase timeout or check for deadlocks
```kotlin
@Test(timeout = 10000)  // 10 seconds
fun slowTest() { ... }

// Or with coroutines:
runTest(timeout = 10.seconds) { ... }
```

### Issue: Flaky tests

**Solution**: Add proper coroutine handling, don't use `Thread.sleep()`
```kotlin
// Bad
Thread.sleep(1000)

// Good
advanceUntilIdle()
// or
delay(1000)  // In runTest
```

### Issue: Tests fail in CI but pass locally

**Check**:
- Emulator differences (API level, screen size)
- Environment variables
- File paths (use context for resources)
- Network availability (mock external calls)
- Timing issues (use `runTest` properly)

### Issue: Room database tests fail

**Solution**: Use in-memory database
```kotlin
Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
    .allowMainThreadQueries()
    .build()
```

---

## Related Documents

- [ANDROID_STYLE_GUIDE.md](ANDROID_STYLE_GUIDE.md) - Coding standards
- [AGENTS.md](../AGENTS.md) - AI development guidelines
- [CONTRIBUTING.md](../CONTRIBUTING.md) - Contribution guidelines
- [Android Testing Documentation](https://developer.android.com/training/testing)
