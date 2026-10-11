package com.figapps.fractioncalculatorv1

import com.figapps.fractioncalculatorv1.domain.DiagramLayout
import com.figapps.fractioncalculatorv1.domain.DiagramPoint
import com.figapps.fractioncalculatorv1.domain.DiagramRect
import com.figapps.fractioncalculatorv1.domain.DiagramRole
import com.figapps.fractioncalculatorv1.domain.DiagramSide
import com.figapps.fractioncalculatorv1.domain.DiagramSize
import com.figapps.fractioncalculatorv1.domain.LabelRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure layout maths backing the shared diagram annotations: candidate boxes, clamping, overlap
 * detection, segment/rect intersection and the greedy label placer.
 */
class DiagramLayoutTest {

    private val canvas = DiagramSize(width = 400f, height = 400f)
    private val gap = DiagramLayout.GAP_DP
    private val margin = DiagramLayout.MARGIN_DP

    private fun request(
        key: String,
        x: Float,
        y: Float,
        w: Float = 80f,
        h: Float = 24f,
        side: DiagramSide = DiagramSide.RIGHT,
        role: DiagramRole = DiagramRole.SECONDARY,
        priority: Int = 100,
    ) = LabelRequest(key, key, DiagramPoint(x, y), DiagramSize(w, h), side, role, priority)

    // --- candidate boxes -------------------------------------------------------------------------

    @Test
    fun candidateBox_sitsOnRequestedSide() {
        val anchor = DiagramPoint(200f, 200f)
        val size = DiagramSize(80f, 24f)
        val right = DiagramLayout.candidateBox(anchor, size, DiagramSide.RIGHT, gap)
        assertEquals(200f + gap, right.left, 1e-3f)
        assertEquals(200f, right.centerY, 1e-3f)

        val above = DiagramLayout.candidateBox(anchor, size, DiagramSide.ABOVE, gap)
        assertEquals(200f - gap, above.bottom, 1e-3f)
        assertEquals(200f, above.centerX, 1e-3f)
    }

    // --- clamping --------------------------------------------------------------------------------

    @Test
    fun clampInto_pullsBoxInsidePadding() {
        val rect = DiagramRect.from(-50f, -50f, 30f, 10f)
        val clamped = DiagramLayout.clampInto(rect, canvas, margin)
        assertEquals(margin, clamped.left, 1e-3f)
        assertEquals(margin, clamped.top, 1e-3f)
        assertEquals(30f, clamped.width, 1e-3f)
    }

    @Test
    fun clampInto_pinsOversizedBoxToMargin() {
        val rect = DiagramRect.from(0f, 0f, canvas.width + 100f, 20f)
        val clamped = DiagramLayout.clampInto(rect, canvas, margin)
        assertEquals(margin, clamped.left, 1e-3f)
    }

    // --- overlap detection -----------------------------------------------------------------------

    @Test
    fun overlapArea_zeroOnceBoxesAreAGapApart() {
        val a = DiagramRect.from(0f, 0f, 50f, 20f)
        val b = DiagramRect.from(58f, 0f, 50f, 20f) // exactly `gap` (8) away
        assertEquals(0f, DiagramLayout.overlapArea(a, b, gap), 1e-3f)
        val closer = DiagramRect.from(50f, 0f, 50f, 20f) // touching
        assertTrue(DiagramLayout.overlapArea(a, closer, gap) > 0f)
    }

    @Test
    fun intersectionArea_computesExactOverlap() {
        val a = DiagramRect.from(0f, 0f, 20f, 20f)
        val b = DiagramRect.from(10f, 10f, 20f, 20f)
        assertEquals(100f, DiagramLayout.intersectionArea(a, b), 1e-3f)
        assertFalse(DiagramLayout.intersectionArea(a, DiagramRect.from(50f, 50f, 5f, 5f)) > 0f)
    }

    // --- segments --------------------------------------------------------------------------------

    @Test
    fun segmentsIntersect_detectsACrossing() {
        assertTrue(
            DiagramLayout.segmentsIntersect(
                DiagramPoint(0f, 0f), DiagramPoint(10f, 10f),
                DiagramPoint(0f, 10f), DiagramPoint(10f, 0f),
            )
        )
        assertFalse(
            DiagramLayout.segmentsIntersect(
                DiagramPoint(0f, 0f), DiagramPoint(10f, 0f),
                DiagramPoint(0f, 5f), DiagramPoint(10f, 5f),
            )
        )
    }

    @Test
    fun segmentIntersectsRect_detectsAPassThrough() {
        val rect = DiagramRect.from(40f, 40f, 20f, 20f)
        assertTrue(DiagramLayout.segmentIntersectsRect(DiagramPoint(0f, 50f), DiagramPoint(100f, 50f), rect))
        assertFalse(DiagramLayout.segmentIntersectsRect(DiagramPoint(0f, 0f), DiagramPoint(10f, 10f), rect))
    }

    // --- leader attachment -----------------------------------------------------------------------

    @Test
    fun leaderStart_isOnTheEdgeFacingTheAnchor() {
        val box = DiagramRect.from(100f, 100f, 80f, 24f)
        val left = DiagramLayout.leaderStart(DiagramPoint(0f, 112f), box)
        assertEquals(100f, left.x, 1e-3f)
        val top = DiagramLayout.leaderStart(DiagramPoint(140f, 0f), box)
        assertEquals(100f, top.y, 1e-3f)
    }

    // --- the placer ------------------------------------------------------------------------------

    @Test
    fun placeLabels_keepsEveryBoxInsideThePadding() {
        val requests = listOf(
            request("a", 10f, 10f),
            request("b", 390f, 390f),
            request("c", 200f, 5f),
            request("d", 5f, 200f),
        )
        val placed = DiagramLayout.placeLabels(requests, canvas, gap = gap, margin = margin)
        for (label in placed) {
            assertTrue("${label.key} left", label.box.left >= margin - 1e-3f)
            assertTrue("${label.key} top", label.box.top >= margin - 1e-3f)
            assertTrue("${label.key} right", label.box.right <= canvas.width - margin + 1e-3f)
            assertTrue("${label.key} bottom", label.box.bottom <= canvas.height - margin + 1e-3f)
        }
    }

    @Test
    fun placeLabels_separatesCrowdedLabelsByTheGap() {
        // Four labels stacked on the same anchor: the placer must fan them out, not pile them up.
        val requests = (0 until 4).map { request("l$it", 200f, 200f) }
        val placed = DiagramLayout.placeLabels(requests, canvas, gap = gap, margin = margin)
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                assertEquals(
                    "labels ${placed[i].key}/${placed[j].key} must not overlap",
                    0f,
                    DiagramLayout.intersectionArea(placed[i].box, placed[j].box),
                    1e-3f,
                )
            }
        }
    }

    @Test
    fun placeLabels_honoursPreferredSideWhenSpaceAllows() {
        val requests = listOf(request("solo", 200f, 200f, side = DiagramSide.RIGHT))
        val placed = DiagramLayout.placeLabels(requests, canvas, gap = gap, margin = margin)
        assertEquals(1, placed.size)
        assertTrue("expected the label to the right of its anchor", placed[0].box.left >= 200f)
    }

    @Test
    fun placeLabels_routesLeadersOffOtherBoxes() {
        val requests = listOf(
            request("a", 200f, 60f, priority = 0, role = DiagramRole.PRIMARY),
            request("b", 200f, 120f),
            request("c", 200f, 280f),
            request("d", 200f, 340f),
        )
        val placed = DiagramLayout.placeLabels(requests, canvas, gap = gap, margin = margin)
        for (label in placed) {
            for (other in placed) {
                if (other.key == label.key) continue
                assertFalse(
                    "leader for ${label.key} must not cross ${other.key}",
                    DiagramLayout.segmentIntersectsRect(label.leaderStart, label.anchor, other.box),
                )
            }
        }
    }

    @Test
    fun placeLabels_avoidsGeometryObstacles() {
        val obstacle = DiagramRect.from(150f, 150f, 100f, 100f)
        val requests = listOf(request("x", 200f, 200f))
        val placed = DiagramLayout.placeLabels(listOf(requests[0]), canvas, obstacles = listOf(obstacle), gap = gap, margin = margin)
        assertEquals(0f, DiagramLayout.intersectionArea(placed[0].box, obstacle), 1e-3f)
    }
}
