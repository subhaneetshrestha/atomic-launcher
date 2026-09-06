package io.github.subhaneetshrestha.atomic.settings

import android.util.AtomicFile
import io.github.subhaneetshrestha.atomic.core.theme.SettingsStorage
import io.github.subhaneetshrestha.atomic.util.Logs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** `filesDir/settings.json` behind android.util.AtomicFile: a write either lands completely or not at all. */
class AtomicFileStorage(
    private val dir: File,
) : SettingsStorage {
    private val file = AtomicFile(File(dir, FILE_NAME))

    override fun read(): String? =
        try {
            if (file.baseFile.exists()) file.readFully().toString(Charsets.UTF_8) else null
        } catch (e: IOException) {
            Logs.w(TAG, "could not read ${file.baseFile}", e)
            null
        }

    override fun write(text: String) {
        var out: FileOutputStream? = null
        try {
            dir.mkdirs()
            out = file.startWrite()
            out.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            if (out != null) file.failWrite(out)
            Logs.w(TAG, "could not write ${file.baseFile}", e)
        }
    }

    override fun preserve(
        text: String,
        tag: String,
    ) {
        try {
            dir.mkdirs()
            File(dir, "$FILE_NAME.$tag-${System.currentTimeMillis()}").writeText(text)
        } catch (e: IOException) {
            Logs.w(TAG, "could not keep a copy of the $tag settings document", e)
        }
    }

    companion object {
        const val FILE_NAME = "settings.json"
        private const val TAG = "AtomicFileStorage"
    }
}
