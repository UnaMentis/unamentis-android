#ifndef UNAMENTIS_AUDIO_ENGINE_H
#define UNAMENTIS_AUDIO_ENGINE_H

#include <cstdint>
#include <memory>
#include <functional>
#include <atomic>
#include <mutex>
#include <vector>
#include <oboe/Oboe.h>

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
 * Low-latency audio engine using Oboe.
 *
 * This implementation uses Google's Oboe library for lowest-latency
 * audio recording and playback on Android devices.
 *
 * Features:
 * - Automatic AAudio/OpenSL ES selection
 * - Low-latency audio capture at 16kHz
 * - Configurable buffer sizes
 * - Thread-safe callbacks
 */
class AudioEngine : public oboe::AudioStreamCallback {
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
    bool isCapturing() const { return is_capturing_.load(); }

    /**
     * Check if currently playing.
     */
    bool isPlaying() const { return is_playing_.load(); }

    /**
     * Get current audio configuration.
     */
    const AudioConfig& getConfig() const { return config_; }

    // Oboe callback interface
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result result) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) override;

private:
    AudioConfig config_;
    std::atomic<bool> is_capturing_{false};
    std::atomic<bool> is_playing_{false};

    // Capture stream
    std::shared_ptr<oboe::AudioStream> capture_stream_;
    AudioCallback capture_callback_;
    void* user_data_ = nullptr;
    std::mutex callback_mutex_;

    // Playback stream
    std::shared_ptr<oboe::AudioStream> playback_stream_;
    std::vector<float> playback_buffer_;
    std::mutex playback_mutex_;
    size_t playback_read_pos_ = 0;
    size_t playback_write_pos_ = 0;

    // Conversion buffer for int16 to float
    std::vector<float> conversion_buffer_;

    bool createCaptureStream();
    bool createPlaybackStream();
    void closeStreams();
};

} // namespace unamentis

#endif // UNAMENTIS_AUDIO_ENGINE_H
