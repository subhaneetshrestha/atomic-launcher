package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionTest {
    private fun decode(json: String): Action = ThemeJson.decodeFromString(Action.serializer(), json)

    private fun encode(action: Action): String = ThemeJson.encodeToString(Action.serializer(), action)

    @Test
    fun `each action variant reads from its documented form`() {
        assertEquals(Action.None, decode("""{"type":"none"}"""))
        assertEquals(Action.OpenApp("com.maps/.Main"), decode("""{"type":"app","component":"com.maps/.Main"}"""))
        assertEquals(
            Action.OpenApp("com.maps/.Main", user = 11),
            decode("""{"type":"app","component":"com.maps/.Main","user":11}"""),
        )
        assertEquals(
            Action.Shortcut("com.maps", "nav_home"),
            decode("""{"type":"shortcut","pkg":"com.maps","id":"nav_home"}"""),
        )
        assertEquals(Action.OpenUrl("https://example.org"), decode("""{"type":"url","url":"https://example.org"}"""))
        assertEquals(Action.Builtin(BuiltinId.OPEN_SEARCH), decode("""{"type":"builtin","id":"open_search"}"""))
        assertEquals(Action.AppInfo("com.maps/.Main"), decode("""{"type":"app_info","component":"com.maps/.Main"}"""))
        assertEquals(Action.Uninstall("com.maps"), decode("""{"type":"uninstall","pkg":"com.maps"}"""))
    }

    @Test
    fun `an action this version does not know is kept verbatim instead of lost`() {
        val future = """{"type":"teleport","destination":"kitchen","times":3}"""

        val decoded = assertIs<Action.Unknown>(decode(future))
        val written = encode(decoded)

        assertTrue("teleport" in written && "kitchen" in written, written)
        assertEquals(decoded, decode(written), "it survives a load-and-save cycle unchanged")
        assertIs<Action.Unknown>(decode("""{"type":"builtin","id":"summon_dragon"}"""))
        assertIs<Action.Unknown>(decode("""{"type":"app"}"""))
        assertIs<Action.Unknown>(decode("""["not","an","object"]"""))
    }

    @Test
    fun `every variant survives an encode and decode cycle`() {
        val all =
            listOf(
                Action.None,
                Action.OpenApp("com.a/.Main", user = 7),
                Action.Shortcut("com.a", "compose", user = 7),
                Action.OpenUrl("https://example.org/x?y=1"),
                Action.AppInfo("com.a/.Main"),
                Action.Uninstall("com.a"),
            ) + BuiltinId.entries.map { Action.Builtin(it) }

        for (action in all) assertEquals(action, decode(encode(action)), action.toString())
    }

    @Test
    fun `builtin ids are grouped so the picker can section them`() {
        assertEquals(ActionGroup.INTENT, BuiltinId.CAMERA.group)
        assertEquals(ActionGroup.LAUNCHER, BuiltinId.OPEN_SEARCH.group)
        assertEquals(ActionGroup.DEVICE, BuiltinId.FLASHLIGHT_TOGGLE.group)
        assertEquals(ActionGroup.SYSTEM, BuiltinId.LOCK_SCREEN.group)
        assertTrue(BuiltinId.entries.size >= 40, "the whole catalogue is present: ${BuiltinId.entries.size}")
    }

    @Test
    fun `an action whose target is malformed is unbound and reported`() {
        val text = """
            { "gestures": { "bindings": {
                "swipe_up": { "type": "app", "component": "not a component" },
                "swipe_down": { "type": "url", "url": "intent://scan/#Intent;scheme=zxing;end" },
                "swipe_left": { "type": "url", "url": "https://ok.example/x" },
                "swipe_right": { "type": "shortcut", "pkg": "com a", "id": "compose" },
                "double_tap": { "type": "app", "component": "com.a/.Main", "user": -2 } } },
              "homeInfo": { "clock": { "enabled": true, "onTap": { "type": "uninstall", "pkg": "" } } } }
        """

        val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))
        val gestures = result.value.gestures

        assertEquals(Action.None, gestures.binding(GestureId.SWIPE_UP), "unbound, not left as a broken action")
        assertEquals(
            Action.None,
            gestures.binding(GestureId.SWIPE_DOWN),
            "intent: URIs can target components and are refused",
        )
        assertEquals(Action.None, gestures.binding(GestureId.SWIPE_RIGHT))
        assertEquals(Action.None, gestures.binding(GestureId.DOUBLE_TAP))
        assertEquals(
            Action.OpenUrl("https://ok.example/x"),
            gestures.binding(GestureId.SWIPE_LEFT),
            "a good one is left alone",
        )
        assertEquals(Action.None, result.value.homeInfo.tap(InfoLineId.CLOCK))
        assertEquals(
            listOf(
                "gestures.bindings.double_tap",
                "gestures.bindings.swipe_down",
                "gestures.bindings.swipe_right",
                "gestures.bindings.swipe_up",
                "homeInfo.clock.onTap",
            ),
            result.warnings.map { it.path }.sorted(),
        )
    }

    @Test
    fun `custom app schemes stay bindable`() {
        for (url in listOf("spotify:playlist:37i9", "mailto:someone@example.org", "tel:+9771234567", "geo:27.7,85.3")) {
            val text = """{ "gestures": { "bindings": { "swipe_up": { "type": "url", "url": "$url" } } } }"""
            val result = assertIs<DecodeResult.Ok<Settings>>(SettingsCodec.decodeSettings(text))
            assertEquals(Action.OpenUrl(url), result.value.gestures.binding(GestureId.SWIPE_UP), url)
            assertEquals(emptyList(), result.warnings, url)
        }
    }
}
