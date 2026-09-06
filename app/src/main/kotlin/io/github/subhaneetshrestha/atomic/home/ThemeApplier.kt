package io.github.subhaneetshrestha.atomic.home

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.core.theme.BadgePosition
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.notifications.BadgeDrawable
import io.github.subhaneetshrestha.atomic.settings.FontSpec
import io.github.subhaneetshrestha.atomic.settings.HomeSettings
import io.github.subhaneetshrestha.atomic.settings.HorizontalAlignment
import io.github.subhaneetshrestha.atomic.settings.VerticalPosition
import kotlin.math.roundToInt

/**
 * Applies settings and resolved theme colours to views. Sizes go through sp/dp and the display
 * metrics and are never derived from fontScale, so Android 14's non-linear font scaling stays
 * correct. Later phases add legibility effects, badges, system bars and user fonts here.
 */
class ThemeApplier(
    private val context: Context,
) {
    fun dp(value: Int): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics,
            ).roundToInt()

    fun typeface(font: FontSpec): Typeface {
        val family = Typeface.create(font.family, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(family, font.weight.coerceIn(1, 1000), font.italic)
        } else {
            Typeface.create(family, legacyStyle(font))
        }
    }

    private fun legacyStyle(font: FontSpec): Int {
        val bold = font.weight >= 600
        return when {
            bold && font.italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            font.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
    }

    fun horizontalGravity(alignment: HorizontalAlignment): Int =
        when (alignment) {
            HorizontalAlignment.START -> Gravity.START
            HorizontalAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
            HorizontalAlignment.END -> Gravity.END
        }

    fun verticalGravity(position: VerticalPosition): Int =
        when (position) {
            VerticalPosition.TOP -> Gravity.TOP
            VerticalPosition.CENTER -> Gravity.CENTER_VERTICAL
            VerticalPosition.BOTTOM -> Gravity.BOTTOM
        }

    fun applyRow(
        row: TextView,
        settings: HomeSettings,
        colors: ResolvedColors,
    ) {
        // Rows are only as wide as their name, so a two-letter one still needs a target to hit.
        row.minWidth = dp(settings.rowMinHeightDp)
        applyText(
            row,
            settings.font,
            settings.textSizeSp,
            colors.text,
            horizontalGravity(settings.horizontalAlignment),
            settings.rowMinHeightDp,
        )
    }

    /**
     * Puts [count]'s badge on a row, or takes it off when there is nothing waiting. The badge is
     * a compound drawable, so the row's text shortens to make room for it rather than sliding
     * under it, and the whole row stays one accessible, clickable view.
     */
    fun applyBadge(
        row: TextView,
        badgeDrawable: BadgeDrawable,
        count: Int,
        settings: HomeSettings,
        colors: ResolvedColors,
    ) {
        val shown =
            badgeDrawable.update(
                count = count,
                badge = settings.badge,
                typeface = row.typeface ?: typeface(settings.font),
                textSizePx = row.textSize,
                background = colors.badgeBackground,
                text = colors.badgeText,
            )
        row.compoundDrawablePadding = if (shown) badgeDrawable.gapPx else 0
        // The badge is drawn, not written, so the count would be invisible to a screen reader
        // (and to anything else reading the screen) unless the row says it out loud.
        row.contentDescription =
            if (shown) {
                context.resources.getQuantityString(R.plurals.badge_a11y, count, row.text, count)
            } else {
                null
            }
        when {
            !shown -> {
                row.setCompoundDrawablesRelative(null, null, null, null)
            }

            settings.badge.position == BadgePosition.START -> {
                row.setCompoundDrawablesRelative(badgeDrawable, null, null, null)
            }

            else -> {
                row.setCompoundDrawablesRelative(null, null, badgeDrawable, null)
            }
        }
    }

    /** Clock, date, battery: same family, own size and colour, same touch-target floor as rows. */
    fun applyText(
        view: TextView,
        font: FontSpec,
        sizeSp: Float,
        color: Int,
        horizontalGravity: Int,
        minHeightDp: Int = 48,
        maxFontScale: Float? = null,
    ) {
        view.typeface = typeface(font)
        if (maxFontScale == null) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        } else {
            // Decorative text (the clock) would otherwise push the app rows off screen at 200%.
            val scale = minOf(context.resources.configuration.fontScale, maxFontScale)
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeSp * scale)
        }
        view.setTextColor(color)
        view.minHeight = dp(minHeightDp)
        view.gravity = Gravity.CENTER_VERTICAL or horizontalGravity
    }
}
