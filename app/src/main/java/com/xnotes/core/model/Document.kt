package com.xnotes.core.model

import com.xnotes.core.text.TextFlow
import com.xnotes.core.util.Paths

/** A user bookmark into a document (spec 02 §3). */
data class Bookmark(var page: Int, var label: String)

/** An item in the hierarchical table of contents (uncapped nesting tree). */
data class OutlineItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var page: Int,
    var title: String,
    var level: Int = 0,
    var expanded: Boolean = true,
)

/**
 * An ordered collection of pages plus on-disk identity (spec 02 §3). New notes
 * open with one blank page at the user's default page size/orientation.
 */
class Document(
    val pages: MutableList<Page> = mutableListOf(),
    var dpi: Int = PageSize.DEFAULT_DPI,
    var path: String? = null,
    /** Real file name from the storage provider, when known (overrides [path]-derived title). */
    var displayName: String? = null,
    var dirty: Boolean = false,
    /**
     * Embedded source PDF (for PDF-imported notes), so the note stays self-contained. Held as a
     * **file** on disk, not bytes in RAM: the file is memory-mapped by the renderer and streamed
     * into/out of the `.xnote` bundle, so even a very large PDF never has to fit in the heap. The
     * file lives in a private cache dir owned by the platform layer (which manages its lifetime).
     */
    var pdfFile: java.io.File? = null,
    val bookmarks: MutableList<Bookmark> = mutableListOf(),
    val outline: MutableList<OutlineItem> = mutableListOf(),
    /** Document-wide ("all pages") style override; per-page [Page.style] layers on top. */
    var style: PageStyle = PageStyle(),
    /** Document-wide ("all pages") margin override; per-page [Page.margins] layers on top. */
    var margins: PageMargins = PageMargins(),
    /** The document-wide flowing rich text (empty until typed into; persisted only when non-empty). */
    val flow: TextFlow = TextFlow(),
    /** When the note was created (epoch ms), or null for files written before this was recorded. */
    var created: Long? = null,
    /**
     * The `.xtemplate` files this note's styles name, by key, carried inside the bundle so the note
     * draws the same on any device. Built-in rulings are never here. Replaced whole, never mutated.
     */
    var templates: Map<String, String> = emptyMap(),
) {
    /** Transient: set by the codec when legacy ink was compacted at load (debug overlay readout). */
    var compactedOnLoad = false

    /** Derived: the storage display name (or path) base name without extension, or "Untitled". */
    val title: String
        get() = displayName?.let { Paths.stem(it) }
            ?: path?.let { Paths.stem(it) }
            ?: "Untitled"

    val hasPdf: Boolean get() = pdfFile != null

    /** Number of pages in the document. */
    val pageCount: Int get() = pages.size

    /**
     * Appends a blank page sized [width] × [height] and marks the document
     * dirty. The UI passes the current page's dimensions so a note stays uniform.
     */
    fun addPage(width: Double, height: Double, pdfPage: Int? = null): Page {
        val page = Page(width, height, pdfPage = pdfPage)
        pages.add(page)
        dirty = true
        return page
    }

    /**
     * Moves the page at [fromIndex] to [toIndex] and marks the document dirty.
     * Bookmarks pointing to affected pages have their page index updated so they
     * remain attached to the same pages.
     *
     * Returns true if moved, or false if indices are invalid or identical.
     */
    fun movePage(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) {
            return false
        }

        val page = pages.removeAt(fromIndex)
        pages.add(toIndex, page)

        for (bookmark in bookmarks) {
            bookmark.page = reorderedIndex(bookmark.page, fromIndex, toIndex)
        }
        for (item in outline) {
            item.page = reorderedIndex(item.page, fromIndex, toIndex)
        }

        dirty = true
        return true
    }

    /** Ensure outline nesting levels never jump by more than 1 relative to their predecessor. */
    fun normalizeOutlineLevels() {
        var prevLevel = -1
        for (item in outline) {
            if (item.level > prevLevel + 1) {
                item.level = (prevLevel + 1).coerceAtLeast(0)
            }
            if (item.level < 0) item.level = 0
            prevLevel = item.level
        }
    }

    /** Synchronizes bookmarks and outline entries when a page is inserted at [at]. */
    fun onPageInserted(at: Int) {
        for (bm in bookmarks) {
            if (bm.page >= at) bm.page++
        }
        for (item in outline) {
            if (item.page >= at) item.page++
        }
        dirty = true
    }

    /** Synchronizes bookmarks and outline entries when a page at [at] is deleted, promoting children. */
    fun onPageDeleted(at: Int) {
        val bIt = bookmarks.iterator()
        while (bIt.hasNext()) {
            val bm = bIt.next()
            if (bm.page == at) bIt.remove()
            else if (bm.page > at) bm.page--
        }
        val oIt = outline.iterator()
        while (oIt.hasNext()) {
            val item = oIt.next()
            if (item.page == at) oIt.remove()
            else if (item.page > at) item.page--
        }
        normalizeOutlineLevels()
        dirty = true
    }

    companion object {
        const val DEFAULT_NEW_PAGES = 1

        /**
         * Computes the new index of [currentIndex] after an item is moved from [fromIndex] to [toIndex].
         */
        fun reorderedIndex(currentIndex: Int, fromIndex: Int, toIndex: Int): Int = when {
            currentIndex == fromIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }

        /** A blank document of [count] pages of the given size/orientation. */
        fun blank(
            count: Int = DEFAULT_NEW_PAGES,
            size: PageSize = PageSize.A4,
            orientation: Orientation = Orientation.PORTRAIT,
            dpi: Int = PageSize.DEFAULT_DPI,
        ): Document {
            val doc = Document(dpi = dpi)
            repeat(count) { doc.pages.add(Page.blank(size, orientation, dpi)) }
            return doc
        }

        /** A blank document of [count] pages measured in document pixels, for a custom size. */
        fun blankPixels(
            count: Int = DEFAULT_NEW_PAGES,
            width: Double,
            height: Double,
            dpi: Int = PageSize.DEFAULT_DPI,
        ): Document {
            val doc = Document(dpi = dpi)
            repeat(count) { doc.pages.add(Page(width, height)) }
            return doc
        }
    }
}
