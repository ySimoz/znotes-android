package com.xnotes.ui

import com.xnotes.core.FakeTextMeasurer
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Document
import com.xnotes.core.model.ImageData
import com.xnotes.core.model.ImageItem
import com.xnotes.core.model.Orientation
import com.xnotes.core.model.PageSize
import com.xnotes.core.model.PageStyle
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TextItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [NoteCreationOptions] and note creation document initialization.
 */
class NoteCreationOptionsTest {

    private val measurer = FakeTextMeasurer()

    private fun sampleOptions(
        includeCover: Boolean = true,
        pageSize: PageSize = PageSize.A4,
        orientation: Orientation = Orientation.PORTRAIT,
        coverColor: Rgba = Rgba(255, 255, 255),
        pageColor: Rgba = Rgba(240, 240, 240),
    ): NoteCreationOptions = NoteCreationOptions(
        pageSize = pageSize,
        orientation = orientation,
        pageStyle = PageStyle(pageColor = pageColor),
        includeCover = includeCover,
        coverStyle = PageStyle(pageColor = coverColor),
    )

    private fun sampleImageData(): ImageData =
        ImageData(file = File("fake_image.png"), width = 800, height = 600)

    @Test
    fun noteCreationWithOptionsSetsCorrectPageCountAndStyles() {
        val options = sampleOptions(includeCover = true)
        val (w, h) = options.pageSize.pixels(options.orientation, PageSize.DEFAULT_DPI)
        val doc = Document.blankPixels(count = 2, width = w, height = h)

        populateBlankDocument(doc, "Project Notes", options, textMeasurer = measurer)

        assertEquals(2, doc.pageCount)
        assertEquals(options.pageStyle, doc.style)
        assertEquals(options.coverStyle, doc.pages[0].style)
    }

    @Test
    fun noteCreationWithoutCoverSetsSinglePage() {
        val options = sampleOptions(includeCover = false)
        val (w, h) = options.pageSize.pixels(options.orientation, PageSize.DEFAULT_DPI)
        val doc = Document.blankPixels(count = 1, width = w, height = h)

        populateBlankDocument(doc, "Quick Memo", options, textMeasurer = measurer)

        assertEquals(1, doc.pageCount)
        assertEquals(options.pageStyle, doc.style)
    }

    @Test
    fun lockedBackgroundPageImageAddedToWritingPagesOnly() {
        val options = sampleOptions(includeCover = true)
        val (w, h) = options.pageSize.pixels(options.orientation, PageSize.DEFAULT_DPI)
        val doc = Document.blankPixels(count = 2, width = w, height = h)
        val image = sampleImageData()

        populateBlankDocument(doc, "Test Note", options, pageImageData = image, textMeasurer = measurer)

        // Cover page (page 0) must NOT receive the writing page background image
        val coverImages = doc.pages[0].items.filterIsInstance<ImageItem>()
        assertTrue("Cover should not have page background image", coverImages.isEmpty())

        // Writing page (page 1) must receive the page background image with locked = true
        val pageImages = doc.pages[1].items.filterIsInstance<ImageItem>()
        assertEquals(1, pageImages.size)
        val bgItem = pageImages.first()
        assertTrue("Page background image item must be locked", bgItem.locked)
        assertEquals(Rect(0.0, 0.0, doc.pages[1].width, doc.pages[1].height), bgItem.rect)
    }

    @Test
    fun lockedCoverImageAndTitleTextItemConfiguredOnCover() {
        val options = sampleOptions(includeCover = true, coverColor = Rgba(255, 255, 255))
        val (w, h) = options.pageSize.pixels(options.orientation, PageSize.DEFAULT_DPI)
        val doc = Document.blankPixels(count = 2, width = w, height = h)
        val coverImage = sampleImageData()

        populateBlankDocument(doc, "Meeting Minutes", options, coverImageData = coverImage, textMeasurer = measurer)

        val cover = doc.pages[0]
        val coverImageItems = cover.items.filterIsInstance<ImageItem>()
        assertEquals(1, coverImageItems.size)
        assertTrue("Cover image item must be locked", coverImageItems.first().locked)
        assertEquals(Rect(0.0, 0.0, cover.width, cover.height), coverImageItems.first().rect)

        val textItems = cover.items.filterIsInstance<TextItem>()
        assertEquals(1, textItems.size)
        val titleItem = textItems.first()
        assertEquals("Meeting Minutes", titleItem.text)
        assertEquals(Rgba(35, 39, 48), titleItem.rgba) // Dark ink for light cover
    }

    @Test
    fun darkCoverSelectsLightInkForTitle() {
        val darkOptions = sampleOptions(includeCover = true, coverColor = Rgba(20, 20, 20))
        val (w, h) = darkOptions.pageSize.pixels(darkOptions.orientation, PageSize.DEFAULT_DPI)
        val doc = Document.blankPixels(count = 2, width = w, height = h)

        populateBlankDocument(doc, "Dark Mode Note", darkOptions, textMeasurer = measurer)

        val titleItem = doc.pages[0].items.filterIsInstance<TextItem>().first()
        assertEquals(Rgba(250, 250, 250), titleItem.rgba) // Light ink for dark cover
    }

    @Test
    fun customPageDimensionsOptionCarriedCorrectly() {
        val options = NoteCreationOptions(
            pageSize = PageSize.CUSTOM,
            orientation = Orientation.PORTRAIT,
            pageStyle = PageStyle(),
            includeCover = false,
            coverStyle = PageStyle(),
            customWidthMm = 180.0,
            customHeightMm = 250.0,
        )
        assertEquals(PageSize.CUSTOM, options.pageSize)
        assertEquals(180.0, options.customWidthMm)
        assertEquals(250.0, options.customHeightMm)
    }
}
