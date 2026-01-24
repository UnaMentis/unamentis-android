package com.unamentis.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unamentis.R
import com.unamentis.ui.components.GlassCapsule
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography

/**
 * Visual asset data for curriculum playback.
 */
data class VisualAsset(
    val id: String,
    val url: String,
    val altText: String,
    val startSegment: Int,
    val endSegment: Int,
    val displayMode: VisualDisplayMode = VisualDisplayMode.PERSISTENT,
)

/**
 * Visual display mode matching iOS VisualDisplayMode.
 */
enum class VisualDisplayMode {
    /** Always visible while active */
    PERSISTENT,

    /** Briefly highlighted then fades */
    HIGHLIGHT,

    /** Shown inline with transcript */
    INLINE,

    /** Full-screen modal popup */
    POPUP,
}

/**
 * Visual asset overlay for curriculum playback.
 *
 * Matches iOS VisualAssetOverlay - shows synchronized visuals during curriculum sessions.
 * Displays at bottom of screen with expand/collapse toggle.
 *
 * @param currentSegment Current segment index in topic playback
 * @param assets List of visual assets for the topic
 * @param isExpanded Whether the overlay is expanded
 * @param onExpandedChange Callback when expanded state changes
 * @param modifier Modifier to apply
 */
@Composable
fun VisualAssetOverlay(
    currentSegment: Int,
    assets: List<VisualAsset>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Filter assets active for current segment
    val activeAssets =
        remember(currentSegment, assets) {
            assets.filter { asset ->
                currentSegment >= asset.startSegment && currentSegment <= asset.endSegment
            }
        }

    // Non-inline assets for overlay display
    val overlayAssets =
        remember(activeAssets) {
            activeAssets.filter { it.displayMode != VisualDisplayMode.INLINE }
        }

    AnimatedVisibility(
        visible = overlayAssets.isNotEmpty(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Expand/collapse toggle button
            VisualOverlayToggle(
                assetCount = overlayAssets.size,
                isExpanded = isExpanded,
                onClick = { onExpandedChange(!isExpanded) },
            )

            // Asset carousel (when expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = spring()),
                exit = shrinkVertically(animationSpec = spring()),
            ) {
                VisualAssetCarousel(
                    assets = overlayAssets,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Toggle button for visual overlay expand/collapse.
 */
@Composable
private fun VisualOverlayToggle(
    assetCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val collapseLabel = stringResource(R.string.collapse_visual_assets)
    val expandLabel = context.resources.getQuantityString(R.plurals.visual_assets, assetCount, assetCount)
    val visualCountText = context.resources.getQuantityString(R.plurals.visual_count, assetCount, assetCount)

    GlassCapsule(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription =
                        if (isExpanded) {
                            collapseLabel
                        } else {
                            expandLabel
                        }
                },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector =
                    if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.Default.KeyboardArrowUp
                    },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = visualCountText,
                style = IOSTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontal carousel for visual assets.
 */
@Composable
fun VisualAssetCarousel(
    assets: List<VisualAsset>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Dimensions.SpacingLarge),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
    ) {
        items(
            items = assets,
            key = { it.id },
        ) { asset ->
            VisualAssetCard(asset = asset)
        }
    }
}

/**
 * Individual visual asset card.
 */
@Composable
fun VisualAssetCard(
    asset: VisualAsset,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .size(width = 200.dp, height = Dimensions.VisualAssetHeightCompact)
                .semantics { contentDescription = asset.altText },
        shape = RoundedCornerShape(Dimensions.CardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        if (asset.url.isNotEmpty()) {
            AsyncImage(
                model = asset.url,
                contentDescription = asset.altText,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Placeholder for missing image
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/**
 * Side panel for visual assets on tablets.
 *
 * Matches iOS VisualAssetSidePanel for iPad layouts.
 *
 * @param currentSegment Current segment index
 * @param assets List of visual assets
 * @param modifier Modifier to apply
 */
@Composable
fun VisualAssetSidePanel(
    currentSegment: Int,
    assets: List<VisualAsset>,
    modifier: Modifier = Modifier,
) {
    val activeAssets =
        remember(currentSegment, assets) {
            assets.filter { asset ->
                currentSegment >= asset.startSegment && currentSegment <= asset.endSegment
            }
        }

    Surface(
        modifier =
            modifier
                .width(Dimensions.VisualAssetSidePanelWidth)
                .fillMaxSize(),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        if (activeAssets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    )
                    Text(
                        text = stringResource(R.string.no_visuals),
                        style = IOSTypography.subheadline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Dimensions.SpacingMedium),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium),
            ) {
                activeAssets.forEach { asset ->
                    VisualAssetCard(
                        asset = asset,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(Dimensions.VisualAssetHeightMedium),
                    )
                }
            }
        }
    }
}
