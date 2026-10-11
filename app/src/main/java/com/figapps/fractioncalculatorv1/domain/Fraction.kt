package com.figapps.fractioncalculatorv1.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Immutable, always-normalized rational number.
 *
 * Invariants:
 *  - [denominator] is strictly positive (sign lives on the numerator).
 *  - numerator/denominator are reduced by their greatest common divisor.
 */
class Fraction private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger
) {

    val isZero: Boolean get() = numerator.signum() == 0
    val isNegative: Boolean get() = numerator.signum() < 0

    fun abs(): Fraction = if (isNegative) negate() else this

    fun negate(): Fraction = Fraction(numerator.negate(), denominator)

    fun reciprocal(): Fraction {
        if (isZero) throw ArithmeticException("Cannot take reciprocal of zero")
        return of(denominator, numerator)
    }

    operator fun plus(other: Fraction): Fraction =
        of(
            numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
            denominator.multiply(other.denominator)
        )

    operator fun minus(other: Fraction): Fraction =
        of(
            numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
            denominator.multiply(other.denominator)
        )

    operator fun times(other: Fraction): Fraction =
        of(
            numerator.multiply(other.numerator),
            denominator.multiply(other.denominator)
        )

    operator fun div(other: Fraction): Fraction {
        if (other.isZero) throw ArithmeticException("Cannot divide by zero")
        return this * other.reciprocal()
    }

    /** Decomposes this fraction into a mixed number. */
    fun toMixed(): MixedNumber {
        if (denominator == BigInteger.ONE) {
            return MixedNumber(numerator, BigInteger.ZERO, BigInteger.ONE)
        }
        val absN = numerator.abs()
        val whole = absN.divide(denominator)
        val remainder = absN.mod(denominator)
        val negative = isNegative

        if (remainder.signum() == 0) {
            val signedWhole = if (negative) whole.negate() else whole
            return MixedNumber(signedWhole, BigInteger.ZERO, BigInteger.ONE)
        }

        return if (whole.signum() != 0) {
            // Sign is carried by the whole part.
            val signedWhole = if (negative) whole.negate() else whole
            MixedNumber(signedWhole, remainder, denominator)
        } else {
            // No whole part: the sign lives on the numerator.
            val signedRemainder = if (negative) remainder.negate() else remainder
            MixedNumber(BigInteger.ZERO, signedRemainder, denominator)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fraction) return false
        return numerator == other.numerator && denominator == other.denominator
    }

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String =
        if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"

    companion object {
        val ZERO: Fraction = Fraction(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Fraction = Fraction(BigInteger.ONE, BigInteger.ONE)

        /**
         * High-precision rational approximation of π (the same value as an IEEE-754 double),
         * used when the π constant is inserted as an operand.
         */
        val PI: Fraction = fromDecimal("3.141592653589793")

        /** Reduces by GCD and normalizes the sign so the denominator is positive. */
        fun of(numerator: BigInteger, denominator: BigInteger): Fraction {
            if (denominator.signum() == 0) throw ArithmeticException("Denominator is zero")
            var n = numerator
            var d = denominator
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            val g = n.gcd(d)
            val divisor = if (g.signum() == 0) BigInteger.ONE else g
            return Fraction(n.divide(divisor), d.divide(divisor))
        }

        fun of(numerator: Long, denominator: Long): Fraction =
            of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))

        fun ofWhole(whole: BigInteger): Fraction = Fraction(whole, BigInteger.ONE)

        /** Parses a decimal string such as "3.625", "-0.5", "2" or ".75" into an exact fraction. */
        fun fromDecimal(s: String): Fraction {
            var str = s.trim()
            if (str.isEmpty()) throw NumberFormatException("Empty decimal string")
            var negative = false
            when {
                str.startsWith("-") -> { negative = true; str = str.substring(1) }
                str.startsWith("+") -> str = str.substring(1)
            }
            val dot = str.indexOf('.')
            val intPart: String
            val fracPart: String
            if (dot < 0) {
                intPart = str.ifEmpty { "0" }
                fracPart = ""
            } else {
                intPart = str.substring(0, dot).ifEmpty { "0" }
                fracPart = str.substring(dot + 1)
            }
            val digits = intPart + fracPart
            val digitsValue = if (digits.isEmpty()) BigInteger.ZERO else BigInteger(digits)
            val scale = BigInteger.TEN.pow(fracPart.length)
            val numerator = if (negative) digitsValue.negate() else digitsValue
            return of(numerator, scale)
        }
    }
}

/** A mixed number: [whole] plus [numerator]/[denominator]. */
data class MixedNumber(
    val whole: BigInteger,
    val numerator: BigInteger,
    val denominator: BigInteger
) {
    val isWhole: Boolean get() = numerator.signum() == 0
    val isNegative: Boolean get() = whole.signum() < 0 || numerator.signum() < 0
}

/** Result of converting a fraction to a decimal string. */
data class DecimalResult(val text: String, val exact: Boolean)

/**
 * Converts this fraction to a decimal string with at most [maxDecimals] digits.
 * [DecimalResult.exact] is true when the decimal terminates within [maxDecimals].
 */
fun Fraction.toDecimal(maxDecimals: Int = 8): DecimalResult {
    val denominator = this.denominator
    val tenPow = BigInteger.TEN.pow(maxDecimals)
    val exact = numerator.multiply(tenPow).mod(denominator).signum() == 0
    val decimal = BigDecimal(numerator)
        .divide(BigDecimal(denominator), maxDecimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return DecimalResult(decimal.toPlainString(), exact)
}
