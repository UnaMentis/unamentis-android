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

// Store JavaVM for callback thread attachment
static JavaVM* g_jvm = nullptr;

// Callback context for passing audio to Java
struct CallbackContext {
    jobject java_object;      // Global reference to Java AudioEngine
    jmethodID callback_method; // Method ID for onAudioData callback
};

// Store callback contexts
static std::map<jlong, std::unique_ptr<CallbackContext>> g_callbacks;

extern "C" {

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGI("JNI_OnLoad: JavaVM stored");
    return JNI_VERSION_1_6;
}

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

    // Create callback context
    auto context = std::make_unique<CallbackContext>();

    // Create a global reference to the Java object for callbacks from audio thread
    context->java_object = env->NewGlobalRef(thiz);
    if (context->java_object == nullptr) {
        LOGE("Failed to create global reference");
        return JNI_FALSE;
    }

    // Get the callback method ID
    jclass clazz = env->GetObjectClass(thiz);
    context->callback_method = env->GetMethodID(clazz, "onNativeAudioData", "([F)V");
    if (context->callback_method == nullptr) {
        LOGE("Failed to find onNativeAudioData method");
        env->DeleteGlobalRef(context->java_object);
        return JNI_FALSE;
    }

    // Store context
    CallbackContext* ctx_ptr = context.get();
    g_callbacks[engine_ptr] = std::move(context);

    // Define callback that will invoke Java method
    auto callback = [ctx_ptr](const float* audio_data, int32_t frame_count, void* user_data) {
        if (g_jvm == nullptr) {
            LOGE("JavaVM not available");
            return;
        }

        JNIEnv* callback_env = nullptr;
        bool needs_detach = false;

        // Check if current thread is attached to JVM
        jint result = g_jvm->GetEnv(reinterpret_cast<void**>(&callback_env), JNI_VERSION_1_6);

        if (result == JNI_EDETACHED) {
            // Attach current thread to JVM
            JavaVMAttachArgs args;
            args.version = JNI_VERSION_1_6;
            args.name = const_cast<char*>("OboeAudioThread");
            args.group = nullptr;

            if (g_jvm->AttachCurrentThread(&callback_env, &args) != JNI_OK) {
                LOGE("Failed to attach audio thread to JVM");
                return;
            }
            needs_detach = true;
        } else if (result != JNI_OK) {
            LOGE("Failed to get JNIEnv: %d", result);
            return;
        }

        // Create float array and copy audio data
        jfloatArray java_array = callback_env->NewFloatArray(frame_count);
        if (java_array == nullptr) {
            LOGE("Failed to create float array");
            if (needs_detach) {
                g_jvm->DetachCurrentThread();
            }
            return;
        }

        callback_env->SetFloatArrayRegion(java_array, 0, frame_count, audio_data);

        // Call Java callback method
        callback_env->CallVoidMethod(ctx_ptr->java_object, ctx_ptr->callback_method, java_array);

        // Check for exceptions
        if (callback_env->ExceptionCheck()) {
            callback_env->ExceptionDescribe();
            callback_env->ExceptionClear();
        }

        // Clean up local reference
        callback_env->DeleteLocalRef(java_array);

        // Detach if we attached
        if (needs_detach) {
            g_jvm->DetachCurrentThread();
        }
    };

    bool success = it->second->startCapture(callback, ctx_ptr);
    if (!success) {
        // Clean up on failure
        env->DeleteGlobalRef(ctx_ptr->java_object);
        g_callbacks.erase(engine_ptr);
    }

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

    // Clean up callback context
    auto cb_it = g_callbacks.find(engine_ptr);
    if (cb_it != g_callbacks.end()) {
        if (cb_it->second->java_object != nullptr) {
            env->DeleteGlobalRef(cb_it->second->java_object);
        }
        g_callbacks.erase(cb_it);
    }
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
 * Check if currently capturing.
 */
JNIEXPORT jboolean JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeIsCapturing(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        return JNI_FALSE;
    }
    return it->second->isCapturing() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Check if currently playing.
 */
JNIEXPORT jboolean JNICALL
Java_com_unamentis_core_audio_AudioEngine_nativeIsPlaying(
    JNIEnv* env,
    jobject /* this */,
    jlong engine_ptr
) {
    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        return JNI_FALSE;
    }
    return it->second->isPlaying() ? JNI_TRUE : JNI_FALSE;
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
    // Clean up callback first
    auto cb_it = g_callbacks.find(engine_ptr);
    if (cb_it != g_callbacks.end()) {
        if (cb_it->second->java_object != nullptr) {
            env->DeleteGlobalRef(cb_it->second->java_object);
        }
        g_callbacks.erase(cb_it);
    }

    auto it = g_engines.find(engine_ptr);
    if (it == g_engines.end()) {
        LOGE("Invalid engine pointer: %lld", (long long)engine_ptr);
        return;
    }

    LOGI("Destroying native AudioEngine: %lld", (long long)engine_ptr);
    g_engines.erase(it);
}

} // extern "C"
