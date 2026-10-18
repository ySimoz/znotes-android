package com.xnotes.canvas

import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.ShapeItem
import com.xnotes.core.model.TextItem
import com.xnotes.core.shapes.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedItemClipboardTest {

    private val measurer = FakeTextMeasurer()

    @Before
    fun setUp() {
        SharedItemClipboard.clear()
    }

    @Test
    fun copyAndGetClonesDeepCopiesItems() {
        val shape = ShapeItem(
            shape = ShapeKind.RECTANGLE,
            start = Pt(10.0, 10.0),
            end = Pt(100.0, 100.0),
            strokeRgba = Rgba(255, 0, 0),
        )
        val text = TextItem(
            pos = Pt(50.0, 50.0),
            text = "Clipboard test",
            measurer = measurer,
        )

        val original = listOf<CanvasItem>(shape, text)
        SharedItemClipboard.copy(original, measurer)

        assertTrue(SharedItemClipboard.hasItems())
        assertFalse(SharedItemClipboard.fromCut)
        assertEquals(2, SharedItemClipboard.count)

        val clones = SharedItemClipboard.getClones(measurer)
        assertEquals(2, clones.size)
        assertNotSame(original[0], clones[0])
        assertNotSame(original[1], clones[1])

        // Modifying clone doesn't touch clipboard
        clones[0].translate(50.0, 50.0)
        val clonesSecond = SharedItemClipboard.getClones(measurer)
        assertEquals(10.0, (clonesSecond[0] as ShapeItem).start.x, 1e-6)
    }

    @Test
    fun cutSetsFromCutAndConsumeClearsClipboard() {
        val shape = ShapeItem(
            shape = ShapeKind.CIRCLE,
            start = Pt(0.0, 0.0),
            end = Pt(50.0, 50.0),
            strokeRgba = Rgba(0, 255, 0),
        )
        SharedItemClipboard.cut(listOf(shape), measurer)

        assertTrue(SharedItemClipboard.hasItems())
        assertTrue(SharedItemClipboard.fromCut)

        SharedItemClipboard.consumeCut()
        assertFalse(SharedItemClipboard.hasItems())
        assertFalse(SharedItemClipboard.fromCut)
    }

    @Test
    fun clearWipesItems() {
        val shape = ShapeItem(
            shape = ShapeKind.LINE,
            start = Pt(0.0, 0.0),
            end = Pt(10.0, 10.0),
            strokeRgba = Rgba(0, 0, 0),
        )
        SharedItemClipboard.copy(listOf(shape), measurer)
        assertTrue(SharedItemClipboard.hasItems())

        SharedItemClipboard.clear()
        assertFalse(SharedItemClipboard.hasItems())
        assertEquals(0, SharedItemClipboard.count)
    }
}
