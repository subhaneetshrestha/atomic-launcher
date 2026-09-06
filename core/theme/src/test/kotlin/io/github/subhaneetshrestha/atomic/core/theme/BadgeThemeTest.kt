package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BadgeThemeTest {
    private val resolver = ThemeResolver(tokens = { null })

    @Test
    fun `a badge is a number in a circle after the name unless a theme says otherwise`() {
        val badge = Settings().theme.badge

        assertEquals(BadgeStyle.CIRCLE, badge.style)
        assertEquals(BadgePosition.END, badge.position)
        assertEquals(0.62f, badge.scale, "a little under two thirds of the name's height")
        assertEquals(ColorValue.AUTO, badge.background)
        assertEquals(ColorValue.AUTO, badge.text)
    }

    @Test
    fun `left to itself a badge is the name's colour with the background showing through`() {
        val colors = resolver.resolve(BuiltinThemes.ink, night = false)

        assertEquals(colors.text, colors.badgeBackground, "so it reads as part of the name")
        assertEquals(colors.background, colors.badgeText, "and the number is cut out of it")
    }

    @Test
    fun `a theme may say exactly what colour the badge is`() {
        val theme =
            BuiltinThemes.ink.copy(
                badge = Badge(background = "#FFCC0000", text = "#FFFFFFFF"),
            )

        val colors = resolver.resolve(theme, night = false)

        assertEquals(0xFFCC0000.toInt(), colors.badgeBackground)
        assertEquals(0xFFFFFFFF.toInt(), colors.badgeText)
    }

    @Test
    fun `a badge scale outside what fits is pulled back, and a bad colour falls back`() {
        val text = """
            { "theme": { "badge": { "style": "dot", "position": "start", "scale": 9,
                                    "background": "not a colour" } } }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))
        val badge = result.value.theme.badge

        assertEquals(BadgeStyle.DOT, badge.style)
        assertEquals(BadgePosition.START, badge.position)
        assertEquals(1.2f, badge.scale, "a badge taller than the name it sits beside is not a badge")
        assertEquals(ColorValue.AUTO, badge.background, "an unreadable colour goes back to following the name")
        assertEquals(
            listOf("theme.badge.background", "theme.badge.scale"),
            result.warnings.map { it.path }.sorted(),
        )
    }
}
