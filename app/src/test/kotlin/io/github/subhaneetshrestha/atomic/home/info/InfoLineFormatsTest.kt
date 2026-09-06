package io.github.subhaneetshrestha.atomic.home.info

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class InfoLineFormatsTest {
    private val sunday = LocalDate.of(2026, 9, 6)
    private val usDefault: (Locale) -> String = { "EEEE, MMMM d" }

    @Test
    fun `the date line uses the locale's default pattern when the theme sets none`() {
        assertEquals(
            "Sunday, September 6",
            DateLineFormat.format(sunday, Locale.US, pattern = null, defaultPattern = usDefault),
        )
    }

    @Test
    fun `a custom date pattern is honoured and a broken one falls back to the default`() {
        assertEquals("6 Sep", DateLineFormat.format(sunday, Locale.US, pattern = "d MMM", defaultPattern = usDefault))
        assertEquals(
            "Sonntag, 6. September",
            DateLineFormat.format(sunday, Locale.GERMANY, pattern = "EEEE, d. MMMM", defaultPattern = usDefault),
        )
        assertEquals(
            "Sunday, September 6",
            DateLineFormat.format(sunday, Locale.US, pattern = "??{", defaultPattern = usDefault),
        )
    }

    @Test
    fun `the battery line shows the level and marks charging`() {
        assertEquals("42%", BatteryLineFormat.format(level = 42, charging = false, template = null))
        assertEquals("42% ⚡", BatteryLineFormat.format(level = 42, charging = true, template = null))
        assertEquals("42 percent", BatteryLineFormat.format(level = 42, charging = true, template = "{level} percent"))
        assertEquals(
            "charging: ⚡",
            BatteryLineFormat.format(level = 42, charging = true, template = "charging:{charging}"),
        )
        assertEquals(
            "100%",
            BatteryLineFormat.format(level = 105, charging = false, template = null),
            "levels are clamped",
        )
        assertEquals("0%", BatteryLineFormat.format(level = -1, charging = false, template = null))
    }
}
