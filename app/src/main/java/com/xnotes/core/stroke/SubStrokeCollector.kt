package com.xnotes.core.stroke

import com.xnotes.core.model.Stroke

/**
 * Result of clipping a [Stroke] against a closed polygon.
 */
data class StrokeClipResult(
    /** Sub-strokes located strictly or predominantly inside the polygon. */
    val inside: List<Stroke>,
    /** Sub-strokes located strictly or predominantly outside the polygon. */
    val outside: List<Stroke>,
    /** True if the stroke crossed the polygon boundary and was split into new sub-strokes. */
    val split: Boolean,
)

/**
 * State accumulator that groups consecutive sub-segments into cohesive [Stroke] instances.
 */
internal class SubStrokeCollector(private val template: Stroke) {
    private val insideStrokes = ArrayList<Stroke>()
    private val outsideStrokes = ArrayList<Stroke>()
    private var currentRun = ArrayList<Sample>()
    private var currentInside: Boolean? = null

    fun addSubSegment(start: Sample, end: Sample, inside: Boolean) {
        if (currentInside == null) {
            currentInside = inside
            currentRun.add(start)
            currentRun.add(end)
            return
        }

        if (currentInside == inside) {
            currentRun.add(end)
            return
        }

        // State flipped: emit previous run and start new sub-stroke at boundary split point.
        flush()
        currentInside = inside
        currentRun.add(start)
        currentRun.add(end)
    }

    fun finish(): StrokeClipResult {
        flush()

        // Unsplit fast paths: return template directly when all samples lie on one side.
        if (insideStrokes.isEmpty() && outsideStrokes.size == 1) {
            return StrokeClipResult(emptyList(), listOf(template), split = false)
        }
        if (outsideStrokes.isEmpty() && insideStrokes.size == 1) {
            return StrokeClipResult(listOf(template), emptyList(), split = false)
        }

        val wasSplit = insideStrokes.isNotEmpty() && outsideStrokes.isNotEmpty() ||
            insideStrokes.size > 1 || outsideStrokes.size > 1

        return StrokeClipResult(insideStrokes, outsideStrokes, split = wasSplit)
    }

    private fun flush() {
        if (currentRun.isEmpty()) return
        val sub = Stroke(
            tool = template.tool,
            config = template.config,
            speedScale = template.speedScale,
            straight = template.straight,
            smoothScale = template.smoothScale,
        )
        sub.setSamples(currentRun)
        if (currentInside == true) {
            insideStrokes.add(sub)
        } else {
            outsideStrokes.add(sub)
        }
        currentRun = ArrayList()
    }
}
