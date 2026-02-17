package com.unamentis.core.readinglist

import android.util.Log
import java.text.BreakIterator
import java.util.Locale

/**
 * Splits text into TTS-optimized chunks at sentence boundaries.
 *
 * Each chunk is sized for natural TTS playback (target ~40 words).
 * Uses ICU BreakIterator for sentence detection (Android's equivalent
 * of iOS NSLinguisticTagger).
 *
 * Maps to iOS ReadingTextChunker.
 */
object ReadingTextChunker {
    private const val TAG = "ReadingTextChunker"

    /** Default chunking configuration. */
    private const val DEFAULT_TARGET_WORDS = 40
    private const val DEFAULT_MAX_WORDS = 60
    private const val DEFAULT_MIN_WORDS = 15

    /** Words per second for duration estimation (150 WPM = 2.5 WPS). */
    private const val WORDS_PER_SECOND = 2.5f

    /**
     * Chunking configuration.
     */
    data class ChunkConfig(
        val targetWords: Int = DEFAULT_TARGET_WORDS,
        val maxWords: Int = DEFAULT_MAX_WORDS,
        val minWords: Int = DEFAULT_MIN_WORDS,
    )

    /**
     * Result of text chunking.
     */
    data class ChunkResult(
        val index: Int,
        val text: String,
        val characterOffset: Long,
        val estimatedDurationSeconds: Float,
    )

    /**
     * Split text into TTS-optimized chunks.
     *
     * @param text Text to chunk
     * @param config Chunking configuration
     * @return List of chunks with metadata
     */
    fun chunk(
        text: String,
        config: ChunkConfig = ChunkConfig(),
    ): List<ChunkResult> {
        val cleanedText = cleanText(text)

        if (cleanedText.isBlank()) {
            return emptyList()
        }

        val sentences = detectSentences(cleanedText)

        if (sentences.isEmpty()) {
            return listOf(
                ChunkResult(
                    index = 0,
                    text = cleanedText,
                    characterOffset = 0,
                    estimatedDurationSeconds = estimateDuration(cleanedText),
                ),
            )
        }

        return groupSentencesIntoChunks(sentences, cleanedText, config)
    }

    /**
     * Extract text from a source and chunk it.
     *
     * @param rawText Raw text content
     * @param sourceType Source type for preprocessing
     * @param config Chunking configuration
     * @return List of chunks
     */
    fun extractAndChunk(
        rawText: String,
        sourceType: String,
        config: ChunkConfig = ChunkConfig(),
    ): List<ChunkResult> {
        val processedText =
            when (sourceType.lowercase()) {
                "markdown", "md" -> MarkdownStripper.strip(rawText)
                "html", "web_article" -> {
                    val extracted = HTMLArticleExtractor.extract(rawText)
                    extracted?.text ?: rawText
                }
                else -> rawText
            }

        return chunk(processedText, config)
    }

    /**
     * Clean text before chunking.
     */
    private fun cleanText(text: String): String {
        return text
            // Normalize line endings
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Collapse 3+ newlines to 2
            .replace(Regex("\\n{3,}"), "\n\n")
            // Collapse multiple spaces
            .replace(Regex(" {2,}"), " ")
            .trim()
    }

    /**
     * Detect sentence boundaries using ICU BreakIterator.
     *
     * Returns list of (startOffset, endOffset) pairs.
     */
    private fun detectSentences(text: String): List<Pair<Int, Int>> {
        val sentences = mutableListOf<Pair<Int, Int>>()

        try {
            val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
            iterator.setText(text)

            var start = iterator.first()
            var end = iterator.next()

            while (end != BreakIterator.DONE) {
                val sentence = text.substring(start, end).trim()
                if (sentence.isNotBlank()) {
                    sentences.add(start to end)
                }
                start = end
                end = iterator.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sentence detection failed, using regex fallback", e)
            return detectSentencesFallback(text)
        }

        if (sentences.isEmpty()) {
            return detectSentencesFallback(text)
        }

        return sentences
    }

    /**
     * Regex fallback for sentence detection.
     */
    private fun detectSentencesFallback(text: String): List<Pair<Int, Int>> {
        val sentences = mutableListOf<Pair<Int, Int>>()
        val pattern = Regex("[.!?]+\\s+|\\n\\n+")
        var lastEnd = 0

        pattern.findAll(text).forEach { match ->
            val sentenceEnd = match.range.last + 1
            if (lastEnd < sentenceEnd) {
                sentences.add(lastEnd to sentenceEnd)
            }
            lastEnd = sentenceEnd
        }

        // Add remaining text
        if (lastEnd < text.length) {
            sentences.add(lastEnd to text.length)
        }

        return sentences
    }

    /**
     * Group sentences into chunks based on word count targets.
     */
    private fun groupSentencesIntoChunks(
        sentences: List<Pair<Int, Int>>,
        text: String,
        config: ChunkConfig,
    ): List<ChunkResult> {
        val chunks = mutableListOf<ChunkResult>()
        var currentSentences = mutableListOf<Pair<Int, Int>>()
        var currentWordCount = 0
        var chunkIndex = 0

        for (sentence in sentences) {
            val sentenceText = text.substring(sentence.first, sentence.second).trim()
            val wordCount = countWords(sentenceText)

            if (currentWordCount + wordCount > config.maxWords && currentSentences.isNotEmpty()) {
                // Emit current chunk
                val chunk = buildChunk(currentSentences, text, chunkIndex)
                chunks.add(chunk)
                chunkIndex++
                currentSentences = mutableListOf()
                currentWordCount = 0
            }

            currentSentences.add(sentence)
            currentWordCount += wordCount

            if (currentWordCount >= config.targetWords) {
                val chunk = buildChunk(currentSentences, text, chunkIndex)
                chunks.add(chunk)
                chunkIndex++
                currentSentences = mutableListOf()
                currentWordCount = 0
            }
        }

        // Handle remaining sentences
        if (currentSentences.isNotEmpty()) {
            if (chunks.isNotEmpty() && currentWordCount < config.minWords) {
                // Merge with previous chunk if too small
                val lastChunk = chunks.removeLast()
                val mergedSentences = mutableListOf<Pair<Int, Int>>()
                // Reconstruct previous sentences by using the offset range
                mergedSentences.addAll(currentSentences)

                val mergedText = lastChunk.text + " " + buildChunkText(currentSentences, text)
                chunks.add(
                    ChunkResult(
                        index = lastChunk.index,
                        text = mergedText.trim(),
                        characterOffset = lastChunk.characterOffset,
                        estimatedDurationSeconds = estimateDuration(mergedText),
                    ),
                )
            } else {
                chunks.add(buildChunk(currentSentences, text, chunkIndex))
            }
        }

        Log.d(TAG, "Chunked ${text.length} chars into ${chunks.size} chunks")
        return chunks
    }

    private fun buildChunk(
        sentences: List<Pair<Int, Int>>,
        text: String,
        index: Int,
    ): ChunkResult {
        val chunkText = buildChunkText(sentences, text)
        val offset = sentences.firstOrNull()?.first?.toLong() ?: 0L

        return ChunkResult(
            index = index,
            text = chunkText,
            characterOffset = offset,
            estimatedDurationSeconds = estimateDuration(chunkText),
        )
    }

    private fun buildChunkText(
        sentences: List<Pair<Int, Int>>,
        text: String,
    ): String {
        return sentences.joinToString(" ") { (start, end) ->
            text.substring(start, end).trim()
        }
    }

    private fun countWords(text: String): Int {
        return text.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    private fun estimateDuration(text: String): Float {
        val wordCount = countWords(text)
        return wordCount / WORDS_PER_SECOND
    }
}
