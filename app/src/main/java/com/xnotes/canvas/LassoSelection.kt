package com.xnotes.canvas

import com.xnotes.core.geometry.Geometry
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.ConnectorItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.stroke.StrokeClipper

/**
 * Split details for a single item divided by a pixel-perfect lasso boundary.
 */
data class ExactLassoSplit(
    val original: CanvasItem,
    val inside: List<CanvasItem>,
    val outside: List<CanvasItem>,
)

/**
 * Result of exact lasso selection over a flat list of items.
 */
data class ExactLassoResult(
    val selected: List<CanvasItem>,
    val splits: List<ExactLassoSplit>,
)

/**
 * Split details for an item on a specific page.
 */
data class SelectedSplit(
    val pageIndex: Int,
    val original: CanvasItem,
    val inside: List<CanvasItem>,
    val outside: List<CanvasItem>,
)

/**
 * Result of exact lasso selection across multiple pages.
 */
data class PagedExactLassoResult(
    val selected: List<Selected>,
    val splits: List<SelectedSplit>,
)

/**
 * Pure selection and splitting logic for pixel-perfect (exact) lasso selection.
 */
object LassoSelection {

    /**
     * Exact lasso selection over a flat list of items.
     *
     * - Entirely inside strokes and shapes are selected whole without splitting.
     * - Entirely outside items are untouched.
     * - Crossing strokes and stroked shapes are split: inside sub-strokes are selected, outside sub-strokes remain unselected.
     * - Non-splittable items (e.g. text/glyphs, image, fills) meeting the lasso polygon are selected whole.
     */
    fun selectExact(items: List<CanvasItem>, polygon: List<Pt>): ExactLassoResult {
        if (polygon.size < 3) return ExactLassoResult(emptyList(), emptyList())
        val selected = ArrayList<CanvasItem>()
        val splits = ArrayList<ExactLassoSplit>()
        val candidates = SelectionMath.lassoMembers(items, polygon)
        val candidateSet = candidates.toSet()

        for (item in items) {
            if (item.locked || item !in candidateSet) continue

            if (item is ShapeItem && item.shape == ShapeKind.LINE) {
                val clip = clipStraightLine(item, polygon)
                if (clip != null) {
                    val (insideLines, outsideLines) = clip
                    if (insideLines.isNotEmpty() && outsideLines.isNotEmpty()) {
                        selected.addAll(insideLines)
                        splits.add(ExactLassoSplit(item, insideLines, outsideLines))
                    } else if (insideLines.isNotEmpty()) {
                        selected.add(item)
                    }
                    continue
                }
            }

            val strokes = when {
                item is Stroke -> listOf(item)
                item is ShapeItem && !item.isFill && item.shape != ShapeKind.GLYPHS -> item.toStrokes()
                item is ConnectorItem -> item.toStrokes()
                else -> null
            }

            if (strokes.isNullOrEmpty()) {
                selected.add(item)
                continue
            }

            val insideStrokes = ArrayList<Stroke>()
            val outsideStrokes = ArrayList<Stroke>()
            var anySplit = false

            for (stroke in strokes) {
                val clip = StrokeClipper.clipStroke(stroke, polygon)
                if (clip.split) {
                    anySplit = true
                }
                insideStrokes.addAll(clip.inside)
                outsideStrokes.addAll(clip.outside)
            }

            if (anySplit || (insideStrokes.isNotEmpty() && outsideStrokes.isNotEmpty())) {
                selected.addAll(insideStrokes)
                splits.add(ExactLassoSplit(item, insideStrokes, outsideStrokes))
            } else if (insideStrokes.isNotEmpty()) {
                selected.add(item)
            }
        }
        return ExactLassoResult(selected, splits)
    }

    /**
     * Clips a straight [line] against [polygon] with exact sub-pixel boundary intersections,
     * returning inside and outside [ShapeItem] line fragments.
     */
    private fun clipStraightLine(
        line: ShapeItem,
        polygon: List<Pt>,
    ): Pair<List<ShapeItem>, List<ShapeItem>>? {
        val a = line.start
        val b = line.end
        val segLen = a.distanceTo(b)
        if (segLen < 1e-6) return null

        val ts = ArrayList<Double>()
        val n = polygon.size
        val vx = b.x - a.x
        val vy = b.y - a.y

        for (j in 0 until n) {
            val c = polygon[j]
            val d = polygon[(j + 1) % n]
            val wx = d.x - c.x
            val wy = d.y - c.y
            val denom = vx * wy - vy * wx
            if (kotlin.math.abs(denom) < 1e-11) continue

            val dx = c.x - a.x
            val dy = c.y - a.y
            val t = (dx * wy - dy * wx) / denom
            val u = (dx * vy - dy * vx) / denom

            if (t in 1e-6..(1.0 - 1e-6) && u in -1e-5..(1.0 + 1e-5)) {
                ts.add(t)
            }
        }

        if (ts.isEmpty()) {
            val mid = Pt((a.x + b.x) * 0.5, (a.y + b.y) * 0.5)
            val inside = Geometry.pointInPolygon(polygon, mid)
            return if (inside) {
                listOf(line) to emptyList()
            } else {
                emptyList<ShapeItem>() to listOf(line)
            }
        }

        ts.sort()
        val minGapT = (1e-4 / segLen).coerceAtLeast(1e-6)
        val filtered = ArrayList<Double>(ts.size)
        var lastT = -1.0
        for (t in ts) {
            if (lastT < 0.0 || (t - lastT) >= minGapT) {
                filtered.add(t)
                lastT = t
            }
        }

        val allT = ArrayList<Double>(filtered.size + 2)
        allT.add(0.0)
        allT.addAll(filtered)
        allT.add(1.0)

        val inside = ArrayList<ShapeItem>()
        val outside = ArrayList<ShapeItem>()

        for (k in 0 until allT.size - 1) {
            val t0 = allT[k]
            val t1 = allT[k + 1]
            val p0 = Pt(a.x + t0 * vx, a.y + t0 * vy)
            val p1 = Pt(a.x + t1 * vx, a.y + t1 * vy)
            val mid = Pt((p0.x + p1.x) * 0.5, (p0.y + p1.y) * 0.5)
            val isInside = Geometry.pointInPolygon(polygon, mid)

            val fragment = ShapeItem(
                shape = ShapeKind.LINE,
                start = p0,
                end = p1,
                strokeRgba = line.strokeRgba,
                strokeWidth = line.strokeWidth,
                fillRgba = line.fillRgba,
                neon = line.neon,
                neonStrength = line.neonStrength,
                dashed = line.dashed,
                dashLength = line.dashLength,
                dashGap = line.dashGap,
            )

            if (isInside) {
                inside.add(fragment)
            } else {
                outside.add(fragment)
            }
        }

        return inside to outside
    }

    /**
     * Exact lasso selection across multiple pages.
     */
    fun selectExact(
        pages: List<Page>,
        pageRects: List<Rect>,
        polygon: List<Pt>,
        toPage: (Int, Pt) -> Pt = { i, p -> Pt(p.x - pageRects[i].left, p.y - pageRects[i].top) },
    ): PagedExactLassoResult {
        if (polygon.size < 3) return PagedExactLassoResult(emptyList(), emptyList())
        val reach = Rect.bounding(polygon)
        val selected = ArrayList<Selected>()
        val splits = ArrayList<SelectedSplit>()

        for (i in pages.indices) {
            val pr = pageRects.getOrNull(i) ?: continue
            if (!pr.outset(SelectionMath.OFF_PAGE_SLACK).intersects(reach)) continue
            val localPolygon = polygon.map { toPage(i, it) }
            val pageResult = selectExact(pages[i].items, localPolygon)

            for (item in pageResult.selected) {
                selected.add(Selected(i, item))
            }
            for (split in pageResult.splits) {
                splits.add(SelectedSplit(i, split.original, split.inside, split.outside))
            }
        }
        return PagedExactLassoResult(selected, splits)
    }
}
