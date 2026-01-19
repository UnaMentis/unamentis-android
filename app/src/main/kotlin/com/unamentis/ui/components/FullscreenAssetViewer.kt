package com.unamentis.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Data class representing an asset for display.
 *
 * @param url URL of the asset
 * @param type Type of asset (image, video, etc.)
 * @param title Optional title for the asset
 * @param description Optional description
 */
data class Asset(
    val url: String,
    val type: AssetType = AssetType.IMAGE,
    val title: String? = null,
    val description: String? = null,
)

/**
 * Types of assets supported.
 */
enum class AssetType {
    IMAGE,
    VIDEO,
}

/**
 * Fullscreen viewer for assets with paging support.
 *
 * Features:
 * - Fullscreen overlay
 * - Pinch-to-zoom on images
 * - Swipe between multiple assets
 * - Page indicator
 * - Share and download actions
 * - Tap to toggle controls visibility
 *
 * @param assets List of assets to display
 * @param initialIndex Initial asset index to show
 * @param onDismiss Callback when viewer is closed
 * @param onShare Optional callback to share an asset
 * @param onDownload Optional callback to download an asset
 */
@Composable
fun FullscreenAssetViewer(
    assets: List<Asset>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onShare: ((Asset) -> Unit)? = null,
    onDownload: ((Asset) -> Unit)? = null,
) {
    if (assets.isEmpty()) {
        onDismiss()
        return
    }

    var showControls by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { assets.size }
    val currentAsset = assets.getOrNull(pagerState.currentPage)

    // Handle back press
    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Asset pager
                HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Toggle controls on tap
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.pressed }) {
                                            showControls = !showControls
                                        }
                                    }
                                }
                            },
                ) { page ->
                    val asset = assets[page]
                    when (asset.type) {
                        AssetType.IMAGE -> {
                            ZoomableImageWithState(
                                imageUrl = asset.url,
                                contentDescription = asset.title ?: "Image ${page + 1}",
                                modifier = Modifier.fillMaxSize(),
                                placeholder = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = Color.White,
                                    )
                                },
                                error = {
                                    Text(
                                        text = "Failed to load image",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                            )
                        }
                        AssetType.VIDEO -> {
                            // Video player placeholder
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Video playback not yet implemented",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }

                // Controls overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top bar
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(8.dp)
                                    .align(Alignment.TopStart),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Close button
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                )
                            }

                            // Page indicator
                            if (assets.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${assets.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }

                            // Action buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (onShare != null && currentAsset != null) {
                                    IconButton(onClick = { onShare(currentAsset) }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color.White,
                                        )
                                    }
                                }
                                if (onDownload != null && currentAsset != null) {
                                    IconButton(onClick = { onDownload(currentAsset) }) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom bar with title/description
                        if (currentAsset?.title != null || currentAsset?.description != null) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(16.dp)
                                        .align(Alignment.BottomStart),
                            ) {
                                currentAsset.title?.let {
                                    Text(
                                        text = it,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                currentAsset.description?.let {
                                    Text(
                                        text = it,
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }

                        // Page dots indicator
                        if (assets.size > 1) {
                            Row(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(
                                            bottom = if (currentAsset?.title != null || currentAsset?.description != null) 80.dp else 16.dp,
                                        ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                repeat(assets.size) { index ->
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .background(
                                                    color =
                                                        if (index == pagerState.currentPage) {
                                                            Color.White
                                                        } else {
                                                            Color.White.copy(alpha = 0.5f)
                                                        },
                                                    shape = MaterialTheme.shapes.small,
                                                ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single asset fullscreen viewer (convenience wrapper).
 *
 * @param asset Asset to display
 * @param onDismiss Callback when viewer is closed
 * @param onShare Optional callback to share the asset
 * @param onDownload Optional callback to download the asset
 */
@Composable
fun FullscreenAssetViewer(
    asset: Asset,
    onDismiss: () -> Unit,
    onShare: ((Asset) -> Unit)? = null,
    onDownload: ((Asset) -> Unit)? = null,
) {
    FullscreenAssetViewer(
        assets = listOf(asset),
        initialIndex = 0,
        onDismiss = onDismiss,
        onShare = onShare,
        onDownload = onDownload,
    )
}
