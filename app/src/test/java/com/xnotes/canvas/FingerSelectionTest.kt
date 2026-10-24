package com.xnotes.canvas

import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.history.History
import com.xnotes.core.model.ConnectorEnd
import com.xnotes.core.model.ConnectorItem
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Routing
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.Stroke
import com.xnotes.core.shapes.ShapeKind
import com.xnotes.core.stroke.Sample
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolDefaults
import com.xnotes.ui.theme.Palette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerSelectionTest {

    private val ink = Rgba(0, 0, 0, 255)

    private fun stateWith(vararg items: com.xnotes.core.model.CanvasItem): CanvasState {
        val page = Page(500.0, 500.0, items.toMutableList())
        val doc = Document(mutableListOf(page))
        return CanvasState(doc, FakeSurfaceFactory(), Palette.DEFAULT).apply {
            viewportW = 1000
            viewportH = 1000
            zoom = 1.0
            scrollX = 0.0
            scrollY = 0.0
            relayout()
        }
    }

    private fun controller(st: CanvasState) =
        InteractionController(st, History(), FakeTextMeasurer(), requestRender = {})

    @Test
    fun fingerHitsSelectedStraightLine() {
        val line = ShapeItem(ShapeKind.LINE, Pt(50.0, 100.0), Pt(250.0, 100.0), ink, 2.0)
        val st = stateWith(line)
        val ctrl = controller(st)
        ctrl.selectPlaced(0, listOf(line))

        val midContent = st.fromPageSpace(0, Pt(150.0, 108.0)) // 8px off the horizontal line
        assertTrue("Finger near line should hit selection", ctrl.fingerHitsSelection(midContent))
        assertTrue("Should grab selection body", ctrl.grabsSelectionBody(midContent, isFinger = true))

        val farContent = st.fromPageSpace(0, Pt(150.0, 200.0)) // 100px away
        assertFalse("Finger far from line should not hit selection", ctrl.fingerHitsSelection(farContent))
    }

    @Test
    fun fingerHitsSelectedStroke() {
        val stroke = Stroke(
            Tool.PEN,
            ToolDefaults.configFor(Tool.PEN),
            mutableListOf(Sample(50.0, 50.0, 1.0), Sample(150.0, 50.0, 1.0)),
        )
        val st = stateWith(stroke)
        val ctrl = controller(st)
        ctrl.selectPlaced(0, listOf(stroke))

        val nearContent = st.fromPageSpace(0, Pt(100.0, 58.0)) // 8px off stroke
        assertTrue("Finger near stroke should hit selection", ctrl.fingerHitsSelection(nearContent))
        assertTrue("Should grab selection body", ctrl.grabsSelectionBody(nearContent, isFinger = true))

        val farContent = st.fromPageSpace(0, Pt(100.0, 150.0))
        assertFalse("Finger far from stroke should not hit selection", ctrl.fingerHitsSelection(farContent))
    }

    @Test
    fun fingerHitsSelectedConnector() {
        val connector = ConnectorItem(
            Routing.STRAIGHT,
            ConnectorEnd(Pt(50.0, 50.0)),
            ConnectorEnd(Pt(200.0, 50.0)),
            ink,
            2.0,
        )
        val st = stateWith(connector)
        val ctrl = controller(st)
        ctrl.selectPlaced(0, listOf(connector))

        val nearContent = st.fromPageSpace(0, Pt(120.0, 58.0))
        assertTrue("Finger near connector should hit selection", ctrl.fingerHitsSelection(nearContent))
        assertTrue("Should grab selection body", ctrl.grabsSelectionBody(nearContent, isFinger = true))

        val farContent = st.fromPageSpace(0, Pt(120.0, 200.0))
        assertFalse("Finger far from connector should not hit selection", ctrl.fingerHitsSelection(farContent))
    }

    @Test
    fun fingerHitsSelectedRectangleInsideAndPaddedBorder() {
        val rect = ShapeItem(ShapeKind.RECTANGLE, Pt(50.0, 50.0), Pt(150.0, 150.0), ink, 2.0)
        val st = stateWith(rect)
        val ctrl = controller(st)
        ctrl.selectPlaced(0, listOf(rect))

        val inside = st.fromPageSpace(0, Pt(100.0, 100.0))
        assertTrue("Finger inside rectangle should hit selection", ctrl.fingerHitsSelection(inside))

        val nearBorder = st.fromPageSpace(0, Pt(158.0, 100.0)) // 8px outside right edge
        assertTrue("Finger slightly outside rectangle should hit selection with finger padding", ctrl.fingerHitsSelection(nearBorder))

        val farContent = st.fromPageSpace(0, Pt(300.0, 300.0))
        assertFalse("Finger far from rectangle should not hit selection", ctrl.fingerHitsSelection(farContent))
    }

    @Test
    fun fingerHitsMultipleSelectedItems() {
        val line = ShapeItem(ShapeKind.LINE, Pt(50.0, 50.0), Pt(150.0, 50.0), ink, 2.0)
        val stroke = Stroke(
            Tool.PEN,
            ToolDefaults.configFor(Tool.PEN),
            mutableListOf(Sample(50.0, 150.0, 1.0), Sample(150.0, 150.0, 1.0)),
        )
        val st = stateWith(line, stroke)
        val ctrl = controller(st)
        ctrl.selectPlaced(0, listOf(line, stroke))

        val nearLine = st.fromPageSpace(0, Pt(100.0, 55.0))
        assertTrue(ctrl.fingerHitsSelection(nearLine))

        val betweenInBox = st.fromPageSpace(0, Pt(100.0, 100.0))
        assertTrue(ctrl.fingerHitsSelection(betweenInBox))

        val farAway = st.fromPageSpace(0, Pt(400.0, 400.0))
        assertFalse(ctrl.fingerHitsSelection(farAway))
    }
}
