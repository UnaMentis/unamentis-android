// UnaMentis - GLM-ASR Decoder Native Implementation
// On-device ASR decoder using llama.cpp for embedding-to-text generation

#include "glm_asr_decoder.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>

#define LOG_TAG "GLMASRDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace unamentis {

// Helper function to clear a batch
static void asr_batch_clear(llama_batch& batch) {
    batch.n_tokens = 0;
}

// Helper function to add a token to a batch (for autoregressive generation)
static void asr_batch_add_token(
    llama_batch& batch,
    llama_token id,
    llama_pos pos,
    const std::vector<llama_seq_id>& seq_ids,
    bool logits
) {
    batch.token[batch.n_tokens] = id;
    batch.pos[batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = static_cast<int32_t>(seq_ids.size());
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits[batch.n_tokens] = logits ? 1 : 0;
    batch.n_tokens++;
}

GLMASRDecoder::GLMASRDecoder() {
    LOGI("GLMASRDecoder created");
}

GLMASRDecoder::~GLMASRDecoder() {
    unloadModel();
    LOGI("GLMASRDecoder destroyed");
}

bool GLMASRDecoder::loadModel(const std::string& model_path, const GLMASRDecoderConfig& config) {
    std::lock_guard<std::mutex> lock(generation_mutex_);

    if (is_loaded_.load()) {
        LOGW("Model already loaded, unloading first");
        unloadModel();
    }

    LOGI("Loading GLM-ASR decoder from: %s", model_path.c_str());
    config_ = config;

    // Initialize llama backend
    llama_backend_init();
    LOGD("Backend initialized");

    // Configure model parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = config.gpu_layers;
    LOGI("GPU layers: %d", config.gpu_layers);

    // Load model
    LOGI("Loading model file (this may take a while)...");
    model_ = llama_model_load_from_file(model_path.c_str(), model_params);
    if (model_ == nullptr) {
        LOGE("Failed to load model from: %s", model_path.c_str());
        llama_backend_free();
        return false;
    }
    LOGI("Model loaded successfully, n_embd=%d", llama_model_n_embd(model_));

    // Create context
    int n_threads = std::max(1, std::min(8, config.n_threads));
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = config.context_size;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    LOGD("Creating context with %d threads, context size: %d", n_threads, config.context_size);
    context_ = llama_init_from_model(model_, ctx_params);
    if (context_ == nullptr) {
        LOGE("Failed to create context");
        llama_model_free(model_);
        model_ = nullptr;
        llama_backend_free();
        return false;
    }

    is_loaded_.store(true);
    LOGI("GLM-ASR decoder ready with %d threads", n_threads);
    return true;
}

void GLMASRDecoder::unloadModel() {
    // Don't lock if already unloaded
    if (!is_loaded_.load()) {
        return;
    }

    std::lock_guard<std::mutex> lock(generation_mutex_);

    if (context_ != nullptr) {
        llama_free(context_);
        context_ = nullptr;
    }

    if (model_ != nullptr) {
        llama_model_free(model_);
        model_ = nullptr;
    }

    llama_backend_free();
    is_loaded_.store(false);
    LOGI("GLM-ASR decoder unloaded");
}

int32_t GLMASRDecoder::getEmbeddingDim() const {
    if (model_ == nullptr) {
        return 0;
    }
    return llama_model_n_embd(model_);
}

bool GLMASRDecoder::injectEmbeddings(
    const float* embeddings,
    int32_t num_tokens,
    int32_t embedding_dim
) {
    if (context_ == nullptr || model_ == nullptr) {
        LOGE("Cannot inject embeddings: model not loaded");
        return false;
    }

    // Verify embedding dimension matches model
    int32_t model_embd = llama_model_n_embd(model_);
    if (embedding_dim != model_embd) {
        LOGE("Embedding dimension mismatch: got %d, expected %d", embedding_dim, model_embd);
        return false;
    }

    // Create batch with embedding allocation
    // llama_batch_init(n_tokens, embd, n_seq_max) - embd != 0 allocates embd array
    llama_batch batch = llama_batch_init(num_tokens, embedding_dim, 1);

    // Copy embeddings into batch
    // The batch.embd is allocated as n_tokens * embd * sizeof(float)
    std::memcpy(batch.embd, embeddings, num_tokens * embedding_dim * sizeof(float));

    // Set batch parameters
    batch.n_tokens = num_tokens;

    for (int32_t i = 0; i < num_tokens; ++i) {
        batch.pos[i] = static_cast<llama_pos>(i);
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = 0;
    }

    // Enable logits for last token (for sampling)
    batch.logits[num_tokens - 1] = 1;

    LOGD("Injecting %d audio embeddings (dim=%d)...", num_tokens, embedding_dim);

    // Process embeddings through decoder
    int32_t result = llama_decode(context_, batch);

    llama_batch_free(batch);

    if (result != 0) {
        LOGE("Failed to process audio embeddings, error code: %d", result);
        return false;
    }

    LOGD("Audio embeddings processed successfully");
    return true;
}

void GLMASRDecoder::decodeFromEmbeddings(
    const float* embeddings,
    int32_t num_tokens,
    int32_t embedding_dim,
    int32_t max_output_tokens,
    ASRTokenCallback callback
) {
    if (!is_loaded_.load()) {
        LOGE("Cannot decode: model not loaded");
        callback("", true);
        return;
    }

    std::lock_guard<std::mutex> lock(generation_mutex_);

    is_generating_.store(true);
    stop_requested_.store(false);

    LOGD("Starting ASR decode with %d audio tokens, dim=%d", num_tokens, embedding_dim);

    // Reset context for new generation
    resetContext();

    // Inject audio embeddings
    if (!injectEmbeddings(embeddings, num_tokens, embedding_dim)) {
        LOGE("Failed to inject embeddings");
        is_generating_.store(false);
        callback("", true);
        return;
    }

    // Get vocab for token operations
    const llama_vocab* vocab = llama_model_get_vocab(model_);

    // Create greedy sampler for ASR (temperature 0)
    llama_sampler* sampler = llama_sampler_init_greedy();

    // Create token batch for autoregressive generation (token-based, not embedding)
    llama_batch token_batch = llama_batch_init(1, 0, 1);

    // Generation loop - continue from the injected embeddings
    int32_t n_cur = num_tokens;
    int32_t n_gen = 0;

    while (n_gen < max_output_tokens) {
        // Check for stop request
        if (stop_requested_.load()) {
            LOGI("ASR generation stopped by request");
            break;
        }

        // Sample next token
        // For the first iteration, sample from the last position of injected embeddings
        // For subsequent iterations, sample from the last generated token
        llama_token new_token = llama_sampler_sample(sampler, context_, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGD("End of generation token received");
            break;
        }

        // Decode token to text
        std::string token_text = detokenize(new_token);

        // Emit token
        if (!token_text.empty()) {
            n_gen++;
            callback(token_text, false);
        }

        // Prepare next token batch
        asr_batch_clear(token_batch);
        asr_batch_add_token(token_batch, new_token, n_cur, {0}, true);

        // Process the new token
        if (llama_decode(context_, token_batch) != 0) {
            LOGE("Decode failed during generation");
            break;
        }

        n_cur++;
    }

    // Cleanup
    llama_sampler_free(sampler);
    llama_batch_free(token_batch);

    LOGI("ASR generation complete: %d tokens generated", n_gen);
    is_generating_.store(false);

    // Final callback to signal completion
    callback("", true);
}

std::string GLMASRDecoder::decodeFromEmbeddingsSync(
    const float* embeddings,
    int32_t num_tokens,
    int32_t embedding_dim,
    int32_t max_output_tokens
) {
    std::string result;

    decodeFromEmbeddings(
        embeddings,
        num_tokens,
        embedding_dim,
        max_output_tokens,
        [&result](const std::string& content, bool /* is_done */) {
            result += content;
        }
    );

    return result;
}

void GLMASRDecoder::stopGeneration() {
    stop_requested_.store(true);
    LOGI("ASR stop requested");
}

std::string GLMASRDecoder::detokenize(llama_token token) {
    // Get vocab from model
    const llama_vocab* vocab = llama_model_get_vocab(model_);

    char buf[8] = {0};
    int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);

    if (n < 0) {
        // Buffer too small
        std::vector<char> larger_buf(-n);
        n = llama_token_to_piece(vocab, token, larger_buf.data(),
                                  static_cast<int32_t>(larger_buf.size()), 0, false);
        if (n > 0) {
            return std::string(larger_buf.data(), n);
        }
        return "";
    }

    return std::string(buf, n);
}

void GLMASRDecoder::resetContext() {
    if (context_ != nullptr) {
        llama_memory_t memory = llama_get_memory(context_);
        if (memory != nullptr) {
            llama_memory_clear(memory, true);
        }
    }
}

} // namespace unamentis
