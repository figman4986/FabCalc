package com.figapps.fractioncalculatorv1.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.BuildConfig
import com.figapps.fractioncalculatorv1.R
import com.figapps.fractioncalculatorv1.settings.AppSettings

/**
 * The tools available in the app. Add a new entry here and it automatically shows up in the tool
 * menu — this is the extension point for future fabricator tools.
 */
enum class AppTool(val title: String) {
    FRACTION_CALCULATOR("Fraction Calculator"),
    OFFSET_CALCULATOR("Offset Calculator"),
    CIRCLE_DIVIDER("Circle Divider"),
    UNIT_CONVERTER("Unit Converter"),
    BEND_ALLOWANCE("Bend Allowance"),
    WIRE_LIBRARY("Wire Library")
}

/** Overlay destinations reachable from the top bar (they sit on top of the selected tool). */
private const val OVERLAY_SETTINGS = "settings"
private const val OVERLAY_HISTORY = "history"

/**
 * Stable [androidx.compose.runtime.saveable.SaveableStateHolder] keys for the swappable content
 * slots. Each tool and overlay keeps its own [rememberSaveable] state (inputs, toggles, sliders,
 * selected wedge, scroll position) alive while it is off-screen. Exposed internally so the key
 * mapping can be unit-tested for uniqueness and stability.
 */
internal object ContentKey {
    const val SETTINGS = "overlay:settings"
    const val HISTORY = "overlay:history"

    /** The persistent state-slot key for [tool]. Derived from the enum name, so it is stable. */
    fun tool(tool: AppTool): String = "tool:${tool.name}"

    /** Every content-slot key; used by tests to assert the keys never collide. */
    fun all(): List<String> = AppTool.entries.map(::tool) + SETTINGS + HISTORY
}

/**
 * App-level shell: owns the top bar, the tool menu and the two overlay destinations (Settings and
 * History), and swaps the selected tool's screen in and out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FractionCalculatorApp(settings: AppSettings) {
    val colors = CalcTheme.colors
    val container = LocalAppContainer.current
    var currentTool by rememberSaveable { mutableStateOf(AppTool.FRACTION_CALCULATOR) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Keeps each tool's / overlay's rememberSaveable state alive while it is off-screen, so
    // leaving a tool and returning preserves the user's inputs. The holder itself is saveable,
    // so the states also survive configuration change and process death.
    val saveableStateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = overlay != null) { overlay = null }

    val title = when (overlay) {
        OVERLAY_SETTINGS -> "Settings"
        OVERLAY_HISTORY -> "History"
        else -> currentTool.title
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (overlay != null) {
                        IconButton(onClick = { overlay = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Choose a tool"
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                AppTool.entries.forEach { tool ->
                                    DropdownMenuItem(
                                        text = { Text(tool.title) },
                                        onClick = {
                                            currentTool = tool
                                            overlay = null
                                            menuExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    onClick = {
                                        overlay = OVERLAY_HISTORY
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        overlay = OVERLAY_SETTINGS
                                        menuExpanded = false
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("About") },
                                    onClick = {
                                        showAbout = true
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (overlay == null) {
                        IconButton(onClick = { overlay = OVERLAY_HISTORY }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "History"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.numberKeyText,
                    navigationIconContentColor = colors.numberKeyText,
                    actionIconContentColor = colors.numberKeyText
                )
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (overlay) {
            OVERLAY_SETTINGS ->
                saveableStateHolder.SaveableStateProvider(ContentKey.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        modifier = contentModifier,
                        onOpenHistory = { overlay = OVERLAY_HISTORY }
                    )
                }

            OVERLAY_HISTORY ->
                saveableStateHolder.SaveableStateProvider(ContentKey.HISTORY) {
                    HistoryScreen(modifier = contentModifier)
                }

            // Each tool gets its own persistent state slot, so switching tools and back keeps
            // every tool's inputs independent.
            else -> saveableStateHolder.SaveableStateProvider(ContentKey.tool(currentTool)) {
                when (currentTool) {
                    AppTool.FRACTION_CALCULATOR ->
                        CalculatorScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onRecord = container.historyRepository
                        )

                    AppTool.OFFSET_CALCULATOR ->
                        OffsetCalculatorScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onRecord = container.historyRepository
                        )

                    AppTool.CIRCLE_DIVIDER ->
                        CircleDividerScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onRecord = container.historyRepository
                        )

                    AppTool.UNIT_CONVERTER ->
                        UnitConverterScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onRecord = container.historyRepository
                        )

                    AppTool.BEND_ALLOWANCE ->
                        BendAllowanceScreen(
                            settings = settings,
                            modifier = contentModifier,
                            onRecord = container.historyRepository
                        )

                    AppTool.WIRE_LIBRARY ->
                        WireLibraryScreen(
                            settings = settings,
                            modifier = contentModifier
                        )
                }
            }
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/** Minimal About dialog: app name, version and a one-line description. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Version ${BuildConfig.VERSION_NAME}")
                Text(text = "Shop-floor calculators for metal fabrication:")
                // Rendered straight from the AppTool enum so this list can never go stale again.
                AppTool.entries.forEach { tool ->
                    Text(text = "\u2022  ${tool.title}", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
