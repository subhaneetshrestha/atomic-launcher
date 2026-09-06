package io.github.subhaneetshrestha.atomic.home

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.subhaneetshrestha.atomic.AtomicApp
import io.github.subhaneetshrestha.atomic.apps.AppLauncher
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.settings.SettingsSource
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * The single activity. It is the HOME activity, so it must never finish on Back, must survive
 * being recreated with no saved state (stateNotNeeded), and receives the Home key through
 * onNewIntent while an instance exists.
 */
class HomeActivity : ComponentActivity() {
    private val app: AtomicApp get() = application as AtomicApp
    private val repository: AppRepository get() = app.appRepository
    private val settings: SettingsSource get() = app.settingsSource

    private lateinit var applier: ThemeApplier
    private lateinit var root: HomeRootView
    private lateinit var list: HomeListView
    private lateinit var banner: DefaultHomeBanner
    private lateinit var launcher: AppLauncher
    private lateinit var defaultHome: DefaultHomePrompt

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshBanner()
        }
    private val snapshotListener = AppRepository.Listener { render() }
    private val settingsListener = SettingsSource.Listener { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.d(TAG) { "onCreate" }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 28) {
            // Draw into the cutout area on every API level, matching the target-35+ enforcement.
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            window.attributes = params
        }

        applier = ThemeApplier(this)
        launcher = AppLauncher(this, repository)
        defaultHome = DefaultHomePrompt(this)

        list =
            HomeListView(this, applier).apply {
                onRowClick = { entry, view -> launcher.launch(entry, view) }
            }
        banner =
            DefaultHomeBanner(this, applier).apply {
                setOnClickListener { defaultHome.request(roleRequest) }
            }
        root =
            HomeRootView(this).apply {
                setList(list, applier.verticalGravity(settings.current.verticalPosition))
                addFooter(banner)
            }
        setContentView(root)

        // Always enabled: Back dismisses overlays (none yet) and otherwise does nothing. This keeps
        // an OnBackInvokedCallback registered on API 33+, so Back can never finish the home task.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Logs.d(TAG) { "back: nothing to dismiss" }
                }
            },
        )
        render()
    }

    override fun onStart() {
        super.onStart()
        repository.addListener(snapshotListener)
        settings.addListener(settingsListener)
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshBanner()
        repository.ensureFresh(resources.configuration.locales)
    }

    override fun onStop() {
        repository.removeListener(snapshotListener)
        settings.removeListener(settingsListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val isHomePress = intent.hasCategory(Intent.CATEGORY_HOME)
        Logs.d(TAG) { "onNewIntent home=$isHomePress" }
        if (isHomePress) resetToHome()
    }

    /** The Home key while we are already showing: later phases dismiss overlays and scroll to top here. */
    private fun resetToHome() = Unit

    private fun render() {
        val current = settings.current
        list.render(HomeListModel.build(repository.current.entries, current), current)
        root.positionList(applier.verticalGravity(current.verticalPosition))
    }

    private fun refreshBanner() {
        banner.visibility = if (defaultHome.isDefaultHome()) View.GONE else View.VISIBLE
    }

    private companion object {
        const val TAG = "HomeActivity"
    }
}
