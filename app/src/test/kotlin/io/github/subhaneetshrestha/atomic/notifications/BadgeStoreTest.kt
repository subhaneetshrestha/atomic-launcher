package io.github.subhaneetshrestha.atomic.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class BadgeStoreTest {
    private val clock = FakeCoalescer()
    private val store = BadgeStore(clock)
    private var told = 0
    private val chat = BadgeKey("com.chat", 0)
    private val mail = BadgeKey("com.mail", 0)

    private fun listen() = store.addListener { told++ }

    @Test
    fun `a count is there to read at once, and the screen is told a moment later`() {
        listen()

        store.replaceAll(mapOf(chat to 3))

        assertEquals(3, store.countFor("com.chat", 0), "the number is available immediately")
        assertEquals(0, told, "but nothing has been redrawn yet")

        clock.run()

        assertEquals(1, told)
        assertEquals(0, store.countFor("com.other", 0), "an app with nothing waiting has no badge")
        assertEquals(0, store.countFor("com.chat", 10), "and neither has the same app in another profile")
    }

    @Test
    fun `a burst of arrivals costs one redraw`() {
        listen()

        store.replaceAll(mapOf(chat to 1))
        store.update(chat, 2)
        store.update(mail, 1)
        store.update(chat, 3)
        clock.run()

        assertEquals(1, told, "five changes, one redraw")
        assertEquals(3, store.countFor("com.chat", 0))
        assertEquals(1, store.countFor("com.mail", 0))
    }

    @Test
    fun `nothing is redrawn when nothing changed`() {
        store.replaceAll(mapOf(chat to 2))
        clock.run()
        listen()

        store.replaceAll(mapOf(chat to 2))
        store.update(chat, 2)
        clock.run()

        assertEquals(0, told)
    }

    @Test
    fun `losing notification access empties the badges`() {
        store.replaceAll(mapOf(chat to 5))
        clock.run()
        listen()

        store.clear()
        clock.run()

        assertEquals(0, store.countFor("com.chat", 0))
        assertEquals(1, told)
    }

    @Test
    fun `an app the user turned badges off for reads as nothing, and comes back untouched`() {
        store.replaceAll(mapOf(chat to 4, mail to 2))
        clock.run()
        listen()

        store.disabled = setOf(chat)
        clock.run()

        assertEquals(0, store.countFor("com.chat", 0))
        assertEquals(2, store.countFor("com.mail", 0))
        assertEquals(1, told, "turning it off is a change the screen has to hear about")

        store.disabled = emptySet()
        clock.run()

        assertEquals(
            4,
            store.countFor("com.chat", 0),
            "the count was kept, so it does not wait for the next notification",
        )
        assertEquals(2, told)
    }

    @Test
    fun `a screen that has gone away is not told`() {
        val listener = BadgeStore.Listener { told++ }
        store.addListener(listener)
        store.removeListener(listener)

        store.replaceAll(mapOf(chat to 1))
        clock.run()

        assertEquals(0, told)
    }
}

/** Holds what was scheduled until the test says to run it, so the coalescing is visible. */
class FakeCoalescer : Coalescer {
    private var pending: (() -> Unit)? = null

    override fun post(
        delayMs: Long,
        action: () -> Unit,
    ): () -> Unit {
        pending = action
        return { pending = null }
    }

    fun run() {
        val action = pending
        pending = null
        action?.invoke()
    }
}
