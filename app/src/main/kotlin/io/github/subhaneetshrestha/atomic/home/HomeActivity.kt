package io.github.subhaneetshrestha.atomic.home

import android.content.Intent
import android.content.res.Configuration
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
import androidx.core.view.WindowCompat
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
import io.github.subhaneetshrestha.atomic.background.BackgroundController
import io.github.subhaneetshrestha.atomic.background.BackgroundView
import io.github.subhaneetshrestha.atomic.background.Legibility
import io.github.subhaneetshrestha.atomic.core.theme.BackgroundMode
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.ColorValue
import io.github.subhaneetshrestha.atomic.core.theme.EdgeExclusion
import io.github.subhaneetshrestha.atomic.core.theme.InfoPosition
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.Theme
import io.github.subhaneetshrestha.atomic.core.theme.ThemeResolver
import io.github.subhaneetshrestha.atomic.gestures.Dispatch
import io.github.subhaneetshrestha.atomic.gestures.GestureDispatcher
import io.github.subhaneetshrestha.atomic.gestures.GestureEvent
import io.github.subhaneetshrestha.atomic.gestures.Haptics
import io.github.subhaneetshrestha.atomic.home.info.InfoLinesView
import io.github.subhaneetshrestha.atomic.notifications.BadgeStore
import io.github.subhaneetshrestha.atomic.notifications.NotificationAccess
import io.github.subhaneetshrestha.atomic.search.AppSearchIndex
import io.github.subhaneetshrestha.atomic.search.SearchOverlay
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
    private lateinit var backdrop: BackgroundView
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
    private lateinit var searchOverlay: SearchOverlay
    private var infoPosition: InfoPosition? = null
    private var visibleRows: List<AppKey> = emptyList()

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshBanner() }
    private val snapshotListener = AppRepository.Listener { render() }
    private val badgeListener = BadgeStore.Listener { render() }
    private val backgroundListener = BackgroundController.Listener { render() }
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
                badgeCount = { entry -> atomicApp.badges.countFor(entry.key.packageName, entry.key.userSerial) }
            }
        infoLines = InfoLinesView(this, applier).apply { onSurface = ::onSurfaceTouched }
        block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        banner = DefaultHomeBanner(this, applier).apply { setOnClickListener { defaultHome.request(roleRequest) } }
        backdrop =
            BackgroundView(this).apply {
                onSize = { width, height -> atomicApp.background.onWindowSize(width, height) }
            }
        searchOverlay =
            SearchOverlay(this, applier).apply {
                onLaunch = ::launchByKey
                onWebSearch = { query -> if (!runner.searchWeb(query)) toast(R.string.action_no_app) }
                onClose = { root.searchOpen = false }
            }
        root =
            HomeRootView(this).apply {
                setBackgroundLayer(backdrop)
                setBlock(block, applier.verticalGravity(settings.current.verticalPosition))
                addFooter(banner)
                setOverlay(searchOverlay)
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
                    if (searchOverlay.isOpen) searchOverlay.close() else Logs.d(TAG) { "back: nothing to dismiss" }
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
        atomicApp.badges.store.addListener(badgeListener)
        atomicApp.background.addListener(backgroundListener)
        infoLines.onStart()
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshBanner()
        // The user may have granted or revoked notification access while we were away.
        atomicApp.badges.onAccessChanged(NotificationAccess.isGranted(this))
        repository.ensureFresh(resources.configuration.locales)
        // Quietly, and only if the interval has run out: a phone that decided this app is idle
        // may not have run the scheduled job for days.
        atomicApp.background.onHomeResumed()
    }

    override fun onStop() {
        // Leaving the launcher puts the screen back to its resting state, so coming back is never
        // a half-finished search.
        searchOverlay.close()
        repository.removeListener(snapshotListener)
        settings.removeDocumentListener(documentListener)
        atomicApp.badges.store.removeListener(badgeListener)
        atomicApp.background.removeListener(backgroundListener)
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

    /** The Home key while the launcher is already showing: put the screen back to its resting state. */
    private fun resetToHome() {
        if (searchOverlay.isOpen) searchOverlay.close()
    }

    override fun openSearch() = showSearch(withKeyboard = true)

    override fun openDrawer() = showSearch(withKeyboard = false)

    override fun nextBackground(): Boolean = atomicApp.background.requestNext()

    private fun showSearch(withKeyboard: Boolean) {
        // Built on the way in: the list is small, so a rename or a new app is never stale.
        val index = AppSearchIndex.build(repository.current.entries, settings.current)
        root.searchOpen = true
        searchOverlay.open(index, settings.current, atomicApp.resolvedColors(this), withKeyboard)
    }

    private fun launchByKey(key: AppKey) {
        val entry = repository.current.entries.firstOrNull { it.key == key } ?: return
        launcher.launch(entry, null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // After a rotation the keyboard has to be asked for again.
        searchOverlay.refreshKeyboard()
    }

    private fun render() {
        val view = settings.current
        val document = settings.settings
        val theme = document.theme
        val themeColors = atomicApp.resolvedColors(this)
        val legibility = atomicApp.background.legibility.takeIf { theme.background.mode == BackgroundMode.COLLECTION }
        val colors = themeColors.over(legibility, theme)
        applyWindowBackground(theme, themeColors)
        applySystemBars(legibility, themeColors)
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

    /**
     * The window itself is the colour of the theme, so the first frame after a cold start is
     * already right; in wallpaper mode it is transparent instead and the device wallpaper shows
     * through, which costs the launcher no memory and needs no permission.
     */
    private fun applyWindowBackground(
        theme: Theme,
        colors: ResolvedColors,
    ) {
        val wallpaper = theme.background.mode == BackgroundMode.WALLPAPER
        if (wallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
        window.setBackgroundDrawable(ColorDrawable(if (wallpaper) Color.TRANSPARENT else colors.background))
        backdrop.apply(theme.background, colors.background)
        backdrop.setImage(atomicApp.background.image, animate = true)
    }

    /** Dark icons on a light background, whether that background is the theme's or a photograph. */
    private fun applySystemBars(
        legibility: Legibility?,
        colors: ResolvedColors,
    ) {
        val controller = WindowCompat.getInsetsController(window, root)
        val light = ThemeResolver.relativeLuminance(colors.background) > ThemeResolver.LUMINANCE_THRESHOLD
        controller.isAppearanceLightStatusBars = legibility?.darkStatusIcons ?: light
        controller.isAppearanceLightNavigationBars = legibility?.darkNavIcons ?: light
    }

    /**
     * Over an image the theme's own text colours may be invisible, so the names, the info lines
     * and an automatic badge take black or white from what the image turned out to look like.
     */
    private fun ResolvedColors.over(
        legibility: Legibility?,
        theme: Theme,
    ): ResolvedColors {
        if (legibility == null) return this
        val front = if (legibility.darkText) BLACK_RGB else WHITE_RGB
        val behind = if (legibility.darkText) WHITE_RGB else BLACK_RGB

        fun tint(
            color: Int,
            rgb: Int,
        ) = (color and ALPHA_MASK) or rgb
        return copy(
            text = tint(text, front),
            textSecondary = tint(textSecondary, front),
            accent = tint(accent, front),
            badgeBackground =
                if (theme.badge.background ==
                    ColorValue.AUTO
                ) {
                    tint(badgeBackground, front)
                } else {
                    badgeBackground
                },
            badgeText = if (theme.badge.text == ColorValue.AUTO) tint(badgeText, behind) else badgeText,
        )
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
        const val ALPHA_MASK = 0xFF000000.toInt()
        const val BLACK_RGB = 0x000000
        const val WHITE_RGB = 0xFFFFFF
    }
}
