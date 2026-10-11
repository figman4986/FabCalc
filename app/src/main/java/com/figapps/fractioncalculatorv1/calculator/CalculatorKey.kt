package com.figapps.fractioncalculatorv1.calculator

import com.figapps.fractioncalculatorv1.domain.Operator

/** Which of the three editable fields of the current operand has focus. */
enum class Field { WHOLE, NUMERATOR, DENOMINATOR }

/** A single user input event originating from the keypad. */
sealed interface CalculatorKey {
    data class Digit(val value: Int) : CalculatorKey
    data object DecimalPoint : CalculatorKey
    data object Backspace : CalculatorKey
    data class BackspaceIn(val field: Field) : CalculatorKey
    data object Clear : CalculatorKey
    data object ToggleSign : CalculatorKey
    data object Equals : CalculatorKey
    data class ChooseOperator(val op: Operator) : CalculatorKey

    /** Moves the input focus to the numerator or denominator (the fraction fields). */
    data object FractionBar : CalculatorKey

    /** Inserts a mathematical constant (π, …) as the current operand. */
    data class InsertConstant(val symbol: String) : CalculatorKey

    /** Explicitly focuses one of the editable fields (used by the input slots). */
    data class SetFocus(val field: Field) : CalculatorKey

    data object WholeMode : CalculatorKey
    data object FractionMode : CalculatorKey
}
