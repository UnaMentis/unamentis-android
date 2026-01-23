# Knowledge Bowl Module

**Last Updated**: 2026-01-22 (Updated)
**iOS Parity**: Full feature parity with iOS implementation

## Overview

The Knowledge Bowl module is a specialized learning experience for academic competition training. It provides practice modes for Knowledge Bowl competitions across 12 academic domains with support for both written and oral rounds.

## Features

### Study Modes

| Mode | Description | Question Count | Time Limit |
|------|-------------|----------------|------------|
| **Diagnostic** | Assess overall knowledge across all domains | 50 | None |
| **Targeted** | Focus on weak areas based on performance | 25 | None |
| **Breadth** | Even distribution across all domains | 36 | None |
| **Speed** | Quick recall training under time pressure | 20 | 5 minutes |
| **Competition** | Simulate real competition conditions | 45 | Timed |
| **Team** | Collaborative team practice mode | 45 | Timed |

### Feature Flags

Study modes can be enabled/disabled via `KBModuleFeatures`:

```kotlin
data class KBModuleFeatures(
    val supportsTeamMode: Boolean,
    val supportsSpeedTraining: Boolean,
    val supportsCompetitionSim: Boolean,
)
```

- `DIAGNOSTIC`, `TARGETED`, `BREADTH` - Always available
- `SPEED` - Requires `supportsSpeedTraining`
- `COMPETITION` - Requires `supportsCompetitionSim`
- `TEAM` - Requires `supportsTeamMode`

### Academic Domains

12 domains with weighted distribution matching competition format:

| Domain | Weight | Icon |
|--------|--------|------|
| Science | 20% | Highest priority |
| Mathematics | 15% | |
| Literature | 12% | |
| History | 12% | |
| Social Studies | 10% | |
| Arts | 8% | |
| Current Events | 8% | |
| Geography | 5% | |
| Technology | 4% | |
| Health | 3% | |
| Foreign Language | 2% | |
| Miscellaneous | 1% | |

### Regional Configurations

Supports multiple Knowledge Bowl regional rule sets:
- **Colorado** (default)
- **Minnesota**
- **Washington**

Each region has different:
- Written question counts and time limits
- Oral round rules (conference time, verbal conferring)
- Point values and SOS bonuses

## Architecture

### Package Structure

```text
com.unamentis.modules.knowledgebowl/
├── KnowledgeBowlModule.kt          # Module entry point (ModuleProtocol)
├── core/
│   ├── engine/
│   │   ├── KBQuestionEngine.kt     # Question loading and selection
│   │   ├── KBPracticeEngine.kt     # Practice session logic
│   │   └── KBPracticeState.kt      # Session state management
│   ├── stats/
│   │   ├── KBStatsManager.kt       # Statistics persistence
│   │   ├── KBDomainStats.kt        # Per-domain statistics
│   │   └── KBSessionRecord.kt      # Session history records
│   ├── validation/
│   │   ├── KBAnswerValidator.kt    # Answer validation logic
│   │   ├── AnswerNormalizer.kt     # Text normalization
│   │   └── LevenshteinDistance.kt  # Fuzzy matching
│   └── voice/
│       ├── KBVoiceCoordinator.kt   # TTS/STT coordination
│       ├── KBAudioCache.kt         # Server audio caching
│       └── KBCachedAudio.kt        # Cached audio model
├── data/
│   ├── model/
│   │   ├── KBQuestion.kt           # Question data model
│   │   ├── KBAnswer.kt             # Answer with alternates
│   │   ├── KBDomain.kt             # Academic domains
│   │   ├── KBDifficulty.kt         # Difficulty levels
│   │   ├── KBStudyMode.kt          # Study mode definitions
│   │   ├── KBModuleFeatures.kt     # Feature flags
│   │   ├── KBRegion.kt             # Regional configurations
│   │   ├── KBSessionConfig.kt      # Session configuration
│   │   └── KBQuestionResult.kt     # Practice result tracking
│   └── remote/
│       └── KBQuestionService.kt    # Server question fetching
└── ui/
    ├── KBNavigation.kt             # Internal navigation host
    ├── dashboard/
    │   ├── KBDashboardScreen.kt    # Main dashboard UI
    │   └── KBDashboardViewModel.kt # Dashboard state
    ├── launcher/
    │   ├── KBPracticeLauncherSheet.kt    # Pre-session setup
    │   └── KBPracticeLauncherViewModel.kt
    ├── session/
    │   ├── KBPracticeSessionScreen.kt    # Unified practice UI
    │   └── KBPracticeSessionViewModel.kt
    ├── written/
    │   ├── KBWrittenSessionScreen.kt     # Legacy written mode
    │   └── KBWrittenSessionViewModel.kt
    ├── oral/
    │   ├── KBOralSessionScreen.kt        # Legacy oral mode
    │   └── KBOralSessionViewModel.kt
    ├── settings/
    │   └── KBSettingsScreen.kt           # KB-specific settings
    └── theme/
        └── KBColors.kt                   # Domain-specific colors
```

### Navigation Flow

```text
KBNavigationHost
    │
    ├─► KBDashboardScreen (entry point)
    │       │
    │       ├─► [Select Study Mode] ─► KBPracticeLauncherSheet (bottom sheet)
    │       │                               │
    │       │                               └─► [Start] ─► KBPracticeSessionScreen
    │       │
    │       ├─► [Written Practice] ─► KBWrittenSessionScreen (legacy)
    │       │
    │       ├─► [Oral Practice] ─► KBOralSessionScreen (legacy)
    │       │
    │       └─► [Settings] ─► KBSettingsScreen
    │
    └─► All screens return to Dashboard on completion/back
```

### Data Flow

```text
┌──────────────────┐     ┌───────────────────┐     ┌──────────────────┐
│  KBQuestionService│────►│  KBQuestionEngine │────►│  KBPracticeEngine │
│  (server fetch)   │     │  (selection algo) │     │  (session logic)  │
└──────────────────┘     └───────────────────┘     └──────────────────┘
         │                        │                         │
         ▼                        ▼                         ▼
┌──────────────────┐     ┌───────────────────┐     ┌──────────────────┐
│  Bundled JSON    │     │  KBStatsManager   │     │  KBAnswerValidator│
│  (fallback)      │     │  (persistence)    │     │  (scoring)        │
└──────────────────┘     └───────────────────┘     └──────────────────┘
```

## Key Components

### KBQuestionService

Server-side question fetching with local fallback:

```kotlin
@Singleton
class KBQuestionService @Inject constructor(
    private val questionEngine: KBQuestionEngine,
) {
    suspend fun loadQuestions()           // Fetch from server or fallback
    fun questionsForMode(mode: KBStudyMode): List<KBQuestion>
    fun balancedSelection(count: Int): List<KBQuestion>
}
```

**Server endpoint**: `http://{host}:8766/api/modules/knowledge-bowl/download`

### KBPracticeLauncherSheet

Bottom sheet shown before starting practice:
- Displays mode information (icon, title, description)
- Shows question count and difficulty
- Provides mode-specific tips
- Loads questions asynchronously
- "Start Practice" / "Cancel" actions

### KBPracticeSessionScreen

Unified practice screen for all study modes:
- Progress header (question counter, timer for speed mode)
- Question display with domain badge and difficulty
- Answer input (text field or voice)
- Feedback view (correct/incorrect, explanation)
- Completion summary with stats

### KBVoiceCoordinator

Coordinates TTS and STT for voice-based practice:
- Question reading via TTS
- Voice answer capture via STT
- Server audio caching for pre-generated audio
- PCM audio playback using AudioTrack

## Statistics & Progress

### Tracked Metrics

- **Overall**: Total questions, correct answers, accuracy
- **Per-Domain**: Questions answered, accuracy, mastery level
- **Response Time**: Average time per question
- **Competition Readiness**: Weighted score (accuracy + coverage + volume)

### Readiness Calculation

```kotlin
val competitionReadiness =
    (accuracyScore * 0.5f) +      // 50% weight
    (domainCoverage * 0.3f) +     // 30% weight
    (volumeScore * 0.2f)          // 20% weight
```

## iOS Parity

This Android implementation maintains full feature parity with the iOS Knowledge Bowl module:

| Feature | iOS | Android |
|---------|-----|---------|
| 6 Study Modes | ✅ | ✅ |
| Feature Flags | ✅ | ✅ |
| Server Question Fetching | ✅ | ✅ |
| Practice Launcher Sheet | ✅ | ✅ |
| Unified Practice Session | ✅ | ✅ |
| Enhanced Dashboard | ✅ | ✅ |
| Domain Mastery Grid | ✅ | ✅ |
| Competition Readiness | ✅ | ✅ |
| Voice Coordinator | ✅ | ✅ |
| Audio Caching | ✅ | ✅ |

## Testing

### Unit Tests

Located in `app/src/test/kotlin/com/unamentis/modules/knowledgebowl/`:

- `KBQuestionEngineTest.kt` - Question loading and selection
- `KBPracticeEngineTest.kt` - Practice session logic
- `KBStatsManagerTest.kt` - Statistics persistence
- `KBAnswerValidatorTest.kt` - Answer validation
- `AnswerNormalizerTest.kt` - Text normalization
- `LevenshteinDistanceTest.kt` - Fuzzy matching
- `KBModelsTest.kt` - Data model validation
- `KBQuestionServiceTest.kt` - Service and mode configuration

### Running KB Tests

```bash
./gradlew :app:testDebugUnitTest --tests "com.unamentis.modules.knowledgebowl.*"
```

## Usage

### Accessing the Module

The KB module is registered as a `ModuleProtocol` implementation:

```kotlin
// In ModuleRegistry
registry.getImplementation("knowledge-bowl")?.let { module ->
    // Display module UI
    module.getUIEntryPoint()
}
```

### Starting a Practice Session

1. User opens KB Dashboard
2. Taps a study mode card
3. Launcher sheet appears, loads questions
4. User taps "Start Practice"
5. Practice session begins
6. On completion, returns to dashboard with updated stats

## Files Created/Modified in iOS Port (2026-01-22)

### New Files (9)
- `data/model/KBModuleFeatures.kt`
- `data/model/KBQuestionResult.kt`
- `data/remote/KBQuestionService.kt`
- `ui/KBNavigation.kt`
- `ui/launcher/KBPracticeLauncherSheet.kt`
- `ui/launcher/KBPracticeLauncherViewModel.kt`
- `ui/session/KBPracticeSessionScreen.kt`
- `ui/session/KBPracticeSessionViewModel.kt`
- `test/.../KBQuestionServiceTest.kt`

### Modified Files (5)
- `data/model/KBStudyMode.kt` - Added `requiredFeature` property
- `ui/dashboard/KBDashboardScreen.kt` - Enhanced UI with hero, modes, stats
- `ui/dashboard/KBDashboardViewModel.kt` - Added study mode state management
- `core/voice/KBVoiceCoordinator.kt` - Implemented PCM audio playback
- `KnowledgeBowlModule.kt` - Updated entry point to use KBNavigationHost

## Code Quality Improvements (2026-01-22)

### KBVoiceCoordinator AudioTrack Error Handling

The voice coordinator now properly handles `AudioTrack.getMinBufferSize()` error codes:

```kotlin
val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
    Log.e(TAG, "AudioTrack.getMinBufferSize returned error: $minBufferSize")
    throw VoiceCoordinatorError.AudioFormatUnavailable
}
```

This prevents crashes when audio configuration is unavailable on certain devices.

### KBQuestionService OkHttp 4+ Patterns

Updated to use modern OkHttp 4+ extension functions:
- `ByteArray(0).toRequestBody()` instead of deprecated `RequestBody.create()`
- `response.use { }` block for automatic resource cleanup

### Dashboard UI Improvements

- **Domain Grid**: Now displays all 12 academic domains (previously truncated to 6)
- **Accessibility**: Added `contentDescription` semantics to StudyModeCard and DomainMasteryCard
- **Localization**: Moved all hardcoded strings to `strings.xml` resources
- **KDoc**: Added comprehensive documentation to all public StateFlows in ViewModels

### Practice Session Robustness

- **Idempotent endSession()**: Session completion is now guarded against duplicate calls
- **String Resources**: All dialog and UI strings externalized for i18n support
- **Speed Mode Stats**: Proper handling of questions within speed target

### String Resources Added

New entries in `res/values/strings.xml`:
- `kb_ready_label`, `kb_diagnostic_prompt`
- `kb_study_sessions`, `kb_your_stats`
- `kb_exit_practice_title`, `kb_exit_practice_message`
- `kb_exit_button`, `kb_continue_practicing_button`
- `kb_stat_correct`, `kb_speed_target`
- `cd_within_speed_target` (accessibility)
- Plurals: `kb_questions_answered_count`, `kb_correct_count`

### KBQuestionService Coroutine Safety (2026-01-22)

Added proper `CancellationException` handling to prevent swallowing structured concurrency cancellations:

```kotlin
import kotlin.coroutines.cancellation.CancellationException

suspend fun loadQuestions() {
    try {
        fetchQuestionsFromServer()
    } catch (e: CancellationException) {
        throw e  // Re-throw to preserve cooperative cancellation
    } catch (e: Exception) {
        Log.w(TAG, "Server fetch failed, using bundled questions: ${e.message}")
        loadBundledFallback()
    }
}
```

### KBQuestionService Localized Error Messages (2026-01-22)

Replaced hardcoded error strings with Android string resources for i18n support:

```kotlin
// Before (hardcoded)
throw IOException("Self-hosted server not configured")

// After (localized)
throw IOException(context.getString(R.string.kb_error_server_not_configured))
```

New error string resources:
- `kb_error_failed_to_load_questions` - Generic load failure
- `kb_error_server_not_configured` - Server not configured
- `kb_error_server_returned` - Server HTTP error (with format placeholder)
- `kb_error_empty_response` - Empty response body

### KBDashboardScreen LazyVerticalGrid Fix (2026-01-22)

Fixed the study modes grid to use flexible height instead of fixed height:

```kotlin
// Before (fixed height caused truncation)
LazyVerticalGrid(
    modifier = Modifier.height(260.dp),
    // ...
)

// After (flexible height with maximum)
LazyVerticalGrid(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 400.dp),
    userScrollEnabled = false,
    // ...
)
```

This prevents content truncation when displaying all 6 study modes.

### KBPracticeSessionViewModel isLastQuestion Fix (2026-01-22)

Fixed the `isLastQuestion` getter to prevent false positives when session hasn't started:

```kotlin
// Before (could return true when totalQuestions == 0)
val isLastQuestion: Boolean
    get() = _questionIndex.value >= _totalQuestions.value - 1

// After (requires at least one question)
val isLastQuestion: Boolean
    get() = _totalQuestions.value > 0 && _questionIndex.value >= _totalQuestions.value - 1
```
