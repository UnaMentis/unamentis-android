package com.unamentis.modules.knowledgebowl.core.validation

import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType

/**
 * Normalizes answers based on their type for comparison.
 *
 * Different answer types require different normalization strategies:
 * - Text: Remove punctuation, articles, normalize whitespace
 * - Person: Handle titles, name order variations
 * - Place: Expand common abbreviations
 * - Number: Parse written numbers, remove formatting
 * - Date: Normalize month names
 * - Title: Remove leading articles, handle subtitles
 * - Scientific: Preserve formula characters
 * - Multiple Choice: Extract just the letter
 */
object AnswerNormalizer {
    // Common articles to remove
    private val articles = listOf("the ", "a ", "an ")

    // Personal titles to remove
    private val personalTitles =
        listOf(
            "dr", "mr", "mrs", "ms", "miss", "prof", "professor",
            "sir", "dame", "lord", "lady", "rev", "reverend",
        )

    // Place abbreviations
    private val placeAbbreviations =
        mapOf(
            "usa" to "united states of america",
            "us" to "united states",
            "uk" to "united kingdom",
            "uae" to "united arab emirates",
            "mt" to "mount",
            "st" to "saint",
            "ft" to "fort",
            "nyc" to "new york city",
            "la" to "los angeles",
            "dc" to "district of columbia",
        )

    // Written number words
    private val wordNumbers =
        mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
            "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
            "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
            "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
            "forty" to "40", "fifty" to "50", "sixty" to "60", "seventy" to "70",
            "eighty" to "80", "ninety" to "90", "hundred" to "100", "thousand" to "1000",
            "million" to "1000000", "billion" to "1000000000",
        )

    // Month name mappings
    private val months =
        mapOf(
            "january" to "1", "jan" to "1",
            "february" to "2", "feb" to "2",
            "march" to "3", "mar" to "3",
            "april" to "4", "apr" to "4",
            "may" to "5",
            "june" to "6", "jun" to "6",
            "july" to "7", "jul" to "7",
            "august" to "8", "aug" to "8",
            "september" to "9", "sep" to "9", "sept" to "9",
            "october" to "10", "oct" to "10",
            "november" to "11", "nov" to "11",
            "december" to "12", "dec" to "12",
        )

    /**
     * Normalize text based on answer type.
     *
     * @param text The text to normalize
     * @param type The type of answer for specialized normalization
     * @return Normalized text ready for comparison
     */
    fun normalize(
        text: String,
        type: KBAnswerType,
    ): String {
        var normalized =
            text
                .lowercase()
                .trim()

        return when (type) {
            KBAnswerType.TEXT -> normalizeText(normalized)
            KBAnswerType.PERSON -> normalizePerson(normalized)
            KBAnswerType.PLACE -> normalizePlace(normalized)
            KBAnswerType.NUMBER -> normalizeNumber(normalized)
            KBAnswerType.DATE -> normalizeDate(normalized)
            KBAnswerType.TITLE -> normalizeTitle(normalized)
            KBAnswerType.SCIENTIFIC -> normalizeScientific(normalized)
            KBAnswerType.MULTIPLE_CHOICE -> normalizeMultipleChoice(normalized)
        }
    }

    /**
     * Basic text normalization.
     */
    private fun normalizeText(text: String): String {
        var result = text
        // Remove common punctuation
        result = result.replace(Regex("[.,!?;:'\"()\\-]"), "")
        // Collapse multiple spaces
        result = result.replace(Regex("\\s+"), " ")
        // Remove leading articles
        result = removeArticles(result)
        return result.trim()
    }

    /**
     * Normalize person names.
     * Handles titles, name order (First Last vs Last, First).
     */
    private fun normalizePerson(text: String): String {
        var result = text

        // Remove personal titles first (before other normalization)
        for (title in personalTitles) {
            result = result.replace(Regex("^$title\\.?\\s+"), "")
        }

        // Handle "Last, First" format -> "First Last" BEFORE removing punctuation
        if (result.contains(",")) {
            val parts = result.split(",").map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                result = "${parts[1]} ${parts[0]}"
            }
        }

        // Now apply standard text normalization
        result = normalizeText(result)

        return result
    }

    /**
     * Normalize place names.
     * Expands common abbreviations.
     */
    private fun normalizePlace(text: String): String {
        var result = normalizeText(text)

        // Check for exact abbreviation matches
        placeAbbreviations[result]?.let { expanded ->
            result = expanded
        }

        return result
    }

    /**
     * Normalize numbers.
     * Converts written numbers and removes formatting.
     */
    private fun normalizeNumber(text: String): String {
        var result = normalizeText(text)

        // Check for written number words
        wordNumbers[result]?.let { numeral ->
            return numeral
        }

        // Remove commas from numbers (e.g., "1,000" -> "1000")
        result = result.replace(",", "")

        return result
    }

    /**
     * Normalize dates.
     * Converts month names to numbers.
     */
    private fun normalizeDate(text: String): String {
        var result = normalizeText(text)

        // Replace month names with numbers
        for ((name, num) in months) {
            result = result.replace(name, num)
        }

        return result
    }

    /**
     * Normalize titles (books, movies, etc.).
     * Removes leading "the" and handles subtitles.
     */
    private fun normalizeTitle(text: String): String {
        var result = text

        // Remove subtitle after colon BEFORE other normalization
        val colonIndex = result.indexOf(':')
        if (colonIndex > 0) {
            result = result.substring(0, colonIndex)
        }

        // Now apply standard text normalization
        result = normalizeText(result)

        // Remove leading "the"
        result = result.replace(Regex("^the\\s+"), "")

        return result.trim()
    }

    /**
     * Normalize scientific terms/formulas.
     * Preserves special characters but removes whitespace.
     */
    private fun normalizeScientific(text: String): String {
        // Keep special characters for formulas, just remove whitespace
        return text.lowercase().replace(Regex("\\s+"), "")
    }

    /**
     * Normalize multiple choice answers.
     * Extracts just the letter (A, B, C, D).
     */
    private fun normalizeMultipleChoice(text: String): String {
        // Extract just the first letter
        return text.filter { it.isLetter() }.take(1).lowercase()
    }

    /**
     * Remove leading articles from text.
     */
    private fun removeArticles(text: String): String {
        var result = text
        for (article in articles) {
            if (result.startsWith(article)) {
                result = result.removePrefix(article)
                break
            }
        }
        return result
    }
}
