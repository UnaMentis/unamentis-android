package com.unamentis.modules.knowledgebowl.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.core.stats.KBStatsManager
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.data.model.KBRoundType
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Knowledge Bowl Dashboard screen.
 */
@HiltViewModel
class KBDashboardViewModel
    @Inject
    constructor(
        private val questionEngine: KBQuestionEngine,
        private val statsManager: KBStatsManager,
    ) : ViewModel() {
        // Region selection
        private val _selectedRegion = MutableStateFlow(KBRegion.COLORADO)
        val selectedRegion: StateFlow<KBRegion> = _selectedRegion.asStateFlow()

        // Loading state
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        // Error state
        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        // Question counts per domain
        private val _questionsByDomain = MutableStateFlow<Map<KBDomain, Int>>(emptyMap())
        val questionsByDomain: StateFlow<Map<KBDomain, Int>> = _questionsByDomain.asStateFlow()

        // Total question count
        val totalQuestionCount: StateFlow<Int> =
            _questionsByDomain
                .combine(MutableStateFlow(Unit)) { domains, _ ->
                    domains.values.sum()
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0,
                )

        // Stats from stats manager
        val overallAccuracy: StateFlow<Float> =
            combine(
                statsManager.totalQuestionsAnswered,
                statsManager.totalCorrectAnswers,
            ) { total, correct ->
                if (total > 0) correct.toFloat() / total else 0f
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0f,
            )

        val competitionReadiness: StateFlow<Float> =
            MutableStateFlow(statsManager.competitionReadiness).asStateFlow()

        // Questions ready for session
        private var loadedQuestions: List<KBQuestion> = emptyList()

        init {
            loadQuestions()
        }

        /**
         * Load questions from the bundled JSON.
         */
        fun loadQuestions() {
            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null

                try {
                    questionEngine.loadBundledQuestions()
                    loadedQuestions = questionEngine.questions.value
                    _questionsByDomain.value = countByDomain(loadedQuestions)
                } catch (e: Exception) {
                    _error.value = e.message ?: "Failed to load questions"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Select a competition region.
         */
        fun selectRegion(region: KBRegion) {
            _selectedRegion.value = region
        }

        /**
         * Get questions for a written practice session.
         */
        fun getWrittenSessionQuestions(): Pair<List<KBQuestion>, KBSessionConfig> {
            val config =
                KBSessionConfig.quickPractice(
                    region = _selectedRegion.value,
                    roundType = KBRoundType.WRITTEN,
                    questionCount = DEFAULT_WRITTEN_COUNT,
                )
            val questions = questionEngine.selectForSession(config)
            return questions to config
        }

        /**
         * Get questions for an oral practice session.
         */
        fun getOralSessionQuestions(): Pair<List<KBQuestion>, KBSessionConfig> {
            val config =
                KBSessionConfig.quickPractice(
                    region = _selectedRegion.value,
                    roundType = KBRoundType.ORAL,
                    questionCount = DEFAULT_ORAL_COUNT,
                )
            val questions = questionEngine.selectForSession(config)
            return questions to config
        }

        /**
         * Get stats for a specific domain.
         */
        fun getDomainMastery(domain: KBDomain): Float = statsManager.mastery(domain)

        private fun countByDomain(questions: List<KBQuestion>): Map<KBDomain, Int> =
            questions.groupBy { it.domain }.mapValues { it.value.size }

        companion object {
            private const val DEFAULT_WRITTEN_COUNT = 10
            private const val DEFAULT_ORAL_COUNT = 5
        }
    }
