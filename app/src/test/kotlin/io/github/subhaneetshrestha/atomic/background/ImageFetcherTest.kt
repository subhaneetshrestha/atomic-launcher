package io.github.subhaneetshrestha.atomic.background

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The fetcher against a real https server, because every rule it enforces is a rule about how
 * somebody else's server behaves: where it redirects, how much it sends, and what it answers when
 * the address is wrong.
 */
class ImageFetcherTest {
    private lateinit var server: HttpsServer
    private lateinit var tempDir: File
    private val fetcher = ImageFetcher("atomic-test", sleep = {})

    private val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

    private fun url(path: String) = "https://localhost:${server.address.port}$path"

    @BeforeEach
    fun start() {
        val password = "changeit".toCharArray()
        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                ImageFetcherTest::class.java.getResourceAsStream("/localhost.p12").use { load(it, password) }
            }
        val keys =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
        // The certificate has to be a trust anchor in its own right; a private-key entry is not one.
        val trustStore =
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setCertificateEntry("localhost", keyStore.getCertificate("localhost"))
            }
        val trust =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trust.trustManagers, null) }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        server =
            HttpsServer.create(InetSocketAddress("localhost", 0), 0).apply {
                httpsConfigurator = HttpsConfigurator(context)
                start()
            }
        tempDir = createTempDirectory("fetcher").toFile()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
        tempDir.deleteRecursively()
    }

    private fun serve(
        path: String,
        handler: (HttpExchange) -> Unit,
    ) {
        server.createContext(path) { exchange -> exchange.use { handler(it) } }
    }

    private inline fun HttpExchange.use(body: (HttpExchange) -> Unit) {
        try {
            body(this)
        } finally {
            close()
        }
    }

    private fun HttpExchange.send(
        code: Int,
        body: ByteArray,
        contentType: String? = null,
    ) {
        contentType?.let { responseHeaders.add("Content-Type", it) }
        sendResponseHeaders(code, body.size.toLong())
        responseBody.write(body)
    }

    @Test
    fun `a list comes back with what the server called it`() {
        serve("/list.txt") { it.send(200, "https://example.org/a.jpg\n".toByteArray(), "text/plain") }
        val outcome = assertIs<ImageFetcher.Outcome.Index>(fetcher.fetchIndex(url("/list.txt")))
        assertEquals("https://example.org/a.jpg\n", outcome.body)
        assertTrue(outcome.contentType!!.startsWith("text/plain"))
    }

    @Test
    fun `an address that is gone is a permanent failure, a server having a bad day is not`() {
        serve("/gone") { it.send(404, ByteArray(0)) }
        serve("/broken") { it.send(500, ByteArray(0)) }
        val gone = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchIndex(url("/gone")))
        assertTrue(gone.permanent, "a 404 will still be a 404 tomorrow")
        val broken = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchIndex(url("/broken")))
        assertFalse(broken.permanent, "a 500 is worth trying again later")
    }

    @Test
    fun `redirects are followed up to five hops and no further`() {
        for (hop in 1..ImageFetcher.MAX_REDIRECTS) {
            serve("/hop$hop") {
                it.responseHeaders.add("Location", url("/hop${hop + 1}"))
                it.send(302, ByteArray(0))
            }
        }
        serve("/hop${ImageFetcher.MAX_REDIRECTS + 1}") {
            it.send(200, "https://example.org/a.jpg".toByteArray(), "text/plain")
        }
        assertIs<ImageFetcher.Outcome.Index>(fetcher.fetchIndex(url("/hop1")), "five hops is allowed")

        serve("/loop") {
            it.responseHeaders.add("Location", url("/loop"))
            it.send(302, ByteArray(0))
        }
        val looped = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchIndex(url("/loop")))
        assertTrue(looped.reason.contains("redirected more than"), looped.reason)
    }

    @Test
    fun `a redirect off https is refused`() {
        serve("/downgrade") {
            it.responseHeaders.add("Location", "http://localhost/list.txt")
            it.send(302, ByteArray(0))
        }
        val outcome = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchIndex(url("/downgrade")))
        assertTrue(outcome.permanent)
        assertTrue(outcome.reason.contains("may not follow"), outcome.reason)
    }

    @Test
    fun `a list longer than a megabyte is refused rather than stored`() {
        serve("/huge") { it.send(200, ByteArray(ImageFetcher.MAX_INDEX_BYTES + 1024) { 'a'.code.toByte() }) }
        val outcome = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchIndex(url("/huge")))
        assertTrue(outcome.reason.contains("KiB"), outcome.reason)
    }

    @Test
    fun `an image the server admits is too large is never downloaded`() {
        val target = File(tempDir, "big.img")
        serve("/big") {
            it.responseHeaders.add("Content-Type", "image/jpeg")
            it.sendResponseHeaders(200, ImageFetcher.MAX_IMAGE_BYTES + 1)
            // The body is never written: the fetcher gives up on the declared length.
        }
        val outcome = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchImage(url("/big"), target))
        assertTrue(outcome.permanent)
        assertFalse(target.exists(), "nothing is left behind")
    }

    @Test
    fun `an image that grows past the cap while arriving is cut off and thrown away`() {
        val target = File(tempDir, "growing.img")
        serve("/growing") {
            it.responseHeaders.add("Content-Type", "image/jpeg")
            // Length unknown, as a chunked response is: the cap has to be enforced while reading.
            it.sendResponseHeaders(200, 0)
            val chunk = ByteArray(64 * 1024)
            repeat(((ImageFetcher.MAX_IMAGE_BYTES / chunk.size) + 4).toInt()) { _ ->
                runCatching { it.responseBody.write(chunk) }
            }
        }
        val outcome = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchImage(url("/growing"), target))
        assertTrue(outcome.reason.contains("MiB"), outcome.reason)
        assertFalse(target.exists())
    }

    @Test
    fun `an image arrives whole and is left where it was asked for`() {
        val body = jpegHeader + ByteArray(2048)
        serve("/photo.jpg") { it.send(200, body, "image/jpeg") }
        val target = File(tempDir, "photo.img")
        val outcome = assertIs<ImageFetcher.Outcome.Image>(fetcher.fetchImage(url("/photo.jpg"), target))
        assertEquals(body.size.toLong(), outcome.bytes)
        assertEquals(body.size.toLong(), target.length())
        assertTrue(target.readBytes().take(4) == body.take(4))
    }

    @Test
    fun `a download that stops short of what was promised is the network, not the address`() {
        serve("/half.jpg") {
            it.responseHeaders.add("Content-Type", "image/jpeg")
            it.sendResponseHeaders(200, 4096)
            runCatching { it.responseBody.write(jpegHeader + ByteArray(1000)) }
        }
        val target = File(tempDir, "half.img")
        val outcome = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchImage(url("/half.jpg"), target))
        assertFalse(outcome.permanent, "a broken connection must never blacklist a good address")
        assertFalse(target.exists(), "half a file is not kept")
    }

    @Test
    fun `the first bytes are kept as they arrived, so a signature survives`() {
        val body = jpegHeader + ByteArray(64) { it.toByte() }
        serve("/sniff") { it.send(200, body, "application/octet-stream") }
        val outcome = assertIs<ImageFetcher.Outcome.Index>(fetcher.fetchIndex(url("/sniff")))
        assertEquals(jpegHeader.toList(), outcome.head.take(4))
    }

    @Test
    fun `an empty answer is a failure, not an empty wallpaper`() {
        serve("/empty.jpg") { it.send(200, ByteArray(0), "image/jpeg") }
        val target = File(tempDir, "empty.img")
        assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchImage(url("/empty.jpg"), target))
        assertFalse(target.exists())
    }

    @Test
    fun `a list that has not changed costs only its headers`() {
        serve("/versioned.txt") { exchange ->
            if (exchange.requestHeaders.getFirst("If-None-Match") == "\"v1\"") {
                exchange.send(304, ByteArray(0))
            } else {
                exchange.responseHeaders.add("ETag", "\"v1\"")
                exchange.send(200, "https://example.org/a.jpg".toByteArray(), "text/plain")
            }
        }
        val first = assertIs<ImageFetcher.Outcome.Index>(fetcher.fetchIndex(url("/versioned.txt")))
        assertEquals("\"v1\"", first.etag)
        assertEquals(ImageFetcher.Outcome.Unchanged, fetcher.fetchIndex(url("/versioned.txt"), etag = first.etag))
    }

    @Test
    fun `an address the rules refuse never reaches the network`() {
        val plain = assertIs<ImageFetcher.Outcome.Failed>(fetcher.fetchIndex("http://localhost/list.txt"))
        assertTrue(plain.permanent)
        assertTrue(plain.reason.contains("https"), plain.reason)
    }
}
