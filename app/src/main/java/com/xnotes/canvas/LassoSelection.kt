package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Page
import com.xnotes.core.model.Stroke
import com.xnotes.core.stroke.StrokeClipper

/**
 * Split details for a single stroke divided by a pixel-perfect lasso boundary.
 */
data class ExactLassoSplit(
    val original: Stroke,
    val inside: List<Stroke>,
    val outside: List<Stroke>,
)

/**
 * Result of exact lasso selection over a flat list of items.
 */
data class ExactLassoResult(
    val selected: List<CanvasItem>,
    val splits: List<ExactLassoSplit>,
)

/**
 * Split details for a stroke on a specific page.
 */
data class SelectedSplit(
    val pageIndex: Int,
    val original: Stroke,
    val inside: List<Stroke>,
    val outside: List<Stroke>,
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
     * - Entirely inside strokes are selected whole without splitting.
     * - Entirely outside strokes are untouched.
     * - Crossing strokes are split: inside sub-strokes are selected, outside sub-strokes remain unselected.
     * - Non-stroke items meeting the lasso polygon are selected whole.
     */
    fun selectExact(items: List<CanvasItem>, polygon: List<Pt>): ExactLassoResult {
        if (polygon.size < 3) return ExactLassoResult(emptyList(), emptyList())
        val selected = ArrayList<CanvasItem>()
        val splits = ArrayList<ExactLassoSplit>()
        val candidates = SelectionMath.lassoMembers(items, polygon)
        val candidateSet = candidates.toSet()

        for (item in items) {
            if (item.locked || item !in candidateSet) continue

            if (item !is Stroke) {
                selected.add(item)
                continue
            }

            val clip = StrokeClipper.clipStroke(item, polygon)
            if (clip.split) {
                selected.addAll(clip.inside)
                splits.add(ExactLassoSplit(item, clip.inside, clip.outside))
            } else if (clip.inside.isNotEmpty()) {
                selected.add(item)
            }
        }
        return ExactLassoResult(selected, splits)
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
