package com.figapps.fractioncalculatorv1.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCatalog
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCategory
import com.figapps.fractioncalculatorv1.domain.convert.ConverterEngine
import com.figapps.fractioncalculatorv1.domain.convert.ConverterFormat
import com.figapps.fractioncalculatorv1.domain.convert.GaugeMaterial
import com.figapps.fractioncalculatorv1.domain.convert.Quantity
import com.figapps.fractioncalculatorv1.domain.convert.SheetMetalGauge
import com.figapps.fractioncalculatorv1.domain.convert.UnitDef
import com.figapps.fractioncalculatorv1.history.HistoryDraft
import com.figapps.fractioncalculatorv1.history.HistoryRecorder
import com.figapps.fractioncalculatorv1.history.NoOpHistoryRecorder
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.ui.components.CalcCard
import com.figapps.fractioncalculatorv1.ui.components.SettingBlock
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Unit Converter tool.
 *
 * Structure mirrors Converter NOW: a category grid, then a converter that turns one typed value into
 * every other unit in the category at once. Typing converts live; the hero cards pick the two units
 * and can be swapped; tapping any row promotes it to the target. Temperature (affine) and the
 * sheet-metal gauge lookup (per-material tables) are handled specially.
 *
 * The last-used unit pair per category is remembered in the global settings, and committed
 * conversions are recorded to history once each (debounced, never per keystroke).
 */
@Composable
fun UnitConverterScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    onRecord: HistoryRecorder = NoOpHistoryRecorder,
) {
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val category = categoryId?.let { ConverterCatalog.category(it) }

    when {
        categoryId == Quantity.GAUGE.id ->
            GaugeLookup(
                settings = settings,
                modifier = modifier,
                onBack = { categoryId = null },
            )

        category != null ->
            CategoryConverter(
                category = category,
                settings = settings,
                modifier = modifier,
                onBack = { categoryId = null },
                onRecord = onRecord,
            )

        else ->
            CategoryGrid(
                modifier = modifier,
                onOpen = { categoryId = it.id },
            )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Category landing                                                                               */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun CategoryGrid(modifier: Modifier, onOpen: (Quantity) -> Unit) {
    val colors = CalcTheme.colors
    val entries = remember { ConverterCatalog.categories.map { it.quantity } + Quantity.GAUGE }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Convert between shop units. Pick a category, type a value and everything " +
                "converts at once.",
            color = colors.numberKeyText.copy(alpha = 0.7f),
            fontSize = 13.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.id }) { quantity ->
                CategoryCard(quantity = quantity, onClick = { onOpen(quantity) })
            }
        }
    }
}

@Composable
private fun CategoryCard(quantity: Quantity, onClick: () -> Unit) {
    val colors = CalcTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.accentKey.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = quantity.glyph,
                    color = colors.accentKey,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            Text(
                text = quantity.title,
                color = colors.numberKeyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Factor-based converter                                                                         */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun CategoryConverter(
    category: ConverterCategory,
    settings: AppSettings,
    modifier: Modifier,
    onBack: () -> Unit,
    onRecord: HistoryRecorder,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val precision = settings.precision
    val tick = rememberHapticTick(settings.hapticsEnabled)

    var inputText by rememberSaveable { mutableStateOf("") }

    // Settings is the single source of truth for the selected units, so the choice is per-category
    // and survives navigation, rotation and process death.
    val saved = settings.converterUnits[category.quantity.id]
    val fromUnit = saved?.fromId?.let { category.unit(it) }
        ?: category.unit(category.defaultFromId)
        ?: category.units.first()
    val toUnit = saved?.toId?.let { category.unit(it) }
        ?: category.unit(category.defaultToId)
        ?: category.units.getOrElse(1) { category.units.first() }

    fun persist(nextFrom: UnitDef, nextTo: UnitDef) {
        scope.launch {
            container.settingsRepository.setConverterUnits(category.quantity.id, nextFrom.id, nextTo.id)
        }
    }

    val input = remember(inputText) { MeasurementParser.parse(inputText) }

    // Convert once per input/unit change; the list is short and the maths is trivial.
    val rows = remember(category.quantity.id, input, fromUnit.id, toUnit.id, precision) {
        category.units
            .filter { it.id != fromUnit.id }
            .map { unit -> unit to input?.let { ConverterEngine.convert(it, fromUnit, unit) } }
    }

    RecordConversion(
        enabled = settings.historyEnabled,
        historyKey = input?.let { "${category.quantity.id}|${fromUnit.id}|${toUnit.id}|${inputText.trim()}" },
        onRecord = onRecord,
        draft = {
            val value = ConverterEngine.convert(input!!, fromUnit, toUnit)
            val fromText = "${inputText.trim()} ${fromUnit.symbol}"
            val toText = "${ConverterFormat.format(value, precision)} ${toUnit.symbol}"
            HistoryDraft(
                toolId = "converter",
                toolName = "Unit Converter",
                inputs = "$fromText  ·  ${category.quantity.title}",
                result = toText,
                detail = "$fromText = $toText",
            )
        },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ConverterHeader(title = category.quantity.title, onBack = onBack)

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            label = { Text("Value") },
            placeholder = { Text("e.g. 25.4 or 3 7/16") },
            singleLine = true,
            isError = inputText.isNotBlank() && input == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            supportingText = {
                Text(
                    text = if (inputText.isNotBlank() && input == null) {
                        "Enter a number or fraction"
                    } else {
                        "Converts as you type"
                    },
                    fontSize = 11.sp,
                )
            },
            colors = converterFieldColors(),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HeroUnitCard(
                role = "From",
                unit = fromUnit,
                units = category.units,
                onSelect = { tick(); persist(it, toUnit) },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    tick()
                    persist(toUnit, fromUnit)
                },
            ) {
                Text("\u21C4", color = CalcTheme.colors.accentKey, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            HeroUnitCard(
                role = "To",
                unit = toUnit,
                units = category.units,
                onSelect = { tick(); persist(fromUnit, it) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = CalcTheme.colors.divider)
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.first.id }) { (unit, value) ->
                UnitRow(
                    unit = unit,
                    value = value,
                    precision = precision,
                    selected = unit.id == toUnit.id,
                    onClick = { tick(); persist(fromUnit, unit) },
                )
            }
        }
    }
}

@Composable
private fun HeroUnitCard(
    role: String,
    unit: UnitDef,
    units: List<UnitDef>,
    onSelect: (UnitDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CalcTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(CalcCornerRadius),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = role,
                    color = colors.numberKeyText.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = unit.symbol,
                        color = colors.accentKey,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Change unit",
                        tint = colors.numberKeyText.copy(alpha = 0.6f),
                    )
                }
                Text(
                    text = unit.name,
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.name} (${option.symbol})") },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun UnitRow(
    unit: UnitDef,
    value: Fraction?,
    precision: ResultPrecision,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = CalcTheme.colors
    val primary = value?.let { ConverterFormat.format(it, precision) }
    val secondary = value?.let { ConverterFormat.secondary(it, unit, precision) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = if (selected) colors.accentKey.copy(alpha = 0.12f) else colors.cardBackground,
        border = BorderStroke(1.dp, if (selected) colors.accentKey else colors.divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unit.name,
                    color = colors.numberKeyText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = unit.symbol,
                    color = colors.numberKeyText.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (value != null && primary != null) "$primary ${unit.symbol}" else ConverterFormat.NO_VALUE,
                    color = if (value == null) colors.numberKeyText.copy(alpha = 0.4f) else colors.numberKeyText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (secondary != null) {
                    Text(
                        text = secondary,
                        color = colors.numberKeyText.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Sheet-metal gauge lookup                                                                       */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun GaugeLookup(
    settings: AppSettings,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    val colors = CalcTheme.colors
    val precision = settings.precision
    val tick = rememberHapticTick(settings.hapticsEnabled)
    var materialId by rememberSaveable { mutableStateOf(SheetMetalGauge.materials.first().id) }
    val material = SheetMetalGauge.material(materialId) ?: SheetMetalGauge.materials.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        ConverterHeader(title = Quantity.GAUGE.title, onBack = onBack)

        CalcCard {
            SettingBlock(
                label = "Material",
                options = SheetMetalGauge.materials.map { it.id to it.shortName },
                selected = materialId,
                onSelected = { tick(); materialId = it },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Gauge numbers are not linear and differ by material — pick the correct table.",
                color = colors.numberKeyText.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GaugeHeaderCell("Gauge", 0.8f)
            GaugeHeaderCell("mm", 1f)
            GaugeHeaderCell("inch", 1f)
            GaugeHeaderCell("fraction", 1f)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colors.divider)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(SheetMetalGauge.gaugeNumbers, key = { it }) { gauge ->
                GaugeRow(material = material, gauge = gauge, precision = precision)
            }
        }
    }
}

@Composable
private fun GaugeRow(material: GaugeMaterial, gauge: Int, precision: ResultPrecision) {
    val colors = CalcTheme.colors
    val inches = material.inches[gauge]
    val mm = SheetMetalGauge.thicknessMm(material, gauge)
    val fraction = inches?.let { ConverterFormat.fractionSecondary(it, precision) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GaugeCell(gauge.toString(), 0.8f, colors.numberKeyText, FontWeight.SemiBold)
        GaugeCell(mm?.let { ConverterFormat.format(it, precision) } ?: ConverterFormat.NO_VALUE, 1f, colors.numberKeyText)
        GaugeCell(inches?.let { ConverterFormat.format(it, precision) } ?: ConverterFormat.NO_VALUE, 1f, colors.numberKeyText)
        GaugeCell(fraction ?: ConverterFormat.NO_VALUE, 1f, colors.numberKeyText.copy(alpha = 0.65f))
    }
}

@Composable
private fun RowScope.GaugeHeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = CalcTheme.colors.numberKeyText.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RowScope.GaugeCell(text: String, weight: Float, color: Color, weight2: FontWeight = FontWeight.Normal) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = color,
        fontSize = 12.sp,
        fontWeight = weight2,
    )
}

/* --------------------------------------------------------------------------------------------- */
/* Shared bits                                                                                    */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun ConverterHeader(title: String, onBack: () -> Unit) {
    val colors = CalcTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to categories",
                tint = colors.numberKeyText,
            )
        }
        Text(
            text = title,
            color = colors.numberKeyText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

/** Records one history entry per committed conversion, debounced so typing does not spam history. */
@Composable
private fun RecordConversion(
    enabled: Boolean,
    historyKey: String?,
    onRecord: HistoryRecorder,
    draft: () -> HistoryDraft,
) {
    var lastRecorded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(historyKey) {
        if (historyKey == null || !enabled) return@LaunchedEffect
        delay(1200)
        if (historyKey != lastRecorded) {
            lastRecorded = historyKey
            onRecord.record(draft())
        }
    }
}

/** A no-arg haptic tick honouring the global haptics setting, matching the keypad's behaviour. */
@Composable
private fun rememberHapticTick(enabled: Boolean): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(enabled, haptics) {
        { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

@Composable
private fun converterFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CalcTheme.colors.accentKey,
    unfocusedBorderColor = CalcTheme.colors.divider,
    focusedLabelColor = CalcTheme.colors.accentKey,
    unfocusedLabelColor = CalcTheme.colors.numberKeyText.copy(alpha = 0.6f),
    cursorColor = CalcTheme.colors.accentKey,
    focusedTextColor = CalcTheme.colors.numberKeyText,
    unfocusedTextColor = CalcTheme.colors.numberKeyText,
)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun UnitConverterPreview() {
    FractionCalculatorV1Theme(dynamicColor = false) {
        Surface { UnitConverterScreen(settings = AppSettings()) }
    }
}
