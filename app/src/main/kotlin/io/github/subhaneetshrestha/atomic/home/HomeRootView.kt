package io.github.subhaneetshrestha.atomic.home

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.subhaneetshrestha.atomic.gestures.GestureEvent
import io.github.subhaneetshrestha.atomic.gestures.GestureRecognizer
import io.github.subhaneetshrestha.atomic.gestures.TouchAction
import io.github.subhaneetshrestha.atomic.gestures.TouchSample
import io.github.subhaneetshrestha.atomic.gestures.TouchThresholds
import kotlin.math.max

/**
 * Root of the home window, and the surface that reads gestures. The frame itself is never padded
 * so a later background can draw edge to edge behind everything; the content takes the system-bar
 * and display-cutout insets. Insets are returned unconsumed so a later search overlay can react to
 * the keyboard itself.
 *
 * Every touch is watched on its way through, so a swipe is recognised whether it begins on empty
 * space or on an app row; once it becomes a drag the children are told to let go, so the row under
 * the finger does not also open.
 */
class HomeRootView(
    context: Context,
) : FrameLayout(context) {
    var onGesture: ((GestureEvent) -> Unit)? = null

    /**
     * While the search is up it owns the screen: the home gestures stand down, and the list and
     * info lines behind it are neither drawn nor reachable by a screen reader.
     */
    var searchOpen: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) recognizer.reset()
            content.visibility = if (value) INVISIBLE else VISIBLE
        }

    /** Take the left and right edges from the system back gesture. The home app may; others may not. */
    var takeOverSideEdges: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyEdgeExclusion()
        }

    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val blockHost = FrameLayout(context)
    private var recognizer = GestureRecognizer(thresholds())
    private var velocity: VelocityTracker? = null
    private var startedOnChild = false
    private val timeout = Runnable { emit(recognizer.onTimeout(SystemClock.uptimeMillis())) }

    init {
        content.addView(blockHost, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    /** The block of info lines plus app list, placed by the theme's vertical alignment. */
    fun setBlock(
        block: View,
        verticalGravity: Int,
    ) {
        blockHost.removeAllViews()
        blockHost.addView(block, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, verticalGravity))
    }

    fun positionBlock(verticalGravity: Int) {
        val params = blockHost.getChildAt(0)?.layoutParams as? LayoutParams ?: return
        if (params.gravity != verticalGravity) {
            params.gravity = verticalGravity
            blockHost.requestLayout()
        }
    }

    /** Painted behind everything, edge to edge: the colour, gradient or image of the theme. */
    fun setBackgroundLayer(view: View) {
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Covers everything, above the app list and the info lines; it takes its own insets. */
    fun setOverlay(view: View) {
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** A full-width row below the block, e.g. the default-home banner. */
    fun addFooter(view: View) {
        content.addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        // How far a long swipe must run depends on how much screen there is to run across.
        recognizer = GestureRecognizer(thresholds())
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) applyEdgeExclusion()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!searchOpen) watch(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** Once the touch is a drag it belongs to the gesture, so the row under the finger is cancelled. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !searchOpen && recognizer.isDragging

    override fun onTouchEvent(ev: MotionEvent): Boolean = !searchOpen

    private fun watch(ev: MotionEvent) {
        val action =
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> TouchAction.DOWN
                MotionEvent.ACTION_MOVE -> TouchAction.MOVE
                MotionEvent.ACTION_UP -> TouchAction.UP
                MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
                MotionEvent.ACTION_POINTER_DOWN -> TouchAction.POINTER_DOWN
                MotionEvent.ACTION_POINTER_UP -> TouchAction.POINTER_UP
                else -> return
            }
        if (action == TouchAction.DOWN) {
            velocity?.recycle()
            velocity = VelocityTracker.obtain()
            startedOnChild = touchesInteractiveChild(ev.x, ev.y)
        }
        velocity?.addMovement(ev)
        var vx = 0f
        var vy = 0f
        if (action == TouchAction.UP) {
            velocity?.computeCurrentVelocity(MILLIS_PER_SECOND)
            vx = velocity?.xVelocity ?: 0f
            vy = velocity?.yVelocity ?: 0f
        }
        emit(
            recognizer.onTouch(
                TouchSample(
                    action = action,
                    x = ev.x,
                    y = ev.y,
                    timeMs = ev.eventTime,
                    pointerCount = ev.pointerCount,
                    onInteractiveChild = startedOnChild,
                    deepPress = isDeepPress(ev),
                    vx = vx,
                    vy = vy,
                ),
            ),
        )
        if (action == TouchAction.UP || action == TouchAction.CANCEL) {
            velocity?.recycle()
            velocity = null
        }
        rearmTimeout()
    }

    private fun emit(events: List<GestureEvent>) {
        for (event in events) onGesture?.invoke(event)
    }

    /** A hold has no touch of its own to announce it, so it is asked for at the moment it is due. */
    private fun rearmTimeout() {
        removeCallbacks(timeout)
        val deadline = recognizer.nextDeadlineMs() ?: return
        postDelayed(timeout, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
    }

    private fun isDeepPress(ev: MotionEvent): Boolean =
        Build.VERSION.SDK_INT >= 29 && ev.classification == MotionEvent.CLASSIFICATION_DEEP_PRESS

    private fun touchesInteractiveChild(
        x: Float,
        y: Float,
    ): Boolean {
        for (index in childCount - 1 downTo 0) if (interactiveAt(getChildAt(index), x, y)) return true
        return false
    }

    /** [x] and [y] are in the coordinates of [view]'s parent. */
    private fun interactiveAt(
        view: View,
        x: Float,
        y: Float,
    ): Boolean {
        if (view.visibility != VISIBLE) return false
        if (x < view.left || x > view.right || y < view.top || y > view.bottom) return false
        val childX = x - view.left
        val childY = y - view.top
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                if (interactiveAt(view.getChildAt(index), childX, childY)) return true
            }
        }
        return view.isClickable || view.isLongClickable
    }

    private fun applyEdgeExclusion() {
        if (Build.VERSION.SDK_INT < 29) return
        systemGestureExclusionRects =
            if (!takeOverSideEdges || width == 0 || height == 0) {
                emptyList()
            } else {
                val gestures = ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemGestures())
                val edge = max(max(gestures?.left ?: 0, gestures?.right ?: 0), dp(MIN_EDGE_DP))
                listOf(Rect(0, 0, edge, height), Rect(width - edge, 0, width, height))
            }
    }

    private fun thresholds(): TouchThresholds {
        val configuration = ViewConfiguration.get(context)
        return TouchThresholds(
            touchSlopPx = configuration.scaledTouchSlop.toFloat(),
            doubleTapSlopPx = configuration.scaledDoubleTapSlop.toFloat(),
            minFlingVelocityPxPerSec = configuration.scaledMinimumFlingVelocity.toFloat(),
            longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong(),
            doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
            swipeMinPx = dp(SWIPE_MIN_DP).toFloat(),
            longSwipeVerticalPx = longSwipe(height),
            longSwipeHorizontalPx = longSwipe(width),
        )
    }

    /** About a third of the way across, but never so short that it is easy to hit by accident. */
    private fun longSwipe(extent: Int): Float =
        (LONG_SWIPE_FRACTION * extent).coerceIn(dp(LONG_SWIPE_MIN_DP).toFloat(), dp(LONG_SWIPE_MAX_DP).toFloat())

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MILLIS_PER_SECOND = 1000
        const val SWIPE_MIN_DP = 48
        const val LONG_SWIPE_FRACTION = 0.35f
        const val LONG_SWIPE_MIN_DP = 140
        const val LONG_SWIPE_MAX_DP = 320
        const val MIN_EDGE_DP = 24
    }
}
