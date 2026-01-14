<p align="center">
  <img src="docs/assets/logo-placeholder.png" alt="UnaMentis Logo" width="120" height="120">
</p>

<h1 align="center">UnaMentis Android</h1>

<p align="center">
  <strong>Voice AI Tutoring for Extended Learning Sessions</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#documentation">Documentation</a> •
  <a href="#contributing">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2014%2B-3DDC84?style=flat-square&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License">
</p>

---

## Overview

UnaMentis Android is a voice-first AI tutoring application designed for extended learning sessions of 60-90+ minutes. The app maintains sub-500ms end-to-end latency for natural conversational flow while supporting multiple AI providers for speech recognition, text-to-speech, and language models.

This is the Android port of the iOS application, maintaining **strict feature parity** to ensure a consistent learning experience across platforms.

### Key Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| **E2E Latency** | <500ms (median) | Full turn response time |
| **Session Duration** | 90+ minutes | Stable without crashes |
| **Memory Growth** | <50MB | Over 90-minute session |
| **Battery Drain** | <15%/hour | During active session |

---

## Features

### Voice Interaction
- **Real-time Speech Recognition** — Multi-provider STT with Deepgram, AssemblyAI, and on-device Android Speech
- **Natural Voice Synthesis** — ElevenLabs, Deepgram Aura, and Android TTS
- **Intelligent Barge-in** — Interrupt AI responses naturally with 600ms confirmation window
- **Voice Activity Detection** — Silero VAD with ONNX Runtime for accurate speech detection

### AI-Powered Tutoring
- **Multi-Provider LLM Support** — OpenAI GPT-4o, Anthropic Claude, Ollama, and on-device llama.cpp
- **Intelligent Routing** — PatchPanel service for optimal provider selection per task
- **Curriculum-Based Learning** — Structured content with progress tracking
- **Session Persistence** — Full conversation history with transcript storage

### Performance & Reliability
- **Low-Latency Audio** — Native Oboe engine with JNI for sub-10ms audio I/O
- **Thermal Management** — Automatic adaptation to device thermal state
- **Device Capability Tiers** — Optimized settings for flagship, standard, and minimum devices
- **Cost Tracking** — Real-time API usage and cost monitoring

### Developer Experience
- **MCP Integration** — Autonomous build, test, and debug via Claude Code
- **Remote Logging** — Python log server for real-time debugging
- **Comprehensive Testing** — Unit tests, integration tests, and performance benchmarks

---

## Quick Start

### Prerequisites

| Requirement | Version | Purpose |
|-------------|---------|---------|
| Android Studio | Ladybug 2024.2.1+ | IDE |
| JDK | 17+ | Compilation |
| Android SDK | API 34 | Target platform |
| NDK | 26.x | Native code (Oboe) |
| Node.js | 20+ | MCP servers |
| Python | 3.12+ | Log server |

### 1. Clone and Setup

```bash
# Clone repository
git clone <repository-url> unamentis-android
cd unamentis-android

# Set environment variables (add to ~/.zshrc or ~/.bashrc)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools
```

### 2. Build and Run

```bash
# Build debug APK
./scripts/build.sh

# Start emulator
./scripts/launch-emulator.sh Pixel_8_Pro_API_34

# Install on emulator
./scripts/install-emulator.sh
```

### 3. Run Tests

```bash
# Quick unit tests
./scripts/test-quick.sh

# Full test suite (requires emulator)
./scripts/test-all.sh

# Pre-commit health check
./scripts/health-check.sh
```

> **See [docs/DEV_ENVIRONMENT.md](docs/DEV_ENVIRONMENT.md) for complete setup instructions.**

---

## Architecture

### Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI** | Jetpack Compose | Declarative UI with Material 3 |
| **DI** | Hilt | Dependency injection |
| **Async** | Kotlin Coroutines/Flow | Reactive programming |
| **Database** | Room | Local persistence |
| **Networking** | OkHttp + Retrofit | HTTP/WebSocket clients |
| **Audio** | Oboe (C++ JNI) | Low-latency audio I/O |
| **ML** | TensorFlow Lite + ONNX | VAD and on-device inference |

### Project Structure

```
app/src/main/kotlin/com/unamentis/
├── core/                    # Business logic
│   ├── audio/              # AudioEngine, VAD integration
│   ├── session/            # SessionManager state machine
│   ├── curriculum/         # Content management
│   ├── telemetry/          # Metrics and cost tracking
│   └── config/             # Provider configuration
├── services/               # External integrations
│   ├── stt/                # Speech-to-text providers
│   ├── tts/                # Text-to-speech providers
│   ├── llm/                # LLM providers + PatchPanel
│   └── vad/                # Voice activity detection
├── data/                   # Data layer
│   ├── local/              # Room database
│   ├── remote/             # API clients
│   └── repository/         # Repository pattern
├── ui/                     # Compose UI
│   ├── session/            # Main voice interface
│   ├── curriculum/         # Content browser
│   ├── settings/           # Configuration
│   └── theme/              # Material 3 theming
└── di/                     # Hilt modules
```

### Provider Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Voice AI Pipeline                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Speaks → [VAD] → [STT] → [LLM] → [TTS] → Audio Playback   │
│                  │        │       │       │                      │
│              Silero    ┌──┴──┐ ┌──┴──┐ ┌──┴──┐                  │
│              (ONNX)    │Multi│ │Patch│ │Multi│                  │
│                        │ STT │ │Panel│ │ TTS │                  │
│                        └─────┘ └─────┘ └─────┘                  │
│                                                                  │
│  STT Providers: Deepgram, AssemblyAI, Groq, Android Speech      │
│  LLM Providers: OpenAI, Anthropic, Ollama, llama.cpp            │
│  TTS Providers: ElevenLabs, Deepgram Aura, Android TTS          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Documentation

| Document | Description |
|----------|-------------|
| [DEV_ENVIRONMENT.md](docs/DEV_ENVIRONMENT.md) | Complete development setup guide |
| [TESTING_GUIDE.md](docs/TESTING_GUIDE.md) | Comprehensive testing instructions |
| [MCP_SETUP.md](docs/MCP_SETUP.md) | MCP server configuration |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Detailed architecture documentation |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines |
| [SPECIFICATION.md](ANDROID_PORT_SPECIFICATION.md) | Full feature specification |

---

## Development

### Available Scripts

| Script | Purpose |
|--------|---------|
| `./scripts/build.sh` | Build debug APK |
| `./scripts/test-quick.sh` | Run unit tests |
| `./scripts/test-all.sh` | Run all tests (unit + instrumented) |
| `./scripts/lint.sh` | Run ktlint + detekt |
| `./scripts/format.sh` | Auto-format code |
| `./scripts/health-check.sh` | Pre-commit validation |
| `./scripts/launch-emulator.sh` | Start Android emulator |
| `./scripts/install-emulator.sh` | Install APK on emulator |

### MCP Integration

This project integrates with [Model Context Protocol](https://modelcontextprotocol.io/) servers for autonomous development:

| Server | Purpose |
|--------|---------|
| **mobile-mcp** | Emulator control, UI automation, screenshots |
| **gradle-mcp-server** | Build, test, and task execution |

```bash
# Verify MCP servers
claude mcp list

# Expected output:
# mobile-mcp: Connected
# gradle-mcp-server: Connected
```

### Debugging with Log Server

The Python log server provides real-time debugging:

```bash
# Start log server
python3 scripts/log-server.py &

# View logs in browser
open http://localhost:8765/

# Fetch logs via API
curl -s http://localhost:8765/logs | python3 -m json.tool

# Clear logs before debugging
curl -s -X POST http://localhost:8765/clear
```

> **From the emulator**, access the log server at `http://10.0.2.2:8765`

---

## Testing Philosophy

### Real Over Mock

We prioritize real implementations over mocks:

- **Only mock paid external APIs** — OpenAI, Anthropic, Deepgram, ElevenLabs
- **Use real implementations for** — Internal services, Room database (in-memory), file operations
- **Mocks must be faithful** — Return data in exact API format, simulate error conditions

### Test Categories

| Type | Location | Command |
|------|----------|---------|
| Unit Tests | `app/src/test/` | `./scripts/test-quick.sh` |
| Integration Tests | `app/src/androidTest/` | `./scripts/test-all.sh` |
| Performance Tests | Manual profiling | Android Studio Profiler |

### Pre-Commit Requirements

```bash
# ALWAYS run before committing
./scripts/health-check.sh
```

This validates:
1. `./scripts/lint.sh` — ktlint + detekt pass with zero violations
2. `./scripts/test-quick.sh` — All unit tests pass

---

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Quick Contribution Guide

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/amazing-feature`)
3. Make your changes
4. Run `./scripts/health-check.sh`
5. Commit with [Conventional Commits](https://www.conventionalcommits.org/) (`git commit -m "feat: add amazing feature"`)
6. Push and open a Pull Request

### Commit Convention

```
feat:     New feature
fix:      Bug fix
docs:     Documentation only
test:     Adding tests
refactor: Code refactoring
perf:     Performance improvement
ci:       CI/CD changes
chore:    Maintenance tasks
```

---

## Requirements

### Minimum Device Requirements

| Tier | RAM | SoC | On-Device Capabilities |
|------|-----|-----|------------------------|
| Flagship | 12GB+ | Snapdragon 8 Gen 2+ | Full on-device AI |
| Standard | 8GB+ | Snapdragon 8 Gen 1+ | Limited on-device |
| Minimum | 6GB+ | Snapdragon 7 Gen 1+ | Cloud-primary |

### Android Version

- **Target:** Android 14 (API 34)
- **Minimum:** Android 9 (API 28)
- **Compile:** Android 15 (API 35)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [Oboe](https://github.com/google/oboe) — Low-latency audio for Android
- [Silero VAD](https://github.com/snakers4/silero-vad) — Voice activity detection
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — Modern Android UI
- [MCP Servers](https://modelcontextprotocol.io/) — Autonomous development tooling

---

<p align="center">
  <sub>Built with ❤️ for learners everywhere</sub>
</p>
