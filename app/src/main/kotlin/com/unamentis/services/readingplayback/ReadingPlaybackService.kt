package com.unamentis.services.readingplayback

import android.util.Log
import com.unamentis.core.audio.AudioEngine
import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for playing reading list content with low-latency TTS.
 *
 * Key features:
 * - Pre-buffers 2-3 chunks ahead for seamless playback
 * - Works with any TTSService (protocol-based)
 * - Auto-saves position via ReadingListManager
 * - Supports pause/resume for barge-in Q&A
 *
 * Maps to iOS ReadingPlaybackService actor.
 */
@Suppress("TooManyFunctions")
@Singleton
class ReadingPlaybackService
    @Inject
    constructor(
        private val ttsService: TTSService,
        private val audioEngine: AudioEngine,
        private val readingListManager: ReadingListManager,
        private val scope: CoroutineScope,
    ) {
        companion object {
            private const val TAG = "ReadingPlayback"
            private const val PRE_BUFFER_COUNT = 3
            private const val BUFFER_TIMEOUT_MS = 30_000L
            private const val BUFFER_POLL_MS = 50L
        }

        // MARK: - State

        private val _state = MutableStateFlow<ReadingPlaybackState>(ReadingPlaybackState.Idle)
        val state: StateFlow<ReadingPlaybackState> = _state.asStateFlow()

        private val _currentChunkIndex = MutableStateFlow(0)
        val currentChunkIndex: StateFlow<Int> = _currentChunkIndex.asStateFlow()

        private val _totalChunks = MutableStateFlow(0)
        val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

        /** Current reading item ID. */
        var currentItemId: String? = null
            private set

        private var chunks: List<ReadingChunkData> = emptyList()
        private val preBufferedChunks = mutableMapOf<Int, PreBufferedChunk>()
        private val mutex = Mutex()

        private var preBufferJob: Job? = null
        private var playbackJob: Job? = null
        private var isPreBuffering = false

        // MARK: - Playback Control

        /**
         * Start playing a reading item from a given position.
         *
         * @param itemId The reading item ID
         * @param chunks Pre-loaded chunk data from the item
         * @param startIndex Starting chunk index (defaults to 0)
         */
        suspend fun startPlayback(
            itemId: String,
            chunks: List<ReadingChunkData>,
            startIndex: Int = 0,
        ) {
            require(chunks.isNotEmpty()) { "No chunks available for playback" }

            Log.i(TAG, "Starting playback for item $itemId, ${chunks.size} chunks, starting at $startIndex")

            stopPlayback()

            this.currentItemId = itemId
            this.chunks = chunks
            val clampedStart = startIndex.coerceIn(0, chunks.size - 1)
            _currentChunkIndex.value = clampedStart
            _totalChunks.value = chunks.size
            mutex.withLock { preBufferedChunks.clear() }

            // Pre-buffer from the NEXT chunk
            val nextIndex = clampedStart + 1
            if (nextIndex < chunks.size) {
                startPreBuffering(nextIndex)
            }

            // Check if first chunk has pre-generated audio
            val firstChunk = chunks[clampedStart]
            val useCachedAudio = firstChunk.hasCachedAudio

            if (useCachedAudio) {
                Log.i(TAG, "Using pre-generated audio for chunk $clampedStart (instant start)")
            }

            _state.value = ReadingPlaybackState.Playing
            startPlaybackLoop(streamFirstChunk = true, useCachedAudio = useCachedAudio)
        }

        /** Pause playback (for barge-in). */
        suspend fun pause() {
            if (_state.value !is ReadingPlaybackState.Playing) return

            Log.d(TAG, "Pausing playback at chunk ${_currentChunkIndex.value}")
            audioEngine.stopPlayback()
            _state.value = ReadingPlaybackState.Paused
            saveCurrentPosition()
        }

        /** Resume playback after pause. */
        fun resume() {
            if (_state.value !is ReadingPlaybackState.Paused) return

            Log.d(TAG, "Resuming playback from chunk ${_currentChunkIndex.value}")
            _state.value = ReadingPlaybackState.Playing

            if (playbackJob == null || playbackJob?.isActive != true) {
                startPlaybackLoop()
            }
        }

        /** Stop playback completely. */
        suspend fun stopPlayback() {
            if (_state.value is ReadingPlaybackState.Idle) return

            Log.d(TAG, "Stopping playback")

            preBufferJob?.cancel()
            preBufferJob = null
            playbackJob?.cancel()
            playbackJob = null

            audioEngine.stopPlayback()
            saveCurrentPosition()

            currentItemId = null
            chunks = emptyList()
            mutex.withLock { preBufferedChunks.clear() }
            _state.value = ReadingPlaybackState.Idle
        }

        /**
         * Skip to a specific chunk.
         *
         * @param index The chunk index to skip to
         */
        suspend fun skipToChunk(index: Int) {
            require(index in 0 until chunks.size) { "Invalid chunk index: $index" }

            Log.d(TAG, "Skipping to chunk $index")

            playbackJob?.cancel()
            playbackJob = null
            audioEngine.stopPlayback()

            _currentChunkIndex.value = index

            mutex.withLock { preBufferedChunks.clear() }
            preBufferJob?.cancel()
            val nextIndex = index + 1
            if (nextIndex < chunks.size) {
                startPreBuffering(nextIndex)
            }

            if (_state.value is ReadingPlaybackState.Playing || _state.value is ReadingPlaybackState.Paused) {
                _state.value = ReadingPlaybackState.Playing
                startPlaybackLoop(streamFirstChunk = true)
            }
        }

        /** Skip forward by N chunks. */
        suspend fun skipForward(count: Int = 1) {
            val newIndex = (_currentChunkIndex.value + count).coerceAtMost(chunks.size - 1)
            skipToChunk(newIndex)
        }

        /** Skip backward by N chunks. */
        suspend fun skipBackward(count: Int = 1) {
            val newIndex = (_currentChunkIndex.value - count).coerceAtLeast(0)
            skipToChunk(newIndex)
        }

        // MARK: - Pre-buffering

        private fun startPreBuffering(fromIndex: Int) {
            preBufferJob?.cancel()
            preBufferJob =
                scope.launch {
                    runPreBufferLoop(fromIndex)
                }
        }

        private suspend fun runPreBufferLoop(fromIndex: Int) {
            isPreBuffering = true
            var nextToBuffer = fromIndex

            while (nextToBuffer < chunks.size) {
                val maxBufferIndex = _currentChunkIndex.value + PRE_BUFFER_COUNT
                if (nextToBuffer > maxBufferIndex) {
                    delay(100)
                    continue
                }

                val alreadyBuffered = mutex.withLock { preBufferedChunks.containsKey(nextToBuffer) }
                if (alreadyBuffered) {
                    nextToBuffer++
                    continue
                }

                val chunk = chunks[nextToBuffer]
                Log.d(TAG, "Pre-buffering chunk $nextToBuffer")

                try {
                    val audioChunks = mutableListOf<TTSAudioChunk>()
                    ttsService.synthesize(chunk.text).collect { audioChunk ->
                        audioChunks.add(audioChunk)
                    }

                    val preBuffered = PreBufferedChunk(chunk, audioChunks)
                    mutex.withLock { preBufferedChunks[nextToBuffer] = preBuffered }
                    Log.d(TAG, "Buffered chunk $nextToBuffer with ${audioChunks.size} audio segments")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to buffer chunk $nextToBuffer: ${e.message}", e)
                }

                nextToBuffer++
            }

            isPreBuffering = false
        }

        private suspend fun waitForChunk(index: Int) {
            val startTime = System.currentTimeMillis()
            while (true) {
                val available = mutex.withLock { preBufferedChunks.containsKey(index) }
                if (available) return

                if (System.currentTimeMillis() - startTime > BUFFER_TIMEOUT_MS) {
                    throw ReadingPlaybackError.BufferTimeout()
                }
                delay(BUFFER_POLL_MS)
            }
        }

        // MARK: - Playback Loop

        private fun startPlaybackLoop(
            streamFirstChunk: Boolean = false,
            useCachedAudio: Boolean = false,
        ) {
            playbackJob?.cancel()
            playbackJob =
                scope.launch {
                    runPlaybackLoop(streamFirstChunk, useCachedAudio)
                }
        }

        @Suppress("CyclomaticComplexMethod")
        private suspend fun runPlaybackLoop(
            streamFirstChunk: Boolean = false,
            useCachedAudio: Boolean = false,
        ) {
            var shouldStreamNext = streamFirstChunk
            var shouldUseCached = useCachedAudio

            while (_state.value is ReadingPlaybackState.Playing) {
                val currentIdx = _currentChunkIndex.value
                if (currentIdx >= chunks.size) {
                    _state.value = ReadingPlaybackState.Completed
                    saveCurrentPosition()
                    return
                }

                val result =
                    when {
                        shouldUseCached -> {
                            shouldUseCached = false
                            shouldStreamNext = false
                            playCachedChunk(currentIdx)
                        }
                        shouldStreamNext -> {
                            shouldStreamNext = false
                            playStreamingChunk(currentIdx)
                        }
                        else -> playPreBufferedChunk(currentIdx)
                    }

                when (result) {
                    ChunkPlayResult.SUCCESS -> advanceToNextChunk()
                    ChunkPlayResult.FALLBACK_TO_STREAM -> shouldStreamNext = true
                    ChunkPlayResult.WAIT_FOR_BUFFER -> Unit
                    ChunkPlayResult.ERROR -> return
                }
            }
        }

        private suspend fun playCachedChunk(index: Int): ChunkPlayResult {
            val chunk = chunks[index]
            Log.d(TAG, "Playing cached audio for chunk $index (instant)")
            val audioData = chunk.cachedAudioData ?: return ChunkPlayResult.FALLBACK_TO_STREAM
            val ttsChunk = TTSAudioChunk(audioData = audioData, isFirst = true, isLast = true)
            return try {
                playAudioChunk(ttsChunk)
                ChunkPlayResult.SUCCESS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cached audio playback error: ${e.message}", e)
                ChunkPlayResult.FALLBACK_TO_STREAM
            }
        }

        private suspend fun playStreamingChunk(index: Int): ChunkPlayResult {
            val chunk = chunks[index]
            Log.d(TAG, "Streaming chunk $index directly for low latency")
            return try {
                ttsService.synthesize(chunk.text).collect { audioChunk ->
                    if (_state.value is ReadingPlaybackState.Playing) playAudioChunk(audioChunk)
                }
                ChunkPlayResult.SUCCESS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Streaming playback error at chunk $index: ${e.message}", e)
                _state.value = ReadingPlaybackState.Error(e.message ?: "Playback failed")
                ChunkPlayResult.ERROR
            }
        }

        private suspend fun playPreBufferedChunk(index: Int): ChunkPlayResult {
            val preBuffered = mutex.withLock { preBufferedChunks[index] }
            if (preBuffered == null) {
                return waitAndRetryChunk(index)
            }
            return try {
                for (audioChunk in preBuffered.audioChunks) {
                    if (_state.value !is ReadingPlaybackState.Playing) return ChunkPlayResult.ERROR
                    playAudioChunk(audioChunk)
                }
                ChunkPlayResult.SUCCESS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playback error at chunk $index: ${e.message}", e)
                _state.value = ReadingPlaybackState.Error(e.message ?: "Playback failed")
                ChunkPlayResult.ERROR
            }
        }

        private suspend fun waitAndRetryChunk(index: Int): ChunkPlayResult {
            _state.value = ReadingPlaybackState.Buffering
            return try {
                waitForChunk(index)
                _state.value = ReadingPlaybackState.Playing
                ChunkPlayResult.WAIT_FOR_BUFFER
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ReadingPlaybackState.Error("Buffering failed")
                ChunkPlayResult.ERROR
            }
        }

        private suspend fun advanceToNextChunk() {
            if (_state.value !is ReadingPlaybackState.Playing) return
            val nextIdx = _currentChunkIndex.value + 1
            _currentChunkIndex.value = nextIdx
            val oldIndex = nextIdx - 2
            if (oldIndex >= 0) {
                mutex.withLock { preBufferedChunks.remove(oldIndex) }
            }
            if (!isPreBuffering) {
                val bufferedCount = mutex.withLock { preBufferedChunks.size }
                if (nextIdx + PRE_BUFFER_COUNT > bufferedCount) {
                    startPreBuffering(nextIdx + 1)
                }
            }
        }

        private fun playAudioChunk(chunk: TTSAudioChunk) {
            if (chunk.audioData.isNotEmpty()) {
                val floatArray = bytesToFloatArray(chunk.audioData)
                audioEngine.queuePlayback(floatArray)
            }
        }

        // MARK: - Position Management

        private suspend fun saveCurrentPosition() {
            val itemId = currentItemId ?: return
            val chunkIdx = _currentChunkIndex.value
            try {
                readingListManager.updatePosition(itemId, chunkIdx)
                Log.d(TAG, "Saved position: chunk $chunkIdx")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save position: ${e.message}", e)
            }
        }

        // MARK: - Bookmarks

        /**
         * Add a bookmark at the current position.
         *
         * @param note Optional note for the bookmark
         */
        suspend fun addBookmark(note: String? = null) {
            val itemId = currentItemId ?: throw ReadingPlaybackError.NotConfigured()
            val chunkIdx = _currentChunkIndex.value
            readingListManager.addBookmark(itemId, chunkIdx, note)
            Log.i(TAG, "Added bookmark at chunk $chunkIdx")
        }

        /**
         * Jump to a bookmark position.
         */
        suspend fun jumpToBookmark(bookmark: ReadingBookmarkData) {
            skipToChunk(bookmark.chunkIndex)
        }

        // MARK: - Utilities

        /**
         * Convert raw byte audio data to float array for AudioEngine.
         *
         * Assumes 16-bit PCM little-endian format.
         */
        private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
            val shortCount = bytes.size / 2
            val result = FloatArray(shortCount)
            for (i in 0 until shortCount) {
                val low = bytes[i * 2].toInt() and 0xFF
                val high = bytes[i * 2 + 1].toInt()
                val sample = (high shl 8) or low
                result[i] = sample / 32768.0f
            }
            return result
        }
    }

/**
 * Result of attempting to play a single chunk.
 */
private enum class ChunkPlayResult {
    SUCCESS,
    FALLBACK_TO_STREAM,
    WAIT_FOR_BUFFER,
    ERROR,
}

/**
 * A chunk with its pre-generated audio data.
 */
private data class PreBufferedChunk(
    val chunk: ReadingChunkData,
    val audioChunks: List<TTSAudioChunk>,
)
