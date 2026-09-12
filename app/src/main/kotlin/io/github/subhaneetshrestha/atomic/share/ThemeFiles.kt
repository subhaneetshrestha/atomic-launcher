package io.github.subhaneetshrestha.atomic.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.subhaneetshrestha.atomic.core.theme.SettingsCodec
import io.github.subhaneetshrestha.atomic.core.theme.Theme
import io.github.subhaneetshrestha.atomic.util.Logs
import java.io.File
import java.io.IOException

/**
 * A theme as a file other apps can be handed. The file is written into the cache and shared
 * through a FileProvider, so the launcher never exposes a path of its own and the grant lasts only
 * as long as the share.
 */
object ThemeFiles {
    const val EXTENSION = "atomictheme"

    /** Themes are plain JSON; the extension is what makes one recognisable as a theme. */
    const val MIME = "application/json"

    fun fileName(theme: Theme): String {
        val id =
            theme.meta.id
                .ifBlank { "theme" }
                .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return "${id.ifEmpty { "theme" }}.$EXTENSION"
    }

    /** Writes [theme] into the cache and returns a URI another app may read it from, or null. */
    fun share(
        context: Context,
        theme: Theme,
    ): Uri? =
        try {
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, fileName(theme))
            file.writeText(SettingsCodec.encodeTheme(theme))
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (e: IOException) {
            Logs.w(TAG, "could not write the theme to share", e)
            null
        } catch (e: IllegalArgumentException) {
            Logs.w(TAG, "the theme file is not one the provider will share", e)
            null
        }

    fun sendIntent(
        uri: Uri,
        subject: String,
    ): Intent =
        Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun sendLinkIntent(link: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, link)

    private const val DIRECTORY = "shared"

    private const val TAG = "ThemeFiles"
}
