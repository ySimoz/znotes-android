package com.xnotes.platform

import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Document
import com.xnotes.core.model.Page
import com.xnotes.core.model.marginStrips
import com.xnotes.core.model.resolvedAccentColor
import com.xnotes.core.model.resolvedColors
import com.xnotes.core.model.resolvedParams
import com.xnotes.core.model.resolvedPatternColor
import com.xnotes.core.model.resolvedSpacing
import com.xnotes.core.model.resolvedTemplate
import com.xnotes.core.pal.Renderer
import com.xnotes.core.template.Template
import com.xnotes.core.template.TemplateLayoutCache
import com.xnotes.core.template.TemplatePage
import com.xnotes.core.template.TemplatePainter
import com.xnotes.core.template.TemplateValues

/**
 * Evaluates template parameters from page style chains and renders template geometry onto paper.
 */
internal object TemplatePaperPainter {

    /** Converts page parameter values into template millimetres. */
    fun values(t: Template, doc: Document, page: Page): TemplateValues {
        val numbers = HashMap(page.resolvedParams(doc))
        val spacing = t.spacingParam
        page.resolvedSpacing(doc)?.let { px ->
            if (spacing != null) numbers[spacing.name] = px * 25.4 / doc.dpi
        }
        return TemplateValues(numbers, page.resolvedColors(doc))
    }

    /** Paints the resolved template into [region] with graceful fallback on corrupt layouts. */
    fun paint(
        r: Renderer,
        doc: Document,
        page: Page,
        cover: Rect,
        region: Rect,
        layouts: TemplateLayoutCache,
    ) {
        val key = page.resolvedTemplate(doc)
        val t = TemplateLibrary.template(doc, key) ?: return
        val mm = doc.dpi / 25.4
        val content = Rect(0.0, 0.0, page.width, page.height)
        val papers = if (page.pdfPage == null) listOf(cover) else marginStrips(cover, content)
        val values = values(t, doc, page)
        val ink = page.resolvedPatternColor(doc)
        val accent = page.resolvedAccentColor(doc)

        for (paper in papers) {
            if (!paper.intersects(region)) continue
            val tp = TemplatePage(
                page.width / mm, page.height / mm,
                Rect(paper.x / mm, paper.y / mm, paper.w / mm, paper.h / mm),
                ink, accent, values,
            )
            runCatching {
                TemplatePainter.paint(r, layouts.layout(t, tp), mm, region)
            }
        }
    }
}
