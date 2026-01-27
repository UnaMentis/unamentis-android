package com.unamentis.modules.knowledgebowl.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.core.engine.KBQuestionEngine
import com.unamentis.modules.knowledgebowl.core.match.BuzzResult
import com.unamentis.modules.knowledgebowl.core.match.KBMatchConfig
import com.unamentis.modules.knowledgebowl.core.match.KBMatchEngine
import com.unamentis.modules.knowledgebowl.core.match.KBMatchSummary
import com.unamentis.modules.knowledgebowl.core.match.KBTeam
import com.unamentis.modules.knowledgebowl.core.match.MatchFormat
import com.unamentis.modules.knowledgebowl.core.match.MatchPhase
import com.unamentis.modules.knowledgebowl.core.match.OralProgress
import com.unamentis.modules.knowledgebowl.core.match.OpponentStrength
import com.unamentis.modules.knowledgebowl.core.validation.KBAnswerValidator
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the match simulation screen.
 */
data class MatchSimulationUiState(
    // Setup state
    val isSetupComplete: Boolean = false,
    val selectedRegion: KBRegion = KBRegion.DEFAULT,
    val selectedFormat: MatchFormat = MatchFormat.QUICK_MATCH,
    val selectedOpponentStrength: OpponentStrength = OpponentStrength.INTERMEDIATE,
    val playerTeamName: String = "Your Team",

    // Match state
    val phase: MatchPhase = MatchPhase.NotStarted,
    val teams: List<KBTeam> = emptyList(),
    val currentQuestion: KBQuestion? = null,
    val writtenProgress: Pair<Int, Int> = 0 to 0,
    val oralProgress: OralProgress = OralProgress(),

    // Answer state
    val selectedAnswer: Int? = null,
    val showFeedback: Boolean = false,
    val lastAnswerCorrect: Boolean? = null,
    val responseStartTime: Long = 0L,

    // Buzz simulation
    val buzzResult: BuzzResult? = null,
    val isWaitingForBuzz: Boolean = false,
    val playerBuzzed: Boolean = false,
    val opponentAnswer: String? = null,
    val opponentAnswerCorrect: Boolean? = null,
    val isReboundOpportunity: Boolean = false,

    // Results
    val matchSummary: KBMatchSummary? = null,

    // Loading/error
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel for the match simulation screen.
 */
@HiltViewModel
class KBMatchSimulationViewModel @Inject constructor(
    private val matchEngine: KBMatchEngine,
    private val questionEngine: KBQuestionEngine,
    private val answerValidator: KBAnswerValidator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MatchSimulationUiState())
    val uiState: StateFlow<MatchSimulationUiState> = _uiState.asStateFlow()

    init {
        // Observe engine state
        viewModelScope.launch {
            matchEngine.currentPhase.collect { phase ->
                _uiState.update { it.copy(phase = phase) }
                updateCurrentQuestion()
            }
        }

        viewModelScope.launch {
            matchEngine.teams.collect { teams ->
                _uiState.update { it.copy(teams = teams) }
            }
        }
    }

    // MARK: - Setup Actions

    fun selectRegion(region: KBRegion) {
        _uiState.update { it.copy(selectedRegion = region) }
    }

    fun selectFormat(format: MatchFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun selectOpponentStrength(strength: OpponentStrength) {
        _uiState.update { it.copy(selectedOpponentStrength = strength) }
    }

    fun setPlayerTeamName(name: String) {
        _uiState.update { it.copy(playerTeamName = name) }
    }

    fun startMatch() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load questions
                questionEngine.loadBundledQuestions()
                val questions = questionEngine.getAllQuestions()

                if (questions.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "No questions available")
                    }
                    return@launch
                }

                val state = _uiState.value
                val config = KBMatchConfig(
                    region = state.selectedRegion,
                    matchFormat = state.selectedFormat,
                    opponentStrengths = listOf(
                        state.selectedOpponentStrength,
                        state.selectedOpponentStrength,
                    ),
                    enablePracticeMode = true,
                )

                // Setup and start match
                matchEngine.setupMatch(
                    config = config,
                    questions = questions,
                    playerTeamName = state.playerTeamName,
                )
                matchEngine.startMatch()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSetupComplete = true,
                        responseStartTime = System.currentTimeMillis(),
                    )
                }
                updateProgress()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to start match")
                }
            }
        }
    }

    // MARK: - Written Round Actions

    fun selectWrittenAnswer(optionIndex: Int) {
        _uiState.update { it.copy(selectedAnswer = optionIndex) }
    }

    fun submitWrittenAnswer() {
        viewModelScope.launch {
            val state = _uiState.value
            val question = state.currentQuestion ?: return@launch
            val selectedIndex = state.selectedAnswer ?: return@launch

            val selectedOption = question.mcqOptions?.getOrNull(selectedIndex)
            val isCorrect = selectedOption?.let {
                answerValidator.validate(it, question.answer).isCorrect
            } ?: false

            val responseTime = (System.currentTimeMillis() - state.responseStartTime) / 1000.0

            _uiState.update {
                it.copy(
                    showFeedback = true,
                    lastAnswerCorrect = isCorrect,
                )
            }

            // Show feedback briefly
            delay(1500)

            // Submit to engine
            matchEngine.submitWrittenAnswer(isCorrect, responseTime)

            // Reset for next question
            _uiState.update {
                it.copy(
                    selectedAnswer = null,
                    showFeedback = false,
                    lastAnswerCorrect = null,
                    responseStartTime = System.currentTimeMillis(),
                )
            }
            updateProgress()
            updateCurrentQuestion()
        }
    }

    fun startOralRounds() {
        viewModelScope.launch {
            matchEngine.startOralRounds()
            _uiState.update { it.copy(responseStartTime = System.currentTimeMillis()) }
            updateProgress()
            updateCurrentQuestion()
            startBuzzSimulation()
        }
    }

    // MARK: - Oral Round Actions

    private fun startBuzzSimulation() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWaitingForBuzz = true,
                    buzzResult = null,
                    playerBuzzed = false,
                    opponentAnswer = null,
                    opponentAnswerCorrect = null,
                    isReboundOpportunity = false,
                )
            }

            // Simulate buzz delay
            delay(1000)

            val result = matchEngine.simulateBuzz()
            _uiState.update { it.copy(buzzResult = result, isWaitingForBuzz = false) }

            if (result != null && !matchEngine.isPlayerTeam(result.teamId)) {
                // Opponent buzzed first - simulate their answer
                simulateOpponentAnswer(result.teamId)
            }
        }
    }

    private suspend fun simulateOpponentAnswer(teamId: String) {
        val question = _uiState.value.currentQuestion ?: return
        val simulator = matchEngine.getOpponentSimulator(teamId) ?: return

        delay(500) // Thinking time

        val isCorrect = simulator.answerOralQuestion(question)
        val answer = simulator.getSimulatedAnswer(question, isCorrect)

        _uiState.update {
            it.copy(
                opponentAnswer = answer,
                opponentAnswerCorrect = isCorrect,
            )
        }

        delay(1000) // Show opponent's answer

        if (isCorrect) {
            // Opponent got it right - record and move on
            matchEngine.recordOralResult(teamId, wasCorrect = true, responseTime = 2.0)
            proceedToNextOralQuestion()
        } else {
            // Opponent wrong - rebound opportunity for player
            _uiState.update { it.copy(isReboundOpportunity = true) }
        }
    }

    fun playerBuzz() {
        val state = _uiState.value
        if (state.buzzResult != null && matchEngine.isPlayerTeam(state.buzzResult.teamId)) {
            _uiState.update { it.copy(playerBuzzed = true) }
        }
    }

    fun submitOralAnswer(answer: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val question = state.currentQuestion ?: return@launch

            val validationResult = answerValidator.validate(answer, question.answer)
            val responseTime = (System.currentTimeMillis() - state.responseStartTime) / 1000.0

            _uiState.update {
                it.copy(
                    showFeedback = true,
                    lastAnswerCorrect = validationResult.isCorrect,
                )
            }

            delay(1500)

            val playerTeam = state.teams.find { it.isPlayer }
            matchEngine.recordOralResult(
                answeringTeamId = playerTeam?.id,
                wasCorrect = validationResult.isCorrect,
                responseTime = responseTime,
            )

            proceedToNextOralQuestion()
        }
    }

    fun skipReboundOpportunity() {
        viewModelScope.launch {
            // Record that no one answered correctly
            matchEngine.recordOralResult(null, wasCorrect = false, responseTime = 0.0)
            proceedToNextOralQuestion()
        }
    }

    private fun proceedToNextOralQuestion() {
        _uiState.update {
            it.copy(
                showFeedback = false,
                lastAnswerCorrect = null,
                selectedAnswer = null,
                buzzResult = null,
                playerBuzzed = false,
                opponentAnswer = null,
                opponentAnswerCorrect = null,
                isReboundOpportunity = false,
                responseStartTime = System.currentTimeMillis(),
            )
        }
        updateProgress()
        updateCurrentQuestion()

        // If still in oral round, start next buzz
        if (_uiState.value.phase is MatchPhase.OralRound) {
            startBuzzSimulation()
        }
    }

    fun startNextOralRound() {
        viewModelScope.launch {
            matchEngine.startNextOralRound()
            _uiState.update { it.copy(responseStartTime = System.currentTimeMillis()) }
            updateProgress()
            updateCurrentQuestion()

            if (_uiState.value.phase is MatchPhase.OralRound) {
                startBuzzSimulation()
            }
        }
    }

    fun finishMatch() {
        val summary = matchEngine.getMatchSummary()
        _uiState.update { it.copy(matchSummary = summary) }
    }

    // MARK: - Private Helpers

    private fun updateCurrentQuestion() {
        val phase = _uiState.value.phase
        val question = when (phase) {
            is MatchPhase.WrittenRound -> matchEngine.getCurrentWrittenQuestion()
            is MatchPhase.OralRound -> matchEngine.getCurrentOralQuestion()
            else -> null
        }
        _uiState.update { it.copy(currentQuestion = question) }
    }

    private fun updateProgress() {
        val writtenProgress = matchEngine.getWrittenProgress()
        val oralProgress = matchEngine.getOralProgress()
        _uiState.update {
            it.copy(
                writtenProgress = writtenProgress,
                oralProgress = oralProgress,
            )
        }
    }

    fun reset() {
        viewModelScope.launch {
            matchEngine.reset()
            _uiState.value = MatchSimulationUiState()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
