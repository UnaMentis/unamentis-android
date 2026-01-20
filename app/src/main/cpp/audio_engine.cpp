#include "audio_engine.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "UnaMentis-Audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace unamentis {

// Playback buffer size (2 seconds at 16kHz mono)
static constexpr size_t PLAYBACK_BUFFER_SIZE = 16000 * 2;

AudioEngine::AudioEngine() {
    LOGI("AudioEngine created");
    playback_buffer_.resize(PLAYBACK_BUFFER_SIZE);
}

AudioEngine::~AudioEngine() {
    stopCapture();
    stopPlayback();
    closeStreams();
    LOGI("AudioEngine destroyed");
}

bool AudioEngine::initialize(const AudioConfig& config) {
    config_ = config;

    // Pre-allocate conversion buffer for typical frame sizes
    conversion_buffer_.resize(config_.frames_per_burst * 4);

    LOGI("AudioEngine initialized: sample_rate=%d, channels=%d, frames_per_burst=%d",
         config_.sample_rate, config_.channel_count, config_.frames_per_burst);

    return true;
}

bool AudioEngine::createCaptureStream() {
    oboe::AudioStreamBuilder builder;

    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setSampleRate(config_.sample_rate)
           ->setChannelCount(config_.channel_count)
           ->setFormat(oboe::AudioFormat::Float)
           ->setInputPreset(oboe::InputPreset::VoiceRecognition)
           ->setCallback(this)
           ->setFramesPerCallback(config_.frames_per_burst);

    oboe::Result result = builder.openStream(capture_stream_);

    if (result != oboe::Result::OK) {
        LOGE("Failed to create capture stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Capture stream created: format=%s, sample_rate=%d, frames_per_burst=%d, buffer_capacity=%d",
         oboe::convertToText(capture_stream_->getFormat()),
         capture_stream_->getSampleRate(),
         capture_stream_->getFramesPerBurst(),
         capture_stream_->getBufferCapacityInFrames());

    return true;
}

bool AudioEngine::createPlaybackStream() {
    oboe::AudioStreamBuilder builder;

    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setSampleRate(config_.sample_rate)
           ->setChannelCount(config_.channel_count)
           ->setFormat(oboe::AudioFormat::Float)
           ->setCallback(this)
           ->setFramesPerCallback(config_.frames_per_burst);

    oboe::Result result = builder.openStream(playback_stream_);

    if (result != oboe::Result::OK) {
        LOGE("Failed to create playback stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Playback stream created: format=%s, sample_rate=%d, buffer_capacity=%d",
         oboe::convertToText(playback_stream_->getFormat()),
         playback_stream_->getSampleRate(),
         playback_stream_->getBufferCapacityInFrames());

    return true;
}

void AudioEngine::closeStreams() {
    if (capture_stream_) {
        capture_stream_->close();
        capture_stream_.reset();
    }
    if (playback_stream_) {
        playback_stream_->close();
        playback_stream_.reset();
    }
}

bool AudioEngine::startCapture(AudioCallback callback, void* user_data) {
    if (is_capturing_.load()) {
        LOGW("Already capturing");
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(callback_mutex_);
        capture_callback_ = callback;
        user_data_ = user_data;
    }

    // Create capture stream if needed
    if (!capture_stream_ && !createCaptureStream()) {
        LOGE("Failed to create capture stream");
        return false;
    }

    // Start the stream
    oboe::Result result = capture_stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start capture stream: %s", oboe::convertToText(result));
        return false;
    }

    is_capturing_.store(true);
    LOGI("Audio capture started");

    return true;
}

void AudioEngine::stopCapture() {
    if (!is_capturing_.load()) {
        return;
    }

    is_capturing_.store(false);

    if (capture_stream_) {
        capture_stream_->requestStop();
    }

    {
        std::lock_guard<std::mutex> lock(callback_mutex_);
        capture_callback_ = nullptr;
        user_data_ = nullptr;
    }

    LOGI("Audio capture stopped");
}

bool AudioEngine::queuePlayback(const float* audio_data, int32_t frame_count) {
    if (!audio_data || frame_count <= 0) {
        return false;
    }

    // Create playback stream if needed
    if (!playback_stream_ && !createPlaybackStream()) {
        LOGE("Failed to create playback stream");
        return false;
    }

    // Queue audio data
    {
        std::lock_guard<std::mutex> lock(playback_mutex_);

        for (int32_t i = 0; i < frame_count; ++i) {
            playback_buffer_[playback_write_pos_] = audio_data[i];
            playback_write_pos_ = (playback_write_pos_ + 1) % PLAYBACK_BUFFER_SIZE;

            // Handle buffer overflow by dropping oldest samples
            if (playback_write_pos_ == playback_read_pos_) {
                playback_read_pos_ = (playback_read_pos_ + 1) % PLAYBACK_BUFFER_SIZE;
            }
        }
    }

    // Start playback if not already playing
    if (!is_playing_.load()) {
        oboe::Result result = playback_stream_->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start playback: %s", oboe::convertToText(result));
            return false;
        }
        is_playing_.store(true);
        LOGI("Audio playback started");
    }

    return true;
}

void AudioEngine::stopPlayback() {
    if (!is_playing_.load()) {
        return;
    }

    is_playing_.store(false);

    if (playback_stream_) {
        playback_stream_->requestStop();
    }

    // Clear playback buffer
    {
        std::lock_guard<std::mutex> lock(playback_mutex_);
        playback_read_pos_ = 0;
        playback_write_pos_ = 0;
    }

    LOGI("Audio playback stopped");
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames) {

    if (stream->getDirection() == oboe::Direction::Input) {
        // Handle capture callback
        if (!is_capturing_.load()) {
            return oboe::DataCallbackResult::Stop;
        }

        std::lock_guard<std::mutex> lock(callback_mutex_);
        if (capture_callback_) {
            // Audio data is already float format (we requested Float in builder)
            const float* floatData = static_cast<const float*>(audioData);
            capture_callback_(floatData, numFrames, user_data_);
        }

        return oboe::DataCallbackResult::Continue;
    } else {
        // Handle playback callback
        if (!is_playing_.load()) {
            return oboe::DataCallbackResult::Stop;
        }

        float* outputBuffer = static_cast<float*>(audioData);

        std::lock_guard<std::mutex> lock(playback_mutex_);

        for (int32_t i = 0; i < numFrames; ++i) {
            if (playback_read_pos_ != playback_write_pos_) {
                outputBuffer[i] = playback_buffer_[playback_read_pos_];
                playback_read_pos_ = (playback_read_pos_ + 1) % PLAYBACK_BUFFER_SIZE;
            } else {
                // Buffer underrun - output silence
                outputBuffer[i] = 0.0f;
            }
        }

        // Check if we should stop (buffer empty)
        if (playback_read_pos_ == playback_write_pos_) {
            is_playing_.store(false);
            return oboe::DataCallbackResult::Stop;
        }

        return oboe::DataCallbackResult::Continue;
    }
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result result) {
    LOGE("Audio stream error before close: %s", oboe::convertToText(result));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result result) {
    LOGE("Audio stream error after close: %s", oboe::convertToText(result));

    // Attempt to restart the stream
    if (stream->getDirection() == oboe::Direction::Input && is_capturing_.load()) {
        LOGI("Attempting to restart capture stream...");
        if (createCaptureStream()) {
            capture_stream_->requestStart();
        }
    } else if (stream->getDirection() == oboe::Direction::Output && is_playing_.load()) {
        LOGI("Attempting to restart playback stream...");
        if (createPlaybackStream()) {
            playback_stream_->requestStart();
        }
    }
}

} // namespace unamentis
