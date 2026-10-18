package com.xnotes.canvas

import com.xnotes.core.model.CanvasItem
import com.xnotes.core.model.deepCopyAll
import com.xnotes.core.pal.TextMeasurer

/**
 * Process-wide in-app clipboard holding [CanvasItem]s (strokes, shapes, text, images, connectors).
 * Preserves vector fidelity and allows copy/cut/paste across open notes, tabs, and canvas modes.
 */
object SharedItemClipboard {
    private val items = mutableListOf<CanvasItem>()
    var fromCut: Boolean = false
        private set

    fun hasItems(): Boolean = synchronized(items) { items.isNotEmpty() }

    fun copy(source: List<CanvasItem>, measurer: TextMeasurer) {
        synchronized(items) {
            if (source.isEmpty()) return
            items.clear()
            fromCut = false
            items.addAll(source.deepCopyAll(measurer))
        }
    }

    fun cut(source: List<CanvasItem>, measurer: TextMeasurer) {
        synchronized(items) {
            if (source.isEmpty()) return
            items.clear()
            fromCut = true
            items.addAll(source.deepCopyAll(measurer))
        }
    }

    fun getClones(measurer: TextMeasurer): List<CanvasItem> = synchronized(items) {
        return items.deepCopyAll(measurer)
    }

    fun consumeCut() {
        synchronized(items) {
            if (fromCut) {
                items.clear()
                fromCut = false
            }
        }
    }

    fun clear() {
        synchronized(items) {
            items.clear()
            fromCut = false
        }
    }

    val count: Int get() = synchronized(items) { items.size }
}
