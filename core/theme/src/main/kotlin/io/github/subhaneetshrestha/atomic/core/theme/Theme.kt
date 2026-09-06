package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The shareable theme document (`.atomictheme`). Colours are strings: `#AARRGGBB`, `#RRGGBB`,
 * an `@android:color/system_*` token, or `auto` for text roles.
 */
@Serializable
data class Theme(
    val schema: Int = SettingsCodec.THEME_SCHEMA,
    val minAppVersion: Int = 1,
    val meta: ThemeMeta = ThemeMeta(),
    val colors: Colors = Colors(),
    /** Optional overrides applied while night mode is active. */
    val darkColors: ColorsOverride? = null,
    val typography: Typography = Typography(),
    val layout: Layout = Layout(),
)

@Serializable
data class ThemeMeta(
    val id: String = "custom",
    val name: String = "Custom",
    val author: String? = null,
    val license: String? = null,
    val homepage: String? = null,
    val description: String? = null,
)

@Serializable
data class Colors(
    val background: String = "#FF000000",
    val text: String = "#FFF2F2F2",
    val textSecondary: String = "#99F2F2F2",
    val accent: String = "#FFF2F2F2",
)

@Serializable
data class ColorsOverride(
    val background: String? = null,
    val text: String? = null,
    val textSecondary: String? = null,
    val accent: String? = null,
)

@Serializable
data class Typography(
    val family: String = "sans-serif",
    val weight: Int = 400,
    val italic: Boolean = false,
    val sizes: TextSizes = TextSizes(),
)

@Serializable
data class TextSizes(
    val homeSp: Float = 24f,
    val clockSp: Float = 48f,
    val infoSp: Float = 14f,
)

@Serializable
data class Layout(
    val hAlign: HAlign = HAlign.CENTER,
    val vAlign: VAlign = VAlign.CENTER,
    val paddingDp: Padding = Padding(),
    val rowGapDp: Int = 4,
)

@Serializable
enum class HAlign {
    @SerialName("start")
    START,

    @SerialName("center")
    CENTER,

    @SerialName("end")
    END,
}

@Serializable
enum class VAlign {
    @SerialName("top")
    TOP,

    @SerialName("center")
    CENTER,

    @SerialName("bottom")
    BOTTOM,
}

@Serializable
data class Padding(
    val h: Int = 28,
    val v: Int = 24,
)
