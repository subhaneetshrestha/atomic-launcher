package io.github.subhaneetshrestha.atomic.core.theme

/**
 * Something an action can be bound to. One concept for the three kinds of touch the home screen
 * reacts to, so the dispatcher, the settings list and the edits all speak the same language.
 * [key] identifies a surface in logs and in the settings list; it is not stored in the document.
 */
sealed class BindingSurface {
    abstract val key: String

    data class Gesture(
        val id: GestureId,
    ) : BindingSurface() {
        override val key: String get() = "gesture:${id.key}"
    }

    data class InfoTap(
        val id: InfoLineId,
    ) : BindingSurface() {
        override val key: String get() = "${id.key}:tap"
    }

    data class InfoLongPress(
        val id: InfoLineId,
    ) : BindingSurface() {
        override val key: String get() = "${id.key}:hold"
    }

    companion object {
        /** Every surface, in the order the settings list shows them. */
        val all: List<BindingSurface> =
            GestureId.entries.map(::Gesture) +
                InfoLineId.entries.flatMap { listOf(InfoTap(it), InfoLongPress(it)) }
    }
}
