package io.github.subhaneetshrestha.atomic.update

/**
 * The one arithmetic fact this whole feature depends on. `docs/release/checklist.md` documents
 * the formula a release commit's version literals follow: `versionCode = MAJOR * 10000 +
 * MINOR * 100 + PATCH`, tag `vMAJOR.MINOR.PATCH`. Reproducing it here is the entire comparison —
 * no build step needs to publish a version code anywhere for this to work.
 */
object ReleaseVersion {
    private val TAG = Regex("^v(\\d{1,3})\\.(\\d{1,2})\\.(\\d{1,2})$")

    /** The version code a tag like `v1.2.3` builds, or null when it is not that shape. */
    fun codeFor(tag: String): Int? {
        val match = TAG.matchEntire(tag.trim()) ?: return null
        val (major, minor, patch) = match.destructured
        return major.toInt() * MAJOR_STEP + minor.toInt() * MINOR_STEP + patch.toInt()
    }

    private const val MAJOR_STEP = 10000
    private const val MINOR_STEP = 100
}
