package com.figapps.fractioncalculatorv1.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.history.HistoryEntry

/** Full detail for a single history entry. */
@Composable
fun HistoryDetailDialog(
    entry: HistoryEntry,
    onDismiss: () -> Unit
) {
    val colors = CalcTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.toolName) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailBlock("Result", entry.result, colors)
                DetailBlock("Inputs", entry.inputs, colors)
                DetailBlock("Detail", entry.detail, colors)
                DetailBlock("When", formatHistoryTimestamp(entry.timestamp), colors)
            }
        }
    )
}

@Composable
private fun DetailBlock(
    label: String,
    value: String,
    colors: CalcColors
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = colors.numberKeyText,
            fontSize = 14.sp
        )
    }
}
