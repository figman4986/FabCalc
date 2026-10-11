package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.figapps.fractioncalculatorv1.domain.AnnotationLayer
import com.figapps.fractioncalculatorv1.domain.AnnotationVisibility
import com.figapps.fractioncalculatorv1.domain.BendAllowanceMath
import com.figapps.fractioncalculatorv1.domain.BendResult
import com.figapps.fractioncalculatorv1.domain.DiagramLayout
import com.figapps.fractioncalculatorv1.domain.DiagramRect
import com.figapps.fractioncalculatorv1.domain.DiagramRole
import com.figapps.fractioncalculatorv1.domain.DiagramSide
import com.figapps.fractioncalculatorv1.domain.DiagramSize
import com.figapps.fractioncalculatorv1.domain.Fraction
import com.figapps.fractioncalculatorv1.domain.ResultFormat
import com.figapps.fractioncalculatorv1.domain.toDoubleValue
import com.figapps.fractioncalculatorv1.ui.CalcTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Side profile of a single bent sheet.
 *
 * The geometry dominates: the filled cross-section and the neutral-axis arc (drawn thin, in accent)
 * carry the strongest weight, and exactly one annotation is primary — the bend allowance, the number
 * this tool exists to produce. Everything else is subordinate and filtered by [visibility]:
 *
 *  - BASICS: the sheet cross-section, the neutral axis and the BA result chip.
 *  - DIMENSIONS (default ON): R, T, bend-angle and neutral-radius/K·T leader callouts.
 *  - THEORY (default OFF): the neutral-axis highlight (thick overdraw + end ticks) and the outside
 *    mold lines that meet at the bend corner.
 *
 * A very thin material is drawn at an exaggerated thickness; that is surfaced as a footnote under
 * the diagram rather than a chip on the canvas.
 */
@Composable
fun BendDiagram(
    thickness: Fraction,
    insideRadius: Fraction,
    angleDegrees: Fraction,
    kFactor: Fraction,
    unitSymbol: String,
    modifier: Modifier = Modifier,
    visibility: AnnotationVisibility = AnnotationVisibility(),
) {
    val colors = CalcTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelTheme = rememberDiagramLabelTheme()

    val bend = BendAllowanceMath.computeBend(thickness, insideRadius, angleDegrees, kFactor)

    val metal = colors.operatorKey.copy(alpha = 0.5f)
    val outline = colors.divider
    val accent = colors.accentKey
    val inkSoft = colors.numberKeyText.copy(alpha = 0.6f)

    val radiusText = "R ${ResultFormat.decimalText(insideRadius, 3)} $unitSymbol"
    val thicknessText = "T ${ResultFormat.decimalText(thickness, 3)} $unitSymbol"
    val neutralText = "R+K\u00B7T ${ResultFormat.decimalText(bend.neutralRadius, 3)} $unitSymbol\n" +
        "(K\u00B7T ${ResultFormat.decimalText(bend.neutralAxisOffset, 4)})"
    val baText = "BA ${ResultFormat.decimalText(bend.bendAllowance, 3)} $unitSymbol"
    val angleText = "${ResultFormat.decimalText(angleDegrees, 2).trimEnd('0').trimEnd('.')}\u00B0"

    // Computed in the composition phase so the caption updates reliably.
    val exaggerated = isExaggerated(thickness.toDoubleValue(), insideRadius.toDoubleValue())
    val caption = if (exaggerated) {
        "Thickness shown exaggerated so the neutral-axis arc stays visible \u2014 not to scale."
    } else {
        null
    }

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.05f)) {
            var t = thickness.toDoubleValue().coerceAtLeast(0.0)
            val r = insideRadius.toDoubleValue().coerceAtLeast(0.0)
            val k = kFactor.toDoubleValue().coerceIn(0.0, 0.5)

            val legLen = (r + t) * 1.9 + t * 2.0
            if (isExaggerated(t, r)) t = (r + legLen) * 0.06
            val rNeutral = r + k * t
            val rOuter = r + t

            val geometry = BendAllowanceMath.bendArcGeometry(angleDegrees)
            val phi0 = geometry.startRadians
            val phi1 = geometry.endRadians

            fun circ(radius: Double, phi: Double) = Offset((radius * cos(phi)).toFloat(), (radius * sin(phi)).toFloat())
            fun sampleArc(radius: Double, from: Double, to: Double, steps: Int = 28): List<Offset> =
                (0..steps).map { circ(radius, from + (to - from) * it / steps) }

            val t1 = circ(r, phi0)
            val t2 = circ(r, phi1)
            val n1 = Offset(cos(phi0).toFloat(), sin(phi0).toFloat())
            val n2 = Offset(cos(phi1).toFloat(), sin(phi1).toFloat())
            val dir1 = Offset(sin(phi0).toFloat(), -cos(phi0).toFloat())
            val dir2 = Offset(-sin(phi1).toFloat(), cos(phi1).toFloat())
            val t2o = t2 + n2 * t.toFloat()
            val p1in = t1 + dir1 * legLen.toFloat()
            val p1out = p1in + n1 * t.toFloat()
            val p2in = t2 + dir2 * legLen.toFloat()
            val p2out = p2in + n2 * t.toFloat()

            val polygon = buildList {
                addAll(sampleArc(r, phi0, phi1))
                add(p2in); add(p2out)
                add(t2o)
                addAll(sampleArc(rOuter, phi1, phi0))
                add(p1out); add(p1in)
            }
            val neutralArc = sampleArc(rNeutral, phi0, phi1)

            val all = polygon + neutralArc
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
            val bw = (maxX - minX).coerceAtLeast(1e-4f)
            val bh = (maxY - minY).coerceAtLeast(1e-4f)

            val pad = DiagramLayout.MARGIN_DP.dp.toPx()
            val scale = min((size.width - 2 * pad) / bw, (size.height - 2 * pad) / bh)
            val originX = pad + (size.width - 2 * pad - bw * scale) / 2f - minX * scale
            val originY = pad + (size.height - 2 * pad - bh * scale) / 2f + maxY * scale
            fun toScreen(p: Offset) = Offset(originX + p.x * scale, originY - p.y * scale)

            // --- geometry (strongest weights) ---
            val fillPath = Path().apply {
                polygon.forEachIndexed { i, p ->
                    val s = toScreen(p)
                    if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
                }
                close()
            }
            drawPath(fillPath, metal)
            drawPath(fillPath, outline, style = Stroke(1.5.dp.toPx()))

            val neutralPath = Path().apply {
                neutralArc.forEachIndexed { i, p ->
                    val s = toScreen(p)
                    if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
                }
            }
            drawPath(neutralPath, accent.copy(alpha = 0.85f), style = Stroke(width = 1.6.dp.toPx()))

            val insideMid = circ(r, Math.toRadians(270.0))
            val neutralMid = circ(rNeutral, Math.toRadians(270.0))
            val baMid = circ(rNeutral, Math.toRadians(270.0))
            val thickCenter = (p2in + p2out) / 2f

            // --- THEORY: neutral-axis highlight + mold lines ---
            if (visibility.shows(AnnotationLayer.THEORY)) {
                drawPath(neutralPath, accent, style = Stroke(width = 3.dp.toPx()))
                drawTick(toScreen(neutralArc.first()), toScreen(neutralArc[2]), accent)
                drawTick(toScreen(neutralArc.last()), toScreen(neutralArc[neutralArc.size - 3]), accent)

                val apexUnit = lineIntersect(t1, dir1, t2, dir2)
                if (apexUnit != null) {
                    val apex = toScreen(apexUnit)
                    drawDashedLine(apex, toScreen(t1), inkSoft.copy(alpha = 0.5f), 1.dp.toPx())
                    drawDashedLine(apex, toScreen(t2), inkSoft.copy(alpha = 0.5f), 1.dp.toPx())
                }
            }

            // --- DIMENSIONS: R, T and the bend-angle arc ---
            var angleAnchor: Offset? = null
            if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                drawLine(inkSoft, toScreen(Offset(0f, 0f)), toScreen(insideMid), strokeWidth = 1.dp.toPx())
                drawArrowHead(toScreen(insideMid), toScreen(Offset(0f, 0f)), inkSoft, 1.dp.toPx())
                drawLine(inkSoft, toScreen(p2in), toScreen(p2out), strokeWidth = 1.dp.toPx())

                val apexUnit = lineIntersect(t1, dir1, t2, dir2)
                if (apexUnit != null) {
                    val apex = toScreen(apexUnit)
                    val rArc = legLen.toFloat() * 0.42f * scale
                    val incoming = Offset(geometry.incomingX.toFloat(), (-geometry.incomingY).toFloat())
                    val outgoing = Offset(geometry.outgoingX.toFloat(), (-geometry.outgoingY).toFloat())
                    val a0 = atan2(incoming.y, incoming.x)
                    val a1 = atan2(outgoing.y, outgoing.x)
                    var delta = a1 - a0
                    while (delta > PI) delta -= (2 * PI).toFloat()
                    while (delta < -PI) delta += (2 * PI).toFloat()
                    val arcPath = Path()
                    val steps = 20
                    for (i in 0..steps) {
                        val a = a0 + delta * i / steps
                        val p = Offset(apex.x + rArc * cos(a), apex.y + rArc * sin(a))
                        if (i == 0) arcPath.moveTo(p.x, p.y) else arcPath.lineTo(p.x, p.y)
                    }
                    drawPath(arcPath, inkSoft, style = Stroke(1.dp.toPx()))
                    val midA = a0 + delta / 2f
                    angleAnchor = Offset(apex.x + rArc * 1.15f * cos(midA), apex.y + rArc * 1.15f * sin(midA))
                }
            }

            // --- labels ---
            val requests = buildList {
                add(
                    measureLabelRequest(
                        textMeasurer, labelTheme, "ba", baText, toScreen(baMid),
                        DiagramSide.BELOW, DiagramRole.PRIMARY, priority = 0
                    )
                )
                if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                    add(measureLabelRequest(textMeasurer, labelTheme, "r", radiusText, toScreen(insideMid), DiagramSide.RIGHT, priority = 10))
                    add(measureLabelRequest(textMeasurer, labelTheme, "t", thicknessText, toScreen(thickCenter), DiagramSide.RIGHT, priority = 20))
                    add(measureLabelRequest(textMeasurer, labelTheme, "neutral", neutralText, toScreen(neutralMid), DiagramSide.LEFT, priority = 30))
                    val anchor = angleAnchor ?: toScreen(Offset(0f, 0f))
                    add(measureLabelRequest(textMeasurer, labelTheme, "angle", angleText, anchor, DiagramSide.LEFT, priority = 40))
                }
            }

            val placed = DiagramLayout.placeLabels(
                requests = requests,
                canvas = DiagramSize(size.width, size.height),
                obstacles = listOf(insetObstacle(all.map { toScreen(it) })),
                gap = DiagramLayout.GAP_DP.dp.toPx(),
                margin = DiagramLayout.MARGIN_DP.dp.toPx(),
            )
            drawDiagramLabels(textMeasurer, labelTheme, placed)
        }
        Spacer(Modifier.height(4.dp))
        DiagramCaption(caption)
    }
}

/**
 * Simplified multi-bend schematic: a Z/U-style profile on top and an "unfold" strip below with the
 * total developed length. Deliberately schematic (not to scale). BASICS shows the profile, the strip
 * and the flat-length result; the extra layers add nothing here (the part is already stripped back).
 */
@Composable
fun MultiBendDiagram(
    bends: List<BendResult>,
    flatLength: Fraction?,
    unitSymbol: String,
    modifier: Modifier = Modifier,
    visibility: AnnotationVisibility = AnnotationVisibility(),
) {
    val colors = CalcTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelTheme = rememberDiagramLabelTheme()
    val metal = colors.operatorKey.copy(alpha = 0.5f)
    val outline = colors.divider
    val accent = colors.accentKey

    val flatText = flatLength?.let { "Flat length ${ResultFormat.decimalText(it, 3)} $unitSymbol" }
        ?: "Enter straight sections to total the flat length"
    val count = bends.size.coerceAtLeast(1)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val n = bends.size.coerceAtLeast(1)
            val profileTop = DiagramLayout.MARGIN_DP.dp.toPx()
            val profileBottom = size.height * 0.62f
            val stripTop = size.height * 0.72f
            val stripHeight = size.height * 0.16f

            val drawn = n.coerceAtMost(8)
            val segLen = 30.dp.toPx()
            val rArc = 12.dp.toPx()

            val dirs = (0..drawn).map { i ->
                if (i % 2 == 0) {
                    Offset(1f, 0f)
                } else {
                    val k = (i - 1) / 2
                    if (k % 2 == 0) Offset(0f, -1f) else Offset(0f, 1f)
                }
            }
            val pts = ArrayList<Offset>()
            val corners = ArrayList<Offset>()
            var cur = Offset(20.dp.toPx(), (profileTop + profileBottom) / 2f)
            pts.add(cur)
            for (i in 0 until drawn) {
                val d = dirs[i]
                val next = dirs[i + 1]
                val corner = cur + d * segLen
                val pin = corner - d * rArc
                val pout = corner + next * rArc
                val center = pin + next * rArc
                pts.add(pin)
                corners.add(corner)
                val a0 = atan2(pin.y - center.y, pin.x - center.x)
                val a1 = atan2(pout.y - center.y, pout.x - center.x)
                var delta = a1 - a0
                while (delta > PI) delta -= (2 * PI).toFloat()
                while (delta < -PI) delta += (2 * PI).toFloat()
                val steps = 12
                for (k in 1..steps) {
                    val a = a0 + delta * k / steps
                    pts.add(Offset(center.x + rArc * cos(a), center.y + rArc * sin(a)))
                }
                pts.add(pout)
                cur = pout
            }

            val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
            val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
            val pad = DiagramLayout.MARGIN_DP.dp.toPx()
            val availW = (size.width - 2 * pad).coerceAtLeast(1f)
            val availH = (profileBottom - profileTop).coerceAtLeast(1f)
            val s = min(availW / (maxX - minX).coerceAtLeast(1f), availH / (maxY - minY).coerceAtLeast(1f))
            val offX = pad + (availW - (maxX - minX) * s) / 2f - minX * s
            val offY = profileTop + (availH - (maxY - minY) * s) / 2f - minY * s
            fun toScreen(p0: Offset) = Offset(offX + p0.x * s, offY + p0.y * s)

            val profile = Path().apply {
                pts.forEachIndexed { i, q ->
                    val sp = toScreen(q)
                    if (i == 0) moveTo(sp.x, sp.y) else lineTo(sp.x, sp.y)
                }
            }
            drawPath(profile, metal)
            drawPath(profile, outline, style = Stroke(1.5.dp.toPx()))
            if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                corners.forEach { drawCircle(accent, 3.dp.toPx(), toScreen(it)) }
            }

            // --- unfold strip ---
            val stripRect = Offset(pad, stripTop)
            val stripSize = Size(size.width - 2 * pad, stripHeight)
            drawRoundRect(metal, stripRect, stripSize, CornerRadius(6.dp.toPx()))
            drawRoundRect(outline, stripRect, stripSize, CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                val sections = n + 1
                for (i in 1 until sections) {
                    val x = pad + stripSize.width * i / sections
                    drawLine(accent, Offset(x, stripTop), Offset(x, stripTop + stripHeight), strokeWidth = 1.5.dp.toPx())
                }
            }

            val requests = listOf(
                measureLabelRequest(
                    textMeasurer, labelTheme, "flat", flatText,
                    Offset(size.width / 2f, stripTop + stripHeight / 2f),
                    DiagramSide.BELOW, DiagramRole.PRIMARY, priority = 0
                )
            )
            val placed = DiagramLayout.placeLabels(
                requests = requests,
                canvas = DiagramSize(size.width, size.height),
                obstacles = emptyList(),
                gap = DiagramLayout.GAP_DP.dp.toPx(),
                margin = DiagramLayout.MARGIN_DP.dp.toPx(),
            )
            drawDiagramLabels(textMeasurer, labelTheme, placed)
        }
        Spacer(Modifier.height(4.dp))
        DiagramCaption("$count-bend Z/U profile is schematic and not to scale \u2014 BA is summed across each bend.")
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Drawing helpers                                                                                */
/* --------------------------------------------------------------------------------------------- */

private fun isExaggerated(thickness: Double, radius: Double): Boolean {
    val t = thickness.coerceAtLeast(0.0)
    val r = radius.coerceAtLeast(0.0)
    val legLen = (r + t) * 1.9 + t * 2.0
    return t < (r + legLen) * 0.06
}

/** A keep-out rect covering the inset core of the geometry, so labels sit in the margin ring. */
private fun insetObstacle(points: List<Offset>): DiagramRect {
    if (points.isEmpty()) return DiagramRect(0f, 0f, 0f, 0f)
    val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
    val ix = (maxX - minX) * 0.12f
    val iy = (maxY - minY) * 0.12f
    return DiagramRect(minX + ix, minY + iy, maxX - ix, maxY - iy)
}

/** A short perpendicular tick centred on [p], oriented across the segment p -> along. */
private fun DrawScope.drawTick(p: Offset, along: Offset, color: Color) {
    val d = along - p
    val len = hypot(d.x, d.y)
    if (len <= 0f) return
    val perp = Offset(-d.y / len, d.x / len) * 4.dp.toPx()
    drawLine(color, p - perp, p + perp, strokeWidth = 2.dp.toPx())
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color, stroke: Float) {
    val d = from - tip
    val len = hypot(d.x, d.y)
    if (len <= 0f) return
    val unit = Offset(d.x / len, d.y / len)
    val perp = Offset(-unit.y, unit.x)
    val size = 6.dp.toPx()
    val base = tip + unit * size
    drawLine(color, tip, base + perp * (size * 0.5f), strokeWidth = stroke)
    drawLine(color, tip, base - perp * (size * 0.5f), strokeWidth = stroke)
}

private fun DrawScope.drawDashedLine(start: Offset, end: Offset, color: Color, stroke: Float) {
    drawLine(
        color, start, end, strokeWidth = stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
    )
}

/** Intersection of line (p + s·dp) and line (q + t·dq) in unit space, or null when parallel. */
private fun lineIntersect(p: Offset, dp: Offset, q: Offset, dq: Offset): Offset? {
    val denom = dp.x * dq.y - dp.y * dq.x
    if (abs(denom) < 1e-9f) return null
    val dx = q.x - p.x
    val dy = q.y - p.y
    val s = (dx * dq.y - dy * dq.x) / denom
    return Offset(p.x + s * dp.x, p.y + s * dp.y)
}
