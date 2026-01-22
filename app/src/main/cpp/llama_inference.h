// UnaMentis - LLM Inference Native Header
// On-device LLM inference using llama.cpp (b7263+ API)
//
// This provides the native C++ layer for on-device LLM inference,
// matching the iOS implementation for feature parity.

#ifndef UNAMENTIS_LLAMA_INFERENCE_H
#define UNAMENTIS_LLAMA_INFERENCE_H

#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include <mutex>
#include "llama.h"

namespace unamentis {

/**
 * Configuration for LLM inference.
 */
struct LlamaConfig {
    int32_t context_size = 4096;      // Context window size
    int32_t gpu_layers = 99;           // Number of layers to offload to GPU (99 = all)
    int32_t n_threads = 4;             // Number of CPU threads
    float temperature = 0.7f;          // Sampling temperature
    int32_t max_tokens = 512;          // Maximum tokens to generate
};

/**
 * Token callback function type.
 * @param content The token text content
 * @param is_done Whether generation is complete
 */
using TokenCallback = std::function<void(const std::string& content, bool is_done)>;

/**
 * LLM Inference engine using llama.cpp.
 *
 * Provides on-device LLM inference with:
 * - Model loading/unloading
 * - Streaming token generation
 * - Thread-safe operation
 * - Memory-efficient inference
 *
 * Thread Safety:
 * - Model loading/unloading must be done from single thread
 * - Generation can be stopped from any thread
 * - Callbacks are invoked from the generation thread
 */
class LlamaInference {
public:
    LlamaInference();
    ~LlamaInference();

    // Disable copy
    LlamaInference(const LlamaInference&) = delete;
    LlamaInference& operator=(const LlamaInference&) = delete;

    /**
     * Load a GGUF model from disk.
     *
     * @param model_path Path to the .gguf model file
     * @param config Configuration for inference
     * @return true if model loaded successfully
     */
    bool loadModel(const std::string& model_path, const LlamaConfig& config);

    /**
     * Unload the current model and free resources.
     */
    void unloadModel();

    /**
     * Check if a model is currently loaded.
     */
    bool isLoaded() const { return is_loaded_.load(); }

    /**
     * Generate tokens from a prompt with streaming callback.
     *
     * This method blocks until generation is complete or stopped.
     * Tokens are emitted via the callback as they are generated.
     *
     * @param prompt The input prompt (pre-formatted for the model)
     * @param max_tokens Maximum number of tokens to generate
     * @param temperature Sampling temperature (0.0 = deterministic)
     * @param callback Function called for each generated token
     */
    void generate(
        const std::string& prompt,
        int32_t max_tokens,
        float temperature,
        TokenCallback callback
    );

    /**
     * Request generation to stop.
     * Safe to call from any thread.
     */
    void stopGeneration();

    /**
     * Check if generation is currently in progress.
     */
    bool isGenerating() const { return is_generating_.load(); }

    /**
     * Get the context size of the loaded model.
     */
    int32_t getContextSize() const { return config_.context_size; }

private:
    // llama.cpp state
    llama_model* model_ = nullptr;
    llama_context* context_ = nullptr;
    LlamaConfig config_;

    // Thread-safety state
    std::atomic<bool> is_loaded_{false};
    std::atomic<bool> is_generating_{false};
    std::atomic<bool> stop_requested_{false};
    std::mutex generation_mutex_;

    // Helper methods
    std::vector<llama_token> tokenize(const std::string& text, bool add_special);
    std::string detokenize(llama_token token);
    void resetContext();
};

} // namespace unamentis

#endif // UNAMENTIS_LLAMA_INFERENCE_H
