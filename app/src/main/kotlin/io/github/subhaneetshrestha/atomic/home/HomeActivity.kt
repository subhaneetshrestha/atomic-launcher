package io.github.subhaneetshrestha.atomic.home

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.ThemedActivity
import io.github.subhaneetshrestha.atomic.actions.ActionAvailability
import io.github.subhaneetshrestha.atomic.actions.ActionGrant
import io.github.subhaneetshrestha.atomic.actions.ActionLabels
import io.github.subhaneetshrestha.atomic.actions.ActionRunner
import io.github.subhaneetshrestha.atomic.actions.AndroidActionEnvironment
import io.github.subhaneetshrestha.atomic.actions.Availability
import io.github.subhaneetshrestha.atomic.actions.BuiltinActions
import io.github.subhaneetshrestha.atomic.actions.LauncherSurfaces
import io.github.subhaneetshrestha.atomic.apps.AppActions
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.apps.AppLauncher
import io.github.subhaneetshrestha.atomic.apps.AppRepository
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.EdgeExclusion
import io.github.subhaneetshrestha.atomic.core.theme.InfoPosition
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.gestures.Dispatch
import io.github.subhaneetshrestha.atomic.gestures.GestureDispatcher
import io.github.subhaneetshrestha.atomic.gestures.GestureEvent
import io.github.subhaneetshrestha.atomic.gestures.Haptics
import io.github.subhaneetshrestha.atomic.home.info.InfoLinesView
import io.github.subhaneetshrestha.atomic.settings.SettingsActivity
import io.github.subhaneetshrestha.atomic.settings.SettingsRepository
import io.github.subhaneetshrestha.atomic.setup.SetupActivity
import io.github.subhaneetshrestha.atomic.system.NoSystemActions
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * The single home activity. It must never finish on Back, must survive being recreated with no
 * saved state (stateNotNeeded), and receives the Home key through onNewIntent while it exists.
 */
class HomeActivity :
    ThemedActivity(),
    LauncherSurfaces {
    private val repository: AppRepository get() = atomicApp.appRepository
    private val settings: SettingsRepository get() = atomicApp.settingsRepository

    private lateinit var applier: ThemeApplier
    private lateinit var root: HomeRootView
    private lateinit var block: LinearLayout
    private lateinit var list: HomeListView
    private lateinit var infoLines: InfoLinesView
    private lateinit var banner: DefaultHomeBanner
    private lateinit var launcher: AppLauncher
    private lateinit var defaultHome: DefaultHomePrompt
    private lateinit var appMenu: AppMenu
    private lateinit var environment: AndroidActionEnvironment
    private lateinit var runner: ActionRunner
    private lateinit var dispatcher: GestureDispatcher
    private lateinit var haptics: Haptics
    private var infoPosition: InfoPosition? = null
    private var visibleRows: List<AppKey> = emptyList()

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshBanner() }
    private val snapshotListener = AppRepository.Listener { render() }
    private val documentListener: (Settings, Settings) -> Unit = { old, new ->
        if (old.appearance.nightMode != new.appearance.nightMode) recreate() else render()
    }

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

        val appActions = AppActions(this, repository)
        appMenu = AppMenu(this, settings, appActions)
        environment =
            AndroidActionEnvironment(this, repository, NoSystemActions, BuiltinActions.BUILT_SURFACES) {
                defaultHome.isDefaultHome()
            }
        runner = ActionRunner(this, environment, this, repository, launcher, appActions, NoSystemActions)
        dispatcher = GestureDispatcher(ActionAvailability(environment))
        list =
            HomeListView(this, applier).apply {
                onRowClick = { entry, view -> launcher.launch(entry, view) }
                onRowLongClick = { entry, view ->
                    appMenu.show(entry, view, visibleRows)
                    true
                }
            }
        infoLines = InfoLinesView(this, applier).apply { onSurface = ::onSurfaceTouched }
        block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        banner = DefaultHomeBanner(this, applier).apply { setOnClickListener { defaultHome.request(roleRequest) } }
        root =
            HomeRootView(this).apply {
                setBlock(block, applier.verticalGravity(settings.current.verticalPosition))
                addFooter(banner)
                onGesture = ::onGesture
            }
        haptics = Haptics(root)
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
        offerSetup()
    }

    private fun offerSetup() {
        if (settings.settings.app.setupDone || atomicApp.setupOffered) return
        atomicApp.setupOffered = true
        startActivity(Intent(this, SetupActivity::class.java))
    }

    override fun onStart() {
        super.onStart()
        repository.addListener(snapshotListener)
        settings.addDocumentListener(documentListener)
        infoLines.onStart()
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshBanner()
        repository.ensureFresh(resources.configuration.locales)
    }

    override fun onStop() {
        repository.removeListener(snapshotListener)
        settings.removeDocumentListener(documentListener)
        infoLines.onStop()
        settings.flush()
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
        val view = settings.current
        val document = settings.settings
        val colors = atomicApp.resolvedColors(this)
        window.setBackgroundDrawable(ColorDrawable(colors.background))
        arrangeBlock(document.homeInfo.position)
        val h = applier.dp(view.horizontalPaddingDp)
        val v = applier.dp(view.verticalPaddingDp)
        block.setPadding(h, v, h, v)
        val rows = HomeListModel.build(repository.current.entries, view)
        visibleRows = rows.map { it.entry.key }
        list.render(rows, view, colors)
        infoLines.bind(document.homeInfo, document.theme, view, colors)
        banner.applyColors(colors)
        haptics.enabled = document.gestures.haptics
        root.takeOverSideEdges = document.gestures.edgeExclusion == EdgeExclusion.BOTH
        root.positionBlock(applier.verticalGravity(view.verticalPosition))
    }

    private fun arrangeBlock(position: InfoPosition) {
        if (infoPosition == position) return
        infoPosition = position
        block.removeAllViews()
        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        val ordered = if (position == InfoPosition.ABOVE) listOf(infoLines, list) else listOf(list, infoLines)
        for (child in ordered) block.addView(child, LinearLayout.LayoutParams(params))
    }

    override fun openLauncherSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun chooseDefaultLauncher() {
        defaultHome.request(roleRequest)
    }

    private fun onGesture(event: GestureEvent) {
        haptics.onGesture(event)
        carryOut(dispatcher.dispatch(event, settings.settings))
    }

    private fun onSurfaceTouched(surface: BindingSurface) {
        carryOut(dispatcher.dispatch(surface, settings.settings))
    }

    private fun carryOut(outcome: Dispatch) {
        when (outcome) {
            Dispatch.Ignore -> {
                Unit
            }

            is Dispatch.Run -> {
                if (!runner.run(outcome.action)) {
                    haptics.rejected()
                    toast(R.string.action_failed)
                }
            }

            // Nothing is enabled behind the user's back: say what it would take.
            is Dispatch.OfferGrant -> {
                haptics.rejected()
                toast(
                    when (outcome.grant) {
                        ActionGrant.ACCESSIBILITY -> R.string.action_needs_accessibility
                        ActionGrant.DEVICE_ADMIN -> R.string.action_needs_device_admin
                    },
                )
            }

            is Dispatch.Explain -> {
                haptics.rejected()
                ActionLabels.reason(this, outcome.availability)?.let(::toast)
            }
        }
    }

    private fun toast(message: Int) = toast(getString(message))

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun refreshBanner() {
        banner.visibility = if (defaultHome.isDefaultHome()) View.GONE else View.VISIBLE
    }

    private companion object {
        const val TAG = "HomeActivity"
    }
}
