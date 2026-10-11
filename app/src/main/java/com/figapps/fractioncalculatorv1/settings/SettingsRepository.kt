package com.figapps.fractioncalculatorv1.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCatalog
import com.figapps.fractioncalculatorv1.domain.convert.UnitSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The single, app-wide DataStore used for settings. Declared once at file scope so a second
 * instance is never created (DataStore must be a singleton per file name).
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * Reactive, persisted store for [AppSettings], backed by Jetpack DataStore Preferences.
 *
 * Missing or corrupt keys always fall back to the [AppSettings] defaults, so the app keeps working
 * even on a fresh install or after a partial write.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toAppSettings() }

    suspend fun setPrecision(precision: ResultPrecision) {
        dataStore.edit { prefs ->
            when (precision) {
                is ResultPrecision.FractionRound -> {
                    prefs[Keys.PRECISION_MODE] = MODE_FRACTION
                    prefs[Keys.PRECISION_VALUE] = precision.denominator
                }

                is ResultPrecision.DecimalRound -> {
                    prefs[Keys.PRECISION_MODE] = MODE_DECIMAL
                    prefs[Keys.PRECISION_VALUE] = precision.places
                }
            }
        }
    }

    /**
     * Enables or disables a unit. Disabling the last remaining enabled unit is refused so the
     * unit picker is never left empty — the preferences are left untouched in that case.
     *
     * If the unit just disabled was the persisted active unit, the active selection is moved to
     * the new primary unit so the stored state never points at a disabled unit.
     */
    suspend fun setUnitEnabled(unit: LengthUnit, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_UNITS]?.toUnits()?.takeIf { it.isNotEmpty() } ?: DEFAULT_UNITS
            val next = if (enabled) current + unit else current - unit
            if (next.isEmpty()) return@edit
            prefs[Keys.ENABLED_UNITS] = next.map { it.name }.toSet()

            val active = prefs[Keys.ACTIVE_UNIT]?.let { name ->
                LengthUnit.entries.firstOrNull { it.name == name }
            }
            if (active != null && active !in next) {
                prefs[Keys.ACTIVE_UNIT] = (next.firstOrNull() ?: LengthUnit.INCHES).name
            }
        }
    }

    /**
     * Sets the app-wide active unit shared by every tool. Ignored when [unit] is not currently
     * enabled, so the selection can never point at a hidden unit.
     */
    suspend fun setActiveUnit(unit: LengthUnit) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_UNITS]?.toUnits()?.takeIf { it.isNotEmpty() } ?: DEFAULT_UNITS
            if (unit in current) prefs[Keys.ACTIVE_UNIT] = unit.name
        }
    }

    /**
     * Remembers the unit pair the user last used for a converter category so it can be restored
     * next time. Keyed by the quantity id; unknown categories are ignored by the reader.
     */
    suspend fun setConverterUnits(categoryId: String, fromId: String, toId: String) {
        dataStore.edit { prefs ->
            prefs[converterUnitsKey(categoryId)] = "$fromId|$toId"
        }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.HISTORY_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.KEEP_SCREEN_AWAKE] = enabled }
    }

    /** Shows/hides the Dimensions annotation layer on every diagram. */
    suspend fun setDiagramDimensions(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DIAGRAM_DIMENSIONS] = enabled }
    }

    /** Shows/hides the Theory annotation layer (neutral axis, mold lines) on every diagram. */
    suspend fun setDiagramTheory(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DIAGRAM_THEORY] = enabled }
    }

    private fun converterUnitsKey(categoryId: String) = stringPreferencesKey("converter_units_$categoryId")

    private object Keys {
        val PRECISION_MODE = stringPreferencesKey("precision_mode")
        val PRECISION_VALUE = intPreferencesKey("precision_value")
        val ENABLED_UNITS = stringSetPreferencesKey("enabled_units")
        val ACTIVE_UNIT = stringPreferencesKey("active_unit")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val DIAGRAM_DIMENSIONS = booleanPreferencesKey("diagram_dimensions")
        val DIAGRAM_THEORY = booleanPreferencesKey("diagram_theory")
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        // A stored unit set that parses to nothing (e.g. corrupt/unknown names) falls back to the
        // defaults rather than leaving the app with no selectable unit.
        val enabled = this[Keys.ENABLED_UNITS]?.toUnits()?.takeIf { it.isNotEmpty() }
            ?: defaults.enabledUnits
        val storedActive = this[Keys.ACTIVE_UNIT]?.let { name ->
            LengthUnit.entries.firstOrNull { it.name == name }
        }
        val converterUnits = ConverterCatalog.categories.mapNotNull { category ->
            val raw = this[converterUnitsKey(category.quantity.id)] ?: return@mapNotNull null
            val parts = raw.split('|')
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                category.quantity.id to UnitSelection(parts[0], parts[1])
            } else {
                null
            }
        }.toMap()
        return AppSettings(
            precision = readPrecision(defaults.precision),
            enabledUnits = enabled,
            // Coerce the persisted active unit back into the enabled set; otherwise use primary.
            activeUnit = storedActive?.takeIf { it in enabled } ?: enabled.firstOrNull() ?: LengthUnit.INCHES,
            historyEnabled = this[Keys.HISTORY_ENABLED] ?: defaults.historyEnabled,
            themeMode = this[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: defaults.themeMode,
            hapticsEnabled = this[Keys.HAPTICS_ENABLED] ?: defaults.hapticsEnabled,
            keepScreenAwake = this[Keys.KEEP_SCREEN_AWAKE] ?: defaults.keepScreenAwake,
            diagramDimensions = this[Keys.DIAGRAM_DIMENSIONS] ?: defaults.diagramDimensions,
            diagramTheory = this[Keys.DIAGRAM_THEORY] ?: defaults.diagramTheory,
            converterUnits = converterUnits,
        )
    }

    private fun Preferences.readPrecision(fallback: ResultPrecision): ResultPrecision {
        val mode = this[Keys.PRECISION_MODE] ?: return fallback
        val value = this[Keys.PRECISION_VALUE] ?: return fallback
        return when (mode) {
            MODE_FRACTION ->
                if (value in ResultPrecision.FRACTION_DENOMINATORS) {
                    ResultPrecision.FractionRound(value)
                } else {
                    fallback
                }

            MODE_DECIMAL ->
                if (value in ResultPrecision.MIN_DECIMAL_PLACES..ResultPrecision.MAX_DECIMAL_PLACES) {
                    ResultPrecision.DecimalRound(value)
                } else {
                    fallback
                }

            else -> fallback
        }
    }

    private fun Set<String>.toUnits(): Set<LengthUnit> =
        mapNotNull { name -> LengthUnit.entries.firstOrNull { it.name == name } }.toSet()

    private companion object {
        const val MODE_FRACTION = "fraction"
        const val MODE_DECIMAL = "decimal"
        val DEFAULT_UNITS = setOf(LengthUnit.INCHES, LengthUnit.MILLIMETERS)
    }
}
