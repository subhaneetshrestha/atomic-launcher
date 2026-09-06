package io.github.subhaneetshrestha.atomic.settings

import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.theme.AppRef
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinThemes
import io.github.subhaneetshrestha.atomic.core.theme.HomeConfig
import io.github.subhaneetshrestha.atomic.core.theme.HomeEntry
import io.github.subhaneetshrestha.atomic.core.theme.Rename
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsProjectionTest {
    @Test
    fun `the home screen view is derived from the document`() {
        val settings =
            Settings(
                theme = BuiltinThemes.paper,
                home =
                    HomeConfig(
                        entries =
                            listOf(
                                HomeEntry("com.a/com.a.Main", label = "Mail"),
                                HomeEntry("com.b/.Main", user = 0),
                            ),
                        fallbackCount = 4,
                    ),
                hidden = listOf(AppRef("com.c/.Main")),
                renames = listOf(Rename("com.b/.Main", label = "Chat")),
            )

        val view = settings.toHomeSettings()

        assertEquals(
            listOf(AppKey("com.a", "com.a.Main", 0), AppKey("com.b", "com.b.Main", 0)),
            view.homeApps,
            "relative class names resolve",
        )
        assertEquals(4, view.homeAppCount)
        assertEquals(
            mapOf(AppKey("com.a", "com.a.Main", 0) to "Mail", AppKey("com.b", "com.b.Main", 0) to "Chat"),
            view.labelOverrides,
        )
        assertEquals(setOf(AppKey("com.c", "com.c.Main", 0)), view.hidden)
        assertEquals(HorizontalAlignment.START, view.horizontalAlignment, "paper is left-aligned")
        assertEquals(VerticalPosition.BOTTOM, view.verticalPosition, "paper sits at the bottom")
        assertEquals(24f, view.textSizeSp)
        assertEquals(FontSpec(family = "serif", weight = 400, italic = false), view.font)
        assertEquals(28, view.horizontalPaddingDp)
        assertEquals(56, view.verticalPaddingDp)
        assertEquals(6, view.rowGapDp)
    }

    @Test
    fun `an entry whose component cannot be parsed is skipped`() {
        val settings = Settings(home = HomeConfig(entries = listOf(HomeEntry("garbage"), HomeEntry("com.a/.Main"))))

        assertEquals(listOf(AppKey("com.a", "com.a.Main", 0)), settings.toHomeSettings().homeApps)
    }
}
