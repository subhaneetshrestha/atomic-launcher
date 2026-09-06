package io.github.subhaneetshrestha.atomic.util

import android.util.Log

/** Debug logging, switched on by [io.github.subhaneetshrestha.atomic.AtomicApp] only for debuggable builds. */
object Logs {
    @Volatile
    var enabled: Boolean = false

    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (enabled) Log.d(tag, message())
    }

    fun w(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        Log.w(tag, message, error)
    }
}
