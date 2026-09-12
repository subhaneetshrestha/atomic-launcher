package io.github.subhaneetshrestha.atomic.background

import io.github.subhaneetshrestha.atomic.core.theme.ThemeResolver

/**
 * What an image behind the launcher demands of the things drawn on top of it. Each part of the
 * screen is asked separately, because a photograph is rarely one brightness: a bright sky can want
 * dark status-bar icons while the app names lower down still need white.
 */
data class Legibility(
    /** Names over the middle of the image read better in black than in white. */
    val darkText: Boolean,
    val darkStatusIcons: Boolean,
    val darkNavIcons: Boolean,
)

/**
 * Reads the brightness of three strips of the image — behind the status bar, behind the names,
 * behind the navigation bar — and says which way round each of them needs to be drawn.
 *
 * Pure, and works on a thumbnail of at most [MAX_THUMBNAIL] pixels a side: the answer is an
 * average, so a postage stamp gives the same one as a photograph at a fraction of the cost. The
 * part of the image the screen actually shows is worked out here too, because the image is centre
 * cropped and the strips must line up with what is on screen, not with the file.
 */
object ColorSampler {
    const val MAX_THUMBNAIL = 64

    private const val STATUS_STRIP = 0.08f
    private const val NAV_STRIP = 0.08f
    private const val TEXT_FROM = 0.25f
    private const val TEXT_TO = 0.75f

    fun sample(
        pixels: IntArray,
        width: Int,
        height: Int,
        viewAspect: Float,
        dim: Float,
    ): Legibility? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val visible = visibleRect(width, height, viewAspect)
        val veil = (1f - dim.coerceIn(0f, 1f)).toDouble()

        fun strip(
            fromFraction: Float,
            toFraction: Float,
        ): Boolean {
            val top = visible.top + (visible.height * fromFraction).toInt()
            val bottom = (visible.top + (visible.height * toFraction).toInt()).coerceAtLeast(top + 1)
            return averageLuminance(pixels, width, visible.left, top, visible.right, bottom) * veil >
                ThemeResolver.LUMINANCE_THRESHOLD
        }
        return Legibility(
            darkText = strip(TEXT_FROM, TEXT_TO),
            darkStatusIcons = strip(0f, STATUS_STRIP),
            darkNavIcons = strip(1f - NAV_STRIP, 1f),
        )
    }

    /** The part of the image left on screen once it has been scaled to fill and centred. */
    fun visibleRect(
        width: Int,
        height: Int,
        viewAspect: Float,
    ): Rect {
        if (viewAspect <= 0f || !viewAspect.isFinite()) return Rect(0, 0, width, height)
        val imageAspect = width.toFloat() / height
        return if (imageAspect > viewAspect) {
            val visibleWidth = (height * viewAspect).toInt().coerceIn(1, width)
            val inset = (width - visibleWidth) / 2
            Rect(inset, 0, inset + visibleWidth, height)
        } else {
            val visibleHeight = (width / viewAspect).toInt().coerceIn(1, height)
            val inset = (height - visibleHeight) / 2
            Rect(0, inset, width, inset + visibleHeight)
        }
    }

    private fun averageLuminance(
        pixels: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Double {
        var total = 0.0
        var counted = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val index = y * stride + x
                if (index !in pixels.indices) continue
                total += ThemeResolver.relativeLuminance(pixels[index])
                counted++
            }
        }
        return if (counted == 0) 0.0 else total / counted
    }

    /** A rectangle in image pixels; android.graphics.Rect is not available to a unit test. */
    data class Rect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }
}
