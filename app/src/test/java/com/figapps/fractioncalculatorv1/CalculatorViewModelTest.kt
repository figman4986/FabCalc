package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.calculator.CalculatorKey
import com.figapps.fractioncalculatorv1.calculator.ExprToken
import com.figapps.fractioncalculatorv1.calculator.CalculatorViewModel
import com.figapps.fractioncalculatorv1.calculator.Field
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.MathConstant
import com.figapps.fractioncalculatorv1.domain.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ViewModel transition tests run on JVM; no Android UI or lifecycle owner is required. */
class CalculatorViewModelTest {

    @Test
    fun fractionEntryFocusAndPartialFieldsAreRepresented() {
        val viewModel = CalculatorViewModel()
        viewModel.onKey(CalculatorKey.FractionMode)
        assertEquals(Field.NUMERATOR, viewModel.state.current.focus)
        assertTrue(viewModel.state.current.isEmpty)

        viewModel.onKey(CalculatorKey.Digit(3))
        assertEquals("3", viewModel.state.current.numerator)
        viewModel.onKey(CalculatorKey.FractionBar)
        assertEquals(Field.DENOMINATOR, viewModel.state.current.focus)
        assertEquals("3/?", viewModel.state.current.displayText())

        viewModel.onKey(CalculatorKey.Digit(4))
        assertEquals(Fraction.of(3, 4), viewModel.state.current.toFraction())
    }

    @Test
    fun operatorSelectionReplacesTrailingOperator() {
        val viewModel = CalculatorViewModel()
        viewModel.onKey(CalculatorKey.Digit(5))
        viewModel.onKey(CalculatorKey.ChooseOperator(Operator.ADD))
        viewModel.onKey(CalculatorKey.ChooseOperator(Operator.SUBTRACT))

        assertEquals(2, viewModel.state.tokens.size)
        assertEquals(Operator.SUBTRACT, (viewModel.state.tokens.last() as ExprToken.OpToken).op)
    }

    @Test
    fun evaluationIsLeftToRightAndDivisionByZeroIsReported() {
        val viewModel = CalculatorViewModel()
        listOf(
            CalculatorKey.Digit(2),
            CalculatorKey.ChooseOperator(Operator.ADD),
            CalculatorKey.Digit(3),
            CalculatorKey.ChooseOperator(Operator.MULTIPLY),
            CalculatorKey.Digit(4),
            CalculatorKey.Equals,
        ).forEach(viewModel::onKey)
        assertEquals(Fraction.of(20L, 1L), viewModel.state.result)

        viewModel.onKey(CalculatorKey.Clear)
        listOf(
            CalculatorKey.Digit(9),
            CalculatorKey.ChooseOperator(Operator.DIVIDE),
            CalculatorKey.Digit(0),
            CalculatorKey.Equals,
        ).forEach(viewModel::onKey)
        assertEquals("Cannot divide by zero", viewModel.state.error)
        assertNull(viewModel.state.result)
    }

    @Test
    fun clearRemovesCurrentOperandThenWholeExpression() {
        val viewModel = CalculatorViewModel()
        viewModel.onKey(CalculatorKey.Digit(2))
        viewModel.onKey(CalculatorKey.ChooseOperator(Operator.ADD))
        viewModel.onKey(CalculatorKey.Digit(3))

        viewModel.onKey(CalculatorKey.Clear)
        assertEquals(2, viewModel.state.tokens.size)
        assertTrue(viewModel.state.current.isEmpty)

        viewModel.onKey(CalculatorKey.Clear)
        assertTrue(viewModel.state.tokens.isEmpty())
        assertTrue(viewModel.state.current.isEmpty)
        assertNull(viewModel.state.result)
        assertNull(viewModel.state.error)
    }

    @Test
    fun piConstantEvaluatesAsTheConfiguredRationalConstant() {
        val viewModel = CalculatorViewModel()
        viewModel.onKey(CalculatorKey.InsertConstant(MathConstant.PI.symbol))
        assertEquals(MathConstant.PI.symbol, viewModel.state.current.constantSymbol)
        viewModel.onKey(CalculatorKey.Equals)
        assertEquals(MathConstant.PI.value, viewModel.state.result)
    }

    @Test
    fun validDigitsAreLimitedToNineCharactersPerField() {
        val viewModel = CalculatorViewModel()
        (1..10).forEach { viewModel.onKey(CalculatorKey.Digit(it % 10)) }
        assertEquals("123456789", viewModel.state.current.whole)
    }

}
