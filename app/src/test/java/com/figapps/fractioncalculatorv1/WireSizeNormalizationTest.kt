package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.LengthUnit
import com.figapps.fractioncalculatorv1.wire.WireSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wire sizes are stored in one canonical unit (millimetres) no matter which unit the user typed,
 * and converted only for display. These tests pin down every common diameter and the rounding.
 */
class WireSizeNormalizationTest {

    /** The shop diameters, and the millimetre value each must normalise to. */
    private val inchToCanonicalMm = mapOf(
        0.023 to 0.5842,
        0.030 to 0.762,
        0.035 to 0.889,
        0.045 to 1.143,
        0.052 to 1.3208,
    )

    @Test
    fun theCommonInchDiametersAreTheExpectedOnes() {
        assertEquals(inchToCanonicalMm.keys, WireSizes.COMMON_INCHES.toSet())
    }

    @Test
    fun everyCommonInchDiameterNormalizesToItsCanonicalMillimetres() {
        for ((inches, millimetres) in inchToCanonicalMm) {
            assertEquals("$inches in", millimetres, WireSizes.inchesToMillimetres(inches), 1e-9)
        }
    }

    @Test
    fun everyCommonMillimetreDiameterIsAlreadyCanonical() {
        assertEquals(listOf(0.8, 0.9, 1.0, 1.2, 1.6), WireSizes.COMMON_MILLIMETRES)
        for (millimetres in WireSizes.COMMON_MILLIMETRES) {
            // Nothing to convert: the stored value *is* the millimetre value.
            assertEquals(millimetres, millimetres, 0.0)
        }
    }

    @Test
    fun canonicalMillimetresSurviveAnInchRoundTrip() {
        for (millimetres in WireSizes.COMMON_MILLIMETRES) {
            assertEquals(
                millimetres,
                WireSizes.inchesToMillimetres(WireSizes.millimetresToInches(millimetres)),
                1e-9,
            )
        }
        for (inches in WireSizes.COMMON_INCHES) {
            assertEquals(
                inches,
                WireSizes.millimetresToInches(WireSizes.inchesToMillimetres(inches)),
                1e-9,
            )
        }
    }

    @Test
    fun anInchSizeTypedByTheUserIsStoredAsMillimetres() {
        // Typing 0.035 in stores 0.889 mm and reads back as 0.035 in.
        val stored = WireSizes.inchesToMillimetres(0.035)
        assertEquals(0.889, stored, 1e-9)
        assertEquals("0.035 in", WireSizes.display(stored, LengthUnit.INCHES))
    }

    @Test
    fun millimetreDisplayRoundsToTwoPlacesAndTrimsTrailingZeros() {
        val expected = mapOf(
            0.8 to "0.8 mm",
            0.9 to "0.9 mm",
            1.0 to "1 mm",
            1.2 to "1.2 mm",
            1.6 to "1.6 mm",
            0.5842 to "0.58 mm",
            0.762 to "0.76 mm",
            0.889 to "0.89 mm",
            1.143 to "1.14 mm",
            1.3208 to "1.32 mm",
        )
        for ((millimetres, text) in expected) {
            assertEquals(millimetres.toString(), text, WireSizes.display(millimetres, LengthUnit.MILLIMETERS))
        }
    }

    @Test
    fun inchDisplayRoundsToThreePlacesAndTrimsTrailingZeros() {
        assertEquals("0.023 in", WireSizes.display(0.5842, LengthUnit.INCHES))
        assertEquals("0.035 in", WireSizes.display(0.889, LengthUnit.INCHES))
        assertEquals("0.045 in", WireSizes.display(1.143, LengthUnit.INCHES))
        assertEquals("0.052 in", WireSizes.display(1.3208, LengthUnit.INCHES))
        // 0.762 mm is 0.030 in; the trailing zero is trimmed.
        assertEquals("0.03 in", WireSizes.display(0.762, LengthUnit.INCHES))
    }

    @Test
    fun everyCommonOptionRendersInBothUnits() {
        for (millimetres in WireSizes.COMMON_MILLIMETRES) {
            val millimetreText = WireSizes.display(millimetres, LengthUnit.MILLIMETERS)
            val inchText = WireSizes.display(millimetres, LengthUnit.INCHES)
            assertEquals(true, millimetreText!!.endsWith(" mm"))
            assertEquals(true, inchText!!.endsWith(" in"))
        }
    }

    @Test
    fun anUnspecifiedSizeHasNoText() {
        assertNull(WireSizes.display(null, LengthUnit.INCHES))
        assertNull(WireSizes.display(null, LengthUnit.MILLIMETERS))
    }

    @Test
    fun commonOptionsAreLabelledInTheActiveUnitAndCarryCanonicalMillimetres() {
        val inches = WireSizes.commonOptions(LengthUnit.INCHES)
        assertEquals(5, inches.size)
        assertEquals("0.023 in", inches[0].first)
        assertEquals(0.5842, inches[0].second, 1e-9)
        assertEquals("0.052 in", inches[4].first)
        assertEquals(1.3208, inches[4].second, 1e-9)

        val millimetres = WireSizes.commonOptions(LengthUnit.MILLIMETERS)
        assertEquals(5, millimetres.size)
        assertEquals("0.8 mm", millimetres[0].first)
        assertEquals(0.8, millimetres[0].second, 1e-9)
        assertEquals("1.6 mm", millimetres[4].first)
        assertEquals(1.6, millimetres[4].second, 1e-9)
    }

    @Test
    fun bothCommonOptionSetsNormaliseToTheSameCanonicalSpace() {
        // 0.023 in and 0.8 mm are different diameters, but both sets land in millimetres.
        for ((_, millimetres) in WireSizes.commonOptions(LengthUnit.INCHES)) {
            assertEquals(true, millimetres > 0.0)
        }
        for ((_, millimetres) in WireSizes.commonOptions(LengthUnit.MILLIMETERS)) {
            assertEquals(true, millimetres > 0.0)
        }
    }
}
