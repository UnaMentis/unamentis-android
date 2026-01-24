# UnaMentis Android Style Guide and Standards

**Version:** 1.0
**Last Updated:** January 2026
**Status:** Mandatory

This document defines the coding standards, accessibility requirements, internationalization patterns, and UI/UX guidelines for the UnaMentis Android application. **All contributors (human and AI) must comply with these standards.**

---

## Table of Contents

1. [Accessibility Requirements](#1-accessibility-requirements)
2. [Internationalization (i18n)](#2-internationalization-i18n)
3. [Tablet and Foldable Support](#3-tablet-and-foldable-support)
4. [Jetpack Compose Best Practices](#4-jetpack-compose-best-practices)
5. [Performance Standards](#5-performance-standards)
6. [Code Style](#6-code-style)
7. [Testing Requirements](#7-testing-requirements)

---

## 1. Accessibility Requirements

### 1.1 Non-Negotiable Standards

Accessibility is **mandatory**, not optional. UnaMentis must be usable by people with disabilities.

#### TalkBack Support

Every interactive element MUST have content descriptions:

```kotlin
// REQUIRED for all interactive elements
Button(
    onClick = { /* ... */ },
    modifier = Modifier.semantics {
        contentDescription = "Start voice session"
    }
) {
    Text("Start")
}
```

#### Using stringResource() Inside Semantics Blocks

**Important:** `stringResource()` is a `@Composable` function and **cannot be called inside non-composable lambda blocks** like `semantics {}`. You must extract the string to a variable first:

```kotlin
// CORRECT: Extract stringResource() before semantics block
val buttonDescription = stringResource(R.string.cd_start_session)
Button(
    onClick = { /* ... */ },
    modifier = Modifier.semantics {
        contentDescription = buttonDescription
    }
) {
    Text(stringResource(R.string.start_session))
}

// CORRECT: With conditional descriptions
val selectedDescription = stringResource(R.string.cd_region_selected, region.displayName)
val unselectedDescription = stringResource(R.string.cd_region_button, region.displayName)
val accessibilityDescription = if (isSelected) selectedDescription else unselectedDescription

Box(
    modifier = Modifier.semantics {
        contentDescription = accessibilityDescription
    }
) { /* ... */ }

// INCORRECT: stringResource() inside semantics block - WILL NOT COMPILE
Button(
    modifier = Modifier.semantics {
        contentDescription = stringResource(R.string.cd_start)  // BAD: Compilation error
    }
) { /* ... */ }

// REQUIRED for icons
Icon(
    imageVector = Icons.Default.Mic,
    contentDescription = "Microphone active",
    modifier = Modifier.semantics {
        stateDescription = if (isRecording) "Recording" else "Not recording"
    }
)

// REQUIRED for dynamic content
Text(
    text = transcriptText,
    modifier = Modifier.semantics {
        contentDescription = if (isUserMessage) "You said: $transcriptText" else "AI said: $transcriptText"
    }
)
```

#### Font Scaling Support

All text MUST scale with user's font size preferences:

```kotlin
// CORRECT: Uses Material typography (scales automatically)
Text(
    text = "Title",
    style = MaterialTheme.typography.headlineMedium
)

// INCORRECT: Fixed font size, won't scale
Text(
    text = "Title",
    fontSize = 18.sp  // BAD: Fixed size
)

// If you must use sp, ensure it scales:
Text(
    text = "Title",
    fontSize = with(LocalDensity.current) {
        MaterialTheme.typography.headlineMedium.fontSize
    }
)
```

#### Minimum Touch Targets

Interactive elements must meet Android's 48dp minimum:

```kotlin
Button(
    onClick = { /* ... */ },
    modifier = Modifier
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        // Or use sizeIn for more control:
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
) {
    Icon(Icons.Default.Settings, contentDescription = "Settings")
}

// For IconButton, it's automatic but verify:
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier.size(48.dp)  // Explicit minimum
) {
    Icon(Icons.Default.Close, contentDescription = "Close")
}
```

### 1.2 Audio-First Considerations

UnaMentis is primarily an audio application. For users who cannot hear:

1. **Visual Feedback Required**: All audio events must have visual representation
   - Speaking indicators (waveforms, animations)
   - Transcript display of all speech
   - Status indicators for audio states

2. **Haptic Feedback**: Use haptics for state changes
   ```kotlin
   val haptic = LocalHapticFeedback.current

   LaunchedEffect(sessionState) {
       haptic.performHapticFeedback(HapticFeedbackType.LongPress)
   }
   ```

3. **Transcript Always Available**: The transcript view provides text representation of all audio content, enabling deaf users to read what is being spoken.

### 1.3 Reduce Motion

Respect the user's Reduce Motion preference:

```kotlin
val reduceMotion = LocalReduceMotion.current

val animationSpec = if (reduceMotion) {
    snap<Float>()  // No animation
} else {
    tween<Float>(durationMillis = 300)
}

val amplitude by animateFloatAsState(
    targetValue = if (isSpeaking) 1f else 0f,
    animationSpec = animationSpec
)
```

---

## 2. Internationalization (i18n)

### 2.1 String Localization

**All user-facing strings MUST be in strings.xml.** Never hardcode strings:

```kotlin
// CORRECT: Uses string resource
Text(text = stringResource(R.string.start_session))

// With parameters
Text(text = stringResource(R.string.topics_count, topicCount))

// INCORRECT: Hardcoded string
Text(text = "Start Session")  // BAD: Not localizable
```

#### strings.xml Structure

```xml
<!-- res/values/strings.xml (English) -->
<resources>
    <!-- Session -->
    <string name="session_start">Start Session</string>
    <string name="session_stop">End Session</string>
    <string name="session_status_idle">Ready to start</string>
    <string name="session_status_listening">Listening...</string>
    <string name="session_status_processing">Thinking...</string>
    <string name="session_status_speaking">Speaking...</string>

    <!-- Curriculum -->
    <string name="curriculum_title">Curriculum</string>
    <string name="curriculum_topics_count">%d topics</string>
    <string name="curriculum_empty_title">No Curriculum Loaded</string>
    <string name="curriculum_empty_description">Import a curriculum to get started.</string>

    <!-- Accessibility -->
    <string name="a11y_session_start_hint">Double-tap to begin a voice conversation</string>
    <string name="a11y_transcript_user">You said</string>
    <string name="a11y_transcript_ai">AI said</string>
</resources>
```

### 2.2 Date and Number Formatting

Always use formatters that respect locale:

```kotlin
// CORRECT: Locale-aware
val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
Text(text = date.format(dateFormatter))

val numberFormat = remember { NumberFormat.getInstance() }
Text(text = numberFormat.format(count))

val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
Text(text = currencyFormat.format(cost))

// INCORRECT: Hardcoded format
Text(text = "${hours}h ${minutes}m")  // BAD: Doesn't localize
```

### 2.3 Currency Formatting

**Never hardcode currency symbols.** Always use `NumberFormat.getCurrencyInstance()`:

```kotlin
// CORRECT: Locale-aware currency
import java.text.NumberFormat
import java.util.Locale

// Standard currency (2 decimal places)
val cost = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(stats.totalCost)
Text(text = cost)  // "$12.50" in en-US, "€12,50" in de-DE

// High-precision currency (4 decimal places for micro-costs)
val formattedCost = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    minimumFractionDigits = 4
    maximumFractionDigits = 4
}.format(provider.totalCost)

// INCORRECT: Hardcoded dollar sign
Text(text = "$${String.format("%.2f", cost)}")  // BAD: Only works for USD
Text(text = "\$12.50")  // BAD: Hardcoded symbol
```

### 2.4 Percentage Formatting

**Never use `String.format()` for percentages.** Always use `NumberFormat.getPercentInstance()`:

```kotlin
import java.text.NumberFormat
import java.util.Locale

// CORRECT: Locale-aware percentage
val percentFormatter = NumberFormat.getPercentInstance(Locale.getDefault())
val accuracy = 0.85f
Text(text = percentFormatter.format(accuracy.toDouble()))  // "85%" in en-US, "85 %" in fr-FR

// For display without the % symbol, use string resources:
// <string name="kb_percent_format">%1$d%%</string>
Text(text = stringResource(R.string.kb_percent_format, (accuracy * 100).toInt()))

// INCORRECT: String.format() - doesn't respect locale
Text(text = String.format("%.0f%%", accuracy * 100))  // BAD: Not locale-aware
Text(text = "${(accuracy * 100).toInt()}%")  // BAD: Hardcoded format
```

### 2.5 Units and Suffixes

Use string resources with placeholders for units:

```kotlin
// In strings.xml:
// <string name="latency_ms">%1$d ms</string>
// <string name="storage_gb">%1$.1f GB</string>

// CORRECT: Localized unit string
Text(text = stringResource(R.string.latency_ms, stats.avgLatency))

// INCORRECT: Hardcoded unit
Text(text = "${stats.avgLatency}ms")  // BAD: Not localizable
Text(text = "${value} ms")  // BAD: Hardcoded
```

### 2.6 Right-to-Left (RTL) Support

Use start/end instead of left/right:

```kotlin
// CORRECT: RTL-aware
Modifier.padding(start = 16.dp, end = 8.dp)
Row(horizontalArrangement = Arrangement.Start) { /* ... */ }

// INCORRECT: Forces LTR
Modifier.padding(left = 16.dp, right = 8.dp)  // BAD
```

### 2.7 Plurals

Use quantity strings for proper pluralization:

```xml
<plurals name="topics_count">
    <item quantity="zero">No topics</item>
    <item quantity="one">%d topic</item>
    <item quantity="other">%d topics</item>
</plurals>
```

```kotlin
val topicsText = pluralStringResource(
    R.plurals.topics_count,
    count,
    count
)
```

---

## 3. Tablet and Foldable Support

### 3.1 Mandatory Adaptive Layouts

UnaMentis MUST work as a first-class tablet app, not just an enlarged phone app.

#### Window Size Classes

Use window size classes to adapt layouts:

```kotlin
@Composable
fun AdaptiveLayout() {
    val windowSizeClass = calculateWindowSizeClass(LocalContext.current as Activity)

    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Phone: Single pane navigation
            PhoneLayout()
        }
        WindowWidthSizeClass.Medium -> {
            // Small tablet/foldable: List-detail
            ListDetailLayout()
        }
        WindowWidthSizeClass.Expanded -> {
            // Large tablet: Multi-pane
            MultiPaneLayout()
        }
    }
}
```

#### NavigationSuiteScaffold for Adaptive Navigation

```kotlin
@Composable
fun AdaptiveNavigation(
    selectedDestination: String,
    onNavigate: (String) -> Unit
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            NavigationSuiteItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_session)) },
                selected = selectedDestination == "session",
                onClick = { onNavigate("session") }
            )
            // More items...
        }
    ) {
        // Content
    }
}
```

### 3.2 Foldable Device Support

Support fold-aware layouts:

```kotlin
@Composable
fun FoldAwareLayout() {
    val foldingFeatures = LocalFoldingFeatures.current

    when {
        foldingFeatures.any { it.state == FoldingFeature.State.HALF_OPENED } -> {
            // Tabletop mode: Content above fold, controls below
            TabletopLayout()
        }
        foldingFeatures.any { it.orientation == FoldingFeature.Orientation.VERTICAL } -> {
            // Book mode: Two-pane side by side
            BookModeLayout()
        }
        else -> {
            // Normal layout
            StandardLayout()
        }
    }
}
```

### 3.3 Orientation Support

Support all orientations:

```kotlin
@Composable
fun OrientationAwareLayout() {
    val configuration = LocalConfiguration.current

    when (configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> {
            LandscapeLayout()
        }
        else -> {
            PortraitLayout()
        }
    }
}
```

---

## 4. Jetpack Compose Best Practices

### 4.1 State Management

Use the correct state management for each situation:

| Pattern | Use Case |
|---------|----------|
| `remember` | Simple local UI state |
| `rememberSaveable` | State that survives configuration changes |
| `ViewModel` + `StateFlow` | Business logic, survives config changes |
| `collectAsStateWithLifecycle` | Collecting flows lifecycle-aware |

```kotlin
// Local UI state
var isExpanded by remember { mutableStateOf(false) }

// Survives rotation
var userInput by rememberSaveable { mutableStateOf("") }

// ViewModel state (preferred for business logic)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionState.Idle)
}

// In Composable
@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    // ...
}
```

### 4.2 Recomposition Optimization

#### Stable Parameters

Mark classes as `@Stable` or `@Immutable` when appropriate:

```kotlin
@Immutable
data class TranscriptMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)

// Or for mutable but stable classes:
@Stable
class SessionState(
    val status: Status,
    val transcript: List<TranscriptMessage>
)
```

#### Lambda Stability

Avoid creating new lambdas on every recomposition:

```kotlin
// BAD: New lambda every recomposition
Button(onClick = { viewModel.startSession() }) { /* ... */ }

// GOOD: Remembered or stable reference
val onStartClick = remember { { viewModel.startSession() } }
Button(onClick = onStartClick) { /* ... */ }

// BEST: Method reference (if possible)
Button(onClick = viewModel::startSession) { /* ... */ }
```

### 4.3 Side Effects

Use the correct side effect for each situation:

```kotlin
// One-time initialization
LaunchedEffect(Unit) {
    viewModel.initialize()
}

// React to state changes
LaunchedEffect(sessionState) {
    when (sessionState) {
        SessionState.Error -> showSnackbar("Error occurred")
        else -> {}
    }
}

// Cleanup on disposal
DisposableEffect(Unit) {
    val listener = audioManager.registerCallback()
    onDispose {
        audioManager.unregisterCallback(listener)
    }
}

// Derived state (computed from other state)
val isReady by remember {
    derivedStateOf { !isLoading && hasPermissions }
}
```

### 4.4 Navigation

Use type-safe navigation with Navigation Compose:

```kotlin
// Define routes
@Serializable
object SessionRoute

@Serializable
data class TopicDetailRoute(val topicId: String)

// NavHost
NavHost(navController = navController, startDestination = SessionRoute) {
    composable<SessionRoute> {
        SessionScreen(onNavigateToTopic = { topicId ->
            navController.navigate(TopicDetailRoute(topicId))
        })
    }
    composable<TopicDetailRoute> { backStackEntry ->
        val route: TopicDetailRoute = backStackEntry.toRoute()
        TopicDetailScreen(topicId = route.topicId)
    }
}
```

### 4.5 Safe Progress Values

Compose progress indicators (`LinearProgressIndicator`, `CircularProgressIndicator`) throw `IllegalArgumentException` when passed `NaN` or `Infinity` values. **Always use the `safeProgress` utilities** from `com.unamentis.ui.util.ProgressUtils`:

```kotlin
import com.unamentis.ui.util.safeProgress
import com.unamentis.ui.util.safeProgressRatio

// REQUIRED: Wrap all progress values
LinearProgressIndicator(
    progress = { safeProgress(downloadProgress) },  // Handles NaN, Infinity, null
    modifier = Modifier.fillMaxWidth(),
)

CircularProgressIndicator(
    progress = { safeProgress(completionRatio) },
    modifier = Modifier.size(100.dp),
)

// For ratios (division that could produce NaN/Infinity):
val barProgress = safeProgressRatio(currentValue, maxValue)  // Safe division

// INCORRECT: Raw progress values can crash
LinearProgressIndicator(
    progress = { currentValue / total },  // BAD: Could be NaN if total is 0
)
```

#### Available Utilities

| Function | Use Case |
|----------|----------|
| `safeProgress(Float?)` | Sanitize any float progress value |
| `safeProgress(Double?)` | Sanitize double progress values |
| `safeProgressRatio(current, total)` | Safe division for progress ratios |
| `safeProgressInRange(value, min, max)` | Custom range validation |

#### What `safeProgress` Handles

- **null** → returns `0f`
- **NaN** → returns `0f`
- **Negative Infinity** → returns `0f`
- **Positive Infinity** → returns `1f`
- **Values < 0** → clamped to `0f`
- **Values > 1** → clamped to `1f`

---

## 5. Performance Standards

### 5.1 Targets

| Metric | Target |
|--------|--------|
| App launch to interactive | < 2 seconds |
| Screen transition | < 300ms |
| List scroll | 60 FPS (no jank) |
| Memory growth per hour | < 50MB |
| E2E voice turn latency | < 500ms |

### 5.2 Lazy Loading

Use lazy containers for large collections:

```kotlin
// GOOD: Lazy loading
LazyColumn {
    items(items = curricula, key = { it.id }) { curriculum ->
        CurriculumItem(curriculum)
    }
}

// BAD: Loads everything
Column {
    curricula.forEach { curriculum ->
        CurriculumItem(curriculum)
    }
}
```

### 5.3 Image Loading

Use Coil for efficient image loading:

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        .crossfade(true)
        .build(),
    contentDescription = "Topic image",
    modifier = Modifier.fillMaxWidth(),
    placeholder = painterResource(R.drawable.placeholder),
    error = painterResource(R.drawable.error)
)
```

### 5.4 Avoid Recomposition Waste

```kotlin
// Use key to help Compose identify items
LazyColumn {
    items(items = messages, key = { it.id }) { message ->
        MessageItem(message)
    }
}

// Skip expensive calculations during recomposition
val expensiveResult = remember(inputData) {
    expensiveCalculation(inputData)
}
```

---

## 6. Code Style

### 6.1 Kotlin Coroutines and Concurrency

All async operations MUST use coroutines properly:

```kotlin
// Services should be classes with suspend functions or Flows
class SessionManager @Inject constructor(
    private val audioEngine: AudioEngine,
    private val sttService: STTService
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    suspend fun startSession() {
        withContext(Dispatchers.Default) {
            // CPU-intensive work
        }
    }
}

// ViewModels launch coroutines in viewModelScope
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    fun startSession() {
        viewModelScope.launch {
            sessionManager.startSession()
        }
    }
}
```

### 6.2 Dependency Injection

Use Hilt for dependency injection:

```kotlin
@HiltAndroidApp
class UnaMentisApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideTelemetryEngine(): TelemetryEngine = TelemetryEngine()
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val telemetry: TelemetryEngine
) : ViewModel()
```

### 6.3 Documentation

Public APIs require KDoc documentation:

```kotlin
/**
 * Manages voice session lifecycle and state.
 *
 * SessionManager coordinates between audio input, speech recognition,
 * LLM processing, and text-to-speech output.
 *
 * @property audioEngine The audio capture and playback engine
 * @property sttService Speech-to-text service
 */
class SessionManager @Inject constructor(
    private val audioEngine: AudioEngine,
    private val sttService: STTService
) {
    /**
     * Starts a new voice session.
     *
     * @param topic Optional topic for curriculum-based sessions
     * @throws SessionException if services are unavailable
     */
    suspend fun startSession(topic: Topic? = null) { /* ... */ }
}
```

### 6.4 File Organization

```kotlin
// 1. Package declaration
package com.unamentis.ui.session

// 2. Imports (sorted by ktlint)
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.core.session.SessionState

// 3. Main Composable
@Composable
fun SessionScreen(
    viewModel: SessionViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ...
}

// 4. Sub-composables (private)
@Composable
private fun SessionContent(
    state: SessionState,
    onStartClick: () -> Unit
) {
    // ...
}

// 5. Previews
@Preview(showBackground = true)
@Composable
private fun SessionScreenPreview() {
    UnaMentisTheme {
        SessionContent(
            state = SessionState.Idle,
            onStartClick = {}
        )
    }
}
```

---

## 7. Testing Requirements

### 7.1 Accessibility Testing Checklist

Before any PR:

- [ ] Navigate all screens with TalkBack enabled
- [ ] Test with font size at largest setting
- [ ] Test with display size at largest setting
- [ ] Verify all interactive elements have content descriptions
- [ ] Run Accessibility Scanner app
- [ ] Test with "Remove animations" enabled

### 7.2 Tablet Testing Checklist

- [ ] Test on 10" tablet in portrait
- [ ] Test on 10" tablet in landscape
- [ ] Test on 7" tablet
- [ ] Test multi-window (split screen)
- [ ] Test on foldable (if available)
- [ ] Verify navigation adapts correctly

### 7.3 Internationalization Testing

- [ ] Verify all strings use string resources
- [ ] Test with pseudolocalization enabled
- [ ] Test with RTL language (Settings > Developer > Force RTL)
- [ ] Verify dates/numbers format correctly
- [ ] Check for text truncation with longer translations

---

## Compliance

### Enforcement

1. All PRs must pass ktlint and detekt
2. New Composables must include accessibility support
3. All user-facing strings must be in strings.xml
4. Code review must verify compliance with this guide

### Exceptions

Exceptions require explicit approval and documentation explaining why the standard cannot be met and the plan to address it in the future.

---

## Related Documents

- [TESTING.md](TESTING.md) - Testing philosophy and guidelines
- [AGENTS.md](../AGENTS.md) - AI development guidelines
- [Material Design 3 Guidelines](https://m3.material.io/)
- [Android Accessibility Guidelines](https://developer.android.com/guide/topics/ui/accessibility)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
