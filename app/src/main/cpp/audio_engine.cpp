#include "audio_engine.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "UnaMentis-Audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace unamentis {

AudioEngine::AudioEngine() {
    LOGI("AudioEngine created");
}

AudioEngine::~AudioEngine() {
    stopCapture();
    stopPlayback();
    LOGI("AudioEngine destroyed");
}

bool AudioEngine::initialize(const AudioConfig& config) {
    config_ = config;

    LOGI("AudioEngine initialized: sample_rate=%d, channels=%d, frames_per_burst=%d",
         config_.sample_rate, config_.channel_count, config_.frames_per_burst);

    return true;
}

bool AudioEngine::startCapture(AudioCallback callback, void* user_data) {
    if (is_capturing_) {
        LOGE("Already capturing");
        return false;
    }

    capture_callback_ = callback;
    user_data_ = user_data;
    is_capturing_ = true;

    LOGI("Audio capture started");

    // In a full implementation with Oboe, we would:
    // 1. Create an audio stream builder
    // 2. Configure for recording
    // 3. Set up callback
    // 4. Open and start the stream

    return true;
}

void AudioEngine::stopCapture() {
    if (!is_capturing_) {
        return;
    }

    is_capturing_ = false;
    capture_callback_ = nullptr;
    user_data_ = nullptr;

    LOGI("Audio capture stopped");
}

bool AudioEngine::queuePlayback(const float* audio_data, int32_t frame_count) {
    if (!audio_data || frame_count <= 0) {
        return false;
    }

    if (!is_playing_) {
        is_playing_ = true;
        LOGI("Audio playback started");
    }

    // In a full implementation with Oboe, we would:
    // 1. Queue the audio data to a playback buffer
    // 2. Trigger playback if not already playing

    return true;
}

void AudioEngine::stopPlayback() {
    if (!is_playing_) {
        return;
    }

    is_playing_ = false;

    LOGI("Audio playback stopped");
}

} // namespace unamentis
