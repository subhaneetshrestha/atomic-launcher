package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes the settings and theme documents. Reading never throws: the result says
 * whether the document was usable and lists every value that had to be corrected.
 */
object SettingsCodec {
    const val SCHEMA = 1
    const val THEME_SCHEMA = 1

    fun encodeSettings(settings: Settings): String = ThemeJson.encodeToString(Settings.serializer(), settings) + "\n"

    fun encodeTheme(theme: Theme): String = ThemeJson.encodeToString(Theme.serializer(), theme) + "\n"

    /**
     * The device's own settings file. A document from a newer app is loaded best-effort (known
     * fields are read, the rest ignored) and re-stamped with this app's schema; the store keeps
     * a copy of the original before writing back.
     */
    fun decodeSettings(text: String): DecodeResult<Settings> {
        val root = parseObject(text) ?: return corrupt(text)
        val schema = root.schema(SCHEMA)
        return decode(root, Settings.serializer()) { settings, sanitizer ->
            val sanitized = sanitizer.settings(settings)
            if (schema > SCHEMA) {
                sanitizer.warnings +=
                    Warning(
                        "schema",
                        "written by a newer app (schema $schema, this app writes $SCHEMA); loaded best-effort",
                    )
                sanitized.copy(schema = SCHEMA)
            } else {
                sanitized
            }
        }
    }

    /** A theme document from a file, share sheet or link. Newer schemas are refused rather than guessed at. */
    fun decodeTheme(
        text: String,
        appVersionCode: Int,
    ): DecodeResult<Theme> {
        val root = parseObject(text) ?: return corrupt(text)
        val schema = root.schema(THEME_SCHEMA)
        if (schema > THEME_SCHEMA) return DecodeResult.Unsupported(schema, THEME_SCHEMA)
        return decode(root, Theme.serializer()) { theme, sanitizer ->
            if (theme.minAppVersion > appVersionCode) {
                sanitizer.warnings +=
                    Warning(
                        "minAppVersion",
                        "made for app version ${theme.minAppVersion}; this is $appVersionCode, some values may be ignored",
                    )
            }
            sanitizer.theme(theme)
        }
    }

    /** The `schema` number a document declares, or null when it has none or is not JSON. */
    fun documentSchema(text: String): Int? = parseObject(text)?.get("schema")?.jsonPrimitive?.intOrNull

    private fun parseObject(text: String): JsonObject? {
        val element: JsonElement =
            try {
                ThemeJson.parseToJsonElement(text)
            } catch (e: SerializationException) {
                return null
            }
        return element as? JsonObject
    }

    private fun corrupt(text: String): DecodeResult.Corrupt =
        if (text.isBlank()) DecodeResult.Corrupt("empty document") else DecodeResult.Corrupt("not a JSON object")

    private fun JsonObject.schema(default: Int): Int = this["schema"]?.jsonPrimitive?.intOrNull ?: default

    private fun <T> decode(
        root: JsonObject,
        serializer: KSerializer<T>,
        finish: (T, Sanitizer) -> T,
    ): DecodeResult<T> {
        val value =
            try {
                ThemeJson.decodeFromJsonElement(serializer, root)
            } catch (e: SerializationException) {
                return DecodeResult.Corrupt(e.message ?: "unreadable document")
            } catch (e: IllegalArgumentException) {
                return DecodeResult.Corrupt(e.message ?: "unreadable document")
            }
        val sanitizer = Sanitizer()
        val finished = finish(value, sanitizer)
        return DecodeResult.Ok(finished, sanitizer.warnings.toList())
    }
}
