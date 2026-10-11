package com.figapps.fractioncalculatorv1.domain

/** Which direct measurement the user is typing into the offset tool. */
enum class MeasurementType(val label: String) {
    CIRCUMFERENCE("Circumference"),
    DIAMETER("Diameter")
}

/**
 * Length units supported by the offset tool. The maths is unit-agnostic — the unit only affects
 * the labels shown to the user, so a value in mm behaves exactly like the same value in inches.
 */
enum class LengthUnit(val label: String, val symbol: String) {
    INCHES("Inches", "in"),
    MILLIMETERS("Millimeters", "mm")
}

/**
 * Everything the offset calculator works out, already normalised to diameters.
 *
 * [swapped] is true when the value entered against "large piece (outer)" was actually the smaller
 * of the two measurements — the maths still works, we just flag it so the UI can mention it.
 */
data class OffsetResult(
    val largeDiameter: Fraction,
    val smallDiameter: Fraction,
    val diameterDifference: Fraction,
    val radialOffset: Fraction,
    val swapped: Boolean
) {
    /** True when the two pieces are the same size, i.e. there is no gap to take up. */
    val hasGap: Boolean get() = !radialOffset.isZero
}

/**
 * Pure, Android-free maths for the concentric-offset calculator: two round pieces, one fitted
 * into/onto the other, measured by circumference or diameter.
 *
 * Kept free of any UI framework types so it is fully unit-testable.
 */
object OffsetMath {

    /** π as a high-precision rational, shared with the rest of the app. */
    val PI: Fraction get() = Fraction.PI

    private val TWO = Fraction.of(2L, 1L)

    /** d = C / π */
    fun circumferenceToDiameter(circumference: Fraction): Fraction =
        if (circumference.isZero) Fraction.ZERO else circumference / PI

    /** C = d · π */
    fun diameterToCircumference(diameter: Fraction): Fraction = diameter * PI

    /**
     * Works out the radial gap between the two pieces.
     *
     * @param outer the value typed against "Large piece (outer)".
     * @param inner the value typed against "Small piece (inner)".
     * @param type whether the typed values are circumferences or diameters.
     */
    fun compute(outer: Fraction, inner: Fraction, type: MeasurementType): OffsetResult {
        val outerDiameter = toDiameter(outer, type)
        val innerDiameter = toDiameter(inner, type)

        val swapped = (outerDiameter - innerDiameter).isNegative
        val large = if (swapped) innerDiameter else outerDiameter
        val small = if (swapped) outerDiameter else innerDiameter

        val difference = (large - small).abs()
        val offset = difference / TWO

        return OffsetResult(
            largeDiameter = large,
            smallDiameter = small,
            diameterDifference = difference,
            radialOffset = offset,
            swapped = swapped
        )
    }

    /**
     * Rounds [value] to [precision]. Delegates to [ResultFormat] so the whole app rounds the same
     * way; kept here as a convenience for callers that think in terms of the offset maths.
     */
    fun roundToPrecision(value: Fraction, precision: ResultPrecision): Fraction =
        ResultFormat.round(value, precision)

    private fun toDiameter(value: Fraction, type: MeasurementType): Fraction =
        if (type == MeasurementType.CIRCUMFERENCE) circumferenceToDiameter(value) else value
}

/** Double conversion used by the diagram and by formatting; lives here so `domain` stays pure. */
fun Fraction.toDoubleValue(): Double =
    numerator.toDouble() / denominator.toDouble()
