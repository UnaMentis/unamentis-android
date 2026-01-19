package com.unamentis.modules.knowledgebowl.core.voice

/**
 * Cached audio entry with access tracking for LRU eviction.
 *
 * @property data Raw audio data (WAV format)
 * @property durationSeconds Duration of the audio
 * @property sampleRate Sample rate in Hz
 * @property cachedAt Timestamp when cached (epoch millis)
 */
data class KBCachedAudio(
    val data: ByteArray,
    val durationSeconds: Double,
    val sampleRate: Int,
    val cachedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Size of the audio data in bytes.
     */
    val sizeBytes: Int
        get() = data.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KBCachedAudio

        if (!data.contentEquals(other.data)) return false
        if (durationSeconds != other.durationSeconds) return false
        if (sampleRate != other.sampleRate) return false
        if (cachedAt != other.cachedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + durationSeconds.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + cachedAt.hashCode()
        return result
    }
}

/**
 * Batch query result for prefetching.
 */
data class KBAudioBatchInfo(
    val questionId: String,
    val segment: KBSegmentType,
    val available: Boolean,
    val durationSeconds: Double,
    val sizeBytes: Int,
)
