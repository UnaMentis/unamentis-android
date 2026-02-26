package com.unamentis.core.telemetry

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// TTFA Feature Identifiers
// ---------------------------------------------------------------------------

/**
 * Standardized feature IDs for TTFA measurement.
 * Each audio-producing feature in the app has a unique ID.
 */
enum class TTFAFeature(val id: String) {
    // Voice sessions
    SESSION_CHAT("session.chat"),
    SESSION_CURRICULUM("session.curriculum"),

    // Knowledge Bowl
    KB_ORAL("kb.oral"),
    KB_WRITTEN("kb.written"),
    KB_DRILL("kb.drill"),
    KB_REBOUND("kb.rebound"),
    KB_CONFERENCE("kb.conference"),

    // Reading List
    READING_PLAY("reading.play"),
    READING_RESUME("reading.resume"),
}

// ---------------------------------------------------------------------------
// TTFA Event Types
// ---------------------------------------------------------------------------

/**
 * TTFA lifecycle events emitted via [Log].
 */
enum class TTFAEvent(val tag: String) {
    /** User action triggered (button tap, deep link, play pressed). */
    ACTIVATE("ACTIVATE"),

    /** First TTS chunk received from synthesis. */
    TTS_FIRST("TTS_FIRST"),

    /** First audio buffer scheduled to AudioTrack / Oboe stream. */
    AUDIO_SCHEDULED("AUDIO_SCHEDULED"),

    /** Audio playback started (closest to actual sound output). */
    AUDIO_PLAYING("AUDIO_PLAYING"),

    /** Audio served from cache (instant path). */
    CACHED_HIT("CACHED_HIT"),

    /** Feature failed to produce audio. */
    ERROR("ERROR"),
}

// ---------------------------------------------------------------------------
// TTFA Instrumentation Service
// ---------------------------------------------------------------------------

/**
 * Lightweight Time-To-First-Audio measurement service.
 *
 * Emits structured events via [android.util.Log.i] that an external TTFA
 * harness can capture using `adb logcat -s TTFA`.
 *
 * This class has minimal overhead: just [Log.i] writes and [System.nanoTime]
 * reads. It is compiled in all build types (debug and release).
 *
 * Log format: `[TTFA] EVENT|feature_id|elapsed_ms|metadata`
 *
 * Usage:
 * ```kotlin
 * ttfaInstrumentation.markActivation(TTFAFeature.READING_PLAY)
 * // ... audio pipeline runs ...
 * // AudioEngine automatically emits AUDIO_SCHEDULED and AUDIO_PLAYING
 * ```
 *
 * The external TTFA harness captures these events to compute:
 * - Activation to first TTS chunk (TTS pipeline latency)
 * - Activation to audio scheduled (full pipeline including buffer creation)
 * - Activation to audio playing (true TTFA, closest to audible output)
 */
@Singleton
class TTFAInstrumentation
    @Inject
    constructor() {
        /** Currently active feature being measured (only one at a time). */
        @Volatile
        private var activeFeature: TTFAFeature? = null

        /** [System.nanoTime] when the current feature was activated. */
        @Volatile
        private var activationTime: Long = 0L

        /** Whether a measurement is currently in progress. */
        val isActive: Boolean
            get() = activeFeature != null

        private val lock = Any()

        // -- Activation -----------------------------------------------------------

        /**
         * Mark the start of a TTFA measurement for a [feature].
         *
         * Call this at the exact moment the user triggers audio
         * (tap play, start session, etc.).
         *
         * If a previous measurement is still active it will be auto-closed
         * with an [TTFAEvent.ERROR] event.
         *
         * @param feature the feature that is starting audio production
         * @param metadata optional key-value pairs attached to the event
         */
        fun markActivation(
            feature: TTFAFeature,
            metadata: Map<String, String> = emptyMap(),
        ) {
            synchronized(lock) {
                // Auto-close a previous measurement that was never completed
                activeFeature?.let { current ->
                    emit(
                        TTFAEvent.ERROR,
                        current,
                        elapsedMs = 0.0,
                        metadata = mapOf("reason" to "superseded by ${feature.id}"),
                    )
                }

                activeFeature = feature
                activationTime = System.nanoTime()
                emit(TTFAEvent.ACTIVATE, feature, elapsedMs = 0.0, metadata = metadata)
            }
        }

        // -- Milestone Events -----------------------------------------------------

        /**
         * Mark when the first TTS audio chunk is received from synthesis.
         */
        fun markTTSFirstChunk() {
            synchronized(lock) {
                val feature = activeFeature ?: return
                val elapsed = elapsedMs()
                emit(TTFAEvent.TTS_FIRST, feature, elapsed)
            }
        }

        /**
         * Mark when the first audio buffer is scheduled to the audio output stream.
         */
        fun markAudioScheduled() {
            synchronized(lock) {
                val feature = activeFeature ?: return
                val elapsed = elapsedMs()
                emit(TTFAEvent.AUDIO_SCHEDULED, feature, elapsed)
            }
        }

        /**
         * Mark when audio playback actually starts (closest to audible output).
         *
         * This completes the TTFA measurement and clears the active feature.
         */
        fun markAudioPlaying() {
            synchronized(lock) {
                val feature = activeFeature ?: return
                val elapsed = elapsedMs()
                emit(TTFAEvent.AUDIO_PLAYING, feature, elapsed)
                // Measurement complete
                activeFeature = null
            }
        }

        /**
         * Mark when audio is served from cache (instant path).
         */
        fun markCachedHit() {
            synchronized(lock) {
                val feature = activeFeature ?: return
                val elapsed = elapsedMs()
                emit(TTFAEvent.CACHED_HIT, feature, elapsed)
            }
        }

        /**
         * Mark an error during audio production.
         *
         * This completes the TTFA measurement and clears the active feature.
         *
         * @param message a short description of what went wrong
         */
        fun markError(message: String) {
            synchronized(lock) {
                val feature = activeFeature ?: return
                val elapsed = elapsedMs()
                emit(TTFAEvent.ERROR, feature, elapsed, metadata = mapOf("error" to message))
                activeFeature = null
            }
        }

        // -- Internal -------------------------------------------------------------

        /**
         * Compute milliseconds elapsed since [activationTime].
         *
         * Uses [System.nanoTime] which is monotonic and not affected by wall-clock
         * adjustments, analogous to `mach_absolute_time()` on iOS.
         */
        private fun elapsedMs(): Double {
            val deltaNs = System.nanoTime() - activationTime
            return deltaNs / NS_PER_MS
        }

        /**
         * Emit a structured TTFA event via [Log.i].
         *
         * Format: `[TTFA] EVENT|feature_id|elapsed_ms|metadata`
         */
        private fun emit(
            event: TTFAEvent,
            feature: TTFAFeature,
            elapsedMs: Double,
            metadata: Map<String, String> = emptyMap(),
        ) {
            val metadataString =
                if (metadata.isEmpty()) {
                    ""
                } else {
                    metadata.entries.joinToString(",") { "${it.key}=${it.value}" }
                }

            Log.i(
                LOG_TAG,
                "[TTFA] ${event.tag}|${feature.id}|${"%.2f".format(elapsedMs)}|$metadataString",
            )
        }

        internal companion object {
            /** Log tag used for all TTFA emissions. */
            const val LOG_TAG = "TTFA"

            /** Nanoseconds per millisecond. */
            private const val NS_PER_MS = 1_000_000.0
        }
    }
