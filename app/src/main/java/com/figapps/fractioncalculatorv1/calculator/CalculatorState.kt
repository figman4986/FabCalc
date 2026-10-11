package com.figapps.fractioncalculatorv1.calculator

import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MathConstant
import com.figapps.fractioncalculatorv1.domain.Operator

/**
 * The operand the user is currently typing: an optional whole number and an optional
 * numerator/denominator pair. Values are held as strings so that partial input (e.g. a lone
 * "-" or an empty denominator) can be represented.
 */
data class FractionInput(
    val whole: String = "",
    val numerator: String = "",
    val denominator: String = "",
    val focus: Field = Field.WHOLE,
    /** When set, the operand is a named constant (e.g. "π") instead of a typed fraction. */
    val constant: String? = null
) {
    val isEmpty: Boolean
        get() = constant == null && whole.isEmpty() && numerator.isEmpty() && denominator.isEmpty()

    /** True when the operand is the named constant, e.g. "π". */
    val constantSymbol: String? get() = constant

    /** Human readable form used on the expression line, e.g. "3 1/2" or "1/2". */
    fun displayText(): String {
        constant?.let { return it }
        val parts = mutableListOf<String>()
        if (whole.isNotEmpty()) parts.add(whole)
        if (numerator.isNotEmpty() || denominator.isNotEmpty()) {
            parts.add("${numerator.ifEmpty { "?" }}/${denominator.ifEmpty { "?" }}")
        }
        return parts.joinToString(" ")
    }

    /** Converts the input to a fraction, or null when it is empty/incomplete/invalid. */
    fun toFraction(): Fraction? {
        constant?.let { symbol ->
            return MathConstant.fromSymbol(symbol)?.value
        }
        if (isEmpty) return null

        var wholeFraction: Fraction? = null
        if (whole.isNotEmpty()) {
            wholeFraction = if (whole.contains('.')) {
                try {
                    Fraction.fromDecimal(whole)
                } catch (e: NumberFormatException) {
                    return null
                }
            } else {
                whole.toBigIntegerOrNull()?.let { Fraction.ofWhole(it) } ?: return null
            }
        }

        val hasFraction = numerator.isNotEmpty() || denominator.isNotEmpty()
        if (!hasFraction) return wholeFraction ?: Fraction.ZERO

        val n = (numerator.ifEmpty { "0" }).toBigIntegerOrNull() ?: return null
        val d = (denominator.ifEmpty { "1" }).toBigIntegerOrNull() ?: return null
        if (d.signum() == 0) return null

        if (wholeFraction == null) {
            return Fraction.of(n, d)
        }

        // Combine: sign of the whole carries for the fraction magnitude.
        val magnitude = Fraction.of(n.abs(), d)
        return if (wholeFraction.isNegative) wholeFraction - magnitude else wholeFraction + magnitude
    }
}

/** A committed element of the running expression. */
sealed interface ExprToken {
    /** An operand. [label] is set when it should be shown symbolically (e.g. "π"). */
    data class Value(val fraction: Fraction, val label: String? = null) : ExprToken
    data class OpToken(val op: Operator) : ExprToken
}

/** Full immutable UI state of the calculator. */
data class CalculatorState(
    val tokens: List<ExprToken> = emptyList(),
    val current: FractionInput = FractionInput(),
    val result: Fraction? = null,
    val decimal: String? = null,
    val decimalExact: Boolean = true,
    val error: String? = null,
    val isEvaluated: Boolean = false
) {

    /** Evaluates tokens + current left-to-right, or null when incomplete / on error. */
    val liveResult: Fraction?
        get() {
            if (error != null) return null
            return try {
                evaluateExpression()
            } catch (e: ArithmeticException) {
                null
            }
        }

    /**
     * Evaluates the running expression left-to-right (basic-calculator semantics).
     * Returns null when the expression is incomplete and throws [ArithmeticException]
     * on an invalid operation such as division by zero.
     */
    fun evaluateExpression(): Fraction? {
        val operands = ArrayList<Fraction>(tokens.size + 1)
        val ops = ArrayList<Operator>(tokens.size)
        for (token in tokens) {
            when (token) {
                is ExprToken.Value -> operands.add(token.fraction)
                is ExprToken.OpToken -> ops.add(token.op)
            }
        }
        current.toFraction()?.let { operands.add(it) }

        if (operands.isEmpty()) return null
        // A complete expression has exactly one more operand than operators.
        if (operands.size != ops.size + 1) return null

        var acc = operands[0]
        for (i in ops.indices) {
            acc = ops[i].apply(acc, operands[i + 1])
        }
        return acc
    }
}
