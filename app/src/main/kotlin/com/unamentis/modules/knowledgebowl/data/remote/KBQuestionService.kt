package com.unamentis.modules.knowledgebowl.data.remote

import android.content.Context
import android.util.Log
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBModuleFeatures
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Service for fetching Knowledge Bowl questions from the server.
 *
 * Attempts to load questions from the Management API (port 8766) and falls back
 * to bundled questions if the server is unavailable. Provides study mode-specific
 * question selection algorithms.
 *
 * @property context Application context for SharedPreferences access
 * @property questionEngine Engine for loading bundled questions as fallback
 * @property statsManager Stats manager for identifying weak domains
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@Singleton
class KBQuestionService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val questionEngine: KBQuestionEngine,
        private val statsManager: KBStatsManager,
    ) {
        companion object {
            private const val TAG = "KBQuestionService"
            private const val DEFAULT_PORT = 8766
            private const val TIMEOUT_SECONDS = 10L
            private const val PREFS_NAME = "unamentis_prefs"
            private const val KEY_SELF_HOSTED_ENABLED = "selfHostedEnabled"
            private const val KEY_PRIMARY_SERVER_IP = "primaryServerIP"
        }

        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private val httpClient =
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        // State
        private val _allQuestions = MutableStateFlow<List<KBQuestion>>(emptyList())
        val allQuestions: StateFlow<List<KBQuestion>> = _allQuestions.asStateFlow()

        private val _isLoaded = MutableStateFlow(false)
        val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

        private val _moduleFeatures = MutableStateFlow(KBModuleFeatures.DEFAULT_ENABLED)
        val moduleFeatures: StateFlow<KBModuleFeatures> = _moduleFeatures.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        // MARK: - Loading

        /**
         * Load questions from server with fallback to bundled questions.
         *
         * First attempts to fetch from the Management API. If that fails,
         * falls back to bundled questions loaded by [KBQuestionEngine].
         */
        suspend fun loadQuestions() {
            _error.value = null

            try {
                val questions = fetchQuestionsFromServer()
                _allQuestions.value = questions
                _isLoaded.value = true
                Log.i(TAG, "Loaded ${questions.size} questions from server")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Server fetch failed, using bundled questions: ${e.message}")
                loadBundledFallback()
            }
        }

        /**
         * Force reload questions from server.
         */
        suspend fun refreshQuestions() {
            _isLoaded.value = false
            loadQuestions()
        }

        private suspend fun loadBundledFallback() {
            try {
                // Ensure bundled questions are loaded
                if (questionEngine.questions.value.isEmpty()) {
                    questionEngine.loadBundledQuestions()
                }
                _allQuestions.value = questionEngine.questions.value
                _isLoaded.value = true
                Log.i(TAG, "Using ${_allQuestions.value.size} bundled questions")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = context.getString(R.string.kb_error_failed_to_load_questions)
                Log.e(TAG, "Failed to load bundled questions", e)
            }
        }

        private suspend fun fetchQuestionsFromServer(): List<KBQuestion> =
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val selfHostedEnabled = prefs.getBoolean(KEY_SELF_HOSTED_ENABLED, false)
                val serverIP = prefs.getString(KEY_PRIMARY_SERVER_IP, "") ?: ""

                if (!selfHostedEnabled || serverIP.isEmpty()) {
                    throw IOException(context.getString(R.string.kb_error_server_not_configured))
                }

                val url = "http://$serverIP:$DEFAULT_PORT/api/modules/knowledge-bowl/download"
                Log.d(TAG, "Fetching questions from: $url")

                val request =
                    Request.Builder()
                        .url(url)
                        .post(ByteArray(0).toRequestBody())
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(context.getString(R.string.kb_error_server_returned, response.code))
                    }

                    val body =
                        response.body?.string()
                            ?: throw IOException(context.getString(R.string.kb_error_empty_response))
                    val moduleContent = json.decodeFromString<ModuleContent>(body)

                    // Extract module features
                    moduleContent.features?.let {
                        _moduleFeatures.value = it
                    }

                    // Extract all questions from all domains
                    moduleContent.domains.flatMap { it.questions }
                }
            }

        // MARK: - Study Mode Selection

        /**
         * Get questions for a specific study mode.
         *
         * @param mode The study mode to select questions for
         * @return List of questions appropriate for the mode
         */
        fun questionsForMode(mode: KBStudyMode): List<KBQuestion> {
            val questions = _allQuestions.value
            if (questions.isEmpty()) return emptyList()

            return when (mode) {
                KBStudyMode.DIAGNOSTIC -> balancedSelection(mode.defaultQuestionCount)
                KBStudyMode.TARGETED -> targetedSelection(mode.defaultQuestionCount)
                KBStudyMode.BREADTH -> balancedSelection(mode.defaultQuestionCount)
                KBStudyMode.SPEED -> speedSelection(mode.defaultQuestionCount)
                KBStudyMode.COMPETITION, KBStudyMode.TEAM -> balancedSelection(mode.defaultQuestionCount)
            }
        }

        /**
         * Select questions balanced across all domains.
         *
         * @param count Total number of questions to select
         * @return Balanced selection of questions
         */
        fun balancedSelection(count: Int): List<KBQuestion> {
            val questions = _allQuestions.value
            if (questions.isEmpty()) return emptyList()

            val selected = mutableListOf<KBQuestion>()
            val byDomain = questions.groupBy { it.domain }
            val domains = byDomain.keys.toList()

            if (domains.isEmpty()) return emptyList()

            val questionsPerDomain = maxOf(1, count / domains.size)

            // Select from each domain
            for (domain in domains) {
                val domainQuestions = byDomain[domain]?.shuffled() ?: continue
                selected.addAll(domainQuestions.take(questionsPerDomain))
            }

            // Fill remaining slots randomly
            val remaining = count - selected.count()
            if (remaining > 0) {
                val selectedIds = selected.map { it.id }.toSet()
                val unused = questions.filter { it.id !in selectedIds }.shuffled()
                selected.addAll(unused.take(remaining))
            }

            return selected.shuffled().take(count)
        }

        /**
         * Select questions focusing on weak domains.
         *
         * Uses stats manager to identify domains with lower accuracy
         * and prioritizes questions from those domains.
         *
         * @param count Total number of questions to select
         * @return Questions prioritizing weak areas
         */
        private fun targetedSelection(count: Int): List<KBQuestion> {
            val questions = _allQuestions.value
            if (questions.isEmpty()) return emptyList()

            // Get weak domains from stats
            val weakDomains = statsManager.getWeakDomains(limit = 5).map { it.first }

            if (weakDomains.isEmpty()) {
                // No stats yet, fall back to random
                return questions.shuffled().take(count)
            }

            // Prioritize weak domain questions (70%) + random (30%)
            val weakDomainCount = (count * 0.7).toInt()
            val randomCount = count - weakDomainCount

            val weakQuestions =
                questions
                    .filter { weakDomains.contains(it.domain) }
                    .shuffled()
                    .take(weakDomainCount)

            val weakIds = weakQuestions.map { it.id }.toSet()
            val randomQuestions =
                questions
                    .filter { it.id !in weakIds }
                    .shuffled()
                    .take(randomCount)

            return (weakQuestions + randomQuestions).shuffled()
        }

        /**
         * Select easier questions for speed drills.
         *
         * Prioritizes lower difficulty questions for fast-paced practice.
         *
         * @param count Total number of questions to select
         * @return Easier questions for speed practice
         */
        private fun speedSelection(count: Int): List<KBQuestion> {
            val questions = _allQuestions.value
            if (questions.isEmpty()) return emptyList()

            // Prioritize easier questions (Overview, Foundational, Intermediate)
            val easyQuestions =
                questions.filter {
                    it.difficulty in
                        listOf(
                            KBDifficulty.OVERVIEW,
                            KBDifficulty.FOUNDATIONAL,
                            KBDifficulty.INTERMEDIATE,
                        )
                }

            return if (easyQuestions.size >= count) {
                easyQuestions.shuffled().take(count)
            } else {
                // Not enough easy questions, fill with harder ones
                val remaining = count - easyQuestions.size
                val harderQuestions =
                    questions
                        .filter { it !in easyQuestions }
                        .shuffled()
                        .take(remaining)
                (easyQuestions + harderQuestions).shuffled()
            }
        }

        // MARK: - Filtering

        /**
         * Get questions filtered by domain.
         *
         * @param domain The domain to filter by
         * @return Questions in the specified domain
         */
        fun questionsForDomain(domain: KBDomain): List<KBQuestion> = _allQuestions.value.filter { it.domain == domain }

        /**
         * Get question count by domain.
         *
         * @return Map of domain to question count
         */
        val questionsByDomain: Map<KBDomain, Int>
            get() = _allQuestions.value.groupingBy { it.domain }.eachCount()

        /**
         * Total number of questions available.
         */
        val totalQuestionCount: Int
            get() = _allQuestions.value.size
    }

// MARK: - Server Response Models

/**
 * Module content response from the server.
 */
@Serializable
private data class ModuleContent(
    val domains: List<DomainContent> = emptyList(),
    val features: KBModuleFeatures? = null,
)

/**
 * Domain content within the module response.
 */
@Serializable
private data class DomainContent(
    val id: String,
    val name: String,
    val questions: List<KBQuestion> = emptyList(),
)
