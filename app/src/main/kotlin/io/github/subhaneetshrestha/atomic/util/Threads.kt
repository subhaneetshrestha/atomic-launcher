package io.github.subhaneetshrestha.atomic.util

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * The app's threads. The apps thread owns every LauncherApps binder call and snapshot write; the
 * main thread owns rendering and launches. This object is the only seam where a coroutine
 * dispatcher would be introduced later, so nothing else may create threads.
 */
object Threads {
    val main: Handler = Handler(Looper.getMainLooper())

    private val appsThread = HandlerThread("atomic-apps").apply { start() }

    val apps: Handler = Handler(appsThread.looper)

    val isMainThread: Boolean
        get() = Looper.myLooper() == Looper.getMainLooper()
}
