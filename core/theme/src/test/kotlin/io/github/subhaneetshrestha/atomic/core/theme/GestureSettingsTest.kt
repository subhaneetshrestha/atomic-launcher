package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GestureSettingsTest {
    @Test
    fun `a fresh install carries the documented default bindings`() {
        val gestures = Settings().gestures

        assertEquals(Action.Builtin(BuiltinId.OPEN_SEARCH), gestures.binding(GestureId.SWIPE_UP))
        assertEquals(Action.Builtin(BuiltinId.NOTIFICATION_SHADE), gestures.binding(GestureId.SWIPE_DOWN))
        assertEquals(Action.Builtin(BuiltinId.CAMERA), gestures.binding(GestureId.SWIPE_LEFT))
        assertEquals(Action.Builtin(BuiltinId.DIALER), gestures.binding(GestureId.SWIPE_RIGHT))
        assertEquals(Action.Builtin(BuiltinId.LAUNCHER_SETTINGS), gestures.binding(GestureId.LONG_PRESS))
        assertEquals(Action.None, gestures.binding(GestureId.DOUBLE_TAP))
        assertEquals(Action.None, gestures.binding(GestureId.LONG_SWIPE_UP))
        assertTrue(gestures.haptics)
        assertEquals(EdgeExclusion.NONE, gestures.edgeExclusion)
    }

    @Test
    fun `a stored binding wins, an absent one keeps its default, and an explicit none unbinds`() {
        val text = """
            { "gestures": { "bindings": {
                "swipe_up": { "type": "app", "component": "com.a/.Main" },
                "swipe_left": { "type": "none" } } } }
        """

        val gestures = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text)).value.gestures

        assertEquals(Action.OpenApp("com.a/.Main"), gestures.binding(GestureId.SWIPE_UP), "the stored binding is used")
        assertEquals(Action.None, gestures.binding(GestureId.SWIPE_LEFT), "an explicit none unbinds the gesture")
        assertEquals(
            Action.Builtin(BuiltinId.DIALER),
            gestures.binding(GestureId.SWIPE_RIGHT),
            "a gesture nobody touched keeps its default",
        )
    }

    @Test
    fun `a binding for a gesture this version does not know is kept and reported`() {
        val text = """
            { "gestures": { "bindings": {
                "two_finger_swipe_up": { "type": "builtin", "id": "camera" },
                "swipe_down": { "type": "wormhole", "to": "1994" } } } }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))
        val gestures = result.value.gestures

        assertTrue("two_finger_swipe_up" in gestures.bindings.keys, "the newer gesture is not deleted")
        assertIs<Action.Unknown>(gestures.bindings.getValue("swipe_down"))
        assertEquals(listOf("gestures.bindings.two_finger_swipe_up"), result.warnings.map { it.path })
    }

    @Test
    fun `gesture ids round-trip through their stored keys`() {
        for (id in GestureId.entries) assertEquals(id, GestureId.fromKey(id.key), id.key)
        assertEquals(null, GestureId.fromKey("pinch"))
        assertEquals(
            listOf(
                "swipe_up",
                "swipe_down",
                "swipe_left",
                "swipe_right",
                "long_swipe_up",
                "long_swipe_down",
                "long_swipe_left",
                "long_swipe_right",
                "double_tap",
                "long_press",
            ),
            GestureId.entries.map { it.key },
        )
    }
}
