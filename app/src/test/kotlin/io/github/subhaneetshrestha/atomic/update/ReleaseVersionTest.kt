package io.github.subhaneetshrestha.atomic.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseVersionTest {
    @Test
    fun `a version tag becomes the code the same commit built`() {
        assertEquals(10000, ReleaseVersion.codeFor("v1.0.0"))
        assertEquals(10203, ReleaseVersion.codeFor("v1.2.3"))
        assertEquals(20000, ReleaseVersion.codeFor("v2.0.0"))
    }

    @Test
    fun `whitespace around a tag does not stop it reading`() {
        assertEquals(10000, ReleaseVersion.codeFor("  v1.0.0\n"))
    }

    @Test
    fun `anything that is not a version tag is refused rather than guessed at`() {
        assertNull(ReleaseVersion.codeFor("edge"))
        assertNull(ReleaseVersion.codeFor("1.0.0"))
        assertNull(ReleaseVersion.codeFor("v1.0"))
        assertNull(ReleaseVersion.codeFor("v1.0.0-beta"))
        assertNull(ReleaseVersion.codeFor(""))
    }
}
