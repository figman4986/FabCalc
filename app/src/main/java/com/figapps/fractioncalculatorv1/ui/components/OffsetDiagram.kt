package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.figapps.fractioncalculatorv1.domain.AnnotationLayer
import com.figapps.fractioncalculatorv1.domain.AnnotationVisibility
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * End-on cross-section of two round pieces with the fit-up gap highlighted.
 *
 * The two pieces are told apart by *colour*, not by reading the labels: the large tube uses the warm
 * `piecePrimary` role and the small tube the cool `pieceSecondary` role, a hue-contrasted,
 * colour-blind-safe pair. Both the muted fills and the saturated strokes carry the
 * distinction, and each piece's diameter chips and leaders are tinted to match so the association
 * survives when a chip floats into the margin. The gap — the primary result — stays on the accent
 * colour and is the most saturated thing on screen, keeping the two pieces subordinate to it.
 *
 * Layers: BASICS = pieces + gap + gap callout; DIMENSIONS (default ON) = the two diameter callouts;
 * THEORY (default OFF) = the zoom inset. A tiny gap is drawn exaggerated; that is a footnote.
 */
@Composable
fun OffsetDiagram(
    largeDiameter: Fraction,
    smallDiameter: Fraction,
    radialOffset: Fraction,
    unitSymbol: String,
    modifier: Modifier = Modifier,
    visibility: AnnotationVisibility = AnnotationVisibility(),
) {
    val colors = CalcTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelTheme = rememberDiagramLabelTheme()

    val outline = colors.divider
    val accent = colors.accentKey
    val inkSoft = colors.numberKeyText.copy(alpha = 0.6f)

    val fillAlpha = 0.30f
    val piecePrimaryFill = colors.piecePrimary.copy(alpha = fillAlpha)
    val pieceSecondaryFill = colors.pieceSecondary.copy(alpha = fillAlpha)
    val piecePrimaryLine = colors.piecePrimary
    val pieceSecondaryLine = colors.pieceSecondary
    val piecePrimaryLabel = colors.piecePrimaryLabel
    val pieceSecondaryLabel = colors.pieceSecondaryLabel

    val largeText = "\u00D8 ${ResultFormat.decimalText(largeDiameter, 3)} $unitSymbol"
    val smallText = "\u00D8 ${ResultFormat.decimalText(smallDiameter, 3)} $unitSymbol"
    val gapText = "gap ${ResultFormat.decimalText(radialOffset, 3)} $unitSymbol"

    val largeDouble = largeDiameter.toDoubleValue()
    val smallDouble = smallDiameter.toDoubleValue()
    val exaggerated = largeDouble > 0.0 && (smallDouble / largeDouble) > 0.985
    val caption = if (exaggerated) "Fit-up gap shown at exaggerated scale \u2014 not to scale." else null

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val center = Offset(cx, cy)

            val largeR = min(w, h) * 0.28f
            val ratio = if (largeDouble <= 0.0) 1.0 else (smallDouble / largeDouble).coerceIn(0.0, 1.0)
            val trueSmallR = (largeR * ratio).toFloat()

            val minRing = 15.dp.toPx()
            val isExaggerated = (largeR - trueSmallR) < minRing
            val smallR = if (isExaggerated) (largeR - minRing).coerceAtLeast(largeR * 0.35f) else trueSmallR
            val annulus = (largeR - smallR).coerceAtLeast(0f)
            val midR = (largeR + smallR) / 2f

            // The two circles are the fit surfaces: the inner piece's material is inside its outer
            // surface (smallR) and the outer piece's material lies *outside* its bore (largeR). The
            // wall band is schematic material (~14% of the bore radius), NOT a computed value.
            val wallBand = largeR * 0.14f
            val outerR = largeR + wallBand

            // Fill order: large-piece wall, gap ring (full annulus, unchanged), small-piece disc.
            drawCircle(piecePrimaryFill, outerR, center)
            if (annulus > 0f) drawCircle(accent, midR, center, style = Stroke(width = annulus))
            drawCircle(pieceSecondaryFill, smallR, center)
            // strokes carry the hue (weight alone would not survive thinning)
            drawCircle(piecePrimaryLine, largeR, center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(piecePrimaryLine, outerR, center, style = Stroke(width = 2.dp.toPx()))
            drawCircle(pieceSecondaryLine, smallR, center, style = Stroke(width = 2.dp.toPx()))

            drawCentreCross(center, inkSoft.copy(alpha = 0.35f))

            // --- labels ---
            val largeAnchor = Offset(cx + largeR, cy)
            val smallAnchor = Offset(cx, cy + smallR)
            val gapAngle = PI * 0.75
            val gapAnchor = center + Offset(cos(gapAngle).toFloat(), sin(gapAngle).toFloat()) * midR

            if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                // Dimension lines take their piece's colour so the association holds.
                drawDimensionLine(Offset(cx - largeR, cy), Offset(cx, cy), piecePrimaryLabel, 1.2.dp.toPx())
                drawDimensionLine(Offset(cx, cy), Offset(cx + largeR, cy), piecePrimaryLabel, 1.2.dp.toPx())
                drawDimensionLine(Offset(cx, cy), Offset(cx, cy + smallR), pieceSecondaryLabel, 1.2.dp.toPx())
            }

            if (visibility.shows(AnnotationLayer.THEORY)) {
                drawMagnifier(
                    center = Offset(w - (DiagramLayout.MARGIN_DP + 34f).dp.toPx(), h - (DiagramLayout.MARGIN_DP + 34f).dp.toPx()),
                    radius = 34.dp.toPx(),
                    source = center + Offset(cos(-PI / 4).toFloat(), sin(-PI / 4).toFloat()) * midR,
                    background = colors.cardBackground,
                    outerMetal = piecePrimaryFill,
                    gapColor = accent,
                    innerMetal = pieceSecondaryFill,
                    outline = outline
                )
            }

            val requests = buildList {
                add(
                    measureLabelRequest(
                        textMeasurer, labelTheme, "gap", gapText, gapAnchor,
                        DiagramSide.LEFT, DiagramRole.PRIMARY, priority = 0
                    )
                )
                if (visibility.shows(AnnotationLayer.DIMENSIONS)) {
                    add(measureLabelRequest(textMeasurer, labelTheme, "od", largeText, largeAnchor, DiagramSide.RIGHT, priority = 10))
                    add(measureLabelRequest(textMeasurer, labelTheme, "id", smallText, smallAnchor, DiagramSide.BELOW, priority = 20))
                }
            }
            val placed = DiagramLayout.placeLabels(
                requests = requests,
                canvas = DiagramSize(w, h),
                obstacles = listOf(DiagramRect(cx - outerR, cy - outerR, cx + outerR, cy + outerR)),
                gap = DiagramLayout.GAP_DP.dp.toPx(),
                margin = DiagramLayout.MARGIN_DP.dp.toPx(),
            )
            drawDiagramLabels(
                textMeasurer = textMeasurer,
                theme = labelTheme,
                labels = placed,
                tintOf = { key ->
                    when (key) {
                        "od" -> piecePrimaryLabel
                        "id" -> pieceSecondaryLabel
                        else -> null
                    }
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        DiagramCaption(caption)
    }
}

private fun DrawScope.drawCentreCross(center: Offset, color: Color) {
    val r = 5.dp.toPx()
    drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), strokeWidth = 1.dp.toPx())
    drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), strokeWidth = 1.dp.toPx())
}

/** A dimension line with arrowheads at both ends. */
private fun DrawScope.drawDimensionLine(start: Offset, end: Offset, color: Color, stroke: Float) {
    drawLine(color, start, end, strokeWidth = stroke)
    val delta = end - start
    val length = hypot(delta.x, delta.y)
    if (length <= 0f) return
    val unit = Offset(delta.x / length, delta.y / length)
    drawArrowHead(start, Offset(-unit.x, -unit.y), color, stroke)
    drawArrowHead(end, unit, color, stroke)
}

private fun DrawScope.drawArrowHead(tip: Offset, outward: Offset, color: Color, stroke: Float) {
    val size = 6.dp.toPx()
    val perp = Offset(-outward.y, outward.x)
    val base = tip - outward * size
    drawLine(color, tip, base + perp * (size * 0.55f), strokeWidth = stroke)
    drawLine(color, tip, base - perp * (size * 0.55f), strokeWidth = stroke)
}

/** A zoomed, circular inset showing a slice of the ring: outer metal, the gap, inner metal. */
private fun DrawScope.drawMagnifier(
    center: Offset,
    radius: Float,
    source: Offset,
    background: Color,
    outerMetal: Color,
    gapColor: Color,
    innerMetal: Color,
    outline: Color,
) {
    val toMag = center - source
    val len = hypot(toMag.x, toMag.y)
    if (len > 0f) {
        val perp = Offset(-toMag.y / len, toMag.x / len)
        drawLine(outline, source, center - perp * (radius * 0.7f), strokeWidth = 1.dp.toPx())
        drawLine(outline, source, center + perp * (radius * 0.7f), strokeWidth = 1.dp.toPx())
    }

    val oval = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    clipPath(Path().apply { addOval(oval) }) {
        val gapBand = radius * 0.36f
        drawRect(background, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2))
        drawRect(outerMetal, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius - gapBand / 2))
        drawRect(innerMetal, topLeft = Offset(center.x - radius, center.y + gapBand / 2), size = Size(radius * 2, radius - gapBand / 2))
        drawRect(gapColor, topLeft = Offset(center.x - radius, center.y - gapBand / 2), size = Size(radius * 2, gapBand))
    }
    drawCircle(outline, radius, center, style = Stroke(width = 1.5.dp.toPx()))
}
