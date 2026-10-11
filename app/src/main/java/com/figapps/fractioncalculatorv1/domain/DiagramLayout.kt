package com.figapps.fractioncalculatorv1.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** A point in diagram (canvas) pixel space. Android-free so layout can be unit-tested. */
data class DiagramPoint(val x: Float, val y: Float)

/** A size in diagram pixel space. */
data class DiagramSize(val width: Float, val height: Float)

/** An axis-aligned rectangle in diagram pixel space (left/top is the min corner). */
data class DiagramRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    companion object {
        /** A rect from its min corner and a size. */
        fun from(left: Float, top: Float, width: Float, height: Float): DiagramRect =
            DiagramRect(left, top, left + width, top + height)
    }
}

/** Which side of its anchor a label prefers to sit on. */
enum class DiagramSide { RIGHT, LEFT, ABOVE, BELOW }

/** Primary labels carry the accent treatment; secondary labels are muted reference. */
enum class DiagramRole { PRIMARY, SECONDARY }

/**
 * Annotation layers, in descending importance. [BASICS] (geometry + the single primary result) is
 * always drawn; the other two are user-toggled.
 */
enum class AnnotationLayer { BASICS, DIMENSIONS, THEORY }

/** Which annotation layers are visible. Only the two extra layers are user-toggled. */
data class AnnotationVisibility(val dimensions: Boolean = true, val theory: Boolean = false) {
    fun shows(layer: AnnotationLayer): Boolean = when (layer) {
        AnnotationLayer.BASICS -> true
        AnnotationLayer.DIMENSIONS -> dimensions
        AnnotationLayer.THEORY -> theory
    }
}

/** A request to place one label. [anchor] is the geometry point it points at. */
data class LabelRequest(
    val key: String,
    val text: String,
    val anchor: DiagramPoint,
    val size: DiagramSize,
    val preferred: DiagramSide,
    val role: DiagramRole = DiagramRole.SECONDARY,
    val priority: Int = 100,
)

/** A label placed into the canvas: final box, the anchor it points at and where its leader starts. */
data class PlacedLabel(
    val key: String,
    val text: String,
    val role: DiagramRole,
    val box: DiagramRect,
    val anchor: DiagramPoint,
    val leaderStart: DiagramPoint,
)

/**
 * Pure, Android-free placement for diagram labels.
 *
 * Each diagram measures its labels and hands them here with a preferred side; this picks a final
 * box per label that (in order of priority) avoids other labels, avoids the geometry keep-out
 * rects, keeps leader lines off other labels, honours the preferred side, and stays inside the
 * content padding. All values are pixels so it is fully unit-testable.
 */
object DiagramLayout {

    /** Minimum edge-to-edge gap between two boxes, in dp. */
    const val GAP_DP = 8f

    /** Content margin the geometry and labels must stay inside, in dp. */
    const val MARGIN_DP = 16f

    private const val OVERLAP_WEIGHT = 10f
    private const val LEADER_CROSS_WEIGHT = 800f
    private const val SIDE_WEIGHT = 500f
    private const val DISTANCE_WEIGHT = 0.5f

    private data class Candidate(val box: DiagramRect, val leaderStart: DiagramPoint, val score: Float)

    /**
     * Places every request, most important first (lower [LabelRequest.priority] wins). Results are
     * returned in priority order and share the request keys.
     */
    fun placeLabels(
        requests: List<LabelRequest>,
        canvas: DiagramSize,
        obstacles: List<DiagramRect> = emptyList(),
        gap: Float = GAP_DP,
        margin: Float = MARGIN_DP,
    ): List<PlacedLabel> {
        val placed = ArrayList<PlacedLabel>(requests.size)
        val ordered = requests.sortedBy { it.priority }
        for (request in ordered) {
            var best: Candidate? = null
            for (side in sidesInOrder(request.preferred)) {
                val extent = if (side == DiagramSide.RIGHT || side == DiagramSide.LEFT) {
                    request.size.width
                } else {
                    request.size.height
                }
                // Two rings: tight against the anchor, and pushed out a full label so crowded
                // anchors can fan out into a second column/row instead of stacking.
                for (push in listOf(0f, extent + gap)) {
                    val box = clampInto(candidateBox(request.anchor, request.size, side, gap, push), canvas, margin)
                    val leader = leaderStart(request.anchor, box)
                    val score = scoreOf(request, box, leader, obstacles, placed, gap)
                    if (best == null || score < best.score) best = Candidate(box, leader, score)
                }
            }
            val chosen = best ?: Candidate(
                box = clampInto(candidateBox(request.anchor, request.size, request.preferred, gap), canvas, margin),
                leaderStart = request.anchor,
                score = 0f,
            )
            placed.add(
                PlacedLabel(
                    key = request.key,
                    text = request.text,
                    role = request.role,
                    box = chosen.box,
                    anchor = request.anchor,
                    leaderStart = chosen.leaderStart,
                )
            )
        }
        return placed
    }

    /** The preferred side first, then the others in enum order. */
    fun sidesInOrder(preferred: DiagramSide): List<DiagramSide> =
        listOf(preferred) + DiagramSide.entries.filter { it != preferred }

    /** A box of [size] offset from [anchor] on the given [side] by at least [gap], plus [push]. */
    fun candidateBox(
        anchor: DiagramPoint,
        size: DiagramSize,
        side: DiagramSide,
        gap: Float,
        push: Float = 0f,
    ): DiagramRect = when (side) {
        DiagramSide.RIGHT -> DiagramRect.from(anchor.x + gap + push, anchor.y - size.height / 2f, size.width, size.height)
        DiagramSide.LEFT -> DiagramRect.from(anchor.x - gap - push - size.width, anchor.y - size.height / 2f, size.width, size.height)
        DiagramSide.ABOVE -> DiagramRect.from(anchor.x - size.width / 2f, anchor.y - gap - push - size.height, size.width, size.height)
        DiagramSide.BELOW -> DiagramRect.from(anchor.x - size.width / 2f, anchor.y + gap + push, size.width, size.height)
    }

    /** Slides [rect] so it sits inside the padded canvas, shrinking the shift if the rect is too big. */
    fun clampInto(rect: DiagramRect, canvas: DiagramSize, margin: Float): DiagramRect {
        val maxX = (canvas.width - margin - rect.width).coerceAtLeast(margin)
        val maxY = (canvas.height - margin - rect.height).coerceAtLeast(margin)
        val left = rect.left.coerceIn(margin, maxX)
        val top = rect.top.coerceIn(margin, maxY)
        return DiagramRect.from(left, top, rect.width, rect.height)
    }

    /** The point on [box]'s perimeter that faces [anchor] — where its leader line starts. */
    fun leaderStart(anchor: DiagramPoint, box: DiagramRect): DiagramPoint {
        val dx = anchor.x - box.centerX
        val dy = anchor.y - box.centerY
        if (dx == 0f && dy == 0f) return DiagramPoint(box.centerX, box.centerY)
        val halfW = box.width / 2f
        val halfH = box.height / 2f
        val sx = if (dx != 0f) halfW / abs(dx) else Float.MAX_VALUE
        val sy = if (dy != 0f) halfH / abs(dy) else Float.MAX_VALUE
        val s = min(sx, sy)
        return DiagramPoint(box.centerX + dx * s, box.centerY + dy * s)
    }

    /** Area of [a] ∩ [b], in px². Zero when they do not overlap. */
    fun intersectionArea(a: DiagramRect, b: DiagramRect): Float {
        val w = min(a.right, b.right) - maxOf(a.left, b.left)
        val h = min(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    /** Area of [a] ∩ ([b] grown by [gap]) — i.e. penalises boxes closer than the gap. */
    fun overlapArea(a: DiagramRect, b: DiagramRect, gap: Float): Float {
        val grown = DiagramRect(b.left - gap, b.top - gap, b.right + gap, b.bottom + gap)
        return intersectionArea(a, grown)
    }

    /** Standard segment-segment intersection test (including touching). */
    fun segmentsIntersect(p1: DiagramPoint, p2: DiagramPoint, q1: DiagramPoint, q2: DiagramPoint): Boolean {
        val d1 = cross(q1, q2, p1)
        val d2 = cross(q1, q2, p2)
        val d3 = cross(p1, p2, q1)
        val d4 = cross(p1, p2, q2)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        return (d1 == 0f && onSegment(q1, q2, p1)) ||
            (d2 == 0f && onSegment(q1, q2, p2)) ||
            (d3 == 0f && onSegment(p1, p2, q1)) ||
            (d4 == 0f && onSegment(p1, p2, q2))
    }

    /** True when the segment [a]–[b] crosses (or touches) [rect]. */
    fun segmentIntersectsRect(a: DiagramPoint, b: DiagramPoint, rect: DiagramRect): Boolean {
        if (a.x in rect.left..rect.right && a.y in rect.top..rect.bottom) return true
        if (b.x in rect.left..rect.right && b.y in rect.top..rect.bottom) return true
        val tl = DiagramPoint(rect.left, rect.top)
        val tr = DiagramPoint(rect.right, rect.top)
        val br = DiagramPoint(rect.right, rect.bottom)
        val bl = DiagramPoint(rect.left, rect.bottom)
        return segmentsIntersect(a, b, tl, tr) ||
            segmentsIntersect(a, b, tr, br) ||
            segmentsIntersect(a, b, br, bl) ||
            segmentsIntersect(a, b, bl, tl)
    }

    private fun scoreOf(
        request: LabelRequest,
        box: DiagramRect,
        leader: DiagramPoint,
        obstacles: List<DiagramRect>,
        placed: List<PlacedLabel>,
        gap: Float,
    ): Float {
        var score = 0f
        for (obstacle in obstacles) score += overlapArea(box, obstacle, gap) * OVERLAP_WEIGHT
        for (other in placed) {
            score += overlapArea(box, other.box, gap) * OVERLAP_WEIGHT
            if (segmentIntersectsRect(leader, request.anchor, other.box)) score += LEADER_CROSS_WEIGHT
            if (segmentsIntersect(leader, request.anchor, other.leaderStart, other.anchor)) {
                score += LEADER_CROSS_WEIGHT
            }
        }
        score += sideRank(request.preferred, sideOf(request, box)) * SIDE_WEIGHT
        score += hypot(box.centerX - request.anchor.x, box.centerY - request.anchor.y) * DISTANCE_WEIGHT
        return score
    }

    /** Which side of the anchor the placed box ended up on (for the side penalty). */
    private fun sideOf(request: LabelRequest, box: DiagramRect): DiagramSide {
        val dx = box.centerX - request.anchor.x
        val dy = box.centerY - request.anchor.y
        return if (abs(dx) >= abs(dy)) {
            if (dx >= 0f) DiagramSide.RIGHT else DiagramSide.LEFT
        } else {
            if (dy >= 0f) DiagramSide.BELOW else DiagramSide.ABOVE
        }
    }

    private fun sideRank(preferred: DiagramSide, actual: DiagramSide): Int = when {
        preferred == actual -> 0
        (preferred == DiagramSide.RIGHT && actual == DiagramSide.LEFT) ||
            (preferred == DiagramSide.LEFT && actual == DiagramSide.RIGHT) ||
            (preferred == DiagramSide.ABOVE && actual == DiagramSide.BELOW) ||
            (preferred == DiagramSide.BELOW && actual == DiagramSide.ABOVE) -> 2
        else -> 1
    }

    private fun cross(a: DiagramPoint, b: DiagramPoint, c: DiagramPoint): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun onSegment(a: DiagramPoint, b: DiagramPoint, c: DiagramPoint): Boolean =
        c.x in min(a.x, b.x)..maxOf(a.x, b.x) && c.y in min(a.y, b.y)..maxOf(a.y, b.y)
}
