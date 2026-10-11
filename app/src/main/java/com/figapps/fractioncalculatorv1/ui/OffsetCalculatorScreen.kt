package com.figapps.fractioncalculatorv1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.domain.MeasurementType
import com.figapps.fractioncalculatorv1.domain.OffsetMath
import com.figapps.fractioncalculatorv1.domain.OffsetResult
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.history.HistoryDraft
import com.figapps.fractioncalculatorv1.history.HistoryRecorder
import com.figapps.fractioncalculatorv1.history.NoOpHistoryRecorder
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.ui.components.AnnotationLayerToggles
import com.figapps.fractioncalculatorv1.ui.components.CalcCard
import com.figapps.fractioncalculatorv1.ui.components.FractionView
import com.figapps.fractioncalculatorv1.ui.components.OffsetDiagram
import com.figapps.fractioncalculatorv1.ui.components.SettingBlock
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Offset Calculator tool: works out the radial gap between two round pieces (a concentric
 * offset) from measurements taken around each piece. Everything recalculates live as you type.
 *
 * Precision and the available units now come from the global [AppSettings]; completed results are
 * recorded to history after a short debounce.
 */
@Composable
fun OffsetCalculatorScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onRecord: HistoryRecorder = NoOpHistoryRecorder,
    initialLarge: String = "",
    initialSmall: String = ""
) {
    val colors = CalcTheme.colors

    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(MeasurementType.CIRCUMFERENCE) }
    var largeText by rememberSaveable { mutableStateOf(initialLarge) }
    var smallText by rememberSaveable { mutableStateOf(initialSmall) }

    val enabledUnits = remember(settings.enabledUnits) {
        LengthUnit.entries.filter { it in settings.enabledUnits }
    }
    // The active unit is app-wide (persisted); coerce it back if it was disabled in Settings.
    val selectedUnit =
        if (settings.activeUnit in settings.enabledUnits) settings.activeUnit else settings.primaryUnit

    val largeValue = remember(largeText) { MeasurementParser.parsePositive(largeText) }
    val smallValue = remember(smallText) { MeasurementParser.parsePositive(smallText) }
    val largeNegative = remember(largeText) { MeasurementParser.isNegative(largeText) }
    val smallNegative = remember(smallText) { MeasurementParser.isNegative(smallText) }
    val result = remember(largeValue, smallValue, mode) {
        if (largeValue != null && smallValue != null) {
            OffsetMath.compute(largeValue, smallValue, mode)
        } else {
            null
        }
    }

    val precision = settings.precision
    val inputsText = if (largeValue != null && smallValue != null) {
        "Large ${largeText.trim()} ${selectedUnit.symbol}, " +
            "Small ${smallText.trim()} ${selectedUnit.symbol} (${mode.label.lowercase()})"
    } else {
        null
    }
    val resultText = result?.let { r ->
        val formatted = ResultFormat.format(r.radialOffset, precision)
        "Offset ${formatted.decimalText} ${selectedUnit.symbol} (\u2248${formatted.fractionText} ${selectedUnit.symbol})"
    }
    val historyKey = if (inputsText != null && resultText != null) "$inputsText|$resultText" else null

    var lastRecorded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(historyKey) {
        if (historyKey == null || !settings.historyEnabled) return@LaunchedEffect
        // Debounce so we do not record on every keystroke while the user is still typing.
        delay(1500)
        if (historyKey != lastRecorded && result != null && inputsText != null && resultText != null) {
            lastRecorded = historyKey
            onRecord.record(
                HistoryDraft(
                    toolId = "offset",
                    toolName = "Offset Calculator",
                    inputs = inputsText,
                    result = resultText,
                    detail = mathSteps(mode, selectedUnit, result)
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Reserve room for the on-screen keyboard and let the content scroll clear of it.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Concentric offset between two round pieces. Measure each one at the fit line " +
                "and read off the radial gap.",
            color = colors.numberKeyText.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        CalcCard {
            SettingBlock(
                label = "Measured by",
                options = MeasurementType.entries.map { it to it.label },
                selected = mode,
                onSelected = { mode = it }
            )
            Spacer(Modifier.height(12.dp))
            SettingBlock(
                label = "Units",
                options = enabledUnits.map { it to it.label },
                selected = selectedUnit,
                onSelected = { scope.launch { container.settingsRepository.setActiveUnit(it) } }
            )
        }

        CalcCard {
            MeasurementField(
                label = "Large piece (outer)",
                value = largeText,
                onValueChange = { largeText = it },
                unit = selectedUnit,
                isError = largeText.isNotBlank() && largeValue == null,
                isNegative = largeNegative
            )
            Spacer(Modifier.height(10.dp))
            MeasurementField(
                label = "Small piece (inner)",
                value = smallText,
                onValueChange = { smallText = it },
                unit = selectedUnit,
                isError = smallText.isNotBlank() && smallValue == null,
                isNegative = smallNegative
            )
            val hint = when {
                largeNegative || smallNegative -> "Measurements must be positive."
                largeText.isBlank() || smallText.isBlank() -> "Enter both measurements to see the gap."
                result == null -> "Check the highlighted fields — use a number or fraction like 12 3/16."
                else -> null
            }
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(hint, color = colors.numberKeyText.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }

        if (result != null && largeValue != null && smallValue != null) {
            if (result.swapped) {
                SwapNote()
            }

            CalcCard {
                Text(
                    text = "Cross-section",
                    fontWeight = FontWeight.SemiBold,
                    color = colors.numberKeyText,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                AnnotationLayerToggles(
                    visibility = settings.diagramVisibility,
                    onDimensionsChange = { scope.launch { container.settingsRepository.setDiagramDimensions(it) } },
                    onTheoryChange = { scope.launch { container.settingsRepository.setDiagramTheory(it) } },
                    hapticsEnabled = settings.hapticsEnabled,
                )
                Spacer(Modifier.height(6.dp))
                OffsetDiagram(
                    largeDiameter = result.largeDiameter,
                    smallDiameter = result.smallDiameter,
                    radialOffset = result.radialOffset,
                    unitSymbol = selectedUnit.symbol,
                    modifier = Modifier.fillMaxWidth(),
                    visibility = settings.diagramVisibility,
                )
            }

            OffsetResultCard(result = result, unit = selectedUnit, precision = precision)

            HowItWorksCard(
                mode = mode,
                unit = selectedUnit,
                precision = precision,
                outer = largeValue,
                inner = smallValue,
                result = result
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Settings                                                                                       */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: LengthUnit,
    isError: Boolean,
    isNegative: Boolean = false
) {
    val colors = CalcTheme.colors
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    scope.launch {
                        // Let the IME inset settle, then scroll the field clear of the keyboard.
                        delay(150.milliseconds)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        label = { Text(label) },
        placeholder = { Text("e.g. 25.133 or 25 1/8") },
        suffix = { Text(unit.symbol, fontWeight = FontWeight.SemiBold) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        supportingText = {
            Text(
                text = when {
                    isNegative -> "Measurements must be positive"
                    isError -> "Enter a number or fraction, e.g. 25 1/8"
                    else -> "Accepts decimals and fractions"
                },
                fontSize = 11.sp
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentKey,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.accentKey,
            unfocusedLabelColor = colors.numberKeyText.copy(alpha = 0.6f),
            cursorColor = colors.accentKey,
            focusedTextColor = colors.numberKeyText,
            unfocusedTextColor = colors.numberKeyText
        )
    )
}

@Composable
private fun SwapNote() {
    val colors = CalcTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = colors.signKey.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colors.signKey.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\u21C4", color = colors.signKey, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Values swapped — using the larger measurement as the outer piece.",
                color = colors.numberKeyText,
                fontSize = 13.sp
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Results                                                                                        */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun OffsetResultCard(
    result: OffsetResult,
    unit: LengthUnit,
    precision: ResultPrecision
) {
    val colors = CalcTheme.colors
    val offset = remember(result.radialOffset, precision) {
        ResultFormat.format(result.radialOffset, precision)
    }
    CalcCard {
        Text("Results", fontWeight = FontWeight.SemiBold, color = colors.numberKeyText, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.accentKey.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, colors.accentKey.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Offset (radial gap)",
                        color = colors.numberKeyText.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${offset.decimalText} ${unit.symbol}",
                        color = colors.accentKey,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (offset.isMisleading && offset.exactNoteText != null) {
                        Text(
                            text = "exact ${offset.exactNoteText} ${unit.symbol}",
                            color = colors.numberKeyText.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    FractionView(
                        fraction = offset.rounded,
                        color = colors.numberKeyText,
                        fontSize = 20.sp
                    )
                    Text(
                        text = precisionDescription(precision),
                        color = colors.numberKeyText.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ResultRow("Large diameter", result.largeDiameter, unit, precision)
        ResultRow("Small diameter", result.smallDiameter, unit, precision)
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = colors.divider
        )
        ResultRow("Diameter difference", result.diameterDifference, unit, precision)
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: Fraction,
    unit: LengthUnit,
    precision: ResultPrecision
) {
    val colors = CalcTheme.colors
    val formatted = remember(value, precision) { ResultFormat.format(value, precision) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.numberKeyText, fontSize = 14.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${formatted.decimalText} ${unit.symbol}",
                color = colors.numberKeyText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${formatted.fractionText} ${unit.symbol}",
                color = colors.numberKeyText.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
            if (formatted.isMisleading && formatted.exactNoteText != null) {
                Text(
                    text = "exact ${formatted.exactNoteText} ${unit.symbol}",
                    color = colors.numberKeyText.copy(alpha = 0.45f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Explanation                                                                                    */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun HowItWorksCard(
    mode: MeasurementType,
    unit: LengthUnit,
    precision: ResultPrecision,
    outer: Fraction,
    inner: Fraction,
    result: OffsetResult
) {
    val colors = CalcTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }

    val symbol = unit.symbol
    val largeDiameter = ResultFormat.decimalText(result.largeDiameter, 3)
    val smallDiameter = ResultFormat.decimalText(result.smallDiameter, 3)
    val difference = ResultFormat.decimalText(result.diameterDifference, 3)
    val offset = ResultFormat.decimalText(result.radialOffset, 3)

    val steps = buildList {
        if (mode == MeasurementType.CIRCUMFERENCE) {
            add(
                "1 · Diameter = Circumference ÷ π" to
                    "Large: ${ResultFormat.decimalText(outer, 3)} $symbol ÷ 3.14159 = $largeDiameter $symbol\n" +
                    "Small: ${ResultFormat.decimalText(inner, 3)} $symbol ÷ 3.14159 = $smallDiameter $symbol"
            )
        } else {
            add(
                "1 · You measured the diameters directly" to
                    "Large: $largeDiameter $symbol\nSmall: $smallDiameter $symbol"
            )
        }
        add("2 · Difference = larger − smaller" to "$largeDiameter $symbol − $smallDiameter $symbol = $difference $symbol")
        add("3 · Offset = Difference ÷ 2" to "$difference $symbol ÷ 2 = $offset $symbol")
    }

    CalcCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("How this works", fontWeight = FontWeight.SemiBold, color = colors.numberKeyText, fontSize = 14.sp)
            Text(
                text = if (expanded) "\u25B2" else "\u25BC",
                color = colors.numberKeyText.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                steps.forEach { (title, detail) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, color = colors.numberKeyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(detail, color = colors.numberKeyText.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = colors.divider)

                Text(
                    text = "Why divide by 2? The diameter difference counts the gap twice — once on each " +
                        "side of centre. The fit-up gap you actually shim or stretch is half of that.",
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Results rounded to the ${precisionDescription(precision)} (set in Settings).",
                    color = colors.numberKeyText.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Helpers                                                                                        */
/* --------------------------------------------------------------------------------------------- */

/** "nearest 1/32", "nearest 0.001" or "nearest whole number". */
private fun precisionDescription(precision: ResultPrecision): String =
    "nearest ${precisionStepLabel(precision)}"

private fun precisionStepLabel(precision: ResultPrecision): String = when (precision) {
    is ResultPrecision.FractionRound -> ResultPrecision.denominatorLabel(precision.denominator)
    is ResultPrecision.DecimalRound ->
        if (precision.places == 0) "whole number"
        else BigDecimal.ONE.movePointLeft(precision.places).toPlainString()
}

/** The three maths steps, as a single string, for a history entry's detail field. */
private fun mathSteps(mode: MeasurementType, unit: LengthUnit, result: OffsetResult): String {
    val symbol = unit.symbol
    val largeDiameter = ResultFormat.decimalText(result.largeDiameter, 3)
    val smallDiameter = ResultFormat.decimalText(result.smallDiameter, 3)
    val difference = ResultFormat.decimalText(result.diameterDifference, 3)
    val offset = ResultFormat.decimalText(result.radialOffset, 3)
    return buildList {
        if (mode == MeasurementType.CIRCUMFERENCE) {
            add("1. Diameter = Circumference \u00F7 \u03C0 -> Large $largeDiameter $symbol, Small $smallDiameter $symbol")
        } else {
            add("1. Diameters measured directly -> Large $largeDiameter $symbol, Small $smallDiameter $symbol")
        }
        add("2. Difference = larger \u2212 smaller = $difference $symbol")
        add("3. Offset = Difference \u00F7 2 = $offset $symbol")
    }.joinToString("\n")
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun OffsetCalculatorPreview() {
    FractionCalculatorV1Theme(dynamicColor = false) {
        Surface { OffsetCalculatorScreen(settings = AppSettings(), initialLarge = "25.133", initialSmall = "24.858") }
    }
}
