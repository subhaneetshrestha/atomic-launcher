package io.github.subhaneetshrestha.atomic.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.github.subhaneetshrestha.atomic.background.ImageFetcher
import io.github.subhaneetshrestha.atomic.util.Logs
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads a release APK, checks it against the checksums published beside it, and hands it to
 * Android's own installer. Every step past the download can fail, and every failure here leaves
 * nothing installed and nothing half-written: [ADR 0005](/docs/decisions/0005-the-app-may-look-for-its-own-updates.md).
 */
class UpdateInstaller(
    private val context: Context,
) {
    sealed class Result {
        /** Handed to the system installer; its own confirmation dialog takes over from here. */
        data object Started : Result()

        data class Failed(
            val reason: String,
        ) : Result()
    }

    fun install(
        fetcher: ImageFetcher,
        apkUrl: String,
        apkName: String,
        checksumsUrl: String?,
    ): Result {
        val target = File(context.cacheDir, "update.apk")
        target.delete()
        val fetched = fetcher.fetchImage(apkUrl, target)
        if (fetched !is ImageFetcher.Outcome.Image) {
            target.delete()
            return Result.Failed((fetched as? ImageFetcher.Outcome.Failed)?.reason ?: "the download failed")
        }
        checksumsUrl?.let { url ->
            val problem = verify(fetcher, url, apkName, target)
            if (problem != null) {
                target.delete()
                return Result.Failed(problem)
            }
        }
        return try {
            commit(target)
        } catch (e: IOException) {
            target.delete()
            Result.Failed(e.message ?: "the installer refused it")
        }
    }

    /** Null when the download matches what was published, or there was nothing published to check. */
    private fun verify(
        fetcher: ImageFetcher,
        checksumsUrl: String,
        apkName: String,
        downloaded: File,
    ): String? {
        val outcome = fetcher.fetchIndex(checksumsUrl, maxBytes = MAX_CHECKSUMS_BYTES)
        val body = (outcome as? ImageFetcher.Outcome.Index)?.body ?: return null
        val expected = Checksums.hashFor(body, apkName) ?: return null
        val actual = sha256(downloaded)
        return if (actual == expected) null else "the download did not match what was published"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Opens an installer session, streams the APK into it, and commits — Android takes it from here. */
    private fun commit(apk: File): Result {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite(SESSION_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusIntent(sessionId).intentSender)
            return Result.Started
        } catch (e: IOException) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    private fun statusIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    companion object {
        private const val SESSION_NAME = "atomic-update"
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_CHECKSUMS_BYTES = 8 * 1024
        const val TAG = "UpdateInstaller"
    }
}

/** Only ever reached by our own [PendingIntent] from [UpdateInstaller.commit]; nothing else may send it this. */
class UpdateInstallReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = extraIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Logs.d(UpdateInstaller.TAG) { "install succeeded" }
            }

            else -> {
                Logs.w(
                    UpdateInstaller.TAG,
                    "install failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
                )
            }
        }
    }

    private fun extraIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
}
