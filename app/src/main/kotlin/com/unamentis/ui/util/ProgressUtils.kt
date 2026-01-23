package com.unamentis.ui.util

// Utility functions for sanitizing progress values.
// Compose semantics throws IllegalArgumentException when NaN is passed
// as a progress value. These utilities ensure safe progress values
// are always used.

/**
 * Sanitizes a Float progress value to ensure it's valid for Compose progress indicators.
 *
 * Handles:
 * - null values (returns 0f)
 * - NaN values (returns 0f)
 * - Infinite values (returns 0f for negative infinity, 1f for positive infinity)
 * - Out-of-range values (clamped to 0f..1f)
 *
 * @param value The potentially unsafe progress value
 * @return A safe progress value in the range [0f, 1f]
 */
fun safeProgress(value: Float?): Float {
    val v = value ?: 0f
    return when {
        v.isNaN() -> 0f
        v.isInfinite() -> if (v > 0) 1f else 0f
        else -> v.coerceIn(0f, 1f)
    }
}

/**
 * Sanitizes a Double progress value to ensure it's valid for Compose progress indicators.
 *
 * @param value The potentially unsafe progress value
 * @return A safe progress value in the range [0f, 1f]
 */
fun safeProgress(value: Double?): Float {
    val v = value?.toFloat() ?: 0f
    return safeProgress(v)
}

/**
 * Safely calculates a progress ratio.
 *
 * Prevents division by zero and handles edge cases.
 *
 * @param current The current value
 * @param total The total value (denominator)
 * @return A safe progress value in the range [0f, 1f]
 */
fun safeProgressRatio(
    current: Number,
    total: Number,
): Float {
    val currentFloat = current.toFloat()
    val totalFloat = total.toFloat()

    if (totalFloat <= 0f || totalFloat.isNaN() || totalFloat.isInfinite()) {
        return 0f
    }

    val ratio = currentFloat / totalFloat
    return safeProgress(ratio)
}

/**
 * Sanitizes a progress value within a custom range.
 *
 * Useful for ProgressBarRangeInfo semantics where the range
 * may not be 0f..1f.
 *
 * @param current The current progress value
 * @param min The minimum value of the range
 * @param max The maximum value of the range
 * @return A safe progress value clamped to [min, max]
 */
fun safeProgressInRange(
    current: Float?,
    min: Float,
    max: Float,
): Float {
    val v = current ?: min
    if (v.isNaN() || v.isInfinite()) return min
    if (max <= min) return min
    return v.coerceIn(min, max)
}
