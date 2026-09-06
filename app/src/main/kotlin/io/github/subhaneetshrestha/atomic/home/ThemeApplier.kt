package io.github.subhaneetshrestha.atomic.home

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
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
        applyText(
            row,
            settings.font,
            settings.textSizeSp,
            colors.text,
            horizontalGravity(settings.horizontalAlignment),
            settings.rowMinHeightDp,
        )
    }

    /** Clock, date, battery: same family, own size and colour, same touch-target floor as rows. */
    fun applyText(
        view: TextView,
        font: FontSpec,
        sizeSp: Float,
        color: Int,
        horizontalGravity: Int,
        minHeightDp: Int = 48,
    ) {
        view.typeface = typeface(font)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        view.setTextColor(color)
        view.minHeight = dp(minHeightDp)
        view.gravity = Gravity.CENTER_VERTICAL or horizontalGravity
    }
}
