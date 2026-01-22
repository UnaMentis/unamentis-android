# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with the UnaMentis Android codebase.

## Project Overview

UnaMentis Android is a Kotlin/Jetpack Compose voice AI tutoring app. It enables 60-90+ minute voice-based learning sessions with sub-500ms latency. This is the Android port of the iOS app, maintaining strict feature parity.

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

## Quick Commands

```bash
# Build debug APK
./scripts/build.sh

# Run unit tests
./scripts/test-quick.sh

# Run all tests (unit + instrumented)
./scripts/test-all.sh

# Lint check
./scripts/lint.sh

# Auto-format code
./scripts/format.sh

# Health check (lint + quick tests)
./scripts/health-check.sh

# Install on emulator
./scripts/install-emulator.sh

# Start emulator
./scripts/launch-emulator.sh Pixel_8_Pro_API_34
```

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

## Quality Gates

Before any code is considered "done", it must pass these gates:

1. **Compilation**: `./gradlew assembleDebug` succeeds
2. **Lint**: `./scripts/lint.sh` passes with zero violations
3. **Unit Tests**: `./scripts/test-quick.sh` passes with ALL tests green
4. **Accessibility**: All interactive elements have content descriptions
5. **Strings**: All user-facing text in `strings.xml`
6. **Documentation**: Public APIs have KDoc comments

## Key Technical Requirements

**Testing Philosophy (Real Over Mock):**
- Only mock paid external APIs (cloud LLM, STT, TTS, Embeddings)
- OnDeviceLLMService (llama.cpp) is NOT a paid API - test with real JNI, mock only model loading
- Use real implementations for all internal services
- Use in-memory Room database for testing
- See `docs/TESTING.md` for detailed testing philosophy and guidelines
- See `AGENTS.md` for complete AI development guidelines

**Code Style Requirements:**
- Follow `docs/ANDROID_STYLE_GUIDE.md` (MANDATORY)
- Accessibility content descriptions on all interactive elements
- Strings in `strings.xml` for all user-facing text
- Tablet adaptive layouts using window size classes
- Kotlin coroutine compliance (proper scope, cancellation)

**Performance Targets:**
- E2E turn latency: <500ms (median), <1000ms (P99)
- Memory growth: <50MB over 90 minutes
- Session stability: 90+ minutes without crashes

## Server Communication

The app communicates with two local servers:

| Server | Port | Purpose |
|--------|------|---------|
| Log Server | 8765 | Remote logging for debugging |
| Management Console | 8766 | Curriculum, metrics, configuration |

**From the emulator, access via:**
- `http://10.0.2.2:8765` (log server)
- `http://10.0.2.2:8766` (management console)

See `docs/DEV_ENVIRONMENT.md` for complete setup instructions.

## Commit Convention

Follow Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `perf:`, `ci:`, `chore:`

**BEFORE EVERY COMMIT:**
```bash
./scripts/health-check.sh
```

Do NOT commit if this command fails. Fix the issues first.

## Key Documentation

- `AGENTS.md` - AI development guidelines and testing philosophy
- `docs/TESTING.md` - Comprehensive testing guide
- `docs/ANDROID_STYLE_GUIDE.md` - Mandatory Android coding standards
- `docs/DEV_ENVIRONMENT.md` - Developer environment setup guide
- `ANDROID_PORT_SPECIFICATION.md` - Complete feature specification
- `SECURITY.md` - Security policy and best practices
- `CONTRIBUTING.md` - Contribution guidelines

## Project Structure

```
app/
├── src/main/kotlin/com/unamentis/
│   ├── core/           # Business logic
│   │   ├── audio/      # AudioEngine, VAD
│   │   ├── session/    # SessionManager
│   │   ├── curriculum/ # CurriculumEngine
│   │   ├── routing/    # PatchPanel LLM routing
│   │   ├── telemetry/  # Metrics, costs
│   │   └── config/     # API keys, server config
│   ├── services/       # External integrations
│   │   ├── stt/        # Speech-to-text providers
│   │   ├── tts/        # Text-to-speech providers
│   │   ├── llm/        # Language model providers
│   │   └── vad/        # Voice activity detection
│   ├── data/           # Persistence layer
│   │   ├── local/      # Room database
│   │   ├── remote/     # API clients
│   │   └── repository/ # Data repositories
│   ├── ui/             # Jetpack Compose UI
│   │   ├── session/    # Main voice session
│   │   ├── curriculum/ # Content browser
│   │   ├── settings/   # Configuration
│   │   └── theme/      # Material 3 theming
│   └── di/             # Hilt dependency injection
├── src/main/cpp/       # Native code (Oboe, llama.cpp)
├── src/test/kotlin/com/unamentis/
│   ├── core/           # Unit tests for core logic
│   ├── data/           # Data layer tests
│   ├── services/       # Service tests
│   └── helpers/        # Test utilities (MockServices, TestDataFactory)
└── src/androidTest/kotlin/com/unamentis/
    ├── ui/             # Compose UI tests
    ├── data/local/     # Room database tests
    └── benchmark/      # Performance benchmarks
```
