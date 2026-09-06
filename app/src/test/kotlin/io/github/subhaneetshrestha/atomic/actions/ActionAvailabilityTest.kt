package io.github.subhaneetshrestha.atomic.actions

import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionAvailabilityTest {
    private val env = FakeActionEnvironment()

    private fun of(action: Action) = ActionAvailability(env).of(action)

    private fun of(id: BuiltinId) = of(Action.Builtin(id))

    @Test
    fun `an action the running Android is too old for is unsupported, and fine on a newer one`() {
        env.sdkInt = 28

        assertEquals(Availability.Unsupported(UnsupportedReason.NEEDS_NEWER_ANDROID), of(BuiltinId.PANEL_INTERNET))

        env.sdkInt = 29

        assertEquals(Availability.Available, of(BuiltinId.PANEL_INTERNET), "Settings panels arrived in Android 10")
    }

    @Test
    fun `the torch is only offered on a device that has one`() {
        env.hasTorch = false
        assertEquals(Availability.Unsupported(UnsupportedReason.NO_HARDWARE), of(BuiltinId.FLASHLIGHT_TOGGLE))

        env.hasTorch = true
        assertEquals(Availability.Available, of(BuiltinId.FLASHLIGHT_TOGGLE))
    }

    @Test
    fun `a launcher surface this version has not built yet is unsupported`() {
        env.unbuilt += BuiltinId.OPEN_SEARCH

        assertEquals(Availability.Unsupported(UnsupportedReason.NOT_IN_THIS_VERSION), of(BuiltinId.OPEN_SEARCH))
        assertEquals(Availability.Available, of(BuiltinId.LAUNCHER_SETTINGS), "the ones it has built are fine")
    }

    @Test
    fun `an action written by a newer version cannot be carried out, and doing nothing always can`() {
        assertEquals(
            Availability.Unsupported(UnsupportedReason.UNKNOWN_ACTION),
            of(Action.Unknown(JsonPrimitive("teleport"))),
        )
        assertEquals(Availability.Available, of(Action.None))
    }

    @Test
    fun `system actions wait for the service the user has to allow`() {
        assertEquals(Availability.NeedsGrant(ActionGrant.ACCESSIBILITY), of(BuiltinId.QUICK_SETTINGS))
        assertEquals(Availability.NeedsGrant(ActionGrant.ACCESSIBILITY), of(BuiltinId.RECENTS))
        assertEquals(Availability.NeedsGrant(ActionGrant.ACCESSIBILITY), of(BuiltinId.NOTIFICATION_SHADE))

        env.accessibilityEnabled = true

        assertEquals(Availability.Available, of(BuiltinId.QUICK_SETTINGS))
        assertEquals(Availability.Available, of(BuiltinId.RECENTS))
    }

    @Test
    fun `locking the screen takes whichever of its two routes is open`() {
        env.sdkInt = 26
        env.deviceAdminActive = true
        assertEquals(Availability.Available, of(BuiltinId.LOCK_SCREEN), "device admin locks on any Android we support")

        env.deviceAdminActive = false
        env.accessibilityEnabled = true
        assertEquals(
            Availability.NeedsGrant(ActionGrant.DEVICE_ADMIN),
            of(BuiltinId.LOCK_SCREEN),
            "the accessibility lock only arrived in Android 9, so offer the other route",
        )

        env.sdkInt = 28
        assertEquals(Availability.Available, of(BuiltinId.LOCK_SCREEN))

        env.accessibilityEnabled = false
        assertEquals(Availability.NeedsGrant(ActionGrant.ACCESSIBILITY), of(BuiltinId.LOCK_SCREEN))
    }

    @Test
    fun `a shortcut can only be read while we are the home app`() {
        env.isDefaultLauncher = false
        assertEquals(Availability.NotDefaultLauncher, of(Action.Shortcut("com.a", "compose")))

        env.isDefaultLauncher = true
        assertEquals(Availability.Available, of(Action.Shortcut("com.a", "compose")))
        assertEquals(Availability.Missing, of(Action.Shortcut("com.gone", "compose")))
    }

    @Test
    fun `an action pointing at something uninstalled says so`() {
        assertEquals(Availability.Available, of(Action.OpenApp("com.a/com.a.Main")))
        assertEquals(Availability.Missing, of(Action.OpenApp("com.gone/com.gone.Main")))
        assertEquals(Availability.Missing, of(Action.AppInfo("com.gone/com.gone.Main")))
        assertEquals(Availability.Available, of(Action.Uninstall("com.a")))
        assertEquals(Availability.Missing, of(Action.Uninstall("com.gone")))
    }

    @Test
    fun `a link is always offered, and an intent nothing answered is not`() {
        assertEquals(Availability.Available, of(Action.OpenUrl("https://example.org")))

        env.handlerless += BuiltinId.TIMERS

        assertEquals(Availability.NoHandler, of(BuiltinId.TIMERS), "nothing on this device answered it")
        assertEquals(Availability.Available, of(BuiltinId.ALARMS))
    }
}

/** Every probe the decision needs, answered from plain fields. */
class FakeActionEnvironment(
    override var sdkInt: Int = 36,
    override var hasTorch: Boolean = true,
    override var isDefaultLauncher: Boolean = true,
    override var accessibilityEnabled: Boolean = false,
    override var deviceAdminActive: Boolean = false,
) : ActionEnvironment {
    val installed = mutableSetOf("com.a/com.a.Main")
    val unbuilt = mutableSetOf<BuiltinId>()
    val handlerless = mutableSetOf<BuiltinId>()

    override fun appExists(
        component: String,
        user: Long,
    ): Boolean = component in installed

    override fun packageExists(
        pkg: String,
        user: Long,
    ): Boolean = installed.any { it.substringBefore('/') == pkg }

    override fun isBuilt(id: BuiltinId): Boolean = id !in unbuilt

    override fun hasNoHandler(id: BuiltinId): Boolean = id in handlerless
}
