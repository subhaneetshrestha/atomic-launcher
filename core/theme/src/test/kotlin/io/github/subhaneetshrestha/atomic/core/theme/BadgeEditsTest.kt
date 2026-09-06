package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BadgeEditsTest {
    private val chat = PackageRef("com.example.chat")

    @Test
    fun `turning badges off for one app leaves the others alone`() {
        val silenced = SettingsEdits.setBadges(Settings(), chat, shown = false)

        assertEquals(listOf(chat), silenced.notifications.perAppDisabled)
        assertFalse(silenced.notifications.shows(chat), "the feature being off is a separate question")

        val again = SettingsEdits.setBadges(silenced, chat, shown = false)
        assertEquals(listOf(chat), again.notifications.perAppDisabled, "asking twice changes nothing")

        val restored = SettingsEdits.setBadges(again, chat, shown = true)
        assertEquals(emptyList(), restored.notifications.perAppDisabled)
    }

    @Test
    fun `the moment access was granted is written down`() {
        val before = Settings()
        assertEquals(emptyMap(), before.consents)

        val after = SettingsEdits.recordConsent(before, ConsentKind.NOTIFICATION_ACCESS, atMillis = 1_757_000_000_000)

        assertEquals(mapOf("notification_access" to 1_757_000_000_000L), after.consents)

        val later = SettingsEdits.recordConsent(after, ConsentKind.NOTIFICATION_ACCESS, atMillis = 1_757_000_009_999)
        assertEquals(
            1_757_000_000_000L,
            later.consents["notification_access"],
            "the first time is the one that matters; asking again does not rewrite it",
        )
    }

    @Test
    fun `a consent written by a newer version is kept as it is`() {
        val text = """{ "consents": { "accessibility": 123, "notification_access": 456 } }"""

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))

        assertEquals(mapOf("accessibility" to 123L, "notification_access" to 456L), result.value.consents)
        assertTrue(result.warnings.isEmpty(), "a kind this version does not know is not a mistake")
    }
}
