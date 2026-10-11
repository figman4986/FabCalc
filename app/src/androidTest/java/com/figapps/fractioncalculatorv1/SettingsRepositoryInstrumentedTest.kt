package com.figapps.fractioncalculatorv1

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.figapps.fractioncalculatorv1.settings.SettingsDataStoreFactory
import com.figapps.fractioncalculatorv1.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** End-to-end tests over the process-wide serialized Preferences DataStore. */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {

    @Test
    fun unreadablePreferencesFileFallsBackToDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "settings-io-${UUID.randomUUID()}.preferences_pb")
        file.writeBytes(byteArrayOf(0xff.toByte(), 0x7f))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val repository = SettingsRepository(context, SettingsDataStoreFactory { store })

        try {
            assertEquals(AppSettings(), repository.settings.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun serializedSettingsDefaultsRoundTripCoercionAndSequentialWrites() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository(base)
        val store = rawPreferences(repository)
        val settingsFile = File(File(base.filesDir, "datastore"), "settings.preferences_pb")
        val originalPreferences = store.data.first()
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
            store.edit { prefs -> restorePreferences(prefs, originalPreferences) }
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

    private fun restorePreferences(preferences: MutablePreferences, snapshot: Preferences) {
        clearPreferences(preferences)
        snapshot.asMap().forEach { (key, value) -> putPreference(preferences, key, value) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun removePreference(preferences: MutablePreferences, key: Preferences.Key<*>) {
        preferences.remove(key as Preferences.Key<Any>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun putPreference(preferences: MutablePreferences, key: Preferences.Key<*>, value: Any) {
        preferences[key as Preferences.Key<Any>] = value
    }
}
