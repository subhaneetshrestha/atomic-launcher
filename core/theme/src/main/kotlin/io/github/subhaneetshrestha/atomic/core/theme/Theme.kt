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
    val badge: Badge = Badge(),
    val background: Background = Background(),
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
    val drawerSp: Float = 20f,
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

/**
 * How a notification count is drawn beside an app name. Colours left at `auto` follow the name:
 * the badge takes the text colour and the number is cut out of it in the background colour, so a
 * badge never needs its own palette to stay legible.
 */
@Serializable
data class Badge(
    val style: BadgeStyle = BadgeStyle.CIRCLE,
    val position: BadgePosition = BadgePosition.END,
    /** Badge height as a fraction of the row's text size. */
    val scale: Float = 0.62f,
    val background: String = ColorValue.AUTO,
    val text: String = ColorValue.AUTO,
)

@Serializable
enum class BadgeStyle {
    /** The count inside a filled circle (a pill once it needs three digits). */
    @SerialName("circle")
    CIRCLE,

    /** A filled dot; the count is not shown. */
    @SerialName("dot")
    DOT,

    /** The count as plain text, no shape behind it. */
    @SerialName("number")
    NUMBER,
}

@Serializable
enum class BadgePosition {
    @SerialName("start")
    START,

    @SerialName("end")
    END,
}

@Serializable
data class Padding(
    val h: Int = 28,
    val v: Int = 24,
)
