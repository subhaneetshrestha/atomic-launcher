package io.github.subhaneetshrestha.atomic.background

import android.app.job.JobParameters
import android.app.job.JobService
import io.github.subhaneetshrestha.atomic.AtomicApp
import io.github.subhaneetshrestha.atomic.util.Threads

/**
 * The scheduled turn of the background. Android starts this on the main thread, so the work is
 * handed straight to the launcher's own I/O thread and the job is finished from there.
 */
class BackgroundJobService : JobService() {
    @Volatile
    private var cancelled = false

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as? AtomicApp ?: return false
        cancelled = false
        Threads.io.post {
            val retry = app.background.runScheduled()
            if (!cancelled) jobFinished(params, retry)
        }
        return true
    }

    /** Android took the job away (the network went, say): let it come back on its own schedule. */
    override fun onStopJob(params: JobParameters): Boolean {
        cancelled = true
        return true
    }
}
