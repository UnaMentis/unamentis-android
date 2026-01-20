# Quick Start Guide

Get UnaMentis Android running in under 10 minutes.

## Prerequisites Checklist

Before starting, ensure you have:

- [ ] **Android Studio** Ladybug 2024.2.1 or newer
- [ ] **JDK 17+** installed
- [ ] **Node.js 20+** for MCP servers
- [ ] **Python 3.12+** for log server

Verify your setup:

```bash
java --version     # Should show 17+
node --version     # Should show v20+
python3 --version  # Should show 3.12+
```

---

## Step 1: Clone & Configure

```bash
# Clone the repository
git clone <repository-url> unamentis-android
cd unamentis-android

# Add Android SDK to your PATH (add to ~/.zshrc or ~/.bashrc)
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools

# Reload shell
source ~/.zshrc  # or ~/.bashrc
```

---

## Step 2: Install MCP Servers

```bash
# Create directory for gradle-mcp-server
mkdir -p ~/mcp-servers/gradle-mcp-server

# Download gradle-mcp-server
curl -fSL -o ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar \
  "https://github.com/IlyaGulya/gradle-mcp-server/releases/latest/download/gradle-mcp-server-all.jar"

# Verify download
ls -la ~/mcp-servers/gradle-mcp-server/
```

Update `.mcp.json` with your paths (replace `<username>` with your actual username):

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

---

## Step 3: Build the App

```bash
# Build debug APK
./scripts/build.sh
```

Expected output:
```
Building debug APK...
BUILD SUCCESSFUL
Build complete: app/build/outputs/apk/debug/app-debug.apk
Size: ~15-25 MB
```

---

## Step 4: Create & Start Emulator

### Option A: Via Android Studio

1. Open Android Studio
2. Tools → Device Manager
3. Create Device → Pixel 8 Pro → API 34
4. Start the emulator

### Option B: Via Command Line

```bash
# Download system image
sdkmanager "system-images;android-34;google_apis_playstore;arm64-v8a"

# Create emulator
avdmanager create avd -n Pixel_8_Pro_API_34 \
  -k "system-images;android-34;google_apis_playstore;arm64-v8a" \
  -d "pixel_8_pro"

# Start emulator
./scripts/launch-emulator.sh Pixel_8_Pro_API_34
```

Wait for the emulator to fully boot:
```bash
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
echo "Emulator ready!"
```

---

## Step 5: Install & Launch

```bash
# Install APK
./scripts/install-emulator.sh

# Launch app
adb shell am start -n com.unamentis/.MainActivity
```

The app should now be running on your emulator!

---

## Step 6: Start Log Server (Optional but Recommended)

```bash
# Start log server in background
python3 scripts/log-server.py &

# Verify it's running
curl -s http://localhost:8765/health  # Returns "OK"

# Open web interface
open http://localhost:8765/
```

---

## Step 7: Verify Tests

```bash
# Run unit tests
./scripts/test-quick.sh

# Run pre-commit health check
./scripts/health-check.sh
```

All tests should pass before making changes.

---

## Quick Reference

### Daily Commands

```bash
# Build and install
./scripts/build.sh && ./scripts/install-emulator.sh

# Run tests
./scripts/test-quick.sh

# Pre-commit check
./scripts/health-check.sh

# View logs
open http://localhost:8765/

# Clear logs
curl -s -X POST http://localhost:8765/clear
```

### MCP Commands (in Claude Code)

```
# List devices
mobile_list_available_devices

# Take screenshot
mobile_take_screenshot

# Build app
execute_gradle_task(task: "assembleDebug")

# Run tests
run_gradle_tests
```

---

## Troubleshooting

### "command not found: adb"

```bash
# Verify ANDROID_HOME is set
echo $ANDROID_HOME

# Add to PATH
export PATH=$PATH:$ANDROID_HOME/platform-tools
source ~/.zshrc
```

### Build fails with "SDK not found"

```bash
# Install required SDK
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

### Emulator won't start

```bash
# List available emulators
emulator -list-avds

# Cold boot (ignore snapshots)
emulator -avd Pixel_8_Pro_API_34 -no-snapshot-load
```

### MCP servers not connecting

```bash
# Verify Node.js
node --version  # Should be 20+

# Verify Java
java --version  # Should be 17+

# Restart Claude Code session
```

---

## Next Steps

1. **Read the full docs** — [DEV_ENVIRONMENT.md](DEV_ENVIRONMENT.md)
2. **Understand the architecture** — [ARCHITECTURE.md](ARCHITECTURE.md)
3. **Learn testing practices** — [TESTING_GUIDE.md](TESTING_GUIDE.md)
4. **Start contributing** — [CONTRIBUTING.md](../CONTRIBUTING.md)

---

## Need Help?

- Check [existing documentation](.)
- Search [issues](https://github.com/owner/unamentis-android/issues)
- Open a new issue with the `question` label
