# Contributing to UnaMentis Android

Thank you for your interest in contributing to UnaMentis Android! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Testing Requirements](#testing-requirements)
- [Documentation](#documentation)
- [Issue Reporting](#issue-reporting)

---

## Code of Conduct

This project adheres to a code of conduct that all contributors are expected to follow. Please be respectful, inclusive, and constructive in all interactions.

### Our Standards

- Use welcoming and inclusive language
- Be respectful of differing viewpoints
- Accept constructive criticism gracefully
- Focus on what is best for the community
- Show empathy towards other community members

---

## Getting Started

### Prerequisites

Before contributing, ensure you have the development environment set up:

| Requirement | Version | Verification |
|-------------|---------|--------------|
| Android Studio | Ladybug 2024.2.1+ | IDE version in About dialog |
| JDK | 17+ | `java --version` |
| Android SDK | API 34 | SDK Manager |
| NDK | 26.x | SDK Manager → SDK Tools |
| Node.js | 20+ | `node --version` |
| Python | 3.12+ | `python3 --version` |

See [docs/DEV_ENVIRONMENT.md](docs/DEV_ENVIRONMENT.md) for complete setup instructions.

### Fork and Clone

```bash
# Fork the repository on GitHub, then clone your fork
git clone https://github.com/<your-username>/unamentis-android.git
cd unamentis-android

# Add upstream remote
git remote add upstream https://github.com/<original-owner>/unamentis-android.git

# Verify remotes
git remote -v
```

### Verify Setup

```bash
# Build the project
./scripts/build.sh

# Run tests
./scripts/test-quick.sh

# Run health check
./scripts/health-check.sh
```

All commands should complete successfully before you start contributing.

---

## Development Workflow

### 1. Sync with Upstream

Before starting work, sync your fork:

```bash
git checkout main
git fetch upstream
git merge upstream/main
git push origin main
```

### 2. Create a Feature Branch

```bash
# Create and switch to a new branch
git checkout -b <type>/<short-description>

# Examples:
git checkout -b feat/voice-command-shortcuts
git checkout -b fix/audio-playback-stutter
git checkout -b docs/api-reference-update
```

### 3. Make Changes

- Write code following our [coding standards](#coding-standards)
- Add tests for new functionality
- Update documentation as needed

### 4. Validate Changes

```bash
# ALWAYS run before committing
./scripts/health-check.sh
```

This runs:
1. **Lint checks** — ktlint + detekt with zero violations
2. **Unit tests** — All tests must pass

### 5. Commit Changes

Follow our [commit guidelines](#commit-guidelines):

```bash
git add .
git commit -m "feat: add voice command shortcuts for session control"
```

### 6. Push and Create PR

```bash
git push origin <branch-name>
```

Then create a Pull Request on GitHub.

---

## Coding Standards

### Kotlin Style Guide

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with additional project-specific rules enforced by ktlint and detekt.

#### Formatting

- **Indentation:** 4 spaces (no tabs)
- **Line length:** 120 characters maximum
- **Trailing commas:** Required for multi-line collections
- **Imports:** No wildcards, sorted alphabetically

```kotlin
// Good
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// Bad
import android.content.*
import kotlinx.coroutines.flow.*
```

#### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `SessionManager` |
| Functions | camelCase | `startSession()` |
| Properties | camelCase | `isPlaying` |
| Constants | SCREAMING_SNAKE_CASE | `MAX_SESSION_DURATION` |
| Packages | lowercase | `com.unamentis.core.audio` |

#### Documentation

```kotlin
/**
 * Manages voice session lifecycle and coordinates audio pipeline.
 *
 * The SessionManager orchestrates the interaction between:
 * - Audio capture and playback
 * - Speech-to-text transcription
 * - LLM response generation
 * - Text-to-speech synthesis
 *
 * @property audioEngine The audio I/O engine
 * @property sttService Speech-to-text service
 * @property llmService Language model service
 * @property ttsService Text-to-speech service
 */
class SessionManager(
    private val audioEngine: AudioEngine,
    private val sttService: STTService,
    private val llmService: LLMService,
    private val ttsService: TTSService
) {
    /**
     * Starts a new voice session.
     *
     * @param topic Optional curriculum topic for guided learning
     * @throws SessionException if session fails to start
     */
    suspend fun startSession(topic: Topic? = null) {
        // Implementation
    }
}
```

### Compose Guidelines

#### State Management

```kotlin
// Use StateFlow for ViewModel state
class SessionViewModel : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
}

// Collect state in Composables
@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    // Use state
}
```

#### Composable Best Practices

```kotlin
// Good: Stateless composable with clear parameters
@Composable
fun TranscriptBubble(
    entry: TranscriptEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Implementation
}

// Good: Extract complex logic to ViewModel
@Composable
fun SessionScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    SessionContent(
        state = state,
        onStartSession = viewModel::startSession,
        onStopSession = viewModel::stopSession
    )
}

// Bad: Logic in Composable
@Composable
fun SessionScreen() {
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        // Complex business logic here - should be in ViewModel
    }
}
```

### Architecture Guidelines

#### Layer Separation

```
UI Layer (Compose)
    ↓ Observes StateFlow
ViewModel
    ↓ Calls
Use Cases / Repositories
    ↓ Uses
Data Sources (Room, API)
```

#### Dependency Injection

```kotlin
// Provide dependencies via Hilt modules
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideAudioEngine(
        @ApplicationContext context: Context
    ): AudioEngine {
        return AudioEngine(context)
    }
}

// Inject in ViewModels
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel()
```

---

## Commit Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature for the user |
| `fix` | Bug fix for the user |
| `docs` | Documentation only changes |
| `style` | Formatting, missing semicolons, etc. (no code change) |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `test` | Adding or updating tests |
| `ci` | CI/CD configuration changes |
| `chore` | Maintenance tasks, dependency updates |

### Scopes (Optional)

| Scope | Area |
|-------|------|
| `audio` | Audio engine, VAD |
| `session` | Session management |
| `stt` | Speech-to-text |
| `tts` | Text-to-speech |
| `llm` | Language models |
| `ui` | User interface |
| `db` | Database, persistence |
| `api` | API clients |
| `test` | Testing infrastructure |

### Examples

```bash
# Feature
git commit -m "feat(audio): add Oboe-based low-latency audio engine"

# Bug fix
git commit -m "fix(session): resolve memory leak in long sessions"

# Documentation
git commit -m "docs: update API integration guide with WebSocket examples"

# Refactoring
git commit -m "refactor(llm): extract common provider logic to base class"

# Performance
git commit -m "perf(vad): optimize Silero inference with NNAPI delegate"

# Breaking change (add ! after type)
git commit -m "feat(api)!: migrate to v2 curriculum API format

BREAKING CHANGE: CurriculumResponse now uses 'topics' instead of 'lessons'"
```

### Commit Message Body

For complex changes, add a body explaining:

- **What** changed
- **Why** it was necessary
- **How** it was implemented (if not obvious)

```bash
git commit -m "fix(audio): resolve audio glitches on Samsung devices

Samsung devices with Exynos chips were experiencing audio underruns
due to buffer size miscalculation. This fix:

- Detects Exynos chipsets via Build.HARDWARE
- Uses larger buffer sizes (512 frames) for affected devices
- Adds fallback to safe mode if glitches persist

Tested on Galaxy S24 (Exynos 2400) and Galaxy A54.

Fixes #123"
```

---

## Pull Request Process

### Before Submitting

1. **Sync with upstream main**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run health check**
   ```bash
   ./scripts/health-check.sh
   ```

3. **Update documentation** if needed

4. **Add/update tests** for new functionality

### PR Title

Use the same format as commit messages:

```
feat(audio): add Oboe-based low-latency audio engine
```

### PR Description Template

```markdown
## Summary

Brief description of what this PR does.

## Changes

- Added AudioEngine with Oboe integration
- Implemented JNI bridge for native audio
- Added unit tests for audio utilities

## Testing

- [ ] Unit tests pass (`./scripts/test-quick.sh`)
- [ ] Lint checks pass (`./scripts/lint.sh`)
- [ ] Manual testing on emulator
- [ ] Tested on physical device (if applicable)

## Screenshots (if applicable)

[Add screenshots for UI changes]

## Related Issues

Fixes #123
Relates to #456
```

### Review Process

1. **Automated checks** must pass (CI/lint/tests)
2. **Code review** by at least one maintainer
3. **Address feedback** with additional commits
4. **Squash commits** if requested
5. **Maintainer merges** when approved

### After Merge

```bash
# Switch to main and sync
git checkout main
git pull upstream main

# Delete feature branch
git branch -d <branch-name>
git push origin --delete <branch-name>
```

---

## Testing Requirements

### Test Coverage

All new features and bug fixes require tests:

| Change Type | Required Tests |
|-------------|----------------|
| New feature | Unit tests + integration tests |
| Bug fix | Regression test proving fix |
| Refactoring | Existing tests must pass |
| Performance | Benchmark showing improvement |

### Writing Tests

#### Unit Tests

```kotlin
class AudioEngineTest {

    @Test
    fun `calculateRms returns correct value for sine wave`() {
        // Given
        val samples = FloatArray(1000) { sin(it * 0.1f) }

        // When
        val rms = AudioUtils.calculateRms(samples)

        // Then
        assertThat(rms).isWithin(0.01f).of(0.707f)
    }

    @Test
    fun `calculateRms returns zero for silent audio`() {
        val samples = FloatArray(1000) { 0f }
        val rms = AudioUtils.calculateRms(samples)
        assertThat(rms).isEqualTo(0f)
    }
}
```

#### Testing with Mocks

```kotlin
class SessionManagerTest {

    @MockK
    private lateinit var sttService: STTService

    @MockK
    private lateinit var llmService: LLMService

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `startSession transitions to listening state`() = runTest {
        // Given
        val sessionManager = SessionManager(sttService, llmService, ...)

        // When
        sessionManager.startSession()

        // Then
        assertThat(sessionManager.state.value).isEqualTo(SessionState.LISTENING)
    }
}
```

### Running Tests

```bash
# Unit tests only (fast)
./scripts/test-quick.sh

# All tests including instrumented (requires emulator)
./scripts/test-all.sh

# Specific test class
./gradlew test --tests "com.unamentis.core.audio.AudioEngineTest"

# With coverage report
./gradlew jacocoTestReport
```

---

## Documentation

### When to Update Documentation

- New features or APIs
- Changed behavior
- New dependencies
- Setup or configuration changes
- Bug fixes that affect user behavior

### Documentation Locations

| Type | Location |
|------|----------|
| API reference | KDoc in source files |
| User guides | `docs/` directory |
| README updates | `README.md` |
| Architecture | `docs/ARCHITECTURE.md` |

### KDoc Style

```kotlin
/**
 * Detects voice activity in audio samples using Silero VAD model.
 *
 * This service loads the Silero VAD ONNX model and processes audio
 * in 32ms frames (512 samples at 16kHz) to determine speech presence.
 *
 * ## Usage
 *
 * ```kotlin
 * val vadService = SileroVADService(context)
 * vadService.initialize()
 *
 * audioFlow.collect { samples ->
 *     val result = vadService.processAudio(samples)
 *     if (result.isSpeech) {
 *         // Handle speech detected
 *     }
 * }
 * ```
 *
 * @property context Application context for asset loading
 * @property threshold Speech detection threshold (default: 0.5f)
 *
 * @see VADResult
 * @see AudioEngine
 */
class SileroVADService(
    private val context: Context,
    private val threshold: Float = 0.5f
)
```

---

## Issue Reporting

### Bug Reports

Use the bug report template:

```markdown
## Bug Description

Clear description of the bug.

## Steps to Reproduce

1. Go to '...'
2. Click on '...'
3. See error

## Expected Behavior

What should happen.

## Actual Behavior

What actually happens.

## Environment

- Device: Pixel 8 Pro
- Android Version: 14
- App Version: 1.0.0
- Build Type: Debug

## Logs

```
Paste relevant logs here
```

## Screenshots

[Add screenshots if applicable]
```

### Feature Requests

Use the feature request template:

```markdown
## Feature Description

Clear description of the feature.

## Problem it Solves

Why is this feature needed?

## Proposed Solution

How should it work?

## Alternatives Considered

Other approaches that were considered.

## Additional Context

Any other relevant information.
```

---

## Questions?

If you have questions about contributing:

1. Check existing [documentation](docs/)
2. Search [existing issues](https://github.com/owner/unamentis-android/issues)
3. Open a new issue with the `question` label

---

Thank you for contributing to UnaMentis Android!
