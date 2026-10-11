package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MeasurementFormat
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.domain.MeasurementType
import com.figapps.fractioncalculatorv1.domain.OffsetMath
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the pure, Android-free offset maths. */
class OffsetMathTest {

    @Test
    fun diameterMode_standardGap() {
        val result = OffsetMath.compute(
            outer = Fraction.fromDecimal("8"),
            inner = Fraction.fromDecimal("7.91"),
            type = MeasurementType.DIAMETER
        )
        assertEquals(Fraction.of(9L, 100L), result.diameterDifference) // 0.090
        assertEquals(Fraction.of(9L, 200L), result.radialOffset)      // 0.045
        assertFalse(result.swapped)
        assertTrue(result.hasGap)
    }

    @Test
    fun identicalPieces_zeroGap() {
        val result = OffsetMath.compute(Fraction.of(5L, 1L), Fraction.of(5L, 1L), MeasurementType.DIAMETER)
        assertTrue(result.radialOffset.isZero)
        assertFalse(result.hasGap)
        assertEquals(Fraction.ZERO, result.diameterDifference)
    }

    @Test
    fun swappedInputs_usesLargerAsOuter() {
        val result = OffsetMath.compute(Fraction.of(3L, 1L), Fraction.of(5L, 1L), MeasurementType.DIAMETER)
        assertTrue(result.swapped)
        assertEquals(Fraction.of(5L, 1L), result.largeDiameter)
        assertEquals(Fraction.of(3L, 1L), result.smallDiameter)
        assertEquals(Fraction.of(2L, 1L), result.diameterDifference)
        assertEquals(Fraction.of(1L, 1L), result.radialOffset)
    }

    @Test
    fun circumferenceMode_convertsThenGaps() {
        // Build circumferences from the same π the app uses, so the round-trip is exact.
        val largeCircumference = Fraction.of(8L, 1L) * OffsetMath.PI
        val smallCircumference = Fraction.of(7L, 1L) * OffsetMath.PI
        val result = OffsetMath.compute(largeCircumference, smallCircumference, MeasurementType.CIRCUMFERENCE)
        assertEquals(Fraction.of(8L, 1L), result.largeDiameter)
        assertEquals(Fraction.of(7L, 1L), result.smallDiameter)
        assertEquals(Fraction.of(1L, 2L), result.radialOffset)
    }

    @Test
    fun fractionalInputs_parseThenCompute() {
        val large = MeasurementParser.parse("8 3/16")
        val small = MeasurementParser.parse("8")
        assertEquals(Fraction.of(131L, 16L), large)
        val result = OffsetMath.compute(large!!, small!!, MeasurementType.DIAMETER)
        assertEquals(Fraction.of(3L, 16L), result.diameterDifference)
        assertEquals(Fraction.of(3L, 32L), result.radialOffset)
    }

    @Test
    fun millimetreValues_areUnitAgnostic() {
        // The unit is only a label, so mm values behave exactly like inches.
        val result = OffsetMath.compute(
            Fraction.fromDecimal("101.6"),
            Fraction.fromDecimal("101.0"),
            MeasurementType.DIAMETER
        )
        assertEquals(Fraction.of(3L, 5L), result.diameterDifference) // 0.6
        assertEquals(Fraction.of(3L, 10L), result.radialOffset)      // 0.3
    }

    @Test
    fun precisionRounding_roundsToNearest() {
        val offset = Fraction.of(9L, 200L) // 0.045
        assertEquals(Fraction.of(1L, 16L), OffsetMath.roundToPrecision(offset, ResultPrecision.FractionRound(16)))
        assertEquals(Fraction.of(1L, 32L), OffsetMath.roundToPrecision(offset, ResultPrecision.FractionRound(32)))
        assertEquals(Fraction.of(3L, 64L), OffsetMath.roundToPrecision(offset, ResultPrecision.FractionRound(64)))
    }

    @Test
    fun zeroAndEmptyInputs_areSafe() {
        val result = OffsetMath.compute(Fraction.ZERO, Fraction.ZERO, MeasurementType.CIRCUMFERENCE)
        assertTrue(result.radialOffset.isZero)
        assertEquals(Fraction.ZERO, OffsetMath.circumferenceToDiameter(Fraction.ZERO))
    }

    @Test
    fun parser_acceptsEveryMeasurementShape() {
        assertEquals(Fraction.of(25L, 2L), MeasurementParser.parse("12.5"))
        assertEquals(Fraction.of(3L, 16L), MeasurementParser.parse("3/16"))
        assertEquals(Fraction.of(195L, 16L), MeasurementParser.parse("12 3/16"))
        assertEquals(Fraction.of(195L, 16L), MeasurementParser.parse("12-3/16"))
        assertEquals(Fraction.of(-5L, 2L), MeasurementParser.parse("-2 1/2"))
        assertEquals(Fraction.of(4L, 1L), MeasurementParser.parse("4"))
        assertNull(MeasurementParser.parse(""))
        assertNull(MeasurementParser.parse("   "))
        assertNull(MeasurementParser.parse("abc"))
        assertNull(MeasurementParser.parse("1/0")) // zero denominator, not a crash
    }

    @Test
    fun formatter_writesMixedNumbersAndDecimals() {
        assertEquals("4", MeasurementFormat.mixed(Fraction.of(4L, 1L)))
        assertEquals("3/16", MeasurementFormat.mixed(Fraction.of(3L, 16L)))
        assertEquals("3 5/8", MeasurementFormat.mixed(Fraction.of(29L, 8L)))
        assertEquals("0.045", MeasurementFormat.decimal(Fraction.of(9L, 200L)))
        assertEquals("1/32", MeasurementFormat.fraction(Fraction.of(9L, 200L), ResultPrecision.FractionRound(32)))
    }
}
