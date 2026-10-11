package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Bend
import com.figapps.fractioncalculatorv1.domain.BendAllowanceMath
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MeasurementParser
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Closed-form checks for the pure, Android-free bend maths.
 *
 * Angle convention (see [BendAllowanceMath]): A is the bend angle — the angle the material is
 * turned through — NOT the interior/included angle (included = 180° − A). A = 90° puts the flanges
 * perpendicular; A = 180° folds the sheet back on itself (a hem). The two conventions coincide at
 * 90°, so the [angleExtremes] 180° case is what actually pins the convention down.
 *
 * The reference case (bend angle A = 90° — flanges end up perpendicular — K = 0.33, R = 0.125",
 * T = 0.06"), worked by hand:
 *   K·T      = 0.33 × 0.06                 = 0.0198"
 *   R + K·T  = 0.125 + 0.0198              = 0.1448"          (= 181/1250)
 *   A/180    = 90 / 180                    = 1/2
 *   BA       = π × 0.1448 × 0.5            = 0.2274513..."     (= π × 181/2500)
 *   OSSB     = tan(45°) × (0.125 + 0.06)   = 0.185"            (= 37/200)
 *   BD       = 2 × 0.185 − 0.2274513       = 0.1425487..."     (= 37/100 − BA)
 */
class BendAllowanceMathTest {

    private fun dec(text: String) = Fraction.fromDecimal(text)

    private val thickness = dec("0.06")
    private val radius = dec("0.125")
    private val angle90 = Fraction.of(90L, 1L)
    private val k = dec("0.33")

    @Test
    fun referenceBend_closedFormValues() {
        val bend = BendAllowanceMath.computeBend(thickness, radius, angle90, k)

        // K·T = 0.0198" and R + K·T = 0.1448"
        assertEquals(Fraction.of(99L, 5000L), bend.neutralAxisOffset)
        assertEquals(Fraction.of(181L, 1250L), bend.neutralRadius)

        // BA = π·(R + K·T)·(A/180) = π × 181/2500
        assertEquals(Fraction.PI * Fraction.of(181L, 2500L), bend.bendAllowance)
        assertEquals("0.2275", ResultFormat.decimalText(bend.bendAllowance, 4))

        // OSSB = tan(A/2)·(R + T) = tan(45°) × 0.185 = 0.185"
        assertEquals(Fraction.of(37L, 200L), bend.outsideSetback)

        // BD = 2·OSSB − BA = 0.37 − 0.2274513 = 0.1425487"
        assertEquals(Fraction.of(37L, 100L) - bend.bendAllowance, bend.bendDeduction)
        assertEquals("0.1425", ResultFormat.decimalText(bend.bendDeduction!!, 4))
    }

    @Test
    fun bothFlatLengthMethodsAgree() {
        val bend = BendAllowanceMath.computeBend(thickness, radius, angle90, k)
        val cross = BendAllowanceMath.crossCheck(bend, legA = dec("1"), legB = dec("1"))!!
        // leg + leg − BD  ==  (leg − OSSB) + (leg − OSSB) + BA, exactly.
        assertEquals(cross.byDeduction, cross.byAllowance)
        assertTrue(cross.difference.isZero)
        assertTrue(cross.agrees)
        // 2 − 0.1425487 = 1.8574513"
        assertEquals("1.8575", ResultFormat.decimalText(cross.byDeduction, 4))
    }

    @Test
    fun angleExtremes() {
        // Smallest legal bend: A = 1°, nearly flat. Everything is defined and positive.
        val one = BendAllowanceMath.computeBend(thickness, radius, Fraction.of(1L, 1L), k)
        assertNotNull(one.outsideSetback)
        assertTrue(one.bendAllowance.isZero.not())
        // BA = π × (R + K·T) × (1/180) = π × 181/1250 × 1/180 = π × 181/225000
        assertEquals(Fraction.PI * Fraction.of(181L, 225000L), one.bendAllowance)

        // A = 180° is a hem / fold-back: the legs come out parallel, so the setback and the
        // deduction are undefined (tan(A/2) = tan 90°).
        val hem = BendAllowanceMath.computeBend(thickness, radius, Fraction.of(180L, 1L), k)
        assertNull(hem.outsideSetback)
        assertNull(hem.bendDeduction)
        // BA = π·(R + K·T)·(180/180) = π × 181/1250 — a semicircle.
        // This is what proves the convention is the TURN angle: at A = 180° the neutral arc is a
        // semicircle. If A were the included angle, 180° would mean a flat sheet and BA would be 0.
        assertEquals(Fraction.PI * Fraction.of(181L, 1250L), hem.bendAllowance)
    }

    @Test
    fun bendAngleArcSweep_alwaysEqualsTheLabel() {
        // The diagram's angle arc is anchored on the material travel directions (incoming = -dir1,
        // outgoing = dir2, y-up space). The sweep between them must equal the label A at every
        // input, not just at 90° where the turn and included angles coincide.
        for (a in listOf(60, 90, 120, 1, 179)) {
            val g = BendAllowanceMath.bendArcGeometry(Fraction.of(a.toLong(), 1L))
            val dot = g.incomingX * g.outgoingX + g.incomingY * g.outgoingY
            val cross = g.incomingX * g.outgoingY - g.incomingY * g.outgoingX
            val sweep = abs(Math.toDegrees(Math.atan2(cross, dot)))
            assertEquals("A = $a°", a.toDouble(), sweep, 1e-9)
        }
    }

    @Test
    fun kFactorBounds() {
        val kZero = BendAllowanceMath.computeBend(thickness, radius, angle90, Fraction.ZERO)
        assertTrue(kZero.neutralAxisOffset.isZero)
        // BA = π·R·(A/180) = π × 0.125 × 0.5 = π × 1/16
        assertEquals(Fraction.PI * Fraction.of(1L, 16L), kZero.bendAllowance)

        val kHalf = BendAllowanceMath.computeBend(thickness, radius, angle90, Fraction.of(1L, 2L))
        // R + K·T = 0.125 + 0.03 = 0.155 = 31/200; ×1/2 = 31/400
        assertEquals(Fraction.PI * Fraction.of(31L, 400L), kHalf.bendAllowance)
    }

    @Test
    fun fractionThicknessInput() {
        // Thickness typed as "3/16".
        val t = MeasurementParser.parse("3/16")!!
        assertEquals(Fraction.of(3L, 16L), t)
        val bend = BendAllowanceMath.computeBend(t, Fraction.of(1L, 4L), angle90, k)
        // K·T = 33/100 × 3/16 = 99/1600; R + K·T = 400/1600 + 99/1600 = 499/1600; ×1/2 = 499/3200
        assertEquals(Fraction.of(99L, 1600L), bend.neutralAxisOffset)
        assertEquals(Fraction.PI * Fraction.of(499L, 3200L), bend.bendAllowance)
    }

    @Test
    fun millimetreValues_areUnitAgnostic() {
        // The unit is only a label: mm values behave exactly like inches.
        val bend = BendAllowanceMath.computeBend(dec("2"), dec("3"), angle90, k)
        // R + K·T = 3 + 0.66 = 3.66 = 183/50; ×1/2 = 183/100
        assertEquals(Fraction.PI * Fraction.of(183L, 100L), bend.bendAllowance)
        // OSSB = tan(45°) × (3 + 2) = 5
        assertEquals(Fraction.of(5L, 1L), bend.outsideSetback)
    }

    @Test
    fun sharpBend_zeroRadiusIsLegal() {
        val bend = BendAllowanceMath.computeBend(thickness, Fraction.ZERO, angle90, k)
        assertTrue(bend.bendAllowance.isZero.not())
        // R + K·T = K·T = 0.0198
        assertEquals(Fraction.of(99L, 5000L), bend.neutralRadius)
    }

    @Test
    fun chainTotals_addAcrossBends() {
        val chain = BendAllowanceMath.computeChain(
            thickness, k,
            bends = listOf(Bend(angle90, radius), Bend(Fraction.of(45L, 1L), radius)),
            straightSections = dec("2")
        )
        assertEquals(2, chain.bends.size)
        val expectedTotalBa = chain.bends.sumOfFirstClass()
        assertEquals(expectedTotalBa, chain.totalBendAllowance)
        // Flat length = straight sections + Σ BA.
        assertEquals(dec("2") + chain.totalBendAllowance, chain.flatLength)
        assertNotNull(chain.totalBendDeduction)
    }

    @Test
    fun emptyChain_isSafe() {
        val chain = BendAllowanceMath.computeChain(thickness, k, bends = emptyList(), straightSections = dec("5"))
        assertTrue(chain.totalBendAllowance.isZero)
        assertEquals(dec("5"), chain.flatLength)
        assertTrue(chain.bends.isEmpty())
    }

    @Test
    fun validationBounds() {
        assertTrue(BendAllowanceMath.isValidAngle(Fraction.of(1L, 1L)))
        assertTrue(BendAllowanceMath.isValidAngle(Fraction.of(180L, 1L)))
        assertFalse(BendAllowanceMath.isValidAngle(Fraction.ZERO))
        assertFalse(BendAllowanceMath.isValidAngle(Fraction.of(181L, 1L)))
        assertFalse(BendAllowanceMath.isValidAngle(Fraction.of(-90L, 1L)))

        assertTrue(BendAllowanceMath.isValidK(Fraction.ZERO))
        assertTrue(BendAllowanceMath.isValidK(Fraction.of(1L, 2L)))
        assertFalse(BendAllowanceMath.isValidK(Fraction.of(51L, 100L)))
        assertFalse(BendAllowanceMath.isValidK(Fraction.of(-1L, 100L)))
    }

    @Test
    fun kFactorPresets_areLabelledStartingPoints() {
        val byId = BendAllowanceMath.K_FACTOR_PRESETS.associateBy { it.id }
        assertEquals(dec("0.33"), byId.getValue("soft_aluminum").kFactor)
        assertEquals(dec("0.40"), byId.getValue("aluminum_half_hard").kFactor)
        assertEquals(dec("0.33"), byId.getValue("mild_steel").kFactor)
        assertEquals(dec("0.44"), byId.getValue("stainless").kFactor)
        assertEquals(dec("0.45"), byId.getValue("spring_steel").kFactor)
    }

    /** Σ of each bend's own BA, for the chain-total assertion. */
    private fun List<com.figapps.fractioncalculatorv1.domain.BendResult>.sumOfFirstClass(): Fraction =
        fold(Fraction.ZERO) { acc, b -> acc + b.bendAllowance }
}
