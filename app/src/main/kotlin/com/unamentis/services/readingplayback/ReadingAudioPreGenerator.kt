package com.unamentis.services.readingplayback

import android.util.Log
import com.unamentis.core.readinglist.ReadingListManager
import com.unamentis.data.model.AudioPreGenStatus
import com.unamentis.data.model.TTSAudioChunk
import com.unamentis.data.model.TTSService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-generates TTS audio for the first chunk of reading list items.
 *
 * Triggered after document import, runs in the background. The playback path
 * checks for cached audio and coordinates with in-progress generation to avoid
 * duplicate work.
 *
 * Maps to iOS ReadingAudioPreGenerator actor.
 */
@Singleton
class ReadingAudioPreGenerator
    @Inject
    constructor(
        private val ttsService: TTSService,
        private val readingListManager: ReadingListManager,
        private val scope: CoroutineScope,
    ) {
        companion object {
            private const val TAG = "ReadingAudioPreGen"
        }

        /** In-progress generation tasks keyed by item ID. */
        private val inProgressTasks = mutableMapOf<String, CompletableDeferred<ByteArray?>>()
        private val mutex = Mutex()

        /**
         * Pre-generate TTS audio for the first chunk of a reading item.
         *
         * Runs in the background and stores the result on the chunk entity.
         *
         * @param itemId The reading item's ID
         * @param chunkText The text of chunk 0 to synthesize
         */
        fun preGenerateFirstChunk(
            itemId: String,
            chunkText: String,
        ) {
            scope.launch {
                mutex.withLock {
                    if (inProgressTasks.containsKey(itemId)) {
                        Log.d(TAG, "Pre-generation already in progress for $itemId")
                        return@launch
                    }
                }

                Log.i(TAG, "Starting pre-generation for item $itemId")

                val deferred = CompletableDeferred<ByteArray?>()
                mutex.withLock { inProgressTasks[itemId] = deferred }

                try {
                    val audioData = synthesizeChunk(chunkText)

                    if (audioData != null) {
                        readingListManager.updateAudioPreGenStatus(itemId, AudioPreGenStatus.READY)
                        Log.i(TAG, "Pre-generation complete for $itemId, ${audioData.size} bytes")
                    } else {
                        readingListManager.updateAudioPreGenStatus(itemId, AudioPreGenStatus.FAILED)
                        Log.w(TAG, "Pre-generation failed for $itemId")
                    }

                    deferred.complete(audioData)
                } catch (e: Exception) {
                    readingListManager.updateAudioPreGenStatus(itemId, AudioPreGenStatus.FAILED)
                    Log.e(TAG, "Pre-generation error for $itemId", e)
                    deferred.complete(null)
                } finally {
                    mutex.withLock { inProgressTasks.remove(itemId) }
                }
            }
        }

        /**
         * Wait for an in-progress pre-generation to complete.
         *
         * @return The audio data if generation succeeds, null otherwise.
         */
        suspend fun waitForPreGeneration(itemId: String): ByteArray? {
            val deferred = mutex.withLock { inProgressTasks[itemId] } ?: return null
            return deferred.await()
        }

        /** Check if pre-generation is currently in progress for an item. */
        suspend fun isGenerating(itemId: String): Boolean {
            return mutex.withLock { inProgressTasks.containsKey(itemId) }
        }

        /**
         * Synthesize audio for a chunk of text using the TTS service.
         */
        private suspend fun synthesizeChunk(text: String): ByteArray? {
            return try {
                val outputStream = ByteArrayOutputStream()
                ttsService.synthesize(text).collect { chunk: TTSAudioChunk ->
                    if (chunk.audioData.isNotEmpty()) {
                        outputStream.write(chunk.audioData)
                    }
                }

                val result = outputStream.toByteArray()
                if (result.isEmpty()) {
                    Log.w(TAG, "TTS produced empty audio")
                    null
                } else {
                    Log.d(TAG, "Synthesized ${result.size} bytes")
                    result
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS synthesis failed: ${e.message}", e)
                null
            }
        }
    }
