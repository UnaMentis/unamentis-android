package com.unamentis.ui.curriculum

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography

/**
 * Available specialized training modules.
 */
enum class TrainingModule(
    val id: String,
    val icon: ImageVector,
    val color: Color,
) {
    KNOWLEDGE_BOWL(
        id = "knowledge-bowl",
        icon = Icons.Default.Psychology,
        color = Color(0xFF9C27B0), // Purple
    ),
    // Future modules can be added here
    // SAT_PREP("sat-prep", Icons.Default.School, Color(0xFF2196F3)),
}

/**
 * Modules section showing available specialized training modules.
 *
 * Displays a list of training modules (like Knowledge Bowl) that users can
 * launch for specialized practice.
 *
 * @param onLaunchModule Callback when a module is selected
 */
@Composable
fun ModulesSection(
    onLaunchModule: (TrainingModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Dimensions.ScreenHorizontalPadding,
            vertical = Dimensions.SpacingLarge,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        item {
            ModulesSectionHeader()
        }

        item {
            // Knowledge Bowl module card
            ModuleCard(
                module = TrainingModule.KNOWLEDGE_BOWL,
                title = stringResource(R.string.module_knowledge_bowl_title),
                description = stringResource(R.string.module_knowledge_bowl_description),
                features = listOf(
                    stringResource(R.string.module_kb_feature_domains),
                    stringResource(R.string.module_kb_feature_practice),
                    stringResource(R.string.module_kb_feature_competition),
                ),
                onClick = { onLaunchModule(TrainingModule.KNOWLEDGE_BOWL) },
            )
        }

        // Placeholder for future modules
        item {
            Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
            ComingSoonSection()
        }
    }
}

/**
 * Header for the modules section.
 */
@Composable
private fun ModulesSectionHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        Text(
            text = stringResource(R.string.modules_section_title),
            style = IOSTypography.title2,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.modules_section_subtitle),
            style = IOSTypography.subheadline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Card displaying a training module with its features.
 */
@Composable
private fun ModuleCard(
    module: TrainingModule,
    title: String,
    description: String,
    features: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardContentDescription = stringResource(R.string.cd_module_card, title)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardContentDescription }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                // Module icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(module.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = null,
                        tint = module.color,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = IOSTypography.headline,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = description,
                        style = IOSTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Features list
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
            ) {
                features.forEach { feature ->
                    FeatureBadge(
                        text = feature,
                        color = module.color,
                    )
                }
            }

            // Launch button hint
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = module.color.copy(alpha = 0.1f),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Dimensions.SpacingMedium,
                        vertical = Dimensions.SpacingSmall,
                    ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tap_to_start_practicing),
                        style = IOSTypography.subheadline,
                        color = module.color,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Feature badge showing a module capability.
 */
@Composable
private fun FeatureBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(
            text = text,
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Section showing coming soon modules.
 */
@Composable
private fun ComingSoonSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        Text(
            text = stringResource(R.string.coming_soon),
            style = IOSTypography.headline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Placeholder cards for future modules
        ComingSoonCard(
            icon = Icons.Default.School,
            title = stringResource(R.string.module_sat_prep_title),
            description = stringResource(R.string.module_sat_prep_description),
        )
    }
}

/**
 * Card for a coming soon module.
 */
@Composable
private fun ComingSoonCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = IOSTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = description,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = stringResource(R.string.coming_soon_badge),
                    style = IOSTypography.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
