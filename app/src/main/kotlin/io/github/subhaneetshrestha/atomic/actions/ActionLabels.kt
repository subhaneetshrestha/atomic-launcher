package io.github.subhaneetshrestha.atomic.actions

import android.content.Context
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.core.theme.GestureId
import io.github.subhaneetshrestha.atomic.core.theme.InfoLineId

/** The words for actions and for the things they can be bound to. */
object ActionLabels {
    fun of(
        context: Context,
        id: BuiltinId,
    ): String = context.getString(labelRes(id))

    fun of(
        context: Context,
        group: ActionGroup,
    ): String =
        context.getString(
            when (group) {
                ActionGroup.INTENT -> R.string.section_apps
                ActionGroup.LAUNCHER -> R.string.section_launcher
                ActionGroup.DEVICE -> R.string.section_device
                ActionGroup.SYSTEM -> R.string.section_system
            },
        )

    /** What a binding does, in the user's words: an app's own name where there is one. */
    fun describe(
        context: Context,
        action: Action,
        apps: AppRepository,
    ): String =
        when (action) {
            Action.None -> context.getString(R.string.action_none)
            is Action.Builtin -> of(context, action.id)
            is Action.OpenApp -> apps.entryFor(action.component, action.user)?.label ?: action.component
            is Action.AppInfo -> context.getString(R.string.menu_app_info)
            is Action.Uninstall -> context.getString(R.string.menu_uninstall)
            is Action.Shortcut -> context.getString(R.string.action_shortcut_in, action.pkg)
            is Action.OpenUrl -> action.url
            is Action.Unknown -> context.getString(R.string.action_from_newer_version)
        }

    /** Why an action cannot run, in one short line; null when it can. */
    fun reason(
        context: Context,
        availability: Availability,
    ): String? =
        when (availability) {
            Availability.Available -> {
                null
            }

            Availability.NoHandler -> {
                context.getString(R.string.action_no_app)
            }

            Availability.Missing -> {
                context.getString(R.string.action_app_gone)
            }

            Availability.NotDefaultLauncher -> {
                context.getString(R.string.action_needs_home_role)
            }

            is Availability.NeedsGrant -> {
                context.getString(
                    when (availability.grant) {
                        ActionGrant.ACCESSIBILITY -> R.string.action_needs_accessibility
                        ActionGrant.DEVICE_ADMIN -> R.string.action_needs_device_admin
                    },
                )
            }

            is Availability.Unsupported -> {
                context.getString(
                    when (availability.reason) {
                        UnsupportedReason.NEEDS_NEWER_ANDROID -> {
                            R.string.action_needs_newer_android
                        }

                        UnsupportedReason.NO_HARDWARE -> {
                            R.string.action_no_hardware
                        }

                        UnsupportedReason.NOT_IN_THIS_VERSION, UnsupportedReason.UNKNOWN_ACTION -> {
                            R.string.action_not_yet_built
                        }
                    },
                )
            }
        }

    fun of(
        context: Context,
        surface: BindingSurface,
    ): String =
        context.getString(
            when (surface) {
                is BindingSurface.Gesture -> {
                    gestureRes(surface.id)
                }

                is BindingSurface.InfoTap -> {
                    when (surface.id) {
                        InfoLineId.CLOCK -> R.string.surface_clock_tap
                        InfoLineId.DATE -> R.string.surface_date_tap
                        InfoLineId.BATTERY -> R.string.surface_battery_tap
                        InfoLineId.SCREEN_TIME -> R.string.surface_screen_time_tap
                    }
                }

                is BindingSurface.InfoLongPress -> {
                    when (surface.id) {
                        InfoLineId.CLOCK -> R.string.surface_clock_hold
                        InfoLineId.DATE -> R.string.surface_date_hold
                        InfoLineId.BATTERY -> R.string.surface_battery_hold
                        InfoLineId.SCREEN_TIME -> R.string.surface_screen_time_hold
                    }
                }
            },
        )

    private fun gestureRes(id: GestureId): Int =
        when (id) {
            GestureId.SWIPE_UP -> R.string.surface_swipe_up
            GestureId.SWIPE_DOWN -> R.string.surface_swipe_down
            GestureId.SWIPE_LEFT -> R.string.surface_swipe_left
            GestureId.SWIPE_RIGHT -> R.string.surface_swipe_right
            GestureId.LONG_SWIPE_UP -> R.string.surface_long_swipe_up
            GestureId.LONG_SWIPE_DOWN -> R.string.surface_long_swipe_down
            GestureId.LONG_SWIPE_LEFT -> R.string.surface_long_swipe_left
            GestureId.LONG_SWIPE_RIGHT -> R.string.surface_long_swipe_right
            GestureId.DOUBLE_TAP -> R.string.surface_double_tap
            GestureId.LONG_PRESS -> R.string.surface_long_press
        }

    private fun labelRes(id: BuiltinId): Int =
        when (id) {
            BuiltinId.ASSISTANT -> R.string.action_assistant
            BuiltinId.CAMERA -> R.string.action_camera
            BuiltinId.DIALER -> R.string.action_dialer
            BuiltinId.MESSAGES -> R.string.action_messages
            BuiltinId.EMAIL -> R.string.action_email
            BuiltinId.CONTACTS -> R.string.action_contacts
            BuiltinId.BROWSER -> R.string.action_browser
            BuiltinId.GALLERY -> R.string.action_gallery
            BuiltinId.MUSIC -> R.string.action_music
            BuiltinId.MAPS -> R.string.action_maps
            BuiltinId.APP_MARKET -> R.string.action_app_market
            BuiltinId.CALCULATOR -> R.string.action_calculator
            BuiltinId.ALARMS -> R.string.action_alarms
            BuiltinId.TIMERS -> R.string.action_timers
            BuiltinId.CALENDAR_TODAY -> R.string.action_calendar_today
            BuiltinId.WEB_SEARCH -> R.string.action_web_search
            BuiltinId.WALLPAPER_PICKER -> R.string.action_wallpaper_picker
            BuiltinId.SETTINGS -> R.string.action_settings
            BuiltinId.WIFI_SETTINGS -> R.string.action_wifi_settings
            BuiltinId.BLUETOOTH_SETTINGS -> R.string.action_bluetooth_settings
            BuiltinId.DISPLAY_SETTINGS -> R.string.action_display_settings
            BuiltinId.SOUND_SETTINGS -> R.string.action_sound_settings
            BuiltinId.BATTERY_SETTINGS -> R.string.action_battery_settings
            BuiltinId.NOTIFICATION_SETTINGS -> R.string.action_notification_settings
            BuiltinId.PANEL_INTERNET -> R.string.action_panel_internet
            BuiltinId.PANEL_WIFI -> R.string.action_panel_wifi
            BuiltinId.PANEL_VOLUME -> R.string.action_panel_volume
            BuiltinId.PANEL_NFC -> R.string.action_panel_nfc
            BuiltinId.OPEN_SEARCH -> R.string.action_open_search
            BuiltinId.OPEN_DRAWER -> R.string.action_open_drawer
            BuiltinId.LAUNCHER_SETTINGS -> R.string.action_launcher_settings
            BuiltinId.NEXT_BACKGROUND -> R.string.action_next_background
            BuiltinId.DEFAULT_LAUNCHER_CHOOSER -> R.string.action_default_launcher_chooser
            BuiltinId.FLASHLIGHT_TOGGLE -> R.string.action_flashlight_toggle
            BuiltinId.VOLUME_UI -> R.string.action_volume_ui
            BuiltinId.MEDIA_PLAY_PAUSE -> R.string.action_media_play_pause
            BuiltinId.MEDIA_NEXT -> R.string.action_media_next
            BuiltinId.MEDIA_PREVIOUS -> R.string.action_media_previous
            BuiltinId.LOCK_SCREEN -> R.string.action_lock_screen
            BuiltinId.NOTIFICATION_SHADE -> R.string.action_notification_shade
            BuiltinId.QUICK_SETTINGS -> R.string.action_quick_settings
            BuiltinId.RECENTS -> R.string.action_recents
            BuiltinId.SCREENSHOT -> R.string.action_screenshot
            BuiltinId.POWER_MENU -> R.string.action_power_menu
        }
}
