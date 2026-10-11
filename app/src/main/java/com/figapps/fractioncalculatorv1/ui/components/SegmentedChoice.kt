package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.ui.CalcTheme

/**
 * A labelled [SegmentedChoice]. Shared by every tool so option pickers look identical; uses the
 * accent palette for the active segment.
 */
@Composable
fun <T> SettingBlock(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    val colors = CalcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        SegmentedChoice(options = options, selected = selected, onSelected = onSelected)
    }
}

/** A single-choice segmented control painted with the app's accent palette. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CalcTheme.colors
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.accentKey,
                    activeContentColor = colors.accentKeyText,
                    activeBorderColor = colors.accentKey,
                    inactiveContainerColor = colors.cardBackground,
                    inactiveContentColor = colors.numberKeyText,
                    inactiveBorderColor = colors.divider
                )
            ) {
                Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
