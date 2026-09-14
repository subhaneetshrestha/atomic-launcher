package io.github.subhaneetshrestha.atomic.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.github.subhaneetshrestha.atomic.background.ImageFetcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Against a real https server standing in for the GitHub API, because the comparison is the point
 * and it has to be exercised with a real answer shaped like one — not a hand-built [GitHubRelease]
 * that skips whatever `fetchIndex` and the JSON decoder do to a real HTTP response.
 */
class UpdateCheckerTest {
    private lateinit var server: HttpsServer
    private val fetcher = ImageFetcher("atomic-test", sleep = {})

    private fun checker(): UpdateChecker = UpdateChecker(fetcher, apiBase = "https://localhost:${server.address.port}")

    @BeforeEach
    fun start() {
        val password = "changeit".toCharArray()
        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                UpdateCheckerTest::class.java.getResourceAsStream("/localhost.p12").use { load(it, password) }
            }
        val keys =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
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
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private fun serve(
        path: String,
        body: String,
    ) {
        server.createContext(path) { exchange ->
            exchange.use {
                val bytes = body.toByteArray()
                it.responseHeaders.add("Content-Type", "application/json")
                it.sendResponseHeaders(200, bytes.size.toLong())
                it.responseBody.write(bytes)
            }
        }
    }

    private inline fun HttpExchange.use(body: (HttpExchange) -> Unit) {
        try {
            body(this)
        } finally {
            close()
        }
    }

    private fun release(
        tag: String,
        publishedAt: String = "2026-09-13T12:00:00Z",
    ) = """
        {"tag_name":"$tag","published_at":"$publishedAt","html_url":"https://example/r",
         "assets":[{"name":"atomic-launcher-$tag.apk","browser_download_url":"https://example/apk"},
                    {"name":"SHA256SUMS","browser_download_url":"https://example/sums"}]}
        """.trimIndent()

    @Test
    fun `a newer tagged release is offered`() {
        serve("/releases/latest", release("v1.1.0"))
        val outcome = assertIs<UpdateChecker.Outcome.Available>(checker().check(edge = false, 10000, 0))
        assertEquals("v1.1.0", outcome.label)
        assertEquals("https://example/apk", outcome.apkUrl)
        assertEquals("https://example/sums", outcome.checksumsUrl)
    }

    @Test
    fun `the same or an older release is not an update`() {
        serve("/releases/latest", release("v1.0.0"))
        assertEquals(UpdateChecker.Outcome.UpToDate, checker().check(edge = false, 10000, 0))
        serve("/releases/latest", release("v0.9.9"))
        assertEquals(UpdateChecker.Outcome.UpToDate, checker().check(edge = false, 10000, 0))
    }

    @Test
    fun `a release whose tag is not a version is reported, not guessed at`() {
        serve("/releases/latest", release("not-a-version"))
        assertIs<UpdateChecker.Outcome.Failed>(checker().check(edge = false, 10000, 0))
    }

    @Test
    fun `an edge release published well after this apk was installed is offered`() {
        val installedAt = UpdateChecker.parseIso8601("2026-09-13T00:00:00Z")!!
        serve("/releases/tags/edge", release("edge", publishedAt = "2026-09-13T12:00:00Z"))
        val outcome = assertIs<UpdateChecker.Outcome.Available>(checker().check(edge = true, 0, installedAt))
        assertEquals("https://example/apk", outcome.apkUrl)
    }

    @Test
    fun `an edge release from moments after install is not offered`() {
        val installedAt = UpdateChecker.parseIso8601("2026-09-13T12:00:00Z")!!
        serve("/releases/tags/edge", release("edge", publishedAt = "2026-09-13T12:01:00Z"))
        assertEquals(UpdateChecker.Outcome.UpToDate, checker().check(edge = true, 0, installedAt))
    }

    @Test
    fun `an edge release from before this apk was installed is not offered`() {
        val installedAt = UpdateChecker.parseIso8601("2026-09-14T00:00:00Z")!!
        serve("/releases/tags/edge", release("edge", publishedAt = "2026-09-13T12:00:00Z"))
        assertEquals(UpdateChecker.Outcome.UpToDate, checker().check(edge = true, 0, installedAt))
    }
}
