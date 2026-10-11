package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.CircleDividerMath
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MeasurementType
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.fractionFromDouble
import com.figapps.fractioncalculatorv1.domain.toDoubleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

/** Tests for the pure, Android-free circle-divider maths. */
class CircleDividerMathTest {

    private val diameter8 = Fraction.of(8L, 1L)
    private val spotChecks = listOf(2, 3, 4, 5, 6, 8, 10, 12, 16, 24, 36, 60, 72, 120, 180, 360)

    // --- toDiameter ------------------------------------------------------------------------------

    @Test
    fun toDiameter_diameterMode_isIdentity() {
        assertEquals(diameter8, CircleDividerMath.toDiameter(diameter8, MeasurementType.DIAMETER))
    }

    @Test
    fun toDiameter_circumferenceMode_dividesByPi() {
        val circumference = diameter8 * Fraction.PI
        assertEquals(
            diameter8,
            CircleDividerMath.toDiameter(circumference, MeasurementType.CIRCUMFERENCE)
        )
        assertEquals(Fraction.ZERO, CircleDividerMath.toDiameter(Fraction.ZERO, MeasurementType.CIRCUMFERENCE))
    }

    // --- compute spot checks for every interesting N ---------------------------------------------

    @Test
    fun compute_producesExpectedRelationshipsForEverySpotCheck() {
        for (n in spotChecks) {
            val result = CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, n)

            assertEquals("divisions", n, result.divisions)
            assertEquals("central angle", Fraction.of(360L, n.toLong()), result.centralAngleDegrees)

            assertEquals("diameter", diameter8, result.diameter)
            assertEquals("radius = d/2", result.diameter / Fraction.of(2L, 1L), result.radius)
            assertEquals("circumference = d*pi", result.diameter * Fraction.PI, result.circumference)
            assertEquals(
                "arc = C/N",
                result.circumference / Fraction.of(n.toLong(), 1L),
                result.arc
            )

            assertEquals("point count", n, result.points.size)
            result.points.forEachIndexed { i, p ->
                assertEquals("index", i, p.index)
                assertEquals("angleFromStart", i * 360.0 / n, p.angleFromStart, 1e-9)
            }
        }
    }

    @Test
    fun apothemForFourDivisions_isRadiusTimesCos45() {
        val result = CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, 4)
        val expected = result.radius.toDoubleValue() * cos(PI / 4)
        assertEquals(expected, result.apothem.toDoubleValue(), 1e-6)
    }

    @Test
    fun sagittaIsAlwaysRadiusMinusApothem() {
        for (n in spotChecks) {
            val result = CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, n)
            assertEquals("n=$n", result.radius - result.apothem, result.sagitta)
        }
    }

    // --- the educational claim: chord is shorter than its arc ------------------------------------

    @Test
    fun chordIsAlwaysShorterThanArcForEveryDivision() {
        for (n in 3..360) {
            val result = CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, n)
            assertTrue(
                "chord (${result.chord.toDoubleValue()}) should be < arc (${result.arc.toDoubleValue()}) for n=$n",
                result.chord.toDoubleValue() < result.arc.toDoubleValue()
            )
        }
    }

    // --- start-angle rotation --------------------------------------------------------------------

    @Test
    fun startAngleRotatesCumulativeAnglesButNotAngleFromStart() {
        val result = CircleDividerMath.compute(
            diameter8,
            MeasurementType.DIAMETER,
            divisions = 8,
            startAngleDegrees = 90.0
        )
        result.points.forEachIndexed { i, p ->
            assertEquals("angleFromStart unaffected", i * 45.0, p.angleFromStart, 1e-9)
            val expected = (90.0 + i * 45.0) % 360.0
            assertEquals("cumulativeAngle", expected, p.cumulativeAngle, 1e-9)
            assertTrue("cumulativeAngle in range", p.cumulativeAngle in 0.0..359.9999999)
        }
    }

    @Test
    fun cumulativeAngleWrapsAroundPastThreeSixty() {
        val result = CircleDividerMath.compute(
            diameter8,
            MeasurementType.DIAMETER,
            divisions = 4,
            startAngleDegrees = 270.0
        )
        // last mark: 270 + 270 = 540 -> 180
        assertEquals(270.0, result.points[3].angleFromStart, 1e-9)
        assertEquals(180.0, result.points[3].cumulativeAngle, 1e-9)
        result.points.forEach {
            assertTrue(it.cumulativeAngle >= 0.0 && it.cumulativeAngle < 360.0)
        }
    }

    // --- precision rounding through ResultFormat -------------------------------------------------

    @Test
    fun chordRoundsThroughResultFormat() {
        val result = CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, 8)
        val format = ResultFormat.format(result.chord, ResultPrecision.FractionRound(32))
        assertEquals(
            ResultFormat.round(result.chord, ResultPrecision.FractionRound(32)),
            format.rounded
        )
        assertTrue(format.decimalText.isNotEmpty())
    }

    @Test
    fun tinySagittaRoundsToZeroAndFlagsExactNote() {
        val result = CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, 360)
        val format = ResultFormat.format(result.sagitta, ResultPrecision.FractionRound(16))
        assertTrue("sagitta should round to zero", format.rounded.isZero)
        assertNotNull(format.exactNoteText)
    }

    // --- fractionFromDouble sanity ---------------------------------------------------------------

    @Test
    fun fractionFromDoubleHandlesWholeHalvesAndNonFinite() {
        assertEquals(Fraction.of(2L, 1L), fractionFromDouble(2.0))
        assertEquals(Fraction.of(1L, 2L), fractionFromDouble(0.5))
        assertEquals(Fraction.of(-3L, 4L), fractionFromDouble(-0.75))
        assertTrue(fractionFromDouble(Double.NaN).isZero)
        assertTrue(fractionFromDouble(Double.POSITIVE_INFINITY).isZero)
    }

    // --- coercing divisions ----------------------------------------------------------------------

    @Test
    fun divisionsAreCoercedIntoRange() {
        assertEquals(
            CircleDividerMath.MIN_DIVISIONS,
            CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, 0).divisions
        )
        assertEquals(
            CircleDividerMath.MAX_DIVISIONS,
            CircleDividerMath.compute(diameter8, MeasurementType.DIAMETER, 5000).divisions
        )
    }
}
