package io.github.subhaneetshrestha.atomic.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * The optional service behind the six actions no ordinary app may perform: the notification shade,
 * quick settings, recents, the power menu, locking the screen and a screenshot.
 *
 * It listens to nothing. Its configuration asks for no event types and no window content, so
 * Android delivers it nothing to read; it exists only to be told, by the launcher's own process,
 * to perform one action the user has bound to a gesture. Each instruction carries the moment it
 * was issued and is dropped if it arrives late, so a gesture cannot fire minutes after it was made.
 *
 * It runs in its own process. If it dies, the home screen does not.
 */
class SystemActionsService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val id = intent?.getStringExtra(EXTRA_ACTION)?.let(BuiltinId::fromKey)
        val issuedAt = intent?.getLongExtra(EXTRA_ISSUED_AT, 0L) ?: 0L
        val action = id?.let(SystemActions::globalAction)
        val now = SystemClock.uptimeMillis()
        when {
            action == null -> Logs.w(TAG, "asked for something that is not a global action")
            !SystemActions.isFresh(issuedAt, now) -> Logs.d(TAG) { "dropped $id, issued ${now - issuedAt} ms ago" }
            else -> Logs.d(TAG) { "$id performed=${performGlobalAction(action)}" }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_ACTION = "io.github.subhaneetshrestha.atomic.ACTION"

        private const val EXTRA_ISSUED_AT = "io.github.subhaneetshrestha.atomic.ISSUED_AT"

        private const val TAG = "SystemActionsService"

        fun intent(
            context: Context,
            id: BuiltinId,
        ): Intent =
            Intent(context, SystemActionsService::class.java)
                .putExtra(EXTRA_ACTION, id.key)
                .putExtra(EXTRA_ISSUED_AT, SystemClock.uptimeMillis())
    }
}
