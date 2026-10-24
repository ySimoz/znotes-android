package com.xnotes.core.stroke

import com.xnotes.core.geometry.Geometry
import com.xnotes.core.geometry.Pt
import com.xnotes.core.geometry.Rect
import com.xnotes.core.model.Stroke

/**
 * Clips stroke geometry against a closed polygon boundary.
 *
 * Implements pixel-perfect stroke segmentation:
 * - Detects intersections between stroke segments and polygon edges.
 * - Interpolates sample attributes (position, pressure, timestamp).
 * - Partitions crossing strokes into distinct inside and outside sub-strokes.
 * - Leaves strokes entirely inside or outside intact without splitting.
 */
object StrokeClipper {

    private const val MIN_SEGMENT_LENGTH = 1e-6
    private const val EPSILON_T = 1e-6
    private const val PARALLEL_DENOMINATOR = 1e-11
    private const val MIN_SPLIT_DISTANCE = 1e-4

    /**
     * Clips [stroke] against a closed [polygon].
     */
    fun clipStroke(stroke: Stroke, polygon: List<Pt>): StrokeClipResult {
        if (polygon.size < 3 || stroke.isEmpty || stroke.sampleCount == 0) {
            return StrokeClipResult(emptyList(), listOf(stroke), split = false)
        }

        // Bounding box reject: if stroke bounding box misses polygon bounding box, stroke is outside.
        val polyBox = Rect.bounding(polygon)
        if (!stroke.bounds().intersects(polyBox)) {
            return StrokeClipResult(emptyList(), listOf(stroke), split = false)
        }

        // Single-sample stroke (tap / dot): test single position directly.
        if (stroke.sampleCount == 1) {
            val p = Pt(stroke.xAt(0), stroke.yAt(0))
            val inside = Geometry.pointInPolygon(polygon, p)
            return if (inside) {
                StrokeClipResult(listOf(stroke), emptyList(), split = false)
            } else {
                StrokeClipResult(emptyList(), listOf(stroke), split = false)
            }
        }

        return splitStrokeSegments(stroke, polygon)
    }

    private fun splitStrokeSegments(stroke: Stroke, polygon: List<Pt>): StrokeClipResult {
        val collector = SubStrokeCollector(stroke)
        val n = stroke.sampleCount

        for (k in 0 until n - 1) {
            val a = stroke.sampleAt(k)
            val b = stroke.sampleAt(k + 1)
            val segLen = a.pos.distanceTo(b.pos)

            if (segLen < MIN_SEGMENT_LENGTH) {
                val mid = a.pos
                val inside = Geometry.pointInPolygon(polygon, mid)
                collector.addSubSegment(a, b, inside)
                continue
            }

            val ts = findIntersections(a.pos, b.pos, polygon, segLen)
            processSegmentWithSplits(a, b, ts, polygon, collector)
        }

        return collector.finish()
    }

    private fun processSegmentWithSplits(
        a: Sample,
        b: Sample,
        ts: List<Double>,
        polygon: List<Pt>,
        collector: SubStrokeCollector,
    ) {
        var prevSample = a
        for (t in ts) {
            val splitSample = interpolate(a, b, t)
            val mid = Pt((prevSample.x + splitSample.x) * 0.5, (prevSample.y + splitSample.y) * 0.5)
            val inside = Geometry.pointInPolygon(polygon, mid)
            collector.addSubSegment(prevSample, splitSample, inside)
            prevSample = splitSample
        }

        val finalMid = Pt((prevSample.x + b.x) * 0.5, (prevSample.y + b.y) * 0.5)
        val finalInside = Geometry.pointInPolygon(polygon, finalMid)
        collector.addSubSegment(prevSample, b, finalInside)
    }

    private fun findIntersections(a: Pt, b: Pt, polygon: List<Pt>, segLen: Double): List<Double> {
        val intersections = ArrayList<Double>()
        val n = polygon.size

        for (j in 0 until n) {
            val c = polygon[j]
            val d = polygon[(j + 1) % n]
            val t = intersectSegmentWithEdge(a, b, c, d)
            if (t != null) intersections.add(t)
        }

        if (intersections.isEmpty()) return emptyList()
        intersections.sort()

        // Deduplicate split points that are too close in parameter space or physical space.
        val minGapT = (MIN_SPLIT_DISTANCE / segLen).coerceAtLeast(EPSILON_T)
        val filtered = ArrayList<Double>(intersections.size)
        var lastT = -1.0

        for (t in intersections) {
            if (lastT < 0.0 || (t - lastT) >= minGapT) {
                filtered.add(t)
                lastT = t
            }
        }
        return filtered
    }

    private fun intersectSegmentWithEdge(a: Pt, b: Pt, c: Pt, d: Pt): Double? {
        val vx = b.x - a.x
        val vy = b.y - a.y
        val wx = d.x - c.x
        val wy = d.y - c.y
        val denom = vx * wy - vy * wx
        if (kotlin.math.abs(denom) < PARALLEL_DENOMINATOR) return null

        val dx = c.x - a.x
        val dy = c.y - a.y
        val t = (dx * wy - dy * wx) / denom
        val u = (dx * vy - dy * vx) / denom

        if (t in EPSILON_T..(1.0 - EPSILON_T) && u in -1e-5..(1.0 + 1e-5)) {
            return t
        }
        return null
    }

    private fun interpolate(a: Sample, b: Sample, t: Double): Sample {
        val f = t.coerceIn(0.0, 1.0)
        return Sample(
            x = a.x + f * (b.x - a.x),
            y = a.y + f * (b.y - a.y),
            pressure = a.pressure + f * (b.pressure - a.pressure),
            t = a.t + f * (b.t - a.t),
        )
    }
}
