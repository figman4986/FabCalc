package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.figapps.fractioncalculatorv1.domain.AnnotationLayer
import com.figapps.fractioncalculatorv1.domain.AnnotationVisibility
import com.figapps.fractioncalculatorv1.domain.CircleDividerMath
import com.figapps.fractioncalculatorv1.domain.DiagramLayout
import com.figapps.fractioncalculatorv1.domain.DiagramRect
import com.figapps.fractioncalculatorv1.domain.DiagramRole
import com.figapps.fractioncalculatorv1.domain.DiagramSide
import com.figapps.fractioncalculatorv1.domain.DiagramSize
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.ResultPrecision
import com.figapps.fractioncalculatorv1.domain.fractionFromDouble
import com.figapps.fractioncalculatorv1.ui.CalcTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive "circle divided into N equal parts" diagram.
 *
 * The circle, its divisions and the chord polygon dominate; exactly one annotation is primary — the
 * chord step (or the hole spacing in bolt mode), the number the divider exists to give. Other layers:
 *
 *  - BASICS: circle, radial ticks, chord polygon and the chord/hole-spacing result.
 *  - DIMENSIONS (default ON): the radius dimension.
 *  - THEORY (default OFF): the chord-vs-arc sliver and its "chord < arc" teaching note.
 *
 * Gestures are unchanged: tap selects the nearest wedge, drag spins the start mark, pinch changes N.
 */
@Composable
fun CircleDividerDiagram(
    radiusUnits: Double,
    divisions: Int,
    startAngleDegrees: Double,
    boltRadiusUnits: Double?,
    selectedIndex: Int,
    unitSymbol: String,
    precision: ResultPrecision,
    modifier: Modifier = Modifier,
    visibility: AnnotationVisibility = AnnotationVisibility(),
    onStartAngleChange: (Double) -> Unit,
    onSelectIndex: (Int) -> Unit,
    onDivisionsChange: (Int) -> Unit,
) {
    val colors = CalcTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelTheme = rememberDiagramLabelTheme()

    val currentDivisions by rememberUpdatedState(divisions)
    val currentStart by rememberUpdatedState(startAngleDegrees)
    val changeStart by rememberUpdatedState(onStartAngleChange)
    val changeDivisions by rememberUpdatedState(onDivisionsChange)
    val selectIndex by rememberUpdatedState(onSelectIndex)

    val wedgeTintA = colors.cardBackground
    val wedgeTintB = colors.divider.copy(alpha = 0.45f)
    val outline = colors.divider
    val accent = colors.accentKey
    val chordColor = colors.operatorKey
    val inkSoft = colors.numberKeyText.copy(alpha = 0.6f)
    val sliverColor = colors.equalsKey

    val radiusText = "r ${ResultFormat.decimalText(fractionFromDouble(radiusUnits), ResultFormat.decimalPlaces(precision))} $unitSymbol"

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val slop = viewConfiguration.touchSlop

                        var mode = 0 // 0 undecided, 1 drag, 2 pinch
                        var maxMove = 0f
                        var dragBase = currentStart
                        var accumulated = 0.0
                        var prevAngle = pointerAngle(down.position, center)
                        var pinchBase = currentDivisions
                        var anchorDistance = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                val distance = averageDistance(pressed.map { it.position }, center)
                                if (mode != 2) {
                                    mode = 2
                                    pinchBase = currentDivisions
                                    anchorDistance = distance
                                } else if (anchorDistance > 1f && distance > 1f) {
                                    val factor = distance / anchorDistance
                                    if (factor.isFinite()) {
                                        val newN = (pinchBase * factor).roundToInt()
                                            .coerceIn(CircleDividerMath.MIN_DIVISIONS, CircleDividerMath.MAX_DIVISIONS)
                                        changeDivisions(newN)
                                    }
                                }
                                anchorDistance = distance
                                event.changes.forEach { it.consume() }
                            } else {
                                val position = pressed.first().position
                                if (mode == 0) {
                                    maxMove = maxMove.coerceAtLeast((position - down.position).getDistance())
                                    if (maxMove > slop) {
                                        val dx = position.x - down.position.x
                                        val dy = position.y - down.position.y
                                        if (abs(dy) > abs(dx)) break
                                        mode = 1
                                        dragBase = currentStart
                                        accumulated = 0.0
                                        prevAngle = pointerAngle(position, center)
                                    }
                                }
                                if (mode == 1) {
                                    val angle = pointerAngle(position, center)
                                    var delta = angle - prevAngle
                                    while (delta > 180.0) delta -= 360.0
                                    while (delta < -180.0) delta += 360.0
                                    accumulated += delta
                                    prevAngle = angle
                                    changeStart(dragBase + accumulated)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }

                        if (mode == 0 && maxMove < slop) {
                            selectIndex(pickWedge(pointerAngle(down.position, center), currentStart, currentDivisions))
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val center = Offset(cx, cy)
            val radius = min(w, h) / 2f * 0.78f
            val step = 360.0 / divisions

            fun point(angleDegrees: Double, r: Float): Offset {
                val radians = Math.toRadians(angleDegrees)
                return Offset(cx + r * cos(radians).toFloat(), cy - r * sin(radians).toFloat())
            }

            // --- wedge fills ---
            if (divisions <= 60) {
                for (i in 0 until divisions) {
                    val theta0 = startAngleDegrees + i * step
                    val theta1 = theta0 + step
                    val fill = when {
                        // A much stronger fill, not just a hue shift, so the selected wedge is
                        // still obvious in grayscale / for colour-blind users.
                        i == selectedIndex -> accent.copy(alpha = 0.38f)
                        i % 2 == 0 -> wedgeTintA
                        else -> wedgeTintB
                    }
                    drawArc(
                        color = fill,
                        startAngle = (-theta1).toFloat(),
                        sweepAngle = step.toFloat(),
                        useCenter = true,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                }
            }

            val idx = if (selectedIndex in 0 until divisions) selectedIndex else 0
            val selectedMidAngle = startAngleDegrees + (idx + 0.5) * step

            // --- THEORY: chord/arc sliver + teaching note ---
            if (visibility.shows(AnnotationLayer.THEORY) && divisions in 2..12) {
                val theta0 = startAngleDegrees + idx * step
                val theta1 = theta0 + step
                val path = Path()
                val first = point(theta0, radius)
                path.moveTo(first.x, first.y)
                val subSteps = 12
                for (k in 1..subSteps) {
                    val a = theta0 + (theta1 - theta0) * k / subSteps
                    val p = point(a, radius)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color = sliverColor.copy(alpha = 0.45f))
                drawPath(path, color = sliverColor, style = Stroke(width = 1.dp.toPx()))
            }

            // --- bolt circle (geometry; always drawn in bolt mode) ---
            if (boltRadiusUnits != null && radiusUnits > 0.0) {
                val ratio = (boltRadiusUnits / radiusUnits).coerceIn(0.05, 0.98).toFloat()
                val boltRadius = radius * ratio
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = boltRadius,
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))
                    )
                )
                for (i in 0 until divisions) {
                    val a = startAngleDegrees + i * step
                    drawCircle(accent, 3.dp.toPx(), point(a, boltRadius))
                }
            }

            // --- main circle + radial ticks ---
            drawCircle(outline, radius, center, style = Stroke(width = 2.dp.toPx()))
            for (i in 0 until divisions) {
                val a = startAngleDegrees + i * step
                drawLine(inkSoft, point(a, radius * 0.93f), point(a, radius), strokeWidth = 1.2.dp.toPx())
            }

            // --- chord polygon ---
            val chordPath = Path()
            for (i in 0 until divisions) {
                val a = startAngleDegrees + i * step
                val p = point(a, radius)
                if (i == 0) chordPath.moveTo(p.x, p.y) else chordPath.lineTo(p.x, p.y)
            }
            chordPath.close()
            drawPath(chordPath, color = chordColor.copy(alpha = 0.9f), style = Stroke(width = 1.5.dp.toPx()))

            // --- centre cross ---
            val cross = 5.dp.toPx()
            drawLine(inkSoft, Offset(cx - cross, cy), Offset(cx + cross, cy), strokeWidth = 1.dp.toPx())
            drawLine(inkSoft, Offset(cx, cy - cross), Offset(cx, cy + cross), strokeWidth = 1.dp.toPx())

            // --- start mark with an outward arrow ---
            val startRadians = Math.toRadians(startAngleDegrees)
            val outward = Offset(cos(startRadians).toFloat(), -sin(startRadians).toFloat())
            val tip = point(startAngleDegrees, radius)
            val markBase = point(startAngleDegrees, radius * 0.86f)
            drawLine(accent, markBase, tip, strokeWidth = 3.dp.toPx())
            val perp = Offset(-outward.y, outward.x)
            val headBase = tip - outward * 9.dp.toPx()
            drawLine(accent, tip, headBase + perp * 5.dp.toPx(), strokeWidth = 2.dp.toPx())
            drawLine(accent, tip, headBase - perp * 5.dp.toPx(), strokeWidth = 2.dp.toPx())

            // --- DIMENSIONS: radius line ---
            if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                drawLine(inkSoft, center, point(startAngleDegrees, radius), strokeWidth = 1.dp.toPx())
            }

            // --- labels ---
            val primaryText = if (boltRadiusUnits != null) {
                boltHoleSpacingText(boltRadiusUnits, divisions, unitSymbol, precision)
            } else {
                "chord ${chordText(radiusUnits, divisions, precision)} $unitSymbol"
            }
            val requests = buildList {
                add(
                    measureLabelRequest(
                        textMeasurer, labelTheme, "primary", primaryText,
                        point(selectedMidAngle, radius), DiagramSide.ABOVE,
                        DiagramRole.PRIMARY, priority = 0
                    )
                )
                if (visibility.shows(AnnotationLayer.DIMENSIONS) && radiusUnits > 0.0) {
                    add(
                        measureLabelRequest(
                            textMeasurer, labelTheme, "r", radiusText,
                            point(startAngleDegrees, radius), DiagramSide.RIGHT, priority = 10
                        )
                    )
                }
                if (visibility.shows(AnnotationLayer.THEORY) && divisions in 2..12) {
                    val apothemPoint = point(selectedMidAngle, radius * cos(Math.toRadians(step / 2.0)).toFloat())
                    add(
                        measureLabelRequest(
                            textMeasurer, labelTheme, "sliver", "chord < arc",
                            apothemPoint, DiagramSide.BELOW, priority = 20
                        )
                    )
                }
            }
            val placed = DiagramLayout.placeLabels(
                requests = requests,
                canvas = DiagramSize(w, h),
                obstacles = listOf(DiagramRect(cx - radius * 0.85f, cy - radius * 0.85f, cx + radius * 0.85f, cy + radius * 0.85f)),
                gap = DiagramLayout.GAP_DP.dp.toPx(),
                margin = DiagramLayout.MARGIN_DP.dp.toPx(),
            )
            drawDiagramLabels(textMeasurer, labelTheme, placed)
        }
        Spacer(Modifier.height(4.dp))
        DiagramCaption("Drag to spin · tap a wedge to select · pinch to change the count.")
    }
}

private fun chordText(radiusUnits: Double, divisions: Int, precision: ResultPrecision): String {
    val chord = fractionFromDouble(2.0 * radiusUnits * sin(Math.toRadians(180.0 / divisions)))
    return ResultFormat.format(chord, precision).decimalText
}

private fun boltHoleSpacingText(
    boltRadiusUnits: Double,
    divisions: Int,
    unitSymbol: String,
    precision: ResultPrecision,
): String {
    val spacing = fractionFromDouble(2.0 * boltRadiusUnits * sin(Math.toRadians(180.0 / divisions)))
    return "hole spacing ${ResultFormat.format(spacing, precision).decimalText} $unitSymbol"
}

private fun pointerAngle(position: Offset, center: Offset): Double {
    val dx = (position.x - center.x).toDouble()
    val dy = -(position.y - center.y).toDouble()
    var degrees = Math.toDegrees(atan2(dy, dx))
    if (degrees < 0.0) degrees += 360.0
    return degrees
}

private fun averageDistance(points: List<Offset>, center: Offset): Float {
    if (points.isEmpty()) return 0f
    var sum = 0f
    for (p in points) sum += (p - center).getDistance()
    return sum / points.size
}

private fun pickWedge(angleDegrees: Double, startDegrees: Double, divisions: Int): Int {
    val step = 360.0 / divisions
    var relative = (angleDegrees - startDegrees) % 360.0
    if (relative < 0.0) relative += 360.0
    return floor(relative / step).toInt().coerceIn(0, divisions - 1)
}
