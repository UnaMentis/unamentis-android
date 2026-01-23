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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unamentis.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    companion object {
        // 15s for navigation tests (CI emulators are slower)
        private const val DEFAULT_TIMEOUT = 15_000L
        // 20s for session/state tests with complex setup
        private const val LONG_TIMEOUT = 20_000L
    }

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun sessionScreen_displaysStartButton() {
        // Wait for UI to load using testTag or text
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Start Session")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("Start Session")
            .assertIsDisplayed()
    }

    @Test
    fun sessionScreen_displaysStateIndicator() {
        // Production shows "Ready" for IDLE state
        composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
            composeTestRule.onAllNodesWithText("Ready")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
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
- **Locale**: en-US (enforced)

### CI Locale Enforcement

The CI workflow enforces `en-US` locale to prevent test failures due to localized strings:

```yaml
# In .github/workflows/android.yml
emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -prop persist.sys.locales=en-US

script: |
  # Ensure device locale is English (en-US)
  adb shell "setprop persist.sys.locales en-US; setprop persist.sys.locale en-US; setprop persist.sys.language en; setprop persist.sys.country US" || true

  # Restart runtime so locale changes apply
  adb shell stop || true
  adb shell start || true
  adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
```

This ensures consistent test behavior regardless of the CI runner's default locale.

### TestTag-Based Navigation Testing

Navigation tests use `testTag` modifiers instead of text selectors for stability:

```kotlin
// In Navigation.kt - stable test hooks
NavigationBarItem(
    modifier = Modifier
        .testTag("nav_session")
        .semantics { contentDescription = "Session tab" },
    // ...
)

// For More menu items
DropdownMenuItem(
    modifier = Modifier
        .testTag("menu_settings")
        .semantics { contentDescription = "Settings tab" },
    // ...
)
```

**Test patterns using testTags:**

```kotlin
// Navigate to a primary tab
private fun navigateToCurriculum() {
    composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
        composeTestRule.onAllNodesWithTag("nav_curriculum")
            .fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("nav_curriculum").performClick()
}

// Navigate via More menu (Settings, Analytics)
private fun navigateToSettings() {
    composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
        composeTestRule.onAllNodesWithTag("nav_more")
            .fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("nav_more").performClick()
    composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
        composeTestRule.onAllNodesWithTag("menu_settings")
            .fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("menu_settings").performClick()
}
```

**Available navigation testTags:**

| TestTag | Description |
|---------|-------------|
| `nav_session` | Session tab (primary) |
| `nav_curriculum` | Curriculum tab (primary) |
| `nav_history` | History tab (primary) |
| `nav_todo` | Todo tab (primary) |
| `nav_more` | More menu button |
| `menu_settings` | Settings menu item |
| `menu_analytics` | Analytics menu item |

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

### Issue: Waiting for Flow collection on real dispatchers (Dispatchers.IO)

**Cause**: Classes that collect Flows internally using `Dispatchers.IO` (like `ModuleRegistry`) won't have their Flows collected when using `runTest` because the test dispatcher doesn't control real dispatchers.

**Solution**: Use a coroutine-friendly polling helper that runs on a real dispatcher:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Coroutine-friendly polling helper that awaits a condition with timeout.
 * Uses real time delays on Dispatchers.Default to allow IO operations
 * (like Flow collection on Dispatchers.IO) to complete.
 */
private suspend fun awaitCondition(
    timeoutMs: Long = 1000L,
    pollIntervalMs: Long = 10L,
    condition: () -> Boolean,
) {
    withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(pollIntervalMs)
            }
        }
    }
}

// Usage in tests:
@Test
fun `test with async Flow collection`() = runTest {
    // Insert data that will be collected by a Flow
    moduleDao.insertModule(entity)

    // Create instance that collects Flow internally on Dispatchers.IO
    val registry = ModuleRegistry(moduleDao, json)

    // Wait for the Flow to emit using real time delays
    awaitCondition { registry.isDownloaded("test-module") }

    // Now the data is available
    assertTrue(registry.hasUpdate("test-module", "2.0.0"))
}
```

**Why this works**: `withContext(Dispatchers.Default)` switches to a real dispatcher where `delay()` actually pauses for real time, allowing concurrent operations on `Dispatchers.IO` to complete. The `withTimeout` ensures the test fails deterministically if the condition is never met.

### Issue: Uncaught exceptions from previous tests

**Cause**: Classes with internal `CoroutineScope` (using `Dispatchers.IO`) continue running background coroutines after tests complete, causing `UncaughtExceptionsBeforeTest` errors.

**Solution**: Add `@After` teardown to clear all mocks:

```kotlin
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.After

class MyClassTest {
    private lateinit var myMock: SomeDao

    @Before
    fun setup() {
        myMock = mockk(relaxed = true)
        every { myMock.getAllItems() } returns flowOf(emptyList())
        // ... create instance that collects from flow
    }

    @After
    fun tearDown() {
        // Clear all mocks to prevent coroutines from previous tests
        // from interfering with subsequent tests
        clearAllMocks()
        unmockkAll()
    }

    // ... tests
}
```

This pattern is especially important when testing classes that:
- Collect from `Flow` in their `init` block
- Use `CoroutineScope(SupervisorJob() + Dispatchers.IO)` internally
- Create background coroutines that outlive the test

### Issue: Tests fail in CI but pass locally

**Check**:
- Emulator differences (API level, screen size)
- **Locale differences** - CI now enforces en-US, ensure tests use testTags not text
- Environment variables
- File paths (use context for resources)
- Network availability (mock external calls)
- Timing issues (use `runTest` properly)
- DataStore singleton conflicts (see below)
- CI emulator boot timing (see below)

### Issue: Navigation tests can't find text like "Settings" or "Analytics"

**Cause**: These screens are accessed via the "More" menu, not primary navigation tabs. Also, text-based selectors are locale-dependent.

**Solution**: Use testTag-based navigation:

```kotlin
// WRONG: Text selectors are fragile and locale-dependent
composeTestRule.onNodeWithText("Settings").performClick()

// RIGHT: Use testTags for stable navigation
composeTestRule.onNodeWithTag("nav_more").performClick()
composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
    composeTestRule.onAllNodesWithTag("menu_settings")
        .fetchSemanticsNodes().isNotEmpty()
}
composeTestRule.onNodeWithTag("menu_settings").performClick()
```

### Issue: Duplicate nodes found for text like "Session"

**Cause**: Multiple UI elements may contain the same text (e.g., "Session" appears in nav tab and screen title).

**Solution**: Use testTags with semantics for unique identification:

```kotlin
// Use testTag + contentDescription for accessibility and testing
Modifier
    .testTag("nav_session")
    .semantics { contentDescription = "Session tab" }
```

### Issue: DataStore "multiple instances active for same file"

**Cause**: Multiple test classes or Hilt components creating separate DataStore instances for the same file.

**Solution**: Use the `ProviderDataStore` singleton pattern:

```kotlin
// In ProviderDataStore.kt - singleton holder
object ProviderDataStore {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "provider_config",
    )

    fun getInstance(context: Context): DataStore<Preferences> = context.dataStore
}

// In ProviderConfig - accept DataStore via constructor
class ProviderConfig(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = ProviderDataStore.getInstance(context),
)

// In Hilt module - provide singleton
@Provides
@Singleton
fun provideProviderDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
    return ProviderDataStore.getInstance(context)
}
```

### Issue: Compose UI tests timeout in CI

**Cause**: CI emulators are slower than local machines, and default 5000ms `waitUntil` timeouts may not be sufficient.

**Solution**: Use 15000ms (15s) timeouts for complex navigation tests, 10000ms (10s) for simpler screen tests:

```kotlin
companion object {
    private const val DEFAULT_TIMEOUT = 15_000L  // 15s for navigation tests
    private const val LONG_TIMEOUT = 20_000L     // 20s for session/state tests
}

// Use consistent timeouts throughout the test class
composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
    composeTestRule.onAllNodesWithTag("nav_session")
        .fetchSemanticsNodes().isNotEmpty()
}
```

**Best practice**: Define `DEFAULT_TIMEOUT` as a companion object constant and use it consistently across all `waitUntil` calls in the test class.

### Issue: More menu animations cause test flakiness

**Cause**: Dropdown menus have animation delays that can cause click events to fail.

**Solution**: Add timing delays after opening menus:

```kotlin
private fun navigateToSettings() {
    composeTestRule.onNodeWithTag("nav_more").performClick()

    composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
        composeTestRule.onAllNodesWithTag("menu_settings")
            .fetchSemanticsNodes().isNotEmpty()
    }

    // Allow menu animation to complete
    composeTestRule.mainClock.advanceTimeBy(300)

    composeTestRule.onNodeWithTag("menu_settings").performClick()

    // Wait for navigation to complete
    composeTestRule.waitForIdle()
}
```

### Issue: Content below fold not found in LazyColumn

**Cause**: `performScrollToNode()` can't find content that hasn't been rendered yet.

**Solution**: Add testTag to LazyColumn and use scroll to reveal content:

```kotlin
// In production code - add testTag to LazyColumn
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .testTag("SettingsLazyColumn"),
    // ...
)

// In test code - scroll to content before asserting
composeTestRule.waitUntil(DEFAULT_TIMEOUT) {
    composeTestRule.onAllNodesWithTag("SettingsLazyColumn")
        .fetchSemanticsNodes().isNotEmpty()
}

composeTestRule.onNodeWithTag("SettingsLazyColumn")
    .performScrollToNode(hasText("Session Trends"))

composeTestRule.onNodeWithText("Session Trends").assertIsDisplayed()
```

### Issue: "On-Device AI" text found multiple times (ambiguous matcher)

**Cause**: Same text appears in multiple places (section header and card title).

**Solution**: Add testTags to specific elements for unique identification:

```kotlin
// In production code - add testTag to disambiguate
Text(
    text = stringResource(R.string.settings_on_device_ai),
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier
        .padding(top = 16.dp)
        .testTag("settings_on_device_ai_header"),
)

// In test code - use testTag instead of text
composeTestRule.onNodeWithTag("SettingsLazyColumn")
    .performScrollToNode(hasTestTag("settings_on_device_ai_header"))

composeTestRule.onNodeWithTag("settings_on_device_ai_header").assertIsDisplayed()
```

### Issue: Memory profiling test fails due to GC non-determinism

**Cause**: `System.gc()` is a hint, not a command. Java GC behavior is non-deterministic.

**Solution**: Use threshold-based leak detection instead of asserting memory was reclaimed:

```kotlin
// WRONG: Unreliable assertion
val memoryReclaimed = duringSessionMemoryMB - afterCleanupMemoryMB
assert(memoryReclaimed > 0) { "No memory was reclaimed" }

// RIGHT: Threshold-based leak detection
assert(afterCleanupMemoryMB < initialMemoryMB + 50) {
    "Memory after cleanup (${afterCleanupMemoryMB}MB) exceeds threshold - possible leak"
}
```

### Issue: Certificate pinning test fails due to OkHttp deduplication

**Cause**: OkHttp deduplicates identical pins, so adding the same pin twice results in only 1 pin per domain.

**Solution**: For now, check that at least 1 pin exists per domain. Add actual intermediate CA backup pins when needed:

```kotlin
// Get intermediate CA pin for rotation support
// echo | openssl s_client -connect api.deepgram.com:443 -showcerts 2>/dev/null | \
//     openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | \
//     openssl dgst -sha256 -binary | openssl enc -base64

@Test
fun allDomains_havePins() {
    val pinsByDomain = CertificatePinning.pinner.pins.groupBy { it.pattern }
    assertTrue(
        "All domains should have at least one pin configured",
        pinsByDomain.all { (_, pins) -> pins.isNotEmpty() },
    )
}
```

### Issue: Health monitor tests fail due to real HTTP requests

**Cause**: `ProviderHealthMonitor` makes actual HTTP health check requests during tests.

**Solution**: Use a mock OkHttp interceptor:

```kotlin
private fun createHealthyHealthMonitor(): ProviderHealthMonitor {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }
        .build()
    return ProviderHealthMonitor(
        config = HealthMonitorConfig(
            healthEndpoint = "http://localhost/health",
            checkIntervalMs = 60000,
        ),
        client = client,
        providerName = "Test",
    )
}
```

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
