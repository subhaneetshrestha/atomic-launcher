package io.github.subhaneetshrestha.atomic.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import io.github.subhaneetshrestha.atomic.apps.AppActions
import io.github.subhaneetshrestha.atomic.apps.AppLauncher
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.system.SystemActionsBridge
import io.github.subhaneetshrestha.atomic.util.Logs

/** The launcher's own surfaces, which an action can ask for. It grows as later phases add them. */
interface LauncherSurfaces {
    fun openLauncherSettings()

    fun chooseDefaultLauncher()
}

/**
 * Carries out an action. Returns false when nothing happened, so the caller can say so instead of
 * leaving the user wondering; an implicit intent that found no app is remembered, so the picker
 * can grey it out from then on.
 */
class ActionRunner(
    private val activity: Activity,
    private val environment: AndroidActionEnvironment,
    private val surfaces: LauncherSurfaces,
    private val repository: AppRepository,
    private val appLauncher: AppLauncher,
    private val appActions: AppActions,
    private val system: SystemActionsBridge,
) {
    private val launcherApps: LauncherApps = activity.getSystemService(LauncherApps::class.java)
    private val audio: AudioManager = activity.getSystemService(AudioManager::class.java)
    private val torch = Torch(activity.applicationContext)

    fun run(action: Action): Boolean =
        when (action) {
            Action.None -> {
                true
            }

            is Action.Unknown -> {
                false
            }

            is Action.OpenApp -> {
                withEntry(action.component, action.user) {
                    appLauncher.launch(it, null)
                    true
                }
            }

            is Action.AppInfo -> {
                withEntry(action.component, action.user) {
                    appActions.showAppInfo(it, null)
                    true
                }
            }

            is Action.Uninstall -> {
                withPackage(action.pkg, action.user) {
                    appActions.uninstall(it)
                    true
                }
            }

            is Action.Shortcut -> {
                startShortcut(action)
            }

            is Action.OpenUrl -> {
                start(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)), null)
            }

            is Action.Builtin -> {
                runBuiltin(action.id)
            }
        }

    private fun runBuiltin(id: BuiltinId): Boolean =
        when (id) {
            BuiltinId.LAUNCHER_SETTINGS -> {
                surfaces.openLauncherSettings()
                true
            }

            BuiltinId.DEFAULT_LAUNCHER_CHOOSER -> {
                surfaces.chooseDefaultLauncher()
                true
            }

            BuiltinId.FLASHLIGHT_TOGGLE -> {
                torch.toggle()
            }

            BuiltinId.VOLUME_UI -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                true
            }

            BuiltinId.MEDIA_PLAY_PAUSE -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }

            BuiltinId.MEDIA_NEXT -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }

            BuiltinId.MEDIA_PREVIOUS -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }

            else -> {
                if (id.group == ActionGroup.SYSTEM) {
                    system.perform(id)
                } else {
                    start(BuiltinIntents.of(id, activity), id)
                }
            }
        }

    private inline fun withEntry(
        component: String,
        user: Long,
        body: (io.github.subhaneetshrestha.atomic.apps.AppEntry) -> Boolean,
    ): Boolean {
        val entry = repository.entryFor(component, user) ?: return false
        return body(entry)
    }

    private inline fun withPackage(
        pkg: String,
        user: Long,
        body: (io.github.subhaneetshrestha.atomic.apps.AppEntry) -> Boolean,
    ): Boolean {
        val entry =
            repository.current.entries.firstOrNull { it.key.packageName == pkg && it.key.userSerial == user }
                ?: return false
        return body(entry)
    }

    private fun startShortcut(action: Action.Shortcut): Boolean {
        val user = repository.userFor(action.user) ?: return false
        return try {
            launcherApps.startShortcut(action.pkg, action.id, null, null, user)
            true
        } catch (e: RuntimeException) {
            Logs.w(TAG, "shortcut ${action.pkg}/${action.id} would not start", e)
            false
        }
    }

    private fun start(
        intent: Intent?,
        id: BuiltinId?,
    ): Boolean {
        if (intent == null) return false
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Logs.w(TAG, "nothing on this device answers $intent", e)
            id?.let(environment::rememberNoHandler)
            false
        } catch (e: SecurityException) {
            Logs.w(TAG, "not allowed to start $intent", e)
            false
        }
    }

    private fun mediaKey(keyCode: Int): Boolean {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return true
    }

    private companion object {
        const val TAG = "ActionRunner"
    }
}

/** The camera flash as a torch, following its real state so a toggle is never out of step. */
private class Torch(
    context: Context,
) {
    private val cameras: CameraManager? = context.getSystemService(CameraManager::class.java)
    private var cameraId: String? = null
    private var on = false

    init {
        cameras?.registerTorchCallback(
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(
                    id: String,
                    enabled: Boolean,
                ) {
                    if (id == torchCamera()) on = enabled
                }
            },
            null,
        )
    }

    fun toggle(): Boolean {
        val id = torchCamera() ?: return false
        return try {
            cameras?.setTorchMode(id, !on) ?: return false
            on = !on
            true
        } catch (e: CameraAccessException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun torchCamera(): String? {
        cameraId?.let { return it }
        val manager = cameras ?: return null
        return try {
            manager.cameraIdList
                .firstOrNull {
                    manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ==
                        true
                }?.also { cameraId = it }
        } catch (e: CameraAccessException) {
            null
        }
    }
}
