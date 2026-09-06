package io.github.subhaneetshrestha.atomic.home.info

/** Text of the battery line from a template with `{level}` and `{charging}` tokens. */
object BatteryLineFormat {
    const val DEFAULT_TEMPLATE = "{level}%{charging}"
    const val CHARGING_MARK = " ⚡"

    fun format(
        level: Int,
        charging: Boolean,
        template: String?,
    ): String {
        val text = template?.takeIf { it.isNotBlank() } ?: DEFAULT_TEMPLATE
        return text
            .replace("{level}", level.coerceIn(0, 100).toString())
            .replace("{charging}", if (charging) CHARGING_MARK else "")
    }
}
