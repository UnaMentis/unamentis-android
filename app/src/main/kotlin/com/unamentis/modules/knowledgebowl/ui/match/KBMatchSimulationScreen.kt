package com.unamentis.modules.knowledgebowl.ui.match

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.core.match.KBTeam
import com.unamentis.modules.knowledgebowl.core.match.MatchFormat
import com.unamentis.modules.knowledgebowl.core.match.MatchPhase
import com.unamentis.modules.knowledgebowl.core.match.OpponentStrength
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBRegion
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSRed
import com.unamentis.ui.util.safeProgress
import java.text.NumberFormat
import java.util.Locale

/**
 * Match simulation screen for competing against AI opponents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBMatchSimulationScreen(
    onNavigateBack: () -> Unit,
    viewModel: KBMatchSimulationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_match_simulation)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSetupComplete && uiState.matchSummary == null) {
                            // Confirm exit during match
                            // For now, just reset and go back
                            viewModel.reset()
                        }
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KBTheme.bgPrimary(),
                ),
            )
        },
        containerColor = KBTheme.bgPrimary(),
    ) { paddingValues ->
        Box(
            modifier = Modifier
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

                !uiState.isSetupComplete -> {
                    SetupView(
                        uiState = uiState,
                        onRegionSelected = viewModel::selectRegion,
                        onFormatSelected = viewModel::selectFormat,
                        onStrengthSelected = viewModel::selectOpponentStrength,
                        onTeamNameChanged = viewModel::setPlayerTeamName,
                        onStartMatch = viewModel::startMatch,
                    )
                }

                uiState.matchSummary != null -> {
                    ResultsView(
                        summary = uiState.matchSummary!!,
                        onFinish = {
                            viewModel.reset()
                            onNavigateBack()
                        },
                    )
                }

                else -> {
                    MatchContentView(
                        uiState = uiState,
                        onSelectAnswer = viewModel::selectWrittenAnswer,
                        onSubmitWrittenAnswer = viewModel::submitWrittenAnswer,
                        onStartOralRounds = viewModel::startOralRounds,
                        onPlayerBuzz = viewModel::playerBuzz,
                        onSubmitOralAnswer = viewModel::submitOralAnswer,
                        onSkipRebound = viewModel::skipReboundOpportunity,
                        onNextRound = viewModel::startNextOralRound,
                        onFinishMatch = viewModel::finishMatch,
                    )
                }
            }
        }
    }
}

/**
 * Match setup view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupView(
    uiState: MatchSimulationUiState,
    onRegionSelected: (KBRegion) -> Unit,
    onFormatSelected: (MatchFormat) -> Unit,
    onStrengthSelected: (OpponentStrength) -> Unit,
    onTeamNameChanged: (String) -> Unit,
    onStartMatch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimensions.ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SectionSpacing),
    ) {
        Text(
            text = stringResource(R.string.kb_setup_match),
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )

        // Team name
        SetupSection(title = stringResource(R.string.kb_your_team)) {
            OutlinedTextField(
                value = uiState.playerTeamName,
                onValueChange = onTeamNameChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        // Region selection
        SetupSection(title = stringResource(R.string.kb_competition_region)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(KBRegion.COLORADO, KBRegion.MINNESOTA, KBRegion.WASHINGTON).forEachIndexed { index, region ->
                    SegmentedButton(
                        selected = uiState.selectedRegion == region,
                        onClick = { onRegionSelected(region) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = 3,
                        ),
                    ) {
                        Text(region.abbreviation)
                    }
                }
            }
        }

        // Match format
        SetupSection(title = stringResource(R.string.kb_match_format)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchFormat.entries.forEach { format ->
                    FormatOptionCard(
                        format = format,
                        isSelected = uiState.selectedFormat == format,
                        onClick = { onFormatSelected(format) },
                    )
                }
            }
        }

        // Opponent strength
        SetupSection(title = stringResource(R.string.kb_opponent_strength)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                OpponentStrength.entries.forEachIndexed { index, strength ->
                    SegmentedButton(
                        selected = uiState.selectedOpponentStrength == strength,
                        onClick = { onStrengthSelected(strength) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = OpponentStrength.entries.size,
                        ),
                    ) {
                        Text(
                            text = stringResource(strength.displayNameResId),
                            style = IOSTypography.caption,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Start button
        Button(
            onClick = onStartMatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KBTheme.mastered(),
            ),
        ) {
            Text(
                text = stringResource(R.string.kb_start_match),
                style = IOSTypography.headline,
            )
        }
    }
}

@Composable
private fun SetupSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = IOSTypography.headline,
            color = KBTheme.textPrimary(),
        )
        content()
    }
}

@Composable
private fun FormatOptionCard(
    format: MatchFormat,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) KBTheme.mastered().copy(alpha = 0.1f) else KBTheme.bgSecondary(),
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, KBTheme.mastered())
        } else null,
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(format.displayNameResId),
                    style = IOSTypography.subheadline,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${format.writtenQuestions} written + ${format.oralRounds} oral rounds",
                    style = IOSTypography.caption,
                    color = KBTheme.textSecondary(),
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = KBTheme.mastered(),
                )
            }
        }
    }
}

/**
 * Main match content view that switches based on phase.
 */
@Composable
private fun MatchContentView(
    uiState: MatchSimulationUiState,
    onSelectAnswer: (Int) -> Unit,
    onSubmitWrittenAnswer: () -> Unit,
    onStartOralRounds: () -> Unit,
    onPlayerBuzz: () -> Unit,
    onSubmitOralAnswer: (String) -> Unit,
    onSkipRebound: () -> Unit,
    onNextRound: () -> Unit,
    onFinishMatch: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Scoreboard
        ScoreboardCard(teams = uiState.teams)

        // Phase-specific content
        when (uiState.phase) {
            is MatchPhase.WrittenRound -> {
                WrittenRoundView(
                    uiState = uiState,
                    onSelectAnswer = onSelectAnswer,
                    onSubmitAnswer = onSubmitWrittenAnswer,
                )
            }

            is MatchPhase.WrittenReview -> {
                ReviewView(
                    title = stringResource(R.string.kb_written_round),
                    teams = uiState.teams,
                    actionLabel = stringResource(R.string.kb_start_oral_rounds),
                    onAction = onStartOralRounds,
                )
            }

            is MatchPhase.OralRound -> {
                OralRoundView(
                    uiState = uiState,
                    onPlayerBuzz = onPlayerBuzz,
                    onSubmitAnswer = onSubmitOralAnswer,
                    onSkipRebound = onSkipRebound,
                )
            }

            is MatchPhase.OralReview -> {
                val isLastRound = uiState.oralProgress.currentRound >= uiState.oralProgress.totalRounds
                ReviewView(
                    title = stringResource(R.string.kb_oral_round_n, uiState.phase.roundNumber),
                    teams = uiState.teams,
                    actionLabel = if (isLastRound) {
                        stringResource(R.string.kb_finish_match)
                    } else {
                        stringResource(R.string.kb_next_round)
                    },
                    onAction = if (isLastRound) onFinishMatch else onNextRound,
                )
            }

            is MatchPhase.FinalResults -> {
                // Handled in parent
            }

            else -> {}
        }
    }
}

/**
 * Scoreboard showing team standings.
 */
@Composable
private fun ScoreboardCard(teams: List<KBTeam>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.ScreenHorizontalPadding),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(modifier = Modifier.padding(Dimensions.CardPadding)) {
            Text(
                text = stringResource(R.string.kb_scoreboard),
                style = IOSTypography.subheadline,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            teams.sortedByDescending { it.totalScore }.forEachIndexed { index, team ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "#${index + 1}",
                            style = IOSTypography.caption,
                            color = KBTheme.textSecondary(),
                        )
                        Text(
                            text = team.name,
                            style = IOSTypography.subheadline,
                            fontWeight = if (team.isPlayer) FontWeight.Bold else FontWeight.Normal,
                            color = if (team.isPlayer) KBTheme.mastered() else KBTheme.textPrimary(),
                        )
                    }
                    Text(
                        text = "${team.totalScore}",
                        style = IOSTypography.headline,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Written round view with MCQ.
 */
@Composable
private fun WrittenRoundView(
    uiState: MatchSimulationUiState,
    onSelectAnswer: (Int) -> Unit,
    onSubmitAnswer: () -> Unit,
) {
    val question = uiState.currentQuestion ?: return
    val (current, total) = uiState.writtenProgress

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        // Progress
        ProgressHeader(
            label = stringResource(R.string.kb_written_round),
            current = current + 1,
            total = total,
        )

        // Question card
        QuestionCard(
            question = question,
            selectedAnswer = uiState.selectedAnswer,
            showFeedback = uiState.showFeedback,
            isCorrect = uiState.lastAnswerCorrect,
            onSelectAnswer = onSelectAnswer,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Submit button
        Button(
            onClick = onSubmitAnswer,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.selectedAnswer != null && !uiState.showFeedback,
        ) {
            Text(stringResource(R.string.kb_submit_answer))
        }
    }
}

@Composable
private fun ProgressHeader(
    label: String,
    current: Int,
    total: Int,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = IOSTypography.subheadline,
                color = KBTheme.textSecondary(),
            )
            Text(
                text = "$current / $total",
                style = IOSTypography.subheadline,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { safeProgress(current.toFloat() / total) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuestionCard(
    question: KBQuestion,
    selectedAnswer: Int?,
    showFeedback: Boolean,
    isCorrect: Boolean?,
    onSelectAnswer: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Domain badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = question.domain.color().copy(alpha = 0.2f),
            ) {
                Text(
                    text = stringResource(question.domain.stringResId),
                    style = IOSTypography.caption,
                    color = question.domain.color(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Question text
            Text(
                text = question.text,
                style = IOSTypography.body,
            )

            // MCQ options
            question.mcqOptions?.forEachIndexed { index, option ->
                val isSelected = selectedAnswer == index
                val isCorrectAnswer = option == question.answer.primary

                val backgroundColor = when {
                    showFeedback && isCorrectAnswer -> iOSGreen.copy(alpha = 0.2f)
                    showFeedback && isSelected && !isCorrectAnswer -> iOSRed.copy(alpha = 0.2f)
                    isSelected -> KBTheme.mastered().copy(alpha = 0.2f)
                    else -> Color.Transparent
                }

                val borderColor = when {
                    showFeedback && isCorrectAnswer -> iOSGreen
                    showFeedback && isSelected && !isCorrectAnswer -> iOSRed
                    isSelected -> KBTheme.mastered()
                    else -> KBTheme.border()
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable(enabled = !showFeedback) { onSelectAnswer(index) },
                    color = backgroundColor,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${('A' + index)}.",
                            style = IOSTypography.subheadline,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            text = option,
                            style = IOSTypography.subheadline,
                            modifier = Modifier.weight(1f),
                        )
                        if (showFeedback && isCorrectAnswer) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = iOSGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (showFeedback && isSelected && !isCorrectAnswer) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = iOSRed,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Oral round view with buzz simulation.
 */
@Composable
private fun OralRoundView(
    uiState: MatchSimulationUiState,
    onPlayerBuzz: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onSkipRebound: () -> Unit,
) {
    val question = uiState.currentQuestion ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Progress
        val progress = uiState.oralProgress
        ProgressHeader(
            label = stringResource(R.string.kb_oral_round_n, progress.currentRound),
            current = progress.currentQuestion + 1,
            total = progress.questionsPerRound,
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Question
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.CardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = question.domain.color().copy(alpha = 0.2f),
                ) {
                    Text(
                        text = stringResource(question.domain.stringResId),
                        style = IOSTypography.caption,
                        color = question.domain.color(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = question.text,
                    style = IOSTypography.body,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Buzz state
        when {
            uiState.isWaitingForBuzz -> {
                Text(
                    text = stringResource(R.string.kb_waiting_for_buzz),
                    style = IOSTypography.subheadline,
                    color = KBTheme.textSecondary(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            uiState.opponentAnswer != null -> {
                OpponentAnswerCard(
                    answer = uiState.opponentAnswer,
                    isCorrect = uiState.opponentAnswerCorrect ?: false,
                    teamName = uiState.buzzResult?.let { buzz ->
                        uiState.teams.find { it.id == buzz.teamId }?.name
                    } ?: "Opponent",
                )

                if (uiState.isReboundOpportunity) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.kb_rebound_opportunity),
                        style = IOSTypography.headline,
                        color = KBTheme.mastered(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSkipRebound) {
                            Text(stringResource(R.string.kb_signal_pass))
                        }
                        Button(onClick = { onSubmitAnswer(question.answer.primary) }) {
                            Text(stringResource(R.string.kb_buzz))
                        }
                    }
                }
            }

            uiState.buzzResult != null && uiState.teams.find { it.id == uiState.buzzResult.teamId }?.isPlayer == true -> {
                if (uiState.playerBuzzed || uiState.showFeedback) {
                    // Show answer input or feedback
                    if (uiState.showFeedback) {
                        FeedbackCard(isCorrect = uiState.lastAnswerCorrect ?: false)
                    } else {
                        Text(
                            text = stringResource(R.string.kb_you_buzzed_first),
                            style = IOSTypography.headline,
                            color = KBTheme.mastered(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { onSubmitAnswer(question.answer.primary) }) {
                            Text(stringResource(R.string.kb_submit_answer))
                        }
                    }
                } else {
                    BuzzButton(onClick = onPlayerBuzz)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
    }
}

@Composable
private fun BuzzButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = iOSRed),
    ) {
        Text(
            text = stringResource(R.string.kb_buzz),
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OpponentAnswerCard(
    answer: String,
    isCorrect: Boolean,
    teamName: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) iOSGreen.copy(alpha = 0.1f) else iOSRed.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.kb_opponent_answered, teamName),
                style = IOSTypography.caption,
                color = KBTheme.textSecondary(),
            )
            Text(
                text = "\"$answer\"",
                style = IOSTypography.headline,
            )
            Icon(
                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = if (isCorrect) iOSGreen else iOSRed,
            )
        }
    }
}

@Composable
private fun FeedbackCard(isCorrect: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) iOSGreen.copy(alpha = 0.1f) else iOSRed.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = if (isCorrect) iOSGreen else iOSRed,
            )
            Text(
                text = if (isCorrect) {
                    stringResource(R.string.cd_kb_correct_answer)
                } else {
                    stringResource(R.string.cd_kb_incorrect_answer)
                },
                style = IOSTypography.headline,
            )
        }
    }
}

/**
 * Review view between rounds.
 */
@Composable
private fun ReviewView(
    title: String,
    teams: List<KBTeam>,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        Text(
            text = title,
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.kb_round_review),
            style = IOSTypography.subheadline,
            color = KBTheme.textSecondary(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(actionLabel)
        }
    }
}

/**
 * Final results view.
 */
@Composable
private fun ResultsView(
    summary: com.unamentis.modules.knowledgebowl.core.match.KBMatchSummary,
    onFinish: () -> Unit,
) {
    val percentFormatter = NumberFormat.getPercentInstance(Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Trophy icon
        Icon(
            Icons.Default.EmojiEvents,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = if (summary.playerWon) Color(0xFFFFD700) else KBTheme.textSecondary(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.kb_match_results),
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )

        if (summary.playerWon) {
            Text(
                text = stringResource(R.string.kb_you_won),
                style = IOSTypography.headline,
                color = KBTheme.mastered(),
            )
        }

        Text(
            text = stringResource(R.string.kb_rank_format, summary.playerRank),
            style = IOSTypography.title1,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Final standings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        ) {
            Column(modifier = Modifier.padding(Dimensions.CardPadding)) {
                Text(
                    text = stringResource(R.string.kb_scoreboard),
                    style = IOSTypography.headline,
                )
                Spacer(modifier = Modifier.height(8.dp))

                summary.teams.forEachIndexed { index, team ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "#${index + 1}",
                                style = IOSTypography.subheadline,
                                color = KBTheme.textSecondary(),
                            )
                            Text(
                                text = team.name,
                                style = IOSTypography.subheadline,
                                fontWeight = if (team.isPlayer) FontWeight.Bold else FontWeight.Normal,
                                color = if (team.isPlayer) KBTheme.mastered() else KBTheme.textPrimary(),
                            )
                        }
                        Text(
                            text = "${team.totalScore}",
                            style = IOSTypography.headline,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingMedium))

        // Player stats
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KBTheme.bgSecondary()),
        ) {
            Column(modifier = Modifier.padding(Dimensions.CardPadding)) {
                Text(
                    text = stringResource(R.string.kb_your_performance),
                    style = IOSTypography.headline,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatColumn(
                        label = stringResource(R.string.kb_written_accuracy),
                        value = percentFormatter.format(summary.playerStats.writtenAccuracy),
                    )
                    StatColumn(
                        label = stringResource(R.string.kb_oral_accuracy),
                        value = percentFormatter.format(summary.playerStats.oralAccuracy),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(stringResource(R.string.done))
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
            color = KBTheme.mastered(),
        )
        Text(
            text = label,
            style = IOSTypography.caption,
            color = KBTheme.textSecondary(),
        )
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

// Extension to get domain color
@Composable
private fun com.unamentis.modules.knowledgebowl.data.model.KBDomain.color(): Color {
    return com.unamentis.modules.knowledgebowl.ui.theme.color(this)
}
