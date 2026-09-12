package io.github.subhaneetshrestha.atomic.core.collections

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When the background changes, to what, and how long it waits after things go wrong. */
class RotationTest {
    private val hour = 60 * 60 * 1000L
    private val urls = (1..5).map { "https://example.org/$it.jpg" }
    private val source = "https://example.org/list.txt"

    private fun stateWith(
        now: Long = 0,
        current: String? = null,
        history: List<String> = emptyList(),
        bad: List<String> = emptyList(),
    ) = BackgroundState(
        index = IndexCache(source, now, urls),
        currentUrl = current,
        currentAt = now,
        history = history,
        bad = bad,
    )

    @Test
    fun `an index is trusted for six hours and only for the address it came from`() {
        val state = stateWith(now = 0)
        assertTrue(Rotation.indexIsFresh(state, source, Rotation.INDEX_TTL_MS - 1))
        assertFalse(Rotation.indexIsFresh(state, source, Rotation.INDEX_TTL_MS))
        assertFalse(Rotation.indexIsFresh(state, "https://example.org/other.txt", 0))
        assertFalse(
            Rotation.indexIsFresh(state, source, -hour),
            "a clock that has gone backwards makes the cache untrustworthy, not eternal",
        )
        assertFalse(Rotation.indexIsFresh(BackgroundState(), source, 0))
    }

    @Test
    fun `the first run is always due, and a clock that went backwards does not stall it`() {
        assertTrue(Rotation.isDue(BackgroundState(), now = 0, intervalMs = 6 * hour))
        val state = stateWith(now = 10 * hour, current = urls[0])
        assertFalse(Rotation.isDue(state, now = 12 * hour, intervalMs = 6 * hour))
        assertTrue(Rotation.isDue(state, now = 16 * hour, intervalMs = 6 * hour))
        assertTrue(Rotation.isDue(state, now = 2 * hour, intervalMs = 6 * hour))
    }

    @Test
    fun `in order, the list is walked and wraps around`() {
        var state = stateWith(current = null)
        assertEquals(urls[0], Rotation.next(state, shuffle = false))
        state = state.copy(currentUrl = urls[3])
        assertEquals(urls[4], Rotation.next(state, shuffle = false))
        state = state.copy(currentUrl = urls[4])
        assertEquals(urls[0], Rotation.next(state, shuffle = false))
    }

    @Test
    fun `shuffled, what was shown lately is skipped until there is nothing else`() {
        val state = stateWith(current = urls[0], history = urls.drop(1).take(3))
        assertEquals(urls[4], Rotation.next(state, shuffle = true), "only one image is neither current nor recent")
        val exhausted = stateWith(current = urls[0], history = urls)
        val picked = Rotation.next(exhausted, shuffle = true, random = Random(1))
        assertTrue(picked in urls && picked != urls[0], "with everything seen it starts again, but not on the current")
    }

    @Test
    fun `an address that failed for good is never offered again`() {
        val state = stateWith(current = urls[0], bad = listOf(urls[1], urls[2], urls[3], urls[4]))
        assertEquals(urls[0], Rotation.next(state, shuffle = true), "the only one left is the one already showing")
        val allBad = stateWith(bad = urls)
        assertNull(Rotation.next(allBad, shuffle = true))
        assertNull(Rotation.next(BackgroundState(), shuffle = true))
    }

    @Test
    fun `a success clears the failure count and remembers what was shown`() {
        val failed =
            Rotation.failed(
                stateWith(),
                urls[0],
                now = hour,
                error = "timeout",
                intervalMs = hour,
                permanent = false,
            )
        val ok = Rotation.succeeded(failed, urls[1], now = 2 * hour)
        assertEquals(urls[1], ok.currentUrl)
        assertEquals(2 * hour, ok.currentAt)
        assertEquals(listOf(urls[1]), ok.history)
        assertEquals(0, ok.failures)
        assertEquals(0, ok.retryAfter)
        assertNull(ok.lastError)
    }

    @Test
    fun `history keeps the last twenty, newest first, without repeats`() {
        var state = BackgroundState()
        for (index in 1..Rotation.HISTORY_SIZE + 5) {
            state = Rotation.succeeded(state, "https://example.org/$index.jpg", now = index.toLong())
        }
        assertEquals(Rotation.HISTORY_SIZE, state.history.size)
        assertEquals("https://example.org/25.jpg", state.history.first())
        state = Rotation.succeeded(state, "https://example.org/25.jpg", now = 99)
        assertEquals(Rotation.HISTORY_SIZE, state.history.size)
        assertEquals(state.history.distinct(), state.history)
    }

    @Test
    fun `a permanent failure remembers the address, a passing one only delays`() {
        val gone = Rotation.failed(stateWith(), urls[0], now = 0, error = "404", intervalMs = hour, permanent = true)
        assertEquals(listOf(urls[0]), gone.bad)
        val offline =
            Rotation.failed(
                gone,
                urls[1],
                now = 0,
                error = "no network",
                intervalMs = hour,
                permanent = false,
            )
        assertEquals(listOf(urls[0]), offline.bad, "a network that is down says nothing about the address")
        assertEquals(2, offline.failures)
    }

    @Test
    fun `the bad list is a ring, not a leak`() {
        var state = BackgroundState()
        for (index in 1..Rotation.BAD_SIZE + 10) {
            state = Rotation.failed(state, "https://example.org/$index.jpg", 0, "404", hour, permanent = true)
        }
        assertEquals(Rotation.BAD_SIZE, state.bad.size)
        assertEquals("https://example.org/60.jpg", state.bad.first())
    }

    @Test
    fun `waiting doubles with each failure and stops at a day`() {
        assertEquals(hour, Rotation.backoffMs(hour, failures = 1))
        assertEquals(2 * hour, Rotation.backoffMs(hour, failures = 2))
        assertEquals(8 * hour, Rotation.backoffMs(hour, failures = 4))
        assertEquals(Rotation.MAX_BACKOFF_MS, Rotation.backoffMs(hour, failures = 9))
        assertEquals(Rotation.MAX_BACKOFF_MS, Rotation.backoffMs(hour, failures = 2000))
        assertEquals(0, Rotation.backoffMs(hour, failures = 0))
    }

    @Test
    fun `a stored retry time far in the future does not park the background forever`() {
        val state = BackgroundState(retryAfter = 10 * hour)
        assertFalse(Rotation.mayTry(state, now = 9 * hour))
        assertTrue(Rotation.mayTry(state, now = 10 * hour))
        val absurd = BackgroundState(retryAfter = Long.MAX_VALUE)
        assertTrue(Rotation.mayTry(absurd, now = 0), "a clock change must not cost the user their background")
    }

    @Test
    fun `a skipped run records why and leaves everything else alone`() {
        val state = stateWith(current = urls[0])
        val skipped = Rotation.skipped(state, "on mobile data")
        assertEquals("on mobile data", skipped.lastSkip)
        assertEquals(state.copy(lastSkip = "on mobile data"), skipped)
    }

    @Test
    fun `a new index replaces the old one and obeys the address rules`() {
        val state =
            Rotation.withIndex(
                BackgroundState(),
                source,
                listOf("https://example.org/a.jpg", "http://example.org/b.jpg", "https://example.org/a.jpg"),
                now = hour,
            )
        assertEquals(listOf("https://example.org/a.jpg"), state.index?.urls)
        assertEquals(hour, state.index?.fetchedAt)
    }
}
