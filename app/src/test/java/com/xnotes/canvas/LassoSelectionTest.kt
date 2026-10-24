package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import com.xnotes.core.history.History
import com.xnotes.core.history.MoveItems
import com.xnotes.core.history.ReplacePageItems
import com.xnotes.core.infinite.CanvasSelection
import com.xnotes.core.infinite.InfiniteDocument
import com.xnotes.core.infinite.SplitCanvasItems
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoSelectionTest {

    private val squarePolygon = listOf(
        Pt(10.0, 10.0),
        Pt(30.0, 10.0),
        Pt(30.0, 30.0),
        Pt(10.0, 30.0),
    )

    private fun sampleStroke(vararg coords: Pair<Double, Double>): Stroke {
        val s = Stroke(Tool.PEN, ToolConfig())
        s.setSamples(coords.map { Sample(it.first, it.second, 1.0) })
        return s
    }

    @Test
    fun exactLassoSplitsAndSelectsOnlyInsidePortions() {
        val crossingStroke = sampleStroke(0.0 to 20.0, 40.0 to 20.0)
        val insideStroke = sampleStroke(15.0 to 15.0, 25.0 to 25.0)
        val outsideStroke = sampleStroke(50.0 to 50.0, 60.0 to 60.0)

        val items = listOf(crossingStroke, insideStroke, outsideStroke)
        val result = LassoSelection.selectExact(items, squarePolygon)

        // Crossing stroke was split
        assertEquals(1, result.splits.size)
        assertSame(crossingStroke, result.splits[0].original)
        assertEquals(1, result.splits[0].inside.size)
        assertEquals(2, result.splits[0].outside.size)

        // Selected items: inside sub-stroke of crossing + insideStroke
        assertEquals(2, result.selected.size)
        assertSame(result.splits[0].inside[0], result.selected[0])
        assertSame(insideStroke, result.selected[1])

        // Outside stroke is not selected and not in splits
        assertFalse(result.selected.contains(outsideStroke))
    }

    @Test
    fun operationsAffectOnlyInsideSubStrokes() {
        val crossingStroke = sampleStroke(0.0 to 20.0, 40.0 to 20.0)
        val result = LassoSelection.selectExact(listOf(crossingStroke), squarePolygon)
        val insideSubStroke = result.splits[0].inside[0]
        val outsideSubStrokes = result.splits[0].outside

        // Translate the selection by dx = 10, dy = 5
        insideSubStroke.translate(10.0, 5.0)

        // Verify inside moved
        assertEquals(20.0, insideSubStroke.xAt(0), 1e-4)
        assertEquals(25.0, insideSubStroke.yAt(0), 1e-4)

        // Verify outside remained in place
        assertEquals(0.0, outsideSubStrokes[0].xAt(0), 1e-4)
        assertEquals(20.0, outsideSubStrokes[0].yAt(0), 1e-4)
        assertEquals(30.0, outsideSubStrokes[1].xAt(0), 1e-4)
        assertEquals(20.0, outsideSubStrokes[1].yAt(0), 1e-4)
    }

    @Test
    fun undoRedoRestoresOriginalStrokeIntactInfiniteCanvas() {
        val doc = InfiniteDocument()
        val history = History()
        val selection = CanvasSelection(doc)

        val crossing = sampleStroke(0.0 to 20.0, 40.0 to 20.0)
        doc.add(crossing)
        assertEquals(1, doc.itemCount)

        // Select with exact lasso
        val cmd = checkNotNull(selection.selectExact(doc.items, squarePolygon))
        history.push(cmd)

        // Document now has 3 fragments (2 outside + 1 inside)
        assertEquals(3, doc.itemCount)
        assertEquals(1, selection.items.size)
        assertFalse(doc.containsRef(crossing))

        // Undo restores original stroke intact
        history.undo()
        assertEquals(1, doc.itemCount)
        assertSame(crossing, doc.items[0])

        // Redo splits stroke again
        history.redo()
        assertEquals(3, doc.itemCount)
        assertFalse(doc.containsRef(crossing))
    }

    @Test
    fun undoRedoRestoresOriginalStrokeIntactPagedCanvas() {
        val page = Page(100.0, 100.0)
        val history = History()

        val crossing = sampleStroke(0.0 to 20.0, 40.0 to 20.0)
        page.items.add(crossing)

        val before = page.items.toList()
        val paged = LassoSelection.selectExact(listOf(page), listOf(com.xnotes.core.geometry.Rect(0.0, 0.0, 100.0, 100.0)), squarePolygon) { _, p -> p }

        assertEquals(1, paged.splits.size)
        val split = paged.splits[0]
        val allFrags = split.outside + split.inside
        val idx = page.items.indexOfFirst { it === split.original }
        page.items.removeAt(idx)
        page.items.addAll(idx, allFrags)

        val cmd = ReplacePageItems(page, before, page.items.toList())
        history.push(cmd)

        // Page now has 3 fragments
        assertEquals(3, page.items.size)

        // Undo restores original stroke intact
        history.undo()
        assertEquals(1, page.items.size)
        assertSame(crossing, page.items[0])

        // Redo replaces with fragments
        history.redo()
        assertEquals(3, page.items.size)
        assertFalse(page.items.any { it === crossing })
    }

    @Test
    fun moveAndUndoRestoresOriginalStroke() {
        val doc = InfiniteDocument()
        val history = History()
        val selection = CanvasSelection(doc)

        val crossing = sampleStroke(0.0 to 20.0, 40.0 to 20.0)
        doc.add(crossing)

        val splitCmd = selection.selectExact(doc.items, squarePolygon)!!
        history.push(splitCmd)

        // Move the selected inside sub-stroke
        val movedItem = selection.items[0]
        val initialX = (movedItem as Stroke).xAt(0)
        movedItem.translate(15.0, 0.0)
        history.push(MoveItems(listOf(movedItem), 15.0, 0.0))

        assertEquals(initialX + 15.0, (doc.items.first { it === movedItem } as Stroke).xAt(0), 1e-4)

        // Undo 1: undoes the move
        history.undo()
        assertEquals(initialX, (doc.items.first { it === movedItem } as Stroke).xAt(0), 1e-4)

        // Undo 2: undoes the split, restoring original intact stroke
        history.undo()
        assertEquals(1, doc.itemCount)
        assertSame(crossing, doc.items[0])
    }

    @Test
    fun exactLassoSplitsStraightLineIntoSubStrokes() {
        val line = ShapeItem(
            shape = ShapeKind.LINE,
            start = Pt(0.0, 20.0),
            end = Pt(40.0, 20.0),
            strokeRgba = Rgba(0, 0, 0, 255),
            strokeWidth = 2.0,
        )

        val result = LassoSelection.selectExact(listOf(line), squarePolygon)

        assertEquals(1, result.splits.size)
        assertSame(line, result.splits[0].original)
        assertEquals(1, result.splits[0].inside.size)
        assertEquals(2, result.splits[0].outside.size)

        assertEquals(1, result.selected.size)
        assertSame(result.splits[0].inside[0], result.selected[0])
    }

    @Test
    fun exactLassoEnclosingWholeShapeSelectsShapeItemIntact() {
        val line = ShapeItem(
            shape = ShapeKind.LINE,
            start = Pt(15.0, 15.0),
            end = Pt(25.0, 25.0),
            strokeRgba = Rgba(0, 0, 0, 255),
            strokeWidth = 2.0,
        )

        val result = LassoSelection.selectExact(listOf(line), squarePolygon)

        assertEquals(0, result.splits.size)
        assertEquals(1, result.selected.size)
        assertSame(line, result.selected[0])
    }

    @Test
    fun exactLassoSplitsRectangleIntoSubStrokes() {
        val rect = ShapeItem(
            shape = ShapeKind.RECTANGLE,
            start = Pt(0.0, 0.0),
            end = Pt(20.0, 40.0),
            strokeRgba = Rgba(0, 0, 0, 255),
            strokeWidth = 2.0,
        )

        val result = LassoSelection.selectExact(listOf(rect), squarePolygon)

        assertEquals(1, result.splits.size)
        assertSame(rect, result.splits[0].original)
        assertTrue(result.splits[0].inside.isNotEmpty())
        assertTrue(result.splits[0].outside.isNotEmpty())
        assertEquals(result.splits[0].inside.size, result.selected.size)
    }

    @Test
    fun exactLassoInsideUnfilledShapeEmptyInteriorDoesNotSelect() {
        val rect = ShapeItem(
            shape = ShapeKind.RECTANGLE,
            start = Pt(0.0, 0.0),
            end = Pt(40.0, 40.0),
            strokeRgba = Rgba(0, 0, 0, 255),
            strokeWidth = 2.0,
        )

        val result = LassoSelection.selectExact(listOf(rect), squarePolygon)

        assertEquals(0, result.splits.size)
        assertEquals(0, result.selected.size)
    }

    @Test
    fun undoRedoRestoresOriginalShapeItemIntactInfiniteCanvas() {
        val doc = InfiniteDocument()
        val history = History()
        val selection = CanvasSelection(doc)

        val line = ShapeItem(
            shape = ShapeKind.LINE,
            start = Pt(0.0, 20.0),
            end = Pt(40.0, 20.0),
            strokeRgba = Rgba(0, 0, 0, 255),
            strokeWidth = 2.0,
        )
        doc.add(line)
        assertEquals(1, doc.itemCount)

        val cmd = checkNotNull(selection.selectExact(doc.items, squarePolygon))
        history.push(cmd)

        // Document replaced ShapeItem with stroke fragments (2 outside + 1 inside)
        assertEquals(3, doc.itemCount)
        assertEquals(1, selection.items.size)
        assertFalse(doc.containsRef(line))

        // Undo restores original shape item intact
        history.undo()
        assertEquals(1, doc.itemCount)
        assertSame(line, doc.items[0])

        // Redo splits shape item into fragments again
        history.redo()
        assertEquals(3, doc.itemCount)
        assertFalse(doc.containsRef(line))
    }
}
