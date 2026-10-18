package com.xnotes.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests custom asset folder directory operations, file filtering, and corruption resilience.
 * Verifies that image lists never crash on corrupted, empty, or non-image assets.
 */
class CustomAssetFoldersTest {

    private val fakePngBytes = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
    )

    private val fakeJpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00.toByte(), 0x10.toByte(), 'J'.code.toByte(), 'F'.code.toByte(),
    )

    @Test
    fun displayNameStripsTimestampSuffix() {
        val original = "landscape-grid-1729188800000.png"
        val displayed = CustomAssetFolders.displayName(original)
        assertEquals("landscape-grid", displayed)
    }

    @Test
    fun displayNamePreservesUntimestampedNames() {
        assertEquals("dark_dots", CustomAssetFolders.displayName("dark_dots.jpg"))
        assertEquals("plain", CustomAssetFolders.displayName("plain"))
    }

    @Test
    fun isImageNameRecognizesSupportedExtensions() {
        assertTrue(CustomAssetFolders.isImageName("cover.png"))
        assertTrue(CustomAssetFolders.isImageName("photo.JPEG"))
        assertTrue(CustomAssetFolders.isImageName("pattern.webp"))
        assertTrue(CustomAssetFolders.isImageName("texture.bmp"))
        assertFalse(CustomAssetFolders.isImageName("notes.txt"))
        assertFalse(CustomAssetFolders.isImageName("template.xtemplate"))
        assertFalse(CustomAssetFolders.isImageName("export.pdf"))
    }

    @Test
    fun storeAndLoadImagesFromDirectory() {
        val root = Files.createTempDirectory("xnotes-assets-test").toFile()
        try {
            val stored = CustomAssetFolders.storeImage(
                rootDirectory = root,
                bytes = fakePngBytes,
                sourceName = "my-cover.png",
                kind = CustomAssetFolders.Kind.COVER,
            )
            assertTrue(stored)

            val images = CustomAssetFolders.loadImages(root, CustomAssetFolders.Kind.COVER)
            assertEquals(1, images.size)
            assertTrue(images[0].name.startsWith("my-cover-"))
            assertNotNull(images[0].file)
            assertTrue(images[0].readBytes().contentEquals(fakePngBytes))
            assertEquals(CustomAssetFolders.displayName(images[0].name), images[0].displayName)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun loadImagesIgnoresNonImageAndCorruptFilesGracefully() {
        val tempFolder = Files.createTempDirectory("xnotes-corrupt-test").toFile()
        try {
            // 1. Valid image
            File(tempFolder, "good.png").writeBytes(fakePngBytes)

            // 2. Non-image file with non-image extension
            File(tempFolder, "readme.txt").writeText("Not an image")

            // 3. Sub-directory (must be ignored)
            File(tempFolder, "nested_dir").mkdirs()

            // 4. Zero-byte image file (corrupt)
            File(tempFolder, "empty.jpg").writeBytes(byteArrayOf())

            // 5. Corrupt file with image extension but invalid header bytes
            File(tempFolder, "corrupt.png").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

            val loaded = CustomAssetFolders.loadImagesFromDirectory(tempFolder)

            // Only the valid PNG must be loaded; all invalid/corrupt entries skipped gracefully
            assertEquals(1, loaded.size)
            assertEquals("good.png", loaded[0].name)
            assertNotNull(loaded[0].file)
            assertTrue(loaded[0].readBytes().contentEquals(fakePngBytes))
        } finally {
            tempFolder.deleteRecursively()
        }
    }

    @Test
    fun customImageStoresLightweightReferenceAndReadsBytesOnDemand() {
        val tempFolder = Files.createTempDirectory("xnotes-custom-image-test").toFile()
        try {
            val file = File(tempFolder, "photo-1729188000000.png")
            file.writeBytes(fakePngBytes)

            val image = CustomAssetFolders.CustomImage("photo-1729188000000.png", file = file)
            assertEquals("photo", image.displayName)
            assertEquals(file, image.file)
            assertTrue(image.readBytes().contentEquals(fakePngBytes))
        } finally {
            tempFolder.deleteRecursively()
        }
    }

    @Test
    fun deleteImageRemovesTargetFile() {
        val root = Files.createTempDirectory("xnotes-delete-test").toFile()
        try {
            CustomAssetFolders.storeImage(root, fakeJpegBytes, "page-bg.jpg", CustomAssetFolders.Kind.PAGE)
            val images = CustomAssetFolders.loadImages(root, CustomAssetFolders.Kind.PAGE)
            assertEquals(1, images.size)

            val deleted = CustomAssetFolders.deleteImage(root, CustomAssetFolders.Kind.PAGE, images[0].id)
            assertTrue(deleted)

            val remaining = CustomAssetFolders.loadImages(root, CustomAssetFolders.Kind.PAGE)
            assertTrue(remaining.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
