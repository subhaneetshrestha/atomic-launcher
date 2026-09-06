package io.github.subhaneetshrestha.atomic.notifications

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import io.github.subhaneetshrestha.atomic.core.theme.Badge
import io.github.subhaneetshrestha.atomic.core.theme.BadgeStyle
import kotlin.math.roundToInt

/**
 * The number beside an app's name, drawn with two Paints and no bitmap: a rounded rectangle whose
 * corner radius is half its height (so one digit is a circle and three are a pill), a dot, or the
 * digits alone. Sized entirely by [BadgeGeometry] from the row's own text size.
 */
class BadgeDrawable : Drawable() {
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
    private val box = RectF()
    private var metrics = EMPTY
    private var style = BadgeStyle.CIRCLE
    private var label = ""

    /** Space the row should leave between the name and this badge. */
    val gapPx: Int get() = metrics.gapPx.roundToInt()

    /**
     * Re-sizes and re-colours the badge for one row. Returns false when there is nothing to draw,
     * which is the row's cue to carry no badge at all.
     */
    fun update(
        count: Int,
        badge: Badge,
        typeface: Typeface,
        textSizePx: Float,
        background: Int,
        text: Int,
    ): Boolean {
        if (count <= 0) {
            metrics = EMPTY
            label = ""
            return false
        }
        style = badge.style
        label = BadgeGeometry.label(count, badge.style)
        labelPaint.typeface = typeface
        labelPaint.textSize = BadgeGeometry.labelSize(badge.style, textSizePx, badge.scale)
        val labelWidth = if (label.isEmpty()) 0f else labelPaint.measureText(label)
        metrics = BadgeGeometry.of(badge.style, textSizePx, badge.scale, labelWidth)
        shapePaint.color = background
        // A plain number has no shape behind it, so the digits themselves take the badge's colour.
        labelPaint.color = if (badge.style == BadgeStyle.NUMBER) background else text
        setBounds(0, 0, metrics.widthPx.roundToInt(), metrics.heightPx.roundToInt())
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth(): Int = metrics.widthPx.roundToInt()

    override fun getIntrinsicHeight(): Int = metrics.heightPx.roundToInt()

    override fun draw(canvas: Canvas) {
        if (metrics === EMPTY) return
        val bounds = bounds
        if (style != BadgeStyle.NUMBER) {
            box.set(bounds)
            canvas.drawRoundRect(box, metrics.radiusPx, metrics.radiusPx, shapePaint)
        }
        if (label.isEmpty()) return
        val font = labelPaint.fontMetrics
        val baseline = bounds.exactCenterY() - (font.ascent + font.descent) / 2f
        canvas.drawText(label, bounds.exactCenterX(), baseline, labelPaint)
    }

    override fun setAlpha(alpha: Int) {
        shapePaint.alpha = alpha
        labelPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        shapePaint.colorFilter = colorFilter
        labelPaint.colorFilter = colorFilter
    }

    @Deprecated("Drawable.getOpacity is deprecated but still abstract.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        val EMPTY = BadgeMetrics(0f, 0f, 0f, 0f, 0f)
    }
}
