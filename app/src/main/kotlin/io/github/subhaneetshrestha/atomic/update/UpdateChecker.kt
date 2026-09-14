package io.github.subhaneetshrestha.atomic.update

import io.github.subhaneetshrestha.atomic.background.ImageFetcher
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Asks GitHub whether there is a build newer than the one installed, and does nothing else: no
 * schedule lives here (Settings turns that on separately), no download, no install. One plain GET,
 * parsed, compared. [ADR 0005](/docs/decisions/0005-the-app-may-look-for-its-own-updates.md).
 *
 * The two channels are compared differently because they mean different things. A tagged release
 * names a version, so its tag is turned back into the version code the exact same commit built
 * ([ReleaseVersion]) and compared as a number. `edge` is not a version — its own version code does
 * not move between merges — so what is compared is *when* the channel's release was last replaced
 * against *when this APK was installed*, which is the only signal that tells "an edge build newer
 * than mine exists" apart from "I am looking at the edge build I already have".
 */
class UpdateChecker(
    private val fetcher: ImageFetcher,
    /** Overridden in tests to point at a local server instead of the real GitHub API. */
    private val apiBase: String = "https://api.github.com/repos/subhaneetshrestha/atomic-launcher",
) {
    sealed class Outcome {
        data class Available(
            val label: String,
            val apkUrl: String,
            val apkName: String,
            val checksumsUrl: String?,
            val htmlUrl: String,
        ) : Outcome()

        data object UpToDate : Outcome()

        data class Failed(
            val reason: String,
        ) : Outcome()
    }

    /** [edge] selects the rolling prerelease channel; false asks about the tagged one. */
    fun check(
        edge: Boolean,
        installedVersionCode: Long,
        installedAtMillis: Long,
    ): Outcome {
        val url = if (edge) "$apiBase/releases/tags/edge" else "$apiBase/releases/latest"
        val fetched = fetcher.fetchIndex(url, maxBytes = MAX_BYTES)
        val body =
            (fetched as? ImageFetcher.Outcome.Index)?.body
                ?: return Outcome.Failed((fetched as? ImageFetcher.Outcome.Failed)?.reason ?: "no answer")
        val release =
            try {
                JSON.decodeFromString(GitHubRelease.serializer(), body)
            } catch (e: SerializationException) {
                return Outcome.Failed("the answer did not look like a release")
            }
        val apk =
            release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return Outcome.Failed("that release has no APK")
        val checksums = release.assets.firstOrNull { it.name == "SHA256SUMS" }?.downloadUrl
        return if (edge) {
            checkEdge(release, apk, checksums, installedAtMillis)
        } else {
            checkRelease(release, apk, checksums, installedVersionCode)
        }
    }

    private fun checkRelease(
        release: GitHubRelease,
        apk: GitHubAsset,
        checksums: String?,
        installedVersionCode: Long,
    ): Outcome {
        val candidate =
            ReleaseVersion.codeFor(release.tagName)
                ?: return Outcome.Failed("'${release.tagName}' is not a version tag")
        return if (candidate.toLong() > installedVersionCode) {
            Outcome.Available(release.tagName, apk.downloadUrl, apk.name, checksums, release.htmlUrl)
        } else {
            Outcome.UpToDate
        }
    }

    private fun checkEdge(
        release: GitHubRelease,
        apk: GitHubAsset,
        checksums: String?,
        installedAtMillis: Long,
    ): Outcome {
        val published =
            parseIso8601(release.publishedAt)
                ?: return Outcome.Failed("that release has no publish time")
        return if (published > installedAtMillis + EDGE_MARGIN_MS) {
            Outcome.Available(release.tagName, apk.downloadUrl, apk.name, checksums, release.htmlUrl)
        } else {
            Outcome.UpToDate
        }
    }

    companion object {
        private const val MAX_BYTES = 32 * 1024

        // The gap between "CI finished publishing this build" and "the app first asks", so a
        // check moments after installing an edge build never calls its own release outdated.
        private const val EDGE_MARGIN_MS = 10 * 60 * 1000L

        private val JSON = Json { ignoreUnknownKeys = true }

        fun parseIso8601(value: String): Long? =
            runCatching {
                java.time.Instant
                    .parse(value)
                    .toEpochMilli()
            }.getOrNull()
    }
}
