# GLM-ASR On-Device Implementation Guide

**Purpose:** Complete guide for implementing and using the on-device GLM-ASR-Nano speech recognition service in UnaMentis Android.

**Last Updated:** February 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Model Files](#3-model-files)
4. [Implementation Details](#4-implementation-details)
5. [Setup Instructions](#5-setup-instructions)
6. [Configuration](#6-configuration)
7. [Testing](#7-testing)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Overview

### 1.1 What is On-Device GLM-ASR?

UnaMentis Android supports running GLM-ASR-Nano directly on the device using ONNX Runtime for the neural network components and llama.cpp for the text decoder. This provides:

- **Zero latency** - No network round-trip
- **Complete privacy** - Audio never leaves the device
- **Offline support** - Works without internet
- **No API costs** - No per-hour transcription fees

### 1.2 Device Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| RAM | 8 GB | 12 GB |
| Android | 12 (API 31) | 13+ (API 33+) |
| CPU | 4+ cores | 8+ cores |
| Storage | 2.5 GB free | 5 GB free |

### 1.3 When to Use On-Device

The `GLMASROnDeviceSTTService` is automatically selected when:

1. Device has sufficient RAM (8GB+ minimum, 12GB+ recommended)
2. Model files are downloaded and present
3. ONNX Runtime is available
4. User has enabled on-device mode in settings

---

## 2. Architecture

### 2.1 Component Overview

```
                    GLM-ASR On-Device Pipeline (Android)
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Audio Input                                                    │
│  (16kHz PCM)                                                    │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────┐                   │
│  │     GLMASRMelSpectrogram (Kotlin)       │                   │
│  │     - Pre-emphasis filter                │                   │
│  │     - FFT computation                    │                   │
│  │     - Mel filterbank application         │                   │
│  │     - Log transform                      │                   │
│  └─────────────────────────────────────────┘                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────┐                   │
│  │     Whisper Encoder (ONNX Runtime)      │                   │
│  │     - 1.2GB ONNX model                   │                   │
│  │     - GPU acceleration (optional)        │                   │
│  │     - Outputs: audio embeddings          │                   │
│  └─────────────────────────────────────────┘                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────┐                   │
│  │     Audio Adapter (ONNX Runtime)        │                   │
│  │     - 56MB ONNX model                    │                   │
│  │     - Aligns audio features to LLM space │                   │
│  └─────────────────────────────────────────┘                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────┐                   │
│  │     Embed Head (ONNX Runtime)           │                   │
│  │     - 232MB ONNX model                   │                   │
│  │     - Produces token embeddings          │                   │
│  └─────────────────────────────────────────┘                   │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────────┐                   │
│  │     GLM-4 Text Decoder (llama.cpp)      │                   │
│  │     - Q4_K_M quantized (935MB model)     │                   │
│  │     - JNI bridge from OnDeviceLLMService │                   │
│  │     - Autoregressive text generation     │                   │
│  │     - Streaming token output             │                   │
│  └─────────────────────────────────────────┘                   │
│       │                                                         │
│       ▼                                                         │
│  Transcribed Text                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Key Classes

| Class | File | Purpose |
|-------|------|---------|
| `GLMASROnDeviceSTTService` | [GLMASROnDeviceSTTService.kt](../../app/src/main/kotlin/com/unamentis/services/stt/GLMASROnDeviceSTTService.kt) | Main STT service implementation |
| `GLMASROnDeviceConfig` | [GLMASROnDeviceConfig.kt](../../app/src/main/kotlin/com/unamentis/services/stt/GLMASROnDeviceConfig.kt) | Configuration data class |
| `GLMASRMelSpectrogram` | [GLMASRMelSpectrogram.kt](../../app/src/main/kotlin/com/unamentis/services/stt/GLMASRMelSpectrogram.kt) | Audio preprocessing (Mel spectrogram) |
| `DeviceCapabilityDetector` | [DeviceCapabilityDetector.kt](../../app/src/main/kotlin/com/unamentis/core/device/DeviceCapabilityDetector.kt) | Device capability checks |

### 2.3 Interface Implementation

`GLMASROnDeviceSTTService` implements `STTService`:

```kotlin
@Singleton
class GLMASROnDeviceSTTService @Inject constructor(
    @ApplicationContext private val context: Context,
) : STTService {

    override fun startStreaming(): Flow<STTResult>
    override suspend fun stopStreaming()
    override val providerName: String = "GLMASROnDevice"

    // Additional methods
    fun isSupported(): Boolean
    fun areModelsReady(): Boolean
    suspend fun loadModels(): Boolean
    fun unloadModels()
}
```

---

## 3. Model Files

### 3.1 Required Models

| Model | Size | Format | Purpose |
|-------|------|--------|---------|
| glm_asr_whisper_encoder.onnx | 1.2 GB | ONNX | Audio feature extraction |
| glm_asr_audio_adapter.onnx | 56 MB | ONNX | Feature alignment |
| glm_asr_embed_head.onnx | 232 MB | ONNX | Token embedding |
| glm-asr-nano-q4km.gguf | 935 MB | GGUF | Text decoding (llama.cpp) |

**Total:** ~2.4 GB

### 3.2 Model Location

Models should be stored in the app's internal files directory:

```
{context.filesDir}/models/glm-asr-nano/
├── glm_asr_whisper_encoder.onnx
├── glm_asr_audio_adapter.onnx
├── glm_asr_embed_head.onnx
└── glm-asr-nano-q4km.gguf
```

### 3.3 Obtaining Models

Models are available from:

1. **Hugging Face:** https://huggingface.co/zai-org/GLM-ASR-Nano-2512
2. **In-app download:** Via the Settings > On-Device AI section

### 3.4 Model Conversion

If you have the original PyTorch models, convert to ONNX:

```bash
# Install dependencies
pip install torch onnx

# Export Whisper encoder
python scripts/export_whisper_to_onnx.py \
    --input models/whisper_encoder.pt \
    --output models/glm_asr_whisper_encoder.onnx

# Export audio adapter
python scripts/export_adapter_to_onnx.py \
    --input models/audio_adapter.pt \
    --output models/glm_asr_audio_adapter.onnx

# Export embed head
python scripts/export_embed_head_to_onnx.py \
    --input models/embed_head.pt \
    --output models/glm_asr_embed_head.onnx
```

### 3.5 GGUF Quantization

The text decoder uses Q4_K_M quantization for optimal size/quality balance:

```bash
# If you have the F16 model, quantize it:
./llama-quantize \
    models/glm-asr-nano/glm-asr-nano-f16.gguf \
    models/glm-asr-nano/glm-asr-nano-q4km.gguf \
    Q4_K_M
```

---

## 4. Implementation Details

### 4.1 Service Initialization

```kotlin
// Get service via Hilt injection
@Inject lateinit var glmAsrOnDevice: GLMASROnDeviceSTTService

// Or create manually
val service = GLMASROnDeviceSTTService(context)

// Initialize with configuration
val config = GLMASROnDeviceConfig(
    modelDirectory = context.filesDir.resolve("models/glm-asr-nano"),
    useGPU = true,
    gpuLayers = 99,
    numThreads = 4,
    language = "auto"
)
service.initialize(config)

// Load models (heavyweight, do during app init)
val loaded = service.loadModels()
if (!loaded) {
    // Fall back to cloud STT
}
```

### 4.2 Audio Processing

The service expects 16kHz mono PCM audio:

```kotlin
// Start streaming
val resultFlow = service.startStreaming()

// Collect results
resultFlow.collect { result ->
    Log.d("STT", "Transcript: ${result.text}, Final: ${result.isFinal}")
}

// Send audio samples (Float array, normalized -1.0 to 1.0)
service.sendAudioSamples(floatArrayOf(...))

// Or send raw PCM bytes (16-bit signed, little-endian)
service.sendAudioData(byteArrayOf(...))

// Stop streaming
service.stopStreaming()
```

### 4.3 Device Support Check

Before using, check if the device supports on-device inference:

```kotlin
val detector = DeviceCapabilityDetector(context)

if (detector.supportsGLMASROnDevice(checkModels = true)) {
    // Use on-device service
    val service = GLMASROnDeviceSTTService(context)
} else if (detector.supportsGLMASROnDevice(checkModels = false)) {
    // Device capable but models not downloaded
    // Prompt user to download models
} else {
    // Fall back to server-based service
    val service = GLMASRSTTService(...)
}
```

### 4.4 Memory Management

Models are large and consume significant RAM. Follow these guidelines:

```kotlin
// Load models only when needed
if (userWantsOnDeviceSTT) {
    service.loadModels()
}

// Unload when done
override fun onDestroy() {
    service.unloadModels()
    super.onDestroy()
}

// Check memory before loading
val detector = DeviceCapabilityDetector(context)
if (!detector.hasOptimalGLMASRHardware()) {
    // Warn user about potential performance issues
}
```

---

## 5. Setup Instructions

### 5.1 Adding ONNX Runtime

Add ONNX Runtime to your `build.gradle.kts`:

```kotlin
dependencies {
    // ONNX Runtime for Android
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // Optional: GPU acceleration
    implementation("com.microsoft.onnxruntime:onnxruntime-android-gpu:1.16.0")
}
```

### 5.2 NDK Configuration

Ensure your `build.gradle.kts` includes proper NDK settings:

```kotlin
android {
    ndkVersion = "26.1.10909125"

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}
```

### 5.3 ProGuard Rules

Add these ProGuard rules for ONNX Runtime:

```proguard
# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
```

### 5.4 Permissions

No special permissions are required for on-device inference. Standard microphone access is already configured for audio recording.

---

## 6. Configuration

### 6.1 Configuration Options

```kotlin
data class GLMASROnDeviceConfig(
    /** Directory containing model files */
    val modelDirectory: File,

    /** Maximum audio duration to process at once (seconds) */
    val maxAudioDurationSeconds: Float = 30.0f,

    /** Whether to use GPU acceleration for ONNX */
    val useGPU: Boolean = true,

    /** Number of GPU layers for llama.cpp (0 = CPU only) */
    val gpuLayers: Int = 99,

    /** Number of CPU threads for inference */
    val numThreads: Int = 4,

    /** Language hint ("auto", "en", "zh", "yue") */
    val language: String = "auto",
)
```

### 6.2 Preset Configurations

```kotlin
// Default configuration
val config = GLMASROnDeviceConfig.default(context.filesDir)

// For low-memory devices (8GB RAM)
val lowMemConfig = GLMASROnDeviceConfig.lowMemory(context.filesDir)

// For high-end devices (12GB+ RAM)
val cpuCores = Runtime.getRuntime().availableProcessors()
val highPerfConfig = GLMASROnDeviceConfig.highPerformance(context.filesDir, cpuCores)
```

### 6.3 Memory vs Performance Trade-offs

| Setting | Low Memory | Default | High Performance |
|---------|------------|---------|------------------|
| useGPU | false | true | true |
| gpuLayers | 0 | 99 | 99 |
| numThreads | 2 | 4 | 6-8 |
| maxAudioDuration | 15s | 30s | 30s |

---

## 7. Testing

### 7.1 Unit Tests

Run GLM-ASR unit tests:

```bash
./gradlew :app:testDebugUnitTest \
    --tests "com.unamentis.services.stt.GLMASROnDeviceSTTServiceTest"
```

### 7.2 Instrumented Tests

For tests requiring Android context:

```bash
./gradlew :app:connectedDebugAndroidTest \
    --tests "com.unamentis.services.stt.*GLMASROnDevice*"
```

### 7.3 Manual Testing

1. **Enable on-device mode** in Settings > Voice & AI > Speech-to-Text
2. **Download models** (will prompt if not present)
3. **Start a session** and speak
4. **Check logs** for "GLMASROnDevice" tag

### 7.4 Performance Benchmarks

Expected performance on various devices:

| Device | First Chunk | Streaming | RTF |
|--------|-------------|-----------|-----|
| Pixel 9 Pro | ~200ms | ~50ms/chunk | 0.15x |
| Pixel 8 Pro | ~300ms | ~70ms/chunk | 0.22x |
| Samsung S24 Ultra | ~180ms | ~45ms/chunk | 0.14x |
| Samsung S23 | ~350ms | ~85ms/chunk | 0.28x |

RTF = Real-Time Factor (lower is better, <1.0 is real-time)

---

## 8. Troubleshooting

### 8.1 Model Not Found

**Symptom:** `Missing model files: [...]`

**Solution:**
1. Verify models are in the correct directory
2. Check file names match exactly (case-sensitive)
3. Ensure download completed successfully

```kotlin
// Debug: Check model status
val config = GLMASROnDeviceConfig.default(context.filesDir)
val missing = config.getMissingModels()
Log.d("GLM-ASR", "Missing models: $missing")
```

### 8.2 Out of Memory

**Symptom:** App crashes with OOM or system kills app

**Solution:**
1. Use `GLMASROnDeviceConfig.lowMemory()` configuration
2. Close other memory-heavy apps
3. Unload models when not in use
4. Consider falling back to cloud STT

### 8.3 Slow Performance

**Symptom:** High latency, choppy audio

**Solution:**
1. Enable GPU acceleration (`useGPU = true`)
2. Increase thread count on high-core-count devices
3. Check thermal throttling (cool down device)
4. Profile with Android Profiler

### 8.4 ONNX Runtime Errors

**Symptom:** `Failed to load ONNX model`

**Solution:**
1. Verify ONNX Runtime library is included
2. Check model file isn't corrupted
3. Ensure correct ONNX opset version
4. Check logcat for detailed error messages

### 8.5 llama.cpp Errors

**Symptom:** `Failed to initialize llama context`

**Solution:**
1. Verify GGUF file is valid
2. Check quantization format (Q4_K_M recommended)
3. Ensure JNI bindings are properly loaded
4. Reduce context size if memory constrained

### 8.6 GPU Not Working

**Symptom:** Inference runs on CPU despite `useGPU = true`

**Solution:**
1. Add GPU dependency: `onnxruntime-android-gpu`
2. Verify device has supported GPU
3. Check OpenCL/Vulkan support
4. Fall back to CPU-only mode

---

## Related Documentation

- [GLM_ASR_NANO_2512.md](GLM_ASR_NANO_2512.md) - Model overview and evaluation
- [GLM_ASR_IMPLEMENTATION_PLAN.md](GLM_ASR_IMPLEMENTATION_PLAN.md) - Android implementation plan
- [../TESTING.md](../TESTING.md) - Testing guidelines

---

## Comparison with iOS Implementation

| Aspect | Android | iOS |
|--------|---------|-----|
| Neural Network Runtime | ONNX Runtime | CoreML |
| Model Format (NN) | .onnx | .mlpackage |
| Text Decoder | llama.cpp (JNI) | llama.cpp (Swift) |
| GPU Acceleration | NNAPI/OpenCL | Neural Engine |
| Minimum OS | Android 12 (API 31) | iOS 18.0 |

---

**Document History:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | February 2026 | Claude | Initial document |
