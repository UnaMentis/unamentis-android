package com.unamentis.modules.knowledgebowl.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBQuestion
import com.unamentis.modules.knowledgebowl.data.model.KBStudyMode
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.ui.theme.IOSTypography

/**
 * Bottom sheet shown before starting a practice session.
 *
 * Displays mode information, loads questions, and lets the user
 * start when ready.
 *
 * @param mode The study mode to prepare for
 * @param sheetState State of the bottom sheet
 * @param onStart Callback when user taps start with loaded questions
 * @param onDismiss Callback when user dismisses the sheet
 * @param viewModel ViewModel for this launcher
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBPracticeLauncherSheet(
    mode: KBStudyMode,
    sheetState: SheetState,
    onStart: (List<KBQuestion>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: KBPracticeLauncherViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val loadedQuestions by viewModel.loadedQuestions.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()

    // Load questions when mode changes
    LaunchedEffect(mode) {
        viewModel.setMode(mode)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mode header
            ModeHeader(mode = selectedMode)

            Spacer(modifier = Modifier.height(24.dp))

            // Content based on state
            when {
                isLoading -> LoadingContent()
                errorMessage != null -> ErrorContent(message = errorMessage!!, onRetry = viewModel::retry)
                else ->
                    ReadyContent(
                        questionCount = loadedQuestions.size,
                        mode = selectedMode,
                        difficultyDescription = viewModel.difficultyDescription,
                        tips = viewModel.tipsForMode,
                    )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            ActionButtons(
                isReady = loadedQuestions.isNotEmpty() && !isLoading,
                mode = selectedMode,
                onStart = { onStart(loadedQuestions) },
                onCancel = onDismiss,
            )
        }
    }
}

@Composable
private fun ModeHeader(mode: KBStudyMode) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = iconForMode(mode),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = colorForMode(mode),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(mode.displayNameResId),
            style = IOSTypography.title2,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(mode.descriptionResId),
            style = IOSTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 32.dp),
    ) {
        CircularProgressIndicator()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.kb_preparing_questions),
            style = IOSTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 24.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = IOSTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.kb_try_again))
        }
    }
}

@Composable
private fun ReadyContent(
    questionCount: Int,
    mode: KBStudyMode,
    difficultyDescription: String,
    tips: List<String>,
) {
    Column {
        // Session info card
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
        ) {
            Column {
                InfoRow(label = stringResource(R.string.kb_questions_label), value = "$questionCount")

                if (mode == KBStudyMode.SPEED) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(
                        label = stringResource(R.string.kb_time_limit_label),
                        value = stringResource(R.string.kb_time_limit_minutes, 5),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                InfoRow(label = stringResource(R.string.kb_difficulty_label), value = difficultyDescription)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tips section
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KBTheme.tipBackground())
                    .padding(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.kb_tips_title),
                    style = IOSTypography.subheadline,
                    color = KBTheme.tipText(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                tips.forEach { tip ->
                    TipRow(tip = tip)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = IOSTypography.body,
        )
    }
}

@Composable
private fun TipRow(tip: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = KBTheme.tipIcon(),
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = tip,
            style = IOSTypography.caption,
            color = KBTheme.tipText(),
        )
    }
}

@Composable
private fun ActionButtons(
    isReady: Boolean,
    mode: KBStudyMode,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = onStart,
            enabled = isReady,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colorForMode(mode),
                ),
        ) {
            Text(
                text = stringResource(R.string.kb_start_practice),
                style = IOSTypography.subheadline,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.kb_cancel))
        }
    }
}

/**
 * Get icon for study mode.
 */
private fun iconForMode(mode: KBStudyMode): ImageVector =
    when (mode) {
        KBStudyMode.DIAGNOSTIC -> Icons.Default.PieChart
        KBStudyMode.TARGETED -> Icons.Default.Star
        KBStudyMode.BREADTH -> Icons.Default.GridOn
        KBStudyMode.SPEED -> Icons.Default.Speed
        KBStudyMode.COMPETITION -> Icons.Default.Star
        KBStudyMode.TEAM -> Icons.Default.Groups
    }

/**
 * Get color for study mode.
 */
@Composable
private fun colorForMode(mode: KBStudyMode): Color =
    when (mode) {
        KBStudyMode.DIAGNOSTIC -> KBTheme.science()
        KBStudyMode.TARGETED -> KBTheme.mathematics()
        KBStudyMode.BREADTH -> KBTheme.literature()
        KBStudyMode.SPEED -> KBTheme.currentEvents()
        KBStudyMode.COMPETITION -> KBTheme.history()
        KBStudyMode.TEAM -> KBTheme.socialStudies()
    }
