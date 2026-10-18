package com.xnotes.canvas

import com.xnotes.core.model.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTabManagerTest {

    private fun makeSession(uri: String?, title: String): NoteTabSession {
        return NoteTabSession(
            uri = uri,
            title = title,
            document = Document(),
        )
    }

    @Test
    fun opensAndSelectsTabsUpToCapacity() {
        val manager = NoteTabManager(maxTabs = 8)

        for (i in 1..5) {
            val (session, isNew) = manager.openOrSelectTab("uri://$i", "Note $i", { makeSession("uri://$i", "Note $i") })
            assertTrue(isNew)
            assertEquals("uri://$i", session.uri)
            assertEquals(i - 1, manager.activeIndex)
        }

        assertEquals(5, manager.count)
        assertEquals("uri://5", manager.activeTab?.uri)
    }

    @Test
    fun selectsExistingTabWithoutDuplication() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })

        assertEquals(2, manager.count)
        assertEquals(1, manager.activeIndex)

        // Select first tab by opening its URI again
        val (session, isNew) = manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        assertFalse(isNew)
        assertEquals(0, manager.activeIndex)
        assertEquals("uri://1", session.uri)
        assertEquals(2, manager.count)
    }

    @Test
    fun evictsOldestCleanTabWhenExceedingMaxTabs() {
        val manager = NoteTabManager(maxTabs = 3)
        var evictedUri: String? = null

        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })
        manager.openOrSelectTab("uri://3", "Note 3", { makeSession("uri://3", "Note 3") })

        assertEquals(3, manager.count)
        // Mark uri://2 as dirty
        manager.tabs[1].document.dirty = true

        // Opening 4th tab should evict uri://1 (oldest clean tab)
        manager.openOrSelectTab("uri://4", "Note 4", { makeSession("uri://4", "Note 4") }, onEvict = { evictedUri = it.uri })

        assertEquals(3, manager.count)
        assertEquals("uri://1", evictedUri)
        assertNotNull(manager.findTabByUri("uri://2"))
        assertNotNull(manager.findTabByUri("uri://3"))
        assertNotNull(manager.findTabByUri("uri://4"))
    }

    @Test
    fun closingTabAdjustsActiveIndexCorrectly() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })
        manager.openOrSelectTab("uri://3", "Note 3", { makeSession("uri://3", "Note 3") })

        manager.selectTab(1) // Active is Note 2

        // Close Note 1 (index 0)
        manager.closeTab(0)
        assertEquals(2, manager.count)
        assertEquals(0, manager.activeIndex)
        assertEquals("uri://2", manager.activeTab?.uri)

        // Close active Note 2
        manager.closeTab(0)
        assertEquals(1, manager.count)
        assertEquals(0, manager.activeIndex)
        assertEquals("uri://3", manager.activeTab?.uri)

        // Close last remaining tab
        manager.closeTab(0)
        assertEquals(0, manager.count)
        assertEquals(-1, manager.activeIndex)
    }

    @Test
    fun movesTabAndAdjustsActiveIndexCorrectly() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })
        manager.openOrSelectTab("uri://3", "Note 3", { makeSession("uri://3", "Note 3") })

        manager.selectTab(0) // Active is Note 1 at index 0

        // Move active tab from 0 to 2: [Note 1, Note 2, Note 3] -> [Note 2, Note 3, Note 1]
        assertTrue(manager.moveTab(0, 2))
        assertEquals(3, manager.count)
        assertEquals(2, manager.activeIndex)
        assertEquals("uri://1", manager.activeTab?.uri)
        assertEquals("uri://2", manager.tabs[0].uri)
        assertEquals("uri://3", manager.tabs[1].uri)
        assertEquals("uri://1", manager.tabs[2].uri)

        // Move inactive tab from 1 to 0: [Note 2, Note 3, Note 1] -> [Note 3, Note 2, Note 1]
        // activeIndex (2) is unchanged because move happened below it
        assertTrue(manager.moveTab(1, 0))
        assertEquals(2, manager.activeIndex)
        assertEquals("uri://1", manager.activeTab?.uri)
        assertEquals("uri://3", manager.tabs[0].uri)
        assertEquals("uri://2", manager.tabs[1].uri)

        // Move tab across active tab: move index 2 (active) to 1
        assertTrue(manager.moveTab(2, 1))
        assertEquals(1, manager.activeIndex)
        assertEquals("uri://1", manager.activeTab?.uri)
    }

    @Test
    fun closeMatchingClosesSingleMatchingTabAndAdjustsActiveIndex() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })
        manager.openOrSelectTab("uri://3", "Note 3", { makeSession("uri://3", "Note 3") })

        manager.selectTab(1) // Active is Note 2
        assertEquals(1, manager.activeIndex)

        // Close matching Note 2
        val closed = manager.closeMatching { it.uri == "uri://2" }
        assertEquals(1, closed.size)
        assertEquals("uri://2", closed[0].uri)

        assertEquals(2, manager.count)
        assertEquals(1, manager.activeIndex)
        assertEquals("uri://3", manager.activeTab?.uri)
        assertEquals("uri://1", manager.tabs[0].uri)
        assertEquals("uri://3", manager.tabs[1].uri)
    }

    @Test
    fun closeMatchingClosesMultipleMatchingTabs() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("folder/note1", "Note 1", { makeSession("folder/note1", "Note 1") })
        manager.openOrSelectTab("folder/note2", "Note 2", { makeSession("folder/note2", "Note 2") })
        manager.openOrSelectTab("other/note3", "Note 3", { makeSession("other/note3", "Note 3") })
        manager.openOrSelectTab("folder/note4", "Note 4", { makeSession("folder/note4", "Note 4") })

        manager.selectTab(0) // Active is folder/note1

        // Close all tabs starting with "folder/"
        val closed = manager.closeMatching { it.uri?.startsWith("folder/") == true }
        assertEquals(3, closed.size)

        assertEquals(1, manager.count)
        assertEquals(0, manager.activeIndex)
        assertEquals("other/note3", manager.activeTab?.uri)
    }

    @Test
    fun closeMatchingClosesAllTabs() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })

        val closed = manager.closeMatching { true }
        assertEquals(2, closed.size)
        assertEquals(0, manager.count)
        assertEquals(-1, manager.activeIndex)
    }

    @Test
    fun closeMatchingNoOpWhenNoMatch() {
        val manager = NoteTabManager(maxTabs = 8)
        manager.openOrSelectTab("uri://1", "Note 1", { makeSession("uri://1", "Note 1") })
        manager.openOrSelectTab("uri://2", "Note 2", { makeSession("uri://2", "Note 2") })

        val closed = manager.closeMatching { it.uri == "uri://unknown" }
        assertEquals(0, closed.size)
        assertEquals(2, manager.count)
    }
}

