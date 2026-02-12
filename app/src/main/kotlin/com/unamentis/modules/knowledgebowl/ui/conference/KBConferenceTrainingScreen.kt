package com.unamentis.modules.knowledgebowl.ui.conference

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.conference.KBHandSignal
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSRed
import java.text.NumberFormat
import java.util.Locale

/**
 * Conference training screen for practicing team conferring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBConferenceTrainingScreen(
    onNavigateBack: () -> Unit,
    viewModel: KBConferenceTrainingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_conference_training)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
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
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    ErrorView(
                        error = uiState.error!!,
                        onDismiss = { viewModel.clearError() },
                    )
                }

                else -> {
                    when (uiState.state) {
                        ConferenceTrainingState.SETUP -> {
                            SetupView(
                                uiState = uiState,
                                onRegionSelected = viewModel::selectRegion,
                                onProgressiveDifficultyChanged = viewModel::setProgressiveDifficulty,
                                onQuestionCountChanged = viewModel::setQuestionCount,
                                onStartTraining = viewModel::startTraining,
                                onStartSignalPractice = viewModel::startSignalPractice,
                            )
                        }

                        ConferenceTrainingState.TRAINING -> {
                            TrainingView(
                                uiState = uiState,
                                onSubmitAnswer = viewModel::submitAnswer,
                                onSkipQuestion = viewModel::skipQuestion,
                                onRequestMoreTime = viewModel::requestMoreTime,
                            )
                        }

                        ConferenceTrainingState.SIGNAL_PRACTICE -> {
                            SignalPracticeView(
                                uiState = uiState,
                                onSelectSignal = viewModel::selectSignal,
                                onBack = viewModel::endSignalPractice,
                            )
                        }

                        ConferenceTrainingState.RESULTS -> {
                            ResultsView(
                                uiState = uiState,
                                onTrainAgain = viewModel::restartTraining,
                                onDone = {
                                    viewModel.reset()
                                    onNavigateBack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Setup view for conference training.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupView(
    uiState: ConferenceTrainingUiState,
    onRegionSelected: (KBRegion) -> Unit,
    onProgressiveDifficultyChanged: (Boolean) -> Unit,
    onQuestionCountChanged: (Int) -> Unit,
    onStartTraining: () -> Unit,
    onStartSignalPractice: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing),
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Groups,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = KBTheme.mastered(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.kb_conference_training),
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.kb_practice_quick_team_decisions),
                style = IOSTypography.subheadline,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )
        }

        // Region Rules Card
        RegionRulesCard(uiState = uiState, onRegionSelected = onRegionSelected)

        // Training Options Card
        TrainingOptionsCard(
            uiState = uiState,
            onProgressiveDifficultyChanged = onProgressiveDifficultyChanged,
            onQuestionCountChanged = onQuestionCountChanged,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Start Button
        Button(
            onClick = onStartTraining,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = KBTheme.mastered(),
                ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.kb_start_training),
                style = IOSTypography.headline,
            )
        }

        // Hand Signal Practice Button (if hand signals only)
        if (uiState.handSignalsOnly) {
            OutlinedButton(
                onClick = onStartSignalPractice,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PanTool, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.kb_practice_hand_signals))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionRulesCard(
    uiState: ConferenceTrainingUiState,
    onRegionSelected: (KBRegion) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Region Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(KBRegion.COLORADO, KBRegion.MINNESOTA, KBRegion.WASHINGTON).forEachIndexed { index, region ->
                    SegmentedButton(
                        selected = uiState.selectedRegion == region,
                        onClick = { onRegionSelected(region) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) {
                        Text(region.abbreviation)
                    }
                }
            }

            HorizontalDivider()

            // Rules Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = KBTheme.mastered(),
                )
                Text(
                    text = stringResource(R.string.kb_conference_rules, uiState.selectedRegion.displayName),
                    style = IOSTypography.headline,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.kb_time_limit),
                    style = IOSTypography.subheadline,
                    color = KBTheme.textSecondary(),
                )
                Text(
                    text =
                        stringResource(
                            R.string.kb_seconds_format,
                            uiState.config?.baseTimeLimit?.toInt() ?: 15,
                        ),
                    style = IOSTypography.subheadline,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.kb_communication),
                    style = IOSTypography.subheadline,
                    color = KBTheme.textSecondary(),
                )
                Text(
                    text =
                        if (uiState.handSignalsOnly) {
                            stringResource(R.string.kb_hand_signals_only)
                        } else {
                            stringResource(R.string.kb_verbal_allowed)
                        },
                    style = IOSTypography.subheadline,
                )
            }

            if (uiState.handSignalsOnly) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.kb_no_talking_warning),
                        style = IOSTypography.caption,
                        color = KBTheme.textSecondary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainingOptionsCard(
    uiState: ConferenceTrainingUiState,
    onProgressiveDifficultyChanged: (Boolean) -> Unit,
    onQuestionCountChanged: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kb_training_options),
                style = IOSTypography.headline,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.kb_progressive_difficulty),
                    style = IOSTypography.subheadline,
                )
                Switch(
                    checked = uiState.progressiveDifficulty,
                    onCheckedChange = onProgressiveDifficultyChanged,
                )
            }

            if (uiState.progressiveDifficulty) {
                Text(
                    text = stringResource(R.string.kb_progressive_levels),
                    style = IOSTypography.caption,
                    color = KBTheme.textSecondary(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.kb_questions_count, uiState.questionCount),
                    style = IOSTypography.subheadline,
                )
            }

            Slider(
                value = uiState.questionCount.toFloat(),
                onValueChange = { onQuestionCountChanged(it.toInt()) },
                valueRange = 5f..30f,
                steps = 4,
            )
        }
    }
}

/**
 * Training view with timer and question.
 */
@Composable
private fun TrainingView(
    uiState: ConferenceTrainingUiState,
    onSubmitAnswer: (Boolean) -> Unit,
    onSkipQuestion: () -> Unit,
    onRequestMoreTime: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress and Level
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = levelColor(uiState.currentLevel).copy(alpha = 0.2f),
            ) {
                Text(
                    text = stringResource(R.string.kb_level_format, uiState.currentLevel + 1),
                    style = IOSTypography.subheadline,
                    fontWeight = FontWeight.Medium,
                    color = levelColor(uiState.currentLevel),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Text(
                text = "${uiState.currentQuestionIndex + 1}/${uiState.totalQuestions}",
                style = IOSTypography.subheadline,
                color = KBTheme.textSecondary(),
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Timer
        ConferenceTimer(
            remainingTime = uiState.remainingTime,
            totalTime = uiState.currentTimeLimit,
            progress = uiState.timerProgress.toFloat(),
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingMedium))

        // Question Card
        uiState.currentQuestion?.let { question ->
            QuestionCard(question = question, handSignalsOnly = uiState.handSignalsOnly)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Answer Buttons
        AnswerButtons(
            canRequestMoreTime = uiState.canRequestMoreTime,
            onSubmitAnswer = onSubmitAnswer,
            onRequestMoreTime = onRequestMoreTime,
        )

        // Skip Button
        OutlinedButton(
            onClick = onSkipQuestion,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.kb_skip_question))
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
    }
}

@Composable
private fun ConferenceTimer(
    remainingTime: Double,
    totalTime: Double,
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "timer_progress",
    )

    val timerColor =
        when {
            remainingTime <= 3 -> iOSRed
            remainingTime <= 5 -> Color(0xFFFFA000)
            else -> KBTheme.mastered()
        }

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Background circle
        Box(
            modifier =
                Modifier
                    .size(160.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color.Gray.copy(alpha = 0.2f),
                            style = Stroke(width = 12.dp.toPx()),
                        )
                        drawArc(
                            color = timerColor,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                        )
                    },
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.getDefault(), "%.1f", remainingTime),
                style = IOSTypography.largeTitle.copy(fontSize = 44.sp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.kb_seconds),
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
            )
        }
    }

    Text(
        text = stringResource(R.string.kb_time_limit_format, totalTime.toInt()),
        style = IOSTypography.caption,
        color = KBTheme.textSecondary(),
    )
}

@Composable
private fun QuestionCard(
    question: KBQuestion,
    handSignalsOnly: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = question.domain.color().copy(alpha = 0.2f),
                ) {
                    Text(
                        text = stringResource(question.domain.stringResId),
                        style = IOSTypography.caption,
                        fontWeight = FontWeight.Bold,
                        color = question.domain.color(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Text(
                    text = question.difficulty.displayName,
                    style = IOSTypography.caption,
                    color = KBTheme.textSecondary(),
                )
            }

            Text(
                text = question.text,
                style = IOSTypography.body,
            )

            if (handSignalsOnly) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.PanTool,
                        contentDescription = null,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.kb_use_hand_signals),
                        style = IOSTypography.caption,
                        color = Color(0xFFFFA000),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerButtons(
    canRequestMoreTime: Boolean,
    onSubmitAnswer: (Boolean) -> Unit,
    onRequestMoreTime: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Ready to Answer
        Button(
            onClick = { onSubmitAnswer(true) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = iOSGreen),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.kb_team_agreed_answer),
                style = IOSTypography.headline,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Pass
            OutlinedButton(
                onClick = { onSubmitAnswer(false) },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.kb_signal_pass))
            }

            // More Time
            OutlinedButton(
                onClick = onRequestMoreTime,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                enabled = canRequestMoreTime,
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.kb_need_more_time))
            }
        }
    }
}

/**
 * Signal practice view.
 */
@Composable
private fun SignalPracticeView(
    uiState: ConferenceTrainingUiState,
    onSelectSignal: (KBHandSignal) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Dimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Icon(
            Icons.Default.PanTool,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = KBTheme.intermediate(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.kb_signal_practice),
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.kb_learn_kb_signals),
            style = IOSTypography.subheadline,
            color = KBTheme.textSecondary(),
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Scenario
        uiState.currentSignalPrompt?.let { (_, scenario) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.CardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.kb_scenario),
                        style = IOSTypography.headline,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = scenario,
                        style = IOSTypography.body,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.kb_what_signal),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textSecondary(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Signal Options Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(KBHandSignal.entries) { signal ->
                SignalButton(
                    signal = signal,
                    isCorrectAnswer = uiState.currentSignalPrompt?.first == signal,
                    isSelected = uiState.selectedSignal == signal,
                    showResult = uiState.lastSignalResult != null,
                    onClick = { onSelectSignal(signal) },
                )
            }
        }

        // Back to Training
        OutlinedButton(onClick = onBack) {
            Text(stringResource(R.string.kb_back_to_training))
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
    }
}

@Composable
private fun SignalButton(
    signal: KBHandSignal,
    isCorrectAnswer: Boolean,
    isSelected: Boolean,
    showResult: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor =
        when {
            showResult && isCorrectAnswer -> iOSGreen.copy(alpha = 0.3f)
            showResult && isSelected && !isCorrectAnswer -> iOSRed.copy(alpha = 0.3f)
            else -> KBTheme.bgSecondary()
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !showResult, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = signal.emoji,
                style = IOSTypography.largeTitle,
            )
            Text(
                text = stringResource(signal.displayNameResId),
                style = IOSTypography.caption,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(signal.gestureDescriptionResId),
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Results view.
 */
@Composable
private fun ResultsView(
    uiState: ConferenceTrainingUiState,
    onTrainAgain: () -> Unit,
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
                .padding(Dimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Result Icon
        val (icon, color, title) =
            when {
                stats.accuracy >= 0.8 -> Triple(Icons.Default.Star, Color(0xFFFFD700), R.string.kb_excellent)
                stats.accuracy >= 0.6 -> Triple(Icons.Default.ThumbUp, iOSGreen, R.string.kb_good_progress)
                else -> Triple(Icons.Default.TrendingUp, KBTheme.intermediate(), R.string.kb_keep_practicing)
            }

        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = color,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(title),
            style = IOSTypography.title,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.kb_correct_of_total, stats.correctCount, stats.totalAttempts),
            style = IOSTypography.headline,
            color = KBTheme.textSecondary(),
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Stats Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(220.dp),
        ) {
            item {
                StatCard(
                    icon = Icons.Default.CheckCircle,
                    label = stringResource(R.string.kb_accuracy),
                    value = percentFormatter.format(stats.accuracy),
                )
            }
            item {
                StatCard(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.kb_fastest_time),
                    value = String.format(Locale.getDefault(), "%.1fs", stats.fastestTime),
                )
            }
            item {
                StatCard(
                    icon = Icons.Default.Warning,
                    label = stringResource(R.string.kb_timeouts),
                    value = "${stats.timeoutsCount}",
                )
            }
            item {
                StatCard(
                    icon = Icons.Default.TrendingUp,
                    label = stringResource(R.string.kb_final_level),
                    value = "${stats.currentDifficultyLevel + 1}",
                )
            }
            item {
                StatCard(
                    icon = Icons.Default.Speed,
                    label = stringResource(R.string.kb_conference_efficiency),
                    value = percentFormatter.format(stats.averageEfficiency),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingMedium))

        // Recommendation
        Card(
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        ) {
            Row(
                modifier = Modifier.padding(Dimensions.CardPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                )
                Text(
                    text = result.recommendation,
                    style = IOSTypography.subheadline,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Actions
        Button(
            onClick = onTrainAgain,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = KBTheme.mastered()),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.kb_train_again))
        }

        OutlinedButton(
            onClick = onDone,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.done))
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = KBTheme.mastered(),
            )
            Text(
                text = value,
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun ErrorView(
    error: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = error,
                style = IOSTypography.body,
                color = iOSRed,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.kb_try_again))
            }
        }
    }
}

@Composable
private fun levelColor(level: Int): Color {
    return when (level) {
        0 -> iOSGreen
        1 -> Color(0xFF2196F3)
        2 -> Color(0xFFFFA000)
        3 -> iOSRed
        else -> KBTheme.mastered()
    }
}
