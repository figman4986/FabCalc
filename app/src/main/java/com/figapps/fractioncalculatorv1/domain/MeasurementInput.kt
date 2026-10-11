package com.figapps.fractioncalculatorv1.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses shop-floor style measurements typed as text:
 *  - decimals — "12.566", ".75", "-0.5"
 *  - whole numbers — "4"
 *  - simple fractions — "3/16"
 *  - mixed numbers — "12 3/16" or "12-3/16"
 *
 * Returns null for blank or unrecognisable input and never throws, so the UI can show a friendly
 * inline message instead of crashing.
 */
object MeasurementParser {

    private val spaces = Regex("\\s+")

    /** A '-' that is NOT the leading sign, i.e. the "12-3/16" style separator. */
    private val innerDash = Regex("(?<=.)-")

    fun parse(raw: String): Fraction? {
        val cleaned = raw.trim()
            .replace('\u2044', '/')   // fraction slash
            .replace('\u2215', '/')   // division slash
            .replace(innerDash, " ")
            .replace(spaces, " ")
            .trim()

        if (cleaned.isEmpty()) return null

        return runCatching {
            if (cleaned.contains('/')) parseMixed(cleaned) else Fraction.fromDecimal(cleaned)
        }.getOrNull()
    }

    /**
     * True when [raw] parses to a negative value. Physical measurements (diameters, circumferences,
     * circle and bolt-circle sizes) cannot be negative, so callers use this to show an inline error.
     */
    fun isNegative(raw: String): Boolean = parse(raw)?.isNegative == true

    /**
     * Parses [raw] like [parse], but rejects negative results. Returns null for empty, invalid or
     * negative input so callers can show an inline error instead of computing nonsense geometry.
     * Zero is accepted (e.g. an identical-piece gap of zero).
     */
    fun parsePositive(raw: String): Fraction? = parse(raw)?.takeIf { !it.isNegative }

    private fun parseMixed(text: String): Fraction? {
        val parts = text.split(' ').filter { it.isNotEmpty() }
        return when (parts.size) {
            1 -> parseRatio(parts[0])
            2 -> {
                val whole = parts[0].toBigIntegerOrNull() ?: return null
                val fraction = parseRatio(parts[1]) ?: return null
                val wholeFraction = Fraction.ofWhole(whole)
                if (wholeFraction.isNegative) wholeFraction - fraction.abs()
                else wholeFraction + fraction.abs()
            }
            else -> null
        }
    }

    private fun parseRatio(text: String): Fraction? {
        val slash = text.indexOf('/')
        if (slash < 0) return null
        val numerator = text.substring(0, slash).trim().toBigIntegerOrNull() ?: return null
        val denominator = text.substring(slash + 1).trim().toBigIntegerOrNull() ?: return null
        if (denominator.signum() == 0) return null
        return runCatching { Fraction.of(numerator, denominator) }.getOrNull()
    }
}

/** Formats fractions and decimals for the offset tool's inputs, results and diagram. */
object MeasurementFormat {

    /** Rounds to [precision], then writes the result as a mixed number: "4 3/16", "3/16", "4". */
    fun fraction(value: Fraction, precision: ResultPrecision): String =
        ResultFormat.fractionText(ResultFormat.round(value, precision))

    /** Writes an already-exact fraction as a mixed number: "3 5/8", "1/2", "4". */
    fun mixed(value: Fraction): String {
        val m = value.toMixed()
        return when {
            m.numerator.signum() == 0 -> m.whole.toString()
            m.whole.signum() == 0 -> "${m.numerator}/${m.denominator}"
            else -> "${m.whole} ${m.numerator.abs()}/${m.denominator}"
        }
    }

    /** Fixed-point decimal with exactly [decimals] places, e.g. "4.000". */
    fun decimal(value: Fraction, decimals: Int = 3): String =
        BigDecimal(value.numerator)
            .divide(BigDecimal(value.denominator), decimals, RoundingMode.HALF_UP)
            .setScale(decimals, RoundingMode.HALF_UP)
            .toPlainString()
}
