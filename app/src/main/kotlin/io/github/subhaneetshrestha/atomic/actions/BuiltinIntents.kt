package io.github.subhaneetshrestha.atomic.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId

/**
 * The public intent behind each built-in action that opens something outside the launcher.
 * Everything here is a documented implicit intent: no package is named, so nothing depends on
 * Google apps being present, and any app that claims the intent can answer it.
 */
object BuiltinIntents {
    /** null for the actions that are carried out in code rather than by starting something. */
    fun of(
        id: BuiltinId,
        context: Context,
    ): Intent? =
        when (id) {
            BuiltinId.ASSISTANT -> {
                Intent(Intent.ACTION_ASSIST)
            }

            BuiltinId.CAMERA -> {
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            }

            BuiltinId.DIALER -> {
                Intent(Intent.ACTION_DIAL)
            }

            BuiltinId.MESSAGES -> {
                mainApp(Intent.CATEGORY_APP_MESSAGING)
            }

            BuiltinId.EMAIL -> {
                mainApp(Intent.CATEGORY_APP_EMAIL)
            }

            BuiltinId.CONTACTS -> {
                mainApp(Intent.CATEGORY_APP_CONTACTS)
            }

            BuiltinId.BROWSER -> {
                mainApp(Intent.CATEGORY_APP_BROWSER)
            }

            BuiltinId.GALLERY -> {
                mainApp(Intent.CATEGORY_APP_GALLERY)
            }

            BuiltinId.MUSIC -> {
                mainApp(Intent.CATEGORY_APP_MUSIC)
            }

            BuiltinId.MAPS -> {
                mainApp(Intent.CATEGORY_APP_MAPS)
            }

            BuiltinId.APP_MARKET -> {
                mainApp(Intent.CATEGORY_APP_MARKET)
            }

            BuiltinId.CALCULATOR -> {
                mainApp(Intent.CATEGORY_APP_CALCULATOR)
            }

            BuiltinId.ALARMS -> {
                Intent(AlarmClock.ACTION_SHOW_ALARMS)
            }

            BuiltinId.TIMERS -> {
                Intent(AlarmClock.ACTION_SHOW_TIMERS)
            }

            BuiltinId.CALENDAR_TODAY -> {
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"),
                )
            }

            BuiltinId.WEB_SEARCH -> {
                Intent(Intent.ACTION_WEB_SEARCH)
            }

            BuiltinId.WALLPAPER_PICKER -> {
                Intent.createChooser(
                    Intent(Intent.ACTION_SET_WALLPAPER),
                    context.getString(android.R.string.selectTextMode),
                )
            }

            BuiltinId.SETTINGS -> {
                Intent(Settings.ACTION_SETTINGS)
            }

            BuiltinId.WIFI_SETTINGS -> {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }

            BuiltinId.BLUETOOTH_SETTINGS -> {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            }

            BuiltinId.DISPLAY_SETTINGS -> {
                Intent(Settings.ACTION_DISPLAY_SETTINGS)
            }

            BuiltinId.SOUND_SETTINGS -> {
                Intent(Settings.ACTION_SOUND_SETTINGS)
            }

            BuiltinId.BATTERY_SETTINGS -> {
                Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            }

            BuiltinId.NOTIFICATION_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS)
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }

            BuiltinId.PANEL_INTERNET -> {
                panel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            }

            BuiltinId.PANEL_WIFI -> {
                panel(Settings.Panel.ACTION_WIFI)
            }

            BuiltinId.PANEL_VOLUME -> {
                panel(Settings.Panel.ACTION_VOLUME)
            }

            BuiltinId.PANEL_NFC -> {
                panel(Settings.Panel.ACTION_NFC)
            }

            else -> {
                null
            }
        }

    /** Opens whichever app the user has for a kind of thing, without naming one. */
    private fun mainApp(category: String): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

    private fun panel(action: String): Intent? = if (Build.VERSION.SDK_INT >= 29) Intent(action) else null
}
