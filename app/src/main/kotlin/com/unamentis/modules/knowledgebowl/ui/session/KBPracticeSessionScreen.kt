package com.unamentis.modules.knowledgebowl.ui.session

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBDifficulty
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.data.model.KBPracticeSessionSummary
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color

/**
 * Unified practice session screen for all study modes.
 *
 * @param mode The study mode for this session
 * @param questions Questions to practice with
 * @param onComplete Called when session completes with summary
 * @param onBack Called when user wants to go back
 * @param viewModel ViewModel instance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBPracticeSessionScreen(
    mode: KBStudyMode,
    questions: List<KBQuestion>,
    onComplete: (KBPracticeSessionSummary) -> Unit,
    onBack: () -> Unit,
    viewModel: KBPracticeSessionViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val currentQuestion by viewModel.currentQuestion.collectAsState()
    val questionIndex by viewModel.questionIndex.collectAsState()
    val totalQuestions by viewModel.totalQuestions.collectAsState()
    val results by viewModel.results.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val userAnswer by viewModel.userAnswer.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }

    // Start session when screen appears
    LaunchedEffect(Unit) {
        viewModel.startSession(questions, mode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mode.displayName) },
                navigationIcon = {
                    if (sessionState != KBSessionState.Completed) {
                        IconButton(onClick = { showExitDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Progress header
            ProgressHeader(
                questionIndex = questionIndex,
                totalQuestions = totalQuestions,
                correctCount = viewModel.correctCount,
                timeRemaining = timeRemaining,
            )

            HorizontalDivider()

            // Main content based on state
            when (sessionState) {
                is KBSessionState.NotStarted -> {
                    StartingContent()
                }
                is KBSessionState.InProgress -> {
                    currentQuestion?.let { question ->
                        QuestionContent(
                            question = question,
                            userAnswer = userAnswer,
                            onAnswerChange = viewModel::updateUserAnswer,
                            onSubmit = viewModel::submitAnswer,
                            onSkip = viewModel::skipQuestion,
                            mode = mode,
                        )
                    }
                }
                is KBSessionState.ShowingAnswer -> {
                    val isCorrect = (sessionState as KBSessionState.ShowingAnswer).isCorrect
                    currentQuestion?.let { question ->
                        AnswerFeedbackContent(
                            question = question,
                            isCorrect = isCorrect,
                            lastResult = results.lastOrNull(),
                            isLastQuestion = viewModel.isLastQuestion,
                            onNext = viewModel::nextQuestion,
                            mode = mode,
                        )
                    }
                }
                is KBSessionState.Completed -> {
                    val summary = viewModel.generateSummary()
                    CompletedContent(
                        summary = summary,
                        mode = mode,
                        onDone = {
                            onComplete(summary)
                            onBack()
                        },
                    )
                }
            }
        }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.kb_exit_practice_title)) },
            text = { Text(stringResource(R.string.kb_exit_practice_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.endSessionEarly()
                        val summary = viewModel.generateSummary()
                        onComplete(summary)
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.kb_exit_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.kb_continue_practicing_button))
                }
            },
        )
    }
}

@Composable
private fun ProgressHeader(
    questionIndex: Int,
    totalQuestions: Int,
    correctCount: Int,
    timeRemaining: Int?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Question counter
        Text(
            text = stringResource(R.string.kb_question_of, questionIndex + 1, totalQuestions),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Score
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = KBTheme.mastered(),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = pluralStringResource(R.plurals.kb_correct_count, correctCount, correctCount),
                style = MaterialTheme.typography.labelMedium,
                color = KBTheme.mastered(),
            )
        }

        // Timer for speed mode
        timeRemaining?.let { time ->
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (time < 30) MaterialTheme.colorScheme.error else KBTheme.currentEvents(),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTime(time),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (time < 30) MaterialTheme.colorScheme.error else KBTheme.currentEvents(),
                )
            }
        }
    }
}

@Composable
private fun StartingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.kb_preparing_questions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuestionContent(
    question: KBQuestion,
    userAnswer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    mode: KBStudyMode,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Domain badge
        item {
            DomainBadge(
                domain = question.domain,
                subdomain = question.subdomain,
            )
        }

        // Question text
        item {
            Text(
                text = question.text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Difficulty indicator
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DifficultyIndicator(difficulty = question.difficulty)
            }
        }

        // Speed target for speed mode
        if (mode == KBStudyMode.SPEED) {
            item {
                Text(
                    text = "Target: 10s",
                    style = MaterialTheme.typography.labelSmall,
                    color = KBTheme.currentEvents(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Answer input
        item {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = userAnswer,
                onValueChange = onAnswerChange,
                label = { Text("Your answer...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Action buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                OutlinedButton(onClick = onSkip) {
                    Text("Skip")
                }
                Button(
                    onClick = onSubmit,
                    enabled = userAnswer.isNotBlank(),
                ) {
                    Text(stringResource(R.string.submit))
                }
            }
        }
    }
}

@Composable
private fun AnswerFeedbackContent(
    question: KBQuestion,
    isCorrect: Boolean,
    lastResult: com.unamentis.modules.knowledgebowl.data.model.KBQuestionResult?,
    isLastQuestion: Boolean,
    onNext: () -> Unit,
    mode: KBStudyMode,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Result icon
        item {
            Icon(
                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = if (isCorrect) KBTheme.mastered() else MaterialTheme.colorScheme.error,
            )
        }

        // Result text
        item {
            Text(
                text =
                    if (isCorrect) {
                        stringResource(R.string.kb_correct)
                    } else {
                        stringResource(R.string.kb_incorrect)
                    },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isCorrect) KBTheme.mastered() else MaterialTheme.colorScheme.error,
            )
        }

        // Correct answer
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.kb_correct_answer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = question.answer.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Response time
        lastResult?.let { result ->
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format("%.1fs", result.responseTimeSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (result.wasWithinSpeedTarget) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.cd_within_speed_target),
                            modifier = Modifier.size(16.dp),
                            tint = KBTheme.currentEvents(),
                        )
                    }
                }
            }
        }

        // Next button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = modeColor(mode),
                    ),
            ) {
                Text(
                    text =
                        if (isLastQuestion) {
                            stringResource(R.string.kb_see_results)
                        } else {
                            stringResource(R.string.kb_next_question)
                        },
                )
            }
        }
    }
}

@Composable
private fun CompletedContent(
    summary: KBPracticeSessionSummary,
    mode: KBStudyMode,
    onDone: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Trophy icon
        item {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = KBTheme.gold(),
            )
        }

        // Title
        item {
            Text(
                text = stringResource(R.string.kb_session_complete),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // Stats grid
        item {
            StatsGrid(summary = summary)
        }

        // Domain breakdown
        if (summary.domainBreakdown.isNotEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                ) {
                    Text(
                        text = "Domain Performance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    summary.domainBreakdown.forEach { (domainId, score) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = domainId.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row {
                                Text(
                                    text = "${score.correct}/${score.total}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.0f%%", score.accuracy * 100),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (score.accuracy >= 0.7) {
                                            KBTheme.mastered()
                                        } else {
                                            KBTheme.currentEvents()
                                        },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Done button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = modeColor(mode),
                    ),
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun DomainBadge(
    domain: KBDomain,
    subdomain: String?,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(domain.color().copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = domain.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = domain.color(),
        )
        if (!subdomain.isNullOrBlank()) {
            Text(
                text = " \u2022 ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subdomain,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DifficultyIndicator(difficulty: KBDifficulty) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(6) { index ->
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < difficulty.ordinal + 1) {
                                KBTheme.currentEvents()
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
            )
        }
    }
}

@Composable
private fun StatsGrid(summary: KBPracticeSessionSummary) {
    // Get colors and labels in composable context
    val accuracyColor = if (summary.accuracy >= 0.7) KBTheme.mastered() else KBTheme.currentEvents()
    val correctColor = KBTheme.intermediate()
    val timeColor = KBTheme.mathematics()
    val speedColor = KBTheme.currentEvents()
    val accuracyLabel = stringResource(R.string.kb_accuracy)
    val avgTimeLabel = stringResource(R.string.kb_avg_time)
    val correctLabel = stringResource(R.string.kb_stat_correct)
    val speedTargetLabel = stringResource(R.string.kb_speed_target)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard(
                stat =
                    StatItem(
                        title = accuracyLabel,
                        value = String.format("%.0f%%", summary.accuracy * 100),
                        icon = Icons.Default.CheckCircle,
                        color = accuracyColor,
                    ),
            )
            StatCard(
                stat =
                    StatItem(
                        title = avgTimeLabel,
                        value = String.format("%.1fs", summary.averageResponseTime),
                        icon = Icons.Default.AccessTime,
                        color = timeColor,
                    ),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatCard(
                stat =
                    StatItem(
                        title = correctLabel,
                        value = "${summary.correctAnswers}/${summary.totalQuestions}",
                        icon = Icons.Default.Check,
                        color = correctColor,
                    ),
            )
            StatCard(
                stat =
                    StatItem(
                        title = speedTargetLabel,
                        value = String.format("%.0f%%", summary.speedTargetRate * 100),
                        icon = Icons.Default.Timer,
                        color = speedColor,
                    ),
            )
        }
    }
}

@Composable
private fun StatCard(stat: StatItem) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = stat.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = stat.color,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stat.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stat.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class StatItem(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun modeColor(mode: KBStudyMode): Color =
    when (mode) {
        KBStudyMode.DIAGNOSTIC -> KBTheme.science()
        KBStudyMode.TARGETED -> KBTheme.mathematics()
        KBStudyMode.BREADTH -> KBTheme.literature()
        KBStudyMode.SPEED -> KBTheme.currentEvents()
        KBStudyMode.COMPETITION -> KBTheme.history()
        KBStudyMode.TEAM -> KBTheme.socialStudies()
    }

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}
