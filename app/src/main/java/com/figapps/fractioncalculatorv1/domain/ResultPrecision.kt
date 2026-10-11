package com.figapps.fractioncalculatorv1.domain

/**
 * How a result should be rounded before it is shown to the user. This is a global, app-wide
 * preference (see `settings`), so every tool shares the same notion of "precision".
 *
 *  - [FractionRound] rounds to the nearest common fabrication fraction (1/2 … 1/256).
 *  - [DecimalRound] rounds to the nearest decimal place (0 … 12).
 */
sealed interface ResultPrecision {

    /** Rounds to the nearest 1/[denominator]; [denominator] is one of [FRACTION_DENOMINATORS]. */
    data class FractionRound(val denominator: Int) : ResultPrecision

    /** Rounds to the nearest 10^- [places]. */
    data class DecimalRound(val places: Int) : ResultPrecision

    companion object {
        val FRACTION_DENOMINATORS = listOf(2, 4, 8, 16, 32, 64, 128, 256)
        const val MIN_DECIMAL_PLACES = 0
        const val MAX_DECIMAL_PLACES = 12

        val DEFAULT: ResultPrecision = FractionRound(32)

        /** "1/32", used as a chip label. */
        fun denominatorLabel(d: Int): String = "1/$d"
    }
}
