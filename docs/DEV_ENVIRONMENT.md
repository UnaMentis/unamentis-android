# Android Development Environment Setup

This guide provides step-by-step instructions for setting up the UnaMentis Android development environment with full round-trip debugging capabilities.

## Prerequisites

| Software | Version | Purpose |
|----------|---------|---------|
| macOS | 14.0+ | Development host |
| Android Studio | Ladybug (2024.2.1)+ | IDE |
| JDK | 17+ | Compilation |
| Node.js | 20+ | MCP servers |
| Python | 3.12+ | Log server |

## 1. Android Studio Installation

### 1.1 Download and Install

1. Download Android Studio from [developer.android.com](https://developer.android.com/studio)
2. Open the DMG and drag to Applications
3. Launch Android Studio
4. Complete the setup wizard, selecting "Standard" installation

### 1.2 SDK Manager Configuration

Open Android Studio > Settings > Languages & Frameworks > Android SDK

**SDK Platforms tab:**
- [x] Android 14.0 (API 34) - Select "Android SDK Platform 34"
- [x] Android 13.0 (API 33) - For backward compatibility testing

**SDK Tools tab:**
- [x] Android SDK Build-Tools 34.0.0
- [x] Android SDK Command-line Tools (latest)
- [x] Android Emulator
- [x] Android SDK Platform-Tools
- [x] NDK (Side by side) - Version 26.x or newer
- [x] CMake 3.22.1+

Click "Apply" and wait for downloads to complete.

## 2. Environment Variables

Add to `~/.zshrc` (or `~/.bashrc`):

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

Reload your shell:
```bash
source ~/.zshrc
```

Verify installation:
```bash
adb --version
emulator -version
```

## 3. Create Android Emulators

### 3.1 Via Android Studio

1. Open Android Studio
2. Tools > Device Manager
3. Click "Create Device"

### 3.2 Recommended Device Profiles

| Device | API | RAM | Use Case |
|--------|-----|-----|----------|
| Pixel 8 Pro | 34 | 12GB | Primary development |
| Pixel Fold | 34 | 12GB | Foldable testing |
| Pixel Tablet | 34 | 8GB | Large screen |
| Medium Phone | 34 | 8GB | Standard tier |

### 3.3 Emulator Settings

For each emulator, configure:

```
Graphics: Hardware - GLES 2.0+
RAM: 4096 MB (minimum)
VM Heap: 512 MB
Internal Storage: 8 GB
Multi-Core CPU: 4 cores
```

### 3.4 Command-Line Emulator Creation

```bash
# List available system images
sdkmanager --list | grep "system-images"

# Download system image
sdkmanager "system-images;android-34;google_apis_playstore;arm64-v8a"

# Create AVD
avdmanager create avd -n Pixel_8_Pro_API_34 \
  -k "system-images;android-34;google_apis_playstore;arm64-v8a" \
  -d "pixel_8_pro"

# List AVDs
emulator -list-avds
```

## 4. MCP Server Installation

MCP servers enable Claude Code to perform autonomous build, test, and UI automation.

> **Note:** For comprehensive MCP documentation, see [MCP_SETUP.md](MCP_SETUP.md).

### 4.1 Quick Setup

```bash
# 1. Verify prerequisites
node --version  # Should be 20+
java --version  # Should be 17+

# 2. Install gradle-mcp-server
mkdir -p ~/mcp-servers/gradle-mcp-server
curl -fSL -o ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar \
  "https://github.com/IlyaGulya/gradle-mcp-server/releases/latest/download/gradle-mcp-server-all.jar"
```

### 4.2 Configuration

The `.mcp.json` file in the project root configures MCP servers:

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
      "args": [
        "-jar",
        "/Users/<username>/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar"
      ]
    }
  }
}
```

> **Important:** Replace `/Users/<username>` with your actual home directory path. The `~` shorthand may not expand correctly in all contexts.

### 4.3 Verify MCP Connection

```bash
# In Claude Code
claude mcp list
# Should show:
# mobile-mcp: Connected
# gradle-mcp-server: Connected
```

For detailed tool documentation and troubleshooting, see [MCP_SETUP.md](MCP_SETUP.md).

## 5. Log Server Setup

The log server is essential for debugging. It receives logs from the Android app and provides a web interface.

### 5.1 Install Dependencies

```bash
pip3 install aiohttp aiofiles
```

### 5.2 Copy Log Server

The log server script is in `scripts/log-server.py`. It's the same Python server used by the iOS app.

### 5.3 Start Log Server

```bash
# Start in background
python3 scripts/log-server.py &

# Verify it's running
curl -s http://localhost:8765/health  # Returns "OK"

# View web interface
open http://localhost:8765/
```

### 5.4 Emulator Network Configuration

The Android emulator accesses the host machine at `10.0.2.2`:

| Service | Host URL | Emulator URL |
|---------|----------|--------------|
| Log Server | http://localhost:8765 | http://10.0.2.2:8765 |
| Management Console | http://localhost:8766 | http://10.0.2.2:8766 |

The app is pre-configured to use these addresses.

## 6. Project Setup

### 6.1 Clone Repository

```bash
git clone <repository-url> unamentis-android
cd unamentis-android
```

### 6.2 Open in Android Studio

1. Open Android Studio
2. File > Open
3. Select the project directory
4. Wait for Gradle sync to complete

### 6.3 First Build

```bash
# Command line
./gradlew assembleDebug

# Or via script
./scripts/build.sh
```

### 6.4 Run on Emulator

```bash
# Start emulator
./scripts/launch-emulator.sh Pixel_8_Pro_API_34

# Install app
./scripts/install-emulator.sh

# Or via MCP
# Use mobile_install_app and mobile_launch_app tools
```

## 7. Code Quality Tools

### 7.1 ktlint

Add to `app/build.gradle.kts`:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

ktlint {
    android = true
    outputToConsole = true
    ignoreFailures = false
}
```

### 7.2 detekt

Add to `app/build.gradle.kts`:

```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

detekt {
    config.setFrom(files("$rootDir/config/detekt.yml"))
    buildUponDefaultConfig = true
}
```

### 7.3 Create detekt config

Create `config/detekt.yml`:

```yaml
build:
  maxIssues: 0

complexity:
  LongMethod:
    threshold: 60
  LongParameterList:
    functionThreshold: 8

naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
```

## 8. Testing Setup

### 8.1 Unit Tests

```bash
./gradlew test
# Or
./scripts/test-quick.sh
```

### 8.2 Instrumented Tests

Require a running emulator:

```bash
# Start emulator first
./scripts/launch-emulator.sh

# Run tests
./gradlew connectedAndroidTest
# Or
./scripts/test-all.sh
```

### 8.3 Test Coverage

```bash
./gradlew jacocoTestReport
```

## 9. Debugging Workflow

### 9.1 Complete Debug Session

```bash
# 1. Start log server
python3 scripts/log-server.py &

# 2. Start emulator
./scripts/launch-emulator.sh

# 3. Build and install
./scripts/build.sh && ./scripts/install-emulator.sh

# 4. Clear logs before reproducing issue
curl -s -X POST http://localhost:8765/clear

# 5. Reproduce the issue

# 6. Fetch logs
curl -s http://localhost:8765/logs | python3 -m json.tool

# 7. View in browser for real-time monitoring
open http://localhost:8765/
```

### 9.2 Using adb logcat

```bash
# Filter to app logs
adb logcat -v time -s UnaMentis:V *:S

# Clear and monitor
adb logcat -c && adb logcat -v time -s UnaMentis:V *:S

# Save to file
adb logcat -v time -s UnaMentis:V *:S > debug.log
```

### 9.3 Android Studio Debugger

1. Set breakpoints in code
2. Click "Debug 'app'" (bug icon)
3. Select running device
4. Reproduce issue to hit breakpoint

## 10. Native Code & llama.cpp Setup

The app includes native C++ code for two components:
- **Oboe** - Low-latency audio engine
- **llama.cpp** - On-device LLM inference

### 10.1 Prerequisites

Native code building requires:
- NDK 26.x (installed via SDK Manager)
- CMake 3.22.1+ (installed via SDK Manager)

Verify installation:
```bash
ls $ANDROID_HOME/ndk/
ls $ANDROID_HOME/cmake/
```

### 10.2 llama.cpp Submodule

llama.cpp is included as a git submodule for on-device LLM inference:

```bash
# Initialize submodule (automatically done by build.sh)
git submodule update --init --recursive

# Verify it's present
ls app/src/main/cpp/vendor/llama.cpp/
```

The submodule is pinned to a specific commit for API stability.

### 10.3 CMake Configuration

The native build is configured in `app/src/main/cpp/CMakeLists.txt`:

```cmake
# Two native libraries are built:
# 1. audio_engine - Oboe-based audio I/O
# 2. llama_inference - llama.cpp JNI wrapper

# llama.cpp settings for Android:
set(LLAMA_NATIVE OFF)      # Disable native CPU optimizations (cross-compile)
set(GGML_OPENMP OFF)       # Disable OpenMP (not available on Android)
set(GGML_ACCELERATE OFF)   # Apple-only framework
```

### 10.4 Build Architecture

The build produces native libraries for:
- `arm64-v8a` - Modern ARM64 devices (primary)
- `x86_64` - Emulator support

Libraries are placed in:
```
app/build/intermediates/cmake/debug/obj/
├── arm64-v8a/
│   ├── libaudio_engine.so
│   └── libllama_inference.so
└── x86_64/
    ├── libaudio_engine.so
    └── libllama_inference.so
```

### 10.5 GGUF Model Files

On-device LLM requires GGUF model files (not included in repo due to size):

| Model | Size | Download |
|-------|------|----------|
| Ministral-3B-Q4_K_M | ~2.1 GB | Via Settings > On-Device AI |
| TinyLlama-1.1B-Q4_K_M | ~670 MB | Via Settings > On-Device AI |

Models are stored in:
```
Android/data/com.unamentis/files/models/
```

For development, you can manually place GGUF files:
```bash
adb push ministral-3b-instruct-q4_k_m.gguf /sdcard/Android/data/com.unamentis/files/models/
```

### 10.6 Troubleshooting Native Builds

**CMake errors:**
```bash
# Clean and rebuild native code
./gradlew clean
./gradlew assembleDebug

# Or force CMake reconfigure
rm -rf app/.cxx/
./gradlew assembleDebug
```

**NDK not found:**
```bash
# Verify NDK path
echo $ANDROID_NDK_HOME
ls $ANDROID_HOME/ndk/

# Should show version like 26.3.11579264
```

**llama.cpp submodule missing:**
```bash
git submodule update --init --recursive
```

**JNI crash on model load:**
- Ensure model file exists at expected path
- Check device has sufficient RAM (6GB+ for Ministral-3B)
- Review logcat for native crash details:
  ```bash
  adb logcat -s LlamaInferenceJNI:V
  ```

---

## 11. Troubleshooting

### Emulator won't start

```bash
# Check available AVDs
emulator -list-avds

# Start with verbose output
emulator -avd Pixel_8_Pro_API_34 -verbose

# Cold boot (ignore snapshots)
emulator -avd Pixel_8_Pro_API_34 -no-snapshot-load
```

### Gradle sync fails

```bash
# Clear Gradle cache
./gradlew clean
rm -rf ~/.gradle/caches/

# Rebuild
./gradlew build --refresh-dependencies
```

### adb not found

```bash
# Check environment
echo $ANDROID_HOME
echo $PATH | tr ':' '\n' | grep android

# Reload shell
source ~/.zshrc
```

### MCP servers not connecting

```bash
# Restart Claude Code session
# Check .mcp.json syntax
cat .mcp.json | python3 -m json.tool

# Verify Java and Node.js are available
java --version
node --version
```

### App can't reach log server

```bash
# Verify log server is running
curl -s http://localhost:8765/health

# Check emulator network
adb shell ping -c 3 10.0.2.2
```

## 12. Quick Reference

### Daily Development Commands

```bash
# Start development session
python3 scripts/log-server.py &
./scripts/launch-emulator.sh

# Build and deploy
./scripts/build.sh && ./scripts/install-emulator.sh

# Run tests before commit
./scripts/health-check.sh

# View logs
open http://localhost:8765/
```

### MCP Workflow

```
1. Build:    execute_gradle_task(task: "assembleDebug")
2. Install:  mobile_install_app(app_path: "app/build/outputs/apk/debug/app-debug.apk")
3. Launch:   mobile_launch_app(package_name: "com.unamentis")
4. Screenshot: mobile_take_screenshot()
5. Interact: mobile_click_on_screen_at_coordinates(x, y)
6. Debug:    Check http://localhost:8765/ for logs
```
