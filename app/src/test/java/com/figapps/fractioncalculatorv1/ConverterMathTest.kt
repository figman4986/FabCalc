package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCatalog
import com.figapps.fractioncalculatorv1.domain.convert.ConverterCategory
import com.figapps.fractioncalculatorv1.domain.convert.ConverterEngine
import com.figapps.fractioncalculatorv1.domain.convert.UnitDef
import com.figapps.fractioncalculatorv1.domain.toDoubleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conversion maths: exact factors, affine temperature across every pair, and round-trips over the
 * full span of magnitudes. All arithmetic is rational, so round-trips must be exact.
 */
class ConverterMathTest {

    private fun category(id: String): ConverterCategory =
        ConverterCatalog.category(id) ?: error("missing category $id")

    private fun unit(categoryId: String, unitId: String): UnitDef =
        category(categoryId).unit(unitId) ?: error("missing unit $categoryId/$unitId")

    private fun convert(value: Fraction, categoryId: String, fromId: String, toId: String): Fraction =
        ConverterEngine.convert(value, unit(categoryId, fromId), unit(categoryId, toId))

    private fun dec(text: String) = Fraction.fromDecimal(text)

    // --- exact factor checks --------------------------------------------------------------------

    @Test
    fun lengthFactorsAreExact() {
        assertEquals(dec("25.4"), convert(Fraction.ONE, "length", "in", "mm"))
        assertEquals(Fraction.of(12L, 1L), convert(Fraction.ONE, "length", "ft", "in"))
        assertEquals(Fraction.of(3L, 1L), convert(Fraction.ONE, "length", "yd", "ft"))
        assertEquals(Fraction.of(5280L, 1L), convert(Fraction.ONE, "length", "mi", "ft"))
        assertEquals(dec("0.001"), convert(Fraction.ONE, "length", "thou", "in"))
    }

    @Test
    fun massFactorsAreExact() {
        assertEquals(dec("0.45359237"), convert(Fraction.ONE, "mass", "lb", "kg"))
        assertEquals(Fraction.of(16L, 1L), convert(Fraction.ONE, "mass", "lb", "oz"))
        assertEquals(Fraction.of(1000L, 1L), convert(Fraction.ONE, "mass", "kg", "g"))
        assertEquals(Fraction.of(1000L, 1L), convert(Fraction.ONE, "mass", "t", "kg"))
        assertEquals(Fraction.of(2000L, 1L), convert(Fraction.ONE, "mass", "uston", "lb"))
    }

    @Test
    fun pressureForceTorqueFactorsAreExact() {
        assertEquals(Fraction.of(100000L, 1L), convert(Fraction.ONE, "pressure", "bar", "pa"))
        assertEquals(Fraction.of(101325L, 1L), convert(Fraction.ONE, "pressure", "atm", "pa"))
        assertEquals(dec("4.4482216152605"), convert(Fraction.ONE, "force", "lbf", "n"))
        assertEquals(dec("9.80665"), convert(Fraction.ONE, "force", "kgf", "n"))
        assertEquals(
            dec("4.4482216152605") * dec("0.3048"),
            convert(Fraction.ONE, "torque", "lbfft", "nm"),
        )
    }

    @Test
    fun angleFactorsUseTheSharedPi() {
        assertEquals(Fraction.PI, convert(dec("180"), "angle", "deg", "rad"))
        assertEquals(Fraction.PI * dec("2"), convert(dec("360"), "angle", "deg", "rad"))
        assertEquals(Fraction.PI * dec("2"), convert(dec("400"), "angle", "grad", "rad"))
        assertEquals(Fraction.PI * dec("2"), convert(Fraction.ONE, "angle", "rev", "rad"))
    }

    // --- temperature: every pair, both ways ------------------------------------------------------

    @Test
    fun everyTemperaturePairRoundTripsExactly() {
        val ids = category("temperature").units.map { it.id }
        val samples = listOf("0", "100", "-40", "37", "-273.15", "1234.5", "-459.67")
        for (from in ids) {
            for (to in ids) {
                for (sample in samples) {
                    val value = dec(sample)
                    val there = convert(value, "temperature", from, to)
                    val back = convert(there, "temperature", to, from)
                    assertEquals("round-trip $sample $from->$to->$from", value, back)
                }
            }
        }
    }

    @Test
    fun knownTemperatureValues() {
        assertEquals(dec("32"), convert(dec("0"), "temperature", "c", "f"))
        assertEquals(dec("212"), convert(dec("100"), "temperature", "c", "f"))
        assertEquals(dec("-40"), convert(dec("-40"), "temperature", "c", "f"))
        assertEquals(dec("273.15"), convert(dec("0"), "temperature", "c", "k"))
        assertEquals(dec("491.67"), convert(dec("0"), "temperature", "c", "r"))
        assertEquals(dec("-273.15"), convert(Fraction.ZERO, "temperature", "k", "c"))
        assertEquals(dec("-459.67"), convert(Fraction.ZERO, "temperature", "k", "f"))
    }

    @Test
    fun rankineIsNotKelvin() {
        // A classic trap: Rankine uses the Fahrenheit-sized degree, so 1 K != 1 R.
        assertEquals(Fraction.of(9L, 5L), convert(Fraction.ONE, "temperature", "k", "r"))
    }

    // --- round-trips across extreme magnitudes ---------------------------------------------------

    @Test
    fun lengthRoundTripsAcrossHugeAndTinyValues() {
        val pairs = listOf("mm" to "mi", "um" to "mi", "um" to "km", "thou" to "m")
        val samples = listOf("1", "0.000000001", "1609344", "123456789")
        for ((from, to) in pairs) {
            for (sample in samples) {
                val value = dec(sample)
                val back = convert(convert(value, "length", from, to), "length", to, from)
                assertEquals("$sample $from<->$to", value, back)
            }
        }
    }

    @Test
    fun oneMicronInMilesIsTinyButNonZero() {
        val miles = convert(Fraction.ONE, "length", "um", "mi")
        assertTrue(miles.isZero.not())
        // 1e-6 m / 1609.344 m ~= 6.2137e-10
        assertTrue(miles.toDoubleValue() in 6.0e-10..6.5e-10)
    }

    @Test
    fun everyCategoryRoundTripsEveryPairExactly() {
        for (category in ConverterCatalog.categories) {
            val ids = category.units.map { it.id }
            for (from in ids) {
                for (to in ids) {
                    val value = dec("7.25")
                    val back = ConverterEngine.convert(
                        ConverterEngine.convert(value, category.unit(from)!!, category.unit(to)!!),
                        category.unit(to)!!,
                        category.unit(from)!!,
                    )
                    assertEquals("${category.quantity.id} $from<->$to", value, back)
                }
            }
        }
    }

    @Test
    fun catalogDefaultsResolveToRealUnits() {
        for (category in ConverterCatalog.categories) {
            assertNotNull("from default for ${category.quantity.id}", category.unit(category.defaultFromId))
            assertNotNull("to default for ${category.quantity.id}", category.unit(category.defaultToId))
        }
    }
}
