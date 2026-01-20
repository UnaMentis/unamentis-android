package com.unamentis.modules.knowledgebowl.ui.written

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.ui.theme.KBColors
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.KBTimerState
import com.unamentis.modules.knowledgebowl.ui.theme.color
import java.util.Locale

/**
 * Knowledge Bowl Written Session Screen.
 *
 * Displays MCQ practice questions with timer and score tracking.
 */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBWrittenSessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: KBWrittenSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentQuestionIndex by viewModel.currentQuestionIndex.collectAsState()
    val selectedAnswer by viewModel.selectedAnswer.collectAsState()
    val showingFeedback by viewModel.showingFeedback.collectAsState()
    val lastAnswerCorrect by viewModel.lastAnswerCorrect.collectAsState()
    val remainingTime by viewModel.remainingTime.collectAsState()
    val correctCount by viewModel.correctCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Written Practice") },
                actions = {
                    if (uiState.state == WrittenSessionState.IN_PROGRESS) {
                        TextButton(onClick = { viewModel.endSession() }) {
                            Text(
                                "End",
                                color = KBTheme.focusArea(),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = KBTheme.bgSecondary(),
                    ),
            )
        },
        containerColor = KBTheme.bgPrimary(),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Session header (timer, progress, score)
            if (uiState.state != WrittenSessionState.NOT_STARTED &&
                uiState.state != WrittenSessionState.COMPLETED &&
                uiState.state != WrittenSessionState.EXPIRED
            ) {
                SessionHeader(
                    remainingTime = remainingTime,
                    timerState = viewModel.timerState,
                    progress = viewModel.progress,
                    questionNumber = currentQuestionIndex + 1,
                    totalQuestions = viewModel.questions.size,
                    correctCount = correctCount,
                    showTimer = viewModel.config.timeLimitSeconds != null,
                )
            }

            // Content based on state
            when (uiState.state) {
                WrittenSessionState.NOT_STARTED ->
                    StartScreen(
                        questions = viewModel.questions,
                        config = viewModel.config,
                        onStart = { viewModel.startSession() },
                    )
                WrittenSessionState.IN_PROGRESS,
                WrittenSessionState.PAUSED,
                WrittenSessionState.REVIEWING,
                ->
                    QuestionContent(
                        question = viewModel.currentQuestion,
                        selectedAnswer = selectedAnswer,
                        showingFeedback = showingFeedback,
                        isLastQuestion = viewModel.isLastQuestion,
                        onSelectAnswer = { viewModel.selectAnswer(it) },
                        onSubmit = { viewModel.submitAnswer() },
                        onNext = { viewModel.nextQuestion() },
                    )
                WrittenSessionState.COMPLETED,
                WrittenSessionState.EXPIRED,
                ->
                    SummaryScreen(
                        correctCount = correctCount,
                        totalQuestions = currentQuestionIndex + 1,
                        totalPoints = viewModel.totalPoints.collectAsState().value,
                        accuracy = viewModel.accuracy,
                        duration = viewModel.sessionDuration,
                        isExpired = uiState.state == WrittenSessionState.EXPIRED,
                        onDone = onNavigateBack,
                    )
            }
        }
    }
}

@Composable
private fun SessionHeader(
    remainingTime: Double,
    timerState: KBTimerState,
    progress: Float,
    questionNumber: Int,
    totalQuestions: Int,
    correctCount: Int,
    showTimer: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KBTheme.bgSecondary())
                .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Timer (if applicable)
        if (showTimer) {
            TimerDisplay(
                remainingTime = remainingTime,
                timerState = timerState,
            )
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(4.dp),
            color = KBTheme.mastered(),
            trackColor = KBTheme.border(),
        )

        // Question counter and score
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Question $questionNumber of $totalQuestions",
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.textSecondary(),
            )

            Text(
                text = "$correctCount correct",
                style = MaterialTheme.typography.bodySmall,
                color = KBTheme.mastered(),
            )
        }
    }
}

@Composable
private fun TimerDisplay(
    remainingTime: Double,
    timerState: KBTimerState,
) {
    val timerColor = timerState.color()
    val pulseSpeed = timerState.pulseSpeedMs

    val scale =
        if (pulseSpeed != null) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(pulseSpeed.toInt()),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulse",
            )
            pulseScale
        } else {
            1f
        }

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = timerColor,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTime(remainingTime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = timerColor,
            modifier = Modifier.scale(scale),
        )
    }
}

@Composable
private fun StartScreen(
    questions: List<KBQuestion>,
    config: com.unamentis.modules.knowledgebowl.data.model.KBSessionConfig,
    onStart: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = KBTheme.mastered(),
            modifier = Modifier.size(60.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Written Round Practice",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConfigRow(
                    icon = Icons.Default.Edit,
                    label = "Questions",
                    value = "${questions.size}",
                )
                config.timeLimitSeconds?.let { seconds ->
                    ConfigRow(
                        icon = Icons.Default.Schedule,
                        label = "Time Limit",
                        value = formatTime(seconds.toDouble()),
                    )
                }
                ConfigRow(
                    icon = Icons.Default.Star,
                    label = "Region",
                    value = config.region.displayName,
                )
                ConfigRow(
                    icon = Icons.Default.Star,
                    label = "Points",
                    value = "${config.region.config.writtenPointsPerCorrect} per correct",
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "Start Practice",
                modifier = Modifier.padding(vertical = 8.dp),
                fontWeight = FontWeight.Bold,
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
            tint = KBTheme.textSecondary(),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = KBTheme.textSecondary(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = KBTheme.textPrimary(),
        )
    }
}

@Composable
private fun QuestionContent(
    question: KBQuestion?,
    selectedAnswer: Int?,
    showingFeedback: Boolean,
    isLastQuestion: Boolean,
    onSelectAnswer: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    if (question == null) return

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Question card
            QuestionCard(question = question)

            // MCQ options
            MCQOptions(
                question = question,
                selectedAnswer = selectedAnswer,
                showingFeedback = showingFeedback,
                onSelectAnswer = onSelectAnswer,
            )
        }

        // Submit button area
        SubmitButtonArea(
            showingFeedback = showingFeedback,
            selectedAnswer = selectedAnswer,
            isLastQuestion = isLastQuestion,
            onSubmit = onSubmit,
            onNext = onNext,
        )
    }
}

@Composable
private fun QuestionCard(question: KBQuestion) {
    val domainColor = question.domain.color()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        color = domainColor.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Domain and difficulty row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = question.domain.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = domainColor,
                    )
                }

                Text(
                    text = question.difficulty.displayName,
                    style = MaterialTheme.typography.labelSmall,
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

            // Question text
            Text(
                text = question.text,
                style = MaterialTheme.typography.titleMedium,
                color = KBTheme.textPrimary(),
            )
        }
    }
}

@Composable
private fun MCQOptions(
    question: KBQuestion,
    selectedAnswer: Int?,
    showingFeedback: Boolean,
    onSelectAnswer: (Int) -> Unit,
) {
    val options = question.mcqOptions ?: return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEachIndexed { index, option ->
            MCQOptionButton(
                index = index,
                option = option,
                correctAnswer = question.answer.primary,
                isSelected = selectedAnswer == index,
                showingFeedback = showingFeedback,
                onClick = { onSelectAnswer(index) },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun MCQOptionButton(
    index: Int,
    option: String,
    correctAnswer: String,
    isSelected: Boolean,
    showingFeedback: Boolean,
    onClick: () -> Unit,
) {
    val isCorrect = option.equals(correctAnswer, ignoreCase = true)

    val backgroundColor by animateColorAsState(
        targetValue =
            when {
                showingFeedback && isCorrect -> KBTheme.mastered().copy(alpha = 0.2f)
                showingFeedback && isSelected -> KBTheme.focusArea().copy(alpha = 0.2f)
                isSelected -> KBTheme.intermediate().copy(alpha = 0.2f)
                else -> KBTheme.bgSecondary()
            },
        label = "bg",
    )

    val borderColor by animateColorAsState(
        targetValue =
            when {
                showingFeedback && isCorrect -> KBTheme.mastered()
                showingFeedback && isSelected -> KBTheme.focusArea()
                isSelected -> KBTheme.intermediate()
                else -> KBTheme.border()
            },
        label = "border",
    )

    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val letter = letters.getOrNull(index)?.toString() ?: "?"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(enabled = !showingFeedback, onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Letter circle
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected || (showingFeedback && isCorrect)) {
                            borderColor
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color =
                    if (isSelected || (showingFeedback && isCorrect)) {
                        Color.White
                    } else {
                        KBTheme.textSecondary()
                    },
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Option text
        Text(
            text = option,
            style = MaterialTheme.typography.bodyLarge,
            color = KBTheme.textPrimary(),
            modifier = Modifier.weight(1f),
        )

        // Result indicator
        if (showingFeedback) {
            if (isCorrect) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Correct",
                    tint = KBTheme.mastered(),
                )
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Incorrect",
                    tint = KBTheme.focusArea(),
                )
            }
        }
    }
}

@Composable
private fun SubmitButtonArea(
    showingFeedback: Boolean,
    selectedAnswer: Int?,
    isLastQuestion: Boolean,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KBTheme.bgPrimary()),
    ) {
        Divider()

        Box(
            modifier = Modifier.padding(16.dp),
        ) {
            if (showingFeedback) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = KBTheme.mastered()),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = if (isLastQuestion) "See Results" else "Next Question",
                        modifier = Modifier.padding(vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                    )
                }
            } else {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedAnswer != null,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = KBTheme.intermediate(),
                            disabledContainerColor = KBTheme.border(),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Submit Answer",
                        modifier = Modifier.padding(vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SummaryScreen(
    correctCount: Int,
    totalQuestions: Int,
    totalPoints: Int,
    accuracy: Float,
    duration: Double,
    isExpired: Boolean,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Result icon
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = if (accuracy >= 0.7f) KBColors.gold else KBTheme.intermediate(),
            modifier = Modifier.size(60.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isExpired) "Time's Up!" else "Session Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Score card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryRow(label = "Score", value = "$correctCount/$totalQuestions")
                SummaryRow(label = "Accuracy", value = "${(accuracy * 100).toInt()}%")
                SummaryRow(label = "Points", value = "$totalPoints")
                SummaryRow(label = "Time", value = formatTime(duration))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Accuracy meter
        AccuracyMeter(accuracy = accuracy)

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = KBTheme.mastered()),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "Done",
                modifier = Modifier.padding(vertical = 8.dp),
                fontWeight = FontWeight.Bold,
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
    ) {
        Text(
            text = label,
            color = KBTheme.textSecondary(),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )
    }
}

@Composable
private fun AccuracyMeter(accuracy: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Accuracy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp),
        ) {
            // Background circle
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = KBColors.borderLight,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx()),
                )
            }

            // Progress circle
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = if (accuracy >= 0.7f) KBColors.masteredLight else KBColors.beginnerLight,
                    startAngle = -90f,
                    sweepAngle = accuracy * 360f,
                    useCenter = false,
                    style =
                        androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 12.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                )
            }

            // Percentage text
            Text(
                text = "${(accuracy * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )
        }
    }
}

private fun formatTime(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
