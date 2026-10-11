package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.calculator.CalculatorKey
import com.figapps.fractioncalculatorv1.calculator.Field
import com.figapps.fractioncalculatorv1.domain.MathConstant
import com.figapps.fractioncalculatorv1.domain.Operator
import com.figapps.fractioncalculatorv1.ui.CalcTheme

private val PAD_SPACING = 6.dp

/**
 * The keypad is made of three independent number input sections:
 *  - **left** — the whole-number pad (plus the function and operator keys),
 *  - **right (top)** — the numerator pad,
 *  - **right (bottom)** — the denominator pad, separated from the numerator by a divider line.
 *
 * Every digit key is bound to one specific field, so the user can enter and edit the whole
 * number, the numerator and the denominator independently. [hapticsEnabled] mirrors the global
 * setting so every key gives (or suppresses) a haptic tick.
 */
@Composable
fun Keypad(
    onKey: (CalculatorKey) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WholeNumberPad(
            onKey = onKey,
            hapticsEnabled = hapticsEnabled,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        FractionPads(
            onKey = onKey,
            hapticsEnabled = hapticsEnabled,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WholeNumberPad(
    onKey: (CalculatorKey) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PAD_SPACING)
    ) {
        PadLabel("WHOLE")
        KeyRow(Modifier.weight(1f)) {
            WholeDigit(7, onKey, hapticsEnabled)
            WholeDigit(8, onKey, hapticsEnabled)
            WholeDigit(9, onKey, hapticsEnabled)
        }
        KeyRow(Modifier.weight(1f)) {
            WholeDigit(4, onKey, hapticsEnabled)
            WholeDigit(5, onKey, hapticsEnabled)
            WholeDigit(6, onKey, hapticsEnabled)
        }
        KeyRow(Modifier.weight(1f)) {
            WholeDigit(1, onKey, hapticsEnabled)
            WholeDigit(2, onKey, hapticsEnabled)
            WholeDigit(3, onKey, hapticsEnabled)
        }
        KeyRow(Modifier.weight(1f)) {
            CalcButton(
                label = ".",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                hapticsEnabled = hapticsEnabled,
                onClick = {
                    onKey(CalculatorKey.SetFocus(Field.WHOLE))
                    onKey(CalculatorKey.DecimalPoint)
                }
            )
            WholeDigit(0, onKey, hapticsEnabled)
            CalcButton(
                label = "\u232B",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                style = CalcButtonStyle.OPERATOR,
                hapticsEnabled = hapticsEnabled,
                onClick = { onKey(CalculatorKey.BackspaceIn(Field.WHOLE)) }
            )
        }
        KeyRow(Modifier.weight(1f)) {
            CalcButton(
                label = "C",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                style = CalcButtonStyle.FUNCTION,
                hapticsEnabled = hapticsEnabled,
                onClick = { onKey(CalculatorKey.Clear) }
            )
            CalcButton(
                label = "\u00B1",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                style = CalcButtonStyle.SIGN,
                hapticsEnabled = hapticsEnabled,
                onClick = { onKey(CalculatorKey.ToggleSign) }
            )
            CalcButton(
                label = MathConstant.PI.symbol,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                style = CalcButtonStyle.ACCENT,
                fontSize = 22.sp,
                hapticsEnabled = hapticsEnabled,
                onClick = { onKey(CalculatorKey.InsertConstant(MathConstant.PI.symbol)) }
            )
        }
        KeyRow(Modifier.weight(1f)) {
            OperatorCell(Operator.DIVIDE, onKey, hapticsEnabled)
            OperatorCell(Operator.MULTIPLY, onKey, hapticsEnabled)
            OperatorCell(Operator.SUBTRACT, onKey, hapticsEnabled)
        }
        KeyRow(Modifier.weight(1f)) {
            OperatorCell(Operator.ADD, onKey, hapticsEnabled)
            CalcButton(
                label = "=",
                modifier = Modifier.weight(2f).fillMaxHeight(),
                style = CalcButtonStyle.EQUALS,
                fontSize = 24.sp,
                hapticsEnabled = hapticsEnabled,
                onClick = { onKey(CalculatorKey.Equals) }
            )
        }
    }
}

@Composable
private fun FractionPads(
    onKey: (CalculatorKey) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(PAD_SPACING)
    ) {
        FractionPad("NUMERATOR", Field.NUMERATOR, onKey, hapticsEnabled)
        PadDivider()
        FractionPad("DENOMINATOR", Field.DENOMINATOR, onKey, hapticsEnabled)
    }
}

/** A single fraction number pad (four rows of keys) for the given [field]. */
@Composable
private fun FractionPad(
    label: String,
    field: Field,
    onKey: (CalculatorKey) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PAD_SPACING)
    ) {
        PadLabel(label)
        KeyRow {
            FractionDigit(7, field, onKey, hapticsEnabled)
            FractionDigit(8, field, onKey, hapticsEnabled)
            FractionDigit(9, field, onKey, hapticsEnabled)
        }
        KeyRow {
            FractionDigit(4, field, onKey, hapticsEnabled)
            FractionDigit(5, field, onKey, hapticsEnabled)
            FractionDigit(6, field, onKey, hapticsEnabled)
        }
        KeyRow {
            FractionDigit(1, field, onKey, hapticsEnabled)
            FractionDigit(2, field, onKey, hapticsEnabled)
            FractionDigit(3, field, onKey, hapticsEnabled)
        }
        KeyRow {
            CalcButton(
                label = "0",
                modifier = Modifier.weight(2f),
                style = fractionPadStyle(field),
                hapticsEnabled = hapticsEnabled,
                onClick = {
                    onKey(CalculatorKey.SetFocus(field))
                    onKey(CalculatorKey.Digit(0))
                }
            )
            CalcButton(
                label = "\u232B",
                modifier = Modifier.weight(1f),
                style = CalcButtonStyle.OPERATOR,
                hapticsEnabled = hapticsEnabled,
                onClick = { onKey(CalculatorKey.BackspaceIn(field)) }
            )
        }
    }
}

@Composable
private fun KeyRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PAD_SPACING),
        content = content
    )
}

@Composable
private fun RowScope.WholeDigit(digit: Int, onKey: (CalculatorKey) -> Unit, hapticsEnabled: Boolean) {
    CalcButton(
        label = digit.toString(),
        modifier = Modifier.weight(1f).fillMaxHeight(),
        hapticsEnabled = hapticsEnabled,
        onClick = {
            onKey(CalculatorKey.SetFocus(Field.WHOLE))
            onKey(CalculatorKey.Digit(digit))
        }
    )
}

@Composable
private fun RowScope.FractionDigit(
    digit: Int,
    field: Field,
    onKey: (CalculatorKey) -> Unit,
    hapticsEnabled: Boolean
) {
    CalcButton(
        label = digit.toString(),
        modifier = Modifier.weight(1f),
        style = fractionPadStyle(field),
        hapticsEnabled = hapticsEnabled,
        onClick = {
            onKey(CalculatorKey.SetFocus(field))
            onKey(CalculatorKey.Digit(digit))
        }
    )
}

@Composable
private fun RowScope.OperatorCell(
    op: Operator,
    onKey: (CalculatorKey) -> Unit,
    hapticsEnabled: Boolean
) {
    CalcButton(
        label = op.symbol,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        style = CalcButtonStyle.OPERATOR,
        fontSize = 24.sp,
        hapticsEnabled = hapticsEnabled,
        onClick = { onKey(CalculatorKey.ChooseOperator(op)) }
    )
}

/** Fraction pad keys take the same colour as the numerator / denominator in the entry display. */
private fun fractionPadStyle(field: Field): CalcButtonStyle = when (field) {
    Field.NUMERATOR -> CalcButtonStyle.NUMERATOR
    Field.DENOMINATOR -> CalcButtonStyle.DENOMINATOR
    Field.WHOLE -> CalcButtonStyle.NUMBER
}

@Composable
private fun PadLabel(text: String) {
    val colors = CalcTheme.colors
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        color = colors.numberKeyText.copy(alpha = 0.5f)
    )
}

/** The line that separates the numerator pad from the denominator pad. */
@Composable
private fun PadDivider() {
    val colors = CalcTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .height(2.dp)
            .background(colors.focusHighlight.copy(alpha = 0.35f), RoundedCornerShape(50))
    )
}
