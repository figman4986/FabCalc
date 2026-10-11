package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the shared-measurement and settings fallback behaviour added on top of the tools:
 * negative measurements are rejected, and settings never crash on empty unit sets.
 */
class SettingsAndMeasurementTest {

    // --- negative measurement rejection ----------------------------------------------------------

    @Test
    fun parsePositive_rejectsNegativeValues() {
        assertNull(MeasurementParser.parsePositive("-5"))
        assertNull(MeasurementParser.parsePositive("-2 1/2"))
        assertNull(MeasurementParser.parsePositive("-0.001"))
        assertNull(MeasurementParser.parsePositive("-3/16"))
    }

    @Test
    fun parsePositive_acceptsZeroAndPositiveValues() {
        assertEquals(Fraction.of(5L, 1L), MeasurementParser.parsePositive("5"))
        assertEquals(Fraction.ZERO, MeasurementParser.parsePositive("0"))
        assertEquals(Fraction.of(3L, 16L), MeasurementParser.parsePositive("3/16"))
        assertEquals(Fraction.of(131L, 16L), MeasurementParser.parsePositive("8 3/16"))
    }

    @Test
    fun parsePositive_stillReturnsNullForEmptyAndInvalid() {
        assertNull(MeasurementParser.parsePositive(""))
        assertNull(MeasurementParser.parsePositive("   "))
        assertNull(MeasurementParser.parsePositive("abc"))
        assertNull(MeasurementParser.parsePositive("1/0"))
    }

    @Test
    fun isNegative_detectsOnlyNegativeMeasurements() {
        assertTrue(MeasurementParser.isNegative("-1"))
        assertTrue(MeasurementParser.isNegative("-0.5"))
        assertFalse(MeasurementParser.isNegative("1"))
        assertFalse(MeasurementParser.isNegative("0"))
        assertFalse(MeasurementParser.isNegative(""))
        assertFalse(MeasurementParser.isNegative("abc"))
    }

    // --- primaryUnit empty-set fallback ----------------------------------------------------------

    @Test
    fun primaryUnit_fallsBackToInchesWhenUnitsEmpty() {
        assertEquals(LengthUnit.INCHES, AppSettings(enabledUnits = emptySet()).primaryUnit)
    }

    @Test
    fun primaryUnit_usesSoleEnabledUnit() {
        assertEquals(
            LengthUnit.MILLIMETERS,
            AppSettings(enabledUnits = setOf(LengthUnit.MILLIMETERS)).primaryUnit
        )
    }

    @Test
    fun defaultActiveUnitIsEnabled() {
        val settings = AppSettings()
        assertTrue(settings.activeUnit in settings.enabledUnits)
    }
}
