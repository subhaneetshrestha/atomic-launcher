package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InfoLineBindingsTest {
    @Test
    fun `each info line ships with a tap action and nothing on long press`() {
        val info = Settings().homeInfo

        assertEquals(Action.Builtin(BuiltinId.ALARMS), info.tap(InfoLineId.CLOCK))
        assertEquals(Action.Builtin(BuiltinId.CALENDAR_TODAY), info.tap(InfoLineId.DATE))
        assertEquals(Action.Builtin(BuiltinId.BATTERY_SETTINGS), info.tap(InfoLineId.BATTERY))
        assertEquals(Action.None, info.tap(InfoLineId.SCREEN_TIME), "screen time arrives with its own grant flow later")
        for (id in InfoLineId.entries) assertEquals(Action.None, info.longPress(id), id.key)
    }

    @Test
    fun `a stored binding wins, an explicit none unbinds, and the other binding keeps its default`() {
        val text = """
            { "homeInfo": {
                "clock": { "enabled": true, "onTap": { "type": "none" } },
                "date": { "enabled": true, "onLongPress": { "type": "builtin", "id": "settings" } } } }
        """

        val info = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text)).value.homeInfo

        assertEquals(Action.None, info.tap(InfoLineId.CLOCK), "an explicit none unbinds the tap")
        assertEquals(Action.Builtin(BuiltinId.SETTINGS), info.longPress(InfoLineId.DATE))
        assertEquals(
            Action.Builtin(BuiltinId.CALENDAR_TODAY),
            info.tap(InfoLineId.DATE),
            "changing the long press leaves the tap alone",
        )
        assertEquals(
            Action.Builtin(BuiltinId.BATTERY_SETTINGS),
            info.tap(InfoLineId.BATTERY),
            "a line the document does not mention is untouched",
        )
    }

    @Test
    fun `a line the document mentions without enabled is off`() {
        val text = """{ "homeInfo": { "battery": { "format": "{level}" } } }"""

        val info = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text)).value.homeInfo

        assertEquals(false, info.line(InfoLineId.BATTERY).enabled, "what the file states for a line is what you get")
        assertEquals(true, info.line(InfoLineId.CLOCK).enabled)
    }
}
