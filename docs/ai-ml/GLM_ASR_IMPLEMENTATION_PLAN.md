# GLM-ASR Full Implementation Plan for Android

**Purpose:** Complete implementation plan to achieve full feature parity with iOS for GLM-ASR (speech-to-text) functionality.

**Date:** February 2026

---

## Executive Summary

The iOS repository has a comprehensive GLM-ASR implementation with both **server-based** and **on-device** speech-to-text capabilities. The Android repo already has the server-based implementation complete but is **missing the on-device implementation**. This plan details how to implement full feature parity.

---

## Current State Analysis

### What iOS Has (Complete)

| Component | File | Status | Description |
|-----------|------|--------|-------------|
| Server STT Service | `GLMASRSTTService.swift` | ✅ Complete | WebSocket streaming to self-hosted server |
| Health Monitor | `GLMASRHealthMonitor.swift` | ✅ Complete | Server health tracking with failover |
| Provider Router | `STTProviderRouter.swift` | ✅ Complete | Intelligent routing between providers |
| **On-Device STT** | `GLMASROnDeviceSTTService.swift` | ✅ Complete | CoreML + llama.cpp on-device inference |
| STT Protocol | `STTService.swift` | ✅ Complete | Protocol with `glmASRNano` and `glmASROnDevice` providers |
| Documentation | `GLM_ASR_*.md` | ✅ Complete | 4 comprehensive guides |
| Unit Tests | `GLMASRSTTServiceTests.swift` | ✅ Complete | 12+ tests |
| Integration Tests | `GLMASRIntegrationTests.swift` | ✅ Complete | 7+ tests |

### What Android Has (Partially Complete)

| Component | File | Status | Description |
|-----------|------|--------|-------------|
| Server STT Service | `GLMASRSTTService.kt` | ✅ Complete | WebSocket streaming implementation |
| Health Monitor | `GLMASRHealthMonitor.kt` | ✅ Complete | Uses `ProviderHealthMonitor` |
| Provider Router | `STTProviderRouter.kt` | ✅ Complete | Priority-based routing with failover |
| **On-Device STT** | N/A | ❌ Missing | **MAIN GAP** |
| STT Protocol | `STTService.kt` (model) | ⚠️ Partial | Missing `glmASROnDevice` provider enum |
| Documentation | N/A | ❌ Missing | No GLM-ASR specific docs |
| Unit Tests | `GLMASRSTTServiceTest.kt` | ⚠️ Partial | Basic tests only |
| Integration Tests | N/A | ❌ Missing | No GLM-ASR integration tests |

---

## Implementation Plan

### Phase 1: On-Device GLM-ASR Service (CRITICAL)

This is the main missing component - running GLM-ASR-Nano entirely on-device using Android's ML capabilities.

#### 1.1 Create On-Device Service Architecture

**Target File:** `app/src/main/kotlin/com/unamentis/services/stt/GLMASROnDeviceSTTService.kt`

The iOS implementation uses a 4-stage pipeline:
```
Audio (16kHz PCM)
    ↓
Mel Spectrogram (128 x 3000)
    ↓
Whisper Encoder (CoreML → Android: ONNX/TFLite)
    ↓
Audio Adapter (CoreML → Android: ONNX/TFLite)
    ↓
Text Decoder (llama.cpp GGUF)
    ↓
Transcript
```

**Android Equivalent:**
- CoreML → **ONNX Runtime** or **TensorFlow Lite**
- llama.cpp → Already integrated in Android codebase

#### 1.2 Model Files Required

| Model | iOS Size | Android Format | Purpose |
|-------|----------|----------------|---------|
| GLMASRWhisperEncoder | 1.2 GB | `.onnx` or `.tflite` | Audio feature extraction |
| GLMASRAudioAdapter | 56 MB | `.onnx` or `.tflite` | Feature alignment |
| GLMASREmbedHead | 232 MB | `.onnx` or `.tflite` | Token embedding |
| glm-asr-nano-q4km | 935 MB | `.gguf` | Text decoding (llama.cpp) |

**Total: ~2.4 GB**

#### 1.3 Implementation Steps

1. **Create Configuration Class**
```kotlin
data class GLMASROnDeviceConfig(
    val modelDirectory: File,
    val maxAudioDuration: Float = 30.0f,
    val useGPU: Boolean = true,
    val gpuLayers: Int = 99
)
```

2. **Create On-Device Service**
```kotlin
class GLMASROnDeviceSTTService(
    private val config: GLMASROnDeviceConfig,
    private val llamaClient: LocalLLMClient
) : STTService {
    // Implementation following iOS pattern
}
```

3. **Implement Mel Spectrogram Computation**
   - Use Android's AudioRecord for audio input
   - Compute mel spectrogram using FFT (via JNI or Kotlin DSP library)

4. **Implement ONNX/TFLite Inference**
   - Load Whisper encoder model
   - Load audio adapter model
   - Load embed head model

5. **Integrate with Existing llama.cpp**
   - The Android repo already has llama.cpp JNI bindings
   - Use existing `LocalLLMClient` for text decoding

### Phase 2: Provider Enum Update

**Target File:** `app/src/main/kotlin/com/unamentis/data/model/Providers.kt`

Add the missing on-device provider:

```kotlin
enum class STTProvider(
    val displayName: String,
    val identifier: String,
    val requiresNetwork: Boolean,
    val costPerHour: BigDecimal,
    val isOnDevice: Boolean
) {
    ASSEMBLY_AI("AssemblyAI", "assemblyai", true, BigDecimal("0.37"), false),
    DEEPGRAM_NOVA3("Deepgram Nova-3", "deepgram", true, BigDecimal("0.258"), false),
    GROQ_WHISPER("Groq Whisper", "groq", true, BigDecimal.ZERO, false),
    ANDROID_SPEECH("Android Speech", "android", false, BigDecimal.ZERO, true),
    GLM_ASR_NANO("GLM-ASR-Nano (Server)", "glm-asr", true, BigDecimal.ZERO, false),
    GLM_ASR_ON_DEVICE("GLM-ASR-Nano (On-Device)", "glm-asr-ondevice", false, BigDecimal.ZERO, true)
}
```

### Phase 3: Device Capability Detection

**Target File:** `app/src/main/kotlin/com/unamentis/core/device/DeviceCapabilityDetector.kt`

Add GLM-ASR on-device support detection:

```kotlin
/**
 * Check if device supports on-device GLM-ASR.
 *
 * Requirements:
 * - 8GB+ RAM minimum (12GB recommended)
 * - Android 12+ (API 31+)
 * - Models present in app storage
 */
fun supportsGLMASROnDevice(): Boolean {
    val memoryGB = getAvailableMemoryGB()
    val hasModels = checkGLMASRModelsPresent()
    return memoryGB >= 8 && Build.VERSION.SDK_INT >= 31 && hasModels
}

private fun checkGLMASRModelsPresent(): Boolean {
    val modelDir = File(context.filesDir, "models/glm-asr-nano")
    return modelDir.exists() &&
           File(modelDir, "whisper_encoder.onnx").exists() &&
           File(modelDir, "glm-asr-nano-q4km.gguf").exists()
}
```

### Phase 4: Router Integration

**Target File:** `app/src/main/kotlin/com/unamentis/services/stt/STTProviderRouter.kt`

The router already supports on-device providers via priority. Add registration:

```kotlin
// In ProviderModule.kt or wherever providers are configured
if (deviceCapabilityDetector.supportsGLMASROnDevice()) {
    router.registerProvider(
        STTProviderRegistration(
            service = glmASROnDeviceService,
            priority = STTProviderPriority.ON_DEVICE,
            healthMonitor = null,  // On-device doesn't need health monitoring
            requiresApiKey = false,
            isOnDevice = true
        )
    )
}
```

### Phase 5: Settings UI Integration

**Target File:** `app/src/main/kotlin/com/unamentis/ui/settings/SettingsScreen.kt`

Add UI for:
- Showing GLM-ASR on-device availability
- Model download management
- On-device vs server preference

### Phase 6: Documentation

Create documentation parity with iOS:

| Document | Purpose |
|----------|---------|
| `docs/ai-ml/GLM_ASR_ON_DEVICE_GUIDE.md` | Setup and usage guide |
| `docs/ai-ml/GLM_ASR_IMPLEMENTATION_PROGRESS.md` | Implementation tracking |

### Phase 7: Testing

#### Unit Tests
- `GLMASROnDeviceSTTServiceTest.kt` - Test on-device service
- `GLMASRMelSpectrogramTest.kt` - Test audio processing
- Update `STTProviderRouterTest.kt` - Test on-device routing

#### Integration Tests
- `GLMASROnDeviceIntegrationTest.kt` - End-to-end on-device test
- `GLMASRFailoverIntegrationTest.kt` - Test failover scenarios

---

## Technical Considerations

### Memory Management

GLM-ASR on-device requires ~2.4GB of model memory plus runtime buffers. Android implementation must:

1. **Lazy load models** - Only load when needed
2. **Implement memory pressure handling** - Unload models when system needs memory
3. **Monitor thermal state** - Fall back to server when device is hot
4. **Use memory-mapped files** for GGUF models

### Thermal Management

iOS implementation switches to server mode when device overheats. Android should:

```kotlin
class GLMASROnDeviceSTTService {
    private val thermalMonitor: ThermalMonitor

    private fun shouldFallbackToServer(): Boolean {
        return thermalMonitor.currentState >= ThermalState.SERIOUS
    }
}
```

### Battery Considerations

On-device inference is power-intensive. Consider:
- Battery level check before starting on-device inference
- Power-connected preference in settings
- Battery drain telemetry

---

## File Creation Summary

### New Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/unamentis/services/stt/GLMASROnDeviceSTTService.kt` | On-device STT service |
| `app/src/main/kotlin/com/unamentis/services/stt/GLMASRMelSpectrogram.kt` | Audio to mel spectrogram |
| `app/src/main/kotlin/com/unamentis/services/stt/GLMASROnDeviceConfig.kt` | Configuration |
| `app/src/test/kotlin/com/unamentis/services/stt/GLMASROnDeviceSTTServiceTest.kt` | Unit tests |
| `app/src/androidTest/kotlin/com/unamentis/services/stt/GLMASROnDeviceIntegrationTest.kt` | Integration tests |
| `docs/ai-ml/GLM_ASR_ON_DEVICE_GUIDE.md` | Documentation |
| `docs/ai-ml/GLM_ASR_IMPLEMENTATION_PROGRESS.md` | Progress tracking |

### Files to Modify

| File | Changes |
|------|---------|
| `app/src/main/kotlin/com/unamentis/data/model/Providers.kt` | Add `GLM_ASR_ON_DEVICE` enum |
| `app/src/main/kotlin/com/unamentis/core/device/DeviceCapabilityDetector.kt` | Add GLM-ASR support check |
| `app/src/main/kotlin/com/unamentis/di/ProviderModule.kt` | Register on-device service |
| `app/src/main/kotlin/com/unamentis/ui/settings/SettingsScreen.kt` | Add GLM-ASR settings |
| `app/src/main/res/values/strings.xml` | Add GLM-ASR strings |

---

## Dependencies

### Required Libraries

```kotlin
// build.gradle.kts additions

// ONNX Runtime for ML inference
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

// OR TensorFlow Lite (alternative)
implementation("org.tensorflow:tensorflow-lite:2.15.0")
implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0")
```

### Existing Dependencies (Already Available)

- llama.cpp JNI bindings - Already in project
- OkHttp - Already used for WebSocket
- Kotlinx Coroutines - Already used throughout

---

## Timeline Estimate

| Phase | Complexity | Files | Estimate |
|-------|------------|-------|----------|
| Phase 1: On-Device Service | High | 4 | Core implementation |
| Phase 2: Provider Enum | Low | 1 | Quick change |
| Phase 3: Device Detection | Medium | 1 | Moderate |
| Phase 4: Router Integration | Low | 2 | Quick change |
| Phase 5: Settings UI | Medium | 3 | UI work |
| Phase 6: Documentation | Low | 2 | Writing |
| Phase 7: Testing | Medium | 4 | Test coverage |

---

## Success Criteria

1. **Feature Parity:** On-device GLM-ASR works on supported Android devices (12GB+ RAM)
2. **Fallback:** Automatic fallback to server or Deepgram when device unsupported
3. **Performance:** <500ms latency for on-device inference
4. **Memory:** <50MB memory growth over 90-minute sessions
5. **Tests:** All new code has unit and integration tests
6. **Documentation:** Complete setup and usage guides

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Models too large for Android | Use more aggressive quantization (Q3_K) |
| ONNX conversion issues | Fall back to TFLite or custom JNI |
| Performance too slow | Optimize with GPU acceleration, reduce batch size |
| Memory pressure crashes | Implement aggressive memory management |

---

## References

- iOS Implementation: `/Users/cygoerdt/unamentis/UnaMentis/Services/STT/GLMASROnDeviceSTTService.swift`
- iOS Documentation: `/Users/cygoerdt/unamentis/docs/ai-ml/GLM_ASR_ON_DEVICE_GUIDE.md`
- Model Source: https://huggingface.co/zai-org/GLM-ASR-Nano-2512
- ONNX Runtime Android: https://onnxruntime.ai/docs/tutorials/mobile/

---

**Document History:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | February 2026 | Claude | Initial plan |
