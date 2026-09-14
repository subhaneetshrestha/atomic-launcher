package io.github.subhaneetshrestha.atomic.update

/**
 * The `sha256sum` output format `release.yml` publishes beside every APK: one line per file,
 * `<64 lowercase hex characters>␠␠<filename>`. Pure text parsing, so it is tested the same way
 * [ReleaseVersion] is, without touching a file or a network call.
 */
object Checksums {
    private val LINE = Regex("^([0-9a-fA-F]{64})\\s+\\*?(.+)$")

    /** The published hash for [fileName], or null when that file is not named in [sums]. */
    fun hashFor(
        sums: String,
        fileName: String,
    ): String? =
        sums
            .lineSequence()
            .mapNotNull { LINE.matchEntire(it.trim())?.destructured }
            .firstOrNull { (_, name) -> name == fileName }
            ?.component1()
            ?.lowercase()
}
