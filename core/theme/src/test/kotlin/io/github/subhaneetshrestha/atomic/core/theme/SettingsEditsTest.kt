package io.github.subhaneetshrestha.atomic.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsEditsTest {
    private val a = AppRef("com.a/.Main")
    private val b = AppRef("com.b/.Main")

    @Test
    fun `adding to home appends once and stops at the cap`() {
        var s = SettingsEdits.addToHome(Settings(), a)
        s = SettingsEdits.addToHome(s, b)
        s = SettingsEdits.addToHome(s, a)

        assertEquals(listOf(HomeEntry("com.a/.Main"), HomeEntry("com.b/.Main")), s.home.entries)

        val full =
            (1..HomeLimits.MAX_ROWS).fold(
                Settings(),
            ) { acc, i -> SettingsEdits.addToHome(acc, AppRef("com.x$i/.Main")) }
        assertEquals(HomeLimits.MAX_ROWS, full.home.entries.size)
        assertEquals(full, SettingsEdits.addToHome(full, AppRef("com.overflow/.Main")), "the 17th app is refused")
    }

    @Test
    fun `removing and reordering home entries`() {
        val c = AppRef("com.c/.Main")
        val three = listOf(a, b, c).fold(Settings()) { acc, ref -> SettingsEdits.addToHome(acc, ref) }

        val without = SettingsEdits.removeFromHome(three, b)
        assertEquals(listOf("com.a/.Main", "com.c/.Main"), without.home.entries.map { it.component })
        assertEquals(without, SettingsEdits.removeFromHome(without, b), "removing an absent entry changes nothing")

        val moved = SettingsEdits.moveHomeEntry(three, from = 2, to = 0)
        assertEquals(listOf("com.c/.Main", "com.a/.Main", "com.b/.Main"), moved.home.entries.map { it.component })
        assertEquals(three, SettingsEdits.moveHomeEntry(three, from = 0, to = 0), "moving onto itself changes nothing")
        assertEquals(three, SettingsEdits.moveHomeEntry(three, from = 5, to = 0), "out-of-range positions are ignored")
        assertEquals(three, SettingsEdits.moveHomeEntry(three, from = 0, to = -1), "out-of-range positions are ignored")
    }

    @Test
    fun `renaming stores a cleaned label and a blank label removes the rename`() {
        val renamed = SettingsEdits.rename(Settings(), a, "  Mail\u0007 ")
        assertEquals(listOf(Rename("com.a/.Main", label = "Mail")), renamed.renames)
        assertEquals("Mail", SettingsEdits.labelOverride(renamed, a))

        val again = SettingsEdits.rename(renamed, a, "x".repeat(50))
        assertEquals(1, again.renames.size, "a second rename replaces the first")
        assertEquals(
            40,
            again.renames
                .single()
                .label.length,
            "labels are cut at 40 characters",
        )

        val cleared = SettingsEdits.rename(again, a, "   ")
        assertEquals(emptyList(), cleared.renames)
        assertEquals(null, SettingsEdits.labelOverride(cleared, a))
    }

    @Test
    fun `hiding is a toggle and hidden apps may stay on the home list`() {
        val onHome = SettingsEdits.addToHome(Settings(), a)

        val hidden = SettingsEdits.setHidden(onHome, a, true)
        assertTrue(SettingsEdits.isHidden(hidden, a))
        assertEquals(hidden, SettingsEdits.setHidden(hidden, a, true), "hiding twice changes nothing")
        assertEquals(onHome.home, hidden.home, "hiding does not touch the home list")

        val shown = SettingsEdits.setHidden(hidden, a, false)
        assertFalse(SettingsEdits.isHidden(shown, a))
        assertEquals(onHome, shown)
    }

    @Test
    fun `an unconfigured home list is materialised from the visible rows before the first edit`() {
        val visible = listOf(a, b, AppRef("com.c/.Main"))

        val explicit = SettingsEdits.materializeHome(Settings(), visible)
        assertEquals(visible.map { it.component }, explicit.home.entries.map { it.component })

        val configured = SettingsEdits.addToHome(Settings(), a)
        assertEquals(
            configured,
            SettingsEdits.materializeHome(configured, visible),
            "an already configured list is left alone",
        )
    }

    @Test
    fun `binding a surface stores it, unbinding stores an explicit nothing, and clearing restores the shipped one`() {
        val doubleTap = BindingSurface.Gesture(GestureId.DOUBLE_TAP)
        val swipeLeft = BindingSurface.Gesture(GestureId.SWIPE_LEFT)

        val bound = SettingsEdits.bind(Settings(), doubleTap, Action.Builtin(BuiltinId.LOCK_SCREEN))
        assertEquals(Action.Builtin(BuiltinId.LOCK_SCREEN), bound.binding(doubleTap))
        assertEquals(
            bound,
            SettingsEdits.bind(bound, doubleTap, Action.Builtin(BuiltinId.LOCK_SCREEN)),
            "rebinding the same action changes nothing",
        )

        val unbound = SettingsEdits.bind(bound, swipeLeft, Action.None)
        assertEquals(
            Action.None,
            unbound.gestures.bindings[GestureId.SWIPE_LEFT.key],
            "written down, so the shipped default cannot come back",
        )
        assertEquals(Action.None, unbound.binding(swipeLeft))

        val cleared = SettingsEdits.bind(unbound, swipeLeft, null)
        assertEquals(
            Action.Builtin(BuiltinId.CAMERA),
            cleared.binding(swipeLeft),
            "clearing falls back to what the launcher ships",
        )
        assertEquals(false, GestureId.SWIPE_LEFT.key in cleared.gestures.bindings.keys)
    }

    @Test
    fun `the two surfaces of an info line are bound independently`() {
        val tap = BindingSurface.InfoTap(InfoLineId.CLOCK)
        val hold = BindingSurface.InfoLongPress(InfoLineId.CLOCK)

        val tapped = SettingsEdits.bind(Settings(), tap, Action.None)
        assertEquals(Action.None, tapped.binding(tap))
        assertEquals(Action.None, tapped.binding(hold), "nothing is bound to a hold by default")

        val held = SettingsEdits.bind(tapped, hold, Action.Builtin(BuiltinId.SETTINGS))
        assertEquals(Action.Builtin(BuiltinId.SETTINGS), held.binding(hold))
        assertEquals(Action.None, held.binding(tap), "the tap the user unbound stays unbound")
        assertEquals(Action.Builtin(BuiltinId.ALARMS), SettingsEdits.bind(held, tap, null).binding(tap))
        assertEquals(
            Action.Builtin(BuiltinId.CALENDAR_TODAY),
            held.binding(BindingSurface.InfoTap(InfoLineId.DATE)),
            "another line is untouched",
        )
    }

    @Test
    fun `every surface has a stable key for the settings list`() {
        assertEquals("gesture:swipe_up", BindingSurface.Gesture(GestureId.SWIPE_UP).key)
        assertEquals("clock:tap", BindingSurface.InfoTap(InfoLineId.CLOCK).key)
        assertEquals("battery:hold", BindingSurface.InfoLongPress(InfoLineId.BATTERY).key)
        assertEquals(
            BindingSurface.all.size,
            BindingSurface.all
                .map { it.key }
                .toSet()
                .size,
            "keys are unique",
        )
        assertEquals(GestureId.entries.size + InfoLineId.entries.size * 2, BindingSurface.all.size)
    }
}
