package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** How the picker sections the built-in actions, and roughly what each one costs to run. */
enum class ActionGroup {
    /** Public implicit intents: an app or a system screen answers, or nothing does. */
    INTENT,

    /** Inside the launcher itself. */
    LAUNCHER,

    /** Device controls that need no permission (torch, volume, media keys). */
    DEVICE,

    /** Needs the optional accessibility service (or device admin for the lock). */
    SYSTEM,
}

/**
 * Everything a gesture, a home-info line or a menu row can be bound to besides opening an app.
 * [key] is the stored form and never changes once released; the constant name may.
 */
@Serializable(with = BuiltinIdSerializer::class)
enum class BuiltinId(
    val key: String,
    val group: ActionGroup,
) {
    // Apps by category or purpose, through documented implicit intents.
    ASSISTANT("assistant", ActionGroup.INTENT),
    CAMERA("camera", ActionGroup.INTENT),
    DIALER("dialer", ActionGroup.INTENT),
    MESSAGES("messages", ActionGroup.INTENT),
    EMAIL("email", ActionGroup.INTENT),
    CONTACTS("contacts", ActionGroup.INTENT),
    BROWSER("browser", ActionGroup.INTENT),
    GALLERY("gallery", ActionGroup.INTENT),
    MUSIC("music", ActionGroup.INTENT),
    MAPS("maps", ActionGroup.INTENT),
    APP_MARKET("app_market", ActionGroup.INTENT),
    CALCULATOR("calculator", ActionGroup.INTENT),
    ALARMS("alarms", ActionGroup.INTENT),
    TIMERS("timers", ActionGroup.INTENT),
    CALENDAR_TODAY("calendar_today", ActionGroup.INTENT),
    WEB_SEARCH("web_search", ActionGroup.INTENT),
    WALLPAPER_PICKER("wallpaper_picker", ActionGroup.INTENT),

    // System settings screens and the Settings panels (API 29+).
    SETTINGS("settings", ActionGroup.INTENT),
    WIFI_SETTINGS("wifi_settings", ActionGroup.INTENT),
    BLUETOOTH_SETTINGS("bluetooth_settings", ActionGroup.INTENT),
    DISPLAY_SETTINGS("display_settings", ActionGroup.INTENT),
    SOUND_SETTINGS("sound_settings", ActionGroup.INTENT),
    BATTERY_SETTINGS("battery_settings", ActionGroup.INTENT),
    NOTIFICATION_SETTINGS("notification_settings", ActionGroup.INTENT),
    PANEL_INTERNET("panel_internet", ActionGroup.INTENT),
    PANEL_WIFI("panel_wifi", ActionGroup.INTENT),
    PANEL_VOLUME("panel_volume", ActionGroup.INTENT),
    PANEL_NFC("panel_nfc", ActionGroup.INTENT),

    // The launcher's own surfaces.
    OPEN_SEARCH("open_search", ActionGroup.LAUNCHER),
    OPEN_DRAWER("open_drawer", ActionGroup.LAUNCHER),
    LAUNCHER_SETTINGS("launcher_settings", ActionGroup.LAUNCHER),
    NEXT_BACKGROUND("next_background", ActionGroup.LAUNCHER),
    DEFAULT_LAUNCHER_CHOOSER("default_launcher_chooser", ActionGroup.LAUNCHER),

    // Device controls, no permission needed.
    FLASHLIGHT_TOGGLE("flashlight_toggle", ActionGroup.DEVICE),
    VOLUME_UI("volume_ui", ActionGroup.DEVICE),
    MEDIA_PLAY_PAUSE("media_play_pause", ActionGroup.DEVICE),
    MEDIA_NEXT("media_next", ActionGroup.DEVICE),
    MEDIA_PREVIOUS("media_previous", ActionGroup.DEVICE),

    // Behind the optional accessibility service (lock also works through device admin).
    LOCK_SCREEN("lock_screen", ActionGroup.SYSTEM),
    NOTIFICATION_SHADE("notification_shade", ActionGroup.SYSTEM),
    QUICK_SETTINGS("quick_settings", ActionGroup.SYSTEM),
    RECENTS("recents", ActionGroup.SYSTEM),
    SCREENSHOT("screenshot", ActionGroup.SYSTEM),
    POWER_MENU("power_menu", ActionGroup.SYSTEM),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): BuiltinId? = byKey[key]
    }
}

/** Stores the stable [BuiltinId.key] rather than the constant name. */
object BuiltinIdSerializer : KSerializer<BuiltinId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BuiltinId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: BuiltinId,
    ) = encoder.encodeString(value.key)

    override fun deserialize(decoder: Decoder): BuiltinId {
        val key = decoder.decodeString()
        return BuiltinId.fromKey(key) ?: throw SerializationException("no built-in action called '$key'")
    }
}
