package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the pure, Android-free rounding/formatting used app-wide. */
class ResultFormatTest {

    // --- fraction rounding: every denominator ----------------------------------------------------

    @Test
    fun roundsToOneThirdToEveryFractionDenominator() {
        val value = Fraction.of(1L, 3L)
        assertEquals(Fraction.of(1L, 2L), ResultFormat.round(value, ResultPrecision.FractionRound(2)))
        assertEquals(Fraction.of(1L, 4L), ResultFormat.round(value, ResultPrecision.FractionRound(4)))
        assertEquals(Fraction.of(3L, 8L), ResultFormat.round(value, ResultPrecision.FractionRound(8)))
        assertEquals(Fraction.of(5L, 16L), ResultFormat.round(value, ResultPrecision.FractionRound(16)))
        assertEquals(Fraction.of(11L, 32L), ResultFormat.round(value, ResultPrecision.FractionRound(32)))
        assertEquals(Fraction.of(21L, 64L), ResultFormat.round(value, ResultPrecision.FractionRound(64)))
        assertEquals(Fraction.of(43L, 128L), ResultFormat.round(value, ResultPrecision.FractionRound(128)))
        assertEquals(Fraction.of(85L, 256L), ResultFormat.round(value, ResultPrecision.FractionRound(256)))
    }

    @Test
    fun exactMultiplesAreUnchangedByFractionRounding() {
        val eighth = Fraction.of(1L, 8L)
        assertEquals(eighth, ResultFormat.round(eighth, ResultPrecision.FractionRound(8)))
        assertEquals(eighth, ResultFormat.round(eighth, ResultPrecision.FractionRound(16)))
        assertEquals(eighth, ResultFormat.round(eighth, ResultPrecision.FractionRound(256)))
    }

    // --- half-up boundaries ----------------------------------------------------------------------

    @Test
    fun halfUpRoundsTiesAwayFromZeroAtFractionBoundary() {
        assertEquals(Fraction.of(1L, 4L), ResultFormat.round(Fraction.of(1L, 8L), ResultPrecision.FractionRound(4)))
        assertEquals(
            Fraction.of(-1L, 4L),
            ResultFormat.round(Fraction.of(-1L, 8L), ResultPrecision.FractionRound(4))
        )
    }

    @Test
    fun halfUpRoundsTiesAwayFromZeroAtDecimalBoundary() {
        val eighth = Fraction.of(1L, 8L) // 0.125
        val rounded = ResultFormat.round(eighth, ResultPrecision.DecimalRound(2))
        assertEquals(Fraction.of(13L, 100L), rounded)
        assertEquals("0.13", ResultFormat.decimalText(rounded, 2))

        val negative = ResultFormat.round(Fraction.of(-1L, 8L), ResultPrecision.DecimalRound(2))
        assertEquals(Fraction.of(-13L, 100L), negative)
        assertEquals("-0.13", ResultFormat.decimalText(negative, 2))
    }

    // --- rounding to zero ------------------------------------------------------------------------

    @Test
    fun tinyValueRoundsToZeroForFractionPrecision() {
        val tiny = Fraction.of(1L, 1000L)
        val result = ResultFormat.format(tiny, ResultPrecision.FractionRound(16))
        assertTrue(result.rounded.isZero)
        assertNotNull(result.exactNoteText)
        assertEquals("0.001", result.exactNoteText)
        assertEquals("0", result.fractionText)
    }

    @Test
    fun tinyValueRoundsToZeroForDecimalPrecision() {
        val tiny = Fraction.of(1L, 1000L)
        val result = ResultFormat.format(tiny, ResultPrecision.DecimalRound(2))
        assertTrue(result.rounded.isZero)
        assertNotNull(result.exactNoteText)
        assertEquals("0.001", result.exactNoteText)
    }

    @Test
    fun exactNoteIsNullWhenNoRoundingHappens() {
        val sixteenth = Fraction.of(1L, 16L)
        val result = ResultFormat.format(sixteenth, ResultPrecision.FractionRound(16))
        assertEquals(sixteenth, result.rounded)
        assertNull(result.exactNoteText)
    }

    // --- decimal places --------------------------------------------------------------------------

    @Test
    fun decimalPlacesForEveryFractionPrecision() {
        assertEquals(1, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(2)))
        assertEquals(2, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(4)))
        assertEquals(3, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(8)))
        assertEquals(4, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(16)))
        assertEquals(5, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(32)))
        assertEquals(6, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(64)))
        assertEquals(7, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(128)))
        assertEquals(8, ResultFormat.decimalPlaces(ResultPrecision.FractionRound(256)))
    }

    @Test
    fun decimalPlacesForEveryDecimalPrecision() {
        for (places in ResultPrecision.MIN_DECIMAL_PLACES..ResultPrecision.MAX_DECIMAL_PLACES) {
            assertEquals(places, ResultFormat.decimalPlaces(ResultPrecision.DecimalRound(places)))
        }
    }

    // --- decimal rounding: every place 0..12 -----------------------------------------------------

    @Test
    fun roundsToOneThirdAtEveryDecimalPlace() {
        val value = Fraction.of(1L, 3L)
        assertEquals("0", ResultFormat.decimalText(ResultFormat.round(value, ResultPrecision.DecimalRound(0)), 0))
        for (places in 1..ResultPrecision.MAX_DECIMAL_PLACES) {
            val rounded = ResultFormat.round(value, ResultPrecision.DecimalRound(places))
            assertEquals("0." + "3".repeat(places), ResultFormat.decimalText(rounded, places))
        }
    }

    // --- fractionText ----------------------------------------------------------------------------

    @Test
    fun fractionTextFormatsWholeProperMixedAndNegative() {
        assertEquals("0", ResultFormat.fractionText(Fraction.ZERO))
        assertEquals("4", ResultFormat.fractionText(Fraction.of(4L, 1L)))
        assertEquals("3/16", ResultFormat.fractionText(Fraction.of(3L, 16L)))
        assertEquals("3 5/8", ResultFormat.fractionText(Fraction.of(29L, 8L)))
        assertEquals("-1/2", ResultFormat.fractionText(Fraction.of(-1L, 2L)))
        assertEquals("-3 5/8", ResultFormat.fractionText(Fraction.of(-29L, 8L)))
    }

    // --- decimalText trim behaviour --------------------------------------------------------------

    @Test
    fun decimalTextRespectsTrimFlag() {
        assertEquals("0.12500", ResultFormat.decimalText(Fraction.of(1L, 8L), 5))
        assertEquals("0.125", ResultFormat.decimalText(Fraction.of(1L, 8L), 5, trimTrailingZeros = true))
        assertEquals("0.5000", ResultFormat.decimalText(Fraction.of(1L, 2L), 4))
        assertEquals("0.5", ResultFormat.decimalText(Fraction.of(1L, 2L), 4, trimTrailingZeros = true))
        assertEquals("0", ResultFormat.decimalText(Fraction.ZERO, 3, trimTrailingZeros = true))
        assertEquals("4", ResultFormat.decimalText(Fraction.of(4L, 1L), 3, trimTrailingZeros = true))
    }
}
