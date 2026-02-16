package com.unamentis.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for FormulaRenderer utilities and data models.
 *
 * Tests the pure functions used by the FormulaRenderer component:
 * - LaTeX to Unicode fallback conversion
 * - KaTeX HTML generation
 * - FormulaSemantics and VariableDefinition data classes
 */
class FormulaRendererTest {
    // ==========================================================================
    // formatLatexForDisplay - Greek letters
    // ==========================================================================

    @Test
    fun `formatLatexForDisplay converts alpha to Unicode`() {
        val result = formatLatexForDisplay("\\alpha")
        assertEquals("\u03B1", result)
    }

    @Test
    fun `formatLatexForDisplay converts multiple Greek letters`() {
        val result = formatLatexForDisplay("\\alpha + \\beta = \\gamma")
        assertEquals("\u03B1 + \u03B2 = \u03B3", result)
    }

    @Test
    fun `formatLatexForDisplay converts uppercase Greek letters`() {
        val result = formatLatexForDisplay("\\Delta x")
        assertEquals("\u0394 x", result)
    }

    @Test
    fun `formatLatexForDisplay converts pi`() {
        val result = formatLatexForDisplay("2\\pi r")
        assertEquals("2\u03C0 r", result)
    }

    // ==========================================================================
    // formatLatexForDisplay - Mathematical operators
    // ==========================================================================

    @Test
    fun `formatLatexForDisplay converts sum operator`() {
        val result = formatLatexForDisplay("\\sum")
        assertEquals("\u2211", result)
    }

    @Test
    fun `formatLatexForDisplay converts infinity symbol`() {
        val result = formatLatexForDisplay("\\infty")
        assertEquals("\u221E", result)
    }

    @Test
    fun `formatLatexForDisplay converts plus-minus`() {
        val result = formatLatexForDisplay("\\pm")
        assertEquals("\u00B1", result)
    }

    @Test
    fun `formatLatexForDisplay converts times symbol`() {
        val result = formatLatexForDisplay("a \\times b")
        assertEquals("a \u00D7 b", result)
    }

    @Test
    fun `formatLatexForDisplay converts comparison operators`() {
        val result = formatLatexForDisplay("x \\leq y \\geq z")
        assertEquals("x \u2264 y \u2265 z", result)
    }

    @Test
    fun `formatLatexForDisplay converts arrows`() {
        val result = formatLatexForDisplay("A \\rightarrow B")
        assertEquals("A \u2192 B", result)
    }

    @Test
    fun `formatLatexForDisplay converts not-equal`() {
        val result = formatLatexForDisplay("x \\neq 0")
        assertEquals("x \u2260 0", result)
    }

    // ==========================================================================
    // formatLatexForDisplay - Fractions
    // ==========================================================================

    @Test
    fun `formatLatexForDisplay converts simple fraction`() {
        val result = formatLatexForDisplay("\\frac{a}{b}")
        assertEquals("(a)/(b)", result)
    }

    @Test
    fun `formatLatexForDisplay converts fraction with expressions`() {
        val result = formatLatexForDisplay("\\frac{x+1}{x-1}")
        assertEquals("(x+1)/(x-1)", result)
    }

    // ==========================================================================
    // formatLatexForDisplay - Superscripts and subscripts
    // ==========================================================================

    @Test
    fun `formatLatexForDisplay simplifies superscripts`() {
        val result = formatLatexForDisplay("x^{2}")
        assertEquals("x^2", result)
    }

    @Test
    fun `formatLatexForDisplay simplifies subscripts`() {
        val result = formatLatexForDisplay("a_{n}")
        assertEquals("a_n", result)
    }

    @Test
    fun `formatLatexForDisplay handles simple superscript without braces`() {
        val result = formatLatexForDisplay("E = mc^2")
        assertEquals("E = mc^2", result)
    }

    // ==========================================================================
    // formatLatexForDisplay - Complex formulas
    // ==========================================================================

    @Test
    fun `formatLatexForDisplay handles quadratic formula`() {
        val result = formatLatexForDisplay("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
        assertTrue(result.contains("\u00B1")) // plus-minus
        assertTrue(result.contains("\u221A")) // sqrt
        assertFalse(result.contains("\\"))
    }

    @Test
    fun `formatLatexForDisplay handles Pythagorean theorem`() {
        val result = formatLatexForDisplay("a^{2} + b^{2} = c^{2}")
        assertEquals("a^2 + b^2 = c^2", result)
    }

    @Test
    fun `formatLatexForDisplay handles empty string`() {
        val result = formatLatexForDisplay("")
        assertEquals("", result)
    }

    @Test
    fun `formatLatexForDisplay handles plain text without LaTeX`() {
        val result = formatLatexForDisplay("hello world")
        assertEquals("hello world", result)
    }

    @Test
    fun `formatLatexForDisplay removes unhandled backslash commands`() {
        val result = formatLatexForDisplay("\\text{hello}")
        assertEquals("texthello", result)
    }

    // ==========================================================================
    // buildKaTeXHtml - Basic structure
    // ==========================================================================

    @Test
    fun `buildKaTeXHtml returns valid HTML document`() {
        val html = buildKaTeXHtml("E = mc^2", 18, true, false)
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<html>"))
        assertTrue(html.contains("</html>"))
        assertTrue(html.contains("katex"))
    }

    @Test
    fun `buildKaTeXHtml includes KaTeX CDN link`() {
        val html = buildKaTeXHtml("x^2", 18, true, false)
        assertTrue(html.contains("cdn.jsdelivr.net/npm/katex"))
    }

    @Test
    fun `buildKaTeXHtml includes formula content`() {
        val html = buildKaTeXHtml("E = mc^2", 18, true, false)
        assertTrue(html.contains("E = mc^2"))
    }

    @Test
    fun `buildKaTeXHtml respects font size`() {
        val html = buildKaTeXHtml("x", 24, true, false)
        assertTrue(html.contains("24px"))
    }

    @Test
    fun `buildKaTeXHtml respects display mode true`() {
        val html = buildKaTeXHtml("x", 18, true, false)
        assertTrue(html.contains("displayMode: true"))
    }

    @Test
    fun `buildKaTeXHtml respects display mode false`() {
        val html = buildKaTeXHtml("x", 18, false, false)
        assertTrue(html.contains("displayMode: false"))
    }

    // ==========================================================================
    // buildKaTeXHtml - Theme support
    // ==========================================================================

    @Test
    fun `buildKaTeXHtml uses light theme colors`() {
        val html = buildKaTeXHtml("x", 18, true, false)
        assertTrue(html.contains("#1C1B1F"))
    }

    @Test
    fun `buildKaTeXHtml uses dark theme colors`() {
        val html = buildKaTeXHtml("x", 18, true, true)
        assertTrue(html.contains("#E0E0E0"))
    }

    // ==========================================================================
    // buildKaTeXHtml - Special character escaping
    // ==========================================================================

    @Test
    fun `buildKaTeXHtml escapes backslashes`() {
        val html = buildKaTeXHtml("\\frac{a}{b}", 18, true, false)
        // Backslashes should be escaped for JS string
        assertTrue(html.contains("\\\\frac"))
    }

    @Test
    fun `buildKaTeXHtml escapes quotes`() {
        val html = buildKaTeXHtml("\\text{\"hello\"}", 18, true, false)
        assertTrue(html.contains("\\\""))
    }

    @Test
    fun `buildKaTeXHtml has transparent background`() {
        val html = buildKaTeXHtml("x", 18, true, false)
        assertTrue(html.contains("transparent"))
    }

    // ==========================================================================
    // FormulaSemantics data class
    // ==========================================================================

    @Test
    fun `FormulaSemantics default values are null`() {
        val semantics = FormulaSemantics()
        assertNull(semantics.category)
        assertNull(semantics.commonName)
        assertNull(semantics.variables)
    }

    @Test
    fun `FormulaSemantics stores all properties`() {
        val variables =
            listOf(
                VariableDefinition("x", "horizontal position", "m"),
                VariableDefinition("t", "time", "s"),
            )
        val semantics =
            FormulaSemantics(
                category = "physics",
                commonName = "Position Equation",
                variables = variables,
            )
        assertEquals("physics", semantics.category)
        assertEquals("Position Equation", semantics.commonName)
        assertNotNull(semantics.variables)
        assertEquals(2, semantics.variables!!.size)
    }

    @Test
    fun `FormulaSemantics equality works correctly`() {
        val semantics1 = FormulaSemantics(category = "math", commonName = "Test")
        val semantics2 = FormulaSemantics(category = "math", commonName = "Test")
        assertEquals(semantics1, semantics2)
    }

    // ==========================================================================
    // VariableDefinition data class
    // ==========================================================================

    @Test
    fun `VariableDefinition stores all properties`() {
        val variable = VariableDefinition("v", "velocity", "m/s")
        assertEquals("v", variable.symbol)
        assertEquals("velocity", variable.meaning)
        assertEquals("m/s", variable.unit)
    }

    @Test
    fun `VariableDefinition unit is optional`() {
        val variable = VariableDefinition("n", "count")
        assertEquals("n", variable.symbol)
        assertEquals("count", variable.meaning)
        assertNull(variable.unit)
    }

    @Test
    fun `VariableDefinition equality works correctly`() {
        val v1 = VariableDefinition("x", "position", "m")
        val v2 = VariableDefinition("x", "position", "m")
        assertEquals(v1, v2)
    }
}
