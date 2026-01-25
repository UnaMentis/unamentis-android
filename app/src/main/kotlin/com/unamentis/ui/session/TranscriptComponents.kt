package com.unamentis.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unamentis.data.model.TranscriptEntry
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSBlue
import com.unamentis.ui.theme.iOSGray5

/**
 * Transcript bubble matching iOS TranscriptBubble styling.
 *
 * User messages: Blue background, white text
 * AI messages: Gray background, primary text
 * Highlighted messages: Blue tint + blue border (for curriculum playback)
 *
 * @param text The message text
 * @param isUser Whether this is a user message
 * @param modifier Modifier to apply
 * @param isHighlighted Whether this message is highlighted (during curriculum playback)
 */
@Composable
fun TranscriptBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // Spacer for user messages (push to right)
        if (isUser) {
            Spacer(modifier = Modifier.weight(1f, fill = false).widthIn(min = 40.dp))
        }

        val backgroundColor =
            when {
                isUser -> iOSBlue
                isHighlighted -> iOSBlue.copy(alpha = 0.2f)
                else -> iOSGray5
            }

        val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

        val borderModifier =
            if (isHighlighted && !isUser) {
                Modifier.border(
                    width = 2.dp,
                    color = iOSBlue.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Dimensions.BubbleCornerRadius),
                )
            } else {
                Modifier
            }

        Box(
            modifier =
                Modifier
                    .widthIn(max = Dimensions.BubbleMaxWidth)
                    .clip(RoundedCornerShape(Dimensions.BubbleCornerRadius))
                    .background(backgroundColor)
                    .then(borderModifier)
                    .padding(
                        horizontal = Dimensions.BubblePaddingHorizontal,
                        vertical = Dimensions.BubblePaddingVertical,
                    ),
        ) {
            Text(
                text = text,
                style = IOSTypography.subheadline,
                color = textColor,
            )
        }

        // Spacer for AI messages (push to left)
        if (!isUser) {
            Spacer(modifier = Modifier.weight(1f, fill = false).widthIn(min = 40.dp))
        }
    }
}

/**
 * Transcript display with glass morphism background.
 *
 * Matches iOS TranscriptView with automatic scrolling and highlighted message support.
 *
 * @param entries List of transcript entries to display
 * @param highlightedId Optional ID of the currently highlighted message
 * @param modifier Modifier to apply
 * @param liveUserText Current in-progress user transcript (not yet in history)
 * @param liveAiText Current in-progress AI response (not yet in history)
 */
@Composable
fun TranscriptDisplay(
    entries: List<TranscriptEntry>,
    modifier: Modifier = Modifier,
    highlightedId: String? = null,
    liveUserText: String = "",
    liveAiText: String = "",
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    val itemCount = entries.size + (if (liveUserText.isNotEmpty()) 1 else 0) + (if (liveAiText.isNotEmpty()) 1 else 0)
    LaunchedEffect(itemCount, liveAiText, liveUserText) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    // Scroll to highlighted message when it changes
    LaunchedEffect(highlightedId) {
        if (highlightedId != null) {
            val index = entries.indexOfFirst { it.id == highlightedId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 1.dp,
    ) {
        if (entries.isEmpty() && liveUserText.isEmpty() && liveAiText.isEmpty()) {
            // Empty state
            TranscriptEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(Dimensions.SpacingLarge),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                // Historical messages
                items(
                    items = entries,
                    key = { it.id },
                ) { entry ->
                    TranscriptBubble(
                        text = entry.text,
                        isUser = entry.role == "user",
                        isHighlighted = entry.id == highlightedId,
                    )
                }

                // Live user text (not yet in history)
                if (liveUserText.isNotEmpty() && entries.none { it.role == "user" && it.text == liveUserText }) {
                    item(key = "liveUser") {
                        TranscriptBubble(
                            text = liveUserText,
                            isUser = true,
                        )
                    }
                }

                // Live AI text (not yet in history)
                if (liveAiText.isNotEmpty() && entries.none { it.role == "assistant" && it.text == liveAiText }) {
                    item(key = "liveAi") {
                        TranscriptBubble(
                            text = liveAiText,
                            isUser = false,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state for transcript display.
 */
@Composable
private fun TranscriptEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.EmptyStateIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = "Start a session to begin",
                style = IOSTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Streaming indicator showing that AI is generating a response.
 */
@Composable
fun StreamingIndicator(modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "streamingAlpha",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.SpacingLarge),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 100.dp)
                    .clip(RoundedCornerShape(Dimensions.BubbleCornerRadius))
                    .background(iOSGray5.copy(alpha = alpha))
                    .padding(
                        horizontal = Dimensions.BubblePaddingHorizontal,
                        vertical = Dimensions.BubblePaddingVertical,
                    ),
        ) {
            Text(
                text = "...",
                style = IOSTypography.subheadline,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}
