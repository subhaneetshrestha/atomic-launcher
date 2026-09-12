package io.github.subhaneetshrestha.atomic.home.info

import kotlin.test.Test
import kotlin.test.assertEquals

/** How long the phone has been in use today, from what Android says about apps coming and going. */
class ScreenTimeCalculatorTest {
    private val midnight = 1_700_000_000_000L
    private val minute = 60_000L
    private val now = midnight + 600 * minute

    private fun resumed(
        pkg: String,
        minutes: Long,
    ) = ForegroundEvent(pkg, midnight + minutes * minute, resumed = true)

    private fun paused(
        pkg: String,
        minutes: Long,
    ) = ForegroundEvent(pkg, midnight + minutes * minute, resumed = false)

    @Test
    fun `nothing used is no time at all`() {
        assertEquals(0, ScreenTimeCalculator.total(emptyList(), midnight, now))
        assertEquals(0, ScreenTimeCalculator.total(listOf(resumed("a", 5)), midnight, midnight))
    }

    @Test
    fun `one app, in and out, is the time between`() {
        val total = ScreenTimeCalculator.total(listOf(resumed("a", 10), paused("a", 40)), midnight, now)
        assertEquals(30 * minute, total)
    }

    @Test
    fun `two apps one after the other add up`() {
        val total =
            ScreenTimeCalculator.total(
                listOf(resumed("a", 10), paused("a", 20), resumed("b", 30), paused("b", 45)),
                midnight,
                now,
            )
        assertEquals(25 * minute, total)
    }

    @Test
    fun `an overlap across a handover counts once`() {
        // The outgoing app pauses a moment after the incoming one resumes, which is normal.
        val total =
            ScreenTimeCalculator.total(
                listOf(resumed("a", 10), resumed("b", 20), paused("a", 21), paused("b", 40)),
                midnight,
                now,
            )
        assertEquals(30 * minute, total, "ten past to forty past is thirty minutes, not thirty-one")
    }

    @Test
    fun `an app still in front is counted up to now`() {
        val total = ScreenTimeCalculator.total(listOf(resumed("a", 580)), midnight, now)
        assertEquals(20 * minute, total)
    }

    @Test
    fun `an app already in front at midnight is counted from midnight`() {
        val total = ScreenTimeCalculator.total(listOf(paused("a", 15)), midnight, now)
        assertEquals(15 * minute, total)
    }

    @Test
    fun `the launcher's own time is not screen time`() {
        val total =
            ScreenTimeCalculator.total(
                listOf(resumed("home", 0), paused("home", 10), resumed("a", 10), paused("a", 25)),
                midnight,
                now,
                exclude = setOf("home"),
            )
        assertEquals(15 * minute, total)
    }

    @Test
    fun `events from before midnight or after now are pulled into the day`() {
        val total =
            ScreenTimeCalculator.total(
                listOf(
                    ForegroundEvent("a", midnight - 100 * minute, resumed = true),
                    ForegroundEvent("a", now + 100 * minute, resumed = false),
                ),
                midnight,
                now,
            )
        assertEquals(600 * minute, total, "a day is as long as it is, whatever the events say")
    }

    @Test
    fun `events out of order are still read in order`() {
        val total =
            ScreenTimeCalculator.total(listOf(paused("a", 40), resumed("a", 10)), midnight, now)
        assertEquals(30 * minute, total)
    }
}
