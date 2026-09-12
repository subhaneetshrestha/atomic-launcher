package io.github.subhaneetshrestha.atomic.background

import android.util.AtomicFile
import io.github.subhaneetshrestha.atomic.core.collections.BackgroundState
import io.github.subhaneetshrestha.atomic.util.Logs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Where the background lives between runs: `filesDir/background/`. Deliberately not the cache
 * directory — `pm trim-caches`, or Android doing the same thing on a full phone, would otherwise
 * take away the wallpaper that is on screen.
 *
 * A new image is written to [incoming] and only becomes [current] once it has arrived whole, so a
 * download that dies halfway through leaves the launcher showing the picture it already had.
 */
class ImageStore(
    filesDir: File,
) {
    private val dir = File(filesDir, DIRECTORY)

    val current = File(dir, "current.img")

    val previous = File(dir, "previous.img")

    val incoming = File(dir, "incoming.img")

    private val stateFile = AtomicFile(File(dir, "state.json"))

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun readState(): BackgroundState {
        val text =
            try {
                if (stateFile.baseFile.exists()) stateFile.readFully().toString(Charsets.UTF_8) else null
            } catch (e: IOException) {
                Logs.w(TAG, "could not read the background state", e)
                null
            } ?: return BackgroundState()
        return try {
            json.decodeFromString(BackgroundState.serializer(), text)
        } catch (e: SerializationException) {
            Logs.w(TAG, "the background state was unreadable; starting again", e)
            BackgroundState()
        }
    }

    fun writeState(state: BackgroundState) {
        var out: FileOutputStream? = null
        try {
            dir.mkdirs()
            out = stateFile.startWrite()
            out.write(json.encodeToString(BackgroundState.serializer(), state).toByteArray(Charsets.UTF_8))
            stateFile.finishWrite(out)
        } catch (e: IOException) {
            if (out != null) stateFile.failWrite(out)
            Logs.w(TAG, "could not write the background state", e)
        }
    }

    /** An empty file ready to download into; anything left from a failed attempt is cleared first. */
    fun newIncoming(): File? =
        try {
            dir.mkdirs()
            incoming.delete()
            incoming
        } catch (e: IOException) {
            Logs.w(TAG, "could not prepare the download", e)
            null
        }

    /** Makes what was downloaded the current image, keeping the one before it for the crossfade. */
    fun commit(): Boolean {
        if (!incoming.isFile || incoming.length() == 0L) return false
        previous.delete()
        if (current.isFile && !current.renameTo(previous)) current.delete()
        return incoming.renameTo(current)
    }

    fun forget() {
        current.delete()
        previous.delete()
        incoming.delete()
        writeState(BackgroundState())
    }

    private companion object {
        const val DIRECTORY = "background"
        const val TAG = "ImageStore"
    }
}
