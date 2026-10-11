package com.figapps.fractioncalculatorv1.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.figapps.fractioncalculatorv1.settings.AppContainer

/** Provides the app-wide [AppContainer] to the composition tree. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided")
}
