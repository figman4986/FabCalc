package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCatalog
import com.figapps.fractioncalculatorv1.domain.convert.ConverterFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Converter value formatting: grouping, scientific fallback, fraction/feet-inches/DMS secondaries. */
class ConverterFormatTest {

    private fun dec(text: String) = Fraction.fromDecimal(text)

    @Test
    fun integerValuesGetThousandsGrouping() {
        assertEquals("1,000", ConverterFormat.format(Fraction.of(1000L, 1L), ResultPrecision.DecimalRound(2)))
        assertEquals(
            "1,234,567.5",
            ConverterFormat.format(dec("1234567.5"), ResultPrecision.DecimalRound(2)),
        )
        assertEquals("-1,000", ConverterFormat.format(Fraction.of(-1000L, 1L), ResultPrecision.DecimalRound(2)))
    }

    @Test
    fun ordinaryValuesStayFixedWithTrailingZerosTrimmed() {
        assertEquals("0.001", ConverterFormat.format(Fraction.of(1L, 1000L), ResultPrecision.FractionRound(32)))
        assertEquals("0.5", ConverterFormat.format(Fraction.of(1L, 2L), ResultPrecision.DecimalRound(4)))
        assertEquals("0", ConverterFormat.format(Fraction.ZERO, ResultPrecision.DecimalRound(4)))
    }

    @Test
    fun veryTinyAndVeryLargeValuesUseScientificNotation() {
        val tiny = ConverterFormat.format(Fraction.of(3L, 1_000_000_000_000L), ResultPrecision.DecimalRound(12))
        assertTrue("expected scientific, got $tiny", tiny.contains("e"))
        assertTrue("observed $tiny", tiny.startsWith("3"))

        val large = ConverterFormat.format(dec("15000000000000000"), ResultPrecision.DecimalRound(2))
        assertTrue("expected scientific, got $large", large.contains("e"))
    }

    @Test
    fun nonZeroValueNeverRendersAsBareZero() {
        // With 0 decimal places a tiny value would otherwise collapse to "0" and read as a real zero.
        val text = ConverterFormat.format(Fraction.of(1L, 1000L), ResultPrecision.DecimalRound(0))
        assertTrue("got $text", text != "0" && text.isNotEmpty())
    }

    @Test
    fun inchFractionSecondaryFollowsPrecisionMode() {
        val inch = ConverterCatalog.category("length")!!.unit("in")!!
        assertEquals(
            "3 7/16",
            ConverterFormat.secondary(dec("3.4375"), inch, ResultPrecision.FractionRound(16)),
        )
        assertNull(ConverterFormat.secondary(dec("3.4375"), inch, ResultPrecision.DecimalRound(3)))
    }

    @Test
    fun feetInchesSplitsCleanly() {
        assertEquals("5 ft 3 in", ConverterFormat.feetInches(dec("5.25"), ResultPrecision.FractionRound(16)))
        assertEquals("0 ft", ConverterFormat.feetInches(Fraction.ZERO, ResultPrecision.FractionRound(16)))
        assertEquals("6 in", ConverterFormat.feetInches(dec("0.5"), ResultPrecision.FractionRound(16)))
    }

    @Test
    fun dmsConvertsDegrees() {
        assertEquals("45\u00B0 30' 0\"", ConverterFormat.dms(dec("45.5")))
        assertEquals("90\u00B0 0' 0\"", ConverterFormat.dms(dec("90")))
    }
}
