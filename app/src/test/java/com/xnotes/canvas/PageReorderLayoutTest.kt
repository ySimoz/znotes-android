package com.xnotes.canvas

import com.xnotes.core.FakeSurfaceFactory
import com.xnotes.core.history.MovePage
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests layout recalculation and active page navigation when pages are reordered. */
class PageReorderLayoutTest {

    private fun state(pages: Int = 4): CanvasState =
        CanvasState(Document.blank(pages), FakeSurfaceFactory(), Palette.DEFAULT).apply {
            viewportW = 1000
            viewportH = 1400
            verticalScroll = true
            relayout()
        }

    @Test fun relayoutPositionsPagesInNewOrder() {
        val st = state(3)
        val p0 = st.document.pages[0]
        val p1 = st.document.pages[1]
        val p2 = st.document.pages[2]

        // Custom heights to distinguish them easily
        p0.height = 1000.0
        p1.height = 1500.0
        p2.height = 2000.0
        st.relayout()

        val initialY0 = st.pageRects[0].top
        val initialY1 = st.pageRects[1].top
        val initialY2 = st.pageRects[2].top

        assertTrue(initialY0 < initialY1)
        assertTrue(initialY1 < initialY2)

        // Move page 0 to index 2
        st.document.movePage(0, 2)
        st.relayout()

        assertEquals(listOf(p1, p2, p0), st.document.pages)
        assertEquals(1500.0, st.pageRects[0].h, 1e-6)
        assertEquals(2000.0, st.pageRects[1].h, 1e-6)
        assertEquals(1000.0, st.pageRects[2].h, 1e-6)
    }

    @Test fun activePageFollowsWhenMoved() {
        val st = state(4)
        val movedPage = st.document.pages[0]

        st.goToPage(0)
        assertEquals(0, st.currentPageIndex())

        // Move page 0 to index 2
        val activeBefore = st.document.pages[st.currentPageIndex()]
        assertSame(movedPage, activeBefore)

        st.document.movePage(0, 2)
        st.relayout()

        val newIndex = st.document.pages.indexOfFirst { it === activeBefore }
        assertEquals(2, newIndex)

        st.goToPage(newIndex)
        assertEquals(2, st.currentPageIndex())
    }

    @Test fun activePageFollowsWhenShiftedByMove() {
        val st = state(4)
        val targetPage = st.document.pages[2]

        st.goToPage(2)
        assertEquals(2, st.currentPageIndex())

        // Move page 0 to index 3 (pages 1 and 2 shift down by 1)
        val activeBefore = st.document.pages[st.currentPageIndex()]
        assertSame(targetPage, activeBefore)

        st.document.movePage(0, 3)
        st.relayout()

        val newIndex = st.document.pages.indexOfFirst { it === activeBefore }
        assertEquals(1, newIndex)

        st.goToPage(newIndex)
        assertEquals(1, st.currentPageIndex())
    }

    @Test fun activePageRestoredOnUndoAndRedo() {
        val st = state(4)
        val p0 = st.document.pages[0]
        val p1 = st.document.pages[1]
        val p2 = st.document.pages[2]
        val p3 = st.document.pages[3]

        // 1. Setup multi-page canvas state with page 1 active
        st.goToPage(1)
        assertEquals(1, st.currentPageIndex())
        val activePage = st.document.pages[st.currentPageIndex()]
        assertSame(p1, activePage)

        // 2. Execute movePage(1, 0) and record MovePage command
        st.document.movePage(1, 0)
        val cmd = MovePage(st.document, p1, 1, 0)
        st.relayout()
        val indexAfterMove = st.document.pages.indexOfFirst { it === activePage }
        assertEquals(0, indexAfterMove)
        st.goToPage(indexAfterMove)
        assertEquals(0, st.currentPageIndex())
        assertEquals(listOf(p1, p0, p2, p3), st.document.pages)

        // 3. Execute cmd.undo(), update layout/active page, asserting currentPageIndex() is restored to 1
        cmd.undo()
        st.relayout()
        val indexAfterUndo = st.document.pages.indexOfFirst { it === activePage }
        assertEquals(1, indexAfterUndo)
        st.goToPage(indexAfterUndo)
        assertEquals(1, st.currentPageIndex())
        assertEquals(listOf(p0, p1, p2, p3), st.document.pages)

        // 4. Execute cmd.redo(), asserting currentPageIndex() is at 0
        cmd.redo()
        st.relayout()
        val indexAfterRedo = st.document.pages.indexOfFirst { it === activePage }
        assertEquals(0, indexAfterRedo)
        st.goToPage(indexAfterRedo)
        assertEquals(0, st.currentPageIndex())
        assertEquals(listOf(p1, p0, p2, p3), st.document.pages)
    }

    @Test fun shiftedActivePageRestoredOnUndoAndRedo() {
        val st = state(4)
        val p0 = st.document.pages[0]
        val p1 = st.document.pages[1]
        val p2 = st.document.pages[2]
        val p3 = st.document.pages[3]

        // Page 2 is active
        st.goToPage(2)
        assertEquals(2, st.currentPageIndex())
        val activePage = st.document.pages[st.currentPageIndex()]
        assertSame(p2, activePage)

        // Move page 0 to index 3 (p2 shifts down to index 1)
        st.document.movePage(0, 3)
        val cmd = MovePage(st.document, p0, 0, 3)
        st.relayout()
        val indexAfterMove = st.document.pages.indexOfFirst { it === activePage }
        assertEquals(1, indexAfterMove)
        st.goToPage(indexAfterMove)
        assertEquals(1, st.currentPageIndex())
        assertEquals(listOf(p1, p2, p3, p0), st.document.pages)

        // Undo restores active page back to 2
        cmd.undo()
        st.relayout()
        val indexAfterUndo = st.document.pages.indexOfFirst { it === activePage }
        assertEquals(2, indexAfterUndo)
        st.goToPage(indexAfterUndo)
        assertEquals(2, st.currentPageIndex())
        assertEquals(listOf(p0, p1, p2, p3), st.document.pages)

        // Redo shifts active page back to 1
        cmd.redo()
        st.relayout()
        val indexAfterRedo = st.document.pages.indexOfFirst { it === activePage }
        assertEquals(1, indexAfterRedo)
        st.goToPage(indexAfterRedo)
        assertEquals(1, st.currentPageIndex())
        assertEquals(listOf(p1, p2, p3, p0), st.document.pages)
    }
}
