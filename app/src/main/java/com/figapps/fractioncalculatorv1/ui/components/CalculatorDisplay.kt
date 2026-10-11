package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.calculator.CalculatorState
import com.figapps.fractioncalculatorv1.calculator.ExprToken
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.ui.CalcColors
import com.figapps.fractioncalculatorv1.ui.CalcTheme

/**
 * The blue gradient display: a small decimal chip in the top-right corner and the running
 * expression (with a live or committed result) in the centre.
 */
@Composable
fun CalculatorDisplay(
    state: CalculatorState,
    modifier: Modifier = Modifier,
    precision: ResultPrecision = ResultPrecision.DEFAULT
) {
    val colors = CalcTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(listOf(colors.displayTop, colors.displayBottom))
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                decimalChip(state, precision)?.let { chip ->
                    Text(
                        text = chip,
                        color = colors.displaySubText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Expression(state = state, colors = colors)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Expression(state: CalculatorState, colors: CalcColors) {
    val contentColor = colors.displayText

    if (state.error != null) {
        Text(
            text = state.error,
            color = colors.errorText,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        return
    }

    val empty = state.tokens.isEmpty() && state.current.isEmpty
    if (empty && !state.isEvaluated) {
        Text(
            text = "0",
            color = contentColor,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold
        )
        return
    }

    for (token in state.tokens) {
        when (token) {
            is ExprToken.Value -> if (token.label != null) {
                Text(
                    text = token.label,
                    color = contentColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                FractionView(
                    fraction = token.fraction,
                    color = contentColor,
                    fontSize = 26.sp
                )
            }
            is ExprToken.OpToken -> Text(
                text = token.op.symbol,
                color = colors.displaySubText,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(8.dp))
    }

    if (!state.current.isEmpty) {
        FractionInputView(
            input = state.current,
            color = contentColor,
            highlight = contentColor,
            fontSize = 26.sp,
            numeratorColor = colors.displayFractionNumerator,
            denominatorColor = colors.displayFractionDenominator
        )
        Spacer(Modifier.width(8.dp))
    }

    val result: Fraction? = if (state.isEvaluated) state.result else state.liveResult
    if (result != null) {
        Text(
            text = "=",
            color = if (state.isEvaluated) contentColor else colors.displaySubText,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        FractionView(
            fraction = result,
            color = if (state.isEvaluated) contentColor else contentColor.copy(alpha = 0.75f),
            fontSize = if (state.isEvaluated) 36.sp else 26.sp
        )
    }
}

private fun decimalChip(state: CalculatorState, precision: ResultPrecision): String? {
    if (state.error != null) return null
    val fraction: Fraction? = if (state.isEvaluated) state.result else state.liveResult
    val decimal = if (fraction != null) {
        ResultFormat.decimalText(
            value = fraction,
            places = ResultFormat.decimalPlaces(precision),
            trimTrailingZeros = true
        )
    } else {
        state.decimal ?: return null
    }
    return if (state.decimalExact) "($decimal)" else "(\u2248 $decimal)"
}
