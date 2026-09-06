package io.github.subhaneetshrestha.atomic.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationAccessTest {
    private val forms =
        setOf(
            "io.github.subhaneetshrestha.atomic/io.github.subhaneetshrestha.atomic.notifications.BadgeNotificationListener",
            "io.github.subhaneetshrestha.atomic/.notifications.BadgeNotificationListener",
        )

    @Test
    fun `on Android 8 the grant is read from the list the system keeps`() {
        assertFalse(NotificationAccess.isListed(null, forms), "nothing granted yet")
        assertFalse(NotificationAccess.isListed("", forms))
        assertTrue(NotificationAccess.isListed(forms.first(), forms))
        assertTrue(NotificationAccess.isListed("com.other/.Listener:${forms.first()}", forms))
        assertTrue(NotificationAccess.isListed(" ${forms.last()} ", forms), "the short form counts, spaces and all")
    }

    @Test
    fun `an app whose name merely contains ours is not us`() {
        val impostor = "com.example.io.github.subhaneetshrestha.atomic/.notifications.BadgeNotificationListener"

        assertFalse(NotificationAccess.isListed(impostor, forms))
    }

    @Test
    fun `the newest way to the right settings screen is tried first, with older ways behind it`() {
        assertEquals(
            listOf(AccessRoute.LISTENER_DETAIL, AccessRoute.LISTENER_LIST, AccessRoute.ALL_SETTINGS),
            NotificationAccess.routes(sdkInt = 30),
            "Android 11 can open the page for one app",
        )
        assertEquals(
            listOf(AccessRoute.LISTENER_LIST, AccessRoute.ALL_SETTINGS),
            NotificationAccess.routes(sdkInt = 26),
            "before that, only the list of all listeners",
        )
    }

    @Test
    fun `a small phone running an old Android cannot do this at all`() {
        assertFalse(NotificationAccess.isSupported(sdkInt = 29, lowRam = true), "no listener is ever bound there")
        assertTrue(NotificationAccess.isSupported(sdkInt = 30, lowRam = true))
        assertTrue(NotificationAccess.isSupported(sdkInt = 26, lowRam = false))
    }
}
