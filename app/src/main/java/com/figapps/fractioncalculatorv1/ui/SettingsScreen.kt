package com.figapps.fractioncalculatorv1.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.settings.ThemeMode
import com.figapps.fractioncalculatorv1.ui.components.CalcCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The global Settings screen. Every change is written straight to the DataStore-backed
 * [com.figapps.fractioncalculatorv1.settings.SettingsRepository], so the whole app reacts live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {}
) {
    val colors = CalcTheme.colors
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settingsRepository = container.settingsRepository
    val historyRepository = container.historyRepository

    var unitNotice by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }

    LaunchedEffect(unitNotice) {
        if (unitNotice) {
            delay(2500)
            unitNotice = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Result precision -------------------------------------------------------------
        CalcCard {
            SectionTitle("Result precision")
            Spacer(Modifier.height(12.dp))
            val isFraction = settings.precision is ResultPrecision.FractionRound
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isFraction,
                    onClick = {
                        scope.launch {
                            settingsRepository.setPrecision(ResultPrecision.DEFAULT)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                    colors = calcSegmentColors()
                ) { Text("Fraction") }
                SegmentedButton(
                    selected = !isFraction,
                    onClick = {
                        scope.launch {
                            settingsRepository.setPrecision(ResultPrecision.DecimalRound(3))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                    colors = calcSegmentColors()
                ) { Text("Decimal") }
            }
            Spacer(Modifier.height(12.dp))
            when (val precision = settings.precision) {
                is ResultPrecision.FractionRound -> FractionDenominatorChips(
                    selected = precision.denominator,
                    onSelect = { d ->
                        scope.launch { settingsRepository.setPrecision(ResultPrecision.FractionRound(d)) }
                    }
                )

                is ResultPrecision.DecimalRound -> DecimalPlacesSlider(
                    places = precision.places,
                    onSelect = { p ->
                        scope.launch { settingsRepository.setPrecision(ResultPrecision.DecimalRound(p)) }
                    }
                )
            }
        }

        // 2. Measurement units ------------------------------------------------------------
        CalcCard {
            SectionTitle("Measurement units")
            Spacer(Modifier.height(6.dp))
            LengthUnit.entries.forEach { unit ->
                val enabled = unit in settings.enabledUnits
                SwitchRow(
                    label = unit.label,
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (!checked && settings.enabledUnits.size <= 1) {
                            unitNotice = true
                        } else {
                            scope.launch { settingsRepository.setUnitEnabled(unit, checked) }
                        }
                    }
                )
            }
            if (unitNotice) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "At least one unit must stay on",
                    color = colors.functionKey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. History ----------------------------------------------------------------------
        CalcCard {
            SectionTitle("History")
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "Record calculations",
                checked = settings.historyEnabled,
                onCheckedChange = { scope.launch { settingsRepository.setHistoryEnabled(it) } }
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenHistory) { Text("View history") }
                TextButton(onClick = { showClearHistory = true }) {
                    Text("Clear history", color = colors.functionKey)
                }
            }
        }

        // 4. Appearance -------------------------------------------------------------------
        CalcCard {
            SectionTitle("Appearance")
            Spacer(Modifier.height(12.dp))
            val modes = ThemeMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { settingsRepository.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        icon = {},
                        colors = calcSegmentColors()
                    ) { Text(mode.displayLabel()) }
                }
            }
        }

        // 5. Feedback & display -----------------------------------------------------------
        CalcCard {
            SectionTitle("Feedback & display")
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "Haptic feedback",
                checked = settings.hapticsEnabled,
                onCheckedChange = { scope.launch { settingsRepository.setHapticsEnabled(it) } }
            )
            SwitchRow(
                label = "Keep screen awake",
                checked = settings.keepScreenAwake,
                onCheckedChange = { scope.launch { settingsRepository.setKeepScreenAwake(it) } }
            )
        }
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("Clear history?") },
            text = { Text("This permanently deletes every recorded calculation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistory = false
                        scope.launch { historyRepository.clear() }
                    }
                ) { Text("Clear", color = colors.functionKey) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) { Text("Cancel") }
            }
        )
    }
}

/** Segmented-button colours that match the rest of the app's accent palette. */
@Composable
private fun calcSegmentColors(): SegmentedButtonColors {
    val colors = CalcTheme.colors
    return SegmentedButtonDefaults.colors(
        activeContainerColor = colors.accentKey,
        activeContentColor = colors.accentKeyText,
        activeBorderColor = colors.accentKey,
        inactiveContainerColor = colors.cardBackground,
        inactiveContentColor = colors.numberKeyText,
        inactiveBorderColor = colors.divider
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        color = CalcTheme.colors.numberKeyText,
        fontSize = 14.sp
    )
}

@Composable
private fun FractionDenominatorChips(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val colors = CalcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultPrecision.FRACTION_DENOMINATORS.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { denominator ->
                    FilterChip(
                        selected = denominator == selected,
                        onClick = { onSelect(denominator) },
                        label = { Text(ResultPrecision.denominatorLabel(denominator)) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.cardBackground,
                            labelColor = colors.numberKeyText,
                            selectedContainerColor = colors.accentKey,
                            selectedLabelColor = colors.accentKeyText
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DecimalPlacesSlider(
    places: Int,
    onSelect: (Int) -> Unit
) {
    val colors = CalcTheme.colors
    Column {
        Text(
            text = if (places == 1) "1 decimal place" else "$places decimal places",
            color = colors.numberKeyText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = places.toFloat(),
            onValueChange = { onSelect(it.roundToInt()) },
            valueRange = ResultPrecision.MIN_DECIMAL_PLACES.toFloat()..
                ResultPrecision.MAX_DECIMAL_PLACES.toFloat(),
            steps = ResultPrecision.MAX_DECIMAL_PLACES - ResultPrecision.MIN_DECIMAL_PLACES - 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = CalcTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.numberKeyText, fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
