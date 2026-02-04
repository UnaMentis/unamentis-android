// UnaMentis - GLM-ASR Decoder JNI Bindings
// Bridge between Kotlin GLMASROnDeviceSTTService and native GLMASRDecoder

#include <jni.h>
#include <android/log.h>
#include <map>
#include <memory>
#include <mutex>
#include "glm_asr_decoder.h"

#define LOG_TAG "GLMASRDecoderJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global state (following llama_inference_jni.cpp pattern)
static JavaVM* g_jvm = nullptr;
// Using shared_ptr to prevent use-after-free when nativeFreeDecoder is called
// while decoding is still running on another thread
static std::map<jlong, std::shared_ptr<unamentis::GLMASRDecoder>> g_decoders;
static std::mutex g_decoders_mutex;

// Callback context for streaming ASR output
struct ASRCallbackContext {
    jobject callback_object;
    jmethodID callback_method;

    ASRCallbackContext(JNIEnv* env, jobject callback) {
        callback_object = env->NewGlobalRef(callback);
        jclass callback_class = env->GetObjectClass(callback);
        // Kotlin lambda implements Function2<String, Boolean, Unit>
        callback_method = env->GetMethodID(callback_class, "invoke",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        env->DeleteLocalRef(callback_class);
    }

    ~ASRCallbackContext() {
        // Note: Must call release() with JNIEnv before destruction
    }

    void release(JNIEnv* env) {
        if (callback_object != nullptr) {
            env->DeleteGlobalRef(callback_object);
            callback_object = nullptr;
        }
    }
};

// Helper to get JNIEnv for current thread
static JNIEnv* getJNIEnv(bool* needs_detach) {
    JNIEnv* env = nullptr;
    *needs_detach = false;

    if (g_jvm == nullptr) {
        LOGE("JVM not initialized");
        return nullptr;
    }

    jint result = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (result == JNI_EDETACHED) {
        JavaVMAttachArgs args = {
            .version = JNI_VERSION_1_6,
            .name = const_cast<char*>("GLMASRCallback"),
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

// Called when native library is loaded
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    // Note: JNI_OnLoad may be called multiple times if multiple native libs use it
    // Only set g_jvm if not already set
    if (g_jvm == nullptr) {
        g_jvm = vm;
    }
    LOGI("glm_asr_decoder native library loaded");
    return JNI_VERSION_1_6;
}

// Load decoder model
extern "C" JNIEXPORT jlong JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeLoadDecoder(
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

    LOGI("nativeLoadDecoder: path=%s, ctx=%d, gpu=%d, threads=%d",
         path.c_str(), context_size, gpu_layers, n_threads);

    auto decoder = std::make_shared<unamentis::GLMASRDecoder>();

    unamentis::GLMASRDecoderConfig config;
    config.context_size = context_size;
    config.gpu_layers = gpu_layers;
    config.n_threads = n_threads;

    if (!decoder->loadModel(path, config)) {
        LOGE("Failed to load GLM-ASR decoder model");
        return 0;
    }

    jlong ptr = reinterpret_cast<jlong>(decoder.get());

    std::lock_guard<std::mutex> lock(g_decoders_mutex);
    g_decoders[ptr] = std::move(decoder);

    LOGI("GLM-ASR decoder loaded, handle: %ld, embd_dim: %d",
         static_cast<long>(ptr), g_decoders[ptr]->getEmbeddingDim());
    return ptr;
}

// Decode embeddings to text with streaming callback
extern "C" JNIEXPORT void JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeDecodeEmbeddings(
    JNIEnv* env,
    jobject /* thiz */,
    jlong context_ptr,
    jfloatArray embeddings,
    jint num_tokens,
    jint embedding_dim,
    jint max_output_tokens,
    jobject callback
) {
    if (context_ptr == 0) {
        LOGE("Invalid decoder context pointer");
        return;
    }

    // Acquire a shared_ptr to the decoder to keep it alive during decoding
    std::shared_ptr<unamentis::GLMASRDecoder> decoder;
    {
        std::lock_guard<std::mutex> lock(g_decoders_mutex);
        auto it = g_decoders.find(context_ptr);
        if (it == g_decoders.end()) {
            LOGE("Decoder not found for handle: %ld", static_cast<long>(context_ptr));
            return;
        }
        decoder = it->second;
    }

    // Get embeddings array
    jfloat* embd_ptr = env->GetFloatArrayElements(embeddings, nullptr);
    if (embd_ptr == nullptr) {
        LOGE("Failed to get embeddings array");
        return;
    }

    // Verify array size
    jsize embd_len = env->GetArrayLength(embeddings);
    jsize expected_len = num_tokens * embedding_dim;
    if (embd_len < expected_len) {
        LOGE("Embeddings array too small: got %d, expected %d", embd_len, expected_len);
        env->ReleaseFloatArrayElements(embeddings, embd_ptr, JNI_ABORT);
        return;
    }

    LOGD("nativeDecodeEmbeddings: num_tokens=%d, embd_dim=%d, max_out=%d",
         num_tokens, embedding_dim, max_output_tokens);

    // Create callback context with global reference
    auto callback_ctx = std::make_shared<ASRCallbackContext>(env, callback);

    // Copy embeddings to ensure they remain valid during async processing
    std::vector<float> embd_copy(embd_ptr, embd_ptr + expected_len);

    // Release Java array (we have a copy now)
    env->ReleaseFloatArrayElements(embeddings, embd_ptr, JNI_ABORT);

    // Decode with streaming callback
    decoder->decodeFromEmbeddings(
        embd_copy.data(),
        num_tokens,
        embedding_dim,
        max_output_tokens,
        [callback_ctx](const std::string& content, bool is_done) {
            bool needs_detach = false;
            JNIEnv* callback_env = getJNIEnv(&needs_detach);

            if (callback_env != nullptr && callback_ctx->callback_object != nullptr) {
                // Create Java strings
                jstring j_content = callback_env->NewStringUTF(content.c_str());

                // Create Boolean object
                jclass boolean_class = callback_env->FindClass("java/lang/Boolean");
                jmethodID boolean_ctor = callback_env->GetMethodID(boolean_class, "<init>", "(Z)V");
                jobject j_is_done = callback_env->NewObject(
                    boolean_class,
                    boolean_ctor,
                    is_done ? JNI_TRUE : JNI_FALSE
                );
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

// Synchronous decode - returns complete transcription
extern "C" JNIEXPORT jstring JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeDecodeEmbeddingsSync(
    JNIEnv* env,
    jobject /* thiz */,
    jlong context_ptr,
    jfloatArray embeddings,
    jint num_tokens,
    jint embedding_dim,
    jint max_output_tokens
) {
    if (context_ptr == 0) {
        LOGE("Invalid decoder context pointer");
        return env->NewStringUTF("");
    }

    // Acquire decoder
    std::shared_ptr<unamentis::GLMASRDecoder> decoder;
    {
        std::lock_guard<std::mutex> lock(g_decoders_mutex);
        auto it = g_decoders.find(context_ptr);
        if (it == g_decoders.end()) {
            LOGE("Decoder not found for handle: %ld", static_cast<long>(context_ptr));
            return env->NewStringUTF("");
        }
        decoder = it->second;
    }

    // Get embeddings array
    jfloat* embd_ptr = env->GetFloatArrayElements(embeddings, nullptr);
    if (embd_ptr == nullptr) {
        LOGE("Failed to get embeddings array");
        return env->NewStringUTF("");
    }

    // Verify array size
    jsize embd_len = env->GetArrayLength(embeddings);
    jsize expected_len = num_tokens * embedding_dim;
    if (embd_len < expected_len) {
        LOGE("Embeddings array too small: got %d, expected %d", embd_len, expected_len);
        env->ReleaseFloatArrayElements(embeddings, embd_ptr, JNI_ABORT);
        return env->NewStringUTF("");
    }

    // Decode synchronously
    std::string result = decoder->decodeFromEmbeddingsSync(
        embd_ptr,
        num_tokens,
        embedding_dim,
        max_output_tokens
    );

    // Release Java array
    env->ReleaseFloatArrayElements(embeddings, embd_ptr, JNI_ABORT);

    return env->NewStringUTF(result.c_str());
}

// Stop ASR decoding
extern "C" JNIEXPORT void JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeStopDecoder(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_decoders_mutex);
    auto it = g_decoders.find(context_ptr);
    if (it != g_decoders.end()) {
        it->second->stopGeneration();
        LOGI("ASR stop requested for handle: %ld", static_cast<long>(context_ptr));
    }
}

// Free decoder
extern "C" JNIEXPORT void JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeFreeDecoder(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_decoders_mutex);
    auto it = g_decoders.find(context_ptr);
    if (it != g_decoders.end()) {
        LOGI("Freeing GLM-ASR decoder for handle: %ld", static_cast<long>(context_ptr));
        g_decoders.erase(it);
    }
}

// Check if decoder is loaded
extern "C" JNIEXPORT jboolean JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeIsDecoderLoaded(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_decoders_mutex);
    auto it = g_decoders.find(context_ptr);
    if (it != g_decoders.end()) {
        return it->second->isLoaded() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// Check if currently generating
extern "C" JNIEXPORT jboolean JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeIsDecoding(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_decoders_mutex);
    auto it = g_decoders.find(context_ptr);
    if (it != g_decoders.end()) {
        return it->second->isGenerating() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// Get embedding dimension of loaded model
extern "C" JNIEXPORT jint JNICALL
Java_com_unamentis_services_stt_GLMASROnDeviceSTTService_nativeGetEmbeddingDim(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong context_ptr
) {
    if (context_ptr == 0) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(g_decoders_mutex);
    auto it = g_decoders.find(context_ptr);
    if (it != g_decoders.end()) {
        return it->second->getEmbeddingDim();
    }
    return 0;
}
