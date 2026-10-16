package com.xnotes.core.stroke

import com.xnotes.core.geometry.Pt
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.Stroke
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeClipperTest {

    private val squarePolygon = listOf(
        Pt(10.0, 10.0),
        Pt(30.0, 10.0),
        Pt(30.0, 30.0),
        Pt(10.0, 30.0),
    )

    private fun stroke(vararg samples: Sample, straight: Boolean = false): Stroke {
        val cfg = ToolConfig(baseWidth = 4.0, rgba = Rgba(10, 20, 30, 255))
        val s = Stroke(Tool.PEN, cfg, speedScale = 1.5, straight = straight)
        s.setSamples(samples.toList())
        return s
    }

    @Test
    fun simpleCrossingSplit() {
        // Line along y = 20 from x = 0 to x = 40.
        // Crosses square polygon at x = 10 and x = 30.
        val s = stroke(
            Sample(0.0, 20.0, 0.2, 0.0),
            Sample(40.0, 20.0, 1.0, 100.0),
        )

        val result = StrokeClipper.clipStroke(s, squarePolygon)

        assertTrue(result.split)
        assertEquals(1, result.inside.size)
        assertEquals(2, result.outside.size)

        // Inside segment: from x = 10 to x = 30
        val insideStroke = result.inside[0]
        assertEquals(2, insideStroke.sampleCount)
        assertEquals(10.0, insideStroke.xAt(0), 1e-4)
        assertEquals(20.0, insideStroke.yAt(0), 1e-4)
        assertEquals(30.0, insideStroke.xAt(1), 1e-4)
        assertEquals(20.0, insideStroke.yAt(1), 1e-4)

        // Interpolated pressure and timestamp:
        // x = 10 is t = 0.25 -> pressure = 0.2 + 0.25 * 0.8 = 0.4, timestamp = 25.0
        assertEquals(0.4, insideStroke.pAt(0), 1e-4)
        assertEquals(25.0, insideStroke.tAt(0), 1e-4)
        // x = 30 is t = 0.75 -> pressure = 0.2 + 0.75 * 0.8 = 0.8, timestamp = 75.0
        assertEquals(0.8, insideStroke.pAt(1), 1e-4)
        assertEquals(75.0, insideStroke.tAt(1), 1e-4)

        // Outside segment 1: [0, 10]
        val outside1 = result.outside[0]
        assertEquals(0.0, outside1.xAt(0), 1e-4)
        assertEquals(10.0, outside1.xAt(1), 1e-4)

        // Outside segment 2: [30, 40]
        val outside2 = result.outside[1]
        assertEquals(30.0, outside2.xAt(0), 1e-4)
        assertEquals(40.0, outside2.xAt(1), 1e-4)
    }

    @Test
    fun strokeEnteringAndExitingMultipleTimes() {
        // U-shaped polygon with a vertical notch between x = 20 and x = 30
        val uPolygon = listOf(
            Pt(0.0, 0.0),
            Pt(50.0, 0.0),
            Pt(50.0, 40.0),
            Pt(30.0, 40.0),
            Pt(30.0, 20.0),
            Pt(20.0, 20.0),
            Pt(20.0, 40.0),
            Pt(0.0, 40.0),
        )

        // Horizontal line along y = 30 crossing left prong [0, 20], notch [20, 30], and right prong [30, 50]
        val s = stroke(
            Sample(-10.0, 30.0, 0.5),
            Sample(60.0, 30.0, 0.5),
        )

        val result = StrokeClipper.clipStroke(s, uPolygon)

        assertTrue(result.split)
        // Inside prongs: [0, 20] and [30, 50]
        assertEquals(2, result.inside.size)
        assertEquals(0.0, result.inside[0].xAt(0), 1e-4)
        assertEquals(20.0, result.inside[0].xAt(1), 1e-4)
        assertEquals(30.0, result.inside[1].xAt(0), 1e-4)
        assertEquals(50.0, result.inside[1].xAt(1), 1e-4)

        // Outside segments: [-10, 0], [20, 30] (in the notch), [50, 60]
        assertEquals(3, result.outside.size)
        assertEquals(-10.0, result.outside[0].xAt(0), 1e-4)
        assertEquals(0.0, result.outside[0].xAt(1), 1e-4)
        assertEquals(20.0, result.outside[1].xAt(0), 1e-4)
        assertEquals(30.0, result.outside[1].xAt(1), 1e-4)
        assertEquals(50.0, result.outside[2].xAt(0), 1e-4)
        assertEquals(60.0, result.outside[2].xAt(1), 1e-4)
    }

    @Test
    fun strokeEntirelyInsideNoSplit() {
        val s = stroke(
            Sample(15.0, 15.0, 0.5),
            Sample(25.0, 20.0, 0.7),
            Sample(20.0, 25.0, 0.9),
        )

        val result = StrokeClipper.clipStroke(s, squarePolygon)

        assertFalse(result.split)
        assertEquals(1, result.inside.size)
        assertSame(s, result.inside[0])
        assertTrue(result.outside.isEmpty())
    }

    @Test
    fun strokeEntirelyOutsideNoSplit() {
        val s = stroke(
            Sample(100.0, 100.0, 0.5),
            Sample(120.0, 110.0, 0.7),
            Sample(140.0, 105.0, 0.9),
        )

        val result = StrokeClipper.clipStroke(s, squarePolygon)

        assertFalse(result.split)
        assertTrue(result.inside.isEmpty())
        assertEquals(1, result.outside.size)
        assertSame(s, result.outside[0])
    }

    @Test
    fun singleSampleStrokeInsideAndOutside() {
        val insideDot = stroke(Sample(20.0, 20.0, 0.8))
        val insideResult = StrokeClipper.clipStroke(insideDot, squarePolygon)
        assertFalse(insideResult.split)
        assertEquals(1, insideResult.inside.size)
        assertSame(insideDot, insideResult.inside[0])
        assertTrue(insideResult.outside.isEmpty())

        val outsideDot = stroke(Sample(50.0, 50.0, 0.8))
        val outsideResult = StrokeClipper.clipStroke(outsideDot, squarePolygon)
        assertFalse(outsideResult.split)
        assertTrue(outsideResult.inside.isEmpty())
        assertEquals(1, outsideResult.outside.size)
        assertSame(outsideDot, outsideResult.outside[0])
    }

    @Test
    fun straightStrokePreservesStraightFlagAndStyle() {
        val s = stroke(
            Sample(0.0, 20.0, 0.5),
            Sample(40.0, 20.0, 0.5),
            straight = true,
        )

        val result = StrokeClipper.clipStroke(s, squarePolygon)
        assertTrue(result.split)
        val inside = result.inside[0]
        assertTrue(inside.straight)
        assertEquals(Tool.PEN, inside.tool)
        assertEquals(4.0, inside.config.baseWidth, 1e-6)
        assertEquals(Rgba(10, 20, 30, 255), inside.config.rgba)
    }
}
