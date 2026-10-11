package com.figapps.fractioncalculatorv1.domain

/** The four basic arithmetic operators supported by the calculator. */
enum class Operator(val symbol: String) {
    ADD("+"),
    SUBTRACT("\u2212"),
    MULTIPLY("\u00D7"),
    DIVIDE("\u00F7");

    fun apply(a: Fraction, b: Fraction): Fraction = when (this) {
        ADD -> a + b
        SUBTRACT -> a - b
        MULTIPLY -> a * b
        DIVIDE -> a / b
    }
}
