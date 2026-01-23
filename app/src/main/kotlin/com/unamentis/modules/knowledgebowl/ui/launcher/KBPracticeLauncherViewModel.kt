package com.unamentis.modules.knowledgebowl.ui.launcher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import com.unamentis.modules.knowledgebowl.data.remote.KBQuestionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the practice launcher sheet.
 *
 * Manages loading questions for a selected study mode and
 * preparing the session before the user starts practicing.
 */
@HiltViewModel
class KBPracticeLauncherViewModel
    @Inject
    constructor(
        private val questionService: KBQuestionService,
    ) : ViewModel() {
        companion object {
            private const val TAG = "KBPracticeLauncherVM"
        }

        // State
        private val _isLoading = MutableStateFlow(true)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _loadedQuestions = MutableStateFlow<List<KBQuestion>>(emptyList())
        val loadedQuestions: StateFlow<List<KBQuestion>> = _loadedQuestions.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _selectedMode = MutableStateFlow(KBStudyMode.DIAGNOSTIC)
        val selectedMode: StateFlow<KBStudyMode> = _selectedMode.asStateFlow()

        /**
         * Set the study mode and load questions.
         *
         * @param mode The study mode to prepare for
         */
        fun setMode(mode: KBStudyMode) {
            _selectedMode.value = mode
            loadQuestions()
        }

        /**
         * Load questions for the current mode.
         */
        fun loadQuestions() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                _loadedQuestions.value = emptyList()

                try {
                    // Ensure questions are loaded in the service
                    if (!questionService.isLoaded.value) {
                        questionService.loadQuestions()
                    }

                    // Get questions for this mode
                    val questions = questionService.questionsForMode(_selectedMode.value)

                    if (questions.isEmpty()) {
                        _errorMessage.value = "No questions available for this mode. " +
                            "Please check your connection and try again."
                    } else {
                        _loadedQuestions.value = questions
                        Log.i(TAG, "Loaded ${questions.size} questions for ${_selectedMode.value.displayName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load questions", e)
                    _errorMessage.value = "Failed to load questions: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Retry loading after an error.
         */
        fun retry() {
            loadQuestions()
        }

        /**
         * Clear loaded questions.
         */
        fun clear() {
            _loadedQuestions.value = emptyList()
            _errorMessage.value = null
            _isLoading.value = false
        }

        /**
         * Check if ready to start.
         */
        val isReady: Boolean
            get() = !_isLoading.value && _loadedQuestions.value.isNotEmpty() && _errorMessage.value == null

        /**
         * Get difficulty description for the current mode.
         */
        val difficultyDescription: String
            get() =
                when (_selectedMode.value) {
                    KBStudyMode.DIAGNOSTIC -> "All levels"
                    KBStudyMode.TARGETED -> "Varies"
                    KBStudyMode.BREADTH -> "Mixed"
                    KBStudyMode.SPEED -> "Easy to Medium"
                    KBStudyMode.COMPETITION -> "Competition level"
                    KBStudyMode.TEAM -> "Competition level"
                }

        /**
         * Get tips for the current mode.
         */
        val tipsForMode: List<String>
            get() =
                when (_selectedMode.value) {
                    KBStudyMode.DIAGNOSTIC ->
                        listOf(
                            "Answer all questions to get an accurate assessment",
                            "Don't spend too long on any single question",
                            "This helps identify your strengths and weaknesses",
                        )
                    KBStudyMode.TARGETED ->
                        listOf(
                            "Questions focus on your weaker areas",
                            "Take time to understand explanations",
                            "Review incorrect answers carefully",
                        )
                    KBStudyMode.BREADTH ->
                        listOf(
                            "Questions cover all domains evenly",
                            "Good for maintaining overall knowledge",
                            "Helps prevent forgetting less-practiced areas",
                        )
                    KBStudyMode.SPEED ->
                        listOf(
                            "Answer as quickly as possible",
                            "Each question has a target time",
                            "Builds quick recall for competitions",
                        )
                    KBStudyMode.COMPETITION ->
                        listOf(
                            "Simulates real competition conditions",
                            "Questions are timed like actual meets",
                            "Good practice before competitions",
                        )
                    KBStudyMode.TEAM ->
                        listOf(
                            "Designed for team practice",
                            "Take turns answering",
                            "Discuss strategies together",
                        )
                }
    }
