// UnaMentis - LLM Inference Native Implementation
// On-device LLM inference using llama.cpp (b7263+ API)

#include "llama_inference.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>

#define LOG_TAG "LlamaInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace unamentis {

// Helper functions for batch management (matching iOS implementation)
static void llama_batch_clear(llama_batch& batch) {
    batch.n_tokens = 0;
}

static void llama_batch_add(
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

LlamaInference::LlamaInference() {
    LOGI("LlamaInference created");
}

LlamaInference::~LlamaInference() {
    unloadModel();
    LOGI("LlamaInference destroyed");
}

bool LlamaInference::loadModel(const std::string& model_path, const LlamaConfig& config) {
    std::lock_guard<std::mutex> lock(generation_mutex_);

    if (is_loaded_.load()) {
        LOGW("Model already loaded, unloading first");
        unloadModel();
    }

    LOGI("Loading model from: %s", model_path.c_str());
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
    LOGI("Model loaded successfully");

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
    LOGI("Model and context ready with %d threads", n_threads);
    return true;
}

void LlamaInference::unloadModel() {
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
    LOGI("Model unloaded");
}

void LlamaInference::generate(
    const std::string& prompt,
    int32_t max_tokens,
    float temperature,
    TokenCallback callback
) {
    if (!is_loaded_.load()) {
        LOGE("Cannot generate: model not loaded");
        callback("", true);
        return;
    }

    std::lock_guard<std::mutex> lock(generation_mutex_);

    is_generating_.store(true);
    stop_requested_.store(false);

    LOGD("Starting generation with prompt length: %zu chars", prompt.length());

    // Tokenize input
    std::vector<llama_token> tokens = tokenize(prompt, true);
    LOGD("Tokenized to %zu tokens", tokens.size());

    // Reset context for new generation
    resetContext();

    // Create batch for processing
    llama_batch batch = llama_batch_init(512, 0, 1);

    // Add prompt tokens to batch
    llama_batch_clear(batch);
    for (size_t i = 0; i < tokens.size(); ++i) {
        llama_batch_add(batch, tokens[i], static_cast<llama_pos>(i), {0}, false);
    }
    // Enable logits for last token
    batch.logits[batch.n_tokens - 1] = 1;

    // Process prompt
    LOGD("Processing prompt through decoder...");
    if (llama_decode(context_, batch) != 0) {
        LOGE("Initial decode failed");
        llama_batch_free(batch);
        is_generating_.store(false);
        callback("", true);
        return;
    }
    LOGD("Prompt processed, starting generation...");

    // Get vocab for new API
    const llama_vocab* vocab = llama_model_get_vocab(model_);

    // Create greedy sampler (matching iOS implementation)
    llama_sampler* sampler = llama_sampler_init_greedy();

    // Generation loop
    int32_t n_cur = static_cast<int32_t>(tokens.size());
    int32_t n_gen = 0;
    std::vector<char> temp_invalid_chars;

    while (n_cur < static_cast<int32_t>(tokens.size()) + max_tokens) {
        // Check for stop request
        if (stop_requested_.load()) {
            LOGI("Generation stopped by request");
            break;
        }

        // Sample next token using greedy sampler
        llama_token new_token = llama_sampler_sample(sampler, context_, batch.n_tokens - 1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGD("End of generation token received");
            break;
        }

        // Decode token to text
        std::string token_text = detokenize(new_token);

        // Handle multi-byte UTF-8 characters
        if (!token_text.empty()) {
            n_gen++;
            callback(token_text, false);
        }

        // Prepare next batch
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token, n_cur, {0}, true);

        if (llama_decode(context_, batch) != 0) {
            LOGE("Decode failed during generation");
            break;
        }

        n_cur++;
    }

    // Cleanup
    llama_sampler_free(sampler);
    llama_batch_free(batch);

    LOGI("Generation complete: %d tokens generated", n_gen);
    is_generating_.store(false);

    // Final callback to signal completion
    callback("", true);
}

void LlamaInference::stopGeneration() {
    stop_requested_.store(true);
    LOGI("Stop requested");
}

std::vector<llama_token> LlamaInference::tokenize(const std::string& text, bool add_special) {
    // Get vocab from model (new b7263+ API)
    const llama_vocab* vocab = llama_model_get_vocab(model_);

    int32_t n_tokens_max = static_cast<int32_t>(text.length()) + 2;
    std::vector<llama_token> tokens(n_tokens_max);

    // New API: llama_tokenize(vocab, text, text_len, tokens, n_tokens_max, add_special, parse_special)
    int32_t n_tokens = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.length()),
        tokens.data(),
        n_tokens_max,
        add_special,
        false  // parse_special
    );

    if (n_tokens < 0) {
        // Buffer too small, resize and retry
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            vocab,
            text.c_str(),
            static_cast<int32_t>(text.length()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            add_special,
            false
        );
    }

    tokens.resize(std::max(0, n_tokens));
    return tokens;
}

std::string LlamaInference::detokenize(llama_token token) {
    // Get vocab from model (new b7263+ API)
    const llama_vocab* vocab = llama_model_get_vocab(model_);

    char buf[8] = {0};
    // New API: llama_token_to_piece(vocab, token, buf, length, lstrip, special)
    int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);

    if (n < 0) {
        // Buffer too small
        std::vector<char> larger_buf(-n);
        n = llama_token_to_piece(vocab, token, larger_buf.data(), static_cast<int32_t>(larger_buf.size()), 0, false);
        if (n > 0) {
            return std::string(larger_buf.data(), n);
        }
        return "";
    }

    return std::string(buf, n);
}

void LlamaInference::resetContext() {
    if (context_ != nullptr) {
        llama_memory_t memory = llama_get_memory(context_);
        if (memory != nullptr) {
            llama_memory_clear(memory, true);
        }
    }
}

} // namespace unamentis
