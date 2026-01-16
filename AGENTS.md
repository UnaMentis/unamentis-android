# AI Development Guidelines for UnaMentis Android

## Development Model

This project is developed with **100% AI assistance**. All code, tests, documentation, and architecture decisions are made collaboratively between human direction and AI implementation.

## Project Structure

UnaMentis Android is a Kotlin/Jetpack Compose voice AI tutoring app. It enables 60-90+ minute voice-based learning sessions with sub-500ms latency. This is the Android port of the iOS app, maintaining strict feature parity.

### Key Directories

```
app/src/main/kotlin/com/unamentis/
├── core/           # Core business logic
│   ├── audio/      # Audio pipeline, VAD integration
│   ├── session/    # SessionManager, state machine
│   ├── curriculum/ # Curriculum management, progress tracking
│   ├── routing/    # PatchPanel LLM routing
│   ├── telemetry/  # Metrics, cost tracking
│   └── config/     # API keys, server config
├── services/       # External service integrations
│   ├── stt/        # Speech-to-text providers
│   ├── tts/        # Text-to-speech providers
│   ├── llm/        # Language model providers
│   └── vad/        # Voice activity detection
├── data/           # Data layer
│   ├── local/      # Room database
│   ├── remote/     # API clients
│   └── repository/ # Data repositories
├── ui/             # Jetpack Compose UI
│   ├── session/    # Main voice session
│   ├── curriculum/ # Content browser
│   ├── settings/   # Configuration
│   └── theme/      # Material 3 theming
└── di/             # Hilt dependency injection

app/src/test/kotlin/com/unamentis/
├── core/           # Unit tests for core logic
├── data/           # Data layer tests
├── services/       # Service tests
└── helpers/        # Test utilities, mock services

app/src/androidTest/kotlin/com/unamentis/
├── ui/             # Compose UI tests
├── data/local/     # Room database tests
└── benchmark/      # Performance benchmarks
```

---

## MANDATORY: MCP Server Integration

**You MUST use the configured MCP servers for all build and emulator operations.** This enables first-class, round-trip development and debugging.

### Required MCP Servers

Verify both servers are connected:
```bash
claude mcp list
# Should show:
# mobile-mcp: Connected
# gradle-mcp-server: Connected
```

If not connected, restart the Claude Code session.

### MCP Tools to Use

| Task | MCP Tool |
|------|----------|
| Build app | `execute_gradle_task` (assembleDebug) |
| Run tests | `run_gradle_tests` |
| Install on emulator | `mobile_install_app` |
| Launch app | `mobile_launch_app` |
| Take screenshot | `mobile_take_screenshot` |
| Tap UI | `mobile_click_on_screen_at_coordinates` |
| Type text | `mobile_type_keys` |
| Swipe | `mobile_swipe_on_screen` |
| List UI elements | `mobile_list_elements_on_screen` |
| List devices | `mobile_list_available_devices` |

### Round-Trip Debugging Workflow

When debugging UI issues:
1. Build with gradle-mcp-server
2. Install with mobile-mcp
3. Launch with mobile-mcp
4. Capture logs via log server or adb logcat
5. Screenshot with mobile-mcp
6. Interact with mobile-mcp
7. Analyze and iterate

This workflow allows autonomous debugging without manual user intervention.

---

## Build & Test Commands

```bash
# Build debug APK
./scripts/build.sh
# Or: ./gradlew assembleDebug

# Run unit tests
./scripts/test-quick.sh
# Or: ./gradlew test

# Run all tests (unit + instrumented)
./scripts/test-all.sh
# Or: ./gradlew test connectedAndroidTest

# Lint check
./scripts/lint.sh
# Or: ./gradlew ktlintCheck detekt

# Auto-format code
./scripts/format.sh
# Or: ./gradlew ktlintFormat

# Health check (lint + quick tests)
./scripts/health-check.sh
```

---

## MANDATORY: Log Server Must Always Be Running

**The log server MUST be running whenever the app is running.** This is non-negotiable.

```bash
# Start log server FIRST, before anything else
python3 scripts/log-server.py &

# Verify it's running
curl -s http://localhost:8765/health  # Returns "OK"
```

**Access logs:**
- Web interface: http://localhost:8765/
- JSON API: `curl -s http://localhost:8765/logs`
- Clear logs: `curl -s -X POST http://localhost:8765/clear`

**When debugging issues:**
1. Ensure log server is running
2. Clear logs: `curl -s -X POST http://localhost:8765/clear`
3. Reproduce the issue in the app
4. Fetch logs: `curl -s http://localhost:8765/logs | python3 -m json.tool`
5. The last log message before a freeze identifies the blocking point

**Emulator Network Access:**
The emulator accesses the host machine via `10.0.2.2`. The app is configured to use:
- Log server: `http://10.0.2.2:8765`
- Management console: `http://10.0.2.2:8766`

---

## MANDATORY: Definition of Done

**NO IMPLEMENTATION IS COMPLETE UNTIL TESTS PASS.** This is the single most important rule.

### The Golden Rule

You MUST run `./scripts/health-check.sh` and verify ALL tests pass BEFORE:
- Telling the user the work is "done" or "complete"
- Summarizing what you accomplished
- Moving on to the next task
- Committing any changes

### What "Complete" Means

1. Code is written and compiles
2. `./scripts/lint.sh` passes with no violations
3. `./scripts/test-quick.sh` passes with ALL tests green
4. You have ACTUALLY RUN these commands and seen the results yourself

### Failure Mode to Avoid

**WRONG:** Write code, see it compiles, tell user "implementation is complete"
**RIGHT:** Write code, run tests, verify all pass, THEN tell user "implementation is complete"

If you tell the user "tests are passing" or "implementation is complete" when tests are actually failing, you have failed at your job. Always verify by running tests locally.

### Pre-Commit Checklist

```bash
./scripts/health-check.sh
```

If this command fails, fix the issues before proceeding.

---

## Technical Specifications

### Performance Targets

- E2E turn latency: <500ms (median), <1000ms (P99)
- 90-minute session stability without crashes
- Memory growth: <50MB over 90 minutes

### Cost Targets

- Balanced preset: <$3/hour
- Cost-optimized: <$1.50/hour

---

## Testing Philosophy: Real Over Mock

**Mock testing is unacceptable for most scenarios.** Tests should exercise real code paths to provide genuine confidence in behavior.

### When Mocking is VALID

Mocks are only acceptable for:

1. **Paid third-party APIs** (LLM, Embeddings, TTS, STT)
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

### Example: Good vs Bad Mocks

**Bad Mock (unacceptable):**
```kotlin
class BadMockLLM : LLMService {
    override suspend fun streamCompletion(
        messages: List<LLMMessage>,
        config: LLMConfig
    ): Flow<LLMToken> = flowOf(
        // Single token, no validation, no realistic behavior
        LLMToken(content = "response", isDone = true)
    )
}
```

**Good Mock (faithful):**
```kotlin
class FaithfulMockLLM : LLMService {
    var shouldSimulateRateLimit = false
    var responseText = "Default response"

    override suspend fun streamCompletion(
        messages: List<LLMMessage>,
        config: LLMConfig
    ): Flow<LLMToken> = flow {
        // Validate inputs like real API
        require(messages.isNotEmpty()) { "Messages cannot be empty" }
        if (config.maxTokens > 4096) {
            throw LLMException.ContextLengthExceeded(maxTokens = 4096)
        }

        // Simulate rate limiting
        if (shouldSimulateRateLimit) {
            throw LLMException.RateLimited(retryAfterSeconds = 30)
        }

        // Simulate realistic TTFT (150ms)
        delay(150)

        // Stream tokens with realistic delays
        val words = responseText.split(" ")
        words.forEachIndexed { index, word ->
            val isLast = index == words.lastIndex
            emit(LLMToken(
                content = word + if (isLast) "" else " ",
                isDone = isLast,
                stopReason = if (isLast) StopReason.END_TURN else null,
                tokenCount = 1
            ))
            // 20ms between tokens
            if (!isLast) delay(20)
        }
    }
}
```

### Current Mock Inventory

**Valid mocks (external paid APIs):**
- `MockLLMService` - LLM API calls cost money
- `MockEmbeddingService` - Embedding API calls cost money
- `MockSTTService` - STT API calls cost money
- `MockTTSService` - TTS API calls cost money

**Should NOT be mocked (use real implementations):**
- `TelemetryEngine` - Internal, use real implementation
- `Room Database` - Use `inMemoryDatabaseBuilder`
- File operations - Use temp directories
- Cosine similarity, chunking, etc. - Test real implementations

See `app/src/test/kotlin/com/unamentis/helpers/MockServices.kt` for reference implementations.

---

## Working with This Codebase

### Before Implementation

1. **Read the Android Style Guide**: `docs/ANDROID_STYLE_GUIDE.md` (MANDATORY)
2. Read relevant tests first - they document expected behavior
3. Check existing patterns in similar components
4. Reference `ANDROID_PORT_SPECIFICATION.md` for architectural decisions

### During Implementation

1. Write tests first (TDD)
2. Ensure Kotlin coroutine compliance (proper scope, cancellation)
3. Run build frequently to catch issues early
4. Use TodoWrite to track progress on multi-step tasks
5. **Follow Android Style Guide requirements for accessibility and i18n**

### Quality Gates

- All tests pass (you must verify by running them)
- Build succeeds (`./gradlew assembleDebug`)
- No lint violations (`./gradlew ktlintCheck detekt`)
- Public APIs documented with KDoc comments
- Code follows existing patterns in the codebase
- **Accessibility content descriptions on all interactive elements** (per Style Guide)
- **Strings in strings.xml for all user-facing text** (per Style Guide)
- **Tablet adaptive layouts using window size classes** (per Style Guide)

---

## Writing Style Guidelines

All AI agents must follow these style rules when writing documentation, comments, and any text in this project.

### Punctuation Rules

**Never use em dashes or en dashes as sentence interrupters.** This is a strict rule.

- Wrong: "The feature - which was added last week - improves performance"
- Correct: "The feature, which was added last week, improves performance"
- Also correct: "The feature improves performance. It was added last week."

Use commas for parenthetical phrases. Use periods to break up long sentences. Do not use dashes as a substitute for commas or to set off phrases.

### General Style

- Be concise and direct
- Use active voice
- Avoid jargon unless it's standard in the domain
- Match the existing tone and style of the codebase

---

## Multi-Agent Coordination

This project may have multiple AI agents working on it simultaneously. To prevent duplicate work and ensure smooth collaboration:

### Task Status Document

**Location:** `docs/TASK_STATUS.md`

This document is the single source of truth for what's being worked on.

### Protocol for ALL AI Agents

1. **Before ANY work**: Read `docs/TASK_STATUS.md` first
2. **Claim your task**: Add an entry to "Currently Active" with your agent/tool name and timestamp
3. **Check for conflicts**: Do not work on tasks another agent has already claimed
4. **Update on completion**: Move your task to "Completed" section with notes
5. **Note blockers**: If you hit a blocker, add it to "Blocked/Pending" section

### Why This Matters

Without coordination:
- Two agents might implement the same feature differently
- One agent might break code another is actively working on
- Time gets wasted on duplicate effort

With coordination:
- Clear visibility into who's doing what
- Async collaboration between different AI tools
- Audit trail of progress

---

## iOS Parity

This Android app maintains strict feature parity with the iOS version. When implementing features:

1. Reference the iOS implementation in `/Users/cygoerdt/unamentis`
2. Maintain consistent UX and functionality
3. Adapt iOS patterns to Android idioms (SwiftUI -> Compose, Swift actors -> Kotlin coroutines)
4. Document any platform-specific differences

---

## Server Communication

The app communicates with two local servers:

| Server | Port | Purpose |
|--------|------|---------|
| Log Server | 8765 | Remote logging for debugging |
| Management Console | 8766 | Curriculum, metrics, configuration |

**From the emulator, access via:**
- `http://10.0.2.2:8765` (log server)
- `http://10.0.2.2:8766` (management console)

---

## Commit Convention

Follow Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `perf:`, `ci:`, `chore:`

**BEFORE EVERY COMMIT:**
```bash
./scripts/health-check.sh
```

Do NOT commit if this command fails. Fix the issues first.

---

## Key Documentation

- `docs/TESTING.md` - Comprehensive testing philosophy guide
- `docs/ANDROID_STYLE_GUIDE.md` - Mandatory Android coding standards
- `docs/DEV_ENVIRONMENT.md` - Developer environment setup guide
- `ANDROID_PORT_SPECIFICATION.md` - Complete feature specification
- `SECURITY.md` - Security policy and best practices
- `CONTRIBUTING.md` - Contribution guidelines

---

## MANDATORY: Clean Up Test Data

**When testing produces persistent artifacts, you MUST clean them up before finishing.**

This includes:
- **Test curricula** created via import API or direct file writes
- **Test assets** uploaded to the asset system
- **Test files** written to disk during testing
- **Import jobs** that created temporary data

**Naming convention for test data:**
- Prefix with `test-` or `claude-test-`
- Include "DELETE ME" or "TEST" in titles
- Example IDs: `test-import-validation`, `claude-test-assessment-flow`

This makes orphaned test data easy to identify and clean up if cleanup was missed.
