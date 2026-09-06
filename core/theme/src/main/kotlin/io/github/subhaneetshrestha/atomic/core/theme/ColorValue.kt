package io.github.subhaneetshrestha.atomic.core.theme

/**
 * Grammar of colour strings in themes: `#AARRGGBB`, `#RRGGBB`, an `@android:color/system_*`
 * Material You token, or `auto` (text roles only: black or white chosen from the background).
 */
object ColorValue {
    const val AUTO = "auto"

    private val HEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val TOKEN =
        Regex("^@android:color/system_(accent[123]|neutral[12])_(0|10|50|100|200|300|400|500|600|700|800|900|1000)$")

    /** Canonical form (`#AARRGGBB` upper-case, token verbatim, or `auto`), or null when [value] is not a colour. */
    fun normalize(
        value: String,
        allowAuto: Boolean,
    ): String? {
        val v = value.trim()
        return when {
            v == AUTO -> if (allowAuto) AUTO else null
            HEX.matches(v) -> if (v.length == 7) "#FF" + v.substring(1).uppercase() else v.uppercase()
            TOKEN.matches(v) -> v
            else -> null
        }
    }

    fun isToken(value: String): Boolean = TOKEN.matches(value)

    fun isHex(value: String): Boolean = HEX.matches(value)
}
