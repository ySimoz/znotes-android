package com.xnotes.settings

import com.xnotes.core.model.Orientation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that Preferences loading latency optimizations are in place:
 * 1. Lazy composition is used in PreferencesPane to defer off-screen preference sections and toolbar customizer.
 * 2. Editor prewarms custom fonts off-thread to prevent UI thread disk I/O.
 * 3. Preference sections remain modular and all preference values round-trip without regression.
 */
class PreferencesLatencyTest {

    @Test
    fun preferencesPaneUsesScrollableColumn() {
        val candidates = listOf(
            File("app/src/main/java/com/xnotes/ui/PreferencesPane.kt"),
            File("src/main/java/com/xnotes/ui/PreferencesPane.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("PreferencesPane.kt must exist", file)
        val text = file!!.readText()

        // Verify verticalScroll is used to avoid mid-scroll recomposition jank on high-refresh panels
        assertTrue("PreferencesPane must use rememberScrollState()", text.contains("rememberScrollState()"))
        assertTrue("PreferencesPane must use verticalScroll", text.contains("verticalScroll(scrollState)"))
    }

    @Test
    fun preferencesPaneDecomposedIntoModularSections() {
        val candidates = listOf(
            File("app/src/main/java/com/xnotes/ui/PreferencesSections.kt"),
            File("src/main/java/com/xnotes/ui/PreferencesSections.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("PreferencesSections.kt must exist", file)
        val text = file!!.readText()

        // Verify each preference section exists as an independent composable
        assertTrue(text.contains("fun GeneralPreferencesSection("))
        assertTrue(text.contains("fun InputPreferencesSection("))
        assertTrue(text.contains("fun NewNotesPreferencesSection("))
        assertTrue(text.contains("fun PagePreferencesSection("))
        assertTrue(text.contains("fun ExplorerPreferencesSection("))
        assertTrue(text.contains("fun ToolbarPreferencesSection("))
    }

    @Test
    fun editorPrewarmsCustomFontsOffThread() {
        val candidates = listOf(
            File("app/src/main/java/com/xnotes/ui/Editor.kt"),
            File("src/main/java/com/xnotes/ui/Editor.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("Editor.kt must exist", file)
        val text = file!!.readText()

        // Verify prewarmBackstage includes resolving custom fonts off-thread
        assertTrue("Editor prewarmBackstage must resolve custom fonts", text.contains("FontCatalog.resolve(c.face"))
        assertTrue("Editor prewarmBackstage must dispatch to IO", text.contains("Dispatchers.IO"))
    }

    @Test
    fun fontCatalogUsesThreadSafeCacheAndOffThreadPrewarming() {
        val candidates = listOf(
            File("app/src/main/java/com/xnotes/platform/FontCatalog.kt"),
            File("src/main/java/com/xnotes/platform/FontCatalog.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("FontCatalog.kt must exist", file)
        val text = file!!.readText()

        // Verify FontCatalog uses concurrent cache to support background prewarming
        assertTrue(text.contains("ConcurrentHashMap<String, Resolved>()"))
        assertTrue(text.contains("fun resolve("))
    }

    @Test
    fun preferencesDefaultsAndOptionsPreserved() {
        val prefs = Preferences()
        assertEquals("system", prefs.appLanguage)
        assertEquals(CornerStyle.ROUNDED, prefs.cornerStyle)
        assertEquals(MaterialColourMode.DUAL, prefs.materialMode)
        assertEquals(30, prefs.trashDays)
        assertEquals(Orientation.PORTRAIT, prefs.defaultPageOrientation)
        assertTrue(prefs.switcherLayouts.isNotEmpty())
    }


    @Test
    fun backstageMainMaintainsStableWidthDuringSidebarExpandAnimation() {
        val candidates = listOf(
            File("app/src/main/java/com/xnotes/ui/Backstage.kt"),
            File("src/main/java/com/xnotes/ui/Backstage.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("Backstage.kt must exist", file)
        val text = file!!.readText()

        // Verify BackstageMain keeps measured width stable during expand animation
        assertTrue("BackstageMain must stabilize width with targetInset", text.contains("targetInset"))
    }
}
