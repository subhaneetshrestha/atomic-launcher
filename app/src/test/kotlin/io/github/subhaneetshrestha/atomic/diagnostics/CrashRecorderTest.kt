package io.github.subhaneetshrestha.atomic.diagnostics

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashRecorderTest {
    private val dir: File = Files.createTempDirectory("crash").toFile()
    private val environment =
        CrashEnvironment(
            appVersion = "0.1.0-debug",
            versionCode = 1,
            androidRelease = "16",
            sdkInt = 36,
            device = "Google sdk_gphone64_x86_64",
        )
    private val recorder =
        CrashRecorder(dir, clock = { Instant.parse("2026-09-06T10:15:30Z") }, environment = environment)
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @AfterTest
    fun restore() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        dir.deleteRecursively()
    }

    @Test
    fun `a recorded crash can be read back with the environment and the stack trace, and cleared`() {
        assertNull(recorder.lastReport())

        recorder.record(Thread.currentThread(), IllegalStateException("badge count went negative"))

        val report = recorder.lastReport()!!
        assertTrue(report.startsWith("atomic 0.1.0-debug (1)"), report)
        assertTrue("Android 16 (API 36), Google sdk_gphone64_x86_64" in report, report)
        assertTrue("2026-09-06T10:15:30Z" in report, report)
        assertTrue("java.lang.IllegalStateException: badge count went negative" in report, report)
        assertTrue("CrashRecorderTest" in report, "the stack trace names the origin")

        recorder.record(Thread.currentThread(), RuntimeException("second"))
        assertTrue(
            "second" in recorder.lastReport()!! && "badge count" !in recorder.lastReport()!!,
            "only the last crash is kept",
        )

        recorder.clear()
        assertNull(recorder.lastReport())
    }

    @Test
    fun `installing records uncaught exceptions and still hands them to the previous handler`() {
        val seen = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> seen += error }

        recorder.install()
        val error = RuntimeException("boom")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

        assertEquals(
            listOf<Throwable>(error),
            seen,
            "the system's handler still runs, so Android shows its dialog and kills the process",
        )
        assertTrue("boom" in recorder.lastReport()!!)
    }
}
