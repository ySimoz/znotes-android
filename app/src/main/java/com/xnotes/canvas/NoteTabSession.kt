package com.xnotes.canvas

import com.xnotes.core.history.History
import com.xnotes.core.model.Document

/**
 * In-memory tab session for an opened document.
 * Retains vector [Document], independent [History] undo/redo stack, and viewport coordinates.
 */
data class NoteTabSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    var uri: String? = null,
    var title: String = "Untitled",
    val document: Document,
    val history: History = History(),
    var zoom: Double = 1.0,
    var scrollX: Double = 0.0,
    var scrollY: Double = 0.0,
    var viewOverrides: ViewOverrides = ViewOverrides(),
    var didInitialFit: Boolean = false,
    var autosaveUri: String? = null,
    var lastActiveTimestamp: Long = System.currentTimeMillis(),
) {
    val isDirty: Boolean get() = document.dirty
}
