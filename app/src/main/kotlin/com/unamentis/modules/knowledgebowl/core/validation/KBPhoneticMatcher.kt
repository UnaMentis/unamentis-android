package com.unamentis.modules.knowledgebowl.core.validation

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phonetic matching using Double Metaphone algorithm for Knowledge Bowl.
 *
 * Catches pronunciation-based errors from STT transcription by encoding words
 * into phonetic codes and comparing those codes instead of literal strings.
 * The Double Metaphone algorithm produces primary and optional secondary codes,
 * allowing for dialect and pronunciation variations.
 *
 * Ported from iOS KBPhoneticMatcher for feature parity. All encoding rules
 * match the iOS implementation exactly.
 */
@Suppress("TooManyFunctions")
@Singleton
class KBPhoneticMatcher
    @Inject
    constructor() {
        companion object {
            private const val TAG = "KBPhoneticMatcher"

            /** Maximum length of a Metaphone code (matches iOS implementation). */
            const val MAX_CODE_LENGTH = 4
        }

        /**
         * Generate Double Metaphone codes for a text string.
         *
         * @param text Input text to encode
         * @return [MetaphoneResult] containing primary and optional secondary code
         */
        fun metaphone(text: String): MetaphoneResult {
            val cleaned = text.uppercase().filter { it.isLetter() }
            if (cleaned.isEmpty()) {
                return MetaphoneResult("", null)
            }

            val context = MetaphoneContext(cleaned)
            processInitialExceptions(context)
            processMainLoop(context)

            return context.result()
        }

        /**
         * Check if two strings are phonetically equivalent.
         *
         * Two strings match phonetically if any combination of their primary
         * and secondary Metaphone codes match.
         *
         * @param str1 First string
         * @param str2 Second string
         * @return True if strings match phonetically
         */
        @Suppress("ReturnCount")
        fun arePhoneticMatch(
            str1: String,
            str2: String,
        ): Boolean {
            val codes1 = metaphone(str1)
            val codes2 = metaphone(str2)

            // Empty codes (e.g., numbers only) should not match
            if (codes1.primary.isEmpty() || codes2.primary.isEmpty()) {
                return false
            }

            // Match if primary codes match
            if (codes1.primary == codes2.primary) {
                Log.d(TAG, "Phonetic match (primary): '$str1' [$codes1] <-> '$str2' [$codes2]")
                return true
            }

            // Match if either secondary matches the other's primary
            if (codes1.secondary != null && codes1.secondary == codes2.primary) {
                Log.d(TAG, "Phonetic match (sec1=pri2): '$str1' [$codes1] <-> '$str2' [$codes2]")
                return true
            }

            if (codes2.secondary != null && codes1.primary == codes2.secondary) {
                Log.d(TAG, "Phonetic match (pri1=sec2): '$str1' [$codes1] <-> '$str2' [$codes2]")
                return true
            }

            // Match if both secondary codes match
            if (codes1.secondary != null && codes2.secondary != null &&
                codes1.secondary == codes2.secondary
            ) {
                Log.d(TAG, "Phonetic match (sec=sec): '$str1' [$codes1] <-> '$str2' [$codes2]")
                return true
            }

            return false
        }

        // -- Processing --

        private fun processInitialExceptions(context: MetaphoneContext) {
            // Skip initial letters that are not pronounced
            if (context.stringAt(0, 2, "GN", "KN", "PN", "WR", "PS")) {
                context.advanceIndex()
            }

            // Initial X is pronounced Z -> encode as S
            if (context.charAt(0) == 'X') {
                context.appendBoth("S")
                context.advanceIndex()
            }
        }

        private fun processMainLoop(context: MetaphoneContext) {
            while (context.hasMore()) {
                val ch = context.currentChar()
                val pos = context.currentPosition()

                processCharacter(ch, pos, context)
                context.advanceIndex()
            }
        }

        @Suppress("CyclomaticComplexity", "CyclomaticComplexMethod")
        private fun processCharacter(
            ch: Char,
            pos: Int,
            context: MetaphoneContext,
        ) {
            when (ch) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> processVowel(pos, context)
                'B' -> processB(context)
                'C' -> processC(pos, context)
                'D' -> processD(pos, context)
                'F' -> processF(context)
                'G' -> processG(pos, context)
                'H' -> processH(pos, context)
                'J' -> processJ(pos, context)
                'K', 'L', 'M', 'N' -> processSimple(ch, context)
                'P' -> processP(context)
                'Q' -> processQ(context)
                'R' -> processR(context)
                'S' -> processS(pos, context)
                'T' -> processT(pos, context)
                'V' -> processV(context)
                'W' -> processW(pos, context)
                'X' -> processX(pos, context)
                'Z' -> processZ(context)
                else -> { /* skip */ }
            }
        }

        // -- Character Processing Rules (matching iOS exactly) --

        private fun processVowel(
            pos: Int,
            context: MetaphoneContext,
        ) {
            if (pos == 0) {
                context.appendBoth("A")
            }
        }

        private fun processB(context: MetaphoneContext) {
            context.appendBoth("P")
            if (context.charAt(1) == 'B') {
                context.advanceIndex()
            }
        }

        @Suppress("CyclomaticComplexity")
        private fun processC(
            pos: Int,
            context: MetaphoneContext,
        ) {
            if (context.stringAt(pos, 2, "CH")) {
                when {
                    // CHR at start -> K sound
                    pos == 0 && context.stringAt(pos, 3, "CHR") -> {
                        context.appendBoth("K")
                        context.advanceIndex()
                    }
                    // CHL, CHM, CHN -> K sound
                    context.stringAt(pos, 3, "CHL", "CHM", "CHN") -> {
                        context.appendBoth("K")
                        context.advanceIndex()
                    }
                    // CH at start -> K primary, X secondary
                    pos == 0 -> {
                        context.appendPrimary("K")
                        context.appendSecondary("X")
                        context.advanceIndex()
                    }
                    // Other CH -> X primary, K secondary
                    else -> {
                        context.appendPrimary("X")
                        context.appendSecondary("K")
                        context.advanceIndex()
                    }
                }
            } else if (pos == 0 && context.stringAt(pos, 2, "CE", "CI")) {
                context.appendBoth("S")
            } else if (context.stringAt(pos, 2, "CE", "CI", "CY")) {
                context.appendBoth("S")
            } else {
                context.appendBoth("K")
            }
            if (context.charAt(1) == 'C' && !context.stringAt(pos, 2, "CH")) {
                context.advanceIndex()
            }
        }

        private fun processD(
            pos: Int,
            context: MetaphoneContext,
        ) {
            if (context.stringAt(pos, 2, "DG")) {
                val nextNext = context.charAt(2)
                if (nextNext == 'E' || nextNext == 'I' || nextNext == 'Y') {
                    context.appendBoth("J")
                    context.advanceIndex(2)
                } else {
                    context.appendBoth("T")
                }
            } else {
                context.appendBoth("T")
            }
            if (context.charAt(1) == 'D') {
                context.advanceIndex()
            }
        }

        private fun processF(context: MetaphoneContext) {
            context.appendBoth("F")
            if (context.charAt(1) == 'F') {
                context.advanceIndex()
            }
        }

        @Suppress("CyclomaticComplexity")
        private fun processG(
            pos: Int,
            context: MetaphoneContext,
        ) {
            if (context.charAt(1) == 'H') {
                if (pos > 0 && !isVowel(context.charAt(-1))) {
                    context.appendBoth("K")
                } else if (pos == 0) {
                    if (context.charAt(2) == 'I') {
                        context.appendBoth("J")
                    } else {
                        context.appendBoth("K")
                    }
                }
            } else if (context.stringAt(pos, 2, "GN") || context.stringAt(pos, 4, "GNED")) {
                context.advanceIndex()
            } else if (context.charAt(1) == 'E' || context.charAt(1) == 'I' || context.charAt(1) == 'Y') {
                context.appendPrimary("J")
                context.appendSecondary("K")
            } else {
                context.appendBoth("K")
            }
            if (context.charAt(1) == 'G') {
                context.advanceIndex()
            }
        }

        private fun processH(
            pos: Int,
            context: MetaphoneContext,
        ) {
            val next = context.charAt(1)
            @Suppress("ComplexCondition")
            if (next != null && isVowel(next) && (pos == 0 || !isVowel(context.charAt(-1)))) {
                context.appendBoth("H")
            }
        }

        private fun processJ(
            pos: Int,
            context: MetaphoneContext,
        ) {
            context.appendPrimary("J")
            val prev = context.charAt(-1)
            if (pos == 0 || (prev != null && isVowel(prev))) {
                context.appendSecondary("J")
            } else {
                context.appendSecondary("A")
            }
            if (context.charAt(1) == 'J') {
                context.advanceIndex()
            }
        }

        private fun processSimple(
            ch: Char,
            context: MetaphoneContext,
        ) {
            context.appendBoth(ch.toString())
            if (context.charAt(1) == ch) {
                context.advanceIndex()
            }
        }

        private fun processP(context: MetaphoneContext) {
            if (context.charAt(1) == 'H') {
                context.appendBoth("F")
                context.advanceIndex()
            } else {
                context.appendBoth("P")
            }
            if (context.charAt(1) == 'P') {
                context.advanceIndex()
            }
        }

        private fun processQ(context: MetaphoneContext) {
            context.appendBoth("K")
            if (context.charAt(1) == 'Q') {
                context.advanceIndex()
            }
        }

        private fun processR(context: MetaphoneContext) {
            context.appendBoth("R")
            if (context.charAt(1) == 'R') {
                context.advanceIndex()
            }
        }

        private fun processS(
            pos: Int,
            context: MetaphoneContext,
        ) {
            when {
                context.stringAt(pos, 2, "SH") -> {
                    context.appendBoth("X")
                    context.advanceIndex()
                }
                context.stringAt(pos, 3, "SIO", "SIA") -> {
                    context.appendPrimary("S")
                    context.appendSecondary("X")
                }
                else -> {
                    context.appendBoth("S")
                }
            }
            if (context.charAt(1) == 'S') {
                context.advanceIndex()
            }
        }

        private fun processT(
            pos: Int,
            context: MetaphoneContext,
        ) {
            when {
                context.stringAt(pos, 3, "TIA", "TIO") -> {
                    context.appendBoth("X")
                }
                context.stringAt(pos, 2, "TH") -> {
                    context.appendPrimary("0") // Zero for TH (theta)
                    context.appendSecondary("T")
                    context.advanceIndex()
                }
                context.stringAt(pos, 2, "TCH") -> {
                    context.appendBoth("X")
                }
                else -> {
                    context.appendBoth("T")
                }
            }
            if (context.charAt(1) == 'T') {
                context.advanceIndex()
            }
        }

        private fun processV(context: MetaphoneContext) {
            context.appendBoth("F")
            if (context.charAt(1) == 'V') {
                context.advanceIndex()
            }
        }

        private fun processW(
            pos: Int,
            context: MetaphoneContext,
        ) {
            val next = context.charAt(1)
            if (pos == 0 && next != null && isVowel(next)) {
                context.appendPrimary("A")
                context.appendSecondary("F")
            } else if (context.stringAt(pos, 2, "WR")) {
                context.appendBoth("R")
                context.advanceIndex()
            }
        }

        private fun processX(
            pos: Int,
            context: MetaphoneContext,
        ) {
            if (pos == 0) {
                context.appendBoth("S")
            } else {
                context.appendBoth("KS")
            }
        }

        private fun processZ(context: MetaphoneContext) {
            context.appendBoth("S")
            if (context.charAt(1) == 'Z') {
                context.advanceIndex()
            }
        }

        private fun isVowel(ch: Char?): Boolean {
            if (ch == null) return false
            return ch in "AEIOUY"
        }
    }

/**
 * Result of Double Metaphone encoding.
 *
 * @property primary The primary phonetic code (always present if input is non-empty)
 * @property secondary The secondary phonetic code (null if same as primary or not applicable)
 */
data class MetaphoneResult(
    val primary: String,
    val secondary: String?,
)

/**
 * Internal mutable context for Double Metaphone encoding.
 *
 * Tracks the current position within the input text and accumulates
 * primary and secondary phonetic codes as characters are processed.
 */
internal class MetaphoneContext(
    val text: String,
) {
    private var index: Int = 0
    private val primary = StringBuilder()
    private val secondary = StringBuilder()

    fun advanceIndex(by: Int = 1) {
        index = minOf(index + by, text.length)
    }

    fun hasMore(): Boolean = index < text.length

    fun currentChar(): Char = text[index]

    fun currentPosition(): Int = index

    /**
     * Get character at offset from current position.
     *
     * @param offset Offset from current index (can be negative)
     * @return Character at the offset position, or null if out of bounds
     */
    fun charAt(offset: Int): Char? {
        val target = index + offset
        return if (target in text.indices) text[target] else null
    }

    /**
     * Check if any of the given strings appear at position [start] in the text.
     *
     * @param start Starting position in the text
     * @param length Expected length of the match
     * @param matches Strings to check against
     * @return True if any match is found
     */
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

    /**
     * Produce the final [MetaphoneResult], truncating codes to [KBPhoneticMatcher.MAX_CODE_LENGTH].
     */
    fun result(): MetaphoneResult {
        val primaryCode =
            primary.toString().take(KBPhoneticMatcher.MAX_CODE_LENGTH)
        val secondaryStr = secondary.toString()
        val secondaryCode =
            if (secondaryStr.isEmpty() || secondaryStr == primary.toString()) {
                null
            } else {
                secondaryStr.take(KBPhoneticMatcher.MAX_CODE_LENGTH)
            }
        return MetaphoneResult(primaryCode, secondaryCode)
    }
}
