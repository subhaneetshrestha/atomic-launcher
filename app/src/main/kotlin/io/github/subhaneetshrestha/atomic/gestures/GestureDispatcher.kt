package io.github.subhaneetshrestha.atomic.gestures

import io.github.subhaneetshrestha.atomic.actions.ActionAvailability
import io.github.subhaneetshrestha.atomic.actions.ActionGrant
import io.github.subhaneetshrestha.atomic.actions.Availability
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.GestureId
import io.github.subhaneetshrestha.atomic.core.theme.Settings

/** What the home screen should do about a gesture, once its binding and that action's state are known. */
sealed class Dispatch {
    data class Run(
        val action: Action,
    ) : Dispatch()

    /** The action would work once the user allows something; the screen offers the disclosure. */
    data class OfferGrant(
        val grant: ActionGrant,
        val action: Action,
    ) : Dispatch()

    /** It cannot work here: say why rather than appearing to be broken. */
    data class Explain(
        val availability: Availability,
        val action: Action,
    ) : Dispatch()

    data object Ignore : Dispatch()
}

/**
 * Reads the binding a touch landed on and decides what to do with it. Pure, so the awkward cases
 * (an unbound long swipe, an action that needs a grant, an app that has been uninstalled) are
 * settled in tests rather than discovered on a device.
 */
class GestureDispatcher(
    private val availability: ActionAvailability,
) {
    fun dispatch(
        event: GestureEvent,
        settings: Settings,
    ): Dispatch =
        when (event) {
            is GestureEvent.Swipe -> swipe(event, settings)

            GestureEvent.DoubleTap -> dispatch(BindingSurface.Gesture(GestureId.DOUBLE_TAP), settings)

            GestureEvent.LongPress -> dispatch(BindingSurface.Gesture(GestureId.LONG_PRESS), settings)

            // Arming is feedback under the finger, not something to carry out.
            is GestureEvent.LongSwipeArmed, is GestureEvent.LongSwipeDisarmed -> Dispatch.Ignore
        }

    fun dispatch(
        surface: BindingSurface,
        settings: Settings,
    ): Dispatch = decide(settings.binding(surface))

    /**
     * A long swipe runs its own binding, or the short swipe of the same direction when it has
     * none, so swiping further than usual never feels like nothing happened.
     */
    private fun swipe(
        event: GestureEvent.Swipe,
        settings: Settings,
    ): Dispatch {
        val short = BindingSurface.Gesture(GestureId.shortSwipe(event.direction))
        if (!event.long) return dispatch(short, settings)
        val own = settings.binding(BindingSurface.Gesture(GestureId.longSwipe(event.direction)))
        return if (own == Action.None) dispatch(short, settings) else decide(own)
    }

    private fun decide(action: Action): Dispatch =
        when (val state = availability.of(action)) {
            Availability.Available -> if (action == Action.None) Dispatch.Ignore else Dispatch.Run(action)
            is Availability.NeedsGrant -> Dispatch.OfferGrant(state.grant, action)
            else -> Dispatch.Explain(state, action)
        }
}
