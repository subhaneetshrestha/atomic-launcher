package io.github.subhaneetshrestha.atomic.notifications

import io.github.subhaneetshrestha.atomic.core.theme.BadgeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Badge sizing is derived from the row's own text size, so a badge stays in proportion at any
 * text size and at any system font scale without a single dp constant.
 */
class BadgeGeometryTest {
    private val textSize = 100f
    private val scale = 0.6f

    @Test
    fun `a single digit sits in a circle sized from the name`() {
        val labelSize = BadgeGeometry.labelSize(BadgeStyle.CIRCLE, textSize, scale)
        assertEquals(40.8f, labelSize, TOLERANCE, "the digits fit inside the circle")

        val metrics = BadgeGeometry.of(BadgeStyle.CIRCLE, textSize, scale, labelWidthPx = 20f)

        assertEquals(60f, metrics.heightPx, TOLERANCE, "0.6 of the name's size")
        assertEquals(60f, metrics.widthPx, TOLERANCE, "narrow digits do not make an oval")
        assertEquals(30f, metrics.radiusPx, TOLERANCE)
        assertEquals(labelSize, metrics.labelSizePx, TOLERANCE)
        assertEquals(30f, metrics.gapPx, TOLERANCE, "space between the name and the badge")
        assertEquals(90f, metrics.advancePx, TOLERANCE, "what the row reserves after the name")
    }

    @Test
    fun `three digits stretch the circle into a pill and nothing else changes`() {
        val one = BadgeGeometry.of(BadgeStyle.CIRCLE, textSize, scale, labelWidthPx = 20f)
        val many = BadgeGeometry.of(BadgeStyle.CIRCLE, textSize, scale, labelWidthPx = 70f)

        assertEquals(103.6f, many.widthPx, TOLERANCE, "digits plus padding on both sides")
        assertTrue(many.widthPx > many.heightPx, "wider than tall")
        assertEquals(one.heightPx, many.heightPx, TOLERANCE, "rows keep their height as counts grow")
        assertEquals(one.radiusPx, many.radiusPx, TOLERANCE, "the ends stay round")
    }

    @Test
    fun `a dot says only that something is waiting`() {
        val metrics = BadgeGeometry.of(BadgeStyle.DOT, textSize, scale, labelWidthPx = 70f)

        assertEquals(30f, metrics.heightPx, TOLERANCE, "half a numbered badge")
        assertEquals(30f, metrics.widthPx, TOLERANCE, "round whatever the count")
        assertEquals(15f, metrics.radiusPx, TOLERANCE)
        assertEquals(0f, metrics.labelSizePx, TOLERANCE, "nothing to draw")
        assertEquals("", BadgeGeometry.label(7, BadgeStyle.DOT))
    }

    @Test
    fun `a plain number is only the digits`() {
        assertEquals(60f, BadgeGeometry.labelSize(BadgeStyle.NUMBER, textSize, scale), TOLERANCE)

        val metrics = BadgeGeometry.of(BadgeStyle.NUMBER, textSize, scale, labelWidthPx = 45f)

        assertEquals(45f, metrics.widthPx, TOLERANCE, "no shape, so no padding")
        assertEquals(60f, metrics.heightPx, TOLERANCE)
        assertEquals(0f, metrics.radiusPx, TOLERANCE, "nothing to round")
        assertEquals("42", BadgeGeometry.label(42, BadgeStyle.NUMBER))
    }

    @Test
    fun `a badge never outgrows the line it sits on`() {
        val tallest = BadgeGeometry.of(BadgeStyle.CIRCLE, textSize, scale = 4f, labelWidthPx = 20f)

        assertEquals(120f, tallest.heightPx, TOLERANCE, "at most one and a fifth of the name's size")
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
