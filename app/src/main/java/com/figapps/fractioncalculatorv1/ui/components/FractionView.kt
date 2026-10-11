package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.calculator.Field
import com.figapps.fractioncalculatorv1.calculator.FractionInput
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MixedNumber
import java.math.BigInteger

/**
 * Renders a [Fraction] as a proper fraction: an integer when the denominator is one, otherwise
 * a numerator stacked over a denominator with a thin horizontal bar (mixed numbers included).
 */
@Composable
fun FractionView(
    fraction: Fraction,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 30.sp,
    barThickness: Dp = 2.dp,
    barMinWidth: Dp = 16.dp
) {
    MixedNumberView(
        mixed = fraction.toMixed(),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        barThickness = barThickness,
        barMinWidth = barMinWidth
    )
}

/** Renders a [MixedNumber] such as `3 5/8`, keeping the sign on the whole part. */
@Composable
fun MixedNumberView(
    mixed: MixedNumber,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 30.sp,
    barThickness: Dp = 2.dp,
    barMinWidth: Dp = 16.dp
) {
    if (mixed.isWhole) {
        Text(
            text = mixed.whole.toString(),
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (mixed.whole.signum() != 0) {
            Text(
                text = mixed.whole.toString(),
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(fontSize.value.dp * 0.18f))
        }
        VerticalFraction(
            numerator = mixed.numerator,
            denominator = mixed.denominator,
            color = color,
            fontSize = fontSize,
            barThickness = barThickness,
            barMinWidth = barMinWidth
        )
    }
}

/** A single stacked numerator/bar/denominator group. */
@Composable
private fun VerticalFraction(
    numerator: BigInteger,
    denominator: BigInteger,
    color: Color,
    fontSize: TextUnit,
    barThickness: Dp,
    barMinWidth: Dp,
    modifier: Modifier = Modifier,
    numeratorColor: Color = color,
    denominatorColor: Color = color
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .widthIn(min = barMinWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = numerator.toString(),
            color = numeratorColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .height(barThickness)
                .background(color, RoundedCornerShape(50))
        )
        Text(
            text = denominator.toString(),
            color = denominatorColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Renders the operand currently being typed, highlighting the focused field. Empty fields show a
 * subtle placeholder so the user can see where input will land.
 */
@Composable
fun FractionInputView(
    input: FractionInput,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    highlight: Color = Color.Unspecified,
    fontSize: TextUnit = 30.sp,
    barThickness: Dp = 2.dp,
    barMinWidth: Dp = 16.dp,
    onFocusField: ((Field) -> Unit)? = null,
    numeratorColor: Color = color,
    denominatorColor: Color = color
) {
    // A named constant (e.g. π) is rendered symbolically instead of as a fraction.
    input.constant?.let { symbol ->
        Text(
            text = symbol,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold
        )
        return
    }

    val showFraction = input.focus == Field.NUMERATOR ||
        input.focus == Field.DENOMINATOR ||
        input.numerator.isNotEmpty() ||
        input.denominator.isNotEmpty()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NumberField(
            text = input.whole,
            active = input.focus == Field.WHOLE,
            color = color,
            highlight = highlight,
            fontSize = fontSize,
            placeholderWidth = fontSize.value.dp * 0.6f,
            onClick = onFocusField?.let { { it(Field.WHOLE) } }
        )

        if (showFraction) {
            Spacer(Modifier.width(fontSize.value.dp * 0.2f))
            Column(
                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = barMinWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberField(
                    text = input.numerator,
                    active = input.focus == Field.NUMERATOR,
                    color = color,
                    highlight = highlight,
                    fontSize = fontSize,
                    placeholderWidth = barMinWidth,
                    textColor = numeratorColor,
                    onClick = onFocusField?.let { { it(Field.NUMERATOR) } }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .height(barThickness)
                        .background(
                            if (showFraction) highlight else color,
                            RoundedCornerShape(50)
                        )
                )
                NumberField(
                    text = input.denominator,
                    active = input.focus == Field.DENOMINATOR,
                    color = color,
                    highlight = highlight,
                    fontSize = fontSize,
                    placeholderWidth = barMinWidth,
                    textColor = denominatorColor,
                    onClick = onFocusField?.let { { it(Field.DENOMINATOR) } }
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    text: String,
    active: Boolean,
    color: Color,
    highlight: Color,
    fontSize: TextUnit,
    placeholderWidth: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    textColor: Color = if (active) highlight else color
) {
    val background = if (active) highlight.copy(alpha = 0.20f) else Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        if (text.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(placeholderWidth)
                    .height(3.dp)
                    .background(
                        if (active) highlight.copy(alpha = 0.6f) else color.copy(alpha = 0.25f),
                        RoundedCornerShape(50)
                    )
            )
        } else {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
