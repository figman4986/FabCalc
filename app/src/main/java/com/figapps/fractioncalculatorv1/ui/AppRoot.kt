package com.figapps.fractioncalculatorv1.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.figapps.fractioncalculatorv1.FractionApp
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.settings.ThemeMode
import com.figapps.fractioncalculatorv1.ui.theme.FractionCalculatorV1Theme

/**
 * Application root: wires the [com.figapps.fractioncalculatorv1.settings.AppContainer] into the
 * composition, observes the persisted settings, applies the theme and renders the app shell.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as FractionApp).container }
    CompositionLocalProvider(LocalAppContainer provides container) {
        val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        FractionCalculatorV1Theme(darkTheme = dark, dynamicColor = false) {
            KeepScreenOn(enabled = settings.keepScreenAwake)
            FractionCalculatorApp(settings = settings)
        }
    }
}

/**
 * Toggles the window's KEEP_SCREEN_ON flag for as long as it is composed. This is the window flag
 * (not a wake lock), so no permission is required.
 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
