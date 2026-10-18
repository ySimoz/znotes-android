package com.xnotes.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for hierarchical OutlineItem, uncapped nesting, and page sync arithmetic. */
class OutlineTest {

    @Test
    fun outlineItemUncappedNesting() {
        val item = OutlineItem(page = 5, title = "Deep section", level = 7)
        assertEquals(5, item.page)
        assertEquals("Deep section", item.title)
        assertEquals(7, item.level)
        assertTrue(item.expanded)
    }

    @Test
    fun normalizeOutlineLevelsPreventsOrphanJumps() {
        val doc = Document.blank(count = 5)
        doc.outline.add(OutlineItem(page = 0, title = "Chapter 1", level = 0))
        doc.outline.add(OutlineItem(page = 1, title = "Section 1.1", level = 5)) // invalid jump
        doc.outline.add(OutlineItem(page = 2, title = "Subsection 1.1.1", level = 9))

        doc.normalizeOutlineLevels()

        assertEquals(0, doc.outline[0].level)
        assertEquals(1, doc.outline[1].level) // clamped to prev + 1
        assertEquals(2, doc.outline[2].level) // clamped to prev + 1
    }

    @Test
    fun pageMoveReconcilesOutlinePages() {
        val doc = Document.blank(count = 5)
        doc.outline.add(OutlineItem(page = 0, title = "Intro", level = 0))
        doc.outline.add(OutlineItem(page = 2, title = "Methods", level = 0))
        doc.outline.add(OutlineItem(page = 4, title = "Appendix", level = 0))

        // Move page 0 to 2: pages become [p1, p2, p0, p3, p4]
        assertTrue(doc.movePage(0, 2))

        assertEquals(2, doc.outline[0].page) // was 0 -> now 2
        assertEquals(1, doc.outline[1].page) // was 2 -> shifted down to 1
        assertEquals(4, doc.outline[2].page) // was 4 -> unchanged
    }

    @Test
    fun onPageInsertedShiftsSubsequentOutlineItems() {
        val doc = Document.blank(count = 3)
        doc.outline.add(OutlineItem(page = 0, title = "Cover", level = 0))
        doc.outline.add(OutlineItem(page = 1, title = "Chapter 1", level = 0))
        doc.outline.add(OutlineItem(page = 2, title = "Chapter 2", level = 0))

        doc.onPageInserted(at = 1)

        assertEquals(0, doc.outline[0].page) // before insertion point
        assertEquals(2, doc.outline[1].page) // shifted 1 -> 2
        assertEquals(3, doc.outline[2].page) // shifted 2 -> 3
        assertTrue(doc.dirty)
    }

    @Test
    fun onPageDeletedRemovesEntryAndPromotesChildren() {
        val doc = Document.blank(count = 5)
        val ch1 = OutlineItem(page = 1, title = "Chapter 1", level = 0)
        val sec1 = OutlineItem(page = 2, title = "Section 1.1", level = 1)
        val sub1 = OutlineItem(page = 3, title = "Subsection 1.1.1", level = 2)
        doc.outline.addAll(listOf(ch1, sec1, sub1))

        // Delete page 1 (Chapter 1)
        doc.onPageDeleted(at = 1)

        assertEquals(2, doc.outline.size)
        // Previous Section 1.1 was on page 2, now page 1, promoted from level 1 to level 0!
        assertEquals("Section 1.1", doc.outline[0].title)
        assertEquals(1, doc.outline[0].page)
        assertEquals(0, doc.outline[0].level)

        // Previous Subsection 1.1.1 was on page 3, now page 2, level normalized to 1
        assertEquals("Subsection 1.1.1", doc.outline[1].title)
        assertEquals(2, doc.outline[1].page)
        assertEquals(1, doc.outline[1].level)
    }
}
