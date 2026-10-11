package com.figapps.fractioncalculatorv1.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.figapps.fractioncalculatorv1.calculator.CalculatorKey
import com.figapps.fractioncalculatorv1.calculator.CalculatorState
import com.figapps.fractioncalculatorv1.calculator.CalculatorViewModel
import com.figapps.fractioncalculatorv1.calculator.ExprToken
import com.figapps.fractioncalculatorv1.calculator.FractionInput
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.Operator
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.history.HistoryDraft
import com.figapps.fractioncalculatorv1.history.HistoryRecorder
import com.figapps.fractioncalculatorv1.history.NoOpHistoryRecorder
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.ui.components.CalculatorDisplay
import com.figapps.fractioncalculatorv1.ui.components.Keypad
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme

/**
 * The fraction calculator tool. The app shell ([FractionCalculatorApp]) owns the top bar and tool
 * menu, so this only renders the tool's body and records completed calculations to history.
 */
@Composable
fun CalculatorScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onRecord: HistoryRecorder = NoOpHistoryRecorder,
    viewModel: CalculatorViewModel = viewModel()
) {
    val state = viewModel.state
    var lastRecorded by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.isEvaluated, state.result) {
        if (!settings.historyEnabled || !state.isEvaluated) return@LaunchedEffect
        val result = state.result ?: return@LaunchedEffect
        val expression = expressionString(state)
        val decimalText = ResultFormat.decimalText(
            value = result,
            places = ResultFormat.decimalPlaces(settings.precision),
            trimTrailingZeros = true
        )
        val resultText = "${ResultFormat.fractionText(result)} ($decimalText)"
        val key = "$expression|$resultText"
        if (key != lastRecorded) {
            lastRecorded = key
            onRecord.record(
                HistoryDraft(
                    toolId = "fractions",
                    toolName = "Fraction Calculator",
                    inputs = expression,
                    result = resultText,
                    detail = expression
                )
            )
        }
    }

    CalculatorContent(
        state = state,
        onKey = viewModel::onKey,
        modifier = modifier,
        precision = settings.precision,
        hapticsEnabled = settings.hapticsEnabled
    )
}

/** The fraction calculator body: the display plus the keypad. */
@Composable
fun CalculatorContent(
    state: CalculatorState,
    onKey: (CalculatorKey) -> Unit,
    modifier: Modifier = Modifier,
    precision: ResultPrecision = ResultPrecision.DEFAULT,
    hapticsEnabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CalculatorDisplay(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            precision = precision
        )
        Keypad(
            onKey = onKey,
            modifier = Modifier.fillMaxWidth(),
            hapticsEnabled = hapticsEnabled
        )
    }
}

/** Builds the left-hand-side expression string used for a history entry. */
private fun expressionString(state: CalculatorState): String {
    val parts = mutableListOf<String>()
    for (token in state.tokens) {
        when (token) {
            is ExprToken.Value -> parts.add(token.label ?: ResultFormat.fractionText(token.fraction))
            is ExprToken.OpToken -> parts.add(token.op.symbol)
        }
    }
    if (!state.current.isEmpty) parts.add(state.current.displayText())
    return parts.joinToString(" ")
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CalculatorScreenPreviewLight() {
    FractionCalculatorV1Theme(dynamicColor = false) {
        Surface { CalculatorContent(state = sampleState(), onKey = {}) }
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalculatorScreenPreviewDark() {
    FractionCalculatorV1Theme(darkTheme = true, dynamicColor = false) {
        Surface { CalculatorContent(state = sampleState(), onKey = {}) }
    }
}

private fun sampleState(): CalculatorState = CalculatorState(
    tokens = listOf(
        ExprToken.Value(Fraction.of(1L, 8L)),
        ExprToken.OpToken(Operator.ADD)
    ),
    current = FractionInput(
        whole = "3",
        numerator = "1",
        denominator = "2"
    ),
    decimal = "3.625",
    decimalExact = true
)
