package com.unamentis.modules.knowledgebowl.core.engine

import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBAnswer
import com.unamentis.modules.knowledgebowl.data.model.KBAnswerType
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBGradeLevel
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBSuitability
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Transformer for importing and converting questions to KB format.
 *
 * Handles:
 * - Domain string → KBDomain mapping (15+ variations)
 * - Difficulty string → KBDifficulty mapping
 * - Answer type inference from answer text
 * - Quality scoring (0.0-1.0)
 * - Batch import from JSON files
 * - Text cleaning (Quiz Bowl markers, Science Bowl prefixes)
 *
 * Maps to iOS KBTransformer struct.
 */
@Suppress("TooManyFunctions")
object KBTransformer {
    private const val TAG = "KBTransformer"
    private const val READING_SPEED_WPM = 150.0
    private const val QUALITY_BASE = 0.5
    private const val QUALITY_MCQ_BONUS = 0.15
    private const val QUALITY_ACCEPTABLE_BONUS = 0.1
    private const val QUALITY_LENGTH_BONUS = 0.15
    private const val QUALITY_NO_FORMULA_BONUS = 0.1
    private const val MIN_QUESTION_LENGTH = 10
    private const val MAX_QUESTION_LENGTH = 500

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    // MARK: - Import Transformation

    /**
     * Transform an imported question to KB format.
     *
     * @return Transformed KBQuestion, or null if the question can't be mapped
     */
    fun transform(imported: ImportedQuestion): KBQuestion? {
        val domain = mapDomain(imported.domain) ?: return null
        val cleanedText = cleanQuizBowlMarkers(imported.text)
        val cleanedAnswer = cleanScienceBowlPrefix(imported.answer)

        if (cleanedText.isBlank() || cleanedAnswer.isBlank()) return null

        val difficulty = mapDifficulty(imported.difficulty)
        val answerType = inferAnswerType(cleanedAnswer)
        val wordCount = cleanedText.split("\\s+".toRegex()).size
        val estimatedReadTime = (wordCount / READING_SPEED_WPM * 60).toFloat()

        val suitability =
            KBSuitability(
                forWritten = true,
                forOral = !imported.hasFormula.orFalse() && !imported.requiresCalculation.orFalse(),
                mcqPossible = imported.mcqOptions != null && imported.mcqOptions.size >= 2,
                requiresVisual = imported.hasFormula.orFalse(),
            )

        return KBQuestion(
            id = UUID.randomUUID().toString(),
            text = cleanedText,
            answer =
                KBAnswer(
                    primary = cleanedAnswer,
                    acceptable = imported.acceptableAnswers,
                    answerType = answerType,
                ),
            domain = domain,
            subdomain = imported.subdomain,
            difficulty = difficulty,
            gradeLevel = inferGradeLevel(difficulty),
            suitability = suitability,
            estimatedReadTime = estimatedReadTime,
            mcqOptions = imported.mcqOptions,
            source = imported.source,
        )
    }

    /**
     * Transform a batch of imported questions, filtering out unmappable ones.
     */
    fun transformBatch(imported: List<ImportedQuestion>): List<KBQuestion> = imported.mapNotNull { transform(it) }

    // MARK: - Quality Assessment

    /**
     * Score the quality of an imported question (0.0 to 1.0).
     *
     * Higher scores for questions with:
     * - MCQ options available
     * - Acceptable alternative answers
     * - Appropriate text length
     * - No formulas (more accessible)
     */
    fun qualityScore(imported: ImportedQuestion): Double {
        var score = QUALITY_BASE

        // MCQ options available
        if (imported.mcqOptions != null && imported.mcqOptions.size >= 2) {
            score += QUALITY_MCQ_BONUS
        }

        // Has acceptable alternatives
        if (!imported.acceptableAnswers.isNullOrEmpty()) {
            score += QUALITY_ACCEPTABLE_BONUS
        }

        // Appropriate question length
        if (imported.text.length in MIN_QUESTION_LENGTH..MAX_QUESTION_LENGTH) {
            score += QUALITY_LENGTH_BONUS
        }

        // No formulas (more accessible for oral rounds)
        if (!imported.hasFormula.orFalse()) {
            score += QUALITY_NO_FORMULA_BONUS
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Filter questions by quality threshold.
     *
     * @param threshold Minimum quality score (default 0.5)
     */
    fun filterByQuality(
        imported: List<ImportedQuestion>,
        threshold: Double = 0.5,
    ): List<ImportedQuestion> = imported.filter { qualityScore(it) >= threshold }

    // MARK: - JSON Import

    /**
     * Load imported questions from a JSON file.
     */
    fun loadFromJSON(file: File): List<ImportedQuestion> {
        val content = file.readText()
        return json.decodeFromString<List<ImportedQuestion>>(content)
    }

    /**
     * Import questions from a JSON file, transform, and save to output file.
     *
     * @return Number of questions successfully imported
     */
    fun importAndSave(
        fromFile: File,
        toFile: File,
    ): Int {
        val imported = loadFromJSON(fromFile)
        val transformed = transformBatch(imported)

        if (transformed.isNotEmpty()) {
            val serializer =
                kotlinx.serialization.builtins.ListSerializer(KBQuestion.serializer())
            toFile.writeText(json.encodeToString(serializer, transformed))
        }

        Log.i(TAG, "Imported ${transformed.size}/${imported.size} questions")
        return transformed.size
    }

    // MARK: - Domain Mapping

    @Suppress("CyclomaticComplexMethod")
    private fun mapDomain(domainString: String): KBDomain? {
        val normalized = domainString.lowercase().trim()
        return when {
            normalized in listOf("science", "sci", "general science") -> KBDomain.SCIENCE
            normalized in listOf("math", "mathematics", "maths") -> KBDomain.MATHEMATICS
            normalized in listOf("literature", "lit", "english literature") -> KBDomain.LITERATURE
            normalized in
                listOf(
                    "history", "hist", "world history",
                    "us history", "american history",
                ) -> KBDomain.HISTORY
            normalized in
                listOf(
                    "social studies", "social_studies", "government",
                    "economics", "civics", "geography",
                ) -> KBDomain.SOCIAL_STUDIES
            normalized in listOf("arts", "art", "fine arts", "music", "visual arts") -> KBDomain.ARTS
            normalized in listOf("current events", "current_events", "news") -> KBDomain.CURRENT_EVENTS
            normalized in listOf("language", "languages", "foreign language", "world languages") -> KBDomain.LANGUAGE
            normalized in listOf("technology", "tech", "computer science", "cs") -> KBDomain.TECHNOLOGY
            normalized in
                listOf(
                    "pop culture", "pop_culture",
                    "entertainment", "popular culture",
                ) -> KBDomain.POP_CULTURE
            normalized in
                listOf(
                    "religion", "philosophy",
                    "religion_philosophy", "religion & philosophy",
                ) -> KBDomain.RELIGION_PHILOSOPHY
            normalized in listOf("miscellaneous", "misc", "general", "other") -> KBDomain.MISCELLANEOUS
            else -> {
                Log.w(TAG, "Unknown domain: $domainString")
                null
            }
        }
    }

    // MARK: - Difficulty Mapping

    private fun mapDifficulty(difficultyString: String?): KBDifficulty {
        if (difficultyString == null) return KBDifficulty.VARSITY

        val normalized = difficultyString.lowercase().trim()
        return when {
            normalized in listOf("overview", "basic", "easy", "1") -> KBDifficulty.OVERVIEW
            normalized in listOf("foundational", "elementary", "2") -> KBDifficulty.FOUNDATIONAL
            normalized in listOf("intermediate", "medium", "3") -> KBDifficulty.INTERMEDIATE
            normalized in listOf("varsity", "advanced", "hard", "4") -> KBDifficulty.VARSITY
            normalized in listOf("championship", "expert", "very hard", "5") -> KBDifficulty.CHAMPIONSHIP
            normalized in listOf("research", "phd", "extreme", "6") -> KBDifficulty.RESEARCH
            else -> KBDifficulty.VARSITY
        }
    }

    // MARK: - Answer Type Inference

    @Suppress("ReturnCount")
    private fun inferAnswerType(answer: String): KBAnswerType {
        val trimmed = answer.trim()

        // Check for single letter (MCQ)
        if (trimmed.length == 1 && trimmed[0].isLetter()) return KBAnswerType.MULTIPLE_CHOICE

        // Check for numeric
        if (trimmed.matches(Regex("^-?[\\d,]+\\.?\\d*$"))) return KBAnswerType.NUMERIC

        // Check for date patterns
        if (trimmed.matches(Regex("^\\d{4}$")) || trimmed.matches(Regex("^\\d{1,2}/\\d{1,2}/\\d{2,4}$"))) {
            return KBAnswerType.DATE
        }

        // Check for person names (capitalized words, potentially with titles)
        val words = trimmed.split("\\s+".toRegex())
        if (words.size in 2..4 && words.all { it[0].isUpperCase() }) return KBAnswerType.PERSON

        // Check for scientific terms (contains numbers mixed with letters, like H2O)
        if (trimmed.matches(Regex(".*[A-Za-z]\\d+.*"))) return KBAnswerType.SCIENTIFIC

        return KBAnswerType.TEXT
    }

    // MARK: - Grade Level

    private fun inferGradeLevel(difficulty: KBDifficulty): KBGradeLevel =
        when (difficulty) {
            KBDifficulty.OVERVIEW, KBDifficulty.FOUNDATIONAL -> KBGradeLevel.MIDDLE_SCHOOL
            KBDifficulty.INTERMEDIATE, KBDifficulty.VARSITY -> KBGradeLevel.HIGH_SCHOOL
            KBDifficulty.CHAMPIONSHIP, KBDifficulty.RESEARCH -> KBGradeLevel.ADVANCED
        }

    // MARK: - Text Cleaning

    private fun cleanQuizBowlMarkers(text: String): String =
        text
            .replace(Regex("(?i)for \\d+ points?,?\\s*"), "")
            .replace(Regex("(?i)\\bftp\\b,?\\s*"), "")
            .trim()

    private fun cleanScienceBowlPrefix(answer: String): String =
        answer
            .replace(Regex("^[A-Z]\\)\\s*"), "")
            .trim()

    private fun Boolean?.orFalse(): Boolean = this ?: false
}

// MARK: - Imported Question Model

/**
 * A question imported from an external source.
 *
 * Used as input to [KBTransformer.transform] to convert to [KBQuestion].
 */
@Serializable
data class ImportedQuestion(
    val text: String,
    val answer: String,
    @SerialName("acceptable_answers")
    val acceptableAnswers: List<String>? = null,
    val domain: String,
    val subdomain: String? = null,
    val difficulty: String? = null,
    @SerialName("grade_level")
    val gradeLevel: String? = null,
    val source: String,
    @SerialName("mcq_options")
    val mcqOptions: List<String>? = null,
    @SerialName("requires_calculation")
    val requiresCalculation: Boolean? = null,
    @SerialName("has_formula")
    val hasFormula: Boolean? = null,
    @SerialName("year_written")
    val yearWritten: Int? = null,
)
