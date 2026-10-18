package com.xnotes.platform

import com.xnotes.core.model.Document
import com.xnotes.core.model.PageTemplates
import com.xnotes.format.TemplateReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests template discovery, parsing, duplicate management, and corruption fallbacks.
 * Ensures corrupt template packages degrade gracefully to blank paper without crashes.
 */
class TemplateLibraryTest {

    private val sampleTemplateJson = """
        {
          "xtemplate": 1,
          "id": "com.test.sample",
          "name": "Sample Ruled",
          "license": "MIT",
          "strokeWidth": "0.254mm",
          "items": [
            { "type": "hline", "y": 10.0 }
          ]
        }
    """.trimIndent()

    @Test
    fun templateDiscoveryAndParsing() {
        val tempDir = Files.createTempDirectory("xnotes-template-discovery").toFile()
        try {
            TemplateLibrary.configureImportDir(tempDir)

            val entry = TemplateLibrary.import(sampleTemplateJson)
            assertEquals("Sample Ruled", entry.template.name)
            assertEquals(TemplateLibrary.Source.IMPORTED, entry.source)

            val retrieved = TemplateLibrary.entry(entry.key)
            assertNotNull(retrieved)
            assertEquals(entry.key, retrieved?.key)
            assertEquals("Sample Ruled", retrieved?.template?.name)

            val all = TemplateLibrary.all()
            assertTrue(all.any { it.key == entry.key })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reloadImportedGracefullyIgnoresCorruptFiles() {
        val tempDir = Files.createTempDirectory("xnotes-template-corrupt").toFile()
        try {
            TemplateLibrary.configureImportDir(tempDir)

            // 1. Valid template
            val valid = TemplateLibrary.import(sampleTemplateJson)

            // 2. Corrupted template file with invalid JSON syntax
            File(tempDir, "corrupt-syntax.xtemplate").writeText("{ unclosed json: [ }")

            // 3. Corrupted template file missing mandatory xtemplate fields
            File(tempDir, "corrupt-schema.xtemplate").writeText("{\"foo\": \"bar\"}")

            // 4. Non-xtemplate file
            File(tempDir, "notes.txt").writeText("ignored text")

            // Reload imported templates from disk
            TemplateLibrary.reloadImported()

            // Corrupt files must be skipped without crashing; valid template remains intact
            assertNotNull(TemplateLibrary.entry(valid.key))
            assertEquals(1, TemplateLibrary.all().count { it.source == TemplateLibrary.Source.IMPORTED })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun templateReturnsNullOnMissingOrNoneKeyForBlankPaperFallback() {
        val doc = Document.blank(1)
        assertNull(TemplateLibrary.template(doc, PageTemplates.NONE))
        assertNull(TemplateLibrary.template(doc, "non_existent_key_12345"))
    }

    @Test
    fun importBundledExampleGeneratesUniqueKey() {
        val tempDir = Files.createTempDirectory("xnotes-template-bundled").toFile()
        try {
            TemplateLibrary.configureImportDir(tempDir)
            val bundledEntry = TemplateLibrary.Entry(
                TemplateLibrary.keyOf(sampleTemplateJson),
                TemplateReader.read(sampleTemplateJson),
                sampleTemplateJson,
                TemplateLibrary.Source.BUNDLED,
            )
            TemplateLibrary.configureBundled(listOf(bundledEntry))

            val entry1 = TemplateLibrary.import(sampleTemplateJson)
            val entry2 = TemplateLibrary.import(sampleTemplateJson)

            // Keys must be unique and different from bundled template key
            assertNotEquals(bundledEntry.key, entry1.key)
            assertNotEquals(entry1.key, entry2.key)

            // Both entries must contain xnotesImportedCopy metadata
            assertTrue(entry1.text.contains("xnotesImportedCopy"))
            assertTrue(entry2.text.contains("xnotesImportedCopy"))

            // Sources must be marked as IMPORTED
            assertEquals(TemplateLibrary.Source.IMPORTED, entry1.source)
            assertEquals(TemplateLibrary.Source.IMPORTED, entry2.source)

            // Distinct files must be persisted to the import directory
            assertTrue(File(tempDir, entry1.key + ".xtemplate").exists())
            assertTrue(File(tempDir, entry2.key + ".xtemplate").exists())
        } finally {
            TemplateLibrary.configureBundled(emptyList())
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun importDuplicateImportedReturnsExisting() {
        val tempDir = Files.createTempDirectory("xnotes-template-duplicate-import").toFile()
        try {
            TemplateLibrary.configureImportDir(tempDir)
            val entry1 = TemplateLibrary.import(sampleTemplateJson)
            val entry2 = TemplateLibrary.import(sampleTemplateJson)

            // Importing an already-imported user template returns the existing entry
            assertEquals(entry1.key, entry2.key)
            assertEquals(TemplateLibrary.Source.IMPORTED, entry1.source)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun removeTemplateEvictsEntryAndDeletesFile() {
        val tempDir = Files.createTempDirectory("xnotes-template-remove").toFile()
        try {
            TemplateLibrary.configureImportDir(tempDir)
            val entry = TemplateLibrary.import(sampleTemplateJson)
            assertNotNull(TemplateLibrary.entry(entry.key))

            TemplateLibrary.remove(entry.key)
            assertNull(TemplateLibrary.entry(entry.key))
            assertFalse(File(tempDir, entry.key + ".xtemplate").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
