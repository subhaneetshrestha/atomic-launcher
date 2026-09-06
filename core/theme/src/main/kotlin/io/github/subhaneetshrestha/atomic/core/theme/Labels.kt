package io.github.subhaneetshrestha.atomic.core.theme

/** Rules for user-entered labels (renames and per-row overrides). */
object Labels {
    const val MAX_LENGTH = 40

    /** Control characters removed, whitespace trimmed, cut at [MAX_LENGTH]; null when nothing readable is left. */
    fun clean(label: String): String? {
        val cleaned = label.filterNot { it.isISOControl() }.trim()
        return when {
            cleaned.isEmpty() -> null
            cleaned.length > MAX_LENGTH -> cleaned.take(MAX_LENGTH)
            else -> cleaned
        }
    }
}
