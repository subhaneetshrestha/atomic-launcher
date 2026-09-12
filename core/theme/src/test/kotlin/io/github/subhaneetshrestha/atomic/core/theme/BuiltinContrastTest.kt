package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every built-in theme has to be readable. The info lines are 13 to 14 sp, which is small text by
 * WCAG's reckoning, so the secondary colour has the same 4.5:1 to meet as the names do.
 */
class BuiltinContrastTest {
    // The Material You theme resolves its colours from the device, so there is nothing to check here.
    private val resolver = ThemeResolver { null }

    /** WCAG AA for text below 18 sp. */
    private val minimum = 4.5

    private fun check(
        theme: Theme,
        night: Boolean,
    ) {
        val colors = resolver.resolve(theme, night)
        val where = "${theme.meta.id}${if (night) " at night" else ""}"
        val text = ThemeResolver.contrastRatio(colors.text, colors.background)
        assertTrue(text >= minimum, "$where: names are $text:1 against the background")
        val secondary =
            ThemeResolver.contrastRatio(
                ThemeResolver.composite(colors.textSecondary, colors.background),
                colors.background,
            )
        assertTrue(secondary >= minimum, "$where: the info lines are $secondary:1 against the background")
    }

    @Test
    fun `every built-in is readable in the day and at night`() {
        for (theme in BuiltinThemes.all.filterNot { it.meta.id == "you" }) {
            check(theme, night = false)
            check(theme, night = true)
        }
    }

    @Test
    fun `the ratio is the one WCAG defines`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertTrue(ThemeResolver.contrastRatio(black, white) > 20.9, "black on white is 21:1")
        assertTrue(ThemeResolver.contrastRatio(black, black) < 1.001, "a colour against itself is 1:1")
        assertTrue(
            ThemeResolver.composite(0x80FFFFFF.toInt(), black) in 0xFF7E7E7E.toInt()..0xFF808080.toInt(),
            "half-transparent white over black is mid grey",
        )
    }
}
