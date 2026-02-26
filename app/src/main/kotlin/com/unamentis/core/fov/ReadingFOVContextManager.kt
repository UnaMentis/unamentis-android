package com.unamentis.core.fov

import android.util.Log
import com.unamentis.data.model.LLMMessage
import com.unamentis.services.readingplayback.ReadingChunkData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Context window around the current reading position.
 *
 * Used to provide surrounding text context to the LLM when the user
 * pauses reading to ask a barge-in question.
 */
data class ReadingContextWindow(
    val systemPrompt: String,
    val precedingText: String,
    val currentText: String,
    val followingText: String,
    val documentTitle: String,
    val documentAuthor: String? = null,
    val currentChunkIndex: Int,
    val totalChunks: Int,
) {
    /** Combined context formatted for LLM consumption. */
    val fullContext: String
        get() {
            val parts = mutableListOf<String>()

            parts.add(systemPrompt)
            parts.add("")
            parts.add("## Document: $documentTitle")

            if (!documentAuthor.isNullOrEmpty()) {
                parts.add("Author: $documentAuthor")
            }

            parts.add("Progress: Segment ${currentChunkIndex + 1} of $totalChunks")
            parts.add("")

            if (precedingText.isNotEmpty()) {
                parts.add("## Previously Read")
                parts.add(precedingText)
                parts.add("")
            }

            parts.add("## Currently Reading")
            parts.add(currentText)
            parts.add("")

            if (followingText.isNotEmpty()) {
                parts.add("## Coming Up Next")
                parts.add(followingText)
            }

            return parts.joinToString("\n")
        }

    /** Estimated token count (rough approximation: ~4 chars per token). */
    val estimatedTokenCount: Int
        get() = fullContext.length / 4
}

/**
 * Builds context windows around the current reading position
 * for barge-in Q&A during document playback.
 *
 * When the user interrupts reading to ask a question, this manager
 * provides the relevant surrounding text as context to the LLM.
 *
 * Maps to iOS ReadingFOVContextManager actor.
 *
 * @param precedingChunkCount Chunks to include before current (default: 3)
 * @param followingChunkCount Chunks to include after current (default: 2)
 * @param maxSectionCharacters Max chars per section (default: 4000)
 */
@Singleton
class ReadingFOVContextManager
    @Inject
    constructor() {
        companion object {
            private const val TAG = "ReadingFOVCtx"
            private const val DEFAULT_PRECEDING_CHUNKS = 3
            private const val DEFAULT_FOLLOWING_CHUNKS = 2
            private const val DEFAULT_MAX_SECTION_CHARS = 4000

            private const val SYSTEM_PROMPT = """You are a helpful reading assistant. The user is \
listening to a document being read aloud and has paused to ask you a question.

Answer the question based on the document content provided below. \
Be concise and direct. If the answer is in the document, cite the \
relevant passage. If the question is about something not in the \
document, say so clearly.

Keep responses brief since the user will resume listening after your answer."""
        }

        var precedingChunkCount: Int = DEFAULT_PRECEDING_CHUNKS
        var followingChunkCount: Int = DEFAULT_FOLLOWING_CHUNKS
        var maxSectionCharacters: Int = DEFAULT_MAX_SECTION_CHARS

        /**
         * Build a context window around the current reading position.
         *
         * @param chunks All chunks for the document
         * @param currentIndex Current chunk index being read
         * @param title Document title
         * @param author Document author (optional)
         * @return Context window ready for LLM
         */
        fun buildContext(
            chunks: List<ReadingChunkData>,
            currentIndex: Int,
            title: String,
            author: String? = null,
        ): ReadingContextWindow {
            val currentText =
                if (currentIndex < chunks.size) {
                    chunks[currentIndex].text
                } else {
                    ""
                }

            // Get preceding chunks
            val precedingStart = (currentIndex - precedingChunkCount).coerceAtLeast(0)
            val precedingChunks = chunks.subList(precedingStart, currentIndex)
            val precedingText =
                truncateToLimit(
                    precedingChunks.joinToString("\n\n") { it.text },
                )

            // Get following chunks
            val followingEnd = (currentIndex + 1 + followingChunkCount).coerceAtMost(chunks.size)
            val followingChunks =
                if (currentIndex + 1 < chunks.size) {
                    chunks.subList(currentIndex + 1, followingEnd)
                } else {
                    emptyList()
                }
            val followingText =
                truncateToLimit(
                    followingChunks.joinToString("\n\n") { it.text },
                )

            val window =
                ReadingContextWindow(
                    systemPrompt = SYSTEM_PROMPT,
                    precedingText = precedingText,
                    currentText = currentText,
                    followingText = followingText,
                    documentTitle = title,
                    documentAuthor = author,
                    currentChunkIndex = currentIndex,
                    totalChunks = chunks.size,
                )

            Log.d(
                TAG,
                "Built reading context: chunk=$currentIndex, " +
                    "preceding=${precedingText.length}ch, " +
                    "current=${currentText.length}ch, " +
                    "following=${followingText.length}ch, " +
                    "tokens~${window.estimatedTokenCount}",
            )

            return window
        }

        /**
         * Build LLM messages for a barge-in question during reading.
         *
         * @param question The user's question
         * @param chunks All chunks for the document
         * @param currentIndex Current chunk index
         * @param title Document title
         * @param author Document author
         * @param conversationHistory Previous Q&A exchanges during this reading session
         * @return List of LLM messages ready for the model
         */
        @Suppress("LongParameterList")
        fun buildBargeInMessages(
            question: String,
            chunks: List<ReadingChunkData>,
            currentIndex: Int,
            title: String,
            author: String? = null,
            conversationHistory: List<Pair<String, String>> = emptyList(),
        ): List<LLMMessage> {
            val context = buildContext(chunks, currentIndex, title, author)
            val messages = mutableListOf<LLMMessage>()

            // System message with reading context
            messages.add(LLMMessage(role = "system", content = context.fullContext))

            // Add conversation history from this reading session
            for ((q, a) in conversationHistory) {
                messages.add(LLMMessage(role = "user", content = q))
                messages.add(LLMMessage(role = "assistant", content = a))
            }

            // Add current question
            messages.add(LLMMessage(role = "user", content = question))

            return messages
        }

        /**
         * Truncate text to the maximum section character limit.
         * Keeps the suffix (most recent text) when truncating.
         */
        private fun truncateToLimit(text: String): String {
            if (text.length <= maxSectionCharacters) return text
            return text.takeLast(maxSectionCharacters)
        }
    }
