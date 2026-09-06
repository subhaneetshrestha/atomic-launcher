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
}
