// UnaMentis - GLM-ASR Decoder Native Header
// On-device ASR decoder using llama.cpp for embedding-to-text generation
//
// This provides the native C++ layer for GLM-ASR on-device speech-to-text,
// accepting pre-computed audio embeddings from the ONNX encoder pipeline
// and generating transcribed text.

#ifndef UNAMENTIS_GLM_ASR_DECODER_H
#define UNAMENTIS_GLM_ASR_DECODER_H

#include <cstdint>
#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include <mutex>
#include "llama.h"

namespace unamentis {

/**
 * Configuration for GLM-ASR decoder.
 */
struct GLMASRDecoderConfig {
    int32_t context_size = 4096;      // Context window size
    int32_t gpu_layers = 99;           // Number of layers to offload to GPU (99 = all)
    int32_t n_threads = 4;             // Number of CPU threads
    int32_t max_output_tokens = 256;   // Maximum tokens to generate
    float temperature = 0.0f;          // Sampling temperature (0 = greedy)
};

/**
 * Token callback function type for streaming output.
 * @param content The token text content
 * @param is_done Whether generation is complete
 */
using ASRTokenCallback = std::function<void(const std::string& content, bool is_done)>;

/**
 * GLM-ASR Decoder using llama.cpp.
 *
 * Key difference from LlamaInference:
 * - Accepts pre-computed audio embeddings instead of text prompts
 * - Directly injects embeddings into the model's input layer
 * - Bypasses tokenization completely for audio input
 *
 * The embedding input comes from the ONNX encoder pipeline:
 * 1. Whisper Encoder: mel spectrogram -> audio features [1, 1500, 1280]
 * 2. Audio Adapter: audio features -> adapted features [1, 375, 2048]
 * 3. Embed Head: adapted features -> token embeddings [1, 375, 4096]
 *
 * This decoder takes the final embeddings [375, 4096] and generates text.
 *
 * Thread Safety:
 * - Model loading/unloading must be done from single thread
 * - Generation can be stopped from any thread
 * - Callbacks are invoked from the generation thread
 */
class GLMASRDecoder {
public:
    GLMASRDecoder();
    ~GLMASRDecoder();

    // Disable copy
    GLMASRDecoder(const GLMASRDecoder&) = delete;
    GLMASRDecoder& operator=(const GLMASRDecoder&) = delete;

    /**
     * Load a GGUF decoder model from disk.
     *
     * @param model_path Path to the .gguf decoder model file
     * @param config Configuration for inference
     * @return true if model loaded successfully
     */
    bool loadModel(const std::string& model_path, const GLMASRDecoderConfig& config);

    /**
     * Unload the current model and free resources.
     */
    void unloadModel();

    /**
     * Check if a model is currently loaded.
     */
    bool isLoaded() const { return is_loaded_.load(); }

    /**
     * Get the embedding dimension expected by the loaded model.
     * Returns 0 if no model is loaded.
     */
    int32_t getEmbeddingDim() const;

    /**
     * Decode audio embeddings to text with streaming callback.
     *
     * This method blocks until generation is complete or stopped.
     * Tokens are emitted via the callback as they are generated.
     *
     * The embeddings should be the output of the ONNX Embed Head:
     * - Shape: [num_tokens, embedding_dim] (e.g., [375, 4096])
     * - Flattened row-major: [token0_dim0, token0_dim1, ..., token1_dim0, ...]
     *
     * @param embeddings Flattened embedding array
     * @param num_tokens Number of audio tokens (e.g., 375)
     * @param embedding_dim Dimension of each embedding (e.g., 4096)
     * @param max_output_tokens Maximum number of text tokens to generate
     * @param callback Function called for each generated token
     */
    void decodeFromEmbeddings(
        const float* embeddings,
        int32_t num_tokens,
        int32_t embedding_dim,
        int32_t max_output_tokens,
        ASRTokenCallback callback
    );

    /**
     * Synchronous version that returns complete transcription.
     *
     * @param embeddings Flattened embedding array
     * @param num_tokens Number of audio tokens
     * @param embedding_dim Dimension of each embedding
     * @param max_output_tokens Maximum tokens to generate
     * @return Transcribed text, or empty string on error
     */
    std::string decodeFromEmbeddingsSync(
        const float* embeddings,
        int32_t num_tokens,
        int32_t embedding_dim,
        int32_t max_output_tokens
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

private:
    // llama.cpp state
    llama_model* model_ = nullptr;
    llama_context* context_ = nullptr;
    GLMASRDecoderConfig config_;

    // Thread-safety state
    std::atomic<bool> is_loaded_{false};
    std::atomic<bool> is_generating_{false};
    std::atomic<bool> stop_requested_{false};
    std::mutex generation_mutex_;

    // Helper methods
    std::string detokenize(llama_token token);
    void resetContext();

    /**
     * Inject embeddings directly into the model context.
     *
     * This bypasses normal tokenization and directly sets the
     * input embeddings for the first forward pass.
     *
     * @param embeddings Flattened embedding array
     * @param num_tokens Number of tokens
     * @param embedding_dim Embedding dimension
     * @return true if injection succeeded
     */
    bool injectEmbeddings(
        const float* embeddings,
        int32_t num_tokens,
        int32_t embedding_dim
    );
};

} // namespace unamentis

#endif // UNAMENTIS_GLM_ASR_DECODER_H
