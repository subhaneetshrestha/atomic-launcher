package io.github.subhaneetshrestha.atomic.gestures

import io.github.subhaneetshrestha.atomic.core.theme.SwipeDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The recogniser is fed scripted touches, so every branch of the state machine is checked here
 * rather than by hand on a device. Distances are round numbers, not device values.
 */
class GestureRecognizerTest {
    private val thresholds =
        TouchThresholds(
            touchSlopPx = 20f,
            doubleTapSlopPx = 60f,
            minFlingVelocityPxPerSec = 500f,
            longPressTimeoutMs = 400,
            doubleTapTimeoutMs = 300,
            swipeMinPx = 100f,
            longSwipeVerticalPx = 400f,
            longSwipeHorizontalPx = 300f,
        )

    private val recognizer = GestureRecognizer(thresholds)

    private fun feed(vararg samples: TouchSample): List<GestureEvent> = samples.flatMap(recognizer::onTouch)

    private fun down(
        x: Float,
        y: Float,
        t: Long,
        onChild: Boolean = false,
    ) = TouchSample(TouchAction.DOWN, x, y, t, onInteractiveChild = onChild)

    private fun move(
        x: Float,
        y: Float,
        t: Long,
        onChild: Boolean = false,
        deepPress: Boolean = false,
    ) = TouchSample(TouchAction.MOVE, x, y, t, onInteractiveChild = onChild, deepPress = deepPress)

    private fun up(
        x: Float,
        y: Float,
        t: Long,
        vx: Float = 0f,
        vy: Float = 0f,
        onChild: Boolean = false,
    ) = TouchSample(TouchAction.UP, x, y, t, vx = vx, vy = vy, onInteractiveChild = onChild)

    private fun secondFinger(
        x: Float,
        y: Float,
        t: Long,
    ) = TouchSample(TouchAction.POINTER_DOWN, x, y, t, pointerCount = 2)

    private fun cancel(
        x: Float,
        y: Float,
        t: Long,
    ) = TouchSample(TouchAction.CANCEL, x, y, t)

    @Test
    fun `a drag past the swipe distance is a short swipe in that direction`() {
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            feed(down(500f, 1000f, 0), move(500f, 900f, 40), up(500f, 850f, 80)),
        )
        recognizer.reset()
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.DOWN, long = false)),
            feed(down(500f, 500f, 0), move(500f, 600f, 40), up(500f, 650f, 80)),
        )
        recognizer.reset()
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.LEFT, long = false)),
            feed(down(800f, 500f, 0), move(700f, 500f, 40), up(650f, 500f, 80)),
        )
        recognizer.reset()
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.RIGHT, long = false)),
            feed(down(200f, 500f, 0), move(300f, 500f, 40), up(350f, 500f, 80)),
        )
    }

    @Test
    fun `a drag that stays short and slow is not a swipe`() {
        assertEquals(emptyList(), feed(down(500f, 500f, 0), move(500f, 460f, 200), up(500f, 440f, 400)))
    }

    @Test
    fun `a short flick counts, because speed says the user meant it`() {
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            feed(down(500f, 500f, 0), move(500f, 470f, 20), up(500f, 450f, 40, vy = -1200f)),
        )
    }

    @Test
    fun `the axis is locked once the drag begins`() {
        val events =
            feed(
                down(500f, 1000f, 0),
                move(500f, 940f, 30),
                move(300f, 880f, 60),
                up(200f, 870f, 90),
            )

        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            events,
            "a vertical start stays vertical",
        )
    }

    @Test
    fun `nothing is reported until the finger leaves the screen`() {
        assertEquals(emptyList(), feed(down(500f, 1000f, 0), move(500f, 700f, 40)))
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            recognizer.onTouch(up(500f, 700f, 80)),
        )
    }

    @Test
    fun `the touch becomes a drag once it passes the slop, and is free again when it ends`() {
        recognizer.onTouch(down(500f, 1000f, 0))
        assertEquals(false, recognizer.isDragging, "a press alone still belongs to whatever is under it")

        recognizer.onTouch(move(500f, 900f, 40))
        assertEquals(true, recognizer.isDragging, "from here the gesture owns the touch")

        recognizer.onTouch(up(500f, 850f, 80))
        assertEquals(false, recognizer.isDragging)
    }

    @Test
    fun `crossing the long distance arms the swipe once and the release is a long one`() {
        val events =
            feed(
                down(500f, 1000f, 0),
                move(500f, 900f, 30),
                move(500f, 560f, 60),
                move(500f, 540f, 75),
                up(500f, 540f, 90),
            )

        assertEquals(
            listOf(
                GestureEvent.LongSwipeArmed(SwipeDirection.UP),
                GestureEvent.Swipe(SwipeDirection.UP, long = true),
            ),
            events,
            "armed on the crossing, not on every reading after it",
        )
    }

    @Test
    fun `pulling back below the threshold disarms, and the release is a short swipe`() {
        val events =
            feed(
                down(500f, 1000f, 0),
                move(500f, 560f, 30),
                move(500f, 680f, 60),
                up(500f, 700f, 90),
            )

        assertEquals(
            listOf(
                GestureEvent.LongSwipeArmed(SwipeDirection.UP),
                GestureEvent.LongSwipeDisarmed(SwipeDirection.UP),
                GestureEvent.Swipe(SwipeDirection.UP, long = false),
            ),
            events,
            "320 px is under the 340 px the swipe must fall back to before it disarms",
        )
    }

    @Test
    fun `sideways swipes have their own long distance, because a screen is taller than it is wide`() {
        val sideways = feed(down(100f, 500f, 0), move(200f, 500f, 30), move(420f, 500f, 60), up(420f, 500f, 90))
        assertEquals(
            listOf(
                GestureEvent.LongSwipeArmed(SwipeDirection.RIGHT),
                GestureEvent.Swipe(SwipeDirection.RIGHT, long = true),
            ),
            sideways,
            "320 px sideways is a long swipe",
        )

        recognizer.reset()

        val upwards = feed(down(500f, 1000f, 0), move(500f, 900f, 30), up(500f, 680f, 60))
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            upwards,
            "the same 320 px upwards is not",
        )
    }

    @Test
    fun `two quick taps in the same place are a double tap`() {
        assertEquals(emptyList(), feed(down(500f, 500f, 0), up(500f, 500f, 50)), "one tap on its own does nothing")

        assertEquals(listOf(GestureEvent.DoubleTap), feed(down(505f, 505f, 200), up(505f, 505f, 250)))
    }

    @Test
    fun `taps too far apart in time or in place are just two taps`() {
        assertEquals(
            emptyList(),
            feed(down(500f, 500f, 0), up(500f, 500f, 50), down(500f, 500f, 400), up(500f, 500f, 450)),
        )

        recognizer.reset()

        assertEquals(
            emptyList(),
            feed(down(500f, 500f, 0), up(500f, 500f, 50), down(700f, 700f, 200), up(700f, 700f, 250)),
        )
    }

    @Test
    fun `holding still reports a long press once the timeout passes, and the release adds nothing`() {
        recognizer.onTouch(down(500f, 500f, 0))

        assertEquals(400L, recognizer.nextDeadlineMs(), "the caller knows when to ask again")
        assertEquals(emptyList(), recognizer.onTimeout(399), "not yet")
        assertEquals(listOf(GestureEvent.LongPress), recognizer.onTimeout(400))
        assertNull(recognizer.nextDeadlineMs(), "it only fires once")
        assertEquals(emptyList(), recognizer.onTouch(up(500f, 500f, 900)))
    }

    @Test
    fun `a press that turns into a drag is no longer a long press`() {
        recognizer.onTouch(down(500f, 500f, 0))
        recognizer.onTouch(move(500f, 400f, 100))

        assertNull(recognizer.nextDeadlineMs())
        assertEquals(emptyList(), recognizer.onTimeout(400))
        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            recognizer.onTouch(up(500f, 380f, 150)),
        )
    }

    @Test
    fun `a firm press reports the long press without waiting`() {
        recognizer.onTouch(down(500f, 500f, 0))

        assertEquals(listOf(GestureEvent.LongPress), recognizer.onTouch(move(500f, 502f, 30, deepPress = true)))
        assertNull(recognizer.nextDeadlineMs())
    }

    @Test
    fun `a touch that starts on a row leaves taps and holds to the row, but still gives up its swipes`() {
        assertNull(recognizer.nextDeadlineMs())
        recognizer.onTouch(down(500f, 500f, 0, onChild = true))
        assertNull(recognizer.nextDeadlineMs(), "the row has its own hold handler")
        assertEquals(emptyList(), recognizer.onTimeout(400))
        assertEquals(emptyList(), recognizer.onTouch(up(500f, 500f, 50, onChild = true)))
        assertEquals(
            emptyList(),
            feed(down(505f, 505f, 200, onChild = true), up(505f, 505f, 250, onChild = true)),
            "the row's own click already ran twice",
        )

        recognizer.reset()

        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
            feed(down(500f, 1000f, 0, onChild = true), move(500f, 900f, 30), up(500f, 850f, 60)),
            "a swipe that begins on a row is still a swipe",
        )
    }

    @Test
    fun `a second finger abandons the gesture`() {
        val events =
            feed(
                down(500f, 1000f, 0),
                move(500f, 900f, 30),
                secondFinger(300f, 900f, 40),
                move(500f, 500f, 60),
                up(500f, 500f, 90),
            )

        assertEquals(emptyList(), events, "a two-finger gesture is not one of ours")
        assertNull(recognizer.nextDeadlineMs())
    }

    @Test
    fun `a cancelled touch reports nothing and leaves the next one free`() {
        assertEquals(emptyList(), feed(down(500f, 1000f, 0), move(500f, 700f, 30), cancel(500f, 700f, 60)))

        assertEquals(
            listOf(GestureEvent.Swipe(SwipeDirection.DOWN, long = false)),
            feed(down(500f, 500f, 100), move(500f, 600f, 130), up(500f, 650f, 160)),
        )
    }
}
