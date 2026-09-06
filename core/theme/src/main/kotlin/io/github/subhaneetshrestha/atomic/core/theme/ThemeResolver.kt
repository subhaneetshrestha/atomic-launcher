package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.math.pow

/** The colour roles as ARGB ints, ready for Paint and TextView. */
data class ResolvedColors(
    val background: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val badgeBackground: Int,
    val badgeText: Int,
)

/**
 * Turns a theme's colour strings into ARGB values. Material You tokens go through [tokens]
 * (the Android side reads `android.R.color.system_*`; it returns null below Android 12 or for a
 * name the device lacks), falling back to the `ink` colour of the same role so a theme never
 * renders unreadable. `auto` text is black or white, whichever contrasts with the background.
 */
class ThemeResolver(
    private val tokens: (name: String) -> Int?,
) {
    fun resolve(
        theme: Theme,
        night: Boolean,
    ): ResolvedColors {
        val base = theme.colors
        val over = if (night) theme.darkColors else null
        val background = concrete(over?.background ?: base.background, FALLBACK.background)
        val text = text(over?.text ?: base.text, background, FALLBACK.text)
        val badge = theme.badge
        return ResolvedColors(
            background = background,
            text = text,
            textSecondary = text(over?.textSecondary ?: base.textSecondary, background, FALLBACK.textSecondary),
            accent = concrete(over?.accent ?: base.accent, FALLBACK.accent),
            badgeBackground =
                if (badge.background ==
                    ColorValue.AUTO
                ) {
                    text
                } else {
                    concrete(badge.background, FALLBACK.text)
                },
            badgeText =
                if (badge.text == ColorValue.AUTO) {
                    background
                } else {
                    concrete(
                        badge.text,
                        FALLBACK.background,
                    )
                },
        )
    }

    private fun text(
        value: String,
        background: Int,
        fallback: String,
    ): Int =
        if (value == ColorValue.AUTO) autoText(background, alphaOf(parseHex(fallback))) else concrete(value, fallback)

    private fun concrete(
        value: String,
        fallback: String,
    ): Int =
        when {
            ColorValue.isHex(value) -> parseHex(value)
            ColorValue.isToken(value) -> tokens(value.removePrefix(TOKEN_PREFIX)) ?: parseHex(fallback)
            else -> parseHex(fallback)
        }

    companion object {
        private const val TOKEN_PREFIX = "@android:color/"

        /** WCAG contrast is symmetric at this luminance: above it black text contrasts more, below it white does. */
        const val LUMINANCE_THRESHOLD = 0.179

        private val FALLBACK = Colors()

        fun parseHex(value: String): Int {
            val digits = value.removePrefix("#")
            val argb = if (digits.length == 6) 0xFF000000L or digits.toLong(16) else digits.toLong(16)
            return argb.toInt()
        }

        fun autoText(
            background: Int,
            alpha: Int,
        ): Int {
            val rgb = if (relativeLuminance(background) > LUMINANCE_THRESHOLD) 0x000000 else 0xFFFFFF
            return (alpha shl 24) or rgb
        }

        fun relativeLuminance(argb: Int): Double {
            fun channel(shift: Int): Double {
                val s = ((argb shr shift) and 0xFF) / 255.0
                return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF
    }
}
