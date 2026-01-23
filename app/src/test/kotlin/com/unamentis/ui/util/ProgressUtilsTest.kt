package com.unamentis.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ProgressUtils.
 *
 * Tests progress value sanitization utilities that prevent NaN
 * and other invalid values from reaching Compose progress indicators.
 */
class ProgressUtilsTest {
    // region safeProgress(Float?)

    @Test
    fun `safeProgress returns zero for null`() {
        assertEquals(0f, safeProgress(null as Float?), 0.0001f)
    }

    @Test
    fun `safeProgress returns zero for NaN`() {
        assertEquals(0f, safeProgress(Float.NaN), 0.0001f)
    }

    @Test
    fun `safeProgress returns zero for negative infinity`() {
        assertEquals(0f, safeProgress(Float.NEGATIVE_INFINITY), 0.0001f)
    }

    @Test
    fun `safeProgress returns one for positive infinity`() {
        assertEquals(1f, safeProgress(Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun `safeProgress clamps negative values to zero`() {
        assertEquals(0f, safeProgress(-0.5f), 0.0001f)
        assertEquals(0f, safeProgress(-100f), 0.0001f)
    }

    @Test
    fun `safeProgress clamps values above one to one`() {
        assertEquals(1f, safeProgress(1.5f), 0.0001f)
        assertEquals(1f, safeProgress(100f), 0.0001f)
    }

    @Test
    fun `safeProgress preserves valid values in range`() {
        assertEquals(0f, safeProgress(0f), 0.0001f)
        assertEquals(0.5f, safeProgress(0.5f), 0.0001f)
        assertEquals(1f, safeProgress(1f), 0.0001f)
        assertEquals(0.75f, safeProgress(0.75f), 0.0001f)
    }

    // endregion

    // region safeProgress(Double?)

    @Test
    fun `safeProgress Double returns zero for null`() {
        assertEquals(0f, safeProgress(null as Double?), 0.0001f)
    }

    @Test
    fun `safeProgress Double returns zero for NaN`() {
        assertEquals(0f, safeProgress(Double.NaN), 0.0001f)
    }

    @Test
    fun `safeProgress Double preserves valid values`() {
        assertEquals(0.5f, safeProgress(0.5), 0.0001f)
        assertEquals(0.75f, safeProgress(0.75), 0.0001f)
    }

    // endregion

    // region safeProgressRatio

    @Test
    fun `safeProgressRatio returns zero for zero total`() {
        assertEquals(0f, safeProgressRatio(5, 0), 0.0001f)
    }

    @Test
    fun `safeProgressRatio returns zero for negative total`() {
        assertEquals(0f, safeProgressRatio(5, -10), 0.0001f)
    }

    @Test
    fun `safeProgressRatio returns zero for NaN total`() {
        assertEquals(0f, safeProgressRatio(5, Float.NaN), 0.0001f)
    }

    @Test
    fun `safeProgressRatio returns zero for infinite total`() {
        assertEquals(0f, safeProgressRatio(5, Float.POSITIVE_INFINITY), 0.0001f)
    }

    @Test
    fun `safeProgressRatio calculates correct ratio`() {
        assertEquals(0.5f, safeProgressRatio(5, 10), 0.0001f)
        assertEquals(0.25f, safeProgressRatio(1, 4), 0.0001f)
        assertEquals(1f, safeProgressRatio(10, 10), 0.0001f)
    }

    @Test
    fun `safeProgressRatio clamps ratio above one`() {
        assertEquals(1f, safeProgressRatio(15, 10), 0.0001f)
    }

    @Test
    fun `safeProgressRatio handles negative current`() {
        assertEquals(0f, safeProgressRatio(-5, 10), 0.0001f)
    }

    // endregion

    // region safeProgressInRange

    @Test
    fun `safeProgressInRange returns min for null`() {
        assertEquals(0f, safeProgressInRange(null, 0f, 100f), 0.0001f)
        assertEquals(10f, safeProgressInRange(null, 10f, 100f), 0.0001f)
    }

    @Test
    fun `safeProgressInRange returns min for NaN`() {
        assertEquals(0f, safeProgressInRange(Float.NaN, 0f, 100f), 0.0001f)
    }

    @Test
    fun `safeProgressInRange returns min for infinite`() {
        assertEquals(0f, safeProgressInRange(Float.POSITIVE_INFINITY, 0f, 100f), 0.0001f)
        assertEquals(0f, safeProgressInRange(Float.NEGATIVE_INFINITY, 0f, 100f), 0.0001f)
    }

    @Test
    fun `safeProgressInRange returns min when max equals min`() {
        assertEquals(50f, safeProgressInRange(75f, 50f, 50f), 0.0001f)
    }

    @Test
    fun `safeProgressInRange returns min when max is less than min`() {
        assertEquals(100f, safeProgressInRange(75f, 100f, 50f), 0.0001f)
    }

    @Test
    fun `safeProgressInRange clamps to range`() {
        assertEquals(0f, safeProgressInRange(-10f, 0f, 100f), 0.0001f)
        assertEquals(100f, safeProgressInRange(150f, 0f, 100f), 0.0001f)
    }

    @Test
    fun `safeProgressInRange preserves valid values`() {
        assertEquals(50f, safeProgressInRange(50f, 0f, 100f), 0.0001f)
        assertEquals(0f, safeProgressInRange(0f, 0f, 100f), 0.0001f)
        assertEquals(100f, safeProgressInRange(100f, 0f, 100f), 0.0001f)
    }

    // endregion
}
