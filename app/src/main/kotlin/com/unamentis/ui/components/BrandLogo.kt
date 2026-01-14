package com.unamentis.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unamentis.R

/**
 * Reusable brand logo component with configurable size.
 *
 * Matching iOS BrandLogo.swift implementation for visual parity.
 *
 * Usage:
 * - [Size.Compact] (24dp): For toolbar/navigation bar placement
 * - [Size.Standard] (32dp): For headers and prominent placement
 * - [Size.Large] (48dp): For onboarding and splash screens
 *
 * @param size The size variant to use
 * @param modifier Optional modifier for additional customization
 * @param expanded Whether to show the expanded logo with text (default: false)
 */
@Composable
fun BrandLogo(
    size: Size = Size.Standard,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val logoResource = if (expanded) R.drawable.ic_logo_expanded else R.drawable.ic_logo

    Image(
        painter = painterResource(id = logoResource),
        contentDescription = null,
        modifier = modifier
            .height(size.height)
            .semantics {
                contentDescription = "UnaMentis logo"
            },
        contentScale = ContentScale.Fit,
    )
}

/**
 * Size variants for the brand logo, matching iOS implementation.
 */
enum class Size(val height: Dp) {
    /** 24dp - For toolbar/navigation bar placement */
    Compact(24.dp),

    /** 32dp - For headers and prominent placement */
    Standard(32.dp),

    /** 48dp - For onboarding and splash screens */
    Large(48.dp),

    /** 80dp - For onboarding page icons (matching iOS) */
    XLarge(80.dp),
}
