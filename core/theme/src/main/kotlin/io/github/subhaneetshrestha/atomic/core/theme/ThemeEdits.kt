package io.github.subhaneetshrestha.atomic.core.theme

/**
 * Editing the theme that is showing. Every change goes through the same pass a theme read from a
 * document does, so a size typed with one digit too many is pulled into range as it is typed
 * rather than the next time the launcher starts.
 */
object ThemeEdits {
    const val CUSTOM_ID = "custom"

    /** [theme] with every value pulled into its allowed range, and what had to be corrected. */
    fun clean(theme: Theme): Pair<Theme, List<Warning>> {
        val sanitizer = Sanitizer()
        val cleaned = sanitizer.theme(theme)
        return cleaned to sanitizer.warnings.toList()
    }

    /**
     * Applies an edit. Changing anything about a built-in makes the theme the user's own: the
     * picker would otherwise go on claiming that Ink is showing when it no longer is.
     */
    fun edit(
        theme: Theme,
        transform: (Theme) -> Theme,
    ): Theme {
        val (edited, _) = clean(transform(theme))
        if (edited == theme) return theme
        val builtin = BuiltinThemes.byId(edited.meta.id) ?: return edited
        if (edited == builtin) return edited
        return edited.copy(meta = edited.meta.copy(id = CUSTOM_ID))
    }

    /** The built-in this theme is, or null when it is the user's own. */
    fun builtinBehind(theme: Theme): Theme? = BuiltinThemes.byId(theme.meta.id)?.takeIf { it == theme }
}
