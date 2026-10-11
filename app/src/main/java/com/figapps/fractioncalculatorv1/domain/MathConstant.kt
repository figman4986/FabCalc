package com.figapps.fractioncalculatorv1.domain

/**
 * Mathematical constants that can be inserted directly as an operand.
 *
 * New constants (e, φ, …) can simply be added here and they become available to the calculator UI.
 */
enum class MathConstant(val symbol: String, val value: Fraction) {
    PI("\u03C0", Fraction.PI);

    companion object {
        fun fromSymbol(symbol: String): MathConstant? = entries.firstOrNull { it.symbol == symbol }
    }
}
