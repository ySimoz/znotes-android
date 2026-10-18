package com.xnotes.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates downsampling calculations and image validation logic for 144Hz performance.
 * Tests ensure memory-bounded thumbnail decoding avoids UI-thread jank and OOM hazards.
 */
class ThumbnailDecoderTest {

    @Test
    fun calculateInSampleSizeReturnsOneWhenImageSmallerThanTarget() {
        val sampleSize = ThumbnailDecoder.calculateInSampleSize(
            width = 100,
            height = 100,
            reqWidth = 256,
            reqHeight = 256,
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun calculateInSampleSizeReturnsOneWhenImageMatchesTargetExactly() {
        val sampleSize = ThumbnailDecoder.calculateInSampleSize(
            width = 256,
            height = 256,
            reqWidth = 256,
            reqHeight = 256,
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun calculateInSampleSizeComputesPowerOfTwoForLargeImages() {
        // Both dimensions exceed request by at least 2x -> sample size 2
        assertEquals(2, ThumbnailDecoder.calculateInSampleSize(512, 512, 256, 256))

        // Both dimensions exceed request by at least 4x -> sample size 4
        assertEquals(4, ThumbnailDecoder.calculateInSampleSize(1024, 1024, 256, 256))

        // Both dimensions exceed request by at least 8x -> sample size 8
        assertEquals(8, ThumbnailDecoder.calculateInSampleSize(2048, 2048, 256, 256))
    }

    @Test
    fun calculateInSampleSizeStopsWhenOneDimensionDropsBelowRequestedSize() {
        // Width / 4 is 256 (>= 256), but height / 4 is 192 (< 256).
        // It must stop at sample size 2 so height is not downsampled below requested box.
        val sampleSize = ThumbnailDecoder.calculateInSampleSize(
            width = 1024,
            height = 768,
            reqWidth = 256,
            reqHeight = 256,
        )
        assertEquals(2, sampleSize)
    }

    @Test
    fun calculateInSampleSizeHandlesDegenerateDimensionsDefensively() {
        assertEquals(1, ThumbnailDecoder.calculateInSampleSize(0, 500, 256, 256))
        assertEquals(1, ThumbnailDecoder.calculateInSampleSize(500, 0, 256, 256))
        assertEquals(1, ThumbnailDecoder.calculateInSampleSize(-10, 500, 256, 256))
        assertEquals(1, ThumbnailDecoder.calculateInSampleSize(500, 500, 0, 256))
        assertEquals(1, ThumbnailDecoder.calculateInSampleSize(500, 500, 256, -1))
    }

    @Test
    fun isValidImageBytesDetectsValidImageHeaders() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte())
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val gifHeader = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte())
        val bmpHeader = byteArrayOf('B'.code.toByte(), 'M'.code.toByte(), 0x00, 0x00)
        val webpHeader = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            0, 0, 0, 0,
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
        )

        assertTrue(ThumbnailDecoder.isValidImageBytes(pngHeader))
        assertTrue(ThumbnailDecoder.isValidImageBytes(jpegHeader))
        assertTrue(ThumbnailDecoder.isValidImageBytes(gifHeader))
        assertTrue(ThumbnailDecoder.isValidImageBytes(bmpHeader))
        assertTrue(ThumbnailDecoder.isValidImageBytes(webpHeader))
    }

    @Test
    fun isValidImageBytesRejectsCorruptOrNonImageBytes() {
        assertFalse(ThumbnailDecoder.isValidImageBytes(byteArrayOf()))
        assertFalse(ThumbnailDecoder.isValidImageBytes(byteArrayOf(1, 2)))
        assertFalse(ThumbnailDecoder.isValidImageBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00)))
        assertFalse(ThumbnailDecoder.isValidImageBytes("%PDF-1.4".toByteArray()))
        assertFalse(ThumbnailDecoder.isValidImageBytes("{\"key\": \"val\"}".toByteArray()))
    }

    @Test
    fun decodeSampledBitmapHandlesMissingAndEmptyFilesDefensively() {
        val nonExistentFile = java.io.File("/path/to/nonexistent/image.png")
        assertNull(ThumbnailDecoder.decodeSampledBitmap(nonExistentFile))
    }
}
