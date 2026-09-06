package io.github.subhaneetshrestha.atomic.gestures

import io.github.subhaneetshrestha.atomic.actions.ActionAvailability
import io.github.subhaneetshrestha.atomic.actions.ActionGrant
import io.github.subhaneetshrestha.atomic.actions.Availability
import io.github.subhaneetshrestha.atomic.actions.FakeActionEnvironment
import io.github.subhaneetshrestha.atomic.actions.UnsupportedReason
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.core.theme.GestureId
import io.github.subhaneetshrestha.atomic.core.theme.InfoLineId
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.SettingsEdits
import io.github.subhaneetshrestha.atomic.core.theme.SwipeDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class GestureDispatcherTest {
    private val env = FakeActionEnvironment()
    private val dispatcher = GestureDispatcher(ActionAvailability(env))
    private var settings = Settings()

    private fun dispatch(event: GestureEvent) = dispatcher.dispatch(event, settings)

    private fun bind(
        surface: BindingSurface,
        action: Action?,
    ) {
        settings = SettingsEdits.bind(settings, surface, action)
    }

    @Test
    fun `a gesture runs what is bound to it`() {
        assertEquals(
            Dispatch.Run(Action.Builtin(BuiltinId.CAMERA)),
            dispatch(GestureEvent.Swipe(SwipeDirection.LEFT, long = false)),
        )
        assertEquals(Dispatch.Run(Action.Builtin(BuiltinId.LAUNCHER_SETTINGS)), dispatch(GestureEvent.LongPress))
    }

    @Test
    fun `a long swipe nobody bound falls back to the short one, so a longer flick still works`() {
        assertEquals(
            Dispatch.Run(Action.Builtin(BuiltinId.CAMERA)),
            dispatch(GestureEvent.Swipe(SwipeDirection.LEFT, long = true)),
        )

        bind(BindingSurface.Gesture(GestureId.LONG_SWIPE_LEFT), Action.OpenUrl("https://example.org"))

        assertEquals(
            Dispatch.Run(Action.OpenUrl("https://example.org")),
            dispatch(GestureEvent.Swipe(SwipeDirection.LEFT, long = true)),
            "its own binding wins once it has one",
        )
    }

    @Test
    fun `a gesture bound to nothing does nothing`() {
        assertEquals(Dispatch.Ignore, dispatch(GestureEvent.DoubleTap))

        bind(BindingSurface.Gesture(GestureId.SWIPE_LEFT), Action.None)

        assertEquals(Dispatch.Ignore, dispatch(GestureEvent.Swipe(SwipeDirection.LEFT, long = false)))
        assertEquals(
            Dispatch.Ignore,
            dispatch(GestureEvent.Swipe(SwipeDirection.LEFT, long = true)),
            "the fallback is unbound too",
        )
    }

    @Test
    fun `a gesture that needs permission offers it instead of doing nothing`() {
        assertEquals(
            Dispatch.OfferGrant(ActionGrant.ACCESSIBILITY, Action.Builtin(BuiltinId.NOTIFICATION_SHADE)),
            dispatch(GestureEvent.Swipe(SwipeDirection.DOWN, long = false)),
        )

        env.accessibilityEnabled = true

        assertEquals(
            Dispatch.Run(Action.Builtin(BuiltinId.NOTIFICATION_SHADE)),
            dispatch(GestureEvent.Swipe(SwipeDirection.DOWN, long = false)),
        )
    }

    @Test
    fun `a gesture this device cannot carry out says so rather than failing quietly`() {
        env.unbuilt += BuiltinId.OPEN_SEARCH

        assertEquals(
            Dispatch.Explain(
                Availability.Unsupported(UnsupportedReason.NOT_IN_THIS_VERSION),
                Action.Builtin(BuiltinId.OPEN_SEARCH),
            ),
            dispatch(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
        )

        bind(BindingSurface.Gesture(GestureId.SWIPE_UP), Action.OpenApp("com.gone/com.gone.Main"))

        assertEquals(
            Dispatch.Explain(Availability.Missing, Action.OpenApp("com.gone/com.gone.Main")),
            dispatch(GestureEvent.Swipe(SwipeDirection.UP, long = false)),
        )
    }

    @Test
    fun `arming a long swipe is a feeling, not an action`() {
        assertEquals(Dispatch.Ignore, dispatch(GestureEvent.LongSwipeArmed(SwipeDirection.UP)))
        assertEquals(Dispatch.Ignore, dispatch(GestureEvent.LongSwipeDisarmed(SwipeDirection.UP)))
    }

    @Test
    fun `an info line dispatches the surface it was touched on`() {
        assertEquals(
            Dispatch.Run(Action.Builtin(BuiltinId.ALARMS)),
            dispatcher.dispatch(BindingSurface.InfoTap(InfoLineId.CLOCK), settings),
        )
        assertEquals(Dispatch.Ignore, dispatcher.dispatch(BindingSurface.InfoLongPress(InfoLineId.CLOCK), settings))
    }
}
