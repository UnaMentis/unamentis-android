# UnaMentis Android - Physical Device Setup Guide

Complete instructions for installing and running UnaMentis on your Android phone.

---

## Table of Contents

1. [Device Requirements](#1-device-requirements)
2. [Pre-Installation Setup](#2-pre-installation-setup)
3. [Build the App](#3-build-the-app)
4. [Install on Your Phone](#4-install-on-your-phone)
5. [Server Setup (For Debugging)](#5-server-setup-for-debugging)
6. [First Launch Configuration](#6-first-launch-configuration)
7. [Optional: Cloud API Keys](#7-optional-cloud-api-keys)
8. [Optional: On-Device LLM (Fully Offline)](#8-optional-on-device-llm-fully-offline)
9. [Optional: External LLM Server Setup](#9-optional-external-llm-server-setup)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Device Requirements

### Minimum Requirements

| Requirement | Minimum |
|-------------|---------|
| Android Version | Android 9 (Pie) or newer |
| RAM | 6 GB |
| Storage | 500 MB free space |
| Network | WiFi or mobile data |

### Recommended Specifications

| Tier | RAM | Processor | Experience |
|------|-----|-----------|------------|
| **Flagship** | 12GB+ | Snapdragon 8 Gen 2+ | Full features, lowest latency |
| **Standard** | 8GB+ | Snapdragon 8 Gen 1+ | Great experience |
| **Minimum** | 6GB+ | Snapdragon 7 Gen 1+ | Basic functionality |

### Required Hardware

- **Microphone** - For voice input (built-in is fine)
- **Speaker** - For audio playback
- **Internet** - WiFi or mobile data for cloud services

---

## 2. Pre-Installation Setup

### 2.1 Enable Developer Options

1. Open **Settings** on your phone
2. Go to **About Phone** (may be under System)
3. Find **Build Number**
4. **Tap Build Number 7 times** until you see "You are now a developer!"

### 2.2 Enable USB Debugging

1. Go back to **Settings**
2. Open **System** > **Developer Options** (may vary by manufacturer)
3. Enable **USB Debugging**
4. (Optional) Enable **Install via USB** if available

### 2.3 Connect Your Phone

1. Connect your phone to your computer via USB cable
2. When prompted on your phone, tap **Allow** to authorize USB debugging
3. Check **Always allow from this computer** for convenience

### 2.4 Verify Connection

On your computer, run:

```bash
adb devices
```

You should see your device listed:
```text
List of devices attached
XXXXXXXXXX    device
```

If it shows "unauthorized", check your phone for the authorization prompt.

---

## 3. Build the App

### 3.1 Prerequisites on Your Computer

Ensure you have:
- Android Studio Ladybug 2024.2.1 or newer
- JDK 17+
- Android SDK with API 34

### 3.2 Build Debug APK

```bash
cd /path/to/unamentis-android

# Build the debug APK
./scripts/build.sh
```

Or manually:
```bash
./gradlew assembleDebug
```

The APK will be at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Install on Your Phone

### Option A: Via ADB (Recommended)

```bash
# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.unamentis/.MainActivity
```

### Option B: Via File Transfer

1. Copy `app-debug.apk` to your phone
2. Open a file manager on your phone
3. Navigate to the APK and tap it
4. Enable "Install unknown apps" if prompted
5. Complete the installation

### Option C: Wireless Debugging (Android 11+)

1. On your phone: **Developer Options** > **Wireless Debugging** > Enable
2. Tap **Pair device with pairing code**
3. On your computer:
   ```bash
   adb pair <phone-ip>:<pairing-port>
   # Enter the pairing code when prompted

   adb connect <phone-ip>:<debug-port>
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 5. Server Setup (For Debugging)

The log server is optional but highly recommended for debugging issues.

### 5.1 Find Your Computer's IP Address

**macOS:**
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
```

**Windows:**
```bash
ipconfig
```

Note your IP address (e.g., `192.168.1.100`).

### 5.2 Start the Log Server

```bash
# Install dependencies (first time only)
pip3 install aiohttp aiofiles

# Start the server
python3 scripts/log-server.py
```

You'll see:
```text
UnaMentis Remote Log Server v2.0

Server running on:
  - Terminal logs:  Displayed below
  - Web interface:  http://localhost:8765/
  - Network access: http://192.168.1.100:8765/
```

### 5.3 Configure the App

In the UnaMentis app:
1. Go to **Settings** > **Debug**
2. Set **Log Server IP** to your computer's IP (e.g., `192.168.1.100`)
3. Port should be `8765`

### 5.4 View Logs

- **Web interface:** Open `http://localhost:8765/` in your browser
- **Terminal:** Logs appear in real-time where you started the server
- **JSON API:** `curl http://localhost:8765/logs`

---

## 6. First Launch Configuration

### 6.1 Grant Permissions

When you first launch the app, grant these permissions:

| Permission | Purpose |
|------------|---------|
| **Microphone** | Voice input for tutoring sessions |
| **Notifications** | Session alerts and updates |

### 6.2 Choose a Configuration Preset

The app defaults to **FREE** mode which works immediately with no setup:

| Preset | STT | TTS | LLM | Cost |
|--------|-----|-----|-----|------|
| **FREE** (default) | Android | Android | PatchPanel | Free |
| **PREMIUM** | Deepgram | ElevenLabs | PatchPanel | API costs |
| **LOW_LATENCY** | Deepgram | ElevenLabs | OpenAI | API costs |
| **OFFLINE** | Android | Android | OnDevice | Free |

### 6.3 Included Models

The app includes these models **bundled in the APK** - no downloads required:

| Model | Size | Purpose |
|-------|------|---------|
| Silero VAD v5 | 2.3 MB | Voice Activity Detection |

### 6.4 Built-in Services (No API Keys Required)

| Service | Provider | Notes |
|---------|----------|-------|
| Speech-to-Text | Android Speech Recognition | Built into Android, works offline |
| Text-to-Speech | Android TTS | Built into Android, works offline |

To verify Android TTS is installed:
1. Go to phone **Settings** > **Accessibility** > **Text-to-Speech**
2. Ensure a TTS engine is installed and selected
3. Tap "Play" to test

---

## 7. Optional: Cloud API Keys

For enhanced quality, you can add API keys for cloud services.

### 7.1 Supported Cloud Services

| Service | Used For | Get API Key |
|---------|----------|-------------|
| **Deepgram** | High-quality STT | [console.deepgram.com](https://console.deepgram.com) |
| **ElevenLabs** | Premium TTS voices | [elevenlabs.io](https://elevenlabs.io) |
| **OpenAI** | GPT-4 LLM | [platform.openai.com](https://platform.openai.com) |
| **Anthropic** | Claude LLM | [console.anthropic.com](https://console.anthropic.com) |
| **AssemblyAI** | Alternative STT | [assemblyai.com](https://assemblyai.com) |
| **Groq** | Fast inference | [console.groq.com](https://console.groq.com) |

### 7.2 Add API Keys in the App

1. Open UnaMentis
2. Go to **Settings** > **Provider Keys**
3. Enter your API keys for desired services
4. Keys are stored encrypted on your device

### 7.3 Select Premium Providers

After adding keys:
1. Go to **Settings** > **Provider Configuration**
2. Choose **PREMIUM** or **LOW_LATENCY** preset
3. Or manually select individual providers

---

## 8. Optional: On-Device LLM (Fully Offline)

UnaMentis supports running LLM inference directly on your phone for completely offline tutoring. This uses the same llama.cpp engine as iOS for feature parity.

### 8.1 Device Requirements for On-Device LLM

| Model | RAM Required | Download Size |
|-------|--------------|---------------|
| **Ministral 3B** (recommended) | 6GB+ | ~2.1GB |
| **TinyLlama 1.1B** (fallback) | 3GB+ | ~670MB |

### 8.2 Download Models via App

1. Open UnaMentis
2. Go to **Settings** > **On-Device LLM**
3. Tap **Download Recommended Model**
4. Wait for download to complete (requires WiFi recommended)
5. Model is saved to device storage and ready for offline use

### 8.3 Enable Offline Mode

After downloading a model:
1. Go to **Settings** > **Provider Configuration**
2. Select **OFFLINE** preset, or:
   - Set **LLM Provider** to **OnDevice**
   - Set **STT Provider** to **Android**
   - Set **TTS Provider** to **Android**
3. Test offline by enabling Airplane Mode and starting a session

### 8.4 Storage Management

Models are stored in: `Android/data/com.unamentis/files/models/`

> **Note:** On Android 11 (API 30) and later, the `Android/data/` directory is
> protected by scoped storage. Standard file manager apps cannot access this
> location. Use the in-app deletion method below.

**Recommended: In-App Deletion**

To free up space, use the built-in model management:
1. Go to **Settings** > **On-Device LLM**
2. Tap **Delete Downloaded Models**
3. Confirm deletion when prompted

**Advanced Users: Alternative Methods**

If you need to manually remove model files (e.g., corrupted downloads):

- **Via ADB (requires USB debugging enabled):**
  ```bash
  adb shell rm -rf /sdcard/Android/data/com.unamentis/files/models/*
  ```

- **Via PC connection:** Connect your device to a computer. On Windows/macOS/Linux,
  you may be able to navigate to `Android/data/com.unamentis/files/models/` when
  the device is connected in File Transfer mode (though Android 11+ may still
  restrict access depending on your OS and device).

- **Uninstall and reinstall:** As a last resort, uninstalling the app will remove
  all downloaded models. Reinstall from the Play Store or APK afterward.

---

## 9. Optional: External LLM Server Setup

For additional flexibility, you can run an LLM server on your computer.

### 9.1 Option A: Ollama (Easiest)

On your computer:

**Installation Options:**

1. **Recommended: Use official installers** (safest)
   - Visit [ollama.com/download](https://ollama.com/download) for platform-specific installers
   - macOS: Download the `.dmg` installer
   - Windows: Download the `.exe` installer
   - Linux: Use your package manager if available, or follow the manual install below

2. **Linux manual install with verification:**
   ```bash
   # Download the install script first (don't pipe directly to shell)
   curl -fsSL https://ollama.com/install.sh -o ollama-install.sh

   # Review the script contents before running
   less ollama-install.sh

   # Make executable and run only after review
   chmod +x ollama-install.sh
   ./ollama-install.sh

   # Clean up
   rm ollama-install.sh
   ```

> **Security Note:** Never pipe untrusted scripts directly to `sh`. Always download,
> inspect, and verify scripts before execution. See [Ollama's official documentation](https://github.com/ollama/ollama)
> for additional installation methods and verification steps.

**After installation:**

```bash
# Download a model
ollama pull llama3.2:3b

# Start the server (runs on port 11434)
ollama serve
```

In the UnaMentis app:
1. Go to **Settings** > **Servers**
2. Add a new server:
   - **Name:** Local Ollama
   - **Host:** Your computer's IP (e.g., `192.168.1.100`)
   - **Port:** `11434`
   - **Type:** Ollama
3. Go to **Settings** > **Provider Configuration**
4. Set LLM provider to **Ollama**

### 9.2 Option B: llama.cpp Server

> **Note:** llama.cpp is included as a git submodule in this repository for on-device inference.
> After cloning the repo, run `git submodule update --init --recursive` to fetch it.
> The build scripts handle this automatically.

To run a llama.cpp server on your computer for phone access:

> **Security Note:** Always verify downloaded model files before use. Model files from
> untrusted sources could be corrupted or malicious. HuggingFace model repositories
> typically include SHA256 checksums in `.sha256` files or in the model card README.
> Never skip integrity verification.

```bash
# Build llama.cpp (from the submodule or a separate clone)
cd app/src/main/cpp/vendor/llama.cpp
mkdir build && cd build
cmake ..
make -j

# Download a model (GGUF format)
# Example: Llama 3.2 3B
wget https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf

# IMPORTANT: Verify the download integrity
# 1. Get the expected SHA256 from the model's HuggingFace page
#    (look for a .sha256 file or checksums in the model card/README)
# 2. Verify the downloaded file:
sha256sum Llama-3.2-3B-Instruct-Q4_K_M.gguf
# 3. Compare the output with the expected checksum
#    If they don't match, DELETE the file and re-download:
#    rm Llama-3.2-3B-Instruct-Q4_K_M.gguf
#    DO NOT proceed with a file that fails verification

# Start the server (only after successful verification)
./bin/llama-server -m Llama-3.2-3B-Instruct-Q4_K_M.gguf --host 0.0.0.0 --port 8080
```

Configure in the app:
- **Host:** Your computer's IP
- **Port:** `8080`
- **Type:** llama.cpp

### 9.3 Recommended Local Models

| Model | Size | RAM Required | Quality |
|-------|------|--------------|---------|
| Llama 3.2 1B | ~1 GB | 4 GB | Basic |
| Llama 3.2 3B | ~2.5 GB | 6 GB | Good |
| Mistral 7B | ~5 GB | 10 GB | Excellent |
| Llama 3.1 8B | ~6 GB | 12 GB | Best |

---

## 10. Troubleshooting

### App Won't Install

**"App not installed" error:**
- Uninstall any previous version first
- Enable "Install unknown apps" for your file manager
- Ensure sufficient storage space

**"Blocked by Play Protect":**
1. Tap "More details"
2. Tap "Install anyway"

### Microphone Not Working

1. Check app permissions: **Settings** > **Apps** > **UnaMentis** > **Permissions** > **Microphone**
2. Ensure no other app is using the microphone
3. Restart the app

### No Audio Output

1. Check phone volume (media volume, not ringer)
2. Verify TTS is installed: **Settings** > **Accessibility** > **Text-to-Speech**
3. Download voice data if prompted

### Can't Connect to Log Server

1. Ensure phone and computer are on the **same WiFi network**
2. Check firewall isn't blocking port 8765
3. Verify server is running: `curl http://localhost:8765/health`
4. Try disabling VPN if active

### High Latency / Slow Response

1. Check internet connection speed
2. Switch to Android STT/TTS (no network needed)
3. If using local LLM, ensure server has adequate resources
4. Move closer to WiFi router

### App Crashes on Launch

1. Clear app data: **Settings** > **Apps** > **UnaMentis** > **Storage** > **Clear Data**
2. Reinstall the APK
3. Check for crash logs via `adb logcat -s UnaMentis:V`

### Session Ends Unexpectedly

1. Disable battery optimization for UnaMentis
   - **Settings** > **Battery** > **Battery Optimization** > **UnaMentis** > **Don't Optimize**
2. Check device isn't overheating
3. Ensure stable network connection

---

## Quick Reference Card

### Build & Install
```bash
# Build
./scripts/build.sh

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.unamentis/.MainActivity
```

### Log Server
```bash
# Start server
python3 scripts/log-server.py

# View logs
open http://localhost:8765/

# Clear logs
curl -X POST http://localhost:8765/clear
```

### ADB Debugging
```bash
# View app logs
adb logcat -s UnaMentis:V

# Screenshot
adb exec-out screencap -p > screenshot.png

# Screen record
adb shell screenrecord /sdcard/video.mp4
```

---

## Network Ports Reference

| Service | Port | Access From Phone |
|---------|------|-------------------|
| Log Server | 8765 | `http://<computer-ip>:8765` |
| Management Console | 8766 | `http://<computer-ip>:8766` |
| Ollama | 11434 | `http://<computer-ip>:11434` |
| llama.cpp | 8080 | `http://<computer-ip>:8080` |

---

## Support

For issues or questions:
- Check existing documentation in `docs/`
- Review logs via the log server
- Use `adb logcat` for detailed debugging
