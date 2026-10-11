package com.figapps.fractioncalculatorv1.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.Bend
import com.figapps.fractioncalculatorv1.domain.BendAllowanceMath
import com.figapps.fractioncalculatorv1.domain.AnnotationVisibility
import com.figapps.fractioncalculatorv1.domain.BendChainResult
import com.figapps.fractioncalculatorv1.domain.BendResult
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.LegCrossCheck
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.history.HistoryDraft
import com.figapps.fractioncalculatorv1.history.HistoryRecorder
import com.figapps.fractioncalculatorv1.history.NoOpHistoryRecorder
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.ui.components.AnnotationLayerToggles
import com.figapps.fractioncalculatorv1.ui.components.BendDiagram
import com.figapps.fractioncalculatorv1.ui.components.CalcCard
import com.figapps.fractioncalculatorv1.ui.components.FractionView
import com.figapps.fractioncalculatorv1.ui.components.MultiBendDiagram
import com.figapps.fractioncalculatorv1.ui.components.SettingBlock
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_ANGLES = listOf(90, 45, 30, 60, 120)

/**
 * The Bend Allowance / Flat Pattern tool.
 *
 * Works out bend allowance (BA), outside setback (OSSB), bend deduction (BD) and the flat pattern
 * length for a single bend, and chains extra bends into a total developed length. Everything
 * recalculates live; precision, units and history all come from the shared [AppSettings].
 */
@Composable
fun BendAllowanceScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onRecord: HistoryRecorder = NoOpHistoryRecorder,
    initialThickness: String = "",
) {
    val colors = CalcTheme.colors
    val precision = settings.precision

    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val tick = rememberHapticTick(settings.hapticsEnabled)

    var thicknessText by rememberSaveable { mutableStateOf(initialThickness) }
    var radiusText by rememberSaveable { mutableStateOf("") }
    var angleText by rememberSaveable { mutableStateOf("90") }
    var kText by rememberSaveable { mutableStateOf(ResultFormat.decimalText(BendAllowanceMath.DEFAULT_K, 2)) }
    var extraBends by rememberSaveable { mutableStateOf("") }
    var straightText by rememberSaveable { mutableStateOf("") }
    var legAText by rememberSaveable { mutableStateOf("") }
    var legBText by rememberSaveable { mutableStateOf("") }

    val enabledUnits = remember(settings.enabledUnits) {
        LengthUnit.entries.filter { it in settings.enabledUnits }
    }
    // The active unit is app-wide (persisted); coerce it back if it was disabled in Settings.
    val selectedUnit =
        if (settings.activeUnit in settings.enabledUnits) settings.activeUnit else settings.primaryUnit

    // --- parse inputs ---
    val thickness = MeasurementParser.parsePositive(thicknessText)?.takeIf { !it.isZero }
    val thicknessNegative = MeasurementParser.isNegative(thicknessText)
    val radius = parseRadius(radiusText)
    val radiusNegative = MeasurementParser.isNegative(radiusText)
    val angle = MeasurementParser.parse(angleText)
    val angleValid = angle != null && BendAllowanceMath.isValidAngle(angle)
    val k = MeasurementParser.parse(kText)
    val kValid = k != null && BendAllowanceMath.isValidK(k)

    val primaryBend = if (thickness != null && radius != null && angle != null && angleValid && k != null && kValid) {
        Bend(angle, radius)
    } else {
        null
    }

    val extraRows = remember(extraBends) { decodeBends(extraBends) }
    val extraParsed = remember(extraRows) {
        extraRows.mapNotNull { row ->
            val a = MeasurementParser.parse(row.first)
            val r = parseRadius(row.second)
            if (a != null && BendAllowanceMath.isValidAngle(a) && r != null) Bend(a, r) else null
        }
    }

    val allBends = if (primaryBend != null) listOf(primaryBend) + extraParsed else emptyList()
    val straightValue = if (straightText.isBlank()) null else MeasurementParser.parsePositive(straightText)

    val chain = remember(thickness, k, allBends, straightValue) {
        if (thickness != null && k != null && kValid && allBends.isNotEmpty()) {
            BendAllowanceMath.computeChain(thickness, k, allBends, straightValue)
        } else {
            null
        }
    }
    val primary = chain?.bends?.firstOrNull()

    val legA = if (legAText.isBlank()) null else MeasurementParser.parsePositive(legAText)
    val legB = if (legBText.isBlank()) null else MeasurementParser.parsePositive(legBText)
    val crossCheck = if (primary != null && legA != null && legB != null) {
        BendAllowanceMath.crossCheck(primary, legA, legB)
    } else {
        null
    }

    // --- history ---
    val inputsText = if (thickness != null && primary != null) {
        buildInputsText(thicknessText, radiusText, angleText, kText, allBends.size, straightText, selectedUnit)
    } else {
        null
    }
    val resultText = primary?.let {
        "BA ${ResultFormat.decimalText(it.bendAllowance, 3)} ${selectedUnit.symbol}, " +
            "BD ${it.bendDeduction?.let { bd -> ResultFormat.decimalText(bd, 3) } ?: "n/a"} ${selectedUnit.symbol}"
    }
    val historyKey = if (inputsText != null && resultText != null) "$inputsText|$resultText" else null

    var lastRecorded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(historyKey) {
        if (historyKey == null || !settings.historyEnabled) return@LaunchedEffect
        delay(1500)
        if (historyKey != lastRecorded && chain != null && inputsText != null && resultText != null) {
            lastRecorded = historyKey
            onRecord.record(
                HistoryDraft(
                    toolId = "bend_allowance",
                    toolName = "Bend Allowance",
                    inputs = inputsText,
                    result = resultText,
                    detail = mathSteps(chain, selectedUnit)
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
            text = "When sheet is bent, the outside stretches and the inside compresses \u2014 only the " +
                "neutral axis keeps its length. Lay out the flat blank from the bend allowance, or the " +
                "bent part comes out with the wrong legs.",
            color = colors.numberKeyText.copy(alpha = 0.7f),
            fontSize = 13.sp
        )

        // 1 · Material & bend ---------------------------------------------------------------------
        CalcCard {
            SettingBlock(
                label = "Units",
                options = enabledUnits.map { it to it.label },
                selected = selectedUnit,
                onSelected = { tick(); scope.launch { container.settingsRepository.setActiveUnit(it) } }
            )
            Spacer(Modifier.height(12.dp))
            MeasurementField(
                label = "Material thickness",
                value = thicknessText,
                onValueChange = { thicknessText = it },
                unit = selectedUnit,
                placeholder = "e.g. 0.06 or 3/16",
                isError = thicknessText.isNotBlank() && thickness == null,
                errorText = when {
                    thicknessNegative -> "Thickness must be positive"
                    thicknessText.trim() == "0" -> "Thickness must be greater than zero"
                    else -> "Enter a number or fraction, e.g. 3/16"
                }
            )
            Spacer(Modifier.height(10.dp))
            MeasurementField(
                label = "Inside bend radius",
                value = radiusText,
                onValueChange = { radiusText = it },
                unit = selectedUnit,
                placeholder = "e.g. 0.125 or 0 for a sharp bend",
                isError = radiusNegative || (radiusText.isNotBlank() && radius == null),
                errorText = if (radiusNegative) "Radius cannot be negative" else "Enter a number or fraction (0 is a sharp bend)"
            )
            Spacer(Modifier.height(10.dp))
            MeasurementField(
                label = "Bend angle",
                value = angleText,
                onValueChange = { angleText = it },
                unit = null,
                suffix = "\u00B0",
                placeholder = "1 \u2013 180",
                isError = angleText.isNotBlank() && !angleValid,
                errorText = "Angle must be between ${BendAllowanceMath.MIN_ANGLE}\u00B0 and ${BendAllowanceMath.MAX_ANGLE}\u00B0"
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Quick angles",
                color = colors.numberKeyText.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_ANGLES.forEach { a ->
                    FilterChip(
                        selected = angleText.trim() == a.toString(),
                        onClick = { tick(); angleText = a.toString() },
                        label = { Text("$a\u00B0") }
                    )
                }
            }
        }

        // 2 · K-factor ---------------------------------------------------------------------------
        CalcCard {
            MeasurementField(
                label = "K-factor",
                value = kText,
                onValueChange = { kText = it },
                unit = null,
                placeholder = "0.00 \u2013 0.50",
                isError = kText.isNotBlank() && !kValid,
                errorText = "K-factor must be between ${ResultFormat.decimalText(BendAllowanceMath.MIN_K, 2)} and " +
                    ResultFormat.decimalText(BendAllowanceMath.MAX_K, 2)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Common shop starting points",
                color = colors.numberKeyText.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BendAllowanceMath.K_FACTOR_PRESETS.forEach { preset ->
                    val valueText = ResultFormat.decimalText(preset.kFactor, 2)
                    FilterChip(
                        selected = kText.trim() == valueText,
                        onClick = { tick(); kText = valueText },
                        label = { Text("${preset.label} \u00B7 $valueText") }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Presets are starting points only. K-factor is empirical \u2014 it varies with " +
                    "tooling, material lot and bending method, and the value stays editable above.",
                color = colors.numberKeyText.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }

        if (chain != null && primary != null && thickness != null) {
            // 3 · Results ------------------------------------------------------------------------
            BendResultCard(bend = primary, unit = selectedUnit, precision = precision)

            // 4 · Diagram ------------------------------------------------------------------------
            CalcCard {
                Text(
                    text = "Bend profile",
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
                BendDiagram(
                    thickness = thickness,
                    insideRadius = primary.insideRadius,
                    angleDegrees = primary.angleDegrees,
                    kFactor = primary.kFactor,
                    unitSymbol = selectedUnit.symbol,
                    modifier = Modifier.fillMaxWidth(),
                    visibility = settings.diagramVisibility,
                )
            }

            // 5 · Leg cross-check ----------------------------------------------------------------
            LegCrossCheckCard(
                legAText = legAText,
                onLegAChange = { legAText = it },
                legBText = legBText,
                onLegBChange = { legBText = it },
                unit = selectedUnit,
                precision = precision,
                crossCheck = crossCheck,
                bend = primary
            )

            // 6 · Multi-bend ---------------------------------------------------------------------
            MultiBendCard(
                chain = chain,
                extraBends = extraBends,
                onExtraBendsChange = { extraBends = it },
                straightText = straightText,
                onStraightChange = { straightText = it },
                unit = selectedUnit,
                precision = precision,
                visibility = settings.diagramVisibility,
            )

            // 7 · How this works -----------------------------------------------------------------
            HowItWorksCard(
                chain = chain,
                bend = primary,
                unit = selectedUnit,
                precision = precision,
                crossCheck = crossCheck
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Input controls                                                                                 */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: LengthUnit?,
    isError: Boolean,
    errorText: String,
    placeholder: String = "",
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        suffix = {
            val suffixText = suffix ?: unit?.symbol
            if (suffixText != null) Text(suffixText, fontWeight = FontWeight.SemiBold)
        },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        supportingText = { if (isError) Text(errorText, fontSize = 11.sp) },
        colors = fieldColors()
    )
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
/* Results                                                                                        */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun BendResultCard(bend: BendResult, unit: LengthUnit, precision: ResultPrecision) {
    val colors = CalcTheme.colors
    val ba = remember(bend.bendAllowance, precision) { ResultFormat.format(bend.bendAllowance, precision) }

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
                        text = "Bend allowance (BA)",
                        color = colors.numberKeyText.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${ba.decimalText} ${unit.symbol}",
                        color = colors.accentKey,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    FractionView(fraction = ba.rounded, color = colors.numberKeyText, fontSize = 20.sp)
                    Text(
                        text = precisionDescription(precision),
                        color = colors.numberKeyText.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LengthRow("Neutral axis offset (K\u00B7T)", bend.neutralAxisOffset, unit, precision)
        LengthRow("Neutral radius (R + K\u00B7T)", bend.neutralRadius, unit, precision)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)
        LengthRow("Outside setback (OSSB)", bend.outsideSetback, unit, precision)
        LengthRow("Bend deduction (BD)", bend.bendDeduction, unit, precision)
    }
}

@Composable
private fun LengthRow(label: String, value: Fraction?, unit: LengthUnit, precision: ResultPrecision) {
    val colors = CalcTheme.colors
    val formatted = remember(value, precision) { value?.let { ResultFormat.format(it, precision) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.numberKeyText, fontSize = 14.sp)
        Column(horizontalAlignment = Alignment.End) {
            if (formatted == null) {
                Text(
                    text = "undefined at 180\u00B0",
                    color = colors.numberKeyText.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            } else {
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
}

/* --------------------------------------------------------------------------------------------- */
/* Leg cross-check                                                                                */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun LegCrossCheckCard(
    legAText: String,
    onLegAChange: (String) -> Unit,
    legBText: String,
    onLegBChange: (String) -> Unit,
    unit: LengthUnit,
    precision: ResultPrecision,
    crossCheck: LegCrossCheck?,
    bend: BendResult,
) {
    val colors = CalcTheme.colors
    CalcCard {
        Text(
            text = "Flat length cross-check (optional)",
            fontWeight = FontWeight.SemiBold,
            color = colors.numberKeyText,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Enter the two leg lengths measured to the outside corner. The flat length is then " +
                "worked out both ways as a sanity check.",
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        MeasurementField(
            label = "Leg 1",
            value = legAText,
            onValueChange = onLegAChange,
            unit = unit,
            placeholder = "to the outside corner",
            isError = MeasurementParser.isNegative(legAText) || (legAText.isNotBlank() && MeasurementParser.parsePositive(legAText) == null),
            errorText = "Legs must be positive measurements"
        )
        Spacer(Modifier.height(10.dp))
        MeasurementField(
            label = "Leg 2",
            value = legBText,
            onValueChange = onLegBChange,
            unit = unit,
            placeholder = "to the outside corner",
            isError = MeasurementParser.isNegative(legBText) || (legBText.isNotBlank() && MeasurementParser.parsePositive(legBText) == null),
            errorText = "Legs must be positive measurements"
        )

        if (crossCheck != null) {
            Spacer(Modifier.height(12.dp))
            LengthRow("Flat length (leg + leg \u2212 BD)", crossCheck.byDeduction, unit, precision)
            LengthRow("Flat length (straight legs + BA)", crossCheck.byAllowance, unit, precision)
            Spacer(Modifier.height(8.dp))
            if (crossCheck.agrees) {
                Text(
                    text = "\u2713 Both methods agree (difference ${ResultFormat.decimalText(crossCheck.difference.abs(), 6, trimTrailingZeros = true)} ${unit.symbol}).",
                    color = colors.accentKey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.errorText.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, colors.errorText.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "\u26A0 The two methods disagree by " +
                            "${ResultFormat.decimalText(crossCheck.difference.abs(), 6)} ${unit.symbol}. " +
                            "That is a bug in the implementation, not a real mismatch.",
                        modifier = Modifier.padding(10.dp),
                        color = colors.errorText,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (bend.bendDeduction == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The leg method is undefined for a 180\u00B0 bend (parallel legs).",
                color = colors.numberKeyText.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Multi-bend                                                                                     */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun MultiBendCard(
    chain: BendChainResult,
    extraBends: String,
    onExtraBendsChange: (String) -> Unit,
    straightText: String,
    onStraightChange: (String) -> Unit,
    unit: LengthUnit,
    precision: ResultPrecision,
    visibility: AnnotationVisibility,
) {
    val colors = CalcTheme.colors
    val rows = remember(extraBends) { decodeBends(extraBends) }
    val bendCount = 1 + rows.size

    CalcCard {
        Text(
            text = "Multi-bend flat length",
            fontWeight = FontWeight.SemiBold,
            color = colors.numberKeyText,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Add bends to chain them up. Each extra bend uses the same thickness and K-factor, " +
                "with its own angle and radius. Bend 1 is the one set above.",
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))

        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.first,
                    onValueChange = { newValue ->
                        val updated = rows.toMutableList().also { it[index] = newValue to row.second }
                        onExtraBendsChange(encodeBends(updated))
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Bend ${index + 2} angle") },
                    suffix = { Text("\u00B0", fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    isError = MeasurementParser.parse(row.first)?.let { !BendAllowanceMath.isValidAngle(it) } ?: row.first.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = row.second,
                    onValueChange = { newValue ->
                        val updated = rows.toMutableList().also { it[index] = row.first to newValue }
                        onExtraBendsChange(encodeBends(updated))
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Bend ${index + 2} radius") },
                    suffix = { Text(unit.symbol, fontWeight = FontWeight.SemiBold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = fieldColors()
                )
                TextButton(onClick = {
                    val updated = rows.toMutableList().also { it.removeAt(index) }
                    onExtraBendsChange(encodeBends(updated))
                }) { Text("\u2715") }
            }
            Spacer(Modifier.height(8.dp))
        }

        TextButton(onClick = {
            onExtraBendsChange(encodeBends(rows + ("90" to "")))
        }) { Text("+ Add bend") }

        Spacer(Modifier.height(6.dp))
        MeasurementField(
            label = "Straight sections (total)",
            value = straightText,
            onValueChange = onStraightChange,
            unit = unit,
            placeholder = "unfolded straight material between/around bends",
            isError = MeasurementParser.isNegative(straightText) || (straightText.isNotBlank() && MeasurementParser.parsePositive(straightText) == null),
            errorText = "Must be a positive measurement"
        )

        Spacer(Modifier.height(10.dp))
        LengthRow("Total bend allowance ($bendCount bends)", chain.totalBendAllowance, unit, precision)
        LengthRow("Total outside setback", chain.totalOutsideSetback, unit, precision)
        LengthRow("Total bend deduction", chain.totalBendDeduction, unit, precision)
        if (chain.flatLength != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)
            LengthRow("Total developed length", chain.flatLength, unit, precision)
        }

        Spacer(Modifier.height(10.dp))
        // No layer toggles here: the schematic only has a Basics layer, so a toggle row would be
        // dead UI. The diagram still honours the global visibility for consistency.
        MultiBendDiagram(
            bends = chain.bends,
            flatLength = chain.flatLength,
            unitSymbol = unit.symbol,
            modifier = Modifier.fillMaxWidth(),
            visibility = visibility,
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Explanation                                                                                    */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun HowItWorksCard(
    chain: BendChainResult,
    bend: BendResult,
    unit: LengthUnit,
    precision: ResultPrecision,
    crossCheck: LegCrossCheck?,
) {
    val colors = CalcTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val symbol = unit.symbol

    val offset = ResultFormat.decimalText(bend.neutralAxisOffset, 4)
    val neutralRadius = ResultFormat.decimalText(bend.neutralRadius, 4)
    val ba = ResultFormat.decimalText(bend.bendAllowance, 4)
    val ossb = bend.outsideSetback?.let { ResultFormat.decimalText(it, 4) }
    val bd = bend.bendDeduction?.let { ResultFormat.decimalText(it, 4) }
    val r = ResultFormat.decimalText(bend.insideRadius, 4)
    val t = ResultFormat.decimalText(bend.thickness, 4)
    val k = ResultFormat.decimalText(bend.kFactor, 2)
    val angle = ResultFormat.decimalText(bend.angleDegrees, 2).trimEnd('0').trimEnd('.')

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
                Step(
                    "1 \u00B7 Neutral axis sits K\u00B7T in from the inside face",
                    "K = $k, T = $t $symbol \u2192 K\u00B7T = $k \u00D7 $t = $offset $symbol in from the inside.\n" +
                        "The neutral axis is bent around R + K\u00B7T = $r + $offset = $neutralRadius $symbol."
                )
                Step(
                    "2 \u00B7 Bend allowance BA = \u03C0\u00B7(R + K\u00B7T)\u00B7(A/180)",
                    "\u03C0 \u00D7 $neutralRadius \u00D7 ($angle\u00B0/180) = $ba $symbol.\n" +
                        "This is the arc length of the neutral axis \u2014 the highlighted curve on the drawing."
                )
                if (ossb != null && bd != null) {
                    Step(
                        "3 \u00B7 Outside setback OSSB = tan(A/2)\u00B7(R + T)",
                        "tan($angle\u00B0/2) \u00D7 ($r + $t) = $ossb $symbol.\n" +
                            "Bend deduction BD = 2\u00B7OSSB \u2212 BA = 2 \u00D7 $ossb \u2212 $ba = $bd $symbol."
                    )
                } else {
                    Step(
                        "3 \u00B7 Outside setback and bend deduction",
                        "Undefined at 180\u00B0 \u2014 the legs come out parallel, so tan(90\u00B0) does not exist."
                    )
                }
                Step(
                    "4 \u00B7 Both flat-length sums",
                    if (crossCheck != null) {
                        "Leg method: leg1 + leg2 \u2212 BD = ${ResultFormat.decimalText(crossCheck.byDeduction, 4)} $symbol.\n" +
                            "Allowance method: straight legs + BA = ${ResultFormat.decimalText(crossCheck.byAllowance, 4)} $symbol.\n" +
                            "They agree \u2014 as they must."
                    } else {
                        "Flat length = straight sections + \u03A3 BA. Total BA across ${chain.bends.size} bend(s) = " +
                            "${ResultFormat.decimalText(chain.totalBendAllowance, 4)} $symbol. " +
                            "Enter leg lengths above for the per-bend cross-check."
                    }
                )

                HorizontalDivider(color = colors.divider)

                Text(
                    text = "Why does the neutral axis shift inward? Bending stretches the outer fibres and " +
                        "compresses the inner ones. Only one plane keeps its original length, and for most " +
                        "materials that plane sits toward the inside \u2014 hence K around 0.3\u20130.45 rather " +
                        "than 0.5.",
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "90\u00B0 is the common case because most press-brake tooling is set up square, and " +
                        "the setback maths is simplest there.",
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    text = "Caution: K-factor is empirical. For critical work, bend a scrap coupon, measure the " +
                        "resulting leg lengths and back-calculate K \u2014 that captures your actual tooling and " +
                        "material \u2014 then enter YOUR measured K-factor above.",
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

@Composable
private fun Step(title: String, detail: String) {
    val colors = CalcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = colors.numberKeyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = colors.numberKeyText.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Helpers                                                                                        */
/* --------------------------------------------------------------------------------------------- */

/** Blank radius means a sharp bend (R = 0); otherwise a positive measurement. */
private fun parseRadius(text: String): Fraction? =
    if (text.isBlank()) Fraction.ZERO else MeasurementParser.parsePositive(text)

/** Extra bends are stored as saveable text: "angle|radius" rows joined by ";". */
private fun decodeBends(encoded: String): List<Pair<String, String>> =
    encoded.split(';').filter { it.isNotBlank() }.map { part ->
        val pieces = part.split('|')
        (pieces.getOrNull(0) ?: "") to (pieces.getOrNull(1) ?: "")
    }

private fun encodeBends(rows: List<Pair<String, String>>): String =
    rows.joinToString(";") { "${it.first}|${it.second}" }

private fun buildInputsText(
    thicknessText: String,
    radiusText: String,
    angleText: String,
    kText: String,
    bendCount: Int,
    straightText: String,
    unit: LengthUnit,
): String = buildString {
    append("T ${thicknessText.trim()} ${unit.symbol}")
    append(", R ${radiusText.trim().ifBlank { "0" }} ${unit.symbol}")
    append(", angle ${angleText.trim()}\u00B0")
    append(", K ${kText.trim()}")
    append(", $bendCount bend${if (bendCount == 1) "" else "s"}")
    if (straightText.isNotBlank()) append(", straight ${straightText.trim()} ${unit.symbol}")
}

/** The maths steps, as a single string, for a history entry's detail field. */
private fun mathSteps(chain: BendChainResult, unit: LengthUnit): String {
    val symbol = unit.symbol
    val bend = chain.bends.first()
    return buildList {
        add("1. K\u00B7T = ${ResultFormat.decimalText(bend.neutralAxisOffset, 4)} $symbol (neutral axis in from inside)")
        add("2. BA = \u03C0\u00B7(R + K\u00B7T)\u00B7(A/180) = ${ResultFormat.decimalText(bend.bendAllowance, 4)} $symbol")
        bend.outsideSetback?.let { add("3. OSSB = tan(A/2)\u00B7(R + T) = ${ResultFormat.decimalText(it, 4)} $symbol") }
        bend.bendDeduction?.let { add("4. BD = 2\u00B7OSSB \u2212 BA = ${ResultFormat.decimalText(it, 4)} $symbol") }
        add("5. Total BA (${chain.bends.size} bend(s)) = ${ResultFormat.decimalText(chain.totalBendAllowance, 4)} $symbol")
        chain.flatLength?.let { add("6. Flat length = straight + \u03A3BA = ${ResultFormat.decimalText(it, 4)} $symbol") }
    }.joinToString("\n")
}

private fun precisionDescription(precision: ResultPrecision): String = when (precision) {
    is ResultPrecision.FractionRound -> "nearest ${ResultPrecision.denominatorLabel(precision.denominator)}"
    is ResultPrecision.DecimalRound ->
        if (precision.places == 0) "nearest whole number" else "nearest 0.${"0".repeat((precision.places - 1).coerceAtLeast(0))}1"
}

/** A no-arg haptic tick honouring the global haptics setting, matching the other tools. */
@Composable
private fun rememberHapticTick(enabled: Boolean): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(enabled, haptics) {
        { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun BendAllowancePreview() {
    FractionCalculatorV1Theme(dynamicColor = false) {
        Surface {
            BendAllowanceScreen(settings = AppSettings(), initialThickness = "0.06")
        }
    }
}
