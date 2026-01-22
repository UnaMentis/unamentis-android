// UnaMentis - LLM Inference JNI Bindings
// Bridge between Kotlin OnDeviceLLMService and native llama.cpp

#include <jni.h>
#include <android/log.h>
#include <map>
#include <memory>
#include <mutex>
#include "llama_inference.h"

#define LOG_TAG "LlamaInferenceJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global state (following audio_engine_jni.cpp pattern)
static JavaVM* g_jvm = nullptr;
// Using shared_ptr to prevent use-after-free when nativeFreeModel is called
// while nativeStartGeneration's generate() is still running on another thread.
// In-flight operations hold their own shared_ptr, so erasure from the map
// only drops the map's reference while the operation keeps the engine alive.
static std::map<jlong, std::shared_ptr<unamentis::LlamaInference>> g_engines;
static std::mutex g_engines_mutex;

// Callback context for token streaming
struct TokenCallbackContext {
    jobject callback_object;
    jmethodID callback_method;

    TokenCallbackContext(JNIEnv* env, jobject callback) {
        callback_object = env->NewGlobalRef(callback);
        jclass callback_class = env->GetObjectClass(callback);
        // Kotlin lambda implements Function2<String, Boolean, Unit>
        callback_method = env->GetMethodID(callback_class, "invoke",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        // Delete local ref to avoid accumulation (callback_object is a global ref)
        env->DeleteLocalRef(callback_class);
    }

    ~TokenCallbackContext() {
        // Note: Must call release() with JNIEnv before destruction
    }

    void release(JNIEnv* env) {
        if (callback_object != nullptr) {
            env->DeleteGlobalRef(callback_object);
            callback_object = nullptr;
        }
    }
};

// Called when native library is loaded
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    g_jvm = vm;
    LOGI("llama_inference native library loaded");
    return JNI_VERSION_1_6;
}

// Helper to get JNIEnv for current thread
static JNIEnv* getJNIEnv(bool* needs_detach) {
    JNIEnv* env = nullptr;
    *needs_detach = false;

    jint result = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        JavaVMAttachArgs args = {
            .version = JNI_VERSION_1_6,
            .name = const_cast<char*>("LlamaInferenceCallback"),
            .group = nullptr
        };
        if (g_jvm->AttachCurrentThread(&env, &args) == JNI_OK) {
            *needs_detach = true;
        } else {
            LOGE("Failed to attach thread to JVM");
            return nullptr;
        }
    }

    return env;
}

// Load model
extern "C" JNIEXPORT jlong JNICALL
Java_com_unamentis_services_llm_OnDeviceLLMService_nativeLoadModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring model_path,
    jint context_size,
    jint gpu_layers,
    jint n_threads
) {
    const char* path_cstr = env->GetStringUTFChars(model_path, nullptr);
    std::string path(path_cstr);
    env->ReleaseStringUTFChars(model_path, path_cstr);

    LOGI("nativeLoadModel: path=%s, ctx=%d, gpu=%d, threads=%d",
         path.c_str(), context_size, gpu_layers, n_threads);

    auto engine = std::make_shared<unamentis::LlamaInference>();

    unamentis::LlamaConfig config;
    config.context_size = context_size;
    config.gpu_layers = gpu_layers;
    config.n_threads = n_threads;

    if (!engine->loadModel(path, config)) {
        LOGE("Failed to load model");
        return 0;
    }

    jlong ptr = reinterpret_cast<jlong>(engine.get());

    std::lock_guard<std::mutex> lock(g_engines_mutex);
    g_engines[ptr] = std::move(engine);

    LOGI("Model loaded, handle: %ld", static_cast<long>(ptr));
    return ptr;
}

// Start generation with callback
extern "C" JNIEXPORT void JNICALL
Java_com_unamentis_services_llm_OnDeviceLLMService_nativeStartGeneration(
    JNIEnv* env,
    jobject /* thiz */,
    jlong context_ptr,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jobject callback
) {
    if (context_ptr == 0) {
        LOGE("Invalid context pointer");
        return;
    }

    // Acquire a shared_ptr to the engine to keep it alive during generation.
    // This prevents use-after-free if nativeFreeModel is called while generate()
    // is still running - the engine won't be destroyed until this shared_ptr
    // goes out of scope after generate() completes.
    std::shared_ptr<unamentis::LlamaInference> engine;
    {
        std::lock_guard<std::mutex> lock(g_engines_mutex);
        auto it = g_engines.find(context_ptr);
        if (it == g_engines.end()) {
            LOGE("Engine not found for handle: %ld", static_cast<long>(context_ptr));
            return;
        }
        engine = it->second;  // Copy shared_ptr to extend lifetime
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    LOGD("nativeStartGeneration: prompt_len=%zu, max_tokens=%d, temp=%.2f",
         prompt_str.length(), max_tokens, temperature);

    // Create callback context with global reference
    auto callback_ctx = std::make_shared<TokenCallbackContext>(env, callback);

    // Generate with streaming callback
    engine->generate(
        prompt_str,
        max_tokens,
        temperature,
        [callback_ctx](const std::string& content, bool is_done) {
            bool needs_detach = false;
            JNIEnv* callback_env = getJNIEnv(&needs_detach);

            if (callback_env != nullptr && callback_ctx->callback_object != nullptr) {
                // Create Java strings
                jstring j_content = callback_env->NewStringUTF(content.c_str());

                // Create Boolean object - find class once to avoid leaking multiple local refs
                jclass boolean_class = callback_env->FindClass("java/lang/Boolean");
                jmethodID boolean_ctor = callback_env->GetMethodID(boolean_class, "<init>", "(Z)V");
                jobject j_is_done = callback_env->NewObject(
                    boolean_class,
                    boolean_ctor,
                    is_done ? JNI_TRUE : JNI_FALSE
                );
                // Delete Boolean class local ref immediately after use
                callback_env->DeleteLocalRef(boolean_class);

                // Invoke Kotlin callback
                callback_env->CallObjectMethod(
                    callback_ctx->callback_object,
                    callback_ctx->callback_method,
                    j_content,
                    j_is_done
                );

                // Cleanup local references
                callback_env->DeleteLocalRef(j_content);
                callback_env->DeleteLocalRef(j_is_done);

                // Check for exceptions
                if (callback_env->ExceptionCheck()) {
                    callback_env->ExceptionDescribe();
                    callback_env->ExceptionClear();
                }
            }

            if (needs_detach) {
                g_jvm->DetachCurrentThread();
            }

            // Release callback context when done
            if (is_done && callback_ctx->callback_object != nullptr) {
                bool cleanup_needs_detach = false;
                JNIEnv* cleanup_env = getJNIEnv(&cleanup_needs_detach);
                if (cleanup_env != nullptr) {
                    callback_ctx->release(cleanup_env);
                }
                if (cleanup_needs_detach) {
                    g_jvm->DetachCurrentThread();
                }
            }
        }
    );
}

// Stop generation
extern "C" JNIEXPORT void JNICALL
Java_com_unamentis_services_llm_OnDeviceLLMService_nativeStopGeneration(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_engines_mutex);
    auto it = g_engines.find(context_ptr);
    if (it != g_engines.end()) {
        it->second->stopGeneration();
        LOGI("Stop generation requested for handle: %ld", static_cast<long>(context_ptr));
    }
}

// Free model
extern "C" JNIEXPORT void JNICALL
Java_com_unamentis_services_llm_OnDeviceLLMService_nativeFreeModel(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_engines_mutex);
    auto it = g_engines.find(context_ptr);
    if (it != g_engines.end()) {
        LOGI("Freeing model for handle: %ld", static_cast<long>(context_ptr));
        g_engines.erase(it);
    }
}

// Check if model is loaded
extern "C" JNIEXPORT jboolean JNICALL
Java_com_unamentis_services_llm_OnDeviceLLMService_nativeIsLoaded(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_engines_mutex);
    auto it = g_engines.find(context_ptr);
    if (it != g_engines.end()) {
        return it->second->isLoaded() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// Check if currently generating
extern "C" JNIEXPORT jboolean JNICALL
Java_com_unamentis_services_llm_OnDeviceLLMService_nativeIsGenerating(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_engines_mutex);
    auto it = g_engines.find(context_ptr);
    if (it != g_engines.end()) {
        return it->second->isGenerating() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}
