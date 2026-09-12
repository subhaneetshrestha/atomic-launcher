package io.github.subhaneetshrestha.atomic.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * At most [max] bytes of a stream somebody else is filling. Everything the launcher reads from
 * outside — a backup, a theme, a document shared to it — is bounded, because a stream that never
 * ends would otherwise be read until the app is killed for it.
 */
fun InputStream.readAtMost(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < max) {
        val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
