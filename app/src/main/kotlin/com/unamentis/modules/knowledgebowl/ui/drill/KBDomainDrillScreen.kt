package com.unamentis.modules.knowledgebowl.ui.drill

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBDomain
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import com.unamentis.ui.theme.IOSTypography
import java.text.NumberFormat
import java.util.Locale

/**
 * Domain Drill training screen.
 *
 * Allows focused practice on a single domain with progressive difficulty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBDomainDrillScreen(
    onNavigateBack: () -> Unit,
    viewModel: KBDomainDrillViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_domain_drill)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.kb_back))
                    }
                },
                actions = {
                    if (uiState.state == DrillState.DRILLING) {
                        TextButton(onClick = { viewModel.endDrill() }) {
                            Text(
                                stringResource(R.string.kb_end_drill),
                                color = KBTheme.timerCritical(),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = KBTheme.bgPrimary(),
                    ),
            )
        },
        containerColor = KBTheme.bgPrimary(),
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (uiState.state) {
                DrillState.SETUP -> SetupView(uiState, viewModel)
                DrillState.DRILLING -> DrillingView(uiState, viewModel)
                DrillState.FEEDBACK -> FeedbackView(uiState, viewModel)
                DrillState.RESULTS -> ResultsView(uiState, viewModel, onNavigateBack)
            }
        }
    }
}

@Composable
private fun SetupView(
    uiState: DrillUiState,
    viewModel: KBDomainDrillViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Domain Selection
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.kb_select_domain),
                style = IOSTypography.headline,
                color = KBTheme.textPrimary(),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(240.dp),
            ) {
                items(KBDomain.entries) { domain ->
                    DomainButton(
                        domain = domain,
                        isSelected = uiState.selectedDomain == domain,
                        onClick = { viewModel.selectDomain(domain) },
                    )
                }
            }
        }

        HorizontalDivider(color = KBTheme.bgSecondary())

        // Settings
        Card(
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

                // Question count slider
                Column {
                    Text(
                        text = stringResource(R.string.kb_questions_count_label, uiState.questionCount),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textPrimary(),
                    )
                    Slider(
                        value = uiState.questionCount.toFloat(),
                        onValueChange = { viewModel.setQuestionCount(it.toInt()) },
                        valueRange = 5f..30f,
                        steps = 4,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = uiState.selectedDomain?.color() ?: KBTheme.textPrimary(),
                                activeTrackColor = uiState.selectedDomain?.color() ?: KBTheme.textPrimary(),
                            ),
                    )
                }

                // Progressive difficulty toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.kb_progressive_difficulty),
                        style = IOSTypography.body,
                        color = KBTheme.textPrimary(),
                    )
                    Switch(
                        checked = uiState.progressiveDifficulty,
                        onCheckedChange = { viewModel.setProgressiveDifficulty(it) },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = uiState.selectedDomain?.color() ?: KBTheme.mastered(),
                            ),
                    )
                }

                // Time pressure toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.kb_time_pressure_mode),
                            style = IOSTypography.body,
                            color = KBTheme.textPrimary(),
                        )
                        if (uiState.timePressureMode) {
                            Text(
                                text = stringResource(R.string.kb_seconds_per_question, 30),
                                style = IOSTypography.caption,
                                color = KBTheme.textSecondary(),
                            )
                        }
                    }
                    Switch(
                        checked = uiState.timePressureMode,
                        onCheckedChange = { viewModel.setTimePressureMode(it) },
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = uiState.selectedDomain?.color() ?: KBTheme.mastered(),
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Start Button
        Button(
            onClick = { viewModel.startDrill() },
            enabled = uiState.selectedDomain != null,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = uiState.selectedDomain?.color() ?: Color.Gray,
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(R.string.kb_start_drill),
                style = IOSTypography.headline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DomainButton(
    domain: KBDomain,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) domain.color() else KBTheme.bgSecondary()
    val contentColor = if (isSelected) Color.White else domain.color()
    val textColor = if (isSelected) Color.White else KBTheme.textPrimary()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, domain.color(), RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                    },
                ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Domain initial as placeholder for icon
            Text(
                text = domain.displayName.first().toString(),
                style = IOSTypography.title2,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(domain.stringResId),
                style = IOSTypography.caption2,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DrillingView(
    uiState: DrillUiState,
    viewModel: KBDomainDrillViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Progress header
        DrillProgressHeader(uiState)

        HorizontalDivider(color = KBTheme.bgSecondary())

        // Question content
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Timer (if time pressure mode)
            if (uiState.timePressureMode) {
                TimerView(
                    timeRemaining = uiState.timeRemaining,
                    progress = uiState.timerProgress,
                )
            }

            // Question text
            uiState.currentQuestion?.let { question ->
                Text(
                    text = question.text,
                    style = IOSTypography.title3,
                    color = KBTheme.textPrimary(),
                    textAlign = TextAlign.Center,
                )
            }

            // Answer input
            OutlinedTextField(
                value = uiState.userAnswer,
                onValueChange = { viewModel.updateUserAnswer(it) },
                label = { Text(stringResource(R.string.kb_type_your_answer)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (uiState.userAnswer.isNotEmpty()) {
                                viewModel.submitAnswer()
                            }
                        },
                    ),
            )

            Text(
                text = stringResource(R.string.kb_press_enter_to_submit),
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
            )
        }

        // Submit button
        if (uiState.userAnswer.isNotEmpty()) {
            Button(
                onClick = { viewModel.submitAnswer() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = uiState.selectedDomain?.color() ?: KBTheme.mastered(),
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.kb_submit_answer),
                    style = IOSTypography.headline,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DrillProgressHeader(uiState: DrillUiState) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KBTheme.bgSecondary())
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Domain badge
        uiState.selectedDomain?.let { domain ->
            Text(
                text = stringResource(domain.stringResId),
                style = IOSTypography.subheadline,
                fontWeight = FontWeight.Bold,
                color = domain.color(),
                modifier =
                    Modifier
                        .background(
                            domain.color().copy(alpha = 0.2f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Progress
        Text(
            text = "${uiState.currentIndex + 1} / ${uiState.totalQuestions}",
            style = IOSTypography.subheadline,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
        )

        // Score
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = KBTheme.mastered(),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "${uiState.correctCount}",
                style = IOSTypography.subheadline,
                color = KBTheme.textPrimary(),
            )
        }
    }
}

@Composable
private fun TimerView(
    timeRemaining: Double,
    progress: Float,
) {
    val color = if (progress > 0.3f) KBTheme.mastered() else KBTheme.timerCritical()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(60.dp),
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 4.dp,
            trackColor = KBTheme.bgSecondary(),
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = "${timeRemaining.toInt()}",
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun FeedbackView(
    uiState: DrillUiState,
    viewModel: KBDomainDrillViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Result indicator
        Icon(
            imageVector = if (uiState.lastWasCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = if (uiState.lastWasCorrect) KBTheme.mastered() else KBTheme.timerCritical(),
        )

        Text(
            text =
                stringResource(
                    if (uiState.lastWasCorrect) R.string.kb_correct else R.string.kb_incorrect,
                ),
            style = IOSTypography.largeTitle,
            fontWeight = FontWeight.Bold,
            color = if (uiState.lastWasCorrect) KBTheme.mastered() else KBTheme.timerCritical(),
        )

        uiState.lastQuestion?.let { question ->
            if (!uiState.lastWasCorrect) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.kb_correct_answer_label),
                        style = IOSTypography.subheadline,
                        color = KBTheme.textSecondary(),
                    )
                    Text(
                        text = question.answer.primary,
                        style = IOSTypography.headline,
                        color = KBTheme.textPrimary(),
                    )
                }
            }

            question.source?.let { source ->
                Text(
                    text = stringResource(R.string.kb_source_label, source),
                    style = IOSTypography.caption,
                    color = KBTheme.textSecondary(),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.nextQuestion() },
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = uiState.selectedDomain?.color() ?: KBTheme.mastered(),
                ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (uiState.hasMoreQuestions) R.string.kb_next_question else R.string.kb_see_results,
                    ),
                style = IOSTypography.headline,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ResultsView(
    uiState: DrillUiState,
    viewModel: KBDomainDrillViewModel,
    onNavigateBack: () -> Unit,
) {
    val percentFormatter = NumberFormat.getPercentInstance(Locale.getDefault())

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Summary header
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.selectedDomain?.let { domain ->
                    Text(
                        text = stringResource(domain.stringResId),
                        style = IOSTypography.headline,
                        color = domain.color(),
                        modifier =
                            Modifier
                                .background(
                                    domain.color().copy(alpha = 0.2f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.kb_drill_complete),
                    style = IOSTypography.largeTitle,
                    fontWeight = FontWeight.Bold,
                    color = KBTheme.textPrimary(),
                )
            }
        }

        // Score circle
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val accuracyColor = getAccuracyColor(uiState.accuracy)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp),
                ) {
                    CircularProgressIndicator(
                        progress = { uiState.accuracy.toFloat() },
                        modifier = Modifier.fillMaxSize(),
                        color = accuracyColor,
                        strokeWidth = 12.dp,
                        trackColor = KBTheme.bgSecondary(),
                        strokeCap = StrokeCap.Round,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = percentFormatter.format(uiState.accuracy),
                            style = IOSTypography.largeTitle,
                            fontWeight = FontWeight.Bold,
                            color = KBTheme.textPrimary(),
                        )
                        Text(
                            text = "${uiState.correctCount} / ${uiState.questionResults.size}",
                            style = IOSTypography.subheadline,
                            color = KBTheme.textSecondary(),
                        )
                    }
                }
            }
        }

        // Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.kb_avg_time),
                    value = stringResource(R.string.kb_seconds_value, uiState.averageTime),
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.kb_best_streak),
                    value = "${uiState.bestStreak}",
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.kb_drill_difficulty),
                    value = uiState.finalDifficulty,
                )
            }
        }

        // Question breakdown
        if (uiState.questionResults.isNotEmpty()) {
            item {
                HorizontalDivider(color = KBTheme.bgSecondary())
            }

            item {
                Text(
                    text = stringResource(R.string.kb_question_breakdown),
                    style = IOSTypography.headline,
                    color = KBTheme.textPrimary(),
                )
            }

            itemsIndexed(uiState.questionResults) { index, result ->
                QuestionResultRow(index = index, result = result)
            }
        }

        // Action buttons
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.restartDrill() },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = uiState.selectedDomain?.color() ?: KBTheme.mastered(),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.kb_drill_again),
                        style = IOSTypography.headline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetToSetup() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.kb_choose_different_domain),
                        style = IOSTypography.body,
                    )
                }

                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.kb_done),
                        style = IOSTypography.body,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = IOSTypography.headline,
                color = KBTheme.textPrimary(),
            )
            Text(
                text = title,
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
            )
        }
    }
}

@Composable
private fun QuestionResultRow(
    index: Int,
    result: DrillQuestionResult,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KBTheme.bgSecondary(), RoundedCornerShape(8.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Q${index + 1}",
            style = IOSTypography.subheadline,
            fontWeight = FontWeight.Bold,
            color = KBTheme.textPrimary(),
            modifier = Modifier.size(30.dp),
        )

        Icon(
            imageVector = if (result.correct) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (result.correct) KBTheme.mastered() else KBTheme.timerCritical(),
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = result.question.answer.primary,
            style = IOSTypography.subheadline,
            color = KBTheme.textPrimary(),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = stringResource(R.string.kb_seconds_value, result.responseTime),
            style = IOSTypography.caption,
            color = KBTheme.textSecondary(),
        )
    }
}

@Composable
private fun getAccuracyColor(accuracy: Double): Color {
    return when {
        accuracy >= 0.8 -> KBTheme.mastered()
        accuracy >= 0.6 -> KBTheme.intermediate()
        else -> KBTheme.timerCritical()
    }
}
