package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.Operator
import com.figapps.fractioncalculatorv1.domain.toDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class FractionTest {

    @Test
    fun reducesToLowestTerms() {
        assertEquals("1/2", Fraction.of(2L, 4L).toString())
        assertEquals("2/3", Fraction.of(10L, 15L).toString())
    }

    @Test
    fun normalizesSignToNumerator() {
        val f = Fraction.of(1L, -2L)
        assertEquals(BigInteger.valueOf(-1), f.numerator)
        assertEquals(BigInteger.TWO, f.denominator)
        assertTrue(f.isNegative)
        assertEquals("-1/2", f.toString())
        assertEquals("1/2", Fraction.of(-1L, -2L).toString())
    }

    @Test
    fun additionAndSubtraction() {
        assertEquals(Fraction.of(3L, 4L), Fraction.of(1L, 4L) + Fraction.of(1L, 2L))
        assertEquals(Fraction.of(1L, 4L), Fraction.of(3L, 4L) - Fraction.of(1L, 2L))
        // Mixed number addition: 3 1/2 + 1/8 = 29/8
        assertEquals(Fraction.of(29L, 8L), Fraction.of(7L, 2L) + Fraction.of(1L, 8L))
    }

    @Test
    fun multiplicationAndDivision() {
        assertEquals(Fraction.of(1L, 6L), Fraction.of(1L, 2L) * Fraction.of(1L, 3L))
        assertEquals(Fraction.of(3L, 2L), Fraction.of(1L, 2L) / Fraction.of(1L, 3L))
        assertEquals(Fraction.of(-3L, 2L), Fraction.of(1L, 2L) / Fraction.of(-1L, 3L))
    }

    @Test
    fun parsesDecimalsExactly() {
        assertEquals(Fraction.of(29L, 8L), Fraction.fromDecimal("3.625"))
        assertEquals(Fraction.of(1L, 2L), Fraction.fromDecimal("0.5"))
        assertEquals(Fraction.of(-9L, 4L), Fraction.fromDecimal("-2.25"))
        assertEquals(Fraction.ofWhole(BigInteger.valueOf(2)), Fraction.fromDecimal("2"))
        assertEquals(Fraction.of(3L, 4L), Fraction.fromDecimal(".75"))
    }

    @Test
    fun convertsToDecimalExactAndRepeating() {
        val eighth = Fraction.of(1L, 8L).toDecimal()
        assertEquals("0.125", eighth.text)
        assertTrue(eighth.exact)

        val third = Fraction.of(1L, 3L).toDecimal()
        assertFalse(third.exact)
        assertEquals("0.33333333", third.text)
    }

    @Test
    fun toMixedForImproperFraction() {
        val mixed = Fraction.of(29L, 8L).toMixed()
        assertEquals(BigInteger.valueOf(3), mixed.whole)
        assertEquals(BigInteger.valueOf(5), mixed.numerator)
        assertEquals(BigInteger.valueOf(8), mixed.denominator)

        val wholeNumber = Fraction.of(8L, 2L).toMixed()
        assertTrue(wholeNumber.isWhole)
        assertEquals(BigInteger.valueOf(4), wholeNumber.whole)
    }

    @Test
    fun divideByZeroThrows() {
        assertThrows(ArithmeticException::class.java) {
            Fraction.of(1L, 2L) / Fraction.ZERO
        }
        assertThrows(ArithmeticException::class.java) {
            Fraction.of(1L, 0L)
        }
        assertThrows(ArithmeticException::class.java) {
            Fraction.ZERO.reciprocal()
        }
    }

    @Test
    fun operatorApplies() {
        assertEquals(Fraction.of(5L, 6L), Operator.ADD.apply(Fraction.of(1L, 2L), Fraction.of(1L, 3L)))
        assertEquals(Fraction.of(1L, 6L), Operator.SUBTRACT.apply(Fraction.of(1L, 2L), Fraction.of(1L, 3L)))
        assertEquals(Fraction.of(1L, 6L), Operator.MULTIPLY.apply(Fraction.of(1L, 2L), Fraction.of(1L, 3L)))
        assertEquals(Fraction.of(3L, 2L), Operator.DIVIDE.apply(Fraction.of(1L, 2L), Fraction.of(1L, 3L)))
    }

    @Test
    fun piConstantIsAccurateAndUsable() {
        val pi = Fraction.PI
        assertFalse("π should exceed 3", (pi - Fraction.ofWhole(BigInteger.valueOf(3))).isNegative)
        assertTrue("π should be below 22/7", (pi - Fraction.of(22L, 7L)).isNegative)
        // π + π = 2π, and 2π / π = 2 exactly.
        assertEquals(Fraction.ofWhole(BigInteger.TWO), (pi + pi) / pi)
    }
}
