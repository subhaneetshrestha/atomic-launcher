package io.github.subhaneetshrestha.atomic.notifications

import io.github.subhaneetshrestha.atomic.core.theme.BadgeStyle

/** Everything the drawing code needs, in pixels, for one badge beside one name. */
data class BadgeMetrics(
    val widthPx: Float,
    val heightPx: Float,
    /** Corner radius: half the height for a circle or pill, zero for a plain number. */
    val radiusPx: Float,
    /** Text size for the count, or zero when there is nothing to draw. */
    val labelSizePx: Float,
    val gapPx: Float,
) {
    /** Width the row must reserve after the name. */
    val advancePx: Float get() = gapPx + widthPx
}

/**
 * Badge sizing, derived from the row's resolved text size alone: no dp constants, so a badge
 * keeps its proportions at any text size and at any system font scale. Two steps, because the
 * pill's width depends on how wide the digits actually are: ask for [labelSize], measure the
 * digits at it, then ask for [of].
 */
object BadgeGeometry {
    /** Height of a numbered badge, as a fraction of the name's text size, is the theme's scale. */
    const val MAX_SCALE = 1.2f

    /** A dot is half the height of a numbered badge. */
    const val DOT_SCALE = 0.5f

    /** The digits inside a circle, as a fraction of the badge's height. */
    const val LABEL_EM = 0.68f

    /** Padding either side of the digits in a pill, as a fraction of the badge's height. */
    const val PADDING_EM = 0.28f

    /** Space between the name and the badge, as a fraction of the name's text size. */
    const val GAP_EM = 0.3f

    fun labelSize(
        style: BadgeStyle,
        textSizePx: Float,
        scale: Float,
    ): Float =
        when (style) {
            BadgeStyle.CIRCLE -> height(style, textSizePx, scale) * LABEL_EM
            BadgeStyle.NUMBER -> textSizePx * clampScale(scale)
            BadgeStyle.DOT -> 0f
        }

    fun of(
        style: BadgeStyle,
        textSizePx: Float,
        scale: Float,
        labelWidthPx: Float,
    ): BadgeMetrics {
        val height = height(style, textSizePx, scale)
        return BadgeMetrics(
            widthPx =
                when (style) {
                    BadgeStyle.CIRCLE -> maxOf(height, labelWidthPx + 2f * PADDING_EM * height)
                    BadgeStyle.DOT -> height
                    BadgeStyle.NUMBER -> labelWidthPx
                },
            heightPx = height,
            radiusPx = if (style == BadgeStyle.NUMBER) 0f else height / 2f,
            labelSizePx = labelSize(style, textSizePx, scale),
            gapPx = textSizePx * GAP_EM,
        )
    }

    /** What is written in the badge: the count, or nothing at all for a dot. */
    fun label(
        count: Int,
        style: BadgeStyle,
    ): String = if (style == BadgeStyle.DOT) "" else count.toString()

    private fun height(
        style: BadgeStyle,
        textSizePx: Float,
        scale: Float,
    ): Float {
        val full = textSizePx * clampScale(scale)
        return if (style == BadgeStyle.DOT) full * DOT_SCALE else full
    }

    /** The sanitizer clamps a stored theme; a theme built in code is held to the same limit here. */
    private fun clampScale(scale: Float): Float = scale.coerceIn(0f, MAX_SCALE)
}
