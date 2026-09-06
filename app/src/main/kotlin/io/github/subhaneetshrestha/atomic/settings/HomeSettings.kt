package io.github.subhaneetshrestha.atomic.settings

import io.github.subhaneetshrestha.atomic.apps.AppKey

enum class HorizontalAlignment { START, CENTER, END }

enum class VerticalPosition { TOP, CENTER, BOTTOM }

/** A system font family with weight and slant. User font files arrive in a later phase. */
data class FontSpec(
    val family: String = "sans-serif",
    val weight: Int = 400,
    val italic: Boolean = false,
)

/**
 * The home screen's view of the settings: what the list shows and how it is laid out. Derived
 * from the persisted document by [toHomeSettings]; defaults match the built-in `ink` theme.
 */
data class HomeSettings(
    /** Ordered list of apps on the home screen; empty means "first [homeAppCount] apps alphabetically". */
    val homeApps: List<AppKey> = emptyList(),
    val homeAppCount: Int = 6,
    /** Launcher-local labels: per-row overrides and renames, already merged. */
    val labelOverrides: Map<AppKey, String> = emptyMap(),
    /** Excluded from the alphabetical fallback (and later from the drawer); may still be placed on the home list. */
    val hidden: Set<AppKey> = emptySet(),
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    val verticalPosition: VerticalPosition = VerticalPosition.CENTER,
    val textSizeSp: Float = 24f,
    val drawerTextSizeSp: Float = 20f,
    /** With one match left, open it instead of waiting for a tap. */
    val autoLaunchSingle: Boolean = true,
    val autoShowKeyboard: Boolean = true,
    val webSearchFallback: Boolean = false,
    val font: FontSpec = FontSpec(),
    /** Touch-target floor; rows never shrink below this even at small text sizes. */
    val rowMinHeightDp: Int = 48,
    val rowGapDp: Int = 4,
    val horizontalPaddingDp: Int = 28,
    val verticalPaddingDp: Int = 24,
)
