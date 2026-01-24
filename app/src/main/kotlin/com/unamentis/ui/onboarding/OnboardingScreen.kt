package com.unamentis.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.iOSGreen
import com.unamentis.ui.theme.iOSOrange
import com.unamentis.ui.theme.iOSPurple

/**
 * Data class representing a single onboarding page.
 */
data class OnboardingPage(
    val icon: ImageVector?,
    val iconColor: Color,
    val title: String,
    val subtitle: String,
    val description: String,
    val tips: List<String>,
)

/**
 * Main onboarding screen composable.
 *
 * Displays a series of pages introducing the app's key features.
 *
 * @param onComplete Called when user completes or skips onboarding
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }

    val pages =
        listOf(
            OnboardingPage(
                icon = null,
                iconColor = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.onboarding_welcome_title),
                subtitle = stringResource(R.string.onboarding_welcome_subtitle),
                description = stringResource(R.string.onboarding_welcome_description),
                tips =
                    listOf(
                        stringResource(R.string.onboarding_welcome_tip1),
                        stringResource(R.string.onboarding_welcome_tip2),
                        stringResource(R.string.onboarding_welcome_tip3),
                    ),
            ),
            OnboardingPage(
                icon = Icons.Default.Book,
                iconColor = iOSOrange,
                title = stringResource(R.string.onboarding_curriculum_title),
                subtitle = stringResource(R.string.onboarding_curriculum_subtitle),
                description = stringResource(R.string.onboarding_curriculum_description),
                tips =
                    listOf(
                        stringResource(R.string.onboarding_curriculum_tip1),
                        stringResource(R.string.onboarding_curriculum_tip2),
                        stringResource(R.string.onboarding_curriculum_tip3),
                    ),
            ),
            OnboardingPage(
                icon = Icons.Default.PhoneAndroid,
                iconColor = iOSGreen,
                title = stringResource(R.string.onboarding_offline_title),
                subtitle = stringResource(R.string.onboarding_offline_subtitle),
                description = stringResource(R.string.onboarding_offline_description),
                tips =
                    listOf(
                        stringResource(R.string.onboarding_offline_tip1),
                        stringResource(R.string.onboarding_offline_tip2),
                        stringResource(R.string.onboarding_offline_tip3),
                    ),
            ),
            OnboardingPage(
                icon = Icons.Default.PanTool,
                iconColor = iOSPurple,
                title = stringResource(R.string.onboarding_handsfree_title),
                subtitle = stringResource(R.string.onboarding_handsfree_subtitle),
                description = stringResource(R.string.onboarding_handsfree_description),
                tips =
                    listOf(
                        stringResource(R.string.onboarding_handsfree_tip1),
                        stringResource(R.string.onboarding_handsfree_tip2),
                        stringResource(R.string.onboarding_handsfree_tip3),
                    ),
            ),
        )

    val isLastPage = currentPage == pages.lastIndex

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Dimensions.SpacingXLarge),
        ) {
            // Header with logo and skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                TextButton(
                    onClick = onComplete,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Skip onboarding"
                        },
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))

            // Page content
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "page_animation",
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    showLogo = page == 0,
                )
            }

            // Page indicators
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimensions.SpacingLarge),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.forEachIndexed { index, _ ->
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = Dimensions.SpacingXSmall)
                                .size(if (index == currentPage) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentPage) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                ),
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = { currentPage-- },
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Go back"
                            },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            onComplete()
                        } else {
                            currentPage++
                        }
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (isLastPage) {
                                    "Complete onboarding and get started"
                                } else {
                                    "Next page"
                                }
                        },
                ) {
                    Text(
                        if (isLastPage) {
                            stringResource(R.string.onboarding_get_started)
                        } else {
                            stringResource(R.string.onboarding_next)
                        },
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content for a single onboarding page.
 */
@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    showLogo: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))

        // Icon or logo
        if (showLogo) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(Dimensions.OnboardingIconSize),
                tint = page.iconColor,
            )
        } else if (page.icon != null) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.OnboardingIconSize),
                tint = page.iconColor,
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))

        // Title
        Text(
            text = page.title,
            style = IOSTypography.title2,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))

        // Subtitle
        Text(
            text = page.subtitle,
            style = IOSTypography.headline,
            color = page.iconColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))

        // Description
        Text(
            text = page.description,
            style = IOSTypography.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimensions.ScreenHorizontalPadding),
        )

        Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))

        // Tips box
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.ScreenHorizontalPadding),
            shape = RoundedCornerShape(Dimensions.CardCornerRadius),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.CardPadding),
            ) {
                page.tips.forEach { tip ->
                    TipItem(tip = tip)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.SpacingXLarge))
    }
}

/**
 * Single tip item with checkmark.
 */
@Composable
private fun TipItem(tip: String) {
    Row(
        modifier = Modifier.padding(vertical = Dimensions.SpacingXSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iOSGreen,
        )
        Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
        Text(
            text = tip,
            style = IOSTypography.body,
            modifier = Modifier.weight(1f),
        )
    }
}
