package com.unamentis.modules.knowledgebowl.ui.oral

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.util.safeProgress

/**
 * Knowledge Bowl oral practice session screen.
 *
 * Provides voice-first practice with TTS question reading,
 * conference timer, and STT answer capture.
 */
@Suppress("LongMethod")
@Composable
fun KBOralSessionScreen(
    questions: List<KBQuestion>,
    config: KBSessionConfig,
    onNavigateBack: () -> Unit,
    viewModel: KBOralSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentIndex by viewModel.currentQuestionIndex.collectAsState()
    val correctCount by viewModel.correctCount.collectAsState()

    // Initialize session
    LaunchedEffect(questions, config) {
        viewModel.initialize(questions, config)
        viewModel.prepareServices()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(KBTheme.bgPrimary()),
    ) {
        // Header
        SessionHeader(
            currentIndex = currentIndex,
            totalQuestions = questions.size,
            correctCount = correctCount,
            progress = viewModel.progress,
            state = state,
            onEndSession = {
                viewModel.endSession()
            },
        )

        // Main content
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
            },
            label = "oral_state_transition",
        ) { sessionState ->
            when (sessionState) {
                OralSessionState.NOT_STARTED -> StartScreen(viewModel = viewModel)
                OralSessionState.READING_QUESTION -> QuestionReadingScreen(viewModel = viewModel)
                OralSessionState.CONFERENCE_TIME -> ConferenceScreen(viewModel = viewModel)
                OralSessionState.LISTENING_FOR_ANSWER -> ListeningScreen(viewModel = viewModel)
                OralSessionState.SHOWING_FEEDBACK -> FeedbackScreen(viewModel = viewModel)
                OralSessionState.COMPLETED -> SummaryScreen(viewModel = viewModel, onDone = onNavigateBack)
            }
        }
    }
}

// region Header

@Composable
private fun SessionHeader(
    currentIndex: Int,
    totalQuestions: Int,
    correctCount: Int,
    progress: Float,
    state: OralSessionState,
    onEndSession: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KBTheme.bgSecondary())
                .padding(vertical = 12.dp),
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { safeProgress(progress) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            color = KBTheme.mastered(),
            trackColor = KBTheme.border(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.kb_question_of, currentIndex + 1, totalQuestions),
                style = IOSTypography.body,
                color = KBTheme.textSecondary(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.kb_correct_count_label, correctCount),
                    style = IOSTypography.body,
                    color = KBTheme.mastered(),
                )

                if (state != OralSessionState.NOT_STARTED &&
                    state != OralSessionState.COMPLETED
                ) {
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onEndSession) {
                        Text(
                            text = stringResource(R.string.end),
                            color = KBTheme.focusArea(),
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Start Screen

@Composable
private fun StartScreen(viewModel: KBOralSessionViewModel) {
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val sttError by viewModel.sttError.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = KBTheme.mastered(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.kb_oral_practice),
            style = IOSTypography.title2,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.kb_oral_instructions),
            style = IOSTypography.body,
            color = KBTheme.textSecondary(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Config card
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = KBTheme.bgSecondary(),
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ConfigRow(
                    icon = Icons.Default.Pin,
                    label = stringResource(R.string.kb_questions),
                    value = "${viewModel.questions.size}",
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigRow(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.kb_conference_time),
                    value = "${viewModel.regionalConfig.conferenceTime.toInt()}s",
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigRow(
                    icon = Icons.Default.Pin,
                    label = stringResource(R.string.kb_region),
                    value = viewModel.regionalConfig.region.displayName,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val perCorrectText = stringResource(R.string.kb_per_correct)
                ConfigRow(
                    icon = Icons.Default.Star,
                    label = stringResource(R.string.kb_points),
                    value = "${viewModel.regionalConfig.oralPointsPerCorrect} $perCorrectText",
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfigRow(
                    icon = Icons.Default.People,
                    label = stringResource(R.string.kb_verbal_conferring),
                    value =
                        if (viewModel.regionalConfig.verbalConferringAllowed) {
                            stringResource(R.string.kb_allowed)
                        } else {
                            stringResource(R.string.kb_silent_only)
                        },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Permission warning
        if (!hasPermissions) {
            Text(
                text = stringResource(R.string.kb_mic_permission_required),
                style = IOSTypography.caption,
                color = KBTheme.focusArea(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Error message
        sttError?.let { error ->
            Text(
                text = error,
                style = IOSTypography.caption,
                color = KBTheme.focusArea(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.startSession() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_start_practice),
                style = IOSTypography.headline,
            )
        }
    }
}

@Composable
private fun ConfigRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = KBTheme.textSecondary(),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = IOSTypography.body,
            color = KBTheme.textSecondary(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = IOSTypography.body,
            color = KBTheme.textPrimary(),
        )
    }
}

// endregion

// region Question Reading Screen

@Suppress("MagicNumber")
@Composable
private fun QuestionReadingScreen(viewModel: KBOralSessionViewModel) {
    val ttsProgress by viewModel.ttsProgress.collectAsState()

    // Pulsing animation for speaker icon
    val infiniteTransition = rememberInfiniteTransition(label = "speaking_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "speaker_scale",
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Speaking indicator
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = stringResource(R.string.cd_kb_speaking),
            modifier =
                Modifier
                    .size(80.dp)
                    .scale(scale),
            tint = KBTheme.intermediate(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.kb_reading_question),
            style = IOSTypography.title2,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Question card
        viewModel.currentQuestion?.let { question ->
            QuestionCard(question = question)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TTS progress
        LinearProgressIndicator(
            progress = { safeProgress(ttsProgress) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            color = KBTheme.intermediate(),
            trackColor = KBTheme.border(),
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

// endregion

// region Conference Screen

@Suppress("MagicNumber")
@Composable
private fun ConferenceScreen(viewModel: KBOralSessionViewModel) {
    val timeRemaining by viewModel.conferenceTimeRemaining.collectAsState()
    val conferenceProgress by viewModel.conferenceProgress.collectAsState()

    val timerColor by animateColorAsState(
        targetValue = if (timeRemaining < 5) KBTheme.focusArea() else KBTheme.mastered(),
        label = "timer_color",
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Conference timer
        Box(contentAlignment = Alignment.Center) {
            // Background circle
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(150.dp),
                color = KBTheme.border(),
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round,
            )

            // Progress circle
            CircularProgressIndicator(
                progress = { safeProgress(conferenceProgress.toFloat()) },
                modifier = Modifier.size(150.dp),
                color = timerColor,
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round,
            )

            // Timer text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${timeRemaining.toInt()}",
                    style = IOSTypography.largeTitle,
                    color =
                        if (timeRemaining < 5) {
                            KBTheme.focusArea()
                        } else {
                            KBTheme.textPrimary()
                        },
                )
                Text(
                    text = stringResource(R.string.kb_seconds),
                    style = IOSTypography.caption,
                    color = KBTheme.textSecondary(),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.kb_conference_time),
            style = IOSTypography.title2,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text =
                if (viewModel.regionalConfig.verbalConferringAllowed) {
                    stringResource(R.string.kb_discuss_with_team)
                } else {
                    stringResource(R.string.kb_silent_conferring)
                },
            style = IOSTypography.body,
            color = KBTheme.textSecondary(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Compact question card
        viewModel.currentQuestion?.let { question ->
            QuestionCardCompact(question = question)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Skip button
        Button(
            onClick = { viewModel.skipConference() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.intermediate(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_ready_to_answer),
                style = IOSTypography.headline,
            )
        }
    }
}

// endregion

// region Listening Screen

@Suppress("MagicNumber")
@Composable
private fun ListeningScreen(viewModel: KBOralSessionViewModel) {
    val transcript by viewModel.transcript.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val sttError by viewModel.sttError.collectAsState()

    // Pulsing animation for listening
    val infiniteTransition = rememberInfiniteTransition(label = "listening_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "mic_scale",
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Listening indicator
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .scale(if (isListening) scale else 1f)
                    .clip(CircleShape)
                    .background(
                        if (isListening) {
                            KBTheme.mastered().copy(alpha = 0.1f)
                        } else {
                            KBTheme.intermediate().copy(alpha = 0.1f)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector =
                    if (isListening) {
                        Icons.Default.GraphicEq
                    } else {
                        Icons.Default.Mic
                    },
                contentDescription =
                    if (isListening) {
                        stringResource(R.string.cd_kb_listening)
                    } else {
                        stringResource(R.string.cd_kb_tap_to_speak)
                    },
                modifier = Modifier.size(60.dp),
                tint = if (isListening) KBTheme.mastered() else KBTheme.intermediate(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                if (isListening) {
                    stringResource(R.string.kb_listening)
                } else {
                    stringResource(R.string.kb_tap_to_speak)
                },
            style = IOSTypography.title2,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error display
        sttError?.let { error ->
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = KBTheme.focusArea().copy(alpha = 0.1f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    style = IOSTypography.body,
                    color = KBTheme.focusArea(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Transcript display
        if (transcript.isNotEmpty()) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = KBTheme.bgSecondary(),
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = transcript,
                    style = IOSTypography.headline,
                    color = KBTheme.textPrimary(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Compact question card
        viewModel.currentQuestion?.let { question ->
            QuestionCardCompact(question = question)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = { viewModel.toggleListening() },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (isListening) {
                                KBTheme.focusArea()
                            } else {
                                KBTheme.mastered()
                            },
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (isListening) {
                            stringResource(R.string.stop)
                        } else {
                            stringResource(R.string.kb_start_listening)
                        },
                    style = IOSTypography.headline,
                )
            }

            if (transcript.isNotEmpty() && !isListening) {
                Button(
                    onClick = { viewModel.submitAnswer() },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = KBTheme.intermediate(),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.submit),
                        style = IOSTypography.headline,
                    )
                }
            }
        }
    }
}

// endregion

// region Feedback Screen

@Composable
private fun FeedbackScreen(viewModel: KBOralSessionViewModel) {
    val lastAnswerCorrect by viewModel.lastAnswerCorrect.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val isCorrect = lastAnswerCorrect == true

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Result icon
        Icon(
            imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            modifier =
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCorrect) {
                            KBTheme.mastered().copy(alpha = 0.1f)
                        } else {
                            KBTheme.focusArea().copy(alpha = 0.1f)
                        },
                    )
                    .padding(16.dp),
            tint = if (isCorrect) KBTheme.mastered() else KBTheme.focusArea(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                if (isCorrect) {
                    stringResource(R.string.kb_correct)
                } else {
                    stringResource(R.string.kb_incorrect)
                },
            style = IOSTypography.title2,
            color = if (isCorrect) KBTheme.mastered() else KBTheme.focusArea(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Show correct answer if wrong
        if (!isCorrect) {
            viewModel.currentQuestion?.let { question ->
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = KBTheme.bgSecondary(),
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.kb_correct_answer),
                            style = IOSTypography.body,
                            color = KBTheme.textSecondary(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = question.answer.primary,
                            style = IOSTypography.title2,
                            color = KBTheme.textPrimary(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // User's answer
        if (transcript.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.kb_your_answer),
                    style = IOSTypography.body,
                    color = KBTheme.textSecondary(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transcript,
                    style = IOSTypography.body,
                    color = KBTheme.textSecondary(),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Next button
        Button(
            onClick = { viewModel.nextQuestion() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text =
                    if (viewModel.isLastQuestion) {
                        stringResource(R.string.kb_see_results)
                    } else {
                        stringResource(R.string.kb_next_question)
                    },
                style = IOSTypography.headline,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
            )
        }
    }
}

// endregion

// region Summary Screen

@Suppress("MagicNumber")
@Composable
private fun SummaryScreen(
    viewModel: KBOralSessionViewModel,
    onDone: () -> Unit,
) {
    val correctCount by viewModel.correctCount.collectAsState()
    val totalPoints by viewModel.totalPoints.collectAsState()
    val accuracy = viewModel.sessionAccuracy
    val duration = viewModel.sessionDuration

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Result icon
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = if (accuracy >= 0.7f) KBTheme.gold() else KBTheme.intermediate(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.kb_session_complete),
            style = IOSTypography.title2,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Score card
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = KBTheme.bgSecondary(),
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SummaryRow(
                    label = stringResource(R.string.kb_score),
                    value = "$correctCount/${viewModel.questions.size}",
                )
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(
                    label = stringResource(R.string.kb_accuracy),
                    value = String.format("%.0f%%", accuracy * 100),
                )
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(
                    label = stringResource(R.string.kb_points),
                    value = "$totalPoints",
                )
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(
                    label = stringResource(R.string.kb_time),
                    value = formatDuration(duration),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Accuracy meter
        AccuracyMeter(accuracy = accuracy)

        Spacer(modifier = Modifier.weight(1f))

        // Done button
        Button(
            onClick = onDone,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.done),
                style = IOSTypography.headline,
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = IOSTypography.body,
            color = KBTheme.textSecondary(),
        )
        Text(
            text = value,
            style = IOSTypography.headline,
            color = KBTheme.textPrimary(),
        )
    }
}

@Suppress("MagicNumber")
@Composable
private fun AccuracyMeter(accuracy: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.kb_accuracy),
            style = IOSTypography.headline,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(120.dp),
                color = KBTheme.border(),
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round,
            )

            CircularProgressIndicator(
                progress = { safeProgress(accuracy) },
                modifier = Modifier.size(120.dp),
                color = if (accuracy >= 0.7f) KBTheme.mastered() else KBTheme.beginner(),
                strokeWidth = 12.dp,
                strokeCap = StrokeCap.Round,
            )

            Text(
                text = String.format("%.0f%%", accuracy * 100),
                style = IOSTypography.title2,
                color = KBTheme.textPrimary(),
            )
        }
    }
}

// endregion

// region Question Cards

@Composable
private fun QuestionCard(question: KBQuestion) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary(),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Domain indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(question.domain.color()),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = question.domain.displayName,
                        style = IOSTypography.caption2,
                        color = question.domain.color(),
                    )
                }

                Text(
                    text = question.difficulty.displayName,
                    style = IOSTypography.caption2,
                    color = KBTheme.textSecondary(),
                    modifier =
                        Modifier
                            .background(
                                KBTheme.bgPrimary(),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question text
            Text(
                text = question.text,
                style = IOSTypography.headline,
                color = KBTheme.textPrimary(),
            )
        }
    }
}

@Composable
private fun QuestionCardCompact(question: KBQuestion) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = KBTheme.bgSecondary().copy(alpha = 0.5f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(question.domain.color()),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = question.domain.displayName,
                    style = IOSTypography.caption2,
                    color = question.domain.color(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = question.text,
                style = IOSTypography.body,
                color = KBTheme.textSecondary(),
                maxLines = 2,
            )
        }
    }
}

// endregion

// region Helpers

@Suppress("MagicNumber")
private fun formatDuration(seconds: Float): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format("%d:%02d", mins, secs)
}

// endregion
