package io.github.subhaneetshrestha.atomic.core.theme

/**
 * Which links a binding may open. A link must name a scheme, and not one that aims at a component
 * or at our own files: `intent:` and `android-app:` can start arbitrary components with arbitrary
 * extras, and `file:`/`content:` could hand a viewer something private.
 */
object LinkRules {
    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

    private val BLOCKED = setOf("intent", "android-app", "file", "content", "javascript", "data")

    fun problemWith(url: String): String? {
        val scheme =
            SCHEME
                .find(url)
                ?.groupValues
                ?.get(1)
                ?.lowercase()
                ?: return "'$url' does not start with a scheme"
        return if (scheme in BLOCKED) "a $scheme: link is not safe to open from a binding" else null
    }

    fun isOpenable(url: String): Boolean = problemWith(url) == null
}
