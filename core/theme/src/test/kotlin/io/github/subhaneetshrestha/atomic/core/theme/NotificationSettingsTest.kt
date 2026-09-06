package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class NotificationSettingsTest {
    @Test
    fun `badges are off until the user asks for them`() {
        val notifications = Settings().notifications

        assertFalse(notifications.enabled, "reading notifications is never on by default")
        assertFalse(notifications.includeOngoing, "a playing track is not news")
        assertEquals(emptyList(), notifications.perAppDisabled)
    }

    @Test
    fun `an app the user silenced is stored by package, and the list is kept clean`() {
        val text = """
            {
              "notifications": {
                "enabled": true,
                "includeOngoing": true,
                "perAppDisabled": [
                  { "pkg": "com.example.chat" },
                  { "pkg": "com.example.chat" },
                  { "pkg": "not a package!" },
                  { "pkg": "com.example.mail", "user": 10 }
                ]
              }
            }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))
        val notifications = result.value.notifications

        assertEquals(
            listOf(PackageRef("com.example.chat"), PackageRef("com.example.mail", user = 10)),
            notifications.perAppDisabled,
            "the repeat and the nonsense are dropped; the work-profile app is its own entry",
        )
        assertEquals(
            listOf("notifications.perAppDisabled[1]", "notifications.perAppDisabled[2]"),
            result.warnings.map { it.path },
        )
    }

    @Test
    fun `badges are asked for per app and read back per app`() {
        val silenced = PackageRef("com.example.chat")
        val settings =
            Settings().copy(
                notifications = NotificationConfig(enabled = true, perAppDisabled = listOf(silenced)),
            )

        assertFalse(settings.notifications.shows(silenced), "the user turned this one off")
        assertEquals(true, settings.notifications.shows(PackageRef("com.example.mail")))

        val off = settings.copy(notifications = settings.notifications.copy(enabled = false))
        assertFalse(
            off.notifications.shows(PackageRef("com.example.mail")),
            "no badges at all while the feature is off",
        )
    }
}
