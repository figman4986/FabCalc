package com.figapps.fractioncalculatorv1

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCatalog
import com.figapps.fractioncalculatorv1.domain.convert.UnitSelection
import com.figapps.fractioncalculatorv1.settings.AppSettings
import com.figapps.fractioncalculatorv1.settings.SettingsRepository
import com.figapps.fractioncalculatorv1.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** End-to-end tests over the process-wide serialized Preferences DataStore. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {

    @Test
    fun serializedSettingsDefaultsRoundTripCoercionAndSequentialWrites() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository(base)
        val store = rawPreferences(repository)
        val settingsFile = File(File(base.filesDir, "datastore"), "settings.preferences_pb")
        try {
            store.edit { prefs -> clearPreferences(prefs) }
            assertEquals(AppSettings(), repository.settings.first())

            repository.setPrecision(ResultPrecision.DecimalRound(4))
            repository.setActiveUnit(LengthUnit.INCHES)
            repository.setUnitEnabled(LengthUnit.INCHES, false)
            repository.setActiveUnit(LengthUnit.MILLIMETERS)
            repository.setHistoryEnabled(false)
            repository.setThemeMode(ThemeMode.DARK)
            repository.setHapticsEnabled(false)
            repository.setKeepScreenAwake(true)
            repository.setDiagramDimensions(false)
            repository.setDiagramTheory(true)

            val converterSelections = ConverterCatalog.categories.associate { category ->
                category.quantity.id to UnitSelection(category.defaultFromId, category.defaultToId)
            }
            converterSelections.forEach { (categoryId, selection) ->
                repository.setConverterUnits(categoryId, selection.fromId, selection.toId)
            }

            // Recreate the repository facade and collect from the serialized DataStore again.
            val recreated = SettingsRepository(base)
            val expected = AppSettings(
                precision = ResultPrecision.DecimalRound(4),
                enabledUnits = setOf(LengthUnit.MILLIMETERS),
                activeUnit = LengthUnit.MILLIMETERS,
                historyEnabled = false,
                themeMode = ThemeMode.DARK,
                hapticsEnabled = false,
                keepScreenAwake = true,
                diagramDimensions = false,
                diagramTheory = true,
                converterUnits = converterSelections,
            )
            assertEquals(expected, recreated.settings.first())
            assertTrue("settings.preferences_pb is materialized", settingsFile.isFile)

            // A setter may not remove the final enabled unit; active selection stays enabled.
            recreated.setUnitEnabled(LengthUnit.MILLIMETERS, false)
            assertEquals(setOf(LengthUnit.MILLIMETERS), recreated.settings.first().enabledUnits)
            assertEquals(LengthUnit.MILLIMETERS, recreated.settings.first().activeUnit)

            // Unknown enum strings and empty/unknown unit sets coerce safely when read back.
            rawPreferences(recreated).edit { prefs ->
                prefs[stringPreferencesKey("theme_mode")] = "FUTURE_THEME"
                prefs[stringPreferencesKey("active_unit")] = "FUTURE_UNIT"
                prefs[stringPreferencesKey("precision_mode")] = "FUTURE_PRECISION"
                prefs[stringSetPreferencesKey("enabled_units")] = emptySet()
            }
            val emptyUnits = recreated.settings.first()
            assertEquals(setOf(LengthUnit.INCHES, LengthUnit.MILLIMETERS), emptyUnits.enabledUnits)
            assertEquals(LengthUnit.INCHES, emptyUnits.activeUnit)
            assertEquals(ThemeMode.SYSTEM, emptyUnits.themeMode)
            assertEquals(ResultPrecision.DEFAULT, emptyUnits.precision)

            rawPreferences(recreated).edit { prefs ->
                prefs[stringPreferencesKey("theme_mode")] = "FUTURE_THEME"
                prefs[stringPreferencesKey("active_unit")] = "FUTURE_UNIT"
                prefs[stringSetPreferencesKey("enabled_units")] = setOf("FUTURE_UNIT")
            }
            val unknownUnits = recreated.settings.first()
            assertEquals(setOf(LengthUnit.INCHES, LengthUnit.MILLIMETERS), unknownUnits.enabledUnits)
            assertEquals(LengthUnit.INCHES, unknownUnits.activeUnit)
            assertEquals(ThemeMode.SYSTEM, unknownUnits.themeMode)

            // Sequential awaited DataStore edits must persist their final state in order.
            repeat(25) { index -> recreated.setHistoryEnabled(index % 2 == 0) }
            recreated.setHistoryEnabled(false)
            recreated.setPrecision(ResultPrecision.DecimalRound(7))
            val afterWrites = SettingsRepository(base).settings.first()
            assertFalse(afterWrites.historyEnabled)
            assertEquals(ResultPrecision.DecimalRound(7), afterWrites.precision)
            assertTrue(settingsFile.length() > 0L)
        } finally {
            // The existing app store was at defaults before these tests; restore that state.
            store.edit { prefs -> clearPreferences(prefs) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rawPreferences(repository: SettingsRepository): DataStore<Preferences> {
        val field = SettingsRepository::class.java.getDeclaredField("dataStore").apply { isAccessible = true }
        return field.get(repository) as DataStore<Preferences>
    }

    private fun clearPreferences(preferences: MutablePreferences) {
        preferences.asMap().keys.toList().forEach { key -> removePreference(preferences, key) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun removePreference(preferences: MutablePreferences, key: Preferences.Key<*>) {
        preferences.remove(key as Preferences.Key<Any>)
    }
}
