package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The gestures the home screen recognises. [key] is the stored form and never changes once
 * released. Not serialized directly: bindings are keyed by the string so that a gesture a newer
 * version adds survives a load and save on this one.
 */
enum class GestureId(
    val key: String,
) {
    SWIPE_UP("swipe_up"),
    SWIPE_DOWN("swipe_down"),
    SWIPE_LEFT("swipe_left"),
    SWIPE_RIGHT("swipe_right"),
    LONG_SWIPE_UP("long_swipe_up"),
    LONG_SWIPE_DOWN("long_swipe_down"),
    LONG_SWIPE_LEFT("long_swipe_left"),
    LONG_SWIPE_RIGHT("long_swipe_right"),
    DOUBLE_TAP("double_tap"),
    LONG_PRESS("long_press"),
    ;

    val isLongSwipe: Boolean get() = key.startsWith("long_swipe_")

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): GestureId? = byKey[key]

        /** The short swipe of the same direction, for a long swipe that is unbound. */
        fun shortSwipe(direction: SwipeDirection): GestureId =
            when (direction) {
                SwipeDirection.UP -> SWIPE_UP
                SwipeDirection.DOWN -> SWIPE_DOWN
                SwipeDirection.LEFT -> SWIPE_LEFT
                SwipeDirection.RIGHT -> SWIPE_RIGHT
            }

        fun longSwipe(direction: SwipeDirection): GestureId =
            when (direction) {
                SwipeDirection.UP -> LONG_SWIPE_UP
                SwipeDirection.DOWN -> LONG_SWIPE_DOWN
                SwipeDirection.LEFT -> LONG_SWIPE_LEFT
                SwipeDirection.RIGHT -> LONG_SWIPE_RIGHT
            }
    }
}

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/** Whether the launcher takes over the left and right screen edges from the system back gesture. */
@Serializable
enum class EdgeExclusion {
    @SerialName("none")
    NONE,

    @SerialName("both")
    BOTH,
}

@Serializable
data class GestureConfig(
    /** Keyed by [GestureId.key]. A key this version does not know is kept and never fires. */
    val bindings: Map<String, Action> = DEFAULT_BINDINGS,
    val haptics: Boolean = true,
    val edgeExclusion: EdgeExclusion = EdgeExclusion.NONE,
) {
    /**
     * What [id] does: the stored binding, else the shipped default, else nothing. Unbinding in the
     * UI stores [Action.None] explicitly, so a hand-edited file that mentions one gesture does not
     * silently unbind the rest.
     */
    fun binding(id: GestureId): Action = bindings[id.key] ?: DEFAULT_BINDINGS[id.key] ?: Action.None

    companion object {
        val DEFAULT_BINDINGS: Map<String, Action> =
            mapOf(
                GestureId.SWIPE_UP.key to Action.Builtin(BuiltinId.OPEN_SEARCH),
                GestureId.SWIPE_DOWN.key to Action.Builtin(BuiltinId.NOTIFICATION_SHADE),
                GestureId.SWIPE_LEFT.key to Action.Builtin(BuiltinId.CAMERA),
                GestureId.SWIPE_RIGHT.key to Action.Builtin(BuiltinId.DIALER),
                GestureId.LONG_PRESS.key to Action.Builtin(BuiltinId.LAUNCHER_SETTINGS),
            )
    }
}
