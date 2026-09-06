package io.github.subhaneetshrestha.atomic.core.theme

/**
 * Second pass after typed decoding: values are pulled into their allowed ranges and every
 * correction is recorded with the path of the field, so an import screen can show what changed.
 */
internal class Sanitizer {
    val warnings = mutableListOf<Warning>()

    fun settings(settings: Settings): Settings =
        settings.copy(
            home =
                settings.home.copy(
                    entries = homeEntries(settings.home.entries),
                    fallbackCount = clamp(settings.home.fallbackCount, 0, HomeLimits.MAX_ROWS, "home.fallbackCount"),
                ),
            hidden = hidden(settings.hidden),
            renames = renames(settings.renames),
            homeInfo = homeInfo(settings.homeInfo),
            gestures = gestures(settings.gestures),
            theme = theme(settings.theme, prefix = "theme."),
        )

    /**
     * Gesture bindings. A key this version does not know is kept exactly as written (a newer app
     * put it there and can still run it); a binding we can run is checked and unbound if broken.
     */
    private fun gestures(config: GestureConfig): GestureConfig {
        val bindings = LinkedHashMap<String, Action>(config.bindings.size)
        for ((key, bound) in config.bindings) {
            val path = "gestures.bindings.$key"
            if (GestureId.fromKey(key) == null) {
                warnings += Warning(path, "no gesture called '$key' in this version; kept but inert")
                bindings[key] = bound
            } else {
                bindings[key] = action(bound, path)
            }
        }
        return config.copy(bindings = bindings)
    }

    private fun homeInfo(config: HomeInfoConfig): HomeInfoConfig {
        var result = config
        for (id in InfoLineId.entries) {
            val line = config.line(id)
            val tap = line.onTap?.let { action(it, "homeInfo.${id.key}.onTap") }
            val longPress = line.onLongPress?.let { action(it, "homeInfo.${id.key}.onLongPress") }
            if (tap != line.onTap || longPress != line.onLongPress) {
                result = result.withLine(id, line.copy(onTap = tap, onLongPress = longPress))
            }
        }
        return result
    }

    /** An action we cannot carry out is turned into [Action.None] rather than left to fail at the tap. */
    private fun action(
        value: Action,
        path: String,
    ): Action {
        val problem = problemWith(value) ?: return value
        warnings += Warning(path, "$problem; unbound")
        return Action.None
    }

    private fun problemWith(value: Action): String? =
        when (value) {
            Action.None, is Action.Builtin, is Action.Unknown -> {
                null
            }

            is Action.OpenApp -> {
                componentProblem(value.component, value.user)
            }

            is Action.AppInfo -> {
                componentProblem(value.component, value.user)
            }

            is Action.Shortcut -> {
                packageProblem(value.pkg, value.user) ?: "the shortcut has no id".takeIf { value.id.isBlank() }
            }

            is Action.Uninstall -> {
                packageProblem(value.pkg, value.user)
            }

            is Action.OpenUrl -> {
                urlProblem(value.url)
            }
        }

    private fun componentProblem(
        component: String,
        user: Long,
    ): String? =
        when {
            !COMPONENT.matches(component) -> "'$component' is not a component name (package/class)"
            user < 0 -> "user serial $user is negative"
            else -> null
        }

    private fun packageProblem(
        pkg: String,
        user: Long,
    ): String? =
        when {
            !PACKAGE.matches(pkg) -> "'$pkg' is not a package name"
            user < 0 -> "user serial $user is negative"
            else -> null
        }

    /**
     * A link must name a scheme, and not one that aims at a component or at our own files:
     * `intent:` and `android-app:` can start arbitrary components with arbitrary extras, and
     * `file:`/`content:` could hand a viewer something private.
     */
    private fun urlProblem(url: String): String? {
        val scheme =
            URL_SCHEME
                .find(url)
                ?.groupValues
                ?.get(1)
                ?.lowercase() ?: return "'$url' does not start with a scheme"
        return if (scheme in BLOCKED_SCHEMES) "a $scheme: link is not safe to open from a binding" else null
    }

    private fun homeEntries(entries: List<HomeEntry>): List<HomeEntry> {
        val seen = HashSet<Pair<String, Long>>()
        val kept = ArrayList<HomeEntry>()
        var overflow = 0
        entries.forEachIndexed { index, entry ->
            val path = "home.entries[$index]"
            when {
                !validRef(entry.component, entry.user, path) -> {
                    Unit
                }

                !seen.add(entry.component to entry.user) -> {
                    warnings +=
                        Warning(path, "repeats an earlier entry; dropped")
                }

                kept.size >= HomeLimits.MAX_ROWS -> {
                    overflow++
                }

                else -> {
                    kept += entry.copy(label = label(entry.label, "$path.label"))
                }
            }
        }
        if (overflow >
            0
        ) {
            warnings +=
                Warning("home.entries", "more than ${HomeLimits.MAX_ROWS} entries; the last $overflow were dropped")
        }
        return kept
    }

    private fun hidden(refs: List<AppRef>): List<AppRef> {
        val seen = HashSet<AppRef>()
        return refs.filterIndexed { index, ref ->
            val path = "hidden[$index]"
            when {
                !validRef(ref.component, ref.user, path) -> {
                    false
                }

                !seen.add(ref) -> {
                    warnings += Warning(path, "repeats an earlier entry; dropped")
                    false
                }

                else -> {
                    true
                }
            }
        }
    }

    private fun renames(renames: List<Rename>): List<Rename> {
        val seen = HashSet<Pair<String, Long>>()
        return renames.mapIndexedNotNull { index, rename ->
            val path = "renames[$index]"
            when {
                !validRef(rename.component, rename.user, path) -> {
                    null
                }

                !seen.add(rename.component to rename.user) -> {
                    warnings += Warning(path, "repeats an earlier entry; dropped")
                    null
                }

                else -> {
                    val cleaned = cleanLabel(rename.label, "$path.label")
                    if (cleaned == null) warnings += Warning(path, "blank label; rename dropped")
                    cleaned?.let { rename.copy(label = it) }
                }
            }
        }
    }

    private fun validRef(
        component: String,
        user: Long,
        path: String,
    ): Boolean =
        when {
            !COMPONENT.matches(component) -> {
                warnings += Warning(path, "'$component' is not a component name (package/class); dropped")
                false
            }

            user < 0 -> {
                warnings += Warning(path, "user serial $user is negative; dropped")
                false
            }

            else -> {
                true
            }
        }

    /** Optional label of a home entry: cleaned, or null (with a warning) when nothing readable is left. */
    private fun label(
        label: String?,
        path: String,
    ): String? {
        if (label == null) return null
        val cleaned = cleanLabel(label, path)
        if (cleaned == null) warnings += Warning(path, "label is blank; ignored")
        return cleaned
    }

    /** [Labels.clean] plus a warning for each rule that changed the text. */
    private fun cleanLabel(
        label: String,
        path: String,
    ): String? {
        if (label.any { it.isISOControl() }) warnings += Warning(path, "control characters removed")
        if (label.filterNot { it.isISOControl() }.trim().length > Labels.MAX_LENGTH) {
            warnings += Warning(path, "label longer than ${Labels.MAX_LENGTH} characters; cut")
        }
        return Labels.clean(label)
    }

    fun theme(
        theme: Theme,
        prefix: String = "",
    ): Theme {
        val typography = theme.typography
        val sizes = typography.sizes
        val layout = theme.layout
        return theme.copy(
            colors = colors(theme.colors, "${prefix}colors"),
            darkColors = theme.darkColors?.let { overrides(it, "${prefix}darkColors") },
            typography =
                typography.copy(
                    weight = snapWeight(typography.weight, "${prefix}typography.weight"),
                    sizes =
                        sizes.copy(
                            homeSp = clamp(sizes.homeSp, 10f, 64f, "${prefix}typography.sizes.homeSp"),
                            clockSp = clamp(sizes.clockSp, 12f, 140f, "${prefix}typography.sizes.clockSp"),
                            infoSp = clamp(sizes.infoSp, 8f, 40f, "${prefix}typography.sizes.infoSp"),
                        ),
                ),
            layout =
                layout.copy(
                    paddingDp =
                        layout.paddingDp.copy(
                            h = clamp(layout.paddingDp.h, 0, 200, "${prefix}layout.paddingDp.h"),
                            v = clamp(layout.paddingDp.v, 0, 200, "${prefix}layout.paddingDp.v"),
                        ),
                    rowGapDp = clamp(layout.rowGapDp, 0, 64, "${prefix}layout.rowGapDp"),
                ),
        )
    }

    private fun colors(
        colors: Colors,
        path: String,
    ): Colors {
        val defaults = Colors()
        return Colors(
            background = color(colors.background, isText = false, "$path.background") ?: defaults.background,
            text = color(colors.text, isText = true, "$path.text") ?: defaults.text,
            textSecondary = color(colors.textSecondary, isText = true, "$path.textSecondary") ?: defaults.textSecondary,
            accent = color(colors.accent, isText = false, "$path.accent") ?: defaults.accent,
        )
    }

    private fun overrides(
        overrides: ColorsOverride,
        path: String,
    ): ColorsOverride =
        ColorsOverride(
            background = overrides.background?.let { color(it, isText = false, "$path.background") },
            text = overrides.text?.let { color(it, isText = true, "$path.text") },
            textSecondary = overrides.textSecondary?.let { color(it, isText = true, "$path.textSecondary") },
            accent = overrides.accent?.let { color(it, isText = false, "$path.accent") },
        )

    private fun color(
        value: String,
        isText: Boolean,
        path: String,
    ): String? {
        val normalized = ColorValue.normalize(value, allowAuto = isText)
        if (normalized == null) {
            val forms =
                if (isText) {
                    "#RRGGBB, #AARRGGBB, @android:color/system_* or auto"
                } else {
                    "#RRGGBB, #AARRGGBB or @android:color/system_*"
                }
            warnings += Warning(path, "'$value' is not a colour; expected $forms")
        }
        return normalized
    }

    /** Font weights are meaningful in hundreds only; anything else snaps to the nearest hundred within 100..900. */
    private fun snapWeight(
        weight: Int,
        path: String,
    ): Int {
        val snapped = ((weight + 50) / 100 * 100).coerceIn(100, 900)
        if (snapped != weight) warnings += Warning(path, "$weight is not a font weight; using $snapped")
        return snapped
    }

    private fun clamp(
        value: Int,
        min: Int,
        max: Int,
        path: String,
    ): Int {
        val clamped = value.coerceIn(min, max)
        if (clamped != value) warnings += Warning(path, "$value is outside $min..$max; using $clamped")
        return clamped
    }

    private fun clamp(
        value: Float,
        min: Float,
        max: Float,
        path: String,
    ): Float {
        val clamped = value.coerceIn(min, max)
        if (clamped != value) warnings += Warning(path, "$value is outside $min..$max; using $clamped")
        return clamped
    }
}

object HomeLimits {
    const val MAX_ROWS = 16
}

/** `package/class`, the flattened ComponentName form; the class may be relative (`.Main`). */
private val COMPONENT = Regex("^[A-Za-z][\\w.]*/[\\w.$]+$")

/** A package name: no whitespace, dot-separated. */
private val PACKAGE = Regex("^[A-Za-z][\\w.]*$")

private val URL_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*):")

private val BLOCKED_SCHEMES = setOf("intent", "android-app", "file", "content", "javascript", "data")
