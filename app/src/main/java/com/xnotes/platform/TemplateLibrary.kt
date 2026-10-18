package com.xnotes.platform

import android.content.Context
import android.net.Uri
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.PageTemplates
import com.xnotes.core.pal.Renderer
import com.xnotes.core.template.Template
import com.xnotes.core.template.TemplateEval
import com.xnotes.core.template.TemplateLayoutCache
import com.xnotes.core.template.TemplateValues
import com.xnotes.format.TemplateFormatException
import com.xnotes.format.TemplateReader
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages discovery, caching, and painting of page templates.
 * Dual-storage mirrors imported templates into both app-private and notes root SAF storage.
 */
object TemplateLibrary {

    /** Where a template comes from; [NOTE] is one a note carries that the library does not have. */
    enum class Source { BUILT_IN, BUNDLED, IMPORTED, NOTE }

    class Entry(val key: String, val template: Template, val text: String, val source: Source)

    private const val EXT = ".xtemplate"

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var importDir: File? = null
    @Volatile
    private var storageTree: Uri? = null
    @Volatile
    private var storageDirId: String? = null
    @Volatile
    private var bundled: List<Entry> = emptyList()
    private val imported = ConcurrentHashMap<String, Entry>()
    private val parsed = ConcurrentHashMap<String, Template>()

    private val eval = TemplateEval(AndroidGlyphOutliner())
    val layouts = TemplateLayoutCache(eval)

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        importDir = File(app.filesDir, "templates")
        bundled = BundledTemplates.load(app.assets)
        reloadImported()
    }

    /** Sets the directory for imported user templates (used in testing or custom storage). */
    internal fun configureImportDir(dir: File) {
        importDir = dir
        reloadImported()
    }

    /** Sets the bundled templates (used in testing or custom environments). */
    internal fun configureBundled(templates: List<Entry>) {
        bundled = templates
    }

    /** Every template to offer, built-ins first, then the bundled examples, then imports. */
    fun all(): List<Entry> = bundled + imported.values.sortedBy { it.template.name.lowercase() }

    /** Store imported templates in a hidden folder beside the selected notes root as well as privately. */
    fun configureStorageTree(treeUri: String?) {
        val context = appContext
        storageTree = treeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        storageDirId = if (context != null && storageTree != null) {
            TemplateStorage.ensureStorageDir(context, storageTree!!)
        } else null
        reloadImported()
    }

    fun entry(key: String): Entry? {
        val found = bundled.firstOrNull { it.key == key } ?: imported[key]
        if (found != null) return found
        if (PageTemplates.isBuiltIn(key)) {
            val am = appContext?.assets
            if (am != null) {
                return BundledTemplates.loadSingleBuiltIn(am, key)
            }
        }
        return null
    }

    /** The key of a template that is not built in: a hash of its text. */
    fun keyOf(text: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * The template [key] names for [doc]: the note's own embedded copy first, so a note keeps
     * drawing what it was drawn with, then the library.
     */
    fun template(doc: Document, key: String): Template? {
        if (key == PageTemplates.NONE) return null
        if (!PageTemplates.isBuiltIn(key)) doc.templates[key]?.let { return parse(key, it) }
        return entry(key)?.template
    }

    /** [doc] with [key]'s text embedded, when it is a library template the note does not carry yet. */
    fun embed(doc: Document, key: String?) {
        if (key == null || key == PageTemplates.NONE || PageTemplates.isBuiltIn(key) || doc.templates.containsKey(key)) return
        val text = entry(key)?.text ?: return
        doc.templates = doc.templates + (key to text)
    }

    /** Adopt [text] as a user template; throws [TemplateFormatException] when it is not one. */
    fun import(text: String): Entry {
        var importText = text
        var t = TemplateReader.read(importText)
        var key = keyOf(importText)
        val existing = entry(key)
        if (existing != null) {
            if (existing.source == Source.IMPORTED) return existing
            // Preserve design and name, but add unique metadata so custom copy has its own key
            importText = JSONObject(text).put("xnotesImportedCopy", java.util.UUID.randomUUID().toString()).toString(2)
            t = TemplateReader.read(importText)
            key = keyOf(importText)
        }
        val dir = importDir ?: throw TemplateFormatException("template storage is unavailable")
        dir.mkdirs()
        File(dir, key + EXT).writeText(importText, Charsets.UTF_8)
        appContext?.let { ctx ->
            val tree = storageTree
            val dirId = storageDirId
            if (tree != null && dirId != null) {
                TemplateStorage.writeExternal(ctx, tree, dirId, key + EXT, importText.toByteArray(Charsets.UTF_8))
            }
        }
        return Entry(key, t, importText, Source.IMPORTED).also { imported[key] = it; parsed[key] = t }
    }

    fun remove(key: String) {
        imported.remove(key) ?: return
        importDir?.let { File(it, key + EXT).delete() }
        appContext?.let { ctx ->
            val tree = storageTree
            val dirId = storageDirId
            if (tree != null && dirId != null) {
                TemplateStorage.deleteExternal(ctx, tree, dirId, key + EXT)
            }
        }
    }

    fun reloadImported() {
        val newImported = HashMap<String, Entry>()
        importDir?.listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(EXT)) return@forEach
            val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
            val t = runCatching { TemplateReader.read(text) }.getOrNull() ?: return@forEach
            val key = keyOf(text)
            newImported[key] = Entry(key, t, text, Source.IMPORTED)
        }
        val ctx = appContext
        val tree = storageTree
        val dirId = storageDirId
        if (ctx != null && tree != null && dirId != null) {
            TemplateStorage.externalChildren(ctx, tree, dirId).forEach { (name, id) ->
                if (!name.endsWith(EXT)) return@forEach
                val text = TemplateStorage.readExternal(ctx, tree, id) ?: return@forEach
                val t = runCatching { TemplateReader.read(text) }.getOrNull() ?: return@forEach
                val key = keyOf(text)
                newImported[key] = Entry(key, t, text, Source.IMPORTED)
            }
        }
        imported.clear()
        imported.putAll(newImported)
    }

    private fun parse(key: String, text: String): Template? =
        parsed[key] ?: runCatching { TemplateReader.read(text) }.getOrNull()?.also { parsed[key] = it }

    // --- painting ---

    /** [page]'s parameter values from its style chain, lengths converted to template mm. */
    fun values(t: Template, doc: Document, page: Page): TemplateValues =
        TemplatePaperPainter.values(t, doc, page)

    /** Paint [page]'s template into [r] in page-local content px. */
    fun paint(r: Renderer, doc: Document, page: Page, cover: Rect, region: Rect) =
        TemplatePaperPainter.paint(r, doc, page, cover, region, layouts)
}
