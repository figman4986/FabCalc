package com.figapps.fractioncalculatorv1.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.Operator
import com.figapps.fractioncalculatorv1.domain.toDecimal

/**
 * Holds all calculator state and translates [CalculatorKey] events into state transitions.
 * The expression is evaluated left-to-right (basic calculator semantics), not with operator
 * precedence.
 */
class CalculatorViewModel : ViewModel() {

    var state by mutableStateOf(CalculatorState())
        private set

    fun onKey(key: CalculatorKey) {
        state = when (key) {
            is CalculatorKey.Digit -> inputDigit(key.value)
            CalculatorKey.DecimalPoint -> inputDecimalPoint()
            CalculatorKey.Backspace -> backspace(state.current.focus)
            is CalculatorKey.BackspaceIn -> backspace(key.field)
            CalculatorKey.Clear -> clear()
            CalculatorKey.ToggleSign -> toggleSign()
            CalculatorKey.Equals -> evaluate()
            is CalculatorKey.ChooseOperator -> chooseOperator(key.op)
            CalculatorKey.FractionBar -> fractionBar()
            is CalculatorKey.InsertConstant -> insertConstant(key.symbol)
            is CalculatorKey.SetFocus -> setFocus(key.field)
            CalculatorKey.WholeMode -> setFocus(Field.WHOLE)
            CalculatorKey.FractionMode -> setFractionFocus()
        }
    }

    // --- input handlers -------------------------------------------------------

    private fun inputDigit(digit: Int): CalculatorState {
        val base = baseState()
        // Typing a digit replaces a constant operand that was just inserted.
        val current = if (base.current.constant != null) {
            FractionInput(focus = base.current.focus)
        } else {
            base.current
        }
        val updated = when (current.focus) {
            Field.WHOLE -> current.copy(whole = appendDigit(current.whole, digit))
            Field.NUMERATOR -> current.copy(numerator = appendDigit(current.numerator, digit))
            Field.DENOMINATOR -> current.copy(denominator = appendDigit(current.denominator, digit))
        }
        return recompute(base.copy(current = updated, isEvaluated = false, error = null))
    }

    private fun inputDecimalPoint(): CalculatorState {
        val base = baseState()
        if (base.current.constant != null) return base
        val current = base.current
        // Only the whole-number field can hold a decimal point.
        if (current.focus != Field.WHOLE) return base
        if (current.whole.contains('.')) return base
        val newWhole = if (current.whole.isEmpty()) "0." else current.whole + "."
        return recompute(base.copy(current = current.copy(whole = newWhole), isEvaluated = false, error = null))
    }

    private fun backspace(field: Field): CalculatorState {
        val base = baseState()
        // Backspacing a constant operand simply removes it.
        if (base.current.constant != null) {
            return recompute(
                base.copy(current = FractionInput(focus = base.current.focus), isEvaluated = false)
            )
        }
        val current = base.current
        val target = when (field) {
            Field.WHOLE -> current.whole
            Field.NUMERATOR -> current.numerator
            Field.DENOMINATOR -> current.denominator
        }

        if (target.isEmpty()) {
            val newFocus = when (field) {
                Field.DENOMINATOR -> Field.NUMERATOR
                Field.NUMERATOR, Field.WHOLE -> Field.WHOLE
            }
            return base.copy(current = current.copy(focus = newFocus))
        }

        val trimmed = target.dropLast(1)
        val updated = when (field) {
            Field.WHOLE -> current.copy(whole = trimmed)
            Field.NUMERATOR -> current.copy(numerator = trimmed)
            Field.DENOMINATOR -> current.copy(denominator = trimmed)
        }
        return recompute(base.copy(current = updated, isEvaluated = false))
    }

    private fun clear(): CalculatorState {
        return if (!state.current.isEmpty) {
            recompute(
                state.copy(
                    current = FractionInput(),
                    result = null,
                    error = null,
                    isEvaluated = false
                )
            )
        } else {
            CalculatorState()
        }
    }

    private fun toggleSign(): CalculatorState {
        val base = baseState()
        if (base.current.constant != null) return base
        val current = base.current
        val updated = when (current.focus) {
            Field.WHOLE -> when {
                current.whole.isNotEmpty() -> current.copy(whole = toggleSignString(current.whole))
                current.numerator.isNotEmpty() ->
                    current.copy(numerator = toggleSignString(current.numerator))
                else -> current.copy(whole = "-")
            }
            Field.NUMERATOR, Field.DENOMINATOR ->
                current.copy(numerator = toggleSignString(current.numerator.ifEmpty { "0" }))
        }
        return recompute(base.copy(current = updated, isEvaluated = false))
    }

    private fun chooseOperator(op: Operator): CalculatorState {
        val s = state

        // Continue from a freshly computed result.
        if (s.isEvaluated) {
            val res = s.result
            return if (res != null) {
                val tokens = listOf(ExprToken.Value(res), ExprToken.OpToken(op))
                recompute(
                    s.copy(
                        tokens = tokens,
                        current = FractionInput(),
                        result = null,
                        isEvaluated = false,
                        error = null
                    )
                )
            } else {
                CalculatorState()
            }
        }

        val current = s.current
        val currentFraction = current.toFraction()
        val tokens = s.tokens.toMutableList()

        if (currentFraction == null) {
            // Replace a trailing operator rather than stacking a second one.
            if (tokens.isNotEmpty() && tokens.last() is ExprToken.OpToken) {
                tokens[tokens.size - 1] = ExprToken.OpToken(op)
                return recompute(s.copy(tokens = tokens.toList(), error = null))
            }
            return s
        }

        tokens.add(ExprToken.Value(currentFraction, current.constant))
        tokens.add(ExprToken.OpToken(op))
        return recompute(
            s.copy(
                tokens = tokens.toList(),
                current = FractionInput(),
                result = null,
                isEvaluated = false,
                error = null
            )
        )
    }

    private fun evaluate(): CalculatorState {
        val s = state
        if (s.error != null) return s
        return try {
            val value = s.evaluateExpression()
            if (value == null) {
                s
            } else {
                val dec = value.toDecimal()
                s.copy(
                    result = value,
                    decimal = dec.text,
                    decimalExact = dec.exact,
                    isEvaluated = true,
                    error = null
                )
            }
        } catch (e: ArithmeticException) {
            s.copy(error = "Cannot divide by zero", result = null, isEvaluated = false)
        }
    }

    private fun insertConstant(symbol: String): CalculatorState {
        val base = baseState()
        val current = FractionInput(focus = base.current.focus, constant = symbol)
        return recompute(base.copy(current = current, isEvaluated = false, error = null))
    }

    private fun fractionBar(): CalculatorState {
        val base = baseState()
        val current = base.current
        val newFocus = when (current.focus) {
            Field.NUMERATOR -> Field.DENOMINATOR
            Field.DENOMINATOR -> Field.NUMERATOR
            Field.WHOLE -> Field.NUMERATOR
        }
        return base.copy(current = current.copy(focus = newFocus), isEvaluated = false)
    }

    private fun setFocus(field: Field): CalculatorState {
        val base = baseState()
        return base.copy(
            current = base.current.copy(focus = field),
            isEvaluated = false,
            error = null
        )
    }

    private fun setFractionFocus(): CalculatorState {
        val base = baseState()
        val current = base.current
        val newFocus = when (current.focus) {
            Field.NUMERATOR, Field.DENOMINATOR -> current.focus
            Field.WHOLE -> Field.NUMERATOR
        }
        return base.copy(
            current = current.copy(focus = newFocus),
            isEvaluated = false,
            error = null
        )
    }

    // --- helpers --------------------------------------------------------------

    /** Returns the base state, discarding a previous result when a new entry begins. */
    private fun baseState(): CalculatorState =
        if (state.isEvaluated) CalculatorState() else state

    private fun appendDigit(existing: String, digit: Int): String {
        if (existing.length >= MAX_DIGITS) return existing
        return when (existing) {
            "0" -> digit.toString()
            "-0" -> "-$digit"
            else -> existing + digit
        }
    }

    private fun toggleSignString(s: String): String =
        if (s.startsWith("-")) s.substring(1) else "-$s"

    /** Refreshes the live preview / decimal value after an edit. */
    private fun recompute(s: CalculatorState): CalculatorState {
        if (s.current.denominator == "0") {
            return s.copy(
                error = "Undefined",
                result = null,
                decimal = null,
                decimalExact = true,
                isEvaluated = false
            )
        }
        val live: Fraction? = s.liveResult
        return if (live != null) {
            val dec = live.toDecimal()
            s.copy(
                result = null,
                decimal = dec.text,
                decimalExact = dec.exact,
                error = null
            )
        } else {
            s.copy(result = null, decimal = null, decimalExact = true, error = null)
        }
    }

    companion object {
        private const val MAX_DIGITS = 9
    }
}
