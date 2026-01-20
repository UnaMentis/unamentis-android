package com.unamentis.modules.knowledgebowl.core.engine

import android.content.Context
import android.util.Log
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBGradeLevel
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBQuestionBundle
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for loading, filtering, and managing Knowledge Bowl questions.
 *
 * Provides question loading from bundled JSON or external URLs,
 * filtering by various criteria, and selection algorithms for practice sessions.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@Singleton
class KBQuestionEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "KBQuestionEngine"
            private const val BUNDLED_QUESTIONS_FILE = "kb-sample-questions.json"
        }

        // State
        private val _questions = MutableStateFlow<List<KBQuestion>>(emptyList())
        val questions: StateFlow<List<KBQuestion>> = _questions.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _loadError = MutableStateFlow<KBQuestionError?>(null)
        val loadError: StateFlow<KBQuestionError?> = _loadError.asStateFlow()

        // Track attempted questions
        private val attemptedQuestionIds = mutableSetOf<String>()

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /**
         * Load questions from the bundled JSON file in assets.
         */
        suspend fun loadBundledQuestions() {
            _isLoading.value = true
            _loadError.value = null

            try {
                withContext(Dispatchers.IO) {
                    val jsonString =
                        context.assets
                            .open(BUNDLED_QUESTIONS_FILE)
                            .bufferedReader()
                            .use { it.readText() }

                    val bundle = json.decodeFromString<KBQuestionBundle>(jsonString)
                    _questions.value = bundle.questions
                    Log.i(TAG, "Loaded ${bundle.questions.size} questions from bundle v${bundle.version}")
                }
            } catch (e: IOException) {
                val error = KBQuestionError.BundleNotFound
                _loadError.value = error
                Log.e(TAG, "Failed to find bundled questions", e)
                throw error
            } catch (e: Exception) {
                val error = KBQuestionError.DecodingFailed(e.message ?: "Unknown error")
                _loadError.value = error
                Log.e(TAG, "Failed to decode questions", e)
                throw error
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Load questions from a custom URL.
         */
        suspend fun loadQuestions(url: String) {
            _isLoading.value = true
            _loadError.value = null

            try {
                withContext(Dispatchers.IO) {
                    val jsonString = URL(url).readText()
                    val bundle = json.decodeFromString<KBQuestionBundle>(jsonString)
                    _questions.value = bundle.questions
                    Log.i(TAG, "Loaded ${bundle.questions.size} questions from $url")
                }
            } catch (e: IOException) {
                val error = KBQuestionError.LoadFailed(url, e.message ?: "IO error")
                _loadError.value = error
                Log.e(TAG, "Failed to load questions from URL", e)
                throw error
            } catch (e: Exception) {
                val error = KBQuestionError.DecodingFailed(e.message ?: "Unknown error")
                _loadError.value = error
                Log.e(TAG, "Failed to decode questions", e)
                throw error
            } finally {
                _isLoading.value = false
            }
        }

        /**
         * Set questions directly (for testing or custom sources).
         */
        fun setQuestions(questions: List<KBQuestion>) {
            _questions.value = questions
            Log.d(TAG, "Set ${questions.size} questions directly")
        }

        /**
         * Filter questions by various criteria.
         *
         * @param domains Filter by specific domains (null = all domains)
         * @param difficulty Filter by difficulty level (null = all levels)
         * @param gradeLevel Filter by grade level (null = all levels)
         * @param forWritten Filter by written round suitability (null = don't filter)
         * @param forOral Filter by oral round suitability (null = don't filter)
         * @param excludeAttempted Exclude questions that have been attempted
         * @return Filtered list of questions
         */
        @Suppress("LongParameterList")
        fun filter(
            domains: List<KBDomain>? = null,
            difficulty: KBDifficulty? = null,
            gradeLevel: KBGradeLevel? = null,
            forWritten: Boolean? = null,
            forOral: Boolean? = null,
            excludeAttempted: Boolean = false,
        ): List<KBQuestion> {
            var filtered = _questions.value

            // Filter by domains
            if (!domains.isNullOrEmpty()) {
                filtered = filtered.filter { domains.contains(it.domain) }
            }

            // Filter by difficulty
            if (difficulty != null) {
                filtered = filtered.filter { it.difficulty == difficulty }
            }

            // Filter by grade level
            if (gradeLevel != null) {
                filtered = filtered.filter { it.gradeLevel == gradeLevel }
            }

            // Filter by suitability for written round
            if (forWritten != null) {
                filtered = filtered.filter { it.suitability.forWritten == forWritten }
            }

            // Filter by suitability for oral round
            if (forOral != null) {
                filtered = filtered.filter { it.suitability.forOral == forOral }
            }

            // Exclude already attempted questions
            if (excludeAttempted) {
                filtered = filtered.filter { it.id !in attemptedQuestionIds }
            }

            return filtered
        }

        /**
         * Select a random subset of questions.
         *
         * @param count Number of questions to select
         * @param from Optional filtered list to select from (defaults to all questions)
         * @return Random selection of questions
         */
        fun selectRandom(
            count: Int,
            from: List<KBQuestion>? = null,
        ): List<KBQuestion> {
            val pool = from ?: _questions.value
            if (pool.isEmpty()) return emptyList()

            val actualCount = minOf(count, pool.size)
            return pool.shuffled().take(actualCount)
        }

        /**
         * Select questions for a practice session based on configuration.
         *
         * @param config Session configuration specifying filters and count
         * @return Questions for the session
         */
        fun selectForSession(config: KBSessionConfig): List<KBQuestion> {
            val filtered =
                filter(
                    domains = config.domains,
                    difficulty = config.difficulty,
                    gradeLevel = config.gradeLevel,
                    forWritten = if (config.roundType == KBRoundType.WRITTEN) true else null,
                    forOral = if (config.roundType == KBRoundType.ORAL) true else null,
                )

            return selectRandom(config.questionCount, filtered)
        }

        /**
         * Select questions with weighted domain distribution.
         *
         * @param count Total number of questions to select
         * @param respectDomainWeights Whether to use domain weights for distribution
         * @param from Optional filtered list to select from
         * @return Weighted selection of questions
         */
        @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
        fun selectWeighted(
            count: Int,
            respectDomainWeights: Boolean = true,
            from: List<KBQuestion>? = null,
        ): List<KBQuestion> {
            val pool = from ?: _questions.value
            if (pool.isEmpty()) return emptyList()

            if (!respectDomainWeights) {
                return selectRandom(count, pool)
            }

            // Group questions by domain
            val byDomain = pool.groupBy { it.domain }

            // Calculate how many questions per domain based on weights
            val selected = mutableListOf<KBQuestion>()
            var remaining = count

            for (domain in KBDomain.entries) {
                val domainQuestions = byDomain[domain] ?: continue
                if (domainQuestions.isEmpty()) continue

                val targetCount = (count * domain.weight).toInt()
                val actualCount = minOf(targetCount, domainQuestions.size, remaining)

                if (actualCount > 0) {
                    selected.addAll(domainQuestions.shuffled().take(actualCount))
                    remaining -= actualCount
                }
            }

            // Fill any remaining slots randomly
            if (remaining > 0) {
                val alreadySelectedIds = selected.map { it.id }.toSet()
                val additionalPool = pool.filter { it.id !in alreadySelectedIds }
                selected.addAll(additionalPool.shuffled().take(remaining))
            }

            return selected.shuffled()
        }

        // Attempt Tracking

        /**
         * Mark a question as attempted.
         */
        fun markAttempted(questionId: String) {
            attemptedQuestionIds.add(questionId)
        }

        /**
         * Mark multiple questions as attempted.
         */
        fun markAttempted(questionIds: List<String>) {
            attemptedQuestionIds.addAll(questionIds)
        }

        /**
         * Clear attempted question tracking.
         */
        fun clearAttemptedQuestions() {
            attemptedQuestionIds.clear()
        }

        /**
         * Check if a question has been attempted.
         */
        fun hasAttempted(questionId: String): Boolean = questionId in attemptedQuestionIds

        // Statistics

        /**
         * Get question counts by domain.
         */
        val questionsByDomain: Map<KBDomain, Int>
            get() = _questions.value.groupingBy { it.domain }.eachCount()

        /**
         * Get question counts by difficulty.
         */
        val questionsByDifficulty: Map<KBDifficulty, Int>
            get() = _questions.value.groupingBy { it.difficulty }.eachCount()

        /**
         * Get question counts by grade level.
         */
        val questionsByGradeLevel: Map<KBGradeLevel, Int>
            get() = _questions.value.groupingBy { it.gradeLevel }.eachCount()

        /**
         * Total number of questions available.
         */
        val totalQuestionCount: Int
            get() = _questions.value.size

        /**
         * Number of questions not yet attempted.
         */
        val unattemptedCount: Int
            get() = _questions.value.size - attemptedQuestionIds.size
    }
