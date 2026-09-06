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
 * The projection of settings the home screen reads. Phase 2's persisted JSON document maps onto
 * this type; Phase 1 serves these defaults through [SettingsSource].
 */
data class HomeSettings(
    /** Ordered list of apps on the home screen; empty means "first [homeAppCount] apps alphabetically". */
    val homeApps: List<AppKey> = emptyList(),
    val homeAppCount: Int = 6,
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    val verticalPosition: VerticalPosition = VerticalPosition.CENTER,
    val textSizeSp: Float = 24f,
    val font: FontSpec = FontSpec(),
    /** Touch-target floor; rows never shrink below this even at small text sizes. */
    val rowMinHeightDp: Int = 48,
    val rowGapDp: Int = 4,
    val horizontalPaddingDp: Int = 28,
    val verticalPaddingDp: Int = 24,
)
