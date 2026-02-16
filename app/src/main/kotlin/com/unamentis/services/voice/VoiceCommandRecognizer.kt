package com.unamentis.services.voice

import android.util.Log
import androidx.annotation.StringRes
import com.unamentis.R

/**
 * Unified command vocabulary for all voice interactions.
 *
 * Designed for both activity-mode voice-first (Tier 1) and future
 * app-wide voice navigation (Tier 2).
 *
 * @property displayNameResId String resource ID for human-readable display name
 */
enum class VoiceCommand(
    @get:StringRes val displayNameResId: Int,
) {
    /** Proceed/confirm. */
    READY(R.string.voice_command_ready),

    /** Submit current input. */
    SUBMIT(R.string.voice_command_submit),

    /** Advance forward. */
    NEXT(R.string.voice_command_next),

    /** Skip current item. */
    SKIP(R.string.voice_command_skip),

    /** Repeat last audio. */
    REPEAT_LAST(R.string.voice_command_repeat),

    /** Exit/cancel. */
    QUIT(R.string.voice_command_quit),
}

/**
 * How a voice command was matched against the transcript.
 */
enum class MatchType {
    /** Direct string match. */
    EXACT,

    /** Phonetic similarity via Double Metaphone. */
    PHONETIC,

    /** Token similarity via Jaccard coefficient. */
    TOKEN_SIMILARITY,
}

/**
 * Result of voice command recognition attempt.
 *
 * @property command The recognized command
 * @property confidence Confidence score (0.0 - 1.0)
 * @property matchedPhrase The phrase from the library that was matched
 * @property matchType How the match was determined
 */
data class VoiceCommandResult(
    val command: VoiceCommand,
    val confidence: Float,
    val matchedPhrase: String,
    val matchType: MatchType,
) {
    /**
     * Whether confidence meets minimum threshold for execution.
     */
    val shouldExecute: Boolean
        get() = confidence >= EXECUTION_THRESHOLD

    companion object {
        /** Minimum confidence to execute a recognized command. */
        const val EXECUTION_THRESHOLD = 0.75f
    }
}

/**
 * Recognizes voice commands using local matching algorithms.
 *
 * Uses a tiered matching strategy:
 * 1. **Exact match** (confidence 1.0) -- direct string containment
 * 2. **Phonetic match** via Double Metaphone (confidence 0.9)
 * 3. **Token similarity** via Jaccard coefficient (confidence 0.75 - 0.89)
 *
 * Designed for reuse across all voice-centric activities and future
 * app-wide voice navigation. All matching is done locally with zero
 * network latency.
 *
 * Thread Safety: This class is thread-safe. All state is immutable
 * after construction, and recognition is a pure function.
 */
class VoiceCommandRecognizer {
    companion object {
        private const val TAG = "VoiceCommandRecognizer"

        /** Minimum Jaccard similarity to consider a token match. */
        internal const val JACCARD_THRESHOLD = 0.7f

        /** Maximum confidence for token matches (capped below phonetic). */
        internal const val TOKEN_MAX_CONFIDENCE = 0.89f

        /** Confidence for phonetic matches. */
        internal const val PHONETIC_CONFIDENCE = 0.9f
    }

    // Embedded matchers
    private val phoneticMatcher = PhoneticMatcher()
    private val tokenMatcher = TokenMatcher()

    // -- Command Phrase Library --

    /**
     * All recognized phrase variations for each command.
     *
     * Each command has 7-10+ variations covering common speech patterns,
     * contractions, and informal phrasing.
     */
    private val commandPhrases: Map<VoiceCommand, List<String>> =
        mapOf(
            VoiceCommand.READY to
                listOf(
                    "ready",
                    "i'm ready",
                    "im ready",
                    "let's go",
                    "lets go",
                    "go ahead",
                    "start",
                    "begin",
                    "answer now",
                    "yes",
                ),
            VoiceCommand.SUBMIT to
                listOf(
                    "submit",
                    "that's my answer",
                    "thats my answer",
                    "done",
                    "final answer",
                    "finished",
                    "i'm done",
                    "im done",
                ),
            VoiceCommand.NEXT to
                listOf(
                    "next",
                    "continue",
                    "next question",
                    "move on",
                    "okay",
                    "ok",
                ),
            VoiceCommand.SKIP to
                listOf(
                    "skip",
                    "pass",
                    "i don't know",
                    "i dont know",
                    "dont know",
                    "no idea",
                    "skip this",
                ),
            VoiceCommand.REPEAT_LAST to
                listOf(
                    "repeat",
                    "say again",
                    "what was that",
                    "repeat question",
                    "say that again",
                    "pardon",
                    "again",
                ),
            VoiceCommand.QUIT to
                listOf(
                    "quit",
                    "stop",
                    "end",
                    "exit",
                    "go back",
                    "cancel",
                    "end session",
                ),
        )

    // Pre-computed phonetic codes for faster matching
    private val phoneticCodes: Map<VoiceCommand, List<PhoneticEntry>>

    init {
        // Precompute phonetic codes for all phrases
        val codes = mutableMapOf<VoiceCommand, List<PhoneticEntry>>()
        for ((command, phrases) in commandPhrases) {
            codes[command] =
                phrases.map { phrase ->
                    val result = phoneticMatcher.metaphone(phrase)
                    PhoneticEntry(phrase, result.primary, result.secondary)
                }
        }
        phoneticCodes = codes
    }

    // -- Public API --

    /**
     * Attempt to recognize a voice command from transcript.
     *
     * Applies tiered matching: exact -> phonetic -> token similarity.
     * Returns the best match across all valid commands, or null if no
     * command was recognized.
     *
     * @param transcript The STT transcript to analyze
     * @param validCommands Optional filter for commands valid in current context.
     *                      If null, all commands are checked.
     * @return Recognition result if a command was found, null otherwise
     */
    fun recognize(
        transcript: String,
        validCommands: Set<VoiceCommand>? = null,
    ): VoiceCommandResult? {
        val normalized = normalize(transcript)

        if (normalized.isEmpty()) {
            return null
        }

        val commandsToCheck = validCommands ?: VoiceCommand.entries.toSet()
        var bestResult: VoiceCommandResult? = null

        for (command in commandsToCheck) {
            val result = matchCommand(command, normalized)
            if (result != null) {
                if (bestResult == null || result.confidence > bestResult.confidence) {
                    bestResult = result
                }
            }
        }

        if (bestResult != null) {
            Log.d(
                TAG,
                "Recognized command: ${bestResult.command.name} " +
                    "(confidence: ${bestResult.confidence}, type: ${bestResult.matchType})",
            )
        }

        return bestResult
    }

    /**
     * Check if transcript contains a specific command with sufficient confidence.
     *
     * @param command The command to check for
     * @param transcript The STT transcript
     * @return True if command found with confidence >= [VoiceCommandResult.EXECUTION_THRESHOLD]
     */
    fun contains(
        command: VoiceCommand,
        transcript: String,
    ): Boolean {
        val result = matchCommand(command, normalize(transcript))
        return result?.shouldExecute == true
    }

    /**
     * Get all recognized phrase variations for a specific command.
     *
     * Useful for displaying help text showing what the user can say.
     *
     * @param command The command to get phrases for
     * @return List of recognized phrases
     */
    fun getPhrasesForCommand(command: VoiceCommand): List<String> {
        return commandPhrases[command] ?: emptyList()
    }

    // -- Private Matching --

    /**
     * Attempt to match a single command against normalized input.
     *
     * Tier 1: Exact match (confidence 1.0)
     * Tier 2: Phonetic match (confidence 0.9)
     * Tier 3: Token similarity (confidence 0.75-0.89)
     */
    internal fun matchCommand(
        command: VoiceCommand,
        input: String,
    ): VoiceCommandResult? {
        val phrases = commandPhrases[command] ?: return null

        // Tier 1: Exact match
        val exactMatch = findExactMatch(command, phrases, input)
        if (exactMatch != null) return exactMatch

        // Tier 2: Phonetic match
        val phoneticResult = findPhoneticMatch(command, input)
        if (phoneticResult != null) return phoneticResult

        // Tier 3: Token similarity
        return findTokenMatch(command, phrases, input)
    }

    private fun findExactMatch(
        command: VoiceCommand,
        phrases: List<String>,
        input: String,
    ): VoiceCommandResult? {
        for (phrase in phrases) {
            if (input == phrase || input.contains(phrase)) {
                return VoiceCommandResult(
                    command = command,
                    confidence = 1.0f,
                    matchedPhrase = phrase,
                    matchType = MatchType.EXACT,
                )
            }
        }
        return null
    }

    private fun findPhoneticMatch(
        command: VoiceCommand,
        input: String,
    ): VoiceCommandResult? {
        val codes = phoneticCodes[command] ?: return null
        val inputCodes = phoneticMatcher.metaphone(input)

        for (entry in codes) {
            if (phoneticMatch(inputCodes, PhoneticCodes(entry.primary, entry.secondary))) {
                return VoiceCommandResult(
                    command = command,
                    confidence = PHONETIC_CONFIDENCE,
                    matchedPhrase = entry.phrase,
                    matchType = MatchType.PHONETIC,
                )
            }
        }
        return null
    }

    private fun findTokenMatch(
        command: VoiceCommand,
        phrases: List<String>,
        input: String,
    ): VoiceCommandResult? {
        for (phrase in phrases) {
            val similarity = tokenMatcher.jaccardSimilarity(input, phrase)
            if (similarity >= JACCARD_THRESHOLD) {
                val confidence =
                    (0.75f + (similarity - JACCARD_THRESHOLD) * 0.5f)
                        .coerceAtMost(TOKEN_MAX_CONFIDENCE)
                return VoiceCommandResult(
                    command = command,
                    confidence = confidence,
                    matchedPhrase = phrase,
                    matchType = MatchType.TOKEN_SIMILARITY,
                )
            }
        }
        return null
    }

    /**
     * Check if two phonetic code pairs match.
     *
     * Matches if:
     * - Primary codes match
     * - Either secondary matches the other's primary
     * - Both secondaries match
     */
    private fun phoneticMatch(
        input: PhoneticCodes,
        target: PhoneticCodes,
    ): Boolean {
        if (input.primary.isEmpty() || target.primary.isEmpty()) return false

        // Primary codes match
        if (input.primary == target.primary) return true

        // Input secondary matches target primary
        if (input.secondary != null && input.secondary == target.primary) return true

        // Target secondary matches input primary
        if (target.secondary != null && input.primary == target.secondary) return true

        // Both secondaries match
        if (input.secondary != null && target.secondary != null &&
            input.secondary == target.secondary
        ) {
            return true
        }

        return false
    }

    /**
     * Normalize transcript text for matching.
     *
     * Converts to lowercase, trims whitespace, normalizes apostrophes.
     */
    internal fun normalize(text: String): String {
        return text.lowercase()
            .trim()
            .replace("\u2019", "'") // Curly apostrophe -> straight
    }
}

// -- Internal Data Classes --

/**
 * Pre-computed phonetic codes for a command phrase.
 */
private data class PhoneticEntry(
    val phrase: String,
    val primary: String,
    val secondary: String?,
)

/**
 * Pair of phonetic codes (primary and optional secondary).
 */
internal data class PhoneticCodes(
    val primary: String,
    val secondary: String?,
)

// -- Embedded Phonetic Matcher (Double Metaphone) --

/**
 * Double Metaphone phonetic encoding for command matching.
 *
 * Generates primary and secondary phonetic codes for text strings.
 * This is an embedded implementation for independence from external
 * libraries, adapted from the standard Double Metaphone algorithm.
 *
 * For command matching, each word is encoded separately and codes
 * are concatenated, then truncated to 8 characters maximum.
 */
internal class PhoneticMatcher {
    /**
     * Generate Double Metaphone codes for a text string.
     *
     * @param text Text to encode
     * @return Pair of primary and optional secondary phonetic code
     */
    fun metaphone(text: String): PhoneticCodes {
        val cleaned = text.uppercase().filter { it.isLetter() || it == ' ' }
        if (cleaned.isEmpty()) return PhoneticCodes("", null)

        val words = cleaned.split(" ").filter { it.isNotEmpty() }
        val primaryCodes = mutableListOf<String>()
        val secondaryCodes = mutableListOf<String>()

        for (word in words) {
            val (primary, secondary) = encodeWord(word)
            primaryCodes.add(primary)
            if (secondary != null) {
                secondaryCodes.add(secondary)
            }
        }

        val primary = primaryCodes.joinToString("").take(MAX_CODE_LENGTH)
        val secondary =
            if (secondaryCodes.isEmpty()) {
                null
            } else {
                secondaryCodes.joinToString("").take(MAX_CODE_LENGTH)
            }

        return PhoneticCodes(primary, secondary)
    }

    private fun encodeWord(word: String): Pair<String, String?> {
        val ctx = MetaphoneContext(word)
        processInitialExceptions(ctx)
        processMainLoop(ctx)
        return ctx.result()
    }

    private fun processInitialExceptions(ctx: MetaphoneContext) {
        if (ctx.stringAt(0, 2, "GN", "KN", "PN", "WR", "PS")) {
            ctx.advanceIndex()
        }
        if (ctx.charAt(0) == 'X') {
            ctx.appendBoth("S")
            ctx.advanceIndex()
        }
    }

    private fun processMainLoop(ctx: MetaphoneContext) {
        while (ctx.hasMore()) {
            processCharacter(ctx.currentChar(), ctx)
            ctx.advanceIndex()
        }
    }

    @Suppress("CyclomaticComplexity", "CyclomaticComplexMethod")
    private fun processCharacter(
        ch: Char,
        ctx: MetaphoneContext,
    ) {
        when (ch) {
            'A', 'E', 'I', 'O', 'U', 'Y' -> {
                if (ctx.currentPosition() == 0) {
                    ctx.appendBoth("A")
                }
            }
            'B' -> ctx.appendBoth("P")
            'C' -> processC(ctx)
            'D' -> processD(ctx)
            'F' -> ctx.appendBoth("F")
            'G' -> processG(ctx)
            'H' -> processH(ctx)
            'J' -> ctx.appendBoth("J")
            'K', 'Q' -> ctx.appendBoth("K")
            'L' -> ctx.appendBoth("L")
            'M' -> ctx.appendBoth("M")
            'N' -> ctx.appendBoth("N")
            'P' -> processP(ctx)
            'R' -> ctx.appendBoth("R")
            'S' -> processS(ctx)
            'T' -> processT(ctx)
            'V' -> ctx.appendBoth("F")
            'W' -> processW(ctx)
            'X' -> ctx.appendBoth("KS")
            'Z' -> ctx.appendBoth("S")
            else -> { /* Skip unrecognized characters */ }
        }
    }

    private fun processC(ctx: MetaphoneContext) {
        val pos = ctx.currentPosition()
        when {
            ctx.stringAt(pos, 2, "CH") -> {
                ctx.appendBoth("X")
                ctx.advanceIndex()
            }
            ctx.stringAt(pos, 2, "CE", "CI", "CY") -> ctx.appendBoth("S")
            else -> ctx.appendBoth("K")
        }
    }

    private fun processD(ctx: MetaphoneContext) {
        if (ctx.stringAt(ctx.currentPosition(), 2, "DG")) {
            ctx.appendBoth("J")
            ctx.advanceIndex()
        } else {
            ctx.appendBoth("T")
        }
    }

    private fun processG(ctx: MetaphoneContext) {
        val next = ctx.charAtOffset(1)
        when {
            next == 'H' -> ctx.appendBoth("K")
            next == 'N' -> { /* Silent GN */ }
            next == 'E' || next == 'I' || next == 'Y' -> {
                ctx.appendPrimary("J")
                ctx.appendSecondary("K")
            }
            else -> ctx.appendBoth("K")
        }
    }

    private fun processH(ctx: MetaphoneContext) {
        val nextChar = ctx.charAtOffset(1)
        val prevChar = ctx.charAtOffset(-1)
        val nextIsVowel = nextChar != null && isVowel(nextChar)
        val prevIsConsonantOrAbsent = ctx.currentPosition() == 0 || prevChar == null || !isVowel(prevChar)
        if (nextIsVowel && prevIsConsonantOrAbsent) {
            ctx.appendBoth("H")
        }
    }

    private fun processP(ctx: MetaphoneContext) {
        if (ctx.charAtOffset(1) == 'H') {
            ctx.appendBoth("F")
            ctx.advanceIndex()
        } else {
            ctx.appendBoth("P")
        }
    }

    private fun processS(ctx: MetaphoneContext) {
        if (ctx.stringAt(ctx.currentPosition(), 2, "SH")) {
            ctx.appendBoth("X")
            ctx.advanceIndex()
        } else {
            ctx.appendBoth("S")
        }
    }

    private fun processT(ctx: MetaphoneContext) {
        if (ctx.stringAt(ctx.currentPosition(), 2, "TH")) {
            ctx.appendPrimary("0")
            ctx.appendSecondary("T")
            ctx.advanceIndex()
        } else {
            ctx.appendBoth("T")
        }
    }

    private fun processW(ctx: MetaphoneContext) {
        val next = ctx.charAtOffset(1)
        if (next != null && isVowel(next)) {
            ctx.appendBoth("A")
        }
    }

    private fun isVowel(ch: Char): Boolean = ch in "AEIOUY"

    companion object {
        /** Maximum length for phonetic codes. */
        private const val MAX_CODE_LENGTH = 8
    }
}

// -- Metaphone Context --

/**
 * Mutable context for tracking state during Double Metaphone encoding.
 *
 * @property text The word being encoded (uppercase)
 */
private class MetaphoneContext(private val text: String) {
    private var index = 0
    private val primary = StringBuilder()
    private val secondary = StringBuilder()

    fun advanceIndex(by: Int = 1) {
        index = (index + by).coerceAtMost(text.length)
    }

    fun hasMore(): Boolean = index < text.length

    fun currentChar(): Char = text[index]

    fun currentPosition(): Int = index

    fun charAt(position: Int): Char? {
        return if (position in text.indices) text[position] else null
    }

    fun charAtOffset(offset: Int): Char? {
        val target = index + offset
        return if (target in text.indices) text[target] else null
    }

    fun stringAt(
        start: Int,
        length: Int,
        vararg matches: String,
    ): Boolean {
        if (start < 0 || start + length > text.length) return false
        val substr = text.substring(start, start + length)
        return matches.any { it == substr }
    }

    fun appendPrimary(code: String) {
        primary.append(code)
    }

    fun appendSecondary(code: String) {
        secondary.append(code)
    }

    fun appendBoth(code: String) {
        primary.append(code)
        secondary.append(code)
    }

    fun result(): Pair<String, String?> {
        val primaryCode = primary.toString().take(MAX_WORD_CODE_LENGTH)
        val secondaryCode = secondary.toString().take(MAX_WORD_CODE_LENGTH)
        val sec = if (secondaryCode.isEmpty() || secondaryCode == primaryCode) null else secondaryCode
        return primaryCode to sec
    }

    companion object {
        /** Maximum phonetic code length per word. */
        private const val MAX_WORD_CODE_LENGTH = 4
    }
}

// -- Embedded Token Matcher --

/**
 * Token-based similarity for command matching.
 *
 * Uses Jaccard similarity (intersection / union of word token sets)
 * as a fuzzy matching fallback when exact and phonetic matching fail.
 */
internal class TokenMatcher {
    /**
     * Compute Jaccard similarity coefficient between two strings.
     *
     * Tokenizes both strings into word sets (with minimal stop-word
     * removal) and computes intersection / union.
     *
     * @param str1 First string
     * @param str2 Second string
     * @return Similarity score from 0.0 (no overlap) to 1.0 (identical sets)
     */
    fun jaccardSimilarity(
        str1: String,
        str2: String,
    ): Float {
        val tokens1 = tokenize(str1)
        val tokens2 = tokenize(str2)

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0f
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return if (union == 0) 0.0f else intersection.toFloat() / union.toFloat()
    }

    /**
     * Tokenize a string into a set of normalized words.
     *
     * Removes minimal stop words (the, a, an) to focus on
     * content-bearing tokens for command matching.
     */
    internal fun tokenize(text: String): Set<String> {
        val normalized = text.lowercase()
        val tokens =
            normalized.split(Regex("[\\s]+"))
                .flatMap { word -> word.split(Regex("[^a-z0-9']")).filter { it.isNotEmpty() } }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        return tokens.filterNot { it in STOP_WORDS }.toSet()
    }

    companion object {
        /** Minimal stop words for command matching (keep most words). */
        private val STOP_WORDS = setOf("the", "a", "an")
    }
}
