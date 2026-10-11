package com.figapps.fractioncalculatorv1.settings

import com.figapps.fractioncalculatorv1.domain.AnnotationVisibility
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.convert.UnitSelection

/** How the app decides between a light and a dark palette. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * User-configurable, app-wide settings. A single instance is provided by the app root via
 * [AppSettings] so every tool can read the same preferences.
 */
data class AppSettings(
    val precision: ResultPrecision = ResultPrecision.DEFAULT,
    val enabledUnits: Set<LengthUnit> = setOf(LengthUnit.INCHES, LengthUnit.MILLIMETERS),
    /** The app-wide unit shared by every tool; always one of [enabledUnits]. */
    val activeUnit: LengthUnit = LengthUnit.INCHES,
    val historyEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val keepScreenAwake: Boolean = false,
    /** Diagram annotation layers: Dimensions default ON, Theory (neutral axis, mold lines) OFF. */
    val diagramDimensions: Boolean = true,
    val diagramTheory: Boolean = false,
    /** The last-used unit pair per converter category, keyed by the quantity id. */
    val converterUnits: Map<String, UnitSelection> = emptyMap(),
) {
    /** The global diagram-annotation visibility, shared by every tool's diagram. */
    val diagramVisibility: AnnotationVisibility
        get() = AnnotationVisibility(dimensions = diagramDimensions, theory = diagramTheory)

    /**
     * The first enabled unit — the sensible default when the active selection is unavailable.
     * Falls back to inches so a corrupt/empty stored unit set can never crash startup.
     */
    val primaryUnit: LengthUnit get() = enabledUnits.firstOrNull() ?: LengthUnit.INCHES
}
