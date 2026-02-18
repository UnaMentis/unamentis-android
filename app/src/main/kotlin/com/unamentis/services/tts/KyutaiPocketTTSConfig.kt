package com.unamentis.services.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for Kyutai Pocket TTS on-device model.
 *
 * Pocket TTS is a ~100M parameter on-device text-to-speech model featuring:
 * - 8 built-in voices (alba, marius, javert, jean, fantine, cosette, eponine, azelma)
 * - 5-second voice cloning capability
 * - 24kHz high-quality audio output
 * - ~200ms time to first audio
 * - 1.84% WER (best in class for on-device)
 * - CC-BY-4.0 licensed (code and weights)
 *
 * @property modelPath Path to the model directory on device. Null uses default location.
 * @property voiceId Index of the built-in voice to use (0-7).
 * @property sampleRate Output audio sample rate in Hz.
 * @property temperature Sampling temperature controlling generation randomness (0.0-1.5).
 * @property topP Top-p (nucleus) sampling threshold (0.1-1.0).
 * @property speed Speaking rate multiplier (0.5-2.0).
 * @property consistencySteps Number of consistency sampling steps for quality (1-4).
 * @property maxTokens Maximum number of tokens to generate per synthesis call.
 * @property useGpuAcceleration Whether to use GPU/NNAPI acceleration for inference.
 * @property enablePrefetch Whether to prefetch next tokens during streaming for lower latency.
 * @property seed Random seed for reproducible generation. Null for non-deterministic output.
 */
@Serializable
data class KyutaiPocketTTSConfig(
    @SerialName("model_path")
    val modelPath: String? = null,
    @SerialName("voice_id")
    val voiceId: Int = 0,
    @SerialName("sample_rate")
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val temperature: Float = DEFAULT_TEMPERATURE,
    @SerialName("top_p")
    val topP: Float = DEFAULT_TOP_P,
    val speed: Float = DEFAULT_SPEED,
    @SerialName("consistency_steps")
    val consistencySteps: Int = DEFAULT_CONSISTENCY_STEPS,
    @SerialName("max_tokens")
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    @SerialName("use_gpu_acceleration")
    val useGpuAcceleration: Boolean = true,
    @SerialName("enable_prefetch")
    val enablePrefetch: Boolean = true,
    val seed: Int? = null,
) {
    init {
        require(voiceId in VOICE_INDEX_MIN..VOICE_INDEX_MAX) {
            "voiceId must be in range $VOICE_INDEX_MIN-$VOICE_INDEX_MAX"
        }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(temperature in TEMPERATURE_MIN..TEMPERATURE_MAX) {
            "temperature must be in range $TEMPERATURE_MIN-$TEMPERATURE_MAX"
        }
        require(topP in TOP_P_MIN..TOP_P_MAX) {
            "topP must be in range $TOP_P_MIN-$TOP_P_MAX"
        }
        require(speed in SPEED_MIN..SPEED_MAX) {
            "speed must be in range $SPEED_MIN-$SPEED_MAX"
        }
        require(consistencySteps in CONSISTENCY_STEPS_MIN..CONSISTENCY_STEPS_MAX) {
            "consistencySteps must be in range $CONSISTENCY_STEPS_MIN-$CONSISTENCY_STEPS_MAX"
        }
        require(maxTokens > 0) { "maxTokens must be positive" }
    }

    companion object {
        /** Default output sample rate in Hz. */
        const val DEFAULT_SAMPLE_RATE: Int = 24_000

        /** Default sampling temperature. */
        const val DEFAULT_TEMPERATURE: Float = 0.7f

        /** Default top-p sampling threshold. */
        const val DEFAULT_TOP_P: Float = 0.9f

        /** Default speaking speed multiplier. */
        const val DEFAULT_SPEED: Float = 1.0f

        /** Default consistency sampling steps. */
        const val DEFAULT_CONSISTENCY_STEPS: Int = 2

        /** Default maximum tokens per synthesis. */
        const val DEFAULT_MAX_TOKENS: Int = 2048

        /** Minimum voice index (inclusive). */
        const val VOICE_INDEX_MIN: Int = 0

        /** Maximum voice index (inclusive). */
        const val VOICE_INDEX_MAX: Int = 7

        /** Minimum temperature (inclusive). */
        const val TEMPERATURE_MIN: Float = 0.0f

        /** Maximum temperature (inclusive). */
        const val TEMPERATURE_MAX: Float = 1.5f

        /** Minimum top-p (inclusive). */
        const val TOP_P_MIN: Float = 0.1f

        /** Maximum top-p (inclusive). */
        const val TOP_P_MAX: Float = 1.0f

        /** Minimum speed (inclusive). */
        const val SPEED_MIN: Float = 0.5f

        /** Maximum speed (inclusive). */
        const val SPEED_MAX: Float = 2.0f

        /** Minimum consistency steps (inclusive). */
        const val CONSISTENCY_STEPS_MIN: Int = 1

        /** Maximum consistency steps (inclusive). */
        const val CONSISTENCY_STEPS_MAX: Int = 4

        /** Total model size in MB (all components bundled). */
        const val MODEL_SIZE_MB: Int = 230

        /** Model parameter count. */
        const val MODEL_PARAMETERS: Long = 117_856_642L

        /** Typical time to first audio in milliseconds. */
        const val TYPICAL_LATENCY_MS: Int = 200

        /** Default balanced configuration. */
        fun default(): KyutaiPocketTTSConfig = KyutaiPocketTTSConfig()

        /** Low latency preset optimized for voice agents. Fastest time to first byte. */
        fun lowLatency(): KyutaiPocketTTSConfig =
            KyutaiPocketTTSConfig(
                temperature = 0.5f,
                topP = 0.85f,
                speed = 1.1f,
                consistencySteps = 1,
                enablePrefetch = true,
            )

        /** High quality preset for pre-rendered content. Best quality, higher latency. */
        fun highQuality(): KyutaiPocketTTSConfig =
            KyutaiPocketTTSConfig(
                temperature = 0.7f,
                topP = 0.95f,
                speed = 1.0f,
                consistencySteps = 4,
                enablePrefetch = false,
            )

        /** Battery saver preset. CPU-only inference for lower power consumption. */
        fun batterySaver(): KyutaiPocketTTSConfig =
            KyutaiPocketTTSConfig(
                temperature = 0.6f,
                topP = 0.9f,
                speed = 1.0f,
                consistencySteps = 1,
                useGpuAcceleration = false,
                enablePrefetch = false,
            )
    }
}

/**
 * Built-in voices for Kyutai Pocket TTS.
 *
 * Each voice has a unique character and style from the Les Miserables universe.
 *
 * @property index Numeric index used by the native engine (0-7).
 * @property gender Voice gender classification.
 */
@Serializable
enum class KyutaiPocketVoice(
    val index: Int,
    val gender: KyutaiPocketVoiceGender,
) {
    @SerialName("alba")
    ALBA(0, KyutaiPocketVoiceGender.FEMALE),

    @SerialName("marius")
    MARIUS(1, KyutaiPocketVoiceGender.MALE),

    @SerialName("javert")
    JAVERT(2, KyutaiPocketVoiceGender.MALE),

    @SerialName("jean")
    JEAN(3, KyutaiPocketVoiceGender.MALE),

    @SerialName("fantine")
    FANTINE(4, KyutaiPocketVoiceGender.FEMALE),

    @SerialName("cosette")
    COSETTE(5, KyutaiPocketVoiceGender.FEMALE),

    @SerialName("eponine")
    EPONINE(6, KyutaiPocketVoiceGender.FEMALE),

    @SerialName("azelma")
    AZELMA(7, KyutaiPocketVoiceGender.FEMALE),
    ;

    companion object {
        /**
         * Look up a voice by its numeric index.
         *
         * @param index Voice index (0-7)
         * @return Matching voice, or null if index is out of range
         */
        fun fromIndex(index: Int): KyutaiPocketVoice? =
            entries.find { it.index == index }
    }
}

/**
 * Voice gender classification for filtering.
 */
@Serializable
enum class KyutaiPocketVoiceGender {
    @SerialName("female")
    FEMALE,

    @SerialName("male")
    MALE,
}
