package com.unamentis.modules.knowledgebowl.ui.rebound

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.rebound.KBReboundSimulator
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import com.unamentis.ui.theme.IOSTypography
import java.text.NumberFormat
import java.util.Locale

/**
 * Main screen for rebound training mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBReboundTrainingScreen(
    onNavigateBack: () -> Unit,
    viewModel: KBReboundTrainingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_rebound_training)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.kb_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = KBTheme.bgPrimary(),
                    ),
            )
        },
        containerColor = KBTheme.bgPrimary(),
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (uiState.state) {
                ReboundTrainingState.SETUP ->
                    SetupView(
                        uiState = uiState,
                        onOpponentAccuracyChange = viewModel::setOpponentAccuracy,
                        onReboundProbabilityChange = viewModel::setReboundProbability,
                        onShowOpponentAnswerChange = viewModel::setShowOpponentAnswer,
                        onQuestionCountChange = viewModel::setQuestionCount,
                        onShowPracticeScenarios = viewModel::togglePracticeScenarios,
                        onStartTraining = viewModel::startTraining,
                    )

                ReboundTrainingState.WAITING_FOR_OPPONENT ->
                    WaitingView(
                        uiState = uiState,
                    )

                ReboundTrainingState.OPPONENT_ANSWERING ->
                    OpponentAnsweringView(
                        uiState = uiState,
                    )

                ReboundTrainingState.REBOUND_OPPORTUNITY ->
                    ReboundOpportunityView(
                        uiState = uiState,
                        onBuzz = viewModel::buzzOnRebound,
                        onHold = viewModel::holdStrategically,
                    )

                ReboundTrainingState.USER_TURN ->
                    UserTurnView(
                        uiState = uiState,
                        onCorrect = { viewModel.submitAnswer(true) },
                        onIncorrect = { viewModel.submitAnswer(false) },
                    )

                ReboundTrainingState.FEEDBACK ->
                    FeedbackView(
                        uiState = uiState,
                        onNext = viewModel::nextQuestion,
                    )

                ReboundTrainingState.RESULTS ->
                    ResultsView(
                        uiState = uiState,
                        onRestart = viewModel::restartTraining,
                        onDone = onNavigateBack,
                    )
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = KBTheme.mastered())
                }
            }
        }

        // Practice scenarios sheet
        if (uiState.showingPracticeScenarios) {
            PracticeScenariosSheet(
                onDismiss = viewModel::dismissPracticeScenarios,
            )
        }
    }
}

@Composable
private fun SetupView(
    uiState: ReboundTrainingUiState,
    onOpponentAccuracyChange: (Float) -> Unit,
    onReboundProbabilityChange: (Float) -> Unit,
    onShowOpponentAnswerChange: (Boolean) -> Unit,
    onQuestionCountChange: (Int) -> Unit,
    onShowPracticeScenarios: () -> Unit,
    onStartTraining: () -> Unit,
) {
    val percentFormatter = NumberFormat.getPercentInstance(Locale.getDefault())

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Header with rebound emoji
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "\u21A9\uFE0F",
                style = IOSTypography.largeTitle,
            )
            Text(
                text = stringResource(R.string.kb_rebound_training),
                style = IOSTypography.title2,
                color = KBTheme.textPrimary(),
            )
            Text(
                text = stringResource(R.string.kb_rebound_description),
                style = IOSTypography.subheadline,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )
        }

        // Explanation Card
        ExplanationCard()

        // Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.kb_training_settings),
                    style = IOSTypography.headline,
                    color = KBTheme.textPrimary(),
                )

                // Opponent Accuracy
                Column {
                    Text(
                        text =
                            stringResource(
                                R.string.kb_opponent_accuracy_value,
                                percentFormatter.format(uiState.opponentAccuracy.toDouble()),
                            ),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textPrimary(),
                    )
                    Slider(
                        value = uiState.opponentAccuracy,
                        onValueChange = onOpponentAccuracyChange,
                        valueRange = 0.3f..0.9f,
                        steps = 5,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = KBTheme.mastered(),
                                activeTrackColor = KBTheme.mastered(),
                            ),
                    )
                    Text(
                        text = stringResource(R.string.kb_lower_more_rebounds),
                        style = IOSTypography.caption,
                        color = KBTheme.textSecondary(),
                    )
                }

                // Rebound Probability
                Column {
                    Text(
                        text =
                            stringResource(
                                R.string.kb_rebound_probability_value,
                                percentFormatter.format(uiState.reboundProbability.toDouble()),
                            ),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textPrimary(),
                    )
                    Slider(
                        value = uiState.reboundProbability,
                        onValueChange = onReboundProbabilityChange,
                        valueRange = 0.3f..0.8f,
                        steps = 4,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = KBTheme.mastered(),
                                activeTrackColor = KBTheme.mastered(),
                            ),
                    )
                    Text(
                        text = stringResource(R.string.kb_chance_opponent_buzzes),
                        style = IOSTypography.caption,
                        color = KBTheme.textSecondary(),
                    )
                }

                // Show Opponent Answer Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.kb_show_opponent_answer),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textPrimary(),
                    )
                    Switch(
                        checked = uiState.showOpponentAnswer,
                        onCheckedChange = onShowOpponentAnswerChange,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = KBTheme.mastered(),
                            ),
                    )
                }

                // Question Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.kb_questions_count_label, uiState.questionCount),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textPrimary(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onQuestionCountChange(uiState.questionCount - 5) },
                            enabled = uiState.questionCount > 5,
                        ) {
                            Text("-5")
                        }
                        OutlinedButton(
                            onClick = { onQuestionCountChange(uiState.questionCount + 5) },
                            enabled = uiState.questionCount < 30,
                        ) {
                            Text("+5")
                        }
                    }
                }
            }
        }

        // Start Button
        Button(
            onClick = onStartTraining,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.kb_start_training),
                modifier = Modifier.padding(start = 8.dp),
                style = IOSTypography.headline,
            )
        }

        // Practice Scenarios Button
        OutlinedButton(
            onClick = onShowPracticeScenarios,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_view_practice_scenarios),
                style = IOSTypography.headline,
                color = KBTheme.mastered(),
            )
        }
    }
}

@Composable
private fun ExplanationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "\u2139\uFE0F", style = IOSTypography.title3) // ℹ️
                Text(
                    text = stringResource(R.string.kb_how_rebounds_work),
                    style = IOSTypography.headline,
                    color = KBTheme.textPrimary(),
                )
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExplanationRow("1️⃣", stringResource(R.string.kb_rebound_step_1))
                ExplanationRow("2️⃣", stringResource(R.string.kb_rebound_step_2))
                ExplanationRow("3️⃣", stringResource(R.string.kb_rebound_step_3))
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.kb_rebound_key_strategy),
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun ExplanationRow(
    icon: String,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = icon, style = IOSTypography.subheadline)
        Text(
            text = text,
            style = IOSTypography.subheadline,
            color = KBTheme.textPrimary(),
        )
    }
}

@Composable
private fun ProgressHeader(uiState: ReboundTrainingUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Opponent badge (orange)
        Text(
            text = stringResource(R.string.kb_vs_opponent, uiState.opponentName),
            style = IOSTypography.caption,
            color = Color(0xFFFF9800),
            modifier =
                Modifier
                    .background(
                        Color(0xFFFF9800).copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Progress indicator
        Text(
            text =
                stringResource(
                    R.string.kb_question_progress,
                    uiState.currentQuestionIndex + 1,
                    uiState.totalQuestions,
                ),
            style = IOSTypography.subheadline,
            color = KBTheme.textSecondary(),
        )

        // Points display
        Text(
            text = stringResource(R.string.kb_points_display, uiState.totalPoints),
            style = IOSTypography.subheadline,
            fontWeight = FontWeight.Bold,
            color = if (uiState.totalPoints >= 0) KBTheme.mastered() else KBTheme.timerCritical(),
        )
    }
}

@Composable
private fun QuestionCard(question: KBQuestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(question.domain.stringResId),
                    style = IOSTypography.caption,
                    fontWeight = FontWeight.Bold,
                    color = question.domain.color(),
                    modifier =
                        Modifier
                            .background(
                                question.domain.color().copy(alpha = 0.2f),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(
                    text = question.difficulty.displayName,
                    style = IOSTypography.caption,
                    color = KBTheme.textSecondary(),
                )
            }

            Text(
                text = question.text,
                style = IOSTypography.body,
                color = KBTheme.textPrimary(),
            )
        }
    }
}

@Composable
private fun WaitingView(uiState: ReboundTrainingUiState) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProgressHeader(uiState)

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hourglass emoji
            Text(
                text = "\u23F3",
                style = IOSTypography.largeTitle,
            )
            Text(
                text = stringResource(R.string.kb_reading_question),
                style = IOSTypography.title3,
                color = KBTheme.textSecondary(),
            )

            uiState.currentScenario?.let { scenario ->
                QuestionCard(scenario.question)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OpponentAnsweringView(uiState: ReboundTrainingUiState) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProgressHeader(uiState)

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Bell icon (orange)
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color(0xFFFF9800),
            )

            Text(
                text = stringResource(R.string.kb_opponent_buzzed, uiState.opponentName),
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800),
            )

            uiState.currentScenario?.let { scenario ->
                QuestionCard(scenario.question)

                if (uiState.showOpponentAnswer && scenario.opponentAnswer != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.kb_their_answer),
                                style = IOSTypography.subheadline,
                                color = KBTheme.textSecondary(),
                            )
                            Text(
                                text = scenario.opponentAnswer,
                                style = IOSTypography.title3,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (scenario.opponentWasCorrect) KBTheme.mastered() else KBTheme.timerCritical(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReboundOpportunityView(
    uiState: ReboundTrainingUiState,
    onBuzz: () -> Unit,
    onHold: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProgressHeader(uiState)

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Rebound/return arrow icon
            Text(
                text = "\u21A9\uFE0F",
                style = IOSTypography.largeTitle,
            )

            Text(
                text = stringResource(R.string.kb_rebound_opportunity_alert),
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
                color = KBTheme.mastered(),
            )

            Text(
                text = stringResource(R.string.kb_opponent_got_wrong, uiState.opponentName),
                style = IOSTypography.subheadline,
                color = KBTheme.textSecondary(),
            )

            uiState.currentScenario?.let { scenario ->
                QuestionCard(scenario.question)
            }

            // Decision buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onBuzz,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = KBTheme.mastered(),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.kb_rebound_buzz),
                        modifier = Modifier.padding(start = 8.dp),
                        style = IOSTypography.headline,
                    )
                }

                OutlinedButton(
                    onClick = onHold,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.kb_hold_dont_know),
                        style = IOSTypography.subheadline,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Timer
        if (uiState.reboundTimeRemaining > 0) {
            Text(
                text =
                    stringResource(
                        R.string.kb_rebound_time_remaining,
                        String.format(Locale.getDefault(), "%.1f", uiState.reboundTimeRemaining),
                    ),
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun UserTurnView(
    uiState: ReboundTrainingUiState,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProgressHeader(uiState)

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_rebound_your_turn),
                style = IOSTypography.title3,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            uiState.currentScenario?.let { scenario ->
                QuestionCard(scenario.question)

                // Answer buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onCorrect,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = KBTheme.mastered(),
                            ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.kb_i_got_it_right),
                            modifier = Modifier.padding(start = 8.dp),
                            style = IOSTypography.headline,
                        )
                    }

                    Button(
                        onClick = onIncorrect,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = KBTheme.timerCritical(),
                            ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.kb_i_got_it_wrong),
                            modifier = Modifier.padding(start = 8.dp),
                            style = IOSTypography.headline,
                        )
                    }
                }

                // Show correct answer hint
                Card(
                    colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kb_correct_answer_label),
                            style = IOSTypography.caption,
                            color = KBTheme.textSecondary(),
                        )
                        Text(
                            text = scenario.question.answer.primary,
                            style = IOSTypography.subheadline,
                            fontWeight = FontWeight.Bold,
                            color = KBTheme.textPrimary(),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FeedbackView(
    uiState: ReboundTrainingUiState,
    onNext: () -> Unit,
) {
    val feedback = uiState.lastFeedback ?: return

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProgressHeader(uiState)

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Feedback icon
            val iconTint = if (feedback.isPositive) KBTheme.mastered() else KBTheme.timerCritical()
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (feedback.isPositive) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = iconTint,
                )
            }

            Text(
                text = feedback.title,
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )

            Text(
                text = feedback.message,
                style = IOSTypography.body,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )

            if (feedback.points != 0) {
                val sign = if (feedback.points > 0) "+" else ""
                val pointsLabel = stringResource(R.string.kb_points)
                Text(
                    text = "$sign${feedback.points} $pointsLabel",
                    style = IOSTypography.title3,
                    fontWeight = FontWeight.Bold,
                    color = if (feedback.points > 0) KBTheme.mastered() else KBTheme.timerCritical(),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_next_question),
                style = IOSTypography.headline,
            )
        }
    }
}

@Composable
private fun ResultsView(
    uiState: ReboundTrainingUiState,
    onRestart: () -> Unit,
    onDone: () -> Unit,
) {
    val result = uiState.trainingResult ?: return
    val stats = result.stats
    val percentFormatter = NumberFormat.getPercentInstance(Locale.getDefault())

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val masterTitle = stringResource(R.string.kb_rebound_master)
            val goodTitle = stringResource(R.string.kb_good_instincts)
            val practiceTitle = stringResource(R.string.kb_keep_practicing)
            val (icon, title, color) =
                when {
                    stats.reboundAccuracy >= 0.8 -> Triple("\u2B50", masterTitle, KBTheme.mastered())
                    stats.reboundAccuracy >= 0.6 ->
                        Triple("\uD83D\uDC4D", goodTitle, KBTheme.intermediate())
                    else -> Triple("\u2B06\uFE0F", practiceTitle, KBTheme.beginner())
                }

            Text(text = icon, style = IOSTypography.largeTitle)
            Text(
                text = title,
                style = IOSTypography.title,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text =
                    stringResource(
                        R.string.kb_rebounds_correct_of_taken,
                        stats.reboundsCorrect,
                        stats.reboundsTaken,
                    ),
                style = IOSTypography.headline,
                color = KBTheme.textSecondary(),
            )
        }

        // Stats Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(280.dp),
        ) {
            item {
                StatCard(
                    icon = "\u2B50",
                    value = stats.totalPoints.toString(),
                    label = stringResource(R.string.kb_total_points),
                )
            }
            item {
                StatCard(
                    icon = "\uD83C\uDFAF",
                    value = percentFormatter.format(stats.reboundAccuracy),
                    label = stringResource(R.string.kb_rebound_accuracy),
                )
            }
            item {
                StatCard(
                    icon = "\u21A9\uFE0F",
                    value = stats.reboundOpportunities.toString(),
                    label = stringResource(R.string.kb_opportunities),
                )
            }
            item {
                StatCard(
                    icon = "\uD83D\uDD14",
                    value = stats.reboundsTaken.toString(),
                    label = stringResource(R.string.kb_rebounds_taken),
                )
            }
            item {
                StatCard(
                    icon = "\u270B",
                    value = stats.strategicHolds.toString(),
                    label = stringResource(R.string.kb_strategic_holds),
                )
            }
            item {
                StatCard(
                    icon = "\u23F1\uFE0F",
                    value = String.format(Locale.getDefault(), "%.1fs", stats.averageResponseTime),
                    label = stringResource(R.string.kb_avg_response),
                )
            }
        }

        // Recommendation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "\uD83D\uDCA1", style = IOSTypography.title2) // 💡
                Text(
                    text = result.recommendation,
                    style = IOSTypography.subheadline,
                    color = KBTheme.textPrimary(),
                )
            }
        }

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = KBTheme.mastered(),
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.kb_train_again),
                    modifier = Modifier.padding(start = 8.dp),
                    style = IOSTypography.headline,
                )
            }

            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.kb_done),
                    style = IOSTypography.headline,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: String,
    value: String,
    label: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = icon, style = IOSTypography.title2)
            Text(
                text = value,
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
                color = KBTheme.textPrimary(),
            )
            Text(
                text = label,
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeScenariosSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scenarios = KBReboundSimulator.generatePracticeScenarios()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KBTheme.bgPrimary(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_practice_scenarios),
                style = IOSTypography.title2,
                color = KBTheme.textPrimary(),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(400.dp),
            ) {
                items(scenarios) { (scenario, tip) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = scenario,
                                style = IOSTypography.body,
                                color = KBTheme.textPrimary(),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = "\uD83D\uDCA1", style = IOSTypography.caption) // 💡
                                Text(
                                    text = tip,
                                    style = IOSTypography.caption,
                                    color = KBTheme.textSecondary(),
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.kb_done))
            }
        }
    }
}
