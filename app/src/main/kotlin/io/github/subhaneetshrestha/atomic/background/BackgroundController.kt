package io.github.subhaneetshrestha.atomic.background

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import io.github.subhaneetshrestha.atomic.core.collections.BackgroundState
import io.github.subhaneetshrestha.atomic.core.theme.Background
import io.github.subhaneetshrestha.atomic.core.theme.BackgroundMode
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.settings.SettingsRepository
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import java.util.concurrent.CopyOnWriteArrayList

/** What the settings screen says about the background, without reading the disk on the main thread. */
data class BackgroundStatus(
    val currentUrl: String? = null,
    val changedAt: Long = 0,
    val imageCount: Int = 0,
    val lastError: String? = null,
    val lastSkip: String? = null,
    val working: Boolean = false,
)

/**
 * Owns the background for the whole process: the image on screen, what the text over it has to
 * do to stay readable, and when to go and get the next one. One of these exists per process, so
 * a recreated home screen (a rotation, a night-mode change) shows the image it already had
 * instead of fetching another one.
 *
 * Everything that touches the network or the disk runs on [Threads.io]; only the handover is
 * posted to the main thread.
 */
class BackgroundController(
    context: Context,
    private val settings: SettingsRepository,
) {
    fun interface Listener {
        fun onBackgroundChanged()
    }

    private val appContext = context.applicationContext

    // Asking for the private directory touches the disk, and this is built while the home app is
    // starting, so both wait until something needs them — which is always on the I/O thread.
    private val store by lazy { ImageStore(appContext.filesDir) }

    private val engine by lazy {
        BackgroundEngine(store, ImageFetcher(userAgent(appContext)), NetworkPolicy(appContext))
    }
    private val listeners = CopyOnWriteArrayList<Listener>()

    /** The image on screen, or null when there is none. Main thread. */
    var image: Bitmap? = null
        private set

    /** How the text over the image has to be drawn, or null when the theme decides. */
    var legibility: Legibility? = null
        private set

    var status: BackgroundStatus = BackgroundStatus()
        private set

    @Volatile
    private var windowWidth = 0

    @Volatile
    private var windowHeight = 0

    @Volatile
    private var working = false

    private var shownUrl: String? = null

    private val documentListener: (Settings, Settings) -> Unit = { old, new ->
        if (old.theme.background != new.theme.background) onConfigChanged(new.theme.background)
    }

    fun start() {
        settings.addDocumentListener(documentListener)
        val background = settings.settings.theme.background
        Threads.io.post {
            BackgroundScheduler.apply(appContext, background.collection, background.mode == BackgroundMode.COLLECTION)
            val state = store.readState()
            Threads.main.post { publishStatus(state) }
            if (background.mode == BackgroundMode.COLLECTION) loadCurrent(state)
        }
    }

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** The home window's size, which is the size the image is decoded to. */
    fun onWindowSize(
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0) return
        val same = width == windowWidth && height == windowHeight
        windowWidth = width
        windowHeight = height
        if (same || settings.settings.theme.background.mode != BackgroundMode.COLLECTION) return
        Threads.io.post { loadCurrent(store.readState()) }
    }

    /**
     * The home screen came back. Anything given back to a system short of memory is decoded again
     * here; then, if the interval has run out, the next image is fetched — quietly, and on wi-fi.
     */
    fun onHomeResumed() {
        if (settings.settings.theme.background.mode == BackgroundMode.COLLECTION && image == null) {
            Threads.io.post { loadCurrent(store.readState()) }
        }
        run(BackgroundEngine.Trigger.OPPORTUNISTIC)
    }

    /** The user asked for a different image now. Returns false when there is no collection to ask. */
    fun requestNext(): Boolean {
        val background = settings.settings.theme.background
        if (background.mode != BackgroundMode.COLLECTION || !background.collection.isConfigured) return false
        run(BackgroundEngine.Trigger.USER)
        return true
    }

    /** Called on [Threads.io] by the scheduled job; true asks Android to try again sooner. */
    fun runScheduled(): Boolean {
        val outcome = runNow(BackgroundEngine.Trigger.SCHEDULED)
        return outcome is BackgroundEngine.Outcome.Failed
    }

    /**
     * Merely being off screen is not a reason to let the image go: the launcher is back the moment
     * an app is closed, and decoding it again costs more than holding it. It goes when the device
     * is actually short of memory, or when this process is far enough down the list to be at risk.
     */
    fun onTrimMemory(level: Int) {
        val short =
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        if (!short) return
        Threads.main.post {
            if (image == null) return@post
            Logs.d(TAG) { "dropping the background image at trim level $level" }
            image = null
            shownUrl = null
            notifyListeners()
        }
    }

    private fun onConfigChanged(background: Background) {
        BackgroundScheduler.apply(appContext, background.collection, background.mode == BackgroundMode.COLLECTION)
        if (background.mode != BackgroundMode.COLLECTION) {
            image = null
            legibility = null
            shownUrl = null
            notifyListeners()
            return
        }
        Threads.io.post {
            val state = store.readState()
            if (state.index?.sourceUrl != background.collection.url) {
                // A different collection: what is on screen came from one the user has left.
                store.forget()
                Threads.main.post {
                    image = null
                    legibility = null
                    shownUrl = null
                    notifyListeners()
                }
                runNow(BackgroundEngine.Trigger.USER)
            } else {
                loadCurrent(state)
            }
        }
    }

    private fun run(trigger: BackgroundEngine.Trigger) {
        if (working) return
        working = true
        Threads.io.post {
            try {
                runNow(trigger)
            } finally {
                working = false
            }
        }
    }

    /** On [Threads.io]. Runs the engine and, when the image changed, decodes and publishes it. */
    private fun runNow(trigger: BackgroundEngine.Trigger): BackgroundEngine.Outcome {
        val background = settings.settings.theme.background
        if (background.mode != BackgroundMode.COLLECTION) return BackgroundEngine.Outcome.NotConfigured
        val outcome = engine.run(background.collection, trigger)
        val state = store.readState()
        when (outcome) {
            is BackgroundEngine.Outcome.Changed -> {
                publish(outcome.url, decode(), outcome.thumbnail, background, state)
            }

            else -> {
                Threads.main.post { publishStatus(state) }
            }
        }
        return outcome
    }

    /** On [Threads.io]. Shows the image already on disk, e.g. after a restart or a rotation. */
    private fun loadCurrent(state: BackgroundState) {
        val background = settings.settings.theme.background
        val url = state.currentUrl
        if (url == null || !store.current.isFile) {
            Threads.main.post { publishStatus(state) }
            return
        }
        publish(url, decode(), ImageDecoders.thumbnail(store.current), background, state)
    }

    private fun decode(): Bitmap? {
        val width = windowWidth
        val height = windowHeight
        if (width <= 0 || height <= 0) return null
        return ImageDecoders.decode(store.current, width, height)
    }

    private fun publish(
        url: String,
        bitmap: Bitmap?,
        thumbnail: Thumbnail?,
        background: Background,
        state: BackgroundState,
    ) {
        val aspect = if (windowHeight > 0) windowWidth.toFloat() / windowHeight else 0f
        val reading =
            if (background.autoTextColor && thumbnail != null) {
                ColorSampler.sample(thumbnail.pixels, thumbnail.width, thumbnail.height, aspect, background.dim)
            } else {
                null
            }
        Threads.main.post {
            if (bitmap != null) {
                image = bitmap
                shownUrl = url
            }
            legibility = reading
            publishStatus(state)
        }
    }

    private fun publishStatus(state: BackgroundState) {
        status =
            BackgroundStatus(
                currentUrl = state.currentUrl,
                changedAt = state.currentAt,
                imageCount = state.index?.urls?.size ?: 0,
                lastError = state.lastError,
                lastSkip = state.lastSkip,
                working = working,
            )
        notifyListeners()
    }

    private fun notifyListeners() {
        for (listener in listeners) listener.onBackgroundChanged()
    }

    private fun userAgent(context: Context): String =
        "atomic-launcher/${context.packageName} (Android ${android.os.Build.VERSION.RELEASE})"

    private companion object {
        const val TAG = "BackgroundController"
    }
}
