package com.figapps.fractioncalculatorv1.domain

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * One bend in a chain: the angle it turns through and the inside radius it turns around.
 * The material thickness and K-factor are shared by every bend in the chain.
 */
data class Bend(
    /** Bend angle A: the angle the material is turned through (included angle = 180° − A). */
    val angleDegrees: Fraction,
    val insideRadius: Fraction,
)

/** A common shop K-factor, offered as a starting point. Values are empirical, not physics. */
data class KFactorPreset(
    val id: String,
    val label: String,
    val kFactor: Fraction,
)

/**
 * Geometry of a bend arc for a given bend angle A, in a y-up coordinate space (x right, y up).
 *
 * [incomingX]/[incomingY] is the unit direction the material is heading as it enters the bend;
 * [outgoingX]/[outgoingY] is the direction it leaves. The turn angle between them is exactly A —
 * which is what the diagram's angle arc sweeps, so the drawn sweep and the label cannot disagree.
 */
data class BendArcGeometry(
    val startRadians: Double,
    val endRadians: Double,
    val incomingX: Double,
    val incomingY: Double,
    val outgoingX: Double,
    val outgoingY: Double,
)

/**
 * Everything the bend tool works out for a single bend.
 *
 * [outsideSetback] and [bendDeduction] are null for a full 180° bend: the two legs come out
 * parallel, so tan(A/2) = tan(90°) is undefined and neither setback nor deduction exists.
 */
data class BendResult(
    /** Bend angle A: the angle the material is turned through (included angle = 180° − A). */
    val angleDegrees: Fraction,
    val insideRadius: Fraction,
    val thickness: Fraction,
    val kFactor: Fraction,
    /** K·T — how far the neutral axis sits in from the inside face. */
    val neutralAxisOffset: Fraction,
    /** R + K·T — the radius the neutral axis is bent around. */
    val neutralRadius: Fraction,
    /** BA = π·(R + K·T)·(A/180) — always defined. */
    val bendAllowance: Fraction,
    /** OSSB = tan(A/2)·(R + T); null at a 180° bend. */
    val outsideSetback: Fraction?,
    /** BD = 2·OSSB − BA; null when [outsideSetback] is undefined. */
    val bendDeduction: Fraction?,
)

/**
 * Cross-check of the two flat-length methods for a single bend.
 *
 * [byDeduction] uses legs measured to the outside corner: legA + legB − BD.
 * [byAllowance] uses the straight (setback-trimmed) legs plus the bend allowance.
 * The two are algebraically identical, so a non-zero [difference] beyond rounding means a bug.
 */
data class LegCrossCheck(
    val legA: Fraction,
    val legB: Fraction,
    val byDeduction: Fraction,
    val byAllowance: Fraction,
    val difference: Fraction,
    val agrees: Boolean,
)

/** The whole chain of bends plus the totals used to lay out a flat pattern. */
data class BendChainResult(
    val bends: List<BendResult>,
    val totalBendAllowance: Fraction,
    val totalOutsideSetback: Fraction?,
    val totalBendDeduction: Fraction?,
    /** Straight material between/around the bends (unfolded legs), if the user entered it. */
    val straightSections: Fraction?,
    /** Developed length = straight sections + total bend allowance. */
    val flatLength: Fraction?,
)

/**
 * Pure, Android-free maths for the Bend Allowance / Flat Pattern tool.
 *
 * When a sheet is bent the outer fibres stretch and the inner fibres compress; only the neutral
 * axis keeps its original length. Everything here is built around that one idea:
 *
 *  - BA   = π·(R + K·T)·(A/180)   — length of the neutral axis through the bend
 *  - OSSB = tan(A/2)·(R + T)          — outside setback (corner to tangent point)
 *  - BD   = 2·OSSB − BA               — bend deduction
 *  - flat = Σ straight legs + Σ BA   (allowance method)
 *  - flat = leg + leg − BD            (deduction method, per bend)
 *
 * Angle convention: every `angleDegrees` in this tool is the bend angle A — the angle the material
 * is turned through, i.e. the central angle of the bend arc. It is the complement of the interior
 * (included) angle: included = 180° − A. So A = 90° gives perpendicular flanges, A = 180° folds
 * the sheet back on itself (a hem, whose neutral arc is a semicircle), and A = 1° is almost flat.
 * A is used directly in BA, OSSB and BD above.
 *
 * The maths is unit-agnostic — a value in mm behaves exactly like the same value in inches — so
 * the unit is only a label. It is kept free of any UI framework types so it is fully testable.
 */
object BendAllowanceMath {

    const val MIN_ANGLE = 1
    const val MAX_ANGLE = 180

    /** K-factor is bounded to the physically sensible 0.00 .. 0.50. */
    val MIN_K: Fraction = Fraction.ZERO
    val MAX_K: Fraction = Fraction.of(1L, 2L)

    /** Default K-factor offered to the user (mild steel / soft aluminium rule of thumb). */
    val DEFAULT_K: Fraction = Fraction.fromDecimal("0.33")

    /** Two flat-length methods should agree to well within any shop rounding. */
    val CROSS_CHECK_TOLERANCE: Fraction = Fraction.of(1L, 1_000_000L)

    private val ONE_EIGHTY = Fraction.of(180L, 1L)
    private val TWO = Fraction.of(2L, 1L)

    /**
     * Common shop K-factors. These are deliberately coarse starting points — the real K-factor
     * varies with tooling, material lot and bending method. Test-bend a coupon and enter your own.
     *
     * A preset carries only a name and a K value; it does NOT encode material temper, thickness or
     * bending method — the same nominal material bends differently across those.
     */
    val K_FACTOR_PRESETS: List<KFactorPreset> = listOf(
        KFactorPreset("soft_aluminum", "Soft aluminum", Fraction.fromDecimal("0.33")),
        KFactorPreset("aluminum_half_hard", "Aluminum (half-hard)", Fraction.fromDecimal("0.40")),
        KFactorPreset("mild_steel", "Mild steel", Fraction.fromDecimal("0.33")),
        KFactorPreset("stainless", "Stainless steel", Fraction.fromDecimal("0.44")),
        KFactorPreset("spring_steel", "Spring steel", Fraction.fromDecimal("0.45")),
    )

    /** Neutral-axis offset from the inside face: K·T. */
    fun neutralAxisOffset(k: Fraction, thickness: Fraction): Fraction = k * thickness

    /** Radius the neutral axis is bent around: R + K·T. */
    fun neutralRadius(radius: Fraction, k: Fraction, thickness: Fraction): Fraction =
        radius + k * thickness

    /**
     * Bend-arc geometry for [angleDegrees]: the arc's end angles and the incoming/outgoing material
     * travel directions. The arc spans A centred on "straight down" (270°), and the tangent
     * directions are dP/dφ = (−sin φ, cos φ); the material enters against the arc start and leaves
     * along the arc end, so the angle between [BendArcGeometry.incomingX]/Y and
     * [BendArcGeometry.outgoingX]/Y is exactly A.
     */
    fun bendArcGeometry(angleDegrees: Fraction): BendArcGeometry {
        val a = angleDegrees.toDoubleValue().coerceIn(MIN_ANGLE.toDouble(), MAX_ANGLE.toDouble())
        val start = Math.toRadians(270.0 - a / 2.0)
        val end = Math.toRadians(270.0 + a / 2.0)
        // Leg 1 points back along the arc (−dP/dφ at start); leg 2 points forward (+dP/dφ at end).
        return BendArcGeometry(
            startRadians = start,
            endRadians = end,
            incomingX = -sin(start),
            incomingY = cos(start),
            outgoingX = -sin(end),
            outgoingY = cos(end),
        )
    }

    /** BA = π·(R + K·T)·(A/180). */
    fun bendAllowance(radius: Fraction, k: Fraction, thickness: Fraction, angleDegrees: Fraction): Fraction {
        if (angleDegrees.isZero) return Fraction.ZERO
        return Fraction.PI * (radius + k * thickness) * (angleDegrees / ONE_EIGHTY)
    }

    /**
     * OSSB = tan(A/2)·(R + T). Returns null for a 180° bend (parallel legs, tan 90° undefined)
     * or any non-positive angle. The tangent is irrational, so it is carried as a
     * [fractionFromDouble] like the rest of the app's trig.
     */
    fun outsideSetback(radius: Fraction, thickness: Fraction, angleDegrees: Fraction): Fraction? {
        if (angleDegrees.isZero || angleDegrees.isNegative) return null
        // angle >= 180 -> parallel legs.
        val over = angleDegrees - ONE_EIGHTY
        if (over.isZero || !over.isNegative) return null
        val halfRadians = Math.toRadians(angleDegrees.toDoubleValue() / 2.0)
        val tangent = tan(halfRadians)
        if (!tangent.isFinite()) return null
        return fractionFromDouble(tangent * (radius + thickness).toDoubleValue())
    }

    /** BD = 2·OSSB − BA; null when the setback is undefined. */
    fun bendDeduction(bendAllowance: Fraction, outsideSetback: Fraction?): Fraction? =
        outsideSetback?.let { TWO * it - bendAllowance }

    /** Works out BA, OSSB and BD for a single bend. */
    fun computeBend(
        thickness: Fraction,
        radius: Fraction,
        angleDegrees: Fraction,
        k: Fraction,
    ): BendResult {
        val offset = neutralAxisOffset(k, thickness)
        val allowance = bendAllowance(radius, k, thickness, angleDegrees)
        val setback = outsideSetback(radius, thickness, angleDegrees)
        return BendResult(
            angleDegrees = angleDegrees,
            insideRadius = radius,
            thickness = thickness,
            kFactor = k,
            neutralAxisOffset = offset,
            neutralRadius = neutralRadius(radius, k, thickness),
            bendAllowance = allowance,
            outsideSetback = setback,
            bendDeduction = bendDeduction(allowance, setback),
        )
    }

    /**
     * Computes every bend in a chain and the totals used for a flat pattern.
     *
     * @param straightSections the total unfolded straight material (legs trimmed of their
     *   setbacks). When supplied, [BendChainResult.flatLength] = straightSections + Σ BA.
     */
    fun computeChain(
        thickness: Fraction,
        k: Fraction,
        bends: List<Bend>,
        straightSections: Fraction? = null,
    ): BendChainResult {
        val results = bends.map { computeBend(thickness, it.insideRadius, it.angleDegrees, k) }
        val totalBa = results.fold(Fraction.ZERO) { acc, b -> acc + b.bendAllowance }
        val totalOssb = sumOrNull(results.map { it.outsideSetback })
        val totalBd = sumOrNull(results.map { it.bendDeduction })
        val flat = straightSections?.let { it + totalBa }
        return BendChainResult(
            bends = results,
            totalBendAllowance = totalBa,
            totalOutsideSetback = totalOssb,
            totalBendDeduction = totalBd,
            straightSections = straightSections,
            flatLength = flat,
        )
    }

    /**
     * Cross-checks the two flat-length methods for one bend. Returns null when the bend has no
     * deduction (a 180° bend), because the leg method is undefined there.
     */
    fun crossCheck(bend: BendResult, legA: Fraction, legB: Fraction): LegCrossCheck? {
        val ossb = bend.outsideSetback ?: return null
        val bd = bend.bendDeduction ?: return null
        val byDeduction = legA + legB - bd
        val byAllowance = (legA - ossb) + (legB - ossb) + bend.bendAllowance
        val difference = byAllowance - byDeduction
        val overTolerance = difference.abs() - CROSS_CHECK_TOLERANCE
        return LegCrossCheck(
            legA = legA,
            legB = legB,
            byDeduction = byDeduction,
            byAllowance = byAllowance,
            difference = difference,
            agrees = overTolerance.isZero || overTolerance.isNegative,
        )
    }

    /** True when [angleDegrees] is within 1° .. 180° inclusive. */
    fun isValidAngle(angleDegrees: Fraction): Boolean {
        if (angleDegrees.isZero || angleDegrees.isNegative) return false
        val over = angleDegrees - ONE_EIGHTY
        return over.isZero || over.isNegative
    }

    /** True when [k] is within 0.00 .. 0.50 inclusive. */
    fun isValidK(k: Fraction): Boolean {
        if (k.isNegative) return false
        val over = k - MAX_K
        return over.isZero || over.isNegative
    }

    private fun sumOrNull(values: List<Fraction?>): Fraction? {
        if (values.isEmpty()) return Fraction.ZERO
        if (values.any { it == null }) return null
        return values.filterNotNull().fold(Fraction.ZERO) { acc, v -> acc + v }
    }
}
