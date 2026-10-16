package com.xnotes.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests Document page reordering, boundary conditions, and bookmark reconciliation. */
class DocumentTest {

    @Test fun pageCountReflectsPagesSize() {
        val doc = Document.blank(count = 3)
        assertEquals(3, doc.pageCount)
    }

    @Test fun movePageForwardReordersPages() {
        val doc = Document.blank(count = 4)
        val p0 = doc.pages[0]
        val p1 = doc.pages[1]
        val p2 = doc.pages[2]
        val p3 = doc.pages[3]

        assertTrue(doc.movePage(0, 2))
        assertEquals(listOf(p1, p2, p0, p3), doc.pages)
        assertTrue(doc.dirty)
    }

    @Test fun movePageBackwardReordersPages() {
        val doc = Document.blank(count = 4)
        val p0 = doc.pages[0]
        val p1 = doc.pages[1]
        val p2 = doc.pages[2]
        val p3 = doc.pages[3]

        assertTrue(doc.movePage(3, 1))
        assertEquals(listOf(p0, p3, p1, p2), doc.pages)
        assertTrue(doc.dirty)
    }

    @Test fun movePageAdjacentSwap() {
        val doc = Document.blank(count = 3)
        val p0 = doc.pages[0]
        val p1 = doc.pages[1]
        val p2 = doc.pages[2]

        assertTrue(doc.movePage(1, 2))
        assertEquals(listOf(p0, p2, p1), doc.pages)

        assertTrue(doc.movePage(2, 1))
        assertEquals(listOf(p0, p1, p2), doc.pages)
    }

    @Test fun movePageBoundaryCasesReturnFalse() {
        val doc = Document.blank(count = 3)
        doc.dirty = false

        // Invalid negative fromIndex
        assertFalse(doc.movePage(-1, 1))
        // Invalid negative toIndex
        assertFalse(doc.movePage(1, -1))
        // Out of bounds fromIndex
        assertFalse(doc.movePage(3, 1))
        // Out of bounds toIndex
        assertFalse(doc.movePage(1, 3))
        // Identical fromIndex and toIndex
        assertFalse(doc.movePage(1, 1))
        // Document was not dirtied on failed moves
        assertFalse(doc.dirty)
    }

    @Test fun movePageUpdatesBookmarksCorrectly() {
        val doc = Document.blank(count = 5)
        doc.bookmarks.add(Bookmark(0, "BM0"))
        doc.bookmarks.add(Bookmark(1, "BM1"))
        doc.bookmarks.add(Bookmark(2, "BM2"))
        doc.bookmarks.add(Bookmark(3, "BM3"))
        doc.bookmarks.add(Bookmark(4, "BM4"))

        // Move page 1 to page 3 (forward)
        assertTrue(doc.movePage(1, 3))

        // Page 1 moved to 3, pages 2 and 3 shifted down to 1 and 2
        assertEquals(0, doc.bookmarks[0].page) // BM0 stays on page 0
        assertEquals(3, doc.bookmarks[1].page) // BM1 followed moved page 1 to 3
        assertEquals(1, doc.bookmarks[2].page) // BM2 followed page 2 to 1
        assertEquals(2, doc.bookmarks[3].page) // BM3 followed page 3 to 2
        assertEquals(4, doc.bookmarks[4].page) // BM4 stays on page 4

        // Move page 3 back to page 1 (backward)
        assertTrue(doc.movePage(3, 1))

        assertEquals(0, doc.bookmarks[0].page)
        assertEquals(1, doc.bookmarks[1].page)
        assertEquals(2, doc.bookmarks[2].page)
        assertEquals(3, doc.bookmarks[3].page)
        assertEquals(4, doc.bookmarks[4].page)
    }

    @Test fun reorderedIndexComputesCorrectTarget() {
        // Move item from 0 to 3
        assertEquals(3, Document.reorderedIndex(0, 0, 3))
        assertEquals(0, Document.reorderedIndex(1, 0, 3))
        assertEquals(1, Document.reorderedIndex(2, 0, 3))
        assertEquals(2, Document.reorderedIndex(3, 0, 3))
        assertEquals(4, Document.reorderedIndex(4, 0, 3))

        // Move item from 3 to 1
        assertEquals(0, Document.reorderedIndex(0, 3, 1))
        assertEquals(2, Document.reorderedIndex(1, 3, 1))
        assertEquals(3, Document.reorderedIndex(2, 3, 1))
        assertEquals(1, Document.reorderedIndex(3, 3, 1))
        assertEquals(4, Document.reorderedIndex(4, 3, 1))
    }
}
