package com.unamentis.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * A zoomable and pannable image component.
 *
 * Features:
 * - Pinch-to-zoom with configurable min/max scale
 * - Pan when zoomed in
 * - Double-tap to zoom in/out
 * - Smooth animations
 *
 * @param imageUrl URL of the image to display
 * @param contentDescription Accessibility description
 * @param modifier Modifier for the container
 * @param minScale Minimum zoom scale (default 1f)
 * @param maxScale Maximum zoom scale (default 5f)
 * @param onDoubleTap Optional callback when double-tapped
 */
@Composable
fun ZoomableImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    onDoubleTap: (() -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Update scale with limits
                        scale = (scale * zoom).coerceIn(minScale, maxScale)

                        // Update offset (pan) only when zoomed in
                        if (scale > 1f) {
                            // Calculate max offset based on zoom level
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2

                            offset =
                                Offset(
                                    x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY),
                                )
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1f) {
                                // Reset to original scale
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                // Zoom to 2x centered on tap point
                                scale = 2f
                                // Calculate offset to center on tap point
                                offset =
                                    Offset(
                                        x = (size.width / 2f - tapOffset.x) * (scale - 1),
                                        y = (size.height / 2f - tapOffset.y) * (scale - 1),
                                    )
                            }
                            onDoubleTap?.invoke()
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Zoomable image with a placeholder and error state.
 *
 * @param imageUrl URL of the image to display
 * @param contentDescription Accessibility description
 * @param modifier Modifier for the container
 * @param placeholder Composable to show while loading
 * @param error Composable to show on error
 * @param minScale Minimum zoom scale (default 1f)
 * @param maxScale Maximum zoom scale (default 5f)
 */
@Composable
fun ZoomableImageWithState(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
    minScale: Float = 1f,
    maxScale: Float = 5f,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        if (scale > 1f) {
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2
                            offset =
                                Offset(
                                    x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY),
                                )
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2f
                            }
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            contentScale = ContentScale.Fit,
            onLoading = { isLoading = true },
            onSuccess = {
                isLoading = false
                hasError = false
            },
            onError = {
                isLoading = false
                hasError = true
            },
        )

        if (isLoading && placeholder != null) {
            placeholder()
        }

        if (hasError && error != null) {
            error()
        }
    }
}
