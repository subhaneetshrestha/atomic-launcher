package io.github.subhaneetshrestha.atomic.diagnostics

import java.io.File
import java.io.IOException
import java.time.Instant

/** What a report needs to say about the build and the device; filled in by the Application on Android. */
data class CrashEnvironment(
    val appVersion: String,
    val versionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val device: String,
)

/**
 * Keeps the last uncaught exception in a private file so the user can share it from Settings →
 * About. Nothing is sent anywhere. A crashing home app silently loses its default status, so this
 * is the only trace most users will ever be able to give.
 */
class CrashRecorder(
    private val dir: File,
    private val clock: () -> Instant,
    private val environment: CrashEnvironment,
) {
    private val file: File get() = File(dir, FILE_NAME)

    /** Overwrites the previous report. Never throws: it runs inside the crash handler. */
    fun record(
        thread: Thread,
        error: Throwable,
    ) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(render(thread, error))
            if (!tmp.renameTo(file)) file.writeText(render(thread, error))
        } catch (_: IOException) {
            // Nothing sensible left to do while the process is already dying.
        } catch (_: SecurityException) {
            // Same.
        }
    }

    fun lastReport(): String? =
        try {
            file.takeIf { it.isFile }?.readText()
        } catch (_: IOException) {
            null
        }

    fun clear() {
        file.delete()
    }

    /** Records every uncaught exception, then lets the previous handler (Android's) finish the crash. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun render(
        thread: Thread,
        error: Throwable,
    ): String =
        buildString {
            appendLine("atomic ${environment.appVersion} (${environment.versionCode})")
            appendLine("Android ${environment.androidRelease} (API ${environment.sdkInt}), ${environment.device}")
            appendLine("Time: ${clock()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(error.stackTraceToString())
        }

    companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
