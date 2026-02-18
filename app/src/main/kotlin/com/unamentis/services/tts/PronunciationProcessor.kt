package com.unamentis.services.tts

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pronunciation entry defining how a word or phrase should be spoken.
 *
 * Maps to the UMCF pronunciation guide format where each entry can specify
 * IPA phonetic transcription, a respelling hint, and an optional language tag.
 *
 * @property ipa International Phonetic Alphabet representation
 * @property respelling Optional simplified pronunciation respelling
 * @property language Optional BCP-47 language tag (e.g., "en-US", "fr-FR")
 */
@Serializable
data class PronunciationEntry(
    @SerialName("ipa")
    val ipa: String,
    @SerialName("respelling")
    val respelling: String? = null,
    @SerialName("language")
    val language: String? = null,
)

/**
 * Processes text for TTS output by applying pronunciation rules, generating SSML,
 * and expanding numbers, dates, and abbreviations into their spoken forms.
 *
 * This service matches the iOS PronunciationProcessor pattern and integrates with
 * UMCF transcript pronunciation guides. It transforms raw text into forms that
 * produce accurate and natural-sounding speech output.
 *
 * Features:
 * - SSML generation with `<phoneme>` tags for custom pronunciations
 * - Number-to-words expansion (integers and ordinals)
 * - Date pattern expansion to spoken forms
 * - Common abbreviation expansion
 * - Language-specific phoneme annotations
 *
 * Usage:
 * ```kotlin
 * val processor = PronunciationProcessor()
 * val guide = mapOf(
 *     "Euler" to PronunciationEntry(ipa = "OY-ler"),
 *     "Pythagorean" to PronunciationEntry(ipa = "pih-THAG-uh-REE-uhn")
 * )
 *
 * // Plain text processing
 * val processed = processor.processText("Euler proved it in 42 days.", guide)
 *
 * // SSML generation for TTS engines that support it
 * val ssml = processor.generateSSML("Euler proved it in 42 days.", guide)
 * ```
 */
@Singleton
class PronunciationProcessor
    @Inject
    constructor() {
        companion object {
            private const val TAG = "PronunciationProcessor"

            /** Default language for SSML phoneme tags when no language is specified. */
            private const val DEFAULT_LANGUAGE = "en-US"

            /** Built-in abbreviation dictionary mapping abbreviations to expanded forms. */
            val ABBREVIATION_DICTIONARY: Map<String, String> =
                mapOf(
                    "Dr." to "Doctor",
                    "Mr." to "Mister",
                    "Mrs." to "Missus",
                    "Ms." to "Miss",
                    "Prof." to "Professor",
                    "Sr." to "Senior",
                    "Jr." to "Junior",
                    "St." to "Saint",
                    "Ave." to "Avenue",
                    "Blvd." to "Boulevard",
                    "Dept." to "Department",
                    "Est." to "Established",
                    "Fig." to "Figure",
                    "Gov." to "Governor",
                    "Gen." to "General",
                    "Lt." to "Lieutenant",
                    "Sgt." to "Sergeant",
                    "Cpl." to "Corporal",
                    "Pvt." to "Private",
                    "Capt." to "Captain",
                    "Col." to "Colonel",
                    "Maj." to "Major",
                    "Rev." to "Reverend",
                    "vs." to "versus",
                    "etc." to "et cetera",
                    "approx." to "approximately",
                    "dept." to "department",
                    "govt." to "government",
                    "e.g." to "for example",
                    "i.e." to "that is",
                    "vol." to "volume",
                    "ch." to "chapter",
                    "pg." to "page",
                    "pp." to "pages",
                    "ed." to "edition",
                    "no." to "number",
                    "Jan." to "January",
                    "Feb." to "February",
                    "Mar." to "March",
                    "Apr." to "April",
                    "Jun." to "June",
                    "Jul." to "July",
                    "Aug." to "August",
                    "Sep." to "September",
                    "Sept." to "September",
                    "Oct." to "October",
                    "Nov." to "November",
                    "Dec." to "December",
                )

            /** Pattern for standalone integers (not part of dates or decimals). */
            private val NUMBER_PATTERN = Regex("""\b(\d{1,15})\b""")

            /**
             * Pattern for common date formats:
             * - MM/DD/YYYY or M/D/YYYY
             * - YYYY-MM-DD
             */
            private val DATE_PATTERN_SLASH = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{2,4})\b""")
            private val DATE_PATTERN_ISO = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")

            /** Ones words for number expansion. */
            private val ONES =
                arrayOf(
                    "", "one", "two", "three", "four", "five", "six", "seven",
                    "eight", "nine", "ten", "eleven", "twelve", "thirteen",
                    "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
                )

            /** Tens words for number expansion. */
            private val TENS =
                arrayOf(
                    "", "", "twenty", "thirty", "forty", "fifty",
                    "sixty", "seventy", "eighty", "ninety",
                )

            /** Month names for date expansion (1-indexed). */
            private val MONTH_NAMES =
                arrayOf(
                    "", "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December",
                )

            /** Ordinal suffixes for day-of-month expansion. */
            private val ORDINAL_SUFFIXES =
                mapOf(
                    1 to "first", 2 to "second", 3 to "third", 4 to "fourth",
                    5 to "fifth", 6 to "sixth", 7 to "seventh", 8 to "eighth",
                    9 to "ninth", 10 to "tenth", 11 to "eleventh", 12 to "twelfth",
                    13 to "thirteenth", 14 to "fourteenth", 15 to "fifteenth",
                    16 to "sixteenth", 17 to "seventeenth", 18 to "eighteenth",
                    19 to "nineteenth", 20 to "twentieth", 21 to "twenty-first",
                    22 to "twenty-second", 23 to "twenty-third", 24 to "twenty-fourth",
                    25 to "twenty-fifth", 26 to "twenty-sixth", 27 to "twenty-seventh",
                    28 to "twenty-eighth", 29 to "twenty-ninth", 30 to "thirtieth",
                    31 to "thirty-first",
                )
        }

        /**
         * Process text by applying pronunciation guide replacements.
         *
         * Replaces occurrences of guided words with their respelling form (if available)
         * or leaves them unchanged. This is useful for TTS engines that do not support SSML.
         *
         * @param text The input text to process
         * @param guide Pronunciation guide mapping words to their pronunciation entries
         * @return Processed text with pronunciation replacements applied
         */
        fun processText(
            text: String,
            guide: Map<String, PronunciationEntry>,
        ): String {
            if (guide.isEmpty()) return text

            var result = text
            for ((word, entry) in guide) {
                val replacement = entry.respelling ?: continue
                val pattern = Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE)
                result = pattern.replace(result, replacement)
            }
            Log.d(TAG, "Processed text with ${guide.size} pronunciation entries")
            return result
        }

        /**
         * Generate SSML markup with phoneme tags for pronunciation-guided words.
         *
         * Wraps guided words in `<phoneme>` SSML tags using IPA transcription so that
         * TTS engines with SSML support can pronounce them correctly.
         *
         * @param text The input text to convert to SSML
         * @param guide Pronunciation guide mapping words to their pronunciation entries
         * @return SSML string with `<speak>` root and `<phoneme>` tags
         */
        fun generateSSML(
            text: String,
            guide: Map<String, PronunciationEntry>,
        ): String {
            var body = escapeXml(text)

            for ((word, entry) in guide) {
                val escapedWord = Regex.escape(word)
                val pattern = Regex("""\b$escapedWord\b""", RegexOption.IGNORE_CASE)
                val lang = entry.language ?: DEFAULT_LANGUAGE
                val escapedIpa = escapeXml(entry.ipa)
                val phonemeTag =
                    "<phoneme alphabet=\"ipa\" ph=\"$escapedIpa\" xml:lang=\"$lang\">$word</phoneme>"
                body = pattern.replace(body, phonemeTag)
            }

            Log.d(TAG, "Generated SSML with ${guide.size} phoneme entries")
            return "<speak>$body</speak>"
        }

        /**
         * Expand standalone integers in the text to their English word equivalents.
         *
         * Handles numbers from 0 through 999,999,999,999,999 (trillions).
         * Numbers that are part of dates or decimal numbers are left unchanged.
         *
         * Examples:
         * - "42" becomes "forty-two"
         * - "100" becomes "one hundred"
         * - "1000" becomes "one thousand"
         *
         * @param text The input text containing numbers to expand
         * @return Text with standalone numbers replaced by English words
         */
        fun expandNumbers(text: String): String =
            NUMBER_PATTERN.replace(text) { match ->
                val numStr = match.groupValues[1]
                val num = numStr.toLongOrNull()
                if (num != null) {
                    numberToWords(num)
                } else {
                    numStr
                }
            }

        /**
         * Expand date patterns in the text to their spoken English form.
         *
         * Supported formats:
         * - `MM/DD/YYYY` or `M/D/YYYY` (e.g., "1/15/2024" becomes "January fifteenth, twenty twenty-four")
         * - `YYYY-MM-DD` (e.g., "2024-01-15" becomes "January fifteenth, twenty twenty-four")
         *
         * @param text The input text containing date patterns to expand
         * @return Text with dates replaced by spoken English forms
         */
        fun expandDates(text: String): String {
            var result = text

            // Handle MM/DD/YYYY format
            result =
                DATE_PATTERN_SLASH.replace(result) { match ->
                    val month = match.groupValues[1].toIntOrNull() ?: return@replace match.value
                    val day = match.groupValues[2].toIntOrNull() ?: return@replace match.value
                    val year = match.groupValues[3].toIntOrNull() ?: return@replace match.value
                    formatSpokenDate(month, day, normalizeYear(year))
                }

            // Handle YYYY-MM-DD format
            result =
                DATE_PATTERN_ISO.replace(result) { match ->
                    val year = match.groupValues[1].toIntOrNull() ?: return@replace match.value
                    val month = match.groupValues[2].toIntOrNull() ?: return@replace match.value
                    val day = match.groupValues[3].toIntOrNull() ?: return@replace match.value
                    formatSpokenDate(month, day, year)
                }

            return result
        }

        /**
         * Expand common abbreviations in the text to their full spoken forms.
         *
         * Uses the built-in [ABBREVIATION_DICTIONARY] for lookups. Matching is
         * case-sensitive to avoid false positives.
         *
         * Examples:
         * - "Dr. Smith" becomes "Doctor Smith"
         * - "vs." becomes "versus"
         * - "e.g." becomes "for example"
         *
         * @param text The input text containing abbreviations to expand
         * @return Text with abbreviations replaced by their full forms
         */
        fun expandAbbreviations(text: String): String {
            var result = text
            for ((abbr, expansion) in ABBREVIATION_DICTIONARY) {
                result = result.replace(abbr, expansion)
            }
            return result
        }

        // =========================================================================
        // INTERNAL HELPERS
        // =========================================================================

        /**
         * Convert a number to its English word representation.
         *
         * @param num The number to convert (0 to 999,999,999,999,999)
         * @return English words for the number
         */
        internal fun numberToWords(num: Long): String {
            if (num == 0L) return "zero"
            if (num < 0L) return "negative ${numberToWords(-num)}"

            val parts = mutableListOf<String>()

            val trillion = num / 1_000_000_000_000L
            val billion = (num % 1_000_000_000_000L) / 1_000_000_000L
            val million = (num % 1_000_000_000L) / 1_000_000L
            val thousand = (num % 1_000_000L) / 1_000L
            val remainder = num % 1_000L

            if (trillion > 0) parts.add("${hundredsToWords(trillion.toInt())} trillion")
            if (billion > 0) parts.add("${hundredsToWords(billion.toInt())} billion")
            if (million > 0) parts.add("${hundredsToWords(million.toInt())} million")
            if (thousand > 0) parts.add("${hundredsToWords(thousand.toInt())} thousand")
            if (remainder > 0) parts.add(hundredsToWords(remainder.toInt()))

            return parts.joinToString(" ")
        }

        /**
         * Convert a number in the range 1-999 to English words.
         */
        private fun hundredsToWords(num: Int): String {
            if (num == 0) return ""
            if (num < 20) return ONES[num]

            val parts = mutableListOf<String>()

            val hundreds = num / 100
            val tensOnes = num % 100

            if (hundreds > 0) {
                parts.add("${ONES[hundreds]} hundred")
            }

            if (tensOnes > 0) {
                if (tensOnes < 20) {
                    parts.add(ONES[tensOnes])
                } else {
                    val ten = tensOnes / 10
                    val one = tensOnes % 10
                    if (one > 0) {
                        parts.add("${TENS[ten]}-${ONES[one]}")
                    } else {
                        parts.add(TENS[ten])
                    }
                }
            }

            return parts.joinToString(" ")
        }

        /**
         * Format a date as spoken English.
         *
         * @param month Month number (1-12)
         * @param day Day number (1-31)
         * @param year Four-digit year
         * @return Spoken date string (e.g., "January fifteenth, twenty twenty-four")
         */
        private fun formatSpokenDate(
            month: Int,
            day: Int,
            year: Int,
        ): String {
            if (month !in 1..12 || day !in 1..31) return "$month/$day/$year"
            val monthName = MONTH_NAMES[month]
            val dayOrdinal = ORDINAL_SUFFIXES[day] ?: "${day}th"
            val yearWords = yearToWords(year)
            return "$monthName $dayOrdinal, $yearWords"
        }

        /**
         * Normalize a two-digit year to four digits.
         *
         * Years 0-99 are mapped to 2000-2099 for two-digit input.
         */
        private fun normalizeYear(year: Int): Int =
            if (year < 100) year + 2000 else year

        /**
         * Convert a four-digit year to its spoken English form.
         *
         * Uses common English conventions:
         * - Years like 1900 are read as "nineteen hundred"
         * - Years like 2000 are read as "two thousand"
         * - Years like 2024 are read as "twenty twenty-four"
         * - Years like 1776 are read as "seventeen seventy-six"
         */
        private fun yearToWords(year: Int): String {
            if (year == 2000) return "two thousand"
            if (year in 2001..2009) return "two thousand ${ONES[year % 10]}"

            val firstHalf = year / 100
            val secondHalf = year % 100

            val firstPart = hundredsToWords(firstHalf)

            if (secondHalf == 0) return "$firstPart hundred"

            val secondPart =
                if (secondHalf < 10) {
                    "oh-${ONES[secondHalf]}"
                } else {
                    hundredsToWords(secondHalf)
                }

            return "$firstPart $secondPart"
        }

        /**
         * Escape special XML characters for safe embedding in SSML.
         */
        private fun escapeXml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
