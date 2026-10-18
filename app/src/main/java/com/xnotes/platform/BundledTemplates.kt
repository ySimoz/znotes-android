package com.xnotes.platform

import android.content.res.AssetManager
import com.xnotes.format.TemplateReader

/**
 * Discovers and parses pre-packaged .xtemplate assets shipped with the application binary.
 */
internal object BundledTemplates {

    private const val ASSET_DIR = "templates"
    private const val EXAMPLES_DIR = "templates/examples"
    private const val EXT = ".xtemplate"

    /** Reads all built-in rulings and bundled example templates from [am]. */
    fun load(am: AssetManager?): List<TemplateLibrary.Entry> {
        if (am == null) return emptyList()

        fun read(path: String) = runCatching {
            am.open(path).use { String(it.readBytes(), Charsets.UTF_8) }
        }.getOrNull()

        val out = ArrayList<TemplateLibrary.Entry>()
        for (id in listOf("lines", "dots", "grid")) {
            val text = read("$ASSET_DIR/$id$EXT") ?: continue
            val t = runCatching { TemplateReader.read(text) }.getOrNull() ?: continue
            out += TemplateLibrary.Entry(id, t, text, TemplateLibrary.Source.BUILT_IN)
        }

        val examples = (runCatching { am.list(EXAMPLES_DIR) }.getOrNull() ?: emptyArray()).filter { it.endsWith(EXT) }
        out += examples.mapNotNull { name ->
            val text = read("$EXAMPLES_DIR/$name") ?: return@mapNotNull null
            val t = runCatching { TemplateReader.read(text) }.getOrNull() ?: return@mapNotNull null
            TemplateLibrary.Entry(TemplateLibrary.keyOf(text), t, text, TemplateLibrary.Source.BUNDLED)
        }.sortedBy { it.template.name.lowercase() }

        return out
    }

    /**
     * Loads a single built-in template ("lines", "dots", "grid") on demand without
     * waiting for the full template scan to finish.
     */
    fun loadSingleBuiltIn(am: AssetManager?, id: String): TemplateLibrary.Entry? {
        if (am == null) return null
        val text = runCatching {
            am.open("$ASSET_DIR/$id$EXT").use { String(it.readBytes(), Charsets.UTF_8) }
        }.getOrNull() ?: return null
        val t = runCatching { TemplateReader.read(text) }.getOrNull() ?: return null
        return TemplateLibrary.Entry(id, t, text, TemplateLibrary.Source.BUILT_IN)
    }
}
