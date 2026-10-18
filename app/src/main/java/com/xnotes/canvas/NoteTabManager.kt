package com.xnotes.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/**
 * Manages open note tabs, the active tab index, and the 8-tab LRU eviction policy.
 */
class NoteTabManager(val maxTabs: Int = MAX_TABS_DEFAULT) {
    val tabs = mutableStateListOf<NoteTabSession>()
    var activeIndex by mutableIntStateOf(-1)

    val activeTab: NoteTabSession?
        get() = if (activeIndex in tabs.indices) tabs[activeIndex] else null

    val count: Int get() = tabs.size

    /** Find tab by URI */
    fun findTabByUri(uri: String): NoteTabSession? {
        return tabs.firstOrNull { it.uri == uri }
    }

    /** Find index by tab ID */
    fun indexOf(id: String): Int {
        return tabs.indexOfFirst { it.id == id }
    }

    /** Find index by URI */
    fun indexOfUri(uri: String): Int {
        return tabs.indexOfFirst { it.uri == uri }
    }

    /**
     * Add or select a tab. Returns the added or existing session.
     * If tab count exceeds [maxTabs], evicts the oldest tab.
     */
    fun openOrSelectTab(
        uri: String?,
        title: String,
        documentFactory: () -> NoteTabSession,
        onEvict: ((NoteTabSession) -> Unit)? = null,
    ): Pair<NoteTabSession, Boolean> { // (session, isNew)
        if (uri != null) {
            val existingIdx = indexOfUri(uri)
            if (existingIdx >= 0) {
                selectTab(existingIdx)
                return tabs[existingIdx] to false
            }
        }

        // Evict LRU if at or above capacity
        if (tabs.size >= maxTabs) {
            evictOldestCleanTab(onEvict)
        }

        val session = documentFactory()
        tabs.add(session)
        selectTab(tabs.lastIndex)
        return session to true
    }

    fun selectTab(index: Int): Boolean {
        if (index !in tabs.indices) return false
        activeIndex = index
        tabs[index].lastActiveTimestamp = System.currentTimeMillis()
        return true
    }

    fun selectTabByUri(uri: String): Boolean {
        val idx = indexOfUri(uri)
        return if (idx >= 0) selectTab(idx) else false
    }

    /**
     * Closes the tab at [index].
     * Returns the closed tab session and updates [activeIndex].
     */
    fun closeTab(index: Int): NoteTabSession? {
        if (index !in tabs.indices) return null
        val closed = tabs.removeAt(index)
        if (tabs.isEmpty()) {
            activeIndex = -1
        } else if (activeIndex >= tabs.size) {
            activeIndex = tabs.lastIndex
            tabs[activeIndex].lastActiveTimestamp = System.currentTimeMillis()
        } else if (index == activeIndex) {
            // Keep activeIndex pointing to the item that shifted into this slot
            tabs[activeIndex].lastActiveTimestamp = System.currentTimeMillis()
        } else if (index < activeIndex) {
            activeIndex--
        }
        return closed
    }

    /**
     * Moves a tab from [fromIndex] to [toIndex], updating [activeIndex] accordingly.
     */
    fun moveTab(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tabs.indices || toIndex !in tabs.indices || fromIndex == toIndex) return false
        val item = tabs.removeAt(fromIndex)
        tabs.add(toIndex, item)
        activeIndex = when (activeIndex) {
            fromIndex -> toIndex
            in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) -> {
                if (fromIndex < toIndex) activeIndex - 1 else activeIndex + 1
            }
            else -> activeIndex
        }
        return true
    }

    /**
     * Evicts the least-recently-used tab (preferring unmodified clean tabs).
     */
    private fun evictOldestCleanTab(onEvict: ((NoteTabSession) -> Unit)?) {
        val cleanCandidate = tabs
            .filterIndexed { index, tab -> index != activeIndex && !tab.isDirty }
            .minByOrNull { it.lastActiveTimestamp }

        val toEvict = cleanCandidate ?: tabs
            .filterIndexed { index, _ -> index != activeIndex }
            .minByOrNull { it.lastActiveTimestamp }

        if (toEvict != null) {
            val idx = tabs.indexOf(toEvict)
            if (idx >= 0) {
                onEvict?.invoke(toEvict)
                closeTab(idx)
            }
        }
    }

    fun clear() {
        tabs.clear()
        activeIndex = -1
    }

    companion object {
        const val MAX_TABS_DEFAULT = 8
    }
}
