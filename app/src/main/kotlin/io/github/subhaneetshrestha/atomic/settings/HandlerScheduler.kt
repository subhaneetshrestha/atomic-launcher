package io.github.subhaneetshrestha.atomic.settings

import android.os.Handler
import io.github.subhaneetshrestha.atomic.core.theme.WriteScheduler

/** Runs the store's debounced write on a background Handler. */
class HandlerScheduler(
    private val handler: Handler,
) : WriteScheduler {
    override fun schedule(
        delayMs: Long,
        action: () -> Unit,
    ): () -> Unit {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return { handler.removeCallbacks(runnable) }
    }
}
