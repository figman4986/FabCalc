package com.figapps.fractioncalculatorv1.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.cos
import kotlin.math.sin

/** One division mark around the circle. */
data class DivisionPoint(
    val index: Int,                 // 0-based
    val angleFromStart: Double,     // degrees, 0 .. <360
    val cumulativeAngle: Double,    // (start + angleFromStart) wrapped into [0,360)
    val x: Double,                  // offset from centre (radius-based), +x right, +y up
    val y: Double
)

/**
 * Everything the circle-divider tool works out for a circle cut into [divisions] equal parts.
 *
 * Length-type fields are plain [Fraction]s in the user's chosen unit. The slice-angle fields,
 * the chord/apothem/sagitta and the segment sliver come from irrational trig, so they are converted
 * to a 10-significant-digit fraction (see [fractionFromDouble]) and then rounded/formatted by
 * [ResultFormat] exactly like every other length in the app. [circumference], [arc] and
 * [sectorArea] use the exact [Fraction.PI] so they stay exact.
 */
data class CircleDividerResult(
    val divisions: Int,
    val radius: Fraction,
    val diameter: Fraction,
    val circumference: Fraction,
    val centralAngleDegrees: Fraction,   // exactly 360 / divisions
    val chord: Fraction,                 // 2·r·sin(θ/2)
    val arc: Fraction,                   // circumference / divisions
    val apothem: Fraction,               // r·cos(θ/2)  (centre -> chord midpoint)
    val sagitta: Fraction,               // r - apothem
    val sectorArea: Fraction,            // π·r² / divisions
    val segmentArea: Fraction,           // pie-slice minus triangle = the arc/chord sliver area
    val points: List<DivisionPoint>
)

/**
 * Pure, Android-free maths for the circle divider: lay out N equally-spaced marks around a circle
 * and report every dimension a fabricator needs (chord stepping, arc stepping, coordinate layout).
 */
object CircleDividerMath {

    const val MIN_DIVISIONS = 2
    const val MAX_DIVISIONS = 360

    private val TWO = Fraction.of(2L, 1L)

    /** d = value, or d = value / π when [value] is a circumference (reuse [Fraction.PI]). */
    fun toDiameter(value: Fraction, type: MeasurementType): Fraction =
        if (type == MeasurementType.CIRCUMFERENCE) value / Fraction.PI else value

    /**
     * Divides the circle described by [size] (measured either by [type]) into [divisions] equal
     * parts, starting the first mark at [startAngleDegrees].
     *
     * [divisions] is coerced into [MIN_DIVISIONS]..[MAX_DIVISIONS].
     */
    fun compute(
        size: Fraction,
        type: MeasurementType,
        divisions: Int,
        startAngleDegrees: Double = 0.0
    ): CircleDividerResult {
        val n = divisions.coerceIn(MIN_DIVISIONS, MAX_DIVISIONS)
        val nFraction = Fraction.of(n.toLong(), 1L)

        val diameter = toDiameter(size, type)
        val radius = diameter / TWO
        val circumference = diameter * Fraction.PI

        val centralAngle = Fraction.of(360L, n.toLong())
        val thetaDegrees = 360.0 / n
        val thetaRadians = Math.toRadians(thetaDegrees)
        val thetaHalf = thetaRadians / 2.0

        val r = radius.toDoubleValue()

        val chord = fractionFromDouble(2.0 * r * sin(thetaHalf))
        val arc = circumference / nFraction
        val apothem = fractionFromDouble(r * cos(thetaHalf))
        val sagitta = radius - apothem
        val sectorArea = (Fraction.PI * radius * radius) / nFraction
        val triangleArea = fractionFromDouble(r * r * sin(thetaRadians) / 2.0)
        val segmentArea = sectorArea - triangleArea

        val points = (0 until n).map { i ->
            val angleFromStart = i * thetaDegrees
            val absolute = startAngleDegrees + angleFromStart
            val radians = Math.toRadians(absolute)
            val cumulative = ((absolute % 360.0) + 360.0) % 360.0
            DivisionPoint(
                index = i,
                angleFromStart = angleFromStart,
                cumulativeAngle = cumulative,
                x = r * cos(radians),
                y = r * sin(radians)
            )
        }

        return CircleDividerResult(
            divisions = n,
            radius = radius,
            diameter = diameter,
            circumference = circumference,
            centralAngleDegrees = centralAngle,
            chord = chord,
            arc = arc,
            apothem = apothem,
            sagitta = sagitta,
            sectorArea = sectorArea,
            segmentArea = segmentArea,
            points = points
        )
    }
}

/**
 * Trig results are irrational; convert to a [Fraction] (10 significant decimal places by default)
 * so [ResultFormat] can round/format them like any other length. Non-finite input maps to
 * [Fraction.ZERO] rather than throwing.
 */
fun fractionFromDouble(value: Double, scale: Int = 10): Fraction {
    if (!value.isFinite()) return Fraction.ZERO
    val plain = BigDecimal.valueOf(value)
        .setScale(scale, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return Fraction.fromDecimal(plain)
}
