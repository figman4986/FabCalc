package com.figapps.fractioncalculatorv1.domain.convert

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.toDoubleValue
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * Formats converted values for the converter.
 *
 * Reuses the app's single rounding path ([ResultFormat]) so precision behaves identically to every
 * other tool, then layers on converter-specific presentation: thousands grouping, scientific
 * notation for extreme magnitudes, and the workshop-style secondary forms (inch fractions,
 * feet + inches, degrees / minutes / seconds).
 */
object ConverterFormat {

    /** The "no entry" mark. A dash reads as "nothing entered", a 0 would read as "you have a zero". */
    const val NO_VALUE = "\u2014"

    /** Below / above these decimal exponents a value is shown in scientific notation. */
    private const val SMALL_EXPONENT = -5
    private const val LARGE_EXPONENT = 15
    private const val SCIENTIFIC_SIGNIFICANT_DIGITS = 6

    private val SECONDS_PER_DEGREE = BigInteger.valueOf(3600)
    private val SECONDS_PER_MINUTE = BigInteger.valueOf(60)
    private val TWELVE = Fraction.of(12L, 1L)
    private val THIRTY_SIX_HUNDRED = Fraction.of(3600L, 1L)

    /**
     * The primary value text: rounded to [precision], grouped, switched to scientific notation for
     * very large/small magnitudes, and never rendered as a misleading "0" for a non-zero value.
     */
    fun format(value: Fraction, precision: ResultPrecision): String {
        if (value.isZero) return "0"
        val rounded = ResultFormat.round(value, precision)
        if (rounded.isZero || useScientific(value)) return scientific(value)
        val fixed = ResultFormat.decimalText(
            value = rounded,
            places = ResultFormat.decimalPlaces(precision),
            trimTrailingZeros = true,
        )
        return group(fixed)
    }

    /**
     * The fraction form of [value] for fraction-friendly units, or null when the active precision is
     * decimal (a fraction of a decimal-precision value would be noise).
     */
    fun fractionSecondary(value: Fraction, precision: ResultPrecision): String? =
        if (precision is ResultPrecision.FractionRound) {
            ResultFormat.format(value, precision).fractionText
        } else {
            null
        }

    /** A length in feet split into feet + inches, e.g. "5 ft 3 in" or "5 ft 3 1/2 in". */
    fun feetInches(feet: Fraction, precision: ResultPrecision): String {
        if (feet.isZero) return "0 ft"
        val negative = feet.isNegative
        val magnitude = feet.abs()
        val wholeFeet = magnitude.toMixed().whole
        val inches = (magnitude - Fraction.ofWhole(wholeFeet)) * TWELVE
        val inchesRounded = ResultFormat.round(inches, precision)
        val sign = if (negative) "-" else ""
        return if (wholeFeet.signum() == 0) {
            "$sign${inchesText(inchesRounded, precision)} in"
        } else if (inchesRounded.isZero) {
            "$sign$wholeFeet ft"
        } else {
            "$sign$wholeFeet ft ${inchesText(inchesRounded, precision)} in"
        }
    }

    /** Degrees / minutes / seconds, e.g. "45° 30' 0\"". Seconds are rounded to whole. */
    fun dms(degrees: Fraction): String {
        val negative = degrees.isNegative
        val magnitude = degrees.abs()
        val totalSeconds = ResultFormat
            .round(magnitude * THIRTY_SIX_HUNDRED, ResultPrecision.DecimalRound(0))
            .numerator
        val d = totalSeconds.divide(SECONDS_PER_DEGREE)
        val remainder = totalSeconds.mod(SECONDS_PER_DEGREE)
        val m = remainder.divide(SECONDS_PER_MINUTE)
        val s = remainder.mod(SECONDS_PER_MINUTE)
        val sign = if (negative && totalSeconds.signum() != 0) "-" else ""
        return "$sign$d\u00B0 $m' $s\""
    }

    /** The secondary (workshop-style) form for [unit], or null when the unit has none. */
    fun secondary(value: Fraction, unit: UnitDef, precision: ResultPrecision): String? =
        when (unit.display) {
            UnitDisplay.PLAIN -> null
            UnitDisplay.INCH_FRACTION -> fractionSecondary(value, precision)
            UnitDisplay.FEET_INCHES -> feetInches(value, precision)
            UnitDisplay.DEGREES_DMS -> dms(value)
        }

    // --- internals ------------------------------------------------------------------------------

    private fun inchesText(inches: Fraction, precision: ResultPrecision): String =
        when (precision) {
            is ResultPrecision.FractionRound -> ResultFormat.fractionText(inches)
            is ResultPrecision.DecimalRound ->
                ResultFormat.decimalText(inches, precision.places, trimTrailingZeros = true)
        }

    private fun useScientific(value: Fraction): Boolean {
        val magnitude = abs(value.toDoubleValue())
        if (magnitude == 0.0 || !magnitude.isFinite()) return false
        val exponent = floor(log10(magnitude)).toInt()
        return exponent <= SMALL_EXPONENT || exponent >= LARGE_EXPONENT
    }

    private fun scientific(value: Fraction): String {
        val context = MathContext(SCIENTIFIC_SIGNIFICANT_DIGITS)
        val decimal = BigDecimal(value.numerator)
            .divide(BigDecimal(value.denominator), context)
            .stripTrailingZeros()
        return decimal.toString().replace('E', 'e')
    }

    private fun group(text: String): String {
        val negative = text.startsWith("-")
        val body = if (negative) text.substring(1) else text
        val dot = body.indexOf('.')
        val integerPart = if (dot < 0) body else body.substring(0, dot)
        val rest = if (dot < 0) "" else body.substring(dot)
        val grouped = integerPart.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + grouped + rest
    }
}
