package com.figapps.fractioncalculatorv1.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A self-contained palette for the calculator surface. Kept separate from the Material3
 * [androidx.compose.material3.ColorScheme] so the calculator keeps its own identity while the
 * rest of the app can still use the generated theme.
 */
data class CalcColors(
    val background: Color,
    val displayTop: Color,
    val displayBottom: Color,
    val displayText: Color,
    val displaySubText: Color,
    val fractionNumerator: Color,
    val fractionDenominator: Color,
    val fractionNumeratorText: Color,
    val fractionDenominatorText: Color,
    /** Higher-contrast variants used for the active fraction text drawn inside the blue display. */
    val displayFractionNumerator: Color,
    val displayFractionDenominator: Color,
    val numberKey: Color,
    val numberKeyText: Color,
    val functionKey: Color,
    val functionKeyText: Color,
    val signKey: Color,
    val signKeyText: Color,
    val operatorKey: Color,
    val operatorKeyText: Color,
    val equalsKey: Color,
    val equalsKeyText: Color,
    val accentKey: Color,
    val accentKeyText: Color,
    /**
     * Two-piece identification: a hue-contrasted (warm vs cool), colour-blind-safe pair used wherever
     * a diagram shows two distinct physical pieces. [piecePrimary]/[pieceSecondary] are the muted
     * surface fills; the `...Label` variants are the saturated, readable colours (WCAG AA against
     * [cardBackground]) used for strokes, leader lines and chip text, so a piece's colour association
     * survives when its chip floats away from the geometry. Deliberately NOT red-vs-green.
     */
    val piecePrimary: Color,
    val piecePrimaryLabel: Color,
    val pieceSecondary: Color,
    val pieceSecondaryLabel: Color,
    val cardBackground: Color,
    val errorText: Color,
    val divider: Color,
    val focusHighlight: Color,
    val placeholder: Color
)

val LightCalcColors = CalcColors(
    background = Color(0xFFEEF1F5),
    displayTop = Color(0xFF4F7FD4),
    displayBottom = Color(0xFF3A63B4),
    displayText = Color(0xFFFFFFFF),
    displaySubText = Color(0xFFD4E0F7),
    fractionNumerator = Color(0xFFFFC24D),
    fractionDenominator = Color(0xFF7FE0FF),
    fractionNumeratorText = Color(0xFF2B1E00),
    fractionDenominatorText = Color(0xFF002633),
    displayFractionNumerator = Color(0xFFFFF4CC),
    displayFractionDenominator = Color(0xFFCFF2FF),
    numberKey = Color(0xFFFFFFFF),
    numberKeyText = Color(0xFF1B1F24),
    functionKey = Color(0xFFE4685D),
    functionKeyText = Color(0xFFFFFFFF),
    signKey = Color(0xFF5B8DEF),
    signKeyText = Color(0xFFFFFFFF),
    operatorKey = Color(0xFF5A6B85),
    operatorKeyText = Color(0xFFFFFFFF),
    equalsKey = Color(0xFFFFA726),
    equalsKeyText = Color(0xFFFFFFFF),
    accentKey = Color(0xFF3F8E5A),
    accentKeyText = Color(0xFFFFFFFF),
    piecePrimary = Color(0xFFE08A2E),
    piecePrimaryLabel = Color(0xFF9A4F00),
    pieceSecondary = Color(0xFF3E7BC4),
    pieceSecondaryLabel = Color(0xFF1B4F86),
    cardBackground = Color(0xFFFFFFFF),
    errorText = Color(0xFFFF6B6B),
    divider = Color(0xFFD7DEE8),
    focusHighlight = Color(0xFF4F7FD4),
    placeholder = Color(0xFFB4BECB)
)

val DarkCalcColors = CalcColors(
    background = Color(0xFF121417),
    displayTop = Color(0xFF3F66AC),
    displayBottom = Color(0xFF2E4E8C),
    displayText = Color(0xFFF5F8FF),
    displaySubText = Color(0xFFBFD0EC),
    fractionNumerator = Color(0xFFFFD166),
    fractionDenominator = Color(0xFF8FE8FF),
    fractionNumeratorText = Color(0xFF2B1E00),
    fractionDenominatorText = Color(0xFF002633),
    displayFractionNumerator = Color(0xFFFFE9A8),
    displayFractionDenominator = Color(0xFFA9E9FF),
    numberKey = Color(0xFF2A2E33),
    numberKeyText = Color(0xFFF2F4F7),
    functionKey = Color(0xFFC9574D),
    functionKeyText = Color(0xFFFFFFFF),
    signKey = Color(0xFF4B78D1),
    signKeyText = Color(0xFFFFFFFF),
    operatorKey = Color(0xFF3C4757),
    operatorKeyText = Color(0xFFF2F4F7),
    equalsKey = Color(0xFFE0941F),
    equalsKeyText = Color(0xFFFFFFFF),
    accentKey = Color(0xFF35855A),
    accentKeyText = Color(0xFFFFFFFF),
    piecePrimary = Color(0xFFD98A3C),
    piecePrimaryLabel = Color(0xFFF0A85A),
    pieceSecondary = Color(0xFF5A9BE0),
    pieceSecondaryLabel = Color(0xFF8FC2F5),
    cardBackground = Color(0xFF1C1F24),
    errorText = Color(0xFFFF8A8A),
    divider = Color(0xFF2E333A),
    focusHighlight = Color(0xFF7FA6F0),
    placeholder = Color(0xFF5A636E)
)

/**
 * Whether the custom palette should use its dark variant. Provided by the app theme so the custom
 * calculator palette and the Material3 scheme always agree, and so the user's theme setting
 * (System/Light/Dark) drives both.
 */
val LocalCalcDark = staticCompositionLocalOf { false }

/** Access to the active calculator palette for the current theme. */
object CalcTheme {
    val colors: CalcColors
        @Composable get() = if (LocalCalcDark.current) DarkCalcColors else LightCalcColors
}

/** Shared corner radius for calculator surfaces. */
val CalcCornerRadius: Dp = 18.dp
