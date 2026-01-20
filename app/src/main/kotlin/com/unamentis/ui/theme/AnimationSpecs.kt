package com.unamentis.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * iOS-matched animation specifications for consistent motion design.
 *
 * iOS typically uses spring(response: 0.3, dampingFraction: 0.7) for interactive
 * animations. These specs provide Android equivalents for visual parity.
 */
object AnimationSpecs {
    // ==========================================================================
    // SPRING ANIMATIONS (iOS default: response 0.3s, damping 0.7)
    // ==========================================================================

    /**
     * Standard spring animation matching iOS spring(response: 0.3, dampingFraction: 0.7).
     * Use for most interactive animations like button presses, sheet transitions.
     */
    val StandardSpring: AnimationSpec<Float> =
        spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium,
        )

    /**
     * Quick spring for responsive micro-interactions.
     * Use for toggle switches, small indicators.
     */
    val QuickSpring: AnimationSpec<Float> =
        spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        )

    /**
     * Gentle spring for larger movements.
     * Use for sheet presentations, navigation transitions.
     */
    val GentleSpring: AnimationSpec<Float> =
        spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow,
        )

    /**
     * Bouncy spring for playful animations.
     * Use sparingly for celebratory moments like completions.
     */
    val BouncySpring: AnimationSpec<Float> =
        spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium,
        )

    // ==========================================================================
    // TWEEN ANIMATIONS (iOS easeOut patterns)
    // ==========================================================================

    /**
     * Standard easeOut matching iOS easeOut(duration: 0.3).
     * Use for fade transitions, color changes.
     */
    val StandardEaseOut: AnimationSpec<Float> =
        tween(
            durationMillis = Duration.STANDARD,
            easing = FastOutSlowInEasing,
        )

    /**
     * Quick easeOut for immediate feedback.
     * Use for tap highlights, ripples.
     */
    val QuickEaseOut: AnimationSpec<Float> =
        tween(
            durationMillis = Duration.QUICK,
            easing = FastOutSlowInEasing,
        )

    /**
     * Slow easeOut for deliberate transitions.
     * Use for loading states, progress completions.
     */
    val SlowEaseOut: AnimationSpec<Float> =
        tween(
            durationMillis = Duration.SLOW,
            easing = FastOutSlowInEasing,
        )

    // ==========================================================================
    // DURATION CONSTANTS (in milliseconds)
    // ==========================================================================

    /**
     * Standard duration constants matching iOS timing patterns.
     */
    object Duration {
        /** Very quick feedback (50ms) - immediate responses */
        const val INSTANT = 50

        /** Quick animation (100ms) - tap feedback */
        const val QUICK = 100

        /** Standard animation (300ms) - most transitions */
        const val STANDARD = 300

        /** Slow animation (500ms) - emphasized transitions */
        const val SLOW = 500

        /** Long animation (800ms) - major state changes */
        const val LONG = 800
    }
}
