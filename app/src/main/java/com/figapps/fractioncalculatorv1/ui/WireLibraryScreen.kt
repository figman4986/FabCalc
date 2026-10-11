package com.figapps.fractioncalculatorv1.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.wire.MetalType
import com.figapps.fractioncalculatorv1.wire.WireDatasheet
import com.figapps.fractioncalculatorv1.wire.WireLibraryFilter
import com.figapps.fractioncalculatorv1.wire.WireProcess
import com.figapps.fractioncalculatorv1.wire.WireSizes
import com.figapps.fractioncalculatorv1.wire.WireStageResult
import com.figapps.fractioncalculatorv1.wire.WireTags
import com.figapps.fractioncalculatorv1.wire.toTags
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.math.abs

/** A staged PDF awaiting tags (the file is already in internal storage). */
private data class StagedTags(val file: File, val sha256: String)

/**
 * The Welding Wire Datasheet Library: an offline store of imported datasheet PDFs tagged by metal
 * type, process, size and AWS classification. Purely local — no network, no permissions.
 */
@Composable
fun WireLibraryScreen(
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val colors = CalcTheme.colors
    val container = LocalAppContainer.current
    val repository = container.wireRepository
    val scope = rememberCoroutineScope()

    val all by repository.entries.collectAsState(initial = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var metalName by rememberSaveable { mutableStateOf<String?>(null) }
    var processName by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }

    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<StagedTags?>(null) }
    var editing by remember { mutableStateOf<WireDatasheet?>(null) }
    var duplicate by remember { mutableStateOf<WireDatasheet?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageTitle by remember { mutableStateOf("Import failed") }
    var pendingDelete by remember { mutableStateOf<WireDatasheet?>(null) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            progress = 0f
            when (val result = repository.stagePdf(uri) { progress = it }) {
                is WireStageResult.Staged -> pending = StagedTags(result.file, result.sha256)
                is WireStageResult.Duplicate -> duplicate = result.existing
                WireStageResult.NotPdf -> {
                    messageTitle = "Import failed"
                    message = "That file isn't a PDF. Pick a datasheet PDF."
                }
                is WireStageResult.Failed -> {
                    messageTitle = "Import failed"
                    message = result.message
                }
            }
            importing = false
            progress = null
        }
    }

    // --- in-app viewer takes over the whole tool surface ---
    val viewing = viewingId?.let { id -> all.firstOrNull { it.id == id } }
    if (viewing != null) {
        WireViewerScreen(
            datasheet = viewing,
            unit = settings.activeUnit,
            modifier = modifier,
            onBack = { viewingId = null },
            onDelete = {
                scope.launch {
                    try {
                        repository.delete(viewing)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        messageTitle = "Could not remove datasheet"
                        message = failure.message ?: "The datasheet entry was kept because it could not be removed."
                    }
                    viewingId = null
                }
            },
        )
        return
    }

    val filter = WireLibraryFilter(
        searchText = query,
        metalType = metalName?.let { name -> MetalType.entries.firstOrNull { it.name == name } },
        process = processName?.let { name -> WireProcess.entries.firstOrNull { it.name == name } },
    )
    val groups = remember(all, filter) { WireLibraryFilter.group(filter.apply(all)) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- search + filters ---
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search brand, product or AWS class") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = fieldColors(),
                )
                FilterChipRow(
                    label = "Metal",
                    options = listOf(null) + MetalType.entries,
                    selected = filter.metalType,
                    labelOf = { it?.label ?: "All" },
                    onSelected = { metalName = it?.name },
                )
                FilterChipRow(
                    label = "Process",
                    options = listOf(null) + WireProcess.entries,
                    selected = filter.process,
                    labelOf = { it?.label ?: "All" },
                    onSelected = { processName = it?.name },
                )
            }

            if (groups.isEmpty()) {
                EmptyState(hasAny = all.isNotEmpty(), modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).imePadding(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "h-${group.metalType.name}") {
                            Text(
                                text = group.metalType.label,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.numberKeyText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        items(items = group.entries, key = { it.id }) { datasheet ->
                            WireRow(
                                datasheet = datasheet,
                                unit = settings.activeUnit,
                                onView = { viewingId = datasheet.id },
                                onEdit = { editing = datasheet },
                                onDelete = { pendingDelete = datasheet },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            containerColor = colors.accentKey,
            contentColor = colors.accentKeyText,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Import a datasheet PDF")
        }
    }

    // --- dialogs ---
    if (importing) {
        ImportProgressDialog(progress = progress)
    }

    pending?.let { staged ->
        WireTagFormDialog(
            title = "Tag datasheet",
            initial = WireTags(),
            unit = settings.activeUnit,
            onDismiss = {
                scope.launch { repository.discard(staged.file) }
                pending = null
            },
            onConfirm = { tags ->
                pending = null
                scope.launch {
                    try {
                        repository.commit(staged.file, staged.sha256, tags)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        messageTitle = "Import failed"
                        message = failure.message ?: "The datasheet could not be added."
                    }
                }
            },
        )
    }

    editing?.let { datasheet ->
        WireTagFormDialog(
            title = "Edit tags",
            initial = datasheet.toTags(),
            unit = settings.activeUnit,
            onDismiss = { editing = null },
            onConfirm = { tags ->
                editing = null
                scope.launch { repository.updateTags(datasheet, tags) }
            },
        )
    }

    duplicate?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text("Already in the library") },
            text = {
                Text(
                    "This PDF is already imported as " +
                        "\"${existing.productName.ifBlank { existing.fileName }}\"" +
                        "${if (existing.brand.isNotBlank()) " (${existing.brand})" else ""}. " +
                        "The second copy was discarded."
                )
            },
            confirmButton = { TextButton(onClick = { duplicate = null }) { Text("OK") } },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(messageTitle) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    pendingDelete?.let { datasheet ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this datasheet?") },
            text = {
                Text(
                    "\"${datasheet.productName.ifBlank { datasheet.fileName }}\" and its PDF will be " +
                        "permanently removed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        try {
                            repository.delete(datasheet)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Exception) {
                            messageTitle = "Could not remove datasheet"
                            message = failure.message ?: "The datasheet entry was kept because it could not be removed."
                        }
                    }
                }) { Text("Delete", color = colors.errorText) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* List pieces                                                                                    */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun WireRow(
    datasheet: WireDatasheet,
    unit: LengthUnit,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = CalcTheme.colors
    var menu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onView, onLongClick = { menu = true }),
        shape = RoundedCornerShape(CalcCornerRadius),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = datasheet.productName.ifBlank { datasheet.fileName },
                    color = colors.numberKeyText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (datasheet.brand.isNotBlank()) {
                    Text(
                        text = datasheet.brand,
                        color = colors.numberKeyText.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (datasheet.awsClassification.isNotBlank()) {
                        Tag(datasheet.awsClassification)
                    }
                    WireSizes.display(datasheet.wireSizeMm, unit)?.let { Tag(it) }
                    Text(
                        text = datasheet.process.marker,
                        color = colors.accentKey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = colors.numberKeyText)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("View") }, onClick = { menu = false; onView() })
                    DropdownMenuItem(text = { Text("Edit tags") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.errorText) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    val colors = CalcTheme.colors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.accentKey.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colors.accentKey.copy(alpha = 0.4f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = colors.numberKeyText,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun <T> FilterChipRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    val colors = CalcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(labelOf(option)) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasAny: Boolean, modifier: Modifier = Modifier) {
    val colors = CalcTheme.colors
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = if (hasAny) {
                "No datasheets match these filters.\nTry a different metal, process or search."
            } else {
                "No datasheets yet.\nTap the + button to import a welding-wire datasheet PDF " +
                    "and tag it by metal, process and AWS classification."
            },
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(28.dp),
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Dialogs                                                                                        */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun ImportProgressDialog(progress: Float?) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Importing PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Copying the datasheet into the library and hashing it\u2026")
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
    )
}

/** The tag form, shared by import and edit. */
@Composable
private fun WireTagFormDialog(
    title: String,
    initial: WireTags,
    unit: LengthUnit,
    onDismiss: () -> Unit,
    onConfirm: (WireTags) -> Unit,
) {
    val colors = CalcTheme.colors
    var brand by remember { mutableStateOf(initial.brand) }
    var product by remember { mutableStateOf(initial.productName) }
    var aws by remember { mutableStateOf(initial.awsClassification) }
    var metal by remember { mutableStateOf(initial.metalType) }
    var process by remember { mutableStateOf(initial.process) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    var sizeMm by remember { mutableStateOf(initial.wireSizeMm) }
    var custom by remember {
        mutableStateOf(
            initial.wireSizeMm
                ?.takeIf { mm -> WireSizes.commonOptions(unit).none { abs(it.second - mm) < 1e-6 } }
                ?.let { mm -> WireSizes.display(mm, unit)?.removeSuffix(" in")?.removeSuffix(" mm") }
                .orEmpty()
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(CalcCornerRadius),
            color = colors.cardBackground,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, color = colors.numberKeyText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Brand (required)") },
                    singleLine = true,
                    isError = brand.isBlank(),
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = product,
                    onValueChange = { product = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Product name") },
                    singleLine = true,
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = aws,
                    onValueChange = { aws = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AWS classification") },
                    placeholder = { Text("e.g. ER70S-6, E308LT-1") },
                    singleLine = true,
                    colors = fieldColors(),
                )

                DropdownField("Metal type", metal, MetalType.entries, { it.label }, { metal = it })
                DropdownField("Process", process, WireProcess.entries, { it.label }, { process = it })

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Wire size",
                        color = colors.numberKeyText.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WireSizes.commonOptions(unit).forEach { (label, mm) ->
                            FilterChip(
                                selected = sizeMm != null && abs(sizeMm!! - mm) < 1e-6,
                                onClick = {
                                    sizeMm = if (sizeMm != null && abs(sizeMm!! - mm) < 1e-6) null else mm
                                    custom = ""
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { text ->
                            custom = text
                            val parsed = text.trim().toDoubleOrNull()
                            sizeMm = parsed?.let {
                                if (unit == LengthUnit.INCHES) WireSizes.inchesToMillimetres(it) else it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom size (${if (unit == LengthUnit.INCHES) "in" else "mm"})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors(),
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    colors = fieldColors(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = brand.isNotBlank(),
                        onClick = {
                            onConfirm(
                                WireTags(
                                    brand = brand,
                                    productName = product,
                                    awsClassification = aws,
                                    metalType = metal,
                                    process = process,
                                    wireSizeMm = sizeMm,
                                    notes = notes,
                                )
                            )
                        },
                    ) { Text("Save") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    val colors = CalcTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = labelOf(value),
                    modifier = Modifier.weight(1f),
                    color = colors.numberKeyText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("\u25BE", color = colors.numberKeyText.copy(alpha = 0.7f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labelOf(option)) },
                        onClick = { onSelected(option); expanded = false },
                    )
                }
            }
        }
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
    unfocusedTextColor = CalcTheme.colors.numberKeyText,
)
