package io.github.subhaneetshrestha.atomic.system

import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which privileged actions this Android can be asked for, and what it calls them. */
class SystemActionsTest {
    @Test
    fun `every system action has a global action behind it`() {
        for (id in BuiltinId.entries.filter { it.group == ActionGroup.SYSTEM }) {
            assertTrue(SystemActions.globalAction(id) != null, "$id has nothing to perform")
        }
        assertNull(SystemActions.globalAction(BuiltinId.OPEN_SEARCH), "a launcher surface is not a global action")
    }

    @Test
    fun `locking and screenshots arrived in Android 9`() {
        val old = SystemActions.supported(26)
        assertTrue(BuiltinId.NOTIFICATION_SHADE in old)
        assertTrue(BuiltinId.RECENTS in old)
        assertTrue(BuiltinId.POWER_MENU in old)
        assertTrue(BuiltinId.LOCK_SCREEN !in old, "an accessibility service could not lock the screen before 9")
        assertTrue(BuiltinId.SCREENSHOT !in old)
        val recent = SystemActions.supported(28)
        assertTrue(BuiltinId.LOCK_SCREEN in recent)
        assertTrue(BuiltinId.SCREENSHOT in recent)
        assertEquals(6, recent.size)
    }

    @Test
    fun `an instruction the user has moved on from is dropped`() {
        val now = 10_000L
        assertTrue(SystemActions.isFresh(now, now))
        assertTrue(SystemActions.isFresh(now - SystemActions.ISSUE_WINDOW_MS, now))
        assertTrue(!SystemActions.isFresh(now - SystemActions.ISSUE_WINDOW_MS - 1, now))
        assertTrue(!SystemActions.isFresh(now + 1, now), "an instruction from the future is not one either")
    }
}
