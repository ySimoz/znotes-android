package com.xnotes.core.history

import com.xnotes.core.model.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests MovePage command undo/redo and history stack integration. */
class MovePageTest {

    @Test fun movePageUndoRedoRoundTrip() {
        val doc = Document.blank(count = 4)
        val p0 = doc.pages[0]
        val p1 = doc.pages[1]
        val p2 = doc.pages[2]
        val p3 = doc.pages[3]

        // Gesture moves page first
        doc.movePage(0, 2)
        val cmd = MovePage(doc, p0, 0, 2)

        assertTrue(cmd.isStructural)
        assertNull(cmd.touched { null })

        // Undo moves it back to 0
        cmd.undo()
        assertEquals(listOf(p0, p1, p2, p3), doc.pages)

        // Redo moves it to 2 again
        cmd.redo()
        assertEquals(listOf(p1, p2, p0, p3), doc.pages)

        // Idempotency
        cmd.redo()
        assertEquals(listOf(p1, p2, p0, p3), doc.pages)
        cmd.undo()
        assertEquals(listOf(p0, p1, p2, p3), doc.pages)
        cmd.undo()
        assertEquals(listOf(p0, p1, p2, p3), doc.pages)
    }

    @Test fun historyStackIntegration() {
        val doc = Document.blank(count = 3)
        val p0 = doc.pages[0]
        val p1 = doc.pages[1]
        val p2 = doc.pages[2]
        val history = History()

        doc.movePage(2, 0)
        history.push(MovePage(doc, p2, 2, 0))

        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(listOf(p2, p0, p1), doc.pages)

        history.undo()
        assertTrue(history.canRedo)
        assertEquals(listOf(p0, p1, p2), doc.pages)

        history.redo()
        assertEquals(listOf(p2, p0, p1), doc.pages)
    }

    @Test fun compositeCommandWithMovePageIsStructural() {
        val doc = Document.blank(count = 3)
        val p0 = doc.pages[0]
        val cmd = MovePage(doc, p0, 0, 1)
        val composite = CompositeCommand(listOf(cmd))
        assertTrue(composite.isStructural)
    }

    @Test fun movePageUndoRedoTracksActivePageIndexCallback() {
        val doc = Document.blank(count = 4)
        val p1 = doc.pages[1]
        var activePageIndex = 1

        doc.movePage(1, 0)
        activePageIndex = 0
        val cmd = MovePage(doc, p1, 1, 0) { targetIndex ->
            activePageIndex = targetIndex
        }

        cmd.undo()
        assertEquals(1, activePageIndex)
        assertEquals(p1, doc.pages[1])

        cmd.redo()
        assertEquals(0, activePageIndex)
        assertEquals(p1, doc.pages[0])
    }
}
