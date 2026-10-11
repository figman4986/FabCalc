package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.ui.CalcCornerRadius
import com.figapps.fractioncalculatorv1.ui.CalcColors
import com.figapps.fractioncalculatorv1.ui.CalcTheme

/** Visual role of a calculator button. */
enum class CalcButtonStyle { NUMBER, FUNCTION, SIGN, OPERATOR, EQUALS, ACCENT, NUMERATOR, DENOMINATOR }

/** A rounded keypad button that shows a text label. */
@Composable
fun CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    style: CalcButtonStyle = CalcButtonStyle.NUMBER,
    fontSize: TextUnit = 24.sp,
    hapticsEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = CalcTheme.colors
    CalcButton(
        modifier = modifier,
        style = style,
        hapticsEnabled = hapticsEnabled,
        onClick = onClick
    ) {
        Text(
            text = label,
            color = style.contentColor(colors),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * A rounded keypad button that accepts arbitrary content. Used for fraction-slot buttons that
 * render a graphic instead of a plain label.
 */
@Composable
fun CalcButton(
    modifier: Modifier = Modifier,
    style: CalcButtonStyle = CalcButtonStyle.NUMBER,
    minHeight: Dp = DEFAULT_KEY_MIN_HEIGHT,
    hapticsEnabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = CalcTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "calcButtonScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = minHeight)
            .clip(RoundedCornerShape(CalcCornerRadius))
            .background(style.containerColor(colors))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (hapticsEnabled) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Default minimum height of a keypad button. */
val DEFAULT_KEY_MIN_HEIGHT: Dp = 44.dp

private fun CalcButtonStyle.containerColor(colors: CalcColors) = when (this) {
    CalcButtonStyle.NUMBER -> colors.numberKey
    CalcButtonStyle.FUNCTION -> colors.functionKey
    CalcButtonStyle.SIGN -> colors.signKey
    CalcButtonStyle.OPERATOR -> colors.operatorKey
    CalcButtonStyle.EQUALS -> colors.equalsKey
    CalcButtonStyle.ACCENT -> colors.accentKey
    CalcButtonStyle.NUMERATOR -> colors.fractionNumerator
    CalcButtonStyle.DENOMINATOR -> colors.fractionDenominator
}

private fun CalcButtonStyle.contentColor(colors: CalcColors) = when (this) {
    CalcButtonStyle.NUMBER -> colors.numberKeyText
    CalcButtonStyle.FUNCTION -> colors.functionKeyText
    CalcButtonStyle.SIGN -> colors.signKeyText
    CalcButtonStyle.OPERATOR -> colors.operatorKeyText
    CalcButtonStyle.EQUALS -> colors.equalsKeyText
    CalcButtonStyle.ACCENT -> colors.accentKeyText
    CalcButtonStyle.NUMERATOR -> colors.fractionNumeratorText
    CalcButtonStyle.DENOMINATOR -> colors.fractionDenominatorText
}
