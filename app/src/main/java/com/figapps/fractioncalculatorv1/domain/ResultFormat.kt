package com.figapps.fractioncalculatorv1.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * A value prepared for display: the exact value, the value after rounding to the active
 * [ResultPrecision], and ready-to-render text forms of the rounded value.
 *
 * [exactNoteText] is non-null exactly when rounding actually changed the value — i.e. when
 * [rounded] differs from [exact] — and holds the un-rounded value as a trimmed decimal so the UI
 * can show something like "exact 0.0004".
 */
data class PrecisionResult(
    val exact: Fraction,
    val rounded: Fraction,
    val decimalText: String,
    val fractionText: String,
    val exactNoteText: String?
) {
    /**
     * True when the rounding is significant enough to be worth flagging to the user: the value
     * collapsed to zero, or the rounded value differs from the true value by more than
     * [MISLEADING_RELATIVE_ERROR]. This keeps the "exact" note off results whose rounding barely
     * matters, while still guarding against a fabricator chasing a phantom zero gap.
     */
    val isMisleading: Boolean
        get() {
            if (exact == rounded) return false
            if (rounded.isZero) return true
            if (exact.isZero) return false
            val magnitude = exact.abs().toDoubleValue()
            if (magnitude <= 0.0) return false
            val error = (exact - rounded).abs().toDoubleValue()
            return error / magnitude >= MISLEADING_RELATIVE_ERROR
        }

    companion object {
        /** Relative rounding error at (or above) which the unrounded value is surfaced. */
        const val MISLEADING_RELATIVE_ERROR = 0.05
    }
}

/**
 * The single place where rounding and formatting live. Pure Kotlin/BigDecimal — no Android, no
 * `java.time` — so it is fully unit-testable and shared by every tool.
 */
object ResultFormat {

    /**
     * Rounds [value] according to [precision] using HALF_UP (ties away from zero).
     *
     *  - [ResultPrecision.FractionRound] snaps to the nearest 1/denominator.
     *  - [ResultPrecision.DecimalRound] snaps to the nearest 10^-places.
     */
    fun round(value: Fraction, precision: ResultPrecision): Fraction {
        if (value.isZero) return Fraction.ZERO
        return when (precision) {
            is ResultPrecision.FractionRound -> {
                val denominator = BigInteger.valueOf(precision.denominator.toLong())
                val scaled = BigDecimal(value.numerator)
                    .multiply(BigDecimal(denominator))
                    .divide(BigDecimal(value.denominator), 0, RoundingMode.HALF_UP)
                    .toBigInteger()
                Fraction.of(scaled, denominator)
            }

            is ResultPrecision.DecimalRound -> {
                val scale = BigInteger.TEN.pow(precision.places.coerceAtLeast(0))
                val scaled = BigDecimal(value.numerator)
                    .multiply(BigDecimal(scale))
                    .divide(BigDecimal(value.denominator), 0, RoundingMode.HALF_UP)
                    .toBigInteger()
                Fraction.of(scaled, scale)
            }
        }
    }

    /**
     * Number of significant decimal digits the precision implies:
     *  - [ResultPrecision.FractionRound]`(d)` -> log2(d) so 1/2 -> 1 … 1/256 -> 8.
     *  - [ResultPrecision.DecimalRound]`(p)` -> p.
     */
    fun decimalPlaces(value: ResultPrecision): Int = when (value) {
        is ResultPrecision.FractionRound -> Integer.numberOfTrailingZeros(value.denominator)
        is ResultPrecision.DecimalRound -> value.places
    }

    /**
     * Fixed-point decimal string of [value]. When [trimTrailingZeros] is true the result is
     * stripped of trailing zeros (but keeps at least one digit after the point when fractional).
     */
    fun decimalText(value: Fraction, places: Int, trimTrailingZeros: Boolean = false): String {
        val scale = places.coerceAtLeast(0)
        val decimal = BigDecimal(value.numerator)
            .divide(BigDecimal(value.denominator), scale, RoundingMode.HALF_UP)
            .setScale(scale, RoundingMode.HALF_UP)
        if (!trimTrailingZeros) return decimal.toPlainString()
        if (decimal.signum() == 0) return "0"
        val stripped = decimal.stripTrailingZeros()
        return stripped.toPlainString()
    }

    /** Writes [value] as a mixed number: "4", "3/16", "3 5/8" (sign carried by the whole part). */
    fun fractionText(value: Fraction): String {
        val mixed = value.toMixed()
        return when {
            mixed.numerator.signum() == 0 -> mixed.whole.toString()
            mixed.whole.signum() == 0 -> "${mixed.numerator}/${mixed.denominator}"
            else -> "${mixed.whole} ${mixed.numerator.abs()}/${mixed.denominator}"
        }
    }

    /** Rounds [value] then formats it into a [PrecisionResult]. */
    fun format(value: Fraction, precision: ResultPrecision): PrecisionResult {
        val rounded = round(value, precision)
        val places = decimalPlaces(precision)
        val roundedChanged = rounded != value
        return PrecisionResult(
            exact = value,
            rounded = rounded,
            decimalText = decimalText(rounded, places),
            fractionText = fractionText(rounded),
            exactNoteText = if (roundedChanged) {
                decimalText(value, maxOf(places + 2, 6), trimTrailingZeros = true)
            } else {
                null
            }
        )
    }
}
