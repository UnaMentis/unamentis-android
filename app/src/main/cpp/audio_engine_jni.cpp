#include <jni.h>
#include <android/log.h>
#include "audio_engine.h"
#include <memory>
#include <map>

#define LOG_TAG "UnaMentis-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Store engine instances by pointer address
static std::map<jlong, std::unique_ptr<unamentis::AudioEngine>> g_engines;

extern "C" {

/**
 * Create a new AudioEngine instance.
 *
 * @return Pointer to engine instance (as long)
 */
JNIEXPORT jlong JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeCreate(
    JNIEnv* env,
    jobject /* this */
) {
    auto engine = std::make_unique<unamentis::AudioEngine>();
    jlong ptr = reinterpret_cast<jlong>(engine.get());
    g_engines[ptr] = std::move(engine);

    LOGI("Native AudioEngine created: %lld", (long long)ptr);
    return ptr;
}

/**
 * Initialize the audio engine.
 */
JNIEXPORT jboolean JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeInitialize(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr,
    jint sample_rate,
    jint channel_count,
    jint frames_per_burst
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return JNI_FALSE;
    }

    unamentis::AudioConfig config;
    config.sample_rate = sample_rate;
    config.channel_count = channel_count;
    config.frames_per_burst = frames_per_burst;

    bool success = it->second->initialize(config);
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Start audio capture.
 */
JNIEXPORT jboolean JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeStartCapture(
    JNIEnv* env,
    jobject thiz,
    jlong engine_ptr
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return JNI_FALSE;
    }

    // Create a global reference to the Java object for callbacks
    jobject global_ref = env->NewGlobalRef(thiz);

    // Define callback that will invoke Java method
    auto callback = [global_ref](const float* audio_data, int32_t frame_count, void* user_data) {
        // In a full implementation, we would:
        // 1. Get JNIEnv for this thread
        // 2. Convert audio_data to jfloatArray
        // 3. Call Java callback method
        // For now, this is a placeholder
    };

    bool success = it->second->startCapture(callback, global_ref);
    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Stop audio capture.
 */
JNIEXPORT void JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeStopCapture(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return;
    }

    it->second->stopCapture();
}

/**
 * Queue audio for playback.
 */
JNIEXPORT jboolean JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeQueuePlayback(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr,
    jfloatArray audio_data
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return JNI_FALSE;
    }

    jsize length = env->GetArrayLength(audio_data);
    jfloat* samples = env->GetFloatArrayElements(audio_data, nullptr);

    bool success = it->second->queuePlayback(samples, length);

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);

    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Stop audio playback.
 */
JNIEXPORT void JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeStopPlayback(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return;
    }

    it->second->stopPlayback();
}

/**
 * Destroy the audio engine.
 */
JNIEXPORT void JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeDestroy(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return;
    }

    LOGI("Destroying native AudioEngine: %lld", (long long)engine_ptr);
    g_engines.erase(it);
}

} // extern "C"
