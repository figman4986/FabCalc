package com.figapps.fractioncalculatorv1.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.history.HistoryEntry
import com.figapps.fractioncalculatorv1.settings.AppSettings
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Formats a timestamp with the platform's locale-aware date/time formatter. */
internal fun formatHistoryTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance().format(Date(timestamp))

/**
 * The persistent calculation history. Entries are grouped by tool (groups ordered by their newest
 * entry, entries newest-first). Tap an entry for full detail; long-press to delete it.
 */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val colors = CalcTheme.colors
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val historyRepository = container.historyRepository

    val entries by historyRepository.entries.collectAsState(initial = emptyList())
    val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
    val recordingOff = !settings.historyEnabled

    var detail by remember { mutableStateOf<HistoryEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var showClear by remember { mutableStateOf(false) }

    if (entries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recordingOff) {
                RecordingOffBanner()
                Spacer(Modifier.height(12.dp))
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No calculations yet.\nResults you compute are recorded here.",
                    color = colors.numberKeyText.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val groups = remember(entries) {
        entries.groupBy { it.toolName }
            .toList()
            .sortedByDescending { (_, list) -> list.maxOf { it.timestamp } }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (recordingOff) {
            item(key = "recording-off") { RecordingOffBanner() }
        }
        item(key = "clear") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showClear = true }) {
                    Text("Clear history", color = colors.functionKey)
                }
            }
        }
        groups.forEach { (toolName, toolEntries) ->
            item(key = "header-$toolName") {
                Text(
                    text = toolName,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.numberKeyText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(items = toolEntries, key = { it.id }) { entry ->
                HistoryRow(
                    entry = entry,
                    onClick = { detail = entry },
                    onLongClick = { pendingDelete = entry }
                )
            }
        }
    }

    detail?.let { entry ->
        HistoryDetailDialog(entry = entry, onDismiss = { detail = null })
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text(entry.result) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { historyRepository.delete(entry) }
                    }
                ) { Text("Delete", color = colors.functionKey) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear history?") },
            text = { Text("This permanently deletes every recorded calculation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClear = false
                        scope.launch { historyRepository.clear() }
                    }
                ) { Text("Clear", color = colors.functionKey) }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = CalcTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = entry.result,
                color = colors.numberKeyText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (entry.inputs.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.inputs,
                    color = colors.numberKeyText.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatHistoryTimestamp(entry.timestamp),
                color = colors.numberKeyText.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

/** Shown when recording is disabled: entries are retained but nothing new is saved. */
@Composable
private fun RecordingOffBanner(modifier: Modifier = Modifier) {
    val colors = CalcTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = colors.signKey.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colors.signKey.copy(alpha = 0.4f))
    ) {
        Text(
            text = "Recording is off — new calculations are not saved. Existing entries remain.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = colors.numberKeyText,
            fontSize = 12.sp
        )
    }
}
