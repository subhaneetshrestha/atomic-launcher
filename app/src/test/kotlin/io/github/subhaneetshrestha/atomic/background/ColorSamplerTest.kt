package io.github.subhaneetshrestha.atomic.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which way round to draw the text and the system-bar icons over an image. */
class ColorSamplerTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    /** An image [size] square whose rows are white above [splitRow] and black below it. */
    private fun split(
        size: Int,
        splitRow: Int,
    ) = IntArray(size * size) { if (it / size < splitRow) white else black }

    @Test
    fun `a white image asks for black text and dark icons`() {
        val legibility = ColorSampler.sample(IntArray(64 * 64) { white }, 64, 64, viewAspect = 1f, dim = 0f)!!
        assertEquals(Legibility(darkText = true, darkStatusIcons = true, darkNavIcons = true), legibility)
    }

    @Test
    fun `a black image asks for white text and light icons`() {
        val legibility = ColorSampler.sample(IntArray(64 * 64) { black }, 64, 64, viewAspect = 1f, dim = 0f)!!
        assertEquals(Legibility(darkText = false, darkStatusIcons = false, darkNavIcons = false), legibility)
    }

    @Test
    fun `each strip is asked separately`() {
        // Bright sky over a dark landscape: the status bar is on the sky, the names are not.
        val legibility = ColorSampler.sample(split(64, splitRow = 12), 64, 64, viewAspect = 1f, dim = 0f)!!
        assertTrue(legibility.darkStatusIcons, "the status bar sits on the bright part")
        assertFalse(legibility.darkText, "the names sit below it, on the dark part")
        assertFalse(legibility.darkNavIcons)
    }

    @Test
    fun `dimming an image is taken into account before deciding`() {
        val grey = IntArray(64 * 64) { 0xFFBBBBBB.toInt() }
        assertTrue(ColorSampler.sample(grey, 64, 64, 1f, dim = 0f)!!.darkText)
        assertFalse(
            ColorSampler.sample(grey, 64, 64, 1f, dim = 0.8f)!!.darkText,
            "a veil the launcher itself draws is part of what the text sits on",
        )
    }

    @Test
    fun `only the part of the image that survives the crop is read`() {
        // A wide image on a tall screen: the left and right thirds are never seen.
        val width = 90
        val height = 30
        val pixels = IntArray(width * height) { if ((it % width) in 30 until 60) black else white }
        val legibility = ColorSampler.sample(pixels, width, height, viewAspect = 0.5f, dim = 0f)!!
        assertFalse(legibility.darkText, "the middle stripe is what is on screen, and it is black")
    }

    @Test
    fun `the crop keeps the whole of the shorter side`() {
        assertEquals(ColorSampler.Rect(0, 0, 100, 50), ColorSampler.visibleRect(100, 50, viewAspect = 2f))
        assertEquals(ColorSampler.Rect(25, 0, 75, 50), ColorSampler.visibleRect(100, 50, viewAspect = 1f))
        assertEquals(ColorSampler.Rect(0, 20, 40, 60), ColorSampler.visibleRect(40, 80, viewAspect = 1f))
    }

    @Test
    fun `an empty or mismatched buffer is no answer at all`() {
        assertNull(ColorSampler.sample(IntArray(0), 0, 0, 1f, 0f))
        assertNull(ColorSampler.sample(IntArray(10), 64, 64, 1f, 0f))
    }
}
