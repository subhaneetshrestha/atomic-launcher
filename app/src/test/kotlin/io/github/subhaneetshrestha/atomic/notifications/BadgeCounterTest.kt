package io.github.subhaneetshrestha.atomic.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rules for what counts towards the number beside an app's name. They follow the ones the
 * stock launcher uses, so a badge here means the same thing it would there.
 */
class BadgeCounterTest {
    private fun note(
        pkg: String = "com.chat",
        user: Long = 0,
        key: String = "n",
        groupSummary: Boolean = false,
        canShowBadge: Boolean = true,
        channelId: String = "messages",
        ongoing: Boolean = false,
        foregroundService: Boolean = false,
        category: String? = null,
        suspended: Boolean = false,
        hasTitleOrText: Boolean = true,
        number: Int = 0,
    ) = NotificationFacts(
        key = key,
        packageName = pkg,
        userSerial = user,
        isGroupSummary = groupSummary,
        canShowBadge = canShowBadge,
        channelId = channelId,
        isOngoing = ongoing,
        isForegroundService = foregroundService,
        category = category,
        isSuspended = suspended,
        hasTitleOrText = hasTitleOrText,
        number = number,
    )

    private fun counts(
        vararg notes: NotificationFacts,
        includeOngoing: Boolean = false,
    ) = BadgeCounter(includeOngoing).counts(notes.toList())

    private val chat = BadgeKey("com.chat", 0)

    @Test
    fun `each notification counts once, and they add up per app`() {
        assertEquals(mapOf(chat to 1), counts(note()))
        assertEquals(mapOf(chat to 3), counts(note(key = "a"), note(key = "b"), note(key = "c")))
        assertEquals(
            mapOf(chat to 1, BadgeKey("com.mail", 0) to 2),
            counts(note(), note(pkg = "com.mail", key = "a"), note(pkg = "com.mail", key = "b")),
        )
    }

    @Test
    fun `the same app in another profile is counted apart`() {
        assertEquals(
            mapOf(chat to 1, BadgeKey("com.chat", 10) to 1),
            counts(note(), note(user = 10, key = "b")),
        )
    }

    @Test
    fun `an app that says how many there are is believed, within reason`() {
        assertEquals(mapOf(chat to 7), counts(note(number = 7)))
        assertEquals(mapOf(chat to 1), counts(note(number = 0)), "nought still means one notification")
        assertEquals(mapOf(chat to 1), counts(note(number = -3)), "and so does nonsense")
        assertEquals(mapOf(chat to 8), counts(note(key = "a", number = 7), note(key = "b")))
        assertEquals(
            mapOf(chat to 999),
            counts(note(key = "a", number = 900), note(key = "b", number = 900)),
            "the badge stops counting where it stops fitting",
        )
    }

    @Test
    fun `the summary that stands for a group is not counted with the notifications under it`() {
        assertEquals(
            mapOf(chat to 2),
            counts(note(key = "s", groupSummary = true), note(key = "a"), note(key = "b")),
        )
    }

    @Test
    fun `what the user or the system has ruled out does not count`() {
        assertEquals(emptyMap(), counts(note(canShowBadge = false)), "badges turned off for that channel")
        assertEquals(emptyMap(), counts(note(suspended = true)), "the app is suspended")
        assertEquals(emptyMap(), counts(note(hasTitleOrText = false)), "nothing to show")
    }

    @Test
    fun `things that sit in the shade rather than arriving do not count`() {
        assertEquals(emptyMap(), counts(note(ongoing = true)), "a download in progress is not news")
        assertEquals(emptyMap(), counts(note(foregroundService = true)))
        assertEquals(emptyMap(), counts(note(category = "transport")), "nor is whatever is playing")
        assertEquals(
            mapOf(chat to 3),
            counts(
                note(key = "a", ongoing = true),
                note(key = "b", foregroundService = true),
                note(key = "c", category = "transport"),
                includeOngoing = true,
            ),
            "unless the user asked for them",
        )
    }

    @Test
    fun `an old app's ongoing notification on the catch-all channel never counts`() {
        assertEquals(
            emptyMap(),
            counts(note(channelId = "miscellaneous", ongoing = true), includeOngoing = true),
            "even when ongoing ones are wanted: this channel is where every unsorted notification lands",
        )
        assertEquals(mapOf(chat to 1), counts(note(channelId = "miscellaneous")), "a real one there still counts")
    }
}
