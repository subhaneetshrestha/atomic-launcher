package io.github.subhaneetshrestha.atomic.core.theme

/** Themes shipped with the app, as typed constants (no parsing at startup). Exported to docs/themes by a test. */
object BuiltinThemes {
    val ink: Theme =
        Theme(
            meta =
                ThemeMeta(
                    id = "ink",
                    name = "Ink",
                    author = "atomic",
                    license = "CC0-1.0",
                    description = "White text on black. Always legible.",
                ),
            colors = Colors(),
        )

    val paper: Theme =
        Theme(
            meta =
                ThemeMeta(
                    id = "paper",
                    name = "Paper",
                    author = "atomic",
                    license = "CC0-1.0",
                    description = "Dark serif on warm paper; inverts at night.",
                ),
            colors =
                Colors(
                    background = "#FFF6F1E7",
                    text = "#FF1B1B1B",
                    textSecondary = "#991B1B1B",
                    accent = "#FF1B1B1B",
                ),
            darkColors =
                ColorsOverride(
                    background = "#FF141210",
                    text = "#FFEDE7DA",
                    textSecondary = "#99EDE7DA",
                    accent = "#FFEDE7DA",
                ),
            typography = Typography(family = "serif"),
            layout =
                Layout(
                    hAlign = HAlign.START,
                    vAlign = VAlign.BOTTOM,
                    paddingDp = Padding(h = 28, v = 56),
                    rowGapDp = 6,
                ),
        )

    val terminal: Theme =
        Theme(
            meta =
                ThemeMeta(
                    id = "terminal",
                    name = "Terminal",
                    author = "atomic",
                    license = "CC0-1.0",
                    description = "Monospace with a phosphor green accent.",
                ),
            colors =
                Colors(
                    background = "#FF0B0F0A",
                    text = "#FFB8F5B0",
                    textSecondary = "#99B8F5B0",
                    accent = "#FF39FF14",
                ),
            typography =
                Typography(
                    family = "monospace",
                    sizes = TextSizes(homeSp = 22f, clockSp = 40f, infoSp = 13f),
                ),
            layout =
                Layout(
                    hAlign = HAlign.START,
                    vAlign = VAlign.TOP,
                    paddingDp = Padding(h = 24, v = 40),
                    rowGapDp = 4,
                ),
        )

    val you: Theme =
        Theme(
            meta =
                ThemeMeta(
                    id = "you",
                    name = "You",
                    author = "atomic",
                    license = "CC0-1.0",
                    description = "Material You colours from your wallpaper (Android 12+).",
                ),
            colors =
                Colors(
                    background = "@android:color/system_neutral1_900",
                    text = "@android:color/system_neutral1_50",
                    textSecondary = "@android:color/system_neutral1_200",
                    accent = "@android:color/system_accent1_200",
                ),
        )

    val all: List<Theme> = listOf(ink, paper, terminal, you)

    fun byId(id: String): Theme? = all.firstOrNull { it.meta.id == id }
}
