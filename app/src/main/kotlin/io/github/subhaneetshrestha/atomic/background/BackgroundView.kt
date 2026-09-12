package io.github.subhaneetshrestha.atomic.background

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import io.github.subhaneetshrestha.atomic.core.theme.Background
import io.github.subhaneetshrestha.atomic.core.theme.BackgroundMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * What is behind the app names: the theme's colour, a gradient, an image, or nothing at all when
 * the device wallpaper is showing through the window. It is the first child of the home root, so
 * it fills the screen including behind the system bars, and it is the only thing in the launcher
 * that ever holds a bitmap.
 */
class BackgroundView(
    context: Context,
) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gradientPaint = Paint()
    private val matrix = Matrix()

    private var mode = BackgroundMode.COLOR
    private var baseColor = Color.BLACK
    private var gradientFrom = Color.BLACK
    private var gradientTo = Color.BLACK
    private var gradientAngle = 0
    private var gradient: LinearGradient? = null
    private var dim = 0f

    private var image: Bitmap? = null
    private var outgoing: Bitmap? = null
    private var fade = 1f
    private var animator: ValueAnimator? = null

    /** The size of the window, which is the size the image is decoded to. */
    var onSize: ((Int, Int) -> Unit)? = null

    init {
        setWillNotDraw(false)
    }

    /** The colours and the shape of the background, from the theme. The image arrives separately. */
    fun apply(
        background: Background,
        resolvedBackgroundColor: Int,
    ) {
        mode = background.mode
        baseColor = resolvedBackgroundColor
        gradientFrom = parse(background.gradient.from, resolvedBackgroundColor)
        gradientTo = parse(background.gradient.to, resolvedBackgroundColor)
        gradientAngle = background.gradient.angle
        dim = background.dim.coerceIn(0f, 1f)
        gradient = null
        if (mode != BackgroundMode.COLLECTION) setImage(null, animate = false)
        invalidate()
    }

    /** Crossfades to [bitmap]; null clears the image and shows whatever is behind it. */
    fun setImage(
        bitmap: Bitmap?,
        animate: Boolean,
    ) {
        if (bitmap === image) return
        animator?.cancel()
        outgoing = if (animate) image else null
        image = bitmap
        if (!animate || outgoing == null) {
            fade = 1f
            invalidate()
            return
        }
        fade = 0f
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CROSSFADE_MS
                addUpdateListener {
                    fade = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            outgoing = null
                            invalidate()
                        }
                    },
                )
                start()
            }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = null
        onSize?.invoke(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == BackgroundMode.WALLPAPER) return
        when (mode) {
            BackgroundMode.GRADIENT -> drawGradient(canvas)
            else -> canvas.drawColor(baseColor)
        }
        val current = image ?: return
        outgoing?.let { previous ->
            paint.alpha = OPAQUE
            drawCovering(canvas, previous)
        }
        paint.alpha = (fade * OPAQUE).toInt().coerceIn(0, OPAQUE)
        drawCovering(canvas, current)
        paint.alpha = OPAQUE
        if (dim > 0f) canvas.drawColor(Color.argb((dim * OPAQUE).toInt(), 0, 0, 0))
    }

    private fun drawGradient(canvas: Canvas) {
        val shader = gradient ?: buildGradient().also { gradient = it }
        gradientPaint.shader = shader
        canvas.drawPaint(gradientPaint)
    }

    /** [gradientAngle] is degrees clockwise from straight down, so 0 runs top to bottom. */
    private fun buildGradient(): LinearGradient {
        val radians = Math.toRadians(gradientAngle.toDouble())
        val dx = sin(radians).toFloat()
        val dy = cos(radians).toFloat()
        val extent = abs(width * dx) + abs(height * dy)
        val cx = width / 2f
        val cy = height / 2f
        return LinearGradient(
            cx - dx * extent / 2f,
            cy - dy * extent / 2f,
            cx + dx * extent / 2f,
            cy + dy * extent / 2f,
            gradientFrom,
            gradientTo,
            Shader.TileMode.CLAMP,
        )
    }

    /** Centre crop: scaled to cover the view, with the overflow split evenly on both sides. */
    private fun drawCovering(
        canvas: Canvas,
        bitmap: Bitmap,
    ) {
        if (bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate((width - bitmap.width * scale) / 2f, (height - bitmap.height * scale) / 2f)
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun parse(
        value: String,
        fallback: Int,
    ): Int =
        try {
            Color.parseColor(value)
        } catch (e: IllegalArgumentException) {
            fallback
        }

    private companion object {
        const val CROSSFADE_MS = 250L
        const val OPAQUE = 255
    }
}
