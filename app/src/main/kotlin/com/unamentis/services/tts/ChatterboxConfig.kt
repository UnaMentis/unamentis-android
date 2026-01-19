package com.unamentis.services.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for Chatterbox TTS service.
 *
 * Controls emotional expression, generation quality, and speech characteristics.
 *
 * @property exaggeration Emotional intensity multiplier (0.0-1.5). Higher values produce
 *     more expressive speech. Default: 0.5
 * @property cfgWeight Classifier-free guidance weight (0.0-1.0). Higher values produce
 *     more faithful generation to the prompt. Default: 0.5
 * @property speed Speaking rate multiplier (0.5-2.0). Default: 1.0
 * @property language Language code for synthesis. Default: English
 * @property enableParalinguisticTags Enable processing of [laugh], [sigh], etc. Default: true
 * @property voiceId Voice identifier for consistent voice selection. Default: null (use default)
 */
@Serializable
data class ChatterboxConfig(
    val exaggeration: Float = 0.5f,
    val cfgWeight: Float = 0.5f,
    val speed: Float = 1.0f,
    val language: ChatterboxLanguage = ChatterboxLanguage.ENGLISH,
    val enableParalinguisticTags: Boolean = true,
    val voiceId: String? = null,
) {
    init {
        require(exaggeration in 0.0f..1.5f) { "exaggeration must be in range 0.0-1.5" }
        require(cfgWeight in 0.0f..1.0f) { "cfgWeight must be in range 0.0-1.0" }
        require(speed in 0.5f..2.0f) { "speed must be in range 0.5-2.0" }
    }

    companion object {
        /** Default balanced configuration. */
        val DEFAULT = ChatterboxConfig()

        /** Natural-sounding speech with moderate expression. */
        val NATURAL =
            ChatterboxConfig(
                exaggeration = 0.3f,
                cfgWeight = 0.6f,
                speed = 1.0f,
            )

        /** More expressive and dynamic speech. */
        val EXPRESSIVE =
            ChatterboxConfig(
                exaggeration = 0.8f,
                cfgWeight = 0.4f,
                speed = 1.0f,
            )

        /** Optimized for low latency at the cost of some quality. */
        val LOW_LATENCY =
            ChatterboxConfig(
                exaggeration = 0.3f,
                cfgWeight = 0.7f,
                speed = 1.1f,
            )

        /** Calm, measured speech for educational content. */
        val TUTOR =
            ChatterboxConfig(
                exaggeration = 0.4f,
                cfgWeight = 0.6f,
                speed = 0.95f,
            )
    }
}

/**
 * Supported languages for Chatterbox TTS.
 *
 * Chatterbox supports 23 languages with varying quality levels.
 */
@Serializable
enum class ChatterboxLanguage(val code: String, val displayName: String) {
    @SerialName("en")
    ENGLISH("en", "English"),

    @SerialName("es")
    SPANISH("es", "Spanish"),

    @SerialName("fr")
    FRENCH("fr", "French"),

    @SerialName("de")
    GERMAN("de", "German"),

    @SerialName("it")
    ITALIAN("it", "Italian"),

    @SerialName("pt")
    PORTUGUESE("pt", "Portuguese"),

    @SerialName("pl")
    POLISH("pl", "Polish"),

    @SerialName("tr")
    TURKISH("tr", "Turkish"),

    @SerialName("ru")
    RUSSIAN("ru", "Russian"),

    @SerialName("nl")
    DUTCH("nl", "Dutch"),

    @SerialName("cs")
    CZECH("cs", "Czech"),

    @SerialName("ar")
    ARABIC("ar", "Arabic"),

    @SerialName("zh-cn")
    CHINESE_SIMPLIFIED("zh-cn", "Chinese (Simplified)"),

    @SerialName("ja")
    JAPANESE("ja", "Japanese"),

    @SerialName("hu")
    HUNGARIAN("hu", "Hungarian"),

    @SerialName("ko")
    KOREAN("ko", "Korean"),

    @SerialName("hi")
    HINDI("hi", "Hindi"),

    @SerialName("sv")
    SWEDISH("sv", "Swedish"),

    @SerialName("da")
    DANISH("da", "Danish"),

    @SerialName("fi")
    FINNISH("fi", "Finnish"),

    @SerialName("el")
    GREEK("el", "Greek"),

    @SerialName("he")
    HEBREW("he", "Hebrew"),

    @SerialName("uk")
    UKRAINIAN("uk", "Ukrainian"),
}

/**
 * Paralinguistic tags supported by Chatterbox.
 *
 * These can be embedded in text to add non-verbal expressions.
 */
object ChatterboxParalinguisticTags {
    const val LAUGH = "[laugh]"
    const val SIGH = "[sigh]"
    const val GASP = "[gasp]"
    const val COUGH = "[cough]"
    const val CLEAR_THROAT = "[clear_throat]"
    const val HMMMM = "[hmmm]"
    const val UHUH = "[uh-huh]"

    /** All supported tags. */
    val ALL_TAGS =
        listOf(
            LAUGH,
            SIGH,
            GASP,
            COUGH,
            CLEAR_THROAT,
            HMMMM,
            UHUH,
        )
}

/**
 * Request body for Chatterbox streaming endpoint.
 */
@Serializable
internal data class ChatterboxStreamRequest(
    val text: String,
    val exaggeration: Float,
    @SerialName("cfg_weight")
    val cfgWeight: Float,
    val speed: Float,
    val language: String,
    @SerialName("voice_id")
    val voiceId: String? = null,
)

/**
 * Request body for Chatterbox OpenAI-compatible endpoint.
 */
@Serializable
internal data class ChatterboxSpeechRequest(
    val model: String = "chatterbox",
    val input: String,
    val voice: String = "default",
    @SerialName("response_format")
    val responseFormat: String = "pcm",
    val speed: Float = 1.0f,
)

/**
 * Error response from Chatterbox API.
 */
@Serializable
internal data class ChatterboxErrorResponse(
    val error: ChatterboxError? = null,
)

@Serializable
internal data class ChatterboxError(
    val message: String,
    val type: String? = null,
    val code: String? = null,
)
