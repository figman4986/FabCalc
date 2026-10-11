package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.convert.SheetMetalGauge
import com.figapps.fractioncalculatorv1.domain.toDoubleValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The gauge lookup tables: coverage, published values, per-material differences, mm derivation. */
class SheetMetalGaugeTest {

    private fun dec(text: String) = Fraction.fromDecimal(text)

    @Test
    fun gaugeNumbersRunSevenToThirtySix() {
        assertEquals((7..36).toList(), SheetMetalGauge.gaugeNumbers)
    }

    @Test
    fun everyMaterialCoversEveryGaugeNumber() {
        for (material in SheetMetalGauge.materials) {
            for (gauge in SheetMetalGauge.gaugeNumbers) {
                assertNotNull("${material.id} gauge $gauge", material.inches[gauge])
            }
        }
    }

    @Test
    fun publishedSteelValues() {
        assertEquals(dec("0.1793"), SheetMetalGauge.steel.inches[7])
        assertEquals(dec("0.1345"), SheetMetalGauge.steel.inches[10])
        assertEquals(dec("0.0598"), SheetMetalGauge.steel.inches[16])
        assertEquals(dec("0.0067"), SheetMetalGauge.steel.inches[36])
    }

    @Test
    fun publishedAluminiumAndGalvanizedValues() {
        assertEquals(dec("0.0508"), SheetMetalGauge.aluminum.inches[16])
        assertEquals(dec("0.1443"), SheetMetalGauge.aluminum.inches[7])
        assertEquals(dec("0.0438"), SheetMetalGauge.galvanized.inches[16])
        assertEquals(dec("0.0070"), SheetMetalGauge.galvanized.inches[36])
    }

    @Test
    fun millimetresAreDerivedFromInchesExactly() {
        assertEquals(
            dec("0.0598") * dec("25.4"),
            SheetMetalGauge.thicknessMm(SheetMetalGauge.steel, 16),
        )
        assertTrue(SheetMetalGauge.thicknessMm(SheetMetalGauge.steel, 16)!!.toDoubleValue() in 1.5..1.53)
    }

    @Test
    fun gaugeStepsAreNotLinear() {
        val inches = SheetMetalGauge.steel.inches
        val deltas = (8..20).map { inches[it - 1]!! - inches[it]!! }
        assertTrue("gauge decrements should vary", deltas.toSet().size > 1)
    }

    @Test
    fun materialsDisagreeAtTheSameGauge() {
        val s = SheetMetalGauge.steel.inches[16]
        val a = SheetMetalGauge.aluminum.inches[16]
        val g = SheetMetalGauge.galvanized.inches[16]
        assertTrue(s != a && a != g && s != g)
    }
}
