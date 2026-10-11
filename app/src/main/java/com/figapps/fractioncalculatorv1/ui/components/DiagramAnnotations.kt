package com.figapps.fractioncalculatorv1.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.figapps.fractioncalculatorv1.domain.AnnotationVisibility
import com.figapps.fractioncalculatorv1.domain.DiagramPoint
import com.figapps.fractioncalculatorv1.domain.DiagramRole
import com.figapps.fractioncalculatorv1.domain.DiagramSide
import com.figapps.fractioncalculatorv1.domain.DiagramSize
import com.figapps.fractioncalculatorv1.domain.LabelRequest
import com.figapps.fractioncalculatorv1.domain.PlacedLabel
import com.figapps.fractioncalculatorv1.ui.CalcTheme
import kotlin.math.hypot

/**
 * Shared annotation vocabulary for every diagram. Two label roles only — primary (the tool's result)
 * and secondary (reference) — so all four diagrams inherit the same type scale, and no text is
 * smaller than 11sp.
 */
object DiagramChip {
    val PAD_H = 6.dp
    val PAD_V = 4.dp
    val CORNER = 6.dp

    /** Primary (result) label text. */
    fun primaryText(on: Color) = TextStyle(color = on, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

    /** Secondary (reference) label text — the smallest text allowed anywhere in a diagram. */
    fun secondaryText(on: Color) = TextStyle(color = on, fontSize = 11.sp)
}

/** Resolved colours for the two label roles plus the leader lines. */
@Immutable
data class DiagramLabelTheme(
    val primaryText: TextStyle,
    val primaryFill: Color,
    val primaryBorder: Color,
    val secondaryText: TextStyle,
    val secondaryFill: Color,
    val secondaryBorder: Color,
    val leader: Color,
)

/** Builds the shared label theme from the active calculator palette. */
@Composable
fun rememberDiagramLabelTheme(): DiagramLabelTheme {
    val colors = CalcTheme.colors
    return remember(colors) {
        DiagramLabelTheme(
            primaryText = DiagramChip.primaryText(colors.accentKeyText),
            primaryFill = colors.accentKey,
            primaryBorder = colors.accentKey,
            secondaryText = DiagramChip.secondaryText(colors.numberKeyText),
            secondaryFill = colors.cardBackground,
            secondaryBorder = colors.divider,
            leader = colors.numberKeyText.copy(alpha = 0.55f),
        )
    }
}

/**
 * Measures [text] and wraps it in a [LabelRequest] whose box matches exactly what
 * [drawDiagramLabels] will paint, so placement and drawing agree on size.
 */
fun DrawScope.measureLabelRequest(
    textMeasurer: TextMeasurer,
    theme: DiagramLabelTheme,
    key: String,
    text: String,
    anchor: Offset,
    preferred: DiagramSide,
    role: DiagramRole = DiagramRole.SECONDARY,
    priority: Int = 100,
): LabelRequest {
    val style = if (role == DiagramRole.PRIMARY) theme.primaryText else theme.secondaryText
    val layout = textMeasurer.measure(AnnotatedString(text), style)
    val padH = DiagramChip.PAD_H.toPx()
    val padV = DiagramChip.PAD_V.toPx()
    return LabelRequest(
        key = key,
        text = text,
        anchor = DiagramPoint(anchor.x, anchor.y),
        size = DiagramSize(layout.size.width + padH * 2, layout.size.height + padV * 2),
        preferred = preferred,
        role = role,
        priority = priority,
    )
}

/**
 * Draws each placed label's leader line and its chip.
 *
 * [tintOf] optionally returns a colour for a secondary label key; when present it is used for that
 * label's border, text and leader line so a chip keeps the colour association with the piece it
 * annotates even after the placer moves it away from the geometry. Primary labels are never tinted.
 */
fun DrawScope.drawDiagramLabels(
    textMeasurer: TextMeasurer,
    theme: DiagramLabelTheme,
    labels: List<PlacedLabel>,
    tintOf: (String) -> Color? = { null },
) {
    val padH = DiagramChip.PAD_H.toPx()
    val padV = DiagramChip.PAD_V.toPx()
    val corner = CornerRadius(DiagramChip.CORNER.toPx())
    for (label in labels) {
        val primary = label.role == DiagramRole.PRIMARY
        val tint = if (primary) null else tintOf(label.key)

        val from = Offset(label.leaderStart.x, label.leaderStart.y)
        val to = Offset(label.anchor.x, label.anchor.y)
        if (hypot(to.x - from.x, to.y - from.y) > 2f) {
            drawLine(tint ?: theme.leader, from, to, strokeWidth = 1.dp.toPx())
        }

        val style = when {
            primary -> theme.primaryText
            tint != null -> theme.secondaryText.copy(color = tint)
            else -> theme.secondaryText
        }
        val fill = if (primary) theme.primaryFill else theme.secondaryFill
        val border = when {
            primary -> theme.primaryBorder
            tint != null -> tint
            else -> theme.secondaryBorder
        }

        val layout = textMeasurer.measure(AnnotatedString(label.text), style)
        val topLeft = Offset(label.box.left, label.box.top)
        val boxSize = Size(label.box.width, label.box.height)
        drawRoundRect(fill, topLeft, boxSize, corner)
        drawRoundRect(border, topLeft, boxSize, corner, style = Stroke(1.dp.toPx()))
        drawText(layout, topLeft = Offset(label.box.left + padH, label.box.top + padV))
    }
}

/**
 * A small row of layer chips above a diagram. "Basics" is always on (shown locked); "Dimensions"
 * and "Theory" are the two user-toggled layers.
 */
@Composable
fun AnnotationLayerToggles(
    visibility: AnnotationVisibility,
    onDimensionsChange: (Boolean) -> Unit,
    onTheoryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val colors = CalcTheme.colors
    val tick = rememberHapticTick(hapticsEnabled)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Annotation layers",
            color = colors.numberKeyText.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Basics is not a toggle — it is always drawn. A static, accent-tinted pill says so
            // without pretending to be a disabled control.
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.accentKey.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, colors.accentKey.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "Basics \u00B7 always on",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = colors.accentKey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            FilterChip(
                selected = visibility.dimensions,
                onClick = { tick(); onDimensionsChange(!visibility.dimensions) },
                label = { Text("Dimensions") }
            )
            FilterChip(
                selected = visibility.theory,
                onClick = { tick(); onTheoryChange(!visibility.theory) },
                label = { Text("Theory") }
            )
        }
    }
}

/** A small unobtrusive footnote under a diagram (replaces on-canvas note chips). */
@Composable
fun DiagramCaption(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = CalcTheme.colors.numberKeyText.copy(alpha = 0.55f),
        fontSize = 11.sp
    )
}

/** A no-arg haptic tick honouring the global haptics setting, matching the other tools. */
@Composable
fun rememberHapticTick(enabled: Boolean): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(enabled, haptics) {
        { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}
