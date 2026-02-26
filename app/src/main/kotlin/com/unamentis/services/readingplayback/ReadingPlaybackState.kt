package com.unamentis.services.readingplayback

/**
 * Current state of reading list playback.
 *
 * Maps to iOS ReadingPlaybackState enum.
 */
sealed class ReadingPlaybackState {
    data object Idle : ReadingPlaybackState()

    data object Loading : ReadingPlaybackState()

    data object Playing : ReadingPlaybackState()

    data object Paused : ReadingPlaybackState()

    data object Buffering : ReadingPlaybackState()

    data object Completed : ReadingPlaybackState()

    data class Error(val message: String) : ReadingPlaybackState()
}

/**
 * Lightweight data transfer object for reading chunk info.
 *
 * Used to cross coroutine/actor boundaries without Room entity dependencies.
 */
data class ReadingChunkData(
    val index: Int,
    val text: String,
    val characterOffset: Long = 0,
    val estimatedDurationSeconds: Float = 0.0f,
    val cachedAudioData: ByteArray? = null,
    val cachedAudioSampleRate: Double = 0.0,
) {
    /** Whether this chunk has pre-generated audio ready for instant playback. */
    val hasCachedAudio: Boolean
        get() = cachedAudioData != null && cachedAudioSampleRate > 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ReadingChunkData
        if (index != other.index) return false
        if (text != other.text) return false
        if (characterOffset != other.characterOffset) return false
        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + text.hashCode()
        result = 31 * result + characterOffset.hashCode()
        return result
    }
}

/**
 * Lightweight bookmark data for crossing coroutine boundaries.
 */
data class ReadingBookmarkData(
    val id: String,
    val chunkIndex: Int,
    val note: String? = null,
)

/**
 * Errors specific to reading playback.
 */
sealed class ReadingPlaybackError(message: String) : Exception(message) {
    class NotConfigured : ReadingPlaybackError("Reading playback service not configured")

    class NoChunks : ReadingPlaybackError("No chunks available for playback")

    class InvalidChunkIndex : ReadingPlaybackError("Invalid chunk index")

    class BufferTimeout : ReadingPlaybackError("Timed out waiting for audio buffer")

    class PlaybackFailed(message: String) : ReadingPlaybackError("Playback failed: $message")
}
