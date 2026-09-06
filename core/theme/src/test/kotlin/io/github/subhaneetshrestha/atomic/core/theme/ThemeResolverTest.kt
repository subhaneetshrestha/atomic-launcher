package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeResolverTest {
    private val noTokens = ThemeResolver(tokens = { null })

    @Test
    fun `hex colours resolve to ARGB and night overrides apply only at night`() {
        assertEquals(
            ResolvedColors(
                background = 0xFF000000.toInt(),
                text = 0xFFF2F2F2.toInt(),
                textSecondary = 0x99F2F2F2.toInt(),
                accent = 0xFFF2F2F2.toInt(),
                badgeBackground = 0xFFF2F2F2.toInt(),
                badgeText = 0xFF000000.toInt(),
            ),
            noTokens.resolve(BuiltinThemes.ink, night = false),
        )
        assertEquals(
            0xFFF6F1E7.toInt(),
            noTokens.resolve(BuiltinThemes.paper, night = false).background,
            "paper is light by day",
        )
        assertEquals(
            0xFF141210.toInt(),
            noTokens.resolve(BuiltinThemes.paper, night = true).background,
            "paper inverts at night",
        )
        assertEquals(0xFFEDE7DA.toInt(), noTokens.resolve(BuiltinThemes.paper, night = true).text)
    }

    @Test
    fun `tokens go through the lookup and fall back to the ink colour for that role`() {
        val lookup = ThemeResolver(tokens = { name -> if (name == "system_neutral1_900") 0xFF1B1B1F.toInt() else null })

        val resolved = lookup.resolve(BuiltinThemes.you, night = false)

        assertEquals(0xFF1B1B1F.toInt(), resolved.background, "a token the device knows is used")
        assertEquals(0xFFF2F2F2.toInt(), resolved.text, "a token the device lacks falls back to ink's text colour")
        assertEquals(
            noTokens.resolve(BuiltinThemes.ink, night = false),
            noTokens.resolve(BuiltinThemes.you, night = false),
            "without any tokens, you looks like ink",
        )
    }

    @Test
    fun `auto text is black on light backgrounds and white on dark ones`() {
        fun auto(background: String) =
            Theme(
                colors =
                    Colors(
                        background = background,
                        text = ColorValue.AUTO,
                        textSecondary = ColorValue.AUTO,
                        accent = "#FF0000FF",
                    ),
            )

        assertEquals(0xFF000000.toInt(), noTokens.resolve(auto("#FFFFFFFF"), night = false).text)
        assertEquals(0xFFFFFFFF.toInt(), noTokens.resolve(auto("#FF000000"), night = false).text)
        assertEquals(
            0xFF000000.toInt(),
            noTokens.resolve(auto("#FF808080"), night = false).text,
            "mid grey (luminance 0.22) is above the 0.179 threshold",
        )
        assertEquals(0xFFFFFFFF.toInt(), noTokens.resolve(auto("#FF102030"), night = false).text)
        assertEquals(
            0x99FFFFFF.toInt(),
            noTokens.resolve(auto("#FF000000"), night = false).textSecondary,
            "secondary auto text keeps the 0x99 alpha",
        )
    }
}
