package com.unamentis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/**
 * Horizontal carousel for displaying multiple assets.
 *
 * Features:
 * - Horizontal paging/swiping between assets
 * - Page indicators
 * - Tap to open fullscreen viewer
 * - Thumbnail preview mode
 * - Video type indicator overlay
 *
 * @param assets List of assets to display
 * @param modifier Modifier for the carousel
 * @param aspectRatio Aspect ratio for carousel items (default 16:9)
 * @param contentPadding Padding around the carousel
 * @param pageSpacing Spacing between pages
 * @param onAssetClick Callback when asset is clicked, provides asset and index
 */
@Composable
fun AssetCarousel(
    assets: List<Asset>,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    pageSpacing: Dp = 8.dp,
    onAssetClick: ((Asset, Int) -> Unit)? = null,
) {
    if (assets.isEmpty()) return

    val pagerState = rememberPagerState { assets.size }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = contentPadding,
            pageSpacing = pageSpacing,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val asset = assets[page]

            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clickable(enabled = onAssetClick != null) {
                            onAssetClick?.invoke(asset, page)
                        },
                shape = MaterialTheme.shapes.medium,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                AssetThumbnail(
                    asset = asset,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Page indicators
        if (assets.size > 1) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(assets.size) { index ->
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (index == pagerState.currentPage) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    },
                                ),
                    )
                }
            }
        }
    }
}

/**
 * Single asset thumbnail with loading/error states.
 *
 * @param asset Asset to display
 * @param modifier Modifier for the thumbnail
 */
@Composable
fun AssetThumbnail(
    asset: Asset,
    modifier: Modifier = Modifier,
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = asset.url,
            contentDescription = asset.title ?: "Asset thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onState = { imageState = it },
        )

        // Loading indicator
        if (imageState is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }

        // Error state
        if (imageState is AsyncImagePainter.State.Error) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = "Failed to load image",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Video type indicator
        if (asset.type == AssetType.VIDEO) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    modifier = Modifier.size(64.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * Grid layout for displaying assets.
 *
 * @param assets List of assets to display
 * @param modifier Modifier for the grid
 * @param columns Number of columns
 * @param aspectRatio Aspect ratio for grid items
 * @param spacing Spacing between grid items
 * @param onAssetClick Callback when asset is clicked
 */
@Composable
fun AssetGrid(
    assets: List<Asset>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    aspectRatio: Float = 1f,
    spacing: Dp = 4.dp,
    onAssetClick: ((Asset, Int) -> Unit)? = null,
) {
    if (assets.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        assets.chunked(columns).forEachIndexed { rowIndex, rowAssets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                rowAssets.forEachIndexed { columnIndex, asset ->
                    val globalIndex = rowIndex * columns + columnIndex
                    Card(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(aspectRatio)
                                .clickable(enabled = onAssetClick != null) {
                                    onAssetClick?.invoke(asset, globalIndex)
                                },
                        shape = MaterialTheme.shapes.small,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        AssetThumbnail(
                            asset = asset,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Fill remaining space with empty boxes if row is not full
                repeat(columns - rowAssets.size) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(aspectRatio),
                    )
                }
            }
        }
    }
}

/**
 * Single asset display with tap to open fullscreen.
 *
 * @param asset Asset to display
 * @param modifier Modifier for the display
 * @param aspectRatio Aspect ratio for the asset
 * @param onTap Callback when asset is tapped
 */
@Composable
fun SingleAssetDisplay(
    asset: Asset,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    onTap: (() -> Unit)? = null,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .then(
                    if (onTap != null) {
                        Modifier.clickable { onTap() }
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        AssetThumbnail(
            asset = asset,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
