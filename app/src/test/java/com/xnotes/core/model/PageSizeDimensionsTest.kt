package com.xnotes.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSizeDimensionsTest {
    @Test fun templateDimensionsUseMillimetresNotPixels() {
        assertEquals(210.0 to 297.0, PageSize.A4.millimetres(Orientation.PORTRAIT))
        assertEquals(297.0 to 210.0, PageSize.A4.millimetres(Orientation.LANDSCAPE))
        assertEquals(148.0 to 210.0, PageSize.A5.millimetres(Orientation.PORTRAIT))
        // A document-pixel width at 150 DPI is ~1240, not the 210 mm expected by xtemplate.
        assertEquals(210.0, PageSize.pxToMm(
            PageSize.A4.pixels(Orientation.PORTRAIT, PageSize.DEFAULT_DPI).first,
            PageSize.DEFAULT_DPI,
        ), 1e-9)
    }
}
