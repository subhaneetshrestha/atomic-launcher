package io.github.subhaneetshrestha.atomic.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChecksumsTest {
    private val sums =
        """
        8285c125f2c836b7a868d7f35715f6a514e5cfb4d6fe153ea673427b21f189db  atomic-launcher-v1.0.0.apk
        e6351d3766186d2b4d187ee3c1613bf7516a35fd9b3207d9bf72fc7c2576e076  SHA256SUMS
        """.trimIndent()

    @Test
    fun `the hash for a named file is read out`() {
        assertEquals(
            "8285c125f2c836b7a868d7f35715f6a514e5cfb4d6fe153ea673427b21f189db",
            Checksums.hashFor(sums, "atomic-launcher-v1.0.0.apk"),
        )
    }

    @Test
    fun `a file not in the list is not guessed at`() {
        assertNull(Checksums.hashFor(sums, "atomic-launcher-v2.0.0.apk"))
    }

    @Test
    fun `an upper-case hash still compares as lower-case`() {
        val upper = "8285C125F2C836B7A868D7F35715F6A514E5CFB4D6FE153EA673427B21F189DB  atomic-launcher-v1.0.0.apk"
        assertEquals(
            "8285c125f2c836b7a868d7f35715f6a514e5cfb4d6fe153ea673427b21f189db",
            Checksums.hashFor(upper, "atomic-launcher-v1.0.0.apk"),
        )
    }

    @Test
    fun `the binary-mode asterisk some tools prefix a filename with is not part of the name`() {
        val binary = "8285c125f2c836b7a868d7f35715f6a514e5cfb4d6fe153ea673427b21f189db *atomic-launcher-v1.0.0.apk"
        assertEquals(
            "8285c125f2c836b7a868d7f35715f6a514e5cfb4d6fe153ea673427b21f189db",
            Checksums.hashFor(binary, "atomic-launcher-v1.0.0.apk"),
        )
    }
}
