#ifndef UNAMENTIS_AUDIO_ENGINE_H
#define UNAMENTIS_AUDIO_ENGINE_H

#include <cstdint>
#include <memory>
#include <functional>

namespace unamentis {

/**
 * Audio configuration parameters.
 */
struct AudioConfig {
    int32_t sample_rate = 16000;      // 16kHz for STT compatibility
    int32_t channel_count = 1;         // Mono audio
    int32_t frames_per_burst = 192;    // ~12ms at 16kHz
};

/**
 * Callback function for audio data.
 *
 * @param audio_data Pointer to audio samples (float, -1.0 to 1.0)
 * @param frame_count Number of frames (samples for mono)
 * @param user_data User-provided context pointer
 */
using AudioCallback = std::function<void(const float* audio_data, int32_t frame_count, void* user_data)>;

/**
 * Low-latency audio engine using platform audio APIs.
 *
 * This implementation uses Android's low-latency audio path
 * to minimize recording and playback latency for voice conversations.
 */
class AudioEngine {
public:
    AudioEngine();
    ~AudioEngine();

    /**
     * Initialize the audio engine.
     *
     * @param config Audio configuration
     * @return true if initialization succeeded
     */
    bool initialize(const AudioConfig& config);

    /**
     * Start audio capture.
     *
     * @param callback Callback function for captured audio
     * @param user_data User context pointer passed to callback
     * @return true if capture started successfully
     */
    bool startCapture(AudioCallback callback, void* user_data);

    /**
     * Stop audio capture.
     */
    void stopCapture();

    /**
     * Queue audio data for playback.
     *
     * @param audio_data Audio samples to play (float, -1.0 to 1.0)
     * @param frame_count Number of frames
     * @return true if data was queued successfully
     */
    bool queuePlayback(const float* audio_data, int32_t frame_count);

    /**
     * Stop audio playback and clear buffer.
     */
    void stopPlayback();

    /**
     * Check if currently capturing.
     */
    bool isCapturing() const { return is_capturing_; }

    /**
     * Check if currently playing.
     */
    bool isPlaying() const { return is_playing_; }

    /**
     * Get current audio configuration.
     */
    const AudioConfig& getConfig() const { return config_; }

private:
    AudioConfig config_;
    bool is_capturing_ = false;
    bool is_playing_ = false;
    AudioCallback capture_callback_;
    void* user_data_ = nullptr;

    // Platform-specific implementation details would go here
    // For now, this is a simplified version without Oboe dependency
};

} // namespace unamentis

#endif // UNAMENTIS_AUDIO_ENGINE_H
