package io.github.subhaneetshrestha.atomic.gestures

import io.github.subhaneetshrestha.atomic.core.theme.SwipeDirection
import kotlin.math.abs
import kotlin.math.hypot

enum class TouchAction { DOWN, MOVE, UP, CANCEL, POINTER_DOWN, POINTER_UP }

/** One touch reading, in screen pixels. [vx]/[vy] are px per second and only meaningful on [TouchAction.UP]. */
data class TouchSample(
    val action: TouchAction,
    val x: Float,
    val y: Float,
    val timeMs: Long,
    val pointerCount: Int = 1,
    /** The touch began on a row or an info line, which answers its own taps and holds. */
    val onInteractiveChild: Boolean = false,
    /** The platform read this as a deliberate firm press, so the hold need not be waited out. */
    val deepPress: Boolean = false,
    val vx: Float = 0f,
    val vy: Float = 0f,
)

/** Distances and times the recogniser measures against; the view fills these from ViewConfiguration. */
data class TouchThresholds(
    val touchSlopPx: Float,
    val doubleTapSlopPx: Float,
    val minFlingVelocityPxPerSec: Float,
    val longPressTimeoutMs: Long,
    val doubleTapTimeoutMs: Long,
    /** How far a drag must travel to count as a swipe at all. */
    val swipeMinPx: Float,
    val longSwipeVerticalPx: Float,
    val longSwipeHorizontalPx: Float,
)

sealed class GestureEvent {
    data class Swipe(
        val direction: SwipeDirection,
        val long: Boolean,
    ) : GestureEvent()

    /** The drag has travelled far enough that letting go now would be a long swipe. */
    data class LongSwipeArmed(
        val direction: SwipeDirection,
    ) : GestureEvent()

    /** It has come back short of that, so letting go would be an ordinary swipe again. */
    data class LongSwipeDisarmed(
        val direction: SwipeDirection,
    ) : GestureEvent()

    data object DoubleTap : GestureEvent()

    data object LongPress : GestureEvent()
}

/**
 * Turns a stream of touches into the gestures the home screen binds actions to. Pure Kotlin with
 * no Android types, so every path is checked by unit tests instead of by hand on a device.
 *
 * One finger, one gesture per touch. The drag axis locks as soon as the finger passes the touch
 * slop, so a swipe that wanders sideways still reads as the swipe it started as. A touch that
 * begins on a row leaves the tap and the hold to that row and only gives up its swipes. A hold
 * needs no touch of its own to be noticed, so the caller asks [onTimeout] when [nextDeadlineMs]
 * says to.
 */
class GestureRecognizer(
    private val thresholds: TouchThresholds,
) {
    private enum class State { IDLE, PRESSED, DRAGGING, HELD, ABANDONED }

    private data class Tap(
        val x: Float,
        val y: Float,
        val upTimeMs: Long,
    )

    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f
    private var startedOnChild = false
    private var vertical = false
    private var armed = false
    private var secondPress = false
    private var deadlineMs: Long? = null
    private var lastTap: Tap? = null

    /** True once the finger has travelled far enough that this touch belongs to a gesture. */
    val isDragging: Boolean get() = state == State.DRAGGING

    fun onTouch(sample: TouchSample): List<GestureEvent> =
        when (sample.action) {
            TouchAction.DOWN -> {
                onDown(sample)
            }

            TouchAction.MOVE -> {
                onMove(sample)
            }

            TouchAction.UP -> {
                onUp(sample)
            }

            TouchAction.CANCEL -> {
                abandon(State.IDLE)
                NOTHING
            }

            // A second finger is never one of our gestures; the rest of this touch is ignored.
            TouchAction.POINTER_DOWN -> {
                abandon(State.ABANDONED)
                NOTHING
            }

            TouchAction.POINTER_UP -> {
                NOTHING
            }
        }

    /** When the caller should ask [onTimeout], or null while nothing is waiting to fire. */
    fun nextDeadlineMs(): Long? = deadlineMs

    fun onTimeout(nowMs: Long): List<GestureEvent> {
        val deadline = deadlineMs ?: return NOTHING
        if (nowMs < deadline) return NOTHING
        deadlineMs = null
        return if (state == State.PRESSED) hold() else NOTHING
    }

    fun reset() = abandon(State.IDLE)

    private fun onDown(sample: TouchSample): List<GestureEvent> {
        val previousTap = lastTap
        downX = sample.x
        downY = sample.y
        startedOnChild = sample.onInteractiveChild
        vertical = false
        armed = false
        lastTap = null
        secondPress = !startedOnChild && previousTap != null && follows(previousTap, sample)
        state = State.PRESSED
        deadlineMs = if (startedOnChild) null else sample.timeMs + thresholds.longPressTimeoutMs
        return if (sample.deepPress && !startedOnChild) hold() else NOTHING
    }

    private fun onMove(sample: TouchSample): List<GestureEvent> =
        when (state) {
            State.PRESSED -> {
                if (sample.deepPress && !startedOnChild) hold() else beginDragIfPastSlop(sample)
            }

            State.DRAGGING -> {
                updateArming(sample)
            }

            State.IDLE, State.HELD, State.ABANDONED -> {
                NOTHING
            }
        }

    private fun onUp(sample: TouchSample): List<GestureEvent> {
        val was = state
        val wasSecondPress = secondPress
        val wasOnChild = startedOnChild
        state = State.IDLE
        armed = false
        secondPress = false
        deadlineMs = null
        return when {
            was == State.DRAGGING -> {
                swipe(sample)
            }

            was != State.PRESSED || wasOnChild -> {
                NOTHING
            }

            wasSecondPress -> {
                listOf(GestureEvent.DoubleTap)
            }

            else -> {
                // A tap on its own does nothing; it only waits to see whether a second one follows.
                lastTap = Tap(sample.x, sample.y, sample.timeMs)
                NOTHING
            }
        }
    }

    private fun follows(
        tap: Tap,
        sample: TouchSample,
    ): Boolean =
        sample.timeMs - tap.upTimeMs <= thresholds.doubleTapTimeoutMs &&
            hypot(sample.x - tap.x, sample.y - tap.y) <= thresholds.doubleTapSlopPx

    private fun hold(): List<GestureEvent> {
        state = State.HELD
        deadlineMs = null
        return listOf(GestureEvent.LongPress)
    }

    private fun beginDragIfPastSlop(sample: TouchSample): List<GestureEvent> {
        val dx = sample.x - downX
        val dy = sample.y - downY
        if (hypot(dx, dy) <= thresholds.touchSlopPx) return NOTHING
        vertical = abs(dy) > abs(dx)
        state = State.DRAGGING
        deadlineMs = null
        // One large reading can cross both the slop and the long distance at once.
        return updateArming(sample)
    }

    /**
     * Tells the caller when the drag crosses into long-swipe range and when it falls back out, so
     * a fingertip can feel where the boundary is. Coming back out needs a little more travel than
     * going in, so a finger resting on the line does not buzz over and over.
     */
    private fun updateArming(sample: TouchSample): List<GestureEvent> {
        val travel = travel(sample)
        val distance = abs(travel)
        val threshold = longDistance()
        return when {
            !armed && distance >= threshold -> {
                armed = true
                listOf(GestureEvent.LongSwipeArmed(direction(travel)))
            }

            armed && distance < threshold * DISARM_FRACTION -> {
                armed = false
                listOf(GestureEvent.LongSwipeDisarmed(direction(travel)))
            }

            else -> {
                NOTHING
            }
        }
    }

    private fun swipe(sample: TouchSample): List<GestureEvent> {
        val travel = travel(sample)
        val speed = if (vertical) sample.vy else sample.vx
        val distance = abs(travel)
        val farEnough = distance >= thresholds.swipeMinPx
        val fastEnough = distance >= 2 * thresholds.touchSlopPx && abs(speed) >= thresholds.minFlingVelocityPxPerSec
        if (!farEnough && !fastEnough) return NOTHING
        return listOf(GestureEvent.Swipe(direction(travel), long = distance >= longDistance()))
    }

    private fun abandon(next: State) {
        state = next
        armed = false
        secondPress = false
        deadlineMs = null
        lastTap = null
    }

    private fun travel(sample: TouchSample): Float = if (vertical) sample.y - downY else sample.x - downX

    private fun longDistance(): Float =
        if (vertical) thresholds.longSwipeVerticalPx else thresholds.longSwipeHorizontalPx

    private fun direction(travel: Float): SwipeDirection =
        when {
            vertical && travel < 0 -> SwipeDirection.UP
            vertical -> SwipeDirection.DOWN
            travel < 0 -> SwipeDirection.LEFT
            else -> SwipeDirection.RIGHT
        }

    private companion object {
        val NOTHING = emptyList<GestureEvent>()

        /** How far back a long swipe must come before it counts as short again. */
        const val DISARM_FRACTION = 0.85f
    }
}
