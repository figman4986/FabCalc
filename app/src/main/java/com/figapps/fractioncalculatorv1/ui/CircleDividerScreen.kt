package com.figapps.fractioncalculatorv1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.CircleDividerMath
import com.figapps.fractioncalculatorv1.domain.CircleDividerResult
import com.figapps.fractioncalculatorv1.domain.DivisionPoint
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.domain.MeasurementType
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.fractionFromDouble
import com.figapps.fractioncalculatorv1.domain.toDoubleValue
import com.figapps.fractioncalculatorv1.history.HistoryDraft
import com.figapps.fractioncalculatorv1.history.HistoryRecorder
import com.figapps.fractioncalculatorv1.history.NoOpHistoryRecorder
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.ui.components.AnnotationLayerToggles
import com.figapps.fractioncalculatorv1.ui.components.CalcButton
import com.figapps.fractioncalculatorv1.ui.components.CalcButtonStyle
import com.figapps.fractioncalculatorv1.ui.components.CalcCard
import com.figapps.fractioncalculatorv1.ui.components.CircleDividerDiagram
import com.figapps.fractioncalculatorv1.ui.components.SettingBlock
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

private val QUICK_DIVISIONS = listOf(2, 3, 4, 5, 6, 8, 10, 12, 16, 24)

/**
 * The Circle Divider tool: divides a circle into N equal parts and reports everything needed to lay
 * it out on the shop floor — chord/arc stepping, apothem, sagitta, coordinates per mark, plus an
 * interactive diagram you can drag (start angle), tap (select a segment) and pinch (change N).
 */
@Composable
fun CircleDividerScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onRecord: HistoryRecorder = NoOpHistoryRecorder,
    initialSize: String = ""
) {
    val colors = CalcTheme.colors
    val precision = settings.precision

    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    var sizeType by rememberSaveable { mutableStateOf(MeasurementType.DIAMETER) }
    var sizeText by rememberSaveable { mutableStateOf(initialSize) }
    var divisionsText by rememberSaveable { mutableStateOf("8") }
    var startAngle by rememberSaveable { mutableStateOf(0.0) }
    var boltMode by rememberSaveable { mutableStateOf(false) }
    var boltText by rememberSaveable { mutableStateOf("") }
    var selectedIndex by rememberSaveable { mutableStateOf(-1) }

    val enabledUnits = remember(settings.enabledUnits) {
        LengthUnit.entries.filter { it in settings.enabledUnits }
    }
    // The active unit is app-wide (persisted); coerce it back if it was disabled in Settings.
    val selectedUnit =
        if (settings.activeUnit in settings.enabledUnits) settings.activeUnit else settings.primaryUnit

    val sizeValue = remember(sizeText) { MeasurementParser.parsePositive(sizeText) }
    val sizeNegative = remember(sizeText) { MeasurementParser.isNegative(sizeText) }
    val divisions = (divisionsText.toIntOrNull() ?: 8)
        .coerceIn(CircleDividerMath.MIN_DIVISIONS, CircleDividerMath.MAX_DIVISIONS)

    val result = remember(sizeValue, sizeType, divisions, startAngle) {
        sizeValue?.let { CircleDividerMath.compute(it, sizeType, divisions, startAngle) }
    }

    val sizePrefix = if (sizeType == MeasurementType.DIAMETER) "\u00D8" else "C"

    // --- bolt-hole pattern maths ---
    val boltValue = remember(boltText) { MeasurementParser.parsePositive(boltText) }
    val boltNegative = remember(boltText) { MeasurementParser.isNegative(boltText) }
    val boltDiameter = if (boltMode) {
        boltValue ?: result?.diameter?.let { it * Fraction.of(85L, 100L) }
    } else {
        null
    }
    val boltRadiusUnits = boltDiameter?.let { it.toDoubleValue() / 2.0 }
    val boltChord = boltDiameter?.let {
        fractionFromDouble(2.0 * it.toDoubleValue() / 2.0 * sin(Math.toRadians(180.0 / divisions)))
    }

    // --- history ---
    val inputsText = if (sizeValue != null) {
        "$sizePrefix ${sizeText.trim()} ${selectedUnit.symbol}, $divisions divisions, start ${startAngle.roundToInt()}\u00B0"
    } else {
        null
    }
    val resultText = result?.let {
        val chord = ResultFormat.format(it.chord, precision)
        val arc = ResultFormat.format(it.arc, precision)
        "chord ${chord.decimalText} ${selectedUnit.symbol}, arc ${arc.decimalText} ${selectedUnit.symbol}, " +
            "${ResultFormat.decimalText(it.centralAngleDegrees, 4)}\u00B0"
    }
    val historyKey = if (inputsText != null && resultText != null) "$inputsText|$resultText" else null

    var lastRecorded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(historyKey) {
        if (historyKey == null || !settings.historyEnabled) return@LaunchedEffect
        delay(1500)
        if (historyKey != lastRecorded && result != null && inputsText != null && resultText != null) {
            lastRecorded = historyKey
            onRecord.record(
                HistoryDraft(
                    toolId = "circle_divider",
                    toolName = "Circle Divider",
                    inputs = inputsText,
                    result = resultText,
                    detail = mathSteps(result, selectedUnit, precision)
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Divide a circle into as many equal parts as you like and read off every angle, " +
                "the chord step, the arc length and the mark coordinates.",
            color = colors.numberKeyText.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        // 1 · Input -------------------------------------------------------------------------------
        CalcCard {
            SettingBlock(
                label = "Circle measured by",
                options = MeasurementType.entries.map { it to it.label },
                selected = sizeType,
                onSelected = { sizeType = it }
            )
            Spacer(Modifier.height(12.dp))
            SettingBlock(
                label = "Units",
                options = enabledUnits.map { it to it.label },
                selected = selectedUnit,
                onSelected = { scope.launch { container.settingsRepository.setActiveUnit(it) } }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = sizeText,
                onValueChange = { sizeText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Circle size") },
                placeholder = { Text("e.g. 8 or 8 3/16") },
                suffix = { Text(selectedUnit.symbol, fontWeight = FontWeight.SemiBold) },
                singleLine = true,
                isError = sizeText.isNotBlank() && sizeValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                supportingText = {
                    Text(
                        text = when {
                            sizeNegative -> "Measurements must be positive"
                            sizeText.isNotBlank() && sizeValue == null -> "Enter a number or fraction, e.g. 8 3/16"
                            else -> "Accepts decimals and fractions"
                        },
                        fontSize = 11.sp
                    )
                },
                colors = fieldColors()
            )

            Spacer(Modifier.height(12.dp))
            DivisionsControl(
                divisionsText = divisionsText,
                onDivisionsTextChange = { divisionsText = it },
                divisions = divisions,
                onDivisionsChange = { n ->
                    divisionsText = n.coerceIn(CircleDividerMath.MIN_DIVISIONS, CircleDividerMath.MAX_DIVISIONS).toString()
                    if (selectedIndex >= n) selectedIndex = -1
                },
                hapticsEnabled = settings.hapticsEnabled
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Start angle: ${startAngle.roundToInt()}\u00B0",
                color = colors.numberKeyText.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = startAngle.toFloat(),
                onValueChange = { startAngle = it.toDouble() },
                valueRange = 0f..359f
            )

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bolt-hole pattern mode", color = colors.numberKeyText, fontSize = 14.sp)
                Switch(checked = boltMode, onCheckedChange = { boltMode = it })
            }

            if (boltMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = boltText,
                    onValueChange = { boltText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bolt circle diameter") },
                    placeholder = { Text("default 0.85 \u00D7 circle") },
                    suffix = { Text(selectedUnit.symbol, fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    isError = boltText.isNotBlank() && boltValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    supportingText = {
                        if (boltNegative) Text("Measurements must be positive", fontSize = 11.sp)
                    },
                    colors = fieldColors()
                )
            }

            val hint = when {
                sizeText.isBlank() -> "Enter the circle size to see the division layout."
                sizeNegative -> "Measurements must be positive."
                result == null -> "Check the highlighted field — use a number or fraction like 8 3/16."
                else -> null
            }
            if (hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(hint, color = colors.numberKeyText.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }

        // 2 · Diagram -----------------------------------------------------------------------------
        CalcCard {
            Text(
                text = "Circle divided into $divisions parts",
                fontWeight = FontWeight.SemiBold,
                color = colors.numberKeyText,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            AnnotationLayerToggles(
                visibility = settings.diagramVisibility,
                onDimensionsChange = { scope.launch { container.settingsRepository.setDiagramDimensions(it) } },
                onTheoryChange = { scope.launch { container.settingsRepository.setDiagramTheory(it) } },
                hapticsEnabled = settings.hapticsEnabled,
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                CircleDividerDiagram(
                    radiusUnits = result?.radius?.toDoubleValue() ?: 0.0,
                    divisions = divisions,
                    startAngleDegrees = startAngle,
                    boltRadiusUnits = boltRadiusUnits,
                    selectedIndex = selectedIndex,
                    unitSymbol = selectedUnit.symbol,
                    precision = precision,
                    modifier = Modifier.fillMaxWidth(),
                    visibility = settings.diagramVisibility,
                    onStartAngleChange = { startAngle = wrap360(it) },
                    onSelectIndex = { selectedIndex = it },
                    onDivisionsChange = { n ->
                        divisionsText = n.coerceIn(CircleDividerMath.MIN_DIVISIONS, CircleDividerMath.MAX_DIVISIONS).toString()
                        if (selectedIndex >= n) selectedIndex = -1
                    }
                )

                if (result != null && selectedIndex in result.points.indices) {
                    val point = result.points[selectedIndex]
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.cardBackground.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, colors.accentKey)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "Segment #${selectedIndex + 1}",
                                color = colors.accentKey,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(segmentInfo(point, result, precision, selectedUnit), color = colors.numberKeyText, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 3 · Results -----------------------------------------------------------------------------
        if (result != null) {
            CalcCard {
                Text("Results", fontWeight = FontWeight.SemiBold, color = colors.numberKeyText, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                val centralDegrees = 360.0 / result.divisions
                ResultRow(
                    label = "Central angle",
                    valueText = "${ResultFormat.decimalText(result.centralAngleDegrees, 4)}\u00B0",
                    note = dmsText(centralDegrees)
                )
                LengthResultRow("Chord", result.chord, selectedUnit, precision)
                LengthResultRow("Arc length", result.arc, selectedUnit, precision)
                LengthResultRow("Apothem", result.apothem, selectedUnit, precision)
                LengthResultRow("Sagitta", result.sagitta, selectedUnit, precision)
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = colors.divider)
                LengthResultRow("Radius", result.radius, selectedUnit, precision)
                LengthResultRow("Diameter", result.diameter, selectedUnit, precision)
                LengthResultRow("Circumference", result.circumference, selectedUnit, precision)
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = colors.divider)

                val areaUnit = "${selectedUnit.symbol}\u00B2"
                ResultRow(
                    label = "Sector area (\u03C0r\u00B2/N)",
                    valueText = "${decimal(result.sectorArea, precision)} $areaUnit"
                )
                val splitByDiameter = result.divisions == 2
                ResultRow(
                    label = if (splitByDiameter) "Segment area (semicircle)" else "Segment area (sliver)",
                    valueText = "${decimal(result.segmentArea, precision)} $areaUnit",
                    note = if (splitByDiameter) "chord is the diameter, slice is a semicircle" else null
                )

                if (boltMode && boltDiameter != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = colors.divider)
                    LengthResultRow("Bolt-circle diameter", boltDiameter, selectedUnit, precision)
                    if (boltChord != null) {
                        LengthResultRow("Hole spacing (chord)", boltChord, selectedUnit, precision)
                    }
                }
            }

            // 4 · Division table -----------------------------------------------------------------
            CalcCard {
                Text(
                    text = "Division points (${result.points.size})",
                    fontWeight = FontWeight.SemiBold,
                    color = colors.numberKeyText,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeaderCell("#", 0.8f)
                    HeaderCell("From", 1f)
                    HeaderCell("Cum.", 1f)
                    HeaderCell("X", 1f)
                    HeaderCell("Y", 1f)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colors.divider)
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(result.points, key = { it.index }) { point ->
                        DivisionRow(point, precision)
                    }
                }
            }

            // 5 · How this works -----------------------------------------------------------------
            HowItWorksCard(result = result, unit = selectedUnit, precision = precision)
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Input controls                                                                                 */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun DivisionsControl(
    divisionsText: String,
    onDivisionsTextChange: (String) -> Unit,
    divisions: Int,
    onDivisionsChange: (Int) -> Unit,
    hapticsEnabled: Boolean
) {
    val colors = CalcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Divisions",
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QUICK_DIVISIONS.forEach { n ->
                FilterChip(
                    selected = divisions == n,
                    onClick = { onDivisionsChange(n) },
                    label = { Text(n.toString()) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalcButton(
                label = "\u2212",
                modifier = Modifier.width(56.dp),
                style = CalcButtonStyle.OPERATOR,
                fontSize = 22.sp,
                hapticsEnabled = hapticsEnabled,
                onClick = { onDivisionsChange(divisions - 1) }
            )
            OutlinedTextField(
                value = divisionsText,
                onValueChange = { text -> onDivisionsTextChange(text.filter { it.isDigit() }.take(3)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors()
            )
            CalcButton(
                label = "+",
                modifier = Modifier.width(56.dp),
                style = CalcButtonStyle.OPERATOR,
                fontSize = 22.sp,
                hapticsEnabled = hapticsEnabled,
                onClick = { onDivisionsChange(divisions + 1) }
            )
        }
        Text(
            text = "Range ${CircleDividerMath.MIN_DIVISIONS}\u2013${CircleDividerMath.MAX_DIVISIONS}",
            color = colors.numberKeyText.copy(alpha = 0.45f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CalcTheme.colors.accentKey,
    unfocusedBorderColor = CalcTheme.colors.divider,
    focusedLabelColor = CalcTheme.colors.accentKey,
    unfocusedLabelColor = CalcTheme.colors.numberKeyText.copy(alpha = 0.6f),
    cursorColor = CalcTheme.colors.accentKey,
    focusedTextColor = CalcTheme.colors.numberKeyText,
    unfocusedTextColor = CalcTheme.colors.numberKeyText
)

/* --------------------------------------------------------------------------------------------- */
/* Result rows                                                                                    */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun ResultRow(label: String, valueText: String, fractionText: String? = null, note: String? = null) {
    val colors = CalcTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.numberKeyText, fontSize = 14.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text(valueText, color = colors.numberKeyText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (fractionText != null) {
                Text(fractionText, color = colors.numberKeyText.copy(alpha = 0.55f), fontSize = 11.sp)
            }
            if (note != null) {
                Text(note, color = colors.numberKeyText.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun LengthResultRow(label: String, value: Fraction, unit: LengthUnit, precision: ResultPrecision) {
    val formatted = remember(value, precision) { ResultFormat.format(value, precision) }
    ResultRow(
        label = label,
        valueText = "${formatted.decimalText} ${unit.symbol}",
        fractionText = "${formatted.fractionText} ${unit.symbol}",
        note = if (formatted.isMisleading && formatted.exactNoteText != null) {
            "exact ${formatted.exactNoteText} ${unit.symbol}"
        } else {
            null
        }
    )
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = CalcTheme.colors.numberKeyText.copy(alpha = 0.6f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DivisionRow(point: DivisionPoint, precision: ResultPrecision) {
    val colors = CalcTheme.colors
    val places = ResultFormat.decimalPlaces(precision).coerceAtLeast(2)
    val x = ResultFormat.decimalText(fractionFromDouble(point.x), places, trimTrailingZeros = true)
    val y = ResultFormat.decimalText(fractionFromDouble(point.y), places, trimTrailingZeros = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Cell("#${point.index + 1}", 0.8f, colors.numberKeyText, FontWeight.SemiBold)
        Cell("${"%.2f".format(point.angleFromStart)}\u00B0", 1f, colors.numberKeyText.copy(alpha = 0.8f))
        Cell("${"%.2f".format(point.cumulativeAngle)}\u00B0", 1f, colors.numberKeyText.copy(alpha = 0.8f))
        Cell(x, 1f, colors.numberKeyText.copy(alpha = 0.8f))
        Cell(y, 1f, colors.numberKeyText.copy(alpha = 0.8f))
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(text = text, modifier = Modifier.weight(weight), color = color, fontSize = 12.sp, fontWeight = fontWeight)
}

/* --------------------------------------------------------------------------------------------- */
/* Explanation                                                                                    */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun HowItWorksCard(result: CircleDividerResult, unit: LengthUnit, precision: ResultPrecision) {
    val colors = CalcTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val symbol = unit.symbol

    val steps = buildList {
        add(
            "1 · Central angle \u03B8 = 360 / N" to
                "360 / ${result.divisions} = ${ResultFormat.decimalText(result.centralAngleDegrees, 4)}\u00B0"
        )
        add(
            "2 · Chord = 2 \u00B7 r \u00B7 sin(\u03B8/2)" to
                "2 \u00D7 ${ResultFormat.decimalText(result.radius, 4)} $symbol \u00D7 sin(" +
                "${ResultFormat.decimalText(result.centralAngleDegrees / Fraction.of(2L, 1L), 4)}\u00B0) = " +
                "${ResultFormat.decimalText(result.chord, 4)} $symbol"
        )
        add(
            "3 · Arc = C / N" to
                "${ResultFormat.decimalText(result.circumference, 4)} $symbol / ${result.divisions} = " +
                "${ResultFormat.decimalText(result.arc, 4)} $symbol"
        )
        add(
            "4 · Apothem = r \u00B7 cos(\u03B8/2)" to
                "${ResultFormat.decimalText(result.radius, 4)} $symbol \u00D7 cos(" +
                "${ResultFormat.decimalText(result.centralAngleDegrees / Fraction.of(2L, 1L), 4)}\u00B0) = " +
                "${ResultFormat.decimalText(result.apothem, 4)} $symbol"
        )
        add(
            "5 · Sagitta = r \u2212 apothem" to
                "${ResultFormat.decimalText(result.radius, 4)} $symbol \u2212 " +
                "${ResultFormat.decimalText(result.apothem, 4)} $symbol = " +
                "${ResultFormat.decimalText(result.sagitta, 4)} $symbol"
        )
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
                    text = "Stepping chords with dividers is the fastest layout, but the error compounds: " +
                        "every chord is slightly shorter than its arc, so the last gap drifts (see the sliver " +
                        "on the diagram). For large circles, coordinates measured from the centre are the most " +
                        "accurate; for angle layout use a protractor or a rotary table.",
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Bolt holes: mark the centrelines, then the bolt-circle " +
                        "(${ResultFormat.decimalText(result.radius, 4)} $symbol radius), then step the holes " +
                        "\u2014 their angular positions are in the table.",
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Results rounded to ${precisionDescription(precision)} (set in Settings).",
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

private fun segmentInfo(
    point: DivisionPoint,
    result: CircleDividerResult,
    precision: ResultPrecision,
    unit: LengthUnit
): String {
    val places = ResultFormat.decimalPlaces(precision).coerceAtLeast(2)
    val x = ResultFormat.decimalText(fractionFromDouble(point.x), places, trimTrailingZeros = true)
    val y = ResultFormat.decimalText(fractionFromDouble(point.y), places, trimTrailingZeros = true)
    return "\u03B8 ${ResultFormat.decimalText(result.centralAngleDegrees, 4)}\u00B0  ·  " +
        "chord ${ResultFormat.format(result.chord, precision).decimalText} ${unit.symbol}  ·  " +
        "X $x, Y $y ${unit.symbol}"
}

private fun decimal(value: Fraction, precision: ResultPrecision): String =
    ResultFormat.decimalText(value, ResultFormat.decimalPlaces(precision).coerceAtLeast(2), trimTrailingZeros = true)

private fun wrap360(degrees: Double): Double {
    var value = degrees % 360.0
    if (value < 0.0) value += 360.0
    return value
}

/** Degrees -> "D° M' S\"". */
private fun dmsText(degrees: Double): String {
    val totalSeconds = (abs(degrees) * 3600.0).roundToLong()
    val d = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val sign = if (degrees < 0.0) "-" else ""
    return "$sign$d\u00B0 $m' $s\""
}

private fun precisionDescription(precision: ResultPrecision): String = when (precision) {
    is ResultPrecision.FractionRound -> "the nearest ${ResultPrecision.denominatorLabel(precision.denominator)}"
    is ResultPrecision.DecimalRound ->
        if (precision.places == 0) "the nearest whole number" else "the nearest 0.${"0".repeat(precision.places - 1)}1"
}

private fun mathSteps(result: CircleDividerResult, unit: LengthUnit, precision: ResultPrecision): String {
    val symbol = unit.symbol
    return buildList {
        add("1. \u03B8 = 360 / ${result.divisions} = ${ResultFormat.decimalText(result.centralAngleDegrees, 4)}\u00B0")
        add("2. chord = 2\u00B7r\u00B7sin(\u03B8/2) = ${ResultFormat.decimalText(result.chord, 4)} $symbol")
        add("3. arc = C / N = ${ResultFormat.decimalText(result.arc, 4)} $symbol")
        add("4. apothem = r\u00B7cos(\u03B8/2) = ${ResultFormat.decimalText(result.apothem, 4)} $symbol")
        add("5. sagitta = r \u2212 apothem = ${ResultFormat.decimalText(result.sagitta, 4)} $symbol")
        add("6. sector area = \u03C0r\u00B2/N = ${decimal(result.sectorArea, precision)} $symbol\u00B2")
        add("7. segment area (sliver) = ${decimal(result.segmentArea, precision)} $symbol\u00B2")
    }.joinToString("\n")
}

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun CircleDividerPreview() {
    FractionCalculatorV1Theme(dynamicColor = false) {
        Surface { CircleDividerScreen(settings = AppSettings(), initialSize = "8") }
    }
}
