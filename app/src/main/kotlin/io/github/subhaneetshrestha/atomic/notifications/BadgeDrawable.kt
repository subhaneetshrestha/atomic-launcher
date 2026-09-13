package io.github.subhaneetshrestha.atomic.notifications

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import io.github.subhaneetshrestha.atomic.core.theme.Badge
import io.github.subhaneetshrestha.atomic.core.theme.BadgeStyle
import io.github.subhaneetshrestha.atomic.ui.Motion
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

    /** The last count drawn, so a badge can tell an arrival from a redraw. */
    private var drawnCount = 0
    private var pop = 1f

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
            drawnCount = 0
            return false
        }
        // Something arrived while you were looking at the screen. Not a first draw, not a redraw
        // after a settings change: only a number that went up.
        val arrived = count > drawnCount && drawnCount > 0
        drawnCount = count
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
        if (arrived) popIn()
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth(): Int = metrics.widthPx.roundToInt()

    override fun getIntrinsicHeight(): Int = metrics.heightPx.roundToInt()

    private fun popIn() {
        if (!Motion.enabled) return
        ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = Motion.BADGE_MS
            interpolator = Motion.ease
            addUpdateListener {
                pop = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (metrics === EMPTY) return
        val bounds = bounds
        val popped = pop != 1f
        if (popped) {
            canvas.save()
            canvas.scale(pop, pop, bounds.exactCenterX(), bounds.exactCenterY())
        }
        if (style != BadgeStyle.NUMBER) {
            box.set(bounds)
            canvas.drawRoundRect(box, metrics.radiusPx, metrics.radiusPx, shapePaint)
        }
        if (label.isNotEmpty()) {
            val font = labelPaint.fontMetrics
            val baseline = bounds.exactCenterY() - (font.ascent + font.descent) / 2f
            canvas.drawText(label, bounds.exactCenterX(), baseline, labelPaint)
        }
        if (popped) canvas.restore()
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
