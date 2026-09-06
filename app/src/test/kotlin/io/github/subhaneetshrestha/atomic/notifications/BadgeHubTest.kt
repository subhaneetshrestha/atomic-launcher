package io.github.subhaneetshrestha.atomic.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the listener service does with what Android hands it, kept apart from Android so the
 * awkward parts (a setting changing with no notification in sight, a channel being silenced
 * while notifications are already showing) can be tested without a phone.
 */
class BadgeHubTest {
    private val clock = FakeCoalescer()
    private val store = BadgeStore(clock)
    private val hub = BadgeHub(store)
    private var told = 0

    init {
        store.addListener { told++ }
    }

    private fun note(
        key: String,
        pkg: String = "com.chat",
        canShowBadge: Boolean = true,
        ongoing: Boolean = false,
        number: Int = 0,
    ) = NotificationFacts(
        key = key,
        packageName = pkg,
        userSerial = 0,
        isGroupSummary = false,
        canShowBadge = canShowBadge,
        channelId = "messages",
        isOngoing = ongoing,
        isForegroundService = false,
        category = null,
        isSuspended = false,
        hasTitleOrText = true,
        number = number,
    )

    @Test
    fun `everything already showing when access is granted becomes counts at once`() {
        hub.replaceAll(listOf(note("a"), note("b"), note("c", pkg = "com.mail", number = 4)))
        clock.run()

        assertEquals(2, store.countFor("com.chat", 0))
        assertEquals(4, store.countFor("com.mail", 0))
        assertEquals(1, told, "one redraw for the lot")
    }

    @Test
    fun `one arrival changes one app`() {
        hub.replaceAll(listOf(note("a"), note("c", pkg = "com.mail")))
        clock.run()
        told = 0

        hub.posted(note("b"))
        clock.run()

        assertEquals(2, store.countFor("com.chat", 0))
        assertEquals(1, store.countFor("com.mail", 0), "untouched")
        assertEquals(1, told)
    }

    @Test
    fun `dismissing the last notification takes the badge away`() {
        hub.replaceAll(listOf(note("a"), note("b")))
        clock.run()

        hub.removed("a")
        clock.run()
        assertEquals(1, store.countFor("com.chat", 0))

        hub.removed("b")
        clock.run()
        assertEquals(0, store.countFor("com.chat", 0))
    }

    @Test
    fun `turning on ongoing notifications counts what is already there`() {
        hub.replaceAll(listOf(note("a", ongoing = true)))
        clock.run()
        assertEquals(0, store.countFor("com.chat", 0), "a playing track is not news by default")

        hub.includeOngoing = true
        clock.run()

        assertEquals(1, store.countFor("com.chat", 0), "no new notification was needed to notice")
    }

    @Test
    fun `silencing a channel stops the count without dismissing anything`() {
        hub.replaceAll(listOf(note("a"), note("b")))
        clock.run()

        hub.reranked { key -> Ranked(canShowBadge = key != "a", isSuspended = false) }
        clock.run()

        assertEquals(1, store.countFor("com.chat", 0), "only the silenced one stopped counting")
    }

    @Test
    fun `pausing an app takes its badge away, and unpausing brings it back`() {
        hub.replaceAll(listOf(note("a"), note("b")))
        clock.run()
        assertEquals(2, store.countFor("com.chat", 0))

        // A paused app's notifications are hidden rather than removed: the only word we get is a
        // ranking update, and it never arrives again while the app stays paused.
        hub.reranked { Ranked(canShowBadge = true, isSuspended = true) }
        clock.run()

        assertEquals(0, store.countFor("com.chat", 0), "a paused app has nothing to show")

        hub.reranked { Ranked(canShowBadge = true, isSuspended = false) }
        clock.run()

        assertEquals(2, store.countFor("com.chat", 0), "and the count is still there when it comes back")
    }

    @Test
    fun `losing access leaves nothing behind`() {
        hub.replaceAll(listOf(note("a")))
        clock.run()

        hub.clear()
        clock.run()

        assertEquals(0, store.countFor("com.chat", 0))
        hub.includeOngoing = true
        clock.run()
        assertEquals(0, store.countFor("com.chat", 0), "the notifications themselves are gone too")
    }
}
