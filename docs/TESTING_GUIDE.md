# UnaMentis Android - Comprehensive Testing Guide

This guide provides step-by-step instructions for testing the complete UnaMentis Android application after all phases are implemented.

## Prerequisites

### 1. Development Environment Setup

**Required Software:**
- Android Studio Ladybug (2024.2.1) or newer
- JDK 17 or newer
- Android SDK API 34
- Android NDK 26.x
- Python 3.12+ (for log server)
- Node.js 20+ (for MCP servers)

**Verify Installation:**
```bash
# Check Java version
java --version  # Should show Java 17+

# Check Android SDK
echo $ANDROID_HOME  # Should point to SDK directory

# Check Python
python3 --version  # Should show 3.12+

# Check Node.js
node --version  # Should show v20+
```

### 2. MCP Server Configuration

**Verify MCP servers are configured:**
```bash
cd /Users/cygoerdt/unamentis-android
claude mcp list
# Should show:
# - mobile-mcp: Connected
# - gradle-mcp-server: Connected
```

If not connected, restart Claude Code or check `.mcp.json` configuration.

### 3. Silero VAD Model

**Download and place the model:**
1. Download Silero VAD from: https://github.com/snakers4/silero-vad
2. Convert to TensorFlow Lite (if not already in TFLite format)
3. Place `silero_vad.tflite` in `app/src/main/assets/`
4. Verify file size is approximately 1-2 MB

## Phase 1: Build & Lint Validation

### 1.1 Clean Build

```bash
cd /Users/cygoerdt/unamentis-android

# Clean previous builds
./gradlew clean

# Build debug APK
./scripts/build.sh

# Verify APK was created
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

**Expected Output:**
- APK file exists
- Size: ~15-25 MB (depending on dependencies)
- Build completes without errors

### 1.2 Lint Checks

```bash
# Run all lint checks
./scripts/lint.sh
```

**Expected Output:**
- ktlint: 0 violations
- detekt: 0 issues
- All checks pass

### 1.3 Unit Tests

```bash
# Run quick unit tests
./scripts/test-quick.sh
```

**Expected Output:**
- All model tests pass (CurriculumTest, SessionTest)
- All audio utility tests pass (AudioUtilsTest, AudioEngineTest)
- All API client tests pass (ApiClientTest)
- Database tests pass
- 0 failures

### 1.4 Health Check

```bash
# Combined lint + quick tests
./scripts/health-check.sh
```

**Expected Output:**
```
=== Health Check ===
Step 1/2: Lint checks...
✓ ktlint passed
✓ detekt passed

Step 2/2: Quick tests...
✓ All tests passed (XX tests)

=== Health check passed! ===
```

## Phase 2: Emulator Setup & Installation

### 2.1 Create Android Emulator

```bash
# List available system images
sdkmanager --list | grep "system-images;android-34"

# Download system image if needed
sdkmanager "system-images;android-34;google_apis;x86_64"

# Create Pixel 8 Pro emulator
avdmanager create avd -n Pixel_8_Pro_API_34 \
  -k "system-images;android-34;google_apis;x86_64" \
  -d "pixel_8_pro" \
  --force

# Configure emulator settings
echo "hw.ramSize=4096" >> ~/.android/avd/Pixel_8_Pro_API_34.avd/config.ini
echo "hw.gpu.enabled=yes" >> ~/.android/avd/Pixel_8_Pro_API_34.avd/config.ini
```

### 2.2 Start Emulator

```bash
# Start emulator
./scripts/launch-emulator.sh Pixel_8_Pro_API_34

# Or use emulator command directly
emulator -avd Pixel_8_Pro_API_34 -no-snapshot-load &

# Wait for boot to complete
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
```

**Verify Emulator:**
```bash
# Check device is online
adb devices
# Should show: emulator-5554  device

# Check Android version
adb shell getprop ro.build.version.release
# Should show: 14
```

### 2.3 Install APK on Emulator

```bash
# Install using script
./scripts/install-emulator.sh

# Or install manually
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Expected Output:**
```
Performing Streamed Install
Success
```

### 2.4 Verify Installation

```bash
# Check app is installed
adb shell pm list packages | grep unamentis
# Should show: package:com.unamentis.debug

# Check app version
adb shell dumpsys package com.unamentis.debug | grep versionName
```

## Phase 3: Server Setup

### 3.1 Start Log Server

**Terminal 1:**
```bash
cd /Users/cygoerdt/unamentis-android
python3 scripts/log-server.py &

# Verify log server is running
curl -s http://localhost:8765/health
# Should return: OK

# Open web interface (optional)
open http://localhost:8765/
```

### 3.2 Start Management Console (if available)

**Terminal 2:**
```bash
# If you have the management console server
# Start it on port 8766
# (Instructions depend on your server implementation)
```

### 3.3 Verify Network Access from Emulator

```bash
# Test log server access from emulator
adb shell curl -s http://10.0.2.2:8765/health
# Should return: OK

# Test management console (if running)
adb shell curl -s http://10.0.2.2:8766/health
# Should return: {"status":"healthy",...}
```

## Phase 4: App Launch & Basic Functionality

### 4.1 Launch App via MCP

Using MCP tools (autonomous):
```
mobile_launch_app(package_name: "com.unamentis.debug")
mobile_take_screenshot()
```

Or manually:
```bash
# Launch app
adb shell am start -n com.unamentis.debug/com.unamentis.MainActivity

# Verify app is running
adb shell dumpsys activity activities | grep unamentis
```

### 4.2 Navigation Test

**Test all 6 tabs:**
1. Tap Session tab - verify placeholder screen
2. Tap Learning tab - verify placeholder screen
3. Tap To-Do tab - verify placeholder screen
4. Tap History tab - verify placeholder screen
5. Tap Analytics tab - verify placeholder screen
6. Tap Settings tab - verify placeholder screen

**Using MCP:**
```
mobile_list_elements_on_screen()  # Find tab coordinates
mobile_click_on_screen_at_coordinates(x, y)  # Tap each tab
mobile_take_screenshot()  # Capture each screen
```

**Manual:**
- Physically interact with emulator UI
- Verify each tab displays correct title
- Verify no crashes (check logcat)

### 4.3 Theme & Dark Mode Test

```bash
# Switch to dark mode
adb shell "cmd uimode night yes"

# Take screenshot in dark mode
mobile_take_screenshot()

# Switch back to light mode
adb shell "cmd uimode night no"

# Take screenshot in light mode
mobile_take_screenshot()
```

**Verify:**
- App responds to system theme changes
- Colors are appropriate for each mode
- Text remains readable

## Phase 5: Audio Pipeline Testing

### 5.1 Audio Permissions

**Grant audio permissions:**
```bash
adb shell pm grant com.unamentis.debug android.permission.RECORD_AUDIO
adb shell pm grant com.unamentis.debug android.permission.MODIFY_AUDIO_SETTINGS
```

**Verify permissions:**
```bash
adb shell dumpsys package com.unamentis.debug | grep permission
# Should show RECORD_AUDIO granted
```

### 5.2 Native Library Loading

**Check logs for native library load:**
```bash
# Clear logs
adb logcat -c

# Launch app
adb shell am start -n com.unamentis.debug/com.unamentis.MainActivity

# Check for native library logs
adb logcat -d | grep -i "audio_engine"
# Should show: "Native AudioEngine created"
```

### 5.3 VAD Model Loading

**Verify Silero VAD loads:**
```bash
# Check logs for VAD initialization
adb logcat -d | grep -i "SileroVAD"
# Should show: "Model loaded successfully"

# If you see "Model file not found", verify:
ls -lh app/src/main/assets/silero_vad.tflite
```

### 5.4 Audio Level Monitoring Test

**Navigate to Session screen:**
1. Launch app
2. Tap Session tab
3. Start a session (when implemented)
4. Speak into microphone
5. Verify audio level visualization updates

**Check logs:**
```bash
adb logcat -d | grep -i "audio"
# Should show RMS/peak level updates
```

## Phase 6: Provider Integration Testing

### 6.1 STT Provider Tests

**Test each STT provider:**

**Deepgram (WebSocket):**
1. Configure API key in Settings
2. Start session with Deepgram STT
3. Speak clearly
4. Verify transcript appears in real-time
5. Check latency (should be <300ms median)

**AssemblyAI (WebSocket):**
1. Configure API key
2. Switch provider in Settings
3. Start session
4. Verify transcription works

**Android SpeechRecognizer (On-device):**
1. Switch to Android STT
2. Start session
3. Verify works offline (disable WiFi)

**Check logs:**
```bash
adb logcat -d | grep -E "(Deepgram|AssemblyAI|AndroidSTT)"
# Should show connection events, transcription results
```

### 6.2 TTS Provider Tests

**Test each TTS provider:**

**ElevenLabs (WebSocket):**
1. Configure API key
2. Trigger AI response
3. Verify voice playback starts quickly
4. Check TTFB (should be <200ms median)

**Android TTS (On-device):**
1. Switch to Android TTS
2. Trigger AI response
3. Verify works offline

**Check logs:**
```bash
adb logcat -d | grep -E "(ElevenLabs|AndroidTTS)"
# Should show synthesis events, audio chunk delivery
```

### 6.3 LLM Provider Tests

**Test each LLM provider:**

**OpenAI (Streaming):**
1. Configure API key
2. Ask a question
3. Verify streaming response (tokens appear incrementally)
4. Check TTFT (should be <200ms median)

**Anthropic Claude (Streaming):**
1. Configure API key
2. Switch provider
3. Verify streaming works

**Check logs:**
```bash
adb logcat -d | grep -E "(OpenAI|Anthropic)"
# Should show streaming token events
```

### 6.4 Provider Failover Test

1. Configure primary STT provider with invalid API key
2. Configure secondary STT provider with valid key
3. Start session
4. Verify automatic failover to secondary provider
5. Check logs for failover event

## Phase 7: Session Management Testing

### 7.1 Complete Voice Session Test

**End-to-end conversation:**
1. Start session
2. Wait for "listening" indicator
3. Speak: "Hello, can you hear me?"
4. Wait for AI response
5. Verify full pipeline works (STT → LLM → TTS)
6. Measure E2E latency (should be <500ms median)

**Check logs:**
```bash
# Fetch logs from log server
curl -s http://localhost:8765/logs | python3 -m json.tool

# Should show complete turn:
# - VAD detected speech
# - STT transcription
# - LLM generation
# - TTS synthesis
# - Audio playback
```

### 7.2 Barge-in Test

1. Start session
2. Wait for AI to start speaking
3. Interrupt by speaking
4. Verify AI stops immediately
5. Verify your interruption is processed
6. Check 600ms confirmation window works

### 7.3 Long Session Stability Test

**90-minute session test:**
1. Clear logs: `curl -X POST http://localhost:8765/clear`
2. Start session
3. Conduct conversation for 90 minutes
4. Monitor metrics:
   ```bash
   # Check memory usage
   adb shell dumpsys meminfo com.unamentis.debug

   # Check thermal status
   adb shell dumpsys thermalservice
   ```
5. Verify no crashes
6. Check memory growth <50MB
7. Check thermal throttle events <3

### 7.4 Session Persistence Test

1. Start session with curriculum topic
2. Have a conversation (multiple turns)
3. Stop session
4. Navigate to History tab
5. Verify session appears with correct:
   - Timestamp
   - Duration
   - Turn count
   - Cost
6. Tap session to view full transcript
7. Verify all messages are saved

## Phase 8: Database Testing

### 8.1 Curriculum Download Test

1. Navigate to Learning tab
2. Browse available curricula (from server)
3. Download a curriculum
4. Verify download progress
5. Verify curriculum appears in local list
6. Tap curriculum to view topics
7. Close app and reopen
8. Verify curriculum persists

**Check database:**
```bash
# Pull database from device
adb pull /data/data/com.unamentis.debug/databases/unamentis.db

# Inspect with sqlite3
sqlite3 unamentis.db "SELECT * FROM curricula;"
```

### 8.2 Progress Tracking Test

1. Start session with a curriculum topic
2. Complete some segments
3. Stop session
4. Check topic progress:
   - Time spent updated
   - Mastery level updated
   - Completed segments marked
5. Resume topic
6. Verify continues from correct position

**Check database:**
```bash
sqlite3 unamentis.db "SELECT * FROM topic_progress;"
```

## Phase 9: Performance Benchmarking

### 9.1 Latency Metrics

**Capture metrics during a session:**
```bash
# Start session, have conversation
# After 5+ turns, fetch metrics from log server

curl -s http://localhost:8765/logs | \
  python3 -c "
import sys, json
logs = json.load(sys.stdin)
stt_latencies = [l['metadata'].get('latency_ms') for l in logs if l.get('label') == 'STT']
print(f'STT median: {sorted(stt_latencies)[len(stt_latencies)//2]}ms')
"
```

**Verify targets:**
- STT median: <300ms ✓
- LLM TTFT median: <200ms ✓
- TTS TTFB median: <200ms ✓
- E2E turn median: <500ms ✓

### 9.2 Memory Profiling

```bash
# Profile during 90-minute session
adb shell dumpsys meminfo com.unamentis.debug > mem_start.txt

# ... wait 90 minutes ...

adb shell dumpsys meminfo com.unamentis.debug > mem_end.txt

# Compare memory growth
# Should be <50MB increase
```

### 9.3 Battery Consumption

```bash
# Reset battery stats
adb shell dumpsys batterystats --reset

# Run 60-minute session
# ...

# Check battery consumption
adb shell dumpsys batterystats com.unamentis.debug

# Verify: <15% drain per hour
```

## Phase 10: UI/UX Testing

### 10.1 Accessibility Test

**Enable TalkBack:**
```bash
adb shell settings put secure enabled_accessibility_services \
  com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService

adb shell settings put secure accessibility_enabled 1
```

**Test with TalkBack:**
1. Navigate all screens with TalkBack
2. Verify all interactive elements have content descriptions
3. Verify focus order is logical
4. Verify gestures work correctly

**Disable TalkBack:**
```bash
adb shell settings put secure accessibility_enabled 0
```

### 10.2 Orientation Change Test

```bash
# Switch to landscape
adb shell content insert --uri content://settings/system \
  --bind name:s:user_rotation --bind value:i:1

# Use app in landscape
# Verify layout adapts correctly

# Switch back to portrait
adb shell content insert --uri content://settings/system \
  --bind name:s:user_rotation --bind value:i:0
```

### 10.3 Font Scaling Test

```bash
# Set large font size
adb shell settings put system font_scale 1.3

# Verify text remains readable
# Verify layouts don't break

# Reset font size
adb shell settings put system font_scale 1.0
```

## Phase 11: Error Handling & Edge Cases

### 11.1 Network Failure Test

1. Start session with cloud providers
2. During conversation, disable WiFi:
   ```bash
   adb shell svc wifi disable
   ```
3. Verify graceful error handling
4. Re-enable WiFi:
   ```bash
   adb shell svc wifi enable
   ```
5. Verify app recovers

### 11.2 API Key Validation Test

1. Enter invalid API key in Settings
2. Try to start session
3. Verify clear error message
4. Verify app doesn't crash

### 11.3 Microphone Permission Denial

```bash
# Revoke microphone permission
adb shell pm revoke com.unamentis.debug android.permission.RECORD_AUDIO

# Try to start session
# Verify permission request appears
# Verify helpful error if denied
```

### 11.4 Low Storage Test

```bash
# Check available storage
adb shell df /data

# If testing on low storage scenario:
# Verify app handles download failures gracefully
# Verify clear error messages
```

## Phase 12: Integration Testing with MCP

### 12.1 Autonomous Build-Test-Debug Cycle

**Using MCP tools in Claude Code:**

1. **Build:**
   ```
   execute_gradle_task(task: "assembleDebug")
   ```

2. **Install:**
   ```
   mobile_install_app(app_path: "app/build/outputs/apk/debug/app-debug.apk")
   ```

3. **Launch:**
   ```
   mobile_launch_app(package_name: "com.unamentis.debug")
   ```

4. **Capture Logs:**
   ```bash
   curl -s http://localhost:8765/logs | python3 -m json.tool
   ```

5. **Screenshot:**
   ```
   mobile_take_screenshot()
   ```

6. **Interact:**
   ```
   mobile_list_elements_on_screen()
   mobile_click_on_screen_at_coordinates(x: 200, y: 400)
   mobile_type_keys(text: "test message")
   ```

7. **Debug:**
   - Analyze logs for issues
   - Fix code
   - Repeat cycle

## Testing Checklist

Use this checklist to track testing progress:

### Build & Setup
- [ ] Clean build completes successfully
- [ ] Lint checks pass with 0 violations
- [ ] All unit tests pass
- [ ] Health check passes
- [ ] Emulator created and running
- [ ] APK installs on emulator
- [ ] Log server running and accessible
- [ ] MCP servers connected

### Core Functionality
- [ ] App launches without crashes
- [ ] All 6 tabs navigate correctly
- [ ] Light/dark theme switching works
- [ ] Permissions granted correctly
- [ ] Native libraries load successfully
- [ ] VAD model loads successfully

### Audio Pipeline
- [ ] Audio capture works
- [ ] Audio level monitoring updates
- [ ] VAD detects speech correctly
- [ ] Audio playback works
- [ ] No audio artifacts or glitches

### Provider Integration
- [ ] Deepgram STT works (latency <300ms)
- [ ] AssemblyAI STT works
- [ ] Android STT works offline
- [ ] ElevenLabs TTS works (TTFB <200ms)
- [ ] Android TTS works offline
- [ ] OpenAI LLM works (TTFT <200ms)
- [ ] Anthropic LLM works
- [ ] Provider failover works

### Session Management
- [ ] End-to-end conversation works (E2E <500ms)
- [ ] Barge-in interruption works
- [ ] Session state transitions correctly
- [ ] 90-minute sessions complete without crash
- [ ] Memory growth <50MB over 90 minutes
- [ ] Thermal throttling <3 events per session
- [ ] Sessions persist to database
- [ ] Transcripts save correctly

### Database & Persistence
- [ ] Curricula download and save
- [ ] Topic progress tracks correctly
- [ ] Session history displays correctly
- [ ] Data persists after app restart

### Performance
- [ ] STT latency meets targets
- [ ] LLM TTFT meets targets
- [ ] TTS TTFB meets targets
- [ ] E2E latency meets targets
- [ ] Battery consumption <15%/hour
- [ ] No memory leaks detected

### Accessibility
- [ ] TalkBack navigation works
- [ ] All elements have content descriptions
- [ ] Touch targets ≥48dp
- [ ] Font scaling works correctly
- [ ] Orientation changes handled
- [ ] Reduce motion respected

### Error Handling
- [ ] Network failures handled gracefully
- [ ] Invalid API keys show clear errors
- [ ] Permission denials handled
- [ ] Low storage handled
- [ ] No crashes in edge cases

## Debugging Resources

### Log Analysis

**View app logs:**
```bash
adb logcat -s UnaMentis:V
```

**View native logs:**
```bash
adb logcat -s UnaMentis-Audio:V UnaMentis-JNI:V
```

**View TFLite logs:**
```bash
adb logcat -s SileroVAD:V
```

**Export logs:**
```bash
adb logcat -d > debug_session.log
```

### Remote Log Server

**View logs in browser:**
```
http://localhost:8765/
```

**Fetch logs via API:**
```bash
curl -s http://localhost:8765/logs | python3 -m json.tool > logs.json
```

**Clear logs:**
```bash
curl -X POST http://localhost:8765/clear
```

### Performance Profiling

**CPU profiling:**
- Android Studio → Profiler → CPU
- Record 60 seconds during session
- Analyze hotspots

**Memory profiling:**
- Android Studio → Profiler → Memory
- Record 90-minute session
- Check for leaks

**Network profiling:**
- Android Studio → Profiler → Network
- Monitor WebSocket connections
- Verify data transfer rates

## Success Criteria

The app is ready for production when:

✅ **All tests pass** - 100% of checklist items complete
✅ **Performance targets met** - All latency/resource metrics within spec
✅ **No crashes** - 90-minute sessions complete reliably
✅ **Accessibility compliant** - Full TalkBack support
✅ **Error handling robust** - All edge cases handled gracefully
✅ **Documentation complete** - All features documented

---

**Last Updated:** Phase 3 Implementation
**Next Review:** After Phase 6 completion
