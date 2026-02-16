package com.unamentis.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.unamentis.R
import com.unamentis.ui.theme.Dimensions
import com.unamentis.ui.theme.IOSTypography
import com.unamentis.ui.theme.UnaMentisTheme
import kotlinx.serialization.Serializable
import android.graphics.Color as AndroidColor

// ==========================================================================
// FormulaRenderer - LaTeX formula rendering component
//
// Renders LaTeX math formulas using WebView with KaTeX (loaded from CDN).
// Provides both inline and block display modes, dark/light theme support,
// and a fallback Unicode text renderer when WebView is unavailable.
//
// iOS Parity: FormulaRendererView.swift
// ==========================================================================

/**
 * Renders a LaTeX formula using a WebView with KaTeX.
 *
 * This is the primary composable for displaying mathematical formulas.
 * It uses an embedded WebView with the KaTeX library for high-fidelity
 * LaTeX rendering that matches the iOS SwiftMath output.
 *
 * Supports both inline and block (display) modes, and adapts to
 * light/dark theme automatically.
 *
 * @param latex The LaTeX formula string to render
 * @param modifier Modifier to apply to the renderer
 * @param fontSize Font size in scaled pixels for the formula. Defaults to 18.
 * @param displayMode Whether to use block display mode (true) or inline mode (false).
 *                    Display mode centers the formula and adds vertical padding.
 * @param showLoadingIndicator Whether to show a loading spinner while the WebView renders
 */
@Composable
fun FormulaRenderer(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: Int = DEFAULT_FORMULA_FONT_SIZE,
    displayMode: Boolean = true,
    showLoadingIndicator: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val formulaDescription = stringResource(R.string.cd_formula_display, latex)
    var isLoading by remember { mutableStateOf(true) }

    val padding = if (displayMode) Dimensions.SpacingMedium else Dimensions.SpacingXSmall

    Box(
        modifier =
            modifier
                .padding(padding)
                .semantics { contentDescription = formulaDescription },
        contentAlignment = Alignment.Center,
    ) {
        KaTeXWebView(
            latex = latex,
            fontSize = fontSize,
            displayMode = displayMode,
            isDarkTheme = isDark,
            onPageLoaded = { isLoading = false },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = if (displayMode) DISPLAY_MODE_MIN_HEIGHT else INLINE_MODE_MIN_HEIGHT,
                    ),
        )

        if (showLoadingIndicator && isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Compact inline formula view for embedding in text.
 *
 * Uses a smaller font size and inline display mode for formulas
 * that appear within a line of text.
 *
 * @param latex The LaTeX formula string to render
 * @param modifier Modifier to apply to the renderer
 */
@Composable
fun InlineFormula(
    latex: String,
    modifier: Modifier = Modifier,
) {
    FormulaRenderer(
        latex = latex,
        fontSize = INLINE_FORMULA_FONT_SIZE,
        displayMode = false,
        showLoadingIndicator = false,
        modifier = modifier,
    )
}

/**
 * Enhanced equation view with optional title and semantic information.
 *
 * Displays a formula in a card-like container with a tap action to open
 * a fullscreen detail view. Matches iOS EnhancedEquationAssetView.
 *
 * @param latex The LaTeX formula string to render
 * @param modifier Modifier to apply to the view
 * @param title Optional title displayed below the formula
 * @param semantics Optional semantic metadata about the formula
 * @param onFullscreenRequested Callback when the user taps to expand to fullscreen
 */
@Composable
fun EnhancedEquationView(
    latex: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    semantics: FormulaSemantics? = null,
    onFullscreenRequested: () -> Unit = {},
) {
    val accessibilityLabel =
        semantics?.commonName ?: title ?: stringResource(R.string.formula_mathematical_formula)
    val fullscreenHint = stringResource(R.string.cd_formula_fullscreen_hint)

    Box(
        modifier =
            modifier
                .padding(Dimensions.SpacingSmall)
                .clip(RoundedCornerShape(Dimensions.CardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .semantics {
                    contentDescription = "$accessibilityLabel. $fullscreenHint"
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onFullscreenRequested)
                    .padding(Dimensions.CardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
        ) {
            FormulaRenderer(
                latex = latex,
                fontSize = ENHANCED_FORMULA_FONT_SIZE,
                displayMode = true,
                modifier = Modifier.heightIn(min = ENHANCED_MIN_HEIGHT),
            )

            if (title != null) {
                Text(
                    text = title,
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (semantics?.commonName != null) {
                Text(
                    text = semantics.commonName!!,
                    style = IOSTypography.caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Fullscreen formula detail view.
 *
 * Displays a formula prominently with additional context including
 * name, category, variable definitions, and the raw LaTeX source.
 * Shown as a dialog overlay.
 *
 * @param latex The LaTeX formula string to render
 * @param title Optional title for the navigation bar
 * @param semantics Optional semantic metadata about the formula
 * @param onDismiss Callback when the dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenFormulaView(
    latex: String,
    title: String? = null,
    semantics: FormulaSemantics? = null,
    onDismiss: () -> Unit,
) {
    val closeLabel = stringResource(R.string.formula_close)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title ?: stringResource(R.string.formula_fullscreen_title),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = closeLabel,
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXLarge),
            ) {
                // Main formula display
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.CardPadding)
                            .clip(RoundedCornerShape(Dimensions.CardCornerRadius))
                            .background(MaterialTheme.colorScheme.surface),
                ) {
                    FormulaRenderer(
                        latex = latex,
                        fontSize = FULLSCREEN_FORMULA_FONT_SIZE,
                        displayMode = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.CardPadding),
                    )
                }

                // Semantics section
                if (semantics != null) {
                    FormulaSemanticsSection(semantics = semantics)
                }

                // Raw LaTeX source
                LaTeXSourceSection(latex = latex)

                Spacer(modifier = Modifier.height(Dimensions.SpacingXXLarge))
            }
        }
    }
}

// ==========================================================================
// Internal composables
// ==========================================================================

/**
 * WebView wrapper that loads KaTeX from CDN and renders a LaTeX formula.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KaTeXWebView(
    latex: String,
    fontSize: Int,
    displayMode: Boolean,
    isDarkTheme: Boolean,
    onPageLoaded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val html =
        remember(latex, fontSize, displayMode, isDarkTheme) {
            buildKaTeXHtml(latex, fontSize, displayMode, isDarkTheme)
        }

    AndroidView(
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            onPageLoaded()
                        }
                    }
                loadDataWithBaseURL(
                    null,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            val updatedHtml = buildKaTeXHtml(latex, fontSize, displayMode, isDarkTheme)
            webView.loadDataWithBaseURL(
                null,
                updatedHtml,
                "text/html",
                "UTF-8",
                null,
            )
        },
        modifier = modifier,
    )
}

/**
 * Displays semantic information about a formula.
 */
@Composable
private fun FormulaSemanticsSection(semantics: FormulaSemantics) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.CardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingLarge),
    ) {
        if (semantics.commonName != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall)) {
                Text(
                    text = stringResource(R.string.formula_name_label),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = semantics.commonName!!,
                    style = IOSTypography.headline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (semantics.category != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall)) {
                Text(
                    text = stringResource(R.string.formula_category_label),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = semantics.category!!.replaceFirstChar { it.titlecase() },
                    style = IOSTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (!semantics.variables.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall)) {
                Text(
                    text = stringResource(R.string.formula_variables_label),
                    style = IOSTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                semantics.variables!!.forEach { variable ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
                    ) {
                        Text(
                            text = variable.symbol,
                            style =
                                IOSTypography.body.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                ),
                            modifier = Modifier.width(VARIABLE_SYMBOL_WIDTH),
                        )
                        Text(
                            text = stringResource(R.string.formula_equals),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = variable.meaning,
                            style = IOSTypography.subheadline,
                        )
                        if (variable.unit != null) {
                            Text(
                                text = "(${variable.unit})",
                                style = IOSTypography.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Displays the raw LaTeX source code.
 */
@Composable
private fun LaTeXSourceSection(latex: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.CardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
    ) {
        Text(
            text = stringResource(R.string.formula_latex_source_label),
            style = IOSTypography.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = latex,
            style =
                IOSTypography.caption.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimensions.CardCornerRadiusSmall))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .padding(Dimensions.SpacingSmall),
        )
    }
}

/**
 * Fallback formula renderer using Unicode approximations.
 *
 * Used when WebView rendering is not available or in testing contexts.
 * Converts common LaTeX symbols to their Unicode equivalents for
 * basic legibility.
 *
 * @param latex The LaTeX formula string to render
 * @param modifier Modifier to apply to the view
 * @param displayMode Whether to show in block display mode
 */
@Composable
fun FallbackFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    displayMode: Boolean = true,
) {
    val formulaDescription = stringResource(R.string.cd_formula_display, latex)
    val latexRenderingLabel = stringResource(R.string.formula_latex_rendering)

    Column(
        modifier =
            modifier
                .padding(if (displayMode) Dimensions.SpacingMedium else Dimensions.SpacingXSmall)
                .semantics { contentDescription = formulaDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingXSmall),
    ) {
        Text(
            text = formatLatexForDisplay(latex),
            style =
                IOSTypography.body.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (displayMode) {
            Text(
                text = "($latexRenderingLabel)",
                style = IOSTypography.caption2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ==========================================================================
// Data models
// ==========================================================================

/**
 * Semantic information about a mathematical formula.
 *
 * Provides context beyond the LaTeX source, such as the formula's
 * common name, category, and variable definitions.
 *
 * @property category Category of the formula (e.g., "algebraic", "calculus", "physics")
 * @property commonName Common name (e.g., "Quadratic Formula", "Pythagorean Theorem")
 * @property variables List of variable definitions used in the formula
 */
@Immutable
@Serializable
data class FormulaSemantics(
    val category: String? = null,
    val commonName: String? = null,
    val variables: List<VariableDefinition>? = null,
)

/**
 * Definition of a variable used in a formula.
 *
 * @property symbol The variable symbol (e.g., "x", "v")
 * @property meaning Description of what the variable represents
 * @property unit Optional unit of measurement (e.g., "m/s", "kg")
 */
@Immutable
@Serializable
data class VariableDefinition(
    val symbol: String,
    val meaning: String,
    val unit: String? = null,
)

// ==========================================================================
// Helper functions
// ==========================================================================

/**
 * Builds a minimal HTML page that loads KaTeX from CDN and renders a LaTeX formula.
 *
 * @param latex The LaTeX string to render
 * @param fontSize Font size in px for the rendered formula
 * @param displayMode Whether to use KaTeX display mode (block) or inline mode
 * @param isDarkTheme Whether to use dark theme colors
 * @return Complete HTML string ready to load into a WebView
 */
@Suppress("LongMethod")
internal fun buildKaTeXHtml(
    latex: String,
    fontSize: Int,
    displayMode: Boolean,
    isDarkTheme: Boolean,
): String {
    val textColor = if (isDarkTheme) "#E0E0E0" else "#1C1B1F"
    val bgColor = "transparent"
    // Escape backslashes and quotes for JavaScript string embedding
    val escapedLatex =
        latex
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    background: $bgColor;
                    color: $textColor;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    min-height: 100vh;
                    font-size: ${fontSize}px;
                    overflow: hidden;
                }
                #formula {
                    text-align: center;
                    padding: 8px;
                }
                .katex { font-size: 1em; }
            </style>
        </head>
        <body>
            <div id="formula"></div>
            <script>
                try {
                    katex.render("$escapedLatex", document.getElementById("formula"), {
                        displayMode: $displayMode,
                        throwOnError: false,
                        errorColor: "#cc0000",
                        trust: true
                    });
                } catch(e) {
                    document.getElementById("formula").textContent = "$escapedLatex";
                }
            </script>
        </body>
        </html>
        """.trimIndent()
}

/**
 * Converts a LaTeX string to a Unicode approximation for fallback display.
 *
 * This function handles common LaTeX commands by replacing them with
 * their closest Unicode equivalents. Used when WebView rendering
 * is unavailable.
 *
 * @param latex The LaTeX string to convert
 * @return A Unicode-approximated string
 */
@Suppress("LongMethod")
internal fun formatLatexForDisplay(latex: String): String {
    var result = latex

    // Greek letters
    val greekLetters =
        mapOf(
            "\\alpha" to "\u03B1",
            "\\beta" to "\u03B2",
            "\\gamma" to "\u03B3",
            "\\delta" to "\u03B4",
            "\\epsilon" to "\u03B5",
            "\\zeta" to "\u03B6",
            "\\eta" to "\u03B7",
            "\\theta" to "\u03B8",
            "\\iota" to "\u03B9",
            "\\kappa" to "\u03BA",
            "\\lambda" to "\u03BB",
            "\\mu" to "\u03BC",
            "\\nu" to "\u03BD",
            "\\xi" to "\u03BE",
            "\\pi" to "\u03C0",
            "\\rho" to "\u03C1",
            "\\sigma" to "\u03C3",
            "\\tau" to "\u03C4",
            "\\upsilon" to "\u03C5",
            "\\phi" to "\u03C6",
            "\\chi" to "\u03C7",
            "\\psi" to "\u03C8",
            "\\omega" to "\u03C9",
            "\\Alpha" to "\u0391",
            "\\Beta" to "\u0392",
            "\\Gamma" to "\u0393",
            "\\Delta" to "\u0394",
            "\\Theta" to "\u0398",
            "\\Lambda" to "\u039B",
            "\\Xi" to "\u039E",
            "\\Pi" to "\u03A0",
            "\\Sigma" to "\u03A3",
            "\\Phi" to "\u03A6",
            "\\Psi" to "\u03A8",
            "\\Omega" to "\u03A9",
        )

    // Mathematical operators
    val operators =
        mapOf(
            "\\sum" to "\u2211",
            "\\prod" to "\u220F",
            "\\int" to "\u222B",
            "\\partial" to "\u2202",
            "\\nabla" to "\u2207",
            "\\infty" to "\u221E",
            "\\pm" to "\u00B1",
            "\\mp" to "\u2213",
            "\\times" to "\u00D7",
            "\\div" to "\u00F7",
            "\\cdot" to "\u00B7",
            "\\sqrt" to "\u221A",
            "\\approx" to "\u2248",
            "\\neq" to "\u2260",
            "\\leq" to "\u2264",
            "\\geq" to "\u2265",
            "\\subset" to "\u2282",
            "\\supset" to "\u2283",
            "\\in" to "\u2208",
            "\\forall" to "\u2200",
            "\\exists" to "\u2203",
            "\\rightarrow" to "\u2192",
            "\\leftarrow" to "\u2190",
            "\\Rightarrow" to "\u21D2",
            "\\Leftarrow" to "\u21D0",
            "\\leftrightarrow" to "\u2194",
            "\\Leftrightarrow" to "\u21D4",
        )

    // Apply Greek letter substitutions
    for ((latexSymbol, unicode) in greekLetters) {
        result = result.replace(latexSymbol, unicode)
    }

    // Apply operator substitutions
    for ((latexSymbol, unicode) in operators) {
        result = result.replace(latexSymbol, unicode)
    }

    // Handle fractions: \frac{a}{b} -> (a)/(b)
    val fracRegex = Regex("""\\frac\{([^}]*)}\{([^}]*)}""")
    result =
        fracRegex.replace(result) { matchResult ->
            "(${matchResult.groupValues[1]})/(${matchResult.groupValues[2]})"
        }

    // Simplify superscripts and subscripts
    result = result.replace("^{", "^")
    result = result.replace("_{", "_")

    // Remove remaining braces
    result = result.replace("{", "")
    result = result.replace("}", "")

    // Remove remaining backslashes from unhandled commands
    result = result.replace("\\", "")

    return result
}

// ==========================================================================
// Constants
// ==========================================================================

/** Default font size for formula rendering (in px). */
private const val DEFAULT_FORMULA_FONT_SIZE = 18

/** Font size for inline formulas (in px). */
private const val INLINE_FORMULA_FONT_SIZE = 14

/** Font size for enhanced equation view (in px). */
private const val ENHANCED_FORMULA_FONT_SIZE = 20

/** Font size for fullscreen formula view (in px). */
private const val FULLSCREEN_FORMULA_FONT_SIZE = 32

/** Minimum height for display mode formulas. */
private val DISPLAY_MODE_MIN_HEIGHT = 40.dp

/** Minimum height for inline mode formulas. */
private val INLINE_MODE_MIN_HEIGHT = 24.dp

/** Minimum height for enhanced equation view. */
private val ENHANCED_MIN_HEIGHT = 40.dp

/** Width for variable symbol column in semantics display. */
private val VARIABLE_SYMBOL_WIDTH = 30.dp

// ==========================================================================
// Previews
// ==========================================================================

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, name = "FormulaRenderer - Simple")
@Composable
private fun FormulaRendererSimplePreview() {
    UnaMentisTheme {
        FallbackFormulaView(
            latex = "E = mc^2",
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, name = "FormulaRenderer - Quadratic")
@Composable
private fun FormulaRendererQuadraticPreview() {
    UnaMentisTheme {
        FallbackFormulaView(
            latex = "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, name = "FormulaRenderer - Summation")
@Composable
private fun FormulaRendererSummationPreview() {
    UnaMentisTheme {
        FallbackFormulaView(
            latex = "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}",
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, name = "InlineFormula")
@Composable
private fun InlineFormulaPreview() {
    UnaMentisTheme {
        FallbackFormulaView(
            latex = "a^2 + b^2 = c^2",
            displayMode = false,
        )
    }
}
