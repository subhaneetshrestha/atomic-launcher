package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * What a gesture, an info line or a menu row does. Persisted, so the wire form is part of the
 * settings document: `{"type": "...", ...}`. An action a future version introduces decodes to
 * [Unknown] and is written back untouched, so an older app never eats a newer configuration.
 */
@Serializable(with = ActionSerializer::class)
sealed class Action {
    /** Bound to nothing. */
    @Serializable
    data object None : Action()

    @Serializable
    data class OpenApp(
        val component: String,
        val user: Long = 0,
    ) : Action()

    /** A static, dynamic or pinned shortcut of [pkg]; needs the home role to launch. */
    @Serializable
    data class Shortcut(
        val pkg: String,
        val id: String,
        val user: Long = 0,
    ) : Action()

    @Serializable
    data class OpenUrl(
        val url: String,
    ) : Action()

    @Serializable
    data class Builtin(
        val id: BuiltinId,
    ) : Action()

    /** Menu-only: the system app-info page. */
    @Serializable
    data class AppInfo(
        val component: String,
        val user: Long = 0,
    ) : Action()

    /** Menu-only: the system uninstall dialog. */
    @Serializable
    data class Uninstall(
        val pkg: String,
        val user: Long = 0,
    ) : Action()

    /** Read from the document but not understood by this version; kept exactly as it was. */
    data class Unknown(
        val raw: JsonElement,
    ) : Action()
}

/**
 * Reads and writes [Action] with a `type` discriminator, and turns anything unreadable into
 * [Action.Unknown] instead of failing the whole document.
 */
object ActionSerializer : KSerializer<Action> {
    private const val TYPE = "type"

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Action")

    override fun serialize(
        encoder: Encoder,
        value: Action,
    ) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("actions are stored as JSON only")
        val element =
            if (value is Action.Unknown) {
                value.raw
            } else {
                val (name, body) = write(output.json, value)
                buildJsonObject {
                    put(TYPE, JsonPrimitive(name))
                    for ((key, field) in body) put(key, field)
                }
            }
        output.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Action {
        val input = decoder as? JsonDecoder ?: throw SerializationException("actions are stored as JSON only")
        val element = input.decodeJsonElement()
        val obj = element as? JsonObject ?: return Action.Unknown(element)
        val name = (obj[TYPE] as? JsonPrimitive)?.contentOrNull
        val serializer = name?.let(READERS::get) ?: return Action.Unknown(element)
        return try {
            input.json.decodeFromJsonElement(serializer, obj)
        } catch (e: SerializationException) {
            Action.Unknown(element)
        } catch (e: IllegalArgumentException) {
            Action.Unknown(element)
        }
    }

    /** The type name and the fields of a known action. Exhaustive, so a new variant cannot be forgotten. */
    private fun write(
        json: Json,
        value: Action,
    ): Pair<String, JsonObject> =
        when (value) {
            is Action.None -> {
                "none" to JsonObject(emptyMap())
            }

            is Action.OpenApp -> {
                "app" to json.encodeToJsonElement(Action.OpenApp.serializer(), value).jsonObject
            }

            is Action.Shortcut -> {
                "shortcut" to json.encodeToJsonElement(Action.Shortcut.serializer(), value).jsonObject
            }

            is Action.OpenUrl -> {
                "url" to json.encodeToJsonElement(Action.OpenUrl.serializer(), value).jsonObject
            }

            is Action.Builtin -> {
                "builtin" to json.encodeToJsonElement(Action.Builtin.serializer(), value).jsonObject
            }

            is Action.AppInfo -> {
                "app_info" to json.encodeToJsonElement(Action.AppInfo.serializer(), value).jsonObject
            }

            is Action.Uninstall -> {
                "uninstall" to
                    json.encodeToJsonElement(Action.Uninstall.serializer(), value).jsonObject
            }

            is Action.Unknown -> {
                throw IllegalStateException("unknown actions are written verbatim")
            }
        }

    private val READERS: Map<String, KSerializer<out Action>> =
        mapOf(
            "none" to Action.None.serializer(),
            "app" to Action.OpenApp.serializer(),
            "shortcut" to Action.Shortcut.serializer(),
            "url" to Action.OpenUrl.serializer(),
            "builtin" to Action.Builtin.serializer(),
            "app_info" to Action.AppInfo.serializer(),
            "uninstall" to Action.Uninstall.serializer(),
        )
}
