package io.github.subhaneetshrestha.atomic.settings

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.ThemedActivity
import io.github.subhaneetshrestha.atomic.actions.ActionAvailability
import io.github.subhaneetshrestha.atomic.actions.ActionLabels
import io.github.subhaneetshrestha.atomic.actions.AndroidActionEnvironment
import io.github.subhaneetshrestha.atomic.actions.Availability
import io.github.subhaneetshrestha.atomic.actions.BuiltinActions
import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinThemes
import io.github.subhaneetshrestha.atomic.core.theme.ConsentKind
import io.github.subhaneetshrestha.atomic.core.theme.DecodeResult
import io.github.subhaneetshrestha.atomic.core.theme.EdgeExclusion
import io.github.subhaneetshrestha.atomic.core.theme.HomeLimits
import io.github.subhaneetshrestha.atomic.core.theme.LinkRules
import io.github.subhaneetshrestha.atomic.core.theme.NightMode
import io.github.subhaneetshrestha.atomic.core.theme.PackageRef
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.SettingsCodec
import io.github.subhaneetshrestha.atomic.core.theme.SettingsEdits
import io.github.subhaneetshrestha.atomic.home.DefaultHomePrompt
import io.github.subhaneetshrestha.atomic.home.HomeListModel
import io.github.subhaneetshrestha.atomic.notifications.NotificationAccess
import io.github.subhaneetshrestha.atomic.settings.grant.Grants
import io.github.subhaneetshrestha.atomic.settings.grant.InstallSourceProbe
import io.github.subhaneetshrestha.atomic.settings.grant.Restriction
import io.github.subhaneetshrestha.atomic.system.NoSystemActions
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

/**
 * Launcher settings: one activity, a stack of list screens built from platform widgets and themed
 * with the current colours. Every change goes through SettingsEdits and the store; the home screen
 * picks it up through its document listener.
 */
class SettingsActivity : ThemedActivity() {
    private val settings: SettingsRepository get() = atomicApp.settingsRepository
    private val availability by lazy {
        ActionAvailability(
            AndroidActionEnvironment(this, atomicApp.appRepository, NoSystemActions, BuiltinActions.BUILT_SURFACES) {
                DefaultHomePrompt(this).isDefaultHome()
            },
        )
    }
    private lateinit var colors: ResolvedColors
    private lateinit var title: TextView
    private lateinit var container: FrameLayout
    private val stack = ArrayDeque<Screen>()

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> uri?.let(::exportTo) }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importFrom) }
    private val documentListener: (Settings, Settings) -> Unit = { old, new ->
        if (old.theme != new.theme || old.appearance != new.appearance) recreate() else stack.lastOrNull()?.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        colors = atomicApp.resolvedColors(this)
        window.decorView.setBackgroundColor(colors.background)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        title =
            TextView(this).apply {
                setTextColor(colors.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                val h = dp(20)
                setPadding(h, dp(16), h, dp(8))
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(56)
            }
        container = FrameLayout(this)
        root.addView(
            title,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        root.addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (stack.size > 1) pop() else finish()
                }
            },
        )
        val ids = savedInstanceState?.getIntArray(STATE_STACK) ?: intArrayOf(ScreenId.MENU.ordinal)
        val args = savedInstanceState?.getIntArray(STATE_ARGS) ?: IntArray(ids.size) { -1 }
        for (index in ids.indices.take(
            ids.size - 1,
        )) {
            stack.addLast(screenFor(ScreenId.entries[ids[index]], args[index]))
        }
        push(screenFor(ScreenId.entries[ids.last()], args.last()))
    }

    override fun onStart() {
        super.onStart()
        settings.addDocumentListener(documentListener)
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted or revoked access in Settings while we were away.
        atomicApp.badges.onAccessChanged(NotificationAccess.isGranted(this))
        when (val top = stack.lastOrNull()) {
            is GrantScreen -> top.onReturn()
            else -> top?.refresh()
        }
    }

    override fun onStop() {
        settings.removeDocumentListener(documentListener)
        settings.flush()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_STACK, stack.map { it.id.ordinal }.toIntArray())
        outState.putIntArray(STATE_ARGS, stack.map { it.arg }.toIntArray())
    }

    private fun screenFor(
        id: ScreenId,
        arg: Int = -1,
    ): Screen =
        when (id) {
            ScreenId.MENU -> MenuScreen()
            ScreenId.HOME_APPS -> HomeAppsScreen()
            ScreenId.HIDDEN_APPS -> HiddenAppsScreen()
            ScreenId.GESTURES -> GesturesScreen()
            ScreenId.BADGES -> BadgesScreen()
            ScreenId.BADGE_APPS -> BadgeAppsScreen()
            ScreenId.GRANT -> GrantScreen(if (arg < 0) ConsentKind.NOTIFICATION_ACCESS.ordinal else arg)
            ScreenId.RESTRICTED_HELP -> RestrictedHelpScreen()
            ScreenId.ACTION_PICKER -> ActionPickerScreen(arg)
            ScreenId.APP_PICKER -> AppPickerScreen(arg)
            ScreenId.THEME -> ThemeScreen()
            ScreenId.APPEARANCE -> AppearanceScreen()
            ScreenId.BACKUP -> BackupScreen()
            ScreenId.ABOUT -> AboutScreen()
        }

    /** Back out to a screen already on the stack, after a choice deeper in has been made. */
    private fun popTo(id: ScreenId) {
        while (stack.size > 1 && stack.last().id != id) stack.removeLast()
        stack.lastOrNull()?.let(::show)
    }

    private fun push(screen: Screen) {
        stack.addLast(screen)
        show(screen)
    }

    private fun pop() {
        stack.removeLast()
        stack.lastOrNull()?.let(::show)
    }

    private fun show(screen: Screen) {
        title.setText(screen.titleRes)
        container.removeAllViews()
        container.addView(
            screen.createView(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun list(
        adapter: RowAdapter,
        onClick: (Int) -> Unit,
        onLongClick: ((Int) -> Boolean)? = null,
    ): ListView =
        ListView(this).apply {
            this.adapter = adapter
            divider = null
            dividerHeight = 0
            setOnItemClickListener { _, _, position, _ -> onClick(position) }
            if (onLongClick != null) setOnItemLongClickListener { _, _, position, _ -> onLongClick(position) }
        }

    /** A screen of prose with tappable actions under it: disclosures and help pages. */
    private fun page(
        paragraphs: List<String>,
        actions: List<Pair<String, () -> Unit>>,
    ): View {
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(4), dp(20), dp(24))
            }
        for (paragraph in paragraphs) {
            column.addView(
                TextView(this).apply {
                    text = paragraph
                    setTextColor(colors.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setLineSpacing(0f, 1.2f)
                    setPadding(0, dp(8), 0, dp(8))
                },
            )
        }
        for ((label, run) in actions) {
            column.addView(
                TextView(this).apply {
                    text = label
                    setTextColor(colors.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(56)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { run() }
                },
            )
        }
        return ScrollView(this).apply { addView(column) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    // ---- screens ------------------------------------------------------------------------------

    private enum class ScreenId {
        MENU,
        HOME_APPS,
        HIDDEN_APPS,
        GESTURES,
        BADGES,
        BADGE_APPS,
        GRANT,
        RESTRICTED_HELP,
        ACTION_PICKER,
        APP_PICKER,
        THEME,
        APPEARANCE,
        BACKUP,
        ABOUT,
    }

    private abstract inner class Screen(
        val id: ScreenId,
        val titleRes: Int,
    ) {
        /** Which surface this screen is about, for the two that need one; -1 otherwise. */
        open val arg: Int = -1

        abstract fun createView(): View

        open fun refresh() = Unit
    }

    private inner class MenuScreen : Screen(ScreenId.MENU, R.string.settings_title) {
        override fun createView(): View {
            val items =
                listOf(
                    R.string.settings_home_apps to { push(HomeAppsScreen()) },
                    R.string.settings_hidden_apps to { push(HiddenAppsScreen()) },
                    R.string.settings_gestures to { push(GesturesScreen()) },
                    R.string.settings_badges to { push(BadgesScreen()) },
                    R.string.settings_theme to { push(ThemeScreen()) },
                    R.string.settings_appearance to { push(AppearanceScreen()) },
                    R.string.settings_backup to { push(BackupScreen()) },
                    R.string.settings_about to { push(AboutScreen()) },
                )
            val adapter = RowAdapter(this@SettingsActivity, colors, items.map { Row(getString(it.first)) })
            return list(adapter, onClick = { items[it].second() })
        }
    }

    /** All apps, home apps first in their order; tap toggles membership, hold reorders. */
    private inner class HomeAppsScreen : Screen(ScreenId.HOME_APPS, R.string.settings_home_apps) {
        private lateinit var adapter: RowAdapter
        private lateinit var summary: TextView
        private var ordered: List<AppEntry> = emptyList()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            summary =
                TextView(this@SettingsActivity).apply {
                    setTextColor(colors.textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dp(20), 0, dp(20), dp(8))
                }
            refresh()
            val column = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            column.addView(summary)
            column.addView(
                list(adapter, onClick = ::toggle, onLongClick = ::reorder),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            return column
        }

        override fun refresh() {
            val all = atomicApp.appRepository.current.entries
            val onHome = homeKeys()
            val byKey = all.associateBy { it.key }
            ordered = onHome.mapNotNull { byKey[it] } + all.filterNot { it.key in onHome }
            adapter.rows =
                ordered.map { Row(settings.current.labelOverrides[it.key] ?: it.label, checked = it.key in onHome) }
            adapter.notifyDataSetChanged()
            summary.text = getString(R.string.home_apps_summary, onHome.size, HomeLimits.MAX_ROWS)
        }

        /** The rows the home screen shows now: explicit entries, or the alphabetical fallback. */
        private fun homeKeys(): List<AppKey> =
            HomeListModel.build(atomicApp.appRepository.current.entries, settings.current).map {
                it.entry.key
            }

        private fun toggle(position: Int) {
            val entry = ordered.getOrNull(position) ?: return
            val visible = homeKeys().map { it.toRef() }
            val onHome = entry.key in homeKeys()
            settings.update { doc ->
                val pinned = SettingsEdits.materializeHome(doc, visible)
                if (onHome) {
                    SettingsEdits.removeFromHome(
                        pinned,
                        entry.key.toRef(),
                    )
                } else {
                    SettingsEdits.addToHome(pinned, entry.key.toRef())
                }
            }
        }

        private fun reorder(position: Int): Boolean {
            val entry = ordered.getOrNull(position) ?: return false
            val keys = homeKeys()
            val index = keys.indexOf(entry.key)
            if (index < 0) return false
            val visible = keys.map { it.toRef() }
            val options =
                listOf(R.string.move_up to -1, R.string.move_down to 1).filter {
                    (index + it.second) in
                        keys.indices
                }
            AlertDialog
                .Builder(this@SettingsActivity)
                .setTitle(adapter.rows[position].label)
                .setItems(options.map { getString(it.first) }.toTypedArray()) { _, which ->
                    val delta = options[which].second
                    settings.update { doc ->
                        SettingsEdits.moveHomeEntry(
                            SettingsEdits.materializeHome(doc, visible),
                            index,
                            index + delta,
                        )
                    }
                }.show()
            return true
        }
    }

    private inner class HiddenAppsScreen : Screen(ScreenId.HIDDEN_APPS, R.string.settings_hidden_apps) {
        private lateinit var adapter: RowAdapter
        private var entries: List<AppEntry> = emptyList()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            val column = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            column.addView(
                TextView(this@SettingsActivity).apply {
                    setTextColor(colors.textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(dp(20), 0, dp(20), dp(8))
                    setText(R.string.hidden_apps_summary)
                },
            )
            column.addView(
                list(adapter, onClick = { position ->
                    val entry = entries.getOrNull(position) ?: return@list
                    val hidden = entry.key in settings.current.hidden
                    settings.update { SettingsEdits.setHidden(it, entry.key.toRef(), !hidden) }
                }),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            return column
        }

        override fun refresh() {
            entries = atomicApp.appRepository.current.entries
            adapter.rows =
                entries.map {
                    Row(
                        settings.current.labelOverrides[it.key] ?: it.label,
                        checked =
                            it.key in settings.current.hidden,
                    )
                }
            adapter.notifyDataSetChanged()
        }
    }

    /** Everything the home screen answers to, and what each one does at the moment. */
    private inner class GesturesScreen : Screen(ScreenId.GESTURES, R.string.settings_gestures) {
        private lateinit var adapter: RowAdapter

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = ::choose)
        }

        override fun refresh() {
            val gestures = settings.settings.gestures
            adapter.rows =
                listOf(
                    Row(getString(R.string.gestures_haptics), checked = gestures.haptics),
                    Row(
                        getString(R.string.gestures_edges),
                        getString(R.string.gestures_edges_detail),
                        checked = gestures.edgeExclusion == EdgeExclusion.BOTH,
                    ),
                ) + BindingSurface.all.map { Row(ActionLabels.of(this@SettingsActivity, it), boundTo(it)) }
            adapter.notifyDataSetChanged()
        }

        /** What it does, and why that would not work if it would not. */
        private fun boundTo(surface: BindingSurface): String {
            val action = settings.settings.binding(surface)
            val described = ActionLabels.describe(this@SettingsActivity, action, atomicApp.appRepository)
            val problem = ActionLabels.reason(this@SettingsActivity, availability.of(action))
            return if (problem == null) described else "$described \u2014 $problem"
        }

        private fun choose(position: Int) {
            when (position) {
                0 -> {
                    settings.update { it.copy(gestures = it.gestures.copy(haptics = !it.gestures.haptics)) }
                }

                1 -> {
                    settings.update {
                        val next =
                            if (it.gestures.edgeExclusion ==
                                EdgeExclusion.BOTH
                            ) {
                                EdgeExclusion.NONE
                            } else {
                                EdgeExclusion.BOTH
                            }
                        it.copy(gestures = it.gestures.copy(edgeExclusion = next))
                    }
                }

                else -> {
                    push(ActionPickerScreen(position - GESTURE_HEADER_ROWS))
                }
            }
        }
    }

    /** What one surface should do. Everything is listed; what cannot run says why and stays unselectable. */
    private inner class ActionPickerScreen(
        override val arg: Int,
    ) : Screen(ScreenId.ACTION_PICKER, R.string.picker_title) {
        private val surface: BindingSurface = BindingSurface.all[arg.coerceIn(BindingSurface.all.indices)]
        private val rows = mutableListOf<Row>()
        private val choices = mutableListOf<() -> Unit>()

        override fun createView(): View {
            build()
            return list(RowAdapter(this@SettingsActivity, colors, rows), onClick = { choices[it]() })
        }

        private fun add(
            row: Row,
            choice: () -> Unit,
        ) {
            rows += row
            choices += choice
        }

        private fun build() {
            rows.clear()
            choices.clear()
            add(Row(getString(R.string.action_none))) { bindAndReturn(Action.None) }
            add(Row(getString(R.string.action_use_default))) { bindAndReturn(null) }
            add(Row(getString(R.string.action_open_app))) { push(AppPickerScreen(arg)) }
            add(Row(getString(R.string.action_open_link))) { askForLink() }
            for (group in ActionGroup.entries) {
                add(Row(ActionLabels.of(this@SettingsActivity, group), isHeader = true), choice = {})
                for (id in BuiltinId.entries.filter { it.group == group }) {
                    val action = Action.Builtin(id)
                    val state = availability.of(action)
                    val problem = ActionLabels.reason(this@SettingsActivity, state)
                    val usable = state == Availability.Available || state is Availability.NeedsGrant
                    add(Row(ActionLabels.of(this@SettingsActivity, id), problem, enabled = usable)) {
                        if (usable) bindAndReturn(action) else problem?.let(::toast)
                    }
                }
            }
        }

        private fun askForLink() {
            val input =
                EditText(this@SettingsActivity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setHint(R.string.action_link_hint)
                    (settings.settings.binding(surface) as? Action.OpenUrl)?.let { setText(it.url) }
                }
            val pad = dp(20)
            val container = FrameLayout(this@SettingsActivity).apply { setPadding(pad, pad / 2, pad, 0) }
            container.addView(input)
            AlertDialog
                .Builder(this@SettingsActivity)
                .setTitle(R.string.action_open_link)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val url = input.text.toString().trim()
                    if (LinkRules.isOpenable(url)) {
                        bindAndReturn(Action.OpenUrl(url))
                    } else {
                        toast(getString(R.string.link_not_valid))
                    }
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun bindAndReturn(action: Action?) {
            settings.update { SettingsEdits.bind(it, surface, action) }
            popTo(ScreenId.GESTURES)
        }
    }

    private inner class AppPickerScreen(
        override val arg: Int,
    ) : Screen(ScreenId.APP_PICKER, R.string.action_open_app) {
        override fun createView(): View {
            val entries = atomicApp.appRepository.current.entries
            val adapter =
                RowAdapter(
                    this@SettingsActivity,
                    colors,
                    entries.map { Row(settings.current.labelOverrides[it.key] ?: it.label) },
                )
            return list(adapter, onClick = { position ->
                val key = entries[position].key
                val surface = BindingSurface.all[arg.coerceIn(BindingSurface.all.indices)]
                settings.update {
                    SettingsEdits.bind(
                        it,
                        surface,
                        Action.OpenApp(key.flattenedComponent, key.userSerial),
                    )
                }
                popTo(ScreenId.GESTURES)
            })
        }
    }

    private inner class ThemeScreen : Screen(ScreenId.THEME, R.string.settings_theme) {
        override fun createView(): View {
            val themes = BuiltinThemes.all
            val currentId = settings.settings.theme.meta.id
            val adapter =
                RowAdapter(
                    this@SettingsActivity,
                    colors,
                    themes.map {
                        Row(
                            it.meta.name,
                            it.meta.description,
                            checked = it.meta.id == currentId,
                            singleChoice = true,
                        )
                    },
                )
            return list(adapter, onClick = { position -> settings.update { it.copy(theme = themes[position]) } })
        }
    }

    private inner class AppearanceScreen : Screen(ScreenId.APPEARANCE, R.string.settings_appearance) {
        override fun createView(): View {
            val modes =
                listOf(
                    NightMode.SYSTEM to R.string.night_system,
                    NightMode.LIGHT to R.string.night_light,
                    NightMode.DARK to R.string.night_dark,
                )
            val current = settings.settings.appearance.nightMode
            val adapter =
                RowAdapter(
                    this@SettingsActivity,
                    colors,
                    modes.map {
                        Row(
                            getString(it.second),
                            checked =
                                it.first == current,
                            singleChoice = true,
                        )
                    },
                )
            return list(adapter, onClick = { position ->
                settings.update { it.copy(appearance = it.appearance.copy(nightMode = modes[position].first)) }
            })
        }
    }

    private inner class BackupScreen : Screen(ScreenId.BACKUP, R.string.settings_backup) {
        override fun createView(): View {
            val rows =
                listOf(
                    Row(getString(R.string.backup_export), getString(R.string.backup_summary)),
                    Row(getString(R.string.backup_import)),
                )
            return list(RowAdapter(this@SettingsActivity, colors, rows), onClick = { position ->
                when (position) {
                    0 -> exportLauncher.launch("atomic-settings-${LocalDate.now()}.json")
                    else -> importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            })
        }
    }

    private inner class AboutScreen : Screen(ScreenId.ABOUT, R.string.settings_about) {
        private lateinit var adapter: RowAdapter

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = { position ->
                when (position) {
                    1 -> {
                        open(Uri.parse(getString(R.string.source_url)))
                    }

                    3 -> {
                        shareCrash()
                    }

                    4 -> {
                        atomicApp.crashRecorder.clear()
                        refresh()
                    }
                }
            })
        }

        override fun refresh() {
            val env = atomicApp.crashEnvironment
            val hasCrash = atomicApp.crashRecorder.lastReport() != null
            adapter.rows =
                listOf(
                    Row(
                        getString(R.string.about_version, env.appVersion, env.versionCode),
                        "Android ${env.androidRelease} (API ${env.sdkInt})",
                    ),
                    Row(getString(R.string.about_source), getString(R.string.source_url)),
                    Row(getString(R.string.about_license)),
                    Row(
                        getString(R.string.about_crash_share),
                        getString(if (hasCrash) R.string.about_crash_recorded else R.string.about_crash_none),
                    ),
                    Row(getString(R.string.about_crash_clear)),
                )
            adapter.notifyDataSetChanged()
        }

        private fun shareCrash() {
            val report = atomicApp.crashRecorder.lastReport() ?: return toast(getString(R.string.about_crash_none))
            val intent =
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.support_email)))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_mail_subject))
                    putExtra(Intent.EXTRA_TEXT, report)
                }
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        },
                        null,
                    ),
                )
            }
        }

        private fun open(uri: Uri) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                toast(getString(R.string.action_unavailable))
            }
        }
    }

    // ---- backup -------------------------------------------------------------------------------

    private fun exportTo(uri: Uri) {
        val text = SettingsCodec.encodeSettings(settings.settings)
        Threads.io.post {
            val ok =
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } !=
                        null
                } catch (e: IOException) {
                    Logs.w(TAG, "export failed", e)
                    false
                } catch (e: SecurityException) {
                    Logs.w(TAG, "export failed", e)
                    false
                }
            Threads.main.post { toast(getString(if (ok) R.string.backup_exported else R.string.backup_write_failed)) }
        }
    }

    private fun importFrom(uri: Uri) {
        Threads.io.post {
            val text =
                try {
                    contentResolver
                        .openInputStream(
                            uri,
                        )?.use { readAtMost(it, MAX_IMPORT_BYTES).toString(Charsets.UTF_8) }
                } catch (e: IOException) {
                    Logs.w(TAG, "import failed", e)
                    null
                } catch (e: SecurityException) {
                    Logs.w(TAG, "import failed", e)
                    null
                }
            Threads.main.post {
                when (val result = text?.let(SettingsCodec::decodeSettings)) {
                    null -> {
                        toast(getString(R.string.backup_failed, "unreadable"))
                    }

                    is DecodeResult.Corrupt -> {
                        toast(getString(R.string.backup_failed, result.reason))
                    }

                    is DecodeResult.Unsupported -> {
                        toast(getString(R.string.backup_unsupported))
                    }

                    is DecodeResult.Ok -> {
                        // Everything but this device's own run state (setup done, last version) is replaced.
                        settings.update { current -> result.value.copy(app = current.app) }
                        toast(
                            if (result.warnings.isEmpty()) {
                                getString(R.string.backup_imported)
                            } else {
                                getString(R.string.backup_imported_with_warnings, result.warnings.size)
                            },
                        )
                    }
                }
            }
        }
    }

    /** Notification badges: the switch, what counts, and the apps left out. */
    private inner class BadgesScreen : Screen(ScreenId.BADGES, R.string.settings_badges) {
        private lateinit var adapter: RowAdapter

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = ::tap)
        }

        override fun refresh() {
            if (!::adapter.isInitialized) return
            val config = settings.settings.notifications
            val supported = NotificationAccess.isSupported(this@SettingsActivity)
            val granted = NotificationAccess.isGranted(this@SettingsActivity)
            val state =
                when {
                    !supported -> R.string.badges_state_unsupported
                    granted -> R.string.badges_state_granted
                    else -> R.string.badges_state_needed
                }
            val silenced = config.perAppDisabled.size
            adapter.rows =
                listOf(
                    Row(
                        getString(R.string.badges_show),
                        getString(state),
                        checked = config.enabled,
                        enabled = supported,
                    ),
                    Row(
                        getString(R.string.badges_ongoing),
                        getString(R.string.badges_ongoing_detail),
                        checked = config.includeOngoing,
                        enabled = config.enabled,
                    ),
                    Row(
                        getString(R.string.badges_per_app),
                        if (silenced == 0) {
                            getString(R.string.badges_per_app_none)
                        } else {
                            getString(R.string.badges_per_app_detail, silenced, badgeApps().size)
                        },
                        enabled = config.enabled,
                    ),
                )
            adapter.notifyDataSetChanged()
        }

        private fun tap(position: Int) {
            val config = settings.settings.notifications
            when (position) {
                0 -> {
                    when {
                        !NotificationAccess.isSupported(this@SettingsActivity) -> {
                            toast(getString(R.string.badges_state_unsupported))
                        }

                        config.enabled -> {
                            settings.update { it.copy(notifications = it.notifications.copy(enabled = false)) }
                        }

                        // Access first, and only after the disclosure: the switch here is what
                        // asks for it, so this is the moment to explain what it means.
                        !NotificationAccess.isGranted(this@SettingsActivity) -> {
                            push(GrantScreen(ConsentKind.NOTIFICATION_ACCESS.ordinal))
                        }

                        else -> {
                            settings.update { it.copy(notifications = it.notifications.copy(enabled = true)) }
                        }
                    }
                }

                1 -> {
                    if (!config.enabled) {
                        toast(getString(R.string.badges_needs_access))
                    } else {
                        settings.update {
                            it.copy(notifications = it.notifications.copy(includeOngoing = !config.includeOngoing))
                        }
                    }
                }

                else -> {
                    if (!config.enabled) toast(getString(R.string.badges_needs_access)) else push(BadgeAppsScreen())
                }
            }
        }
    }

    /** One row per app, checked while that app may show a badge. Badges are counted per app. */
    private inner class BadgeAppsScreen : Screen(ScreenId.BADGE_APPS, R.string.badges_per_app_title) {
        private lateinit var adapter: RowAdapter
        private var apps: List<AppEntry> = emptyList()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = ::toggle)
        }

        override fun refresh() {
            if (!::adapter.isInitialized) return
            val silenced = settings.settings.notifications.perAppDisabled
            apps = badgeApps()
            adapter.rows =
                apps.map { entry ->
                    Row(
                        settings.current.labelOverrides[entry.key] ?: entry.label,
                        checked = refOf(entry) !in silenced,
                    )
                }
            adapter.notifyDataSetChanged()
        }

        private fun toggle(position: Int) {
            val entry = apps.getOrNull(position) ?: return
            val app = refOf(entry)
            val shown = app !in settings.settings.notifications.perAppDisabled
            settings.update { SettingsEdits.setBadges(it, app, shown = !shown) }
        }
    }

    /**
     * The prominent disclosure for a special access: what is read, what it is for, and what the
     * limits are, followed by the user's own decision. Settings is only opened from Continue.
     */
    private inner class GrantScreen(
        private val kind: Int,
    ) : Screen(ScreenId.GRANT, Grants.of(ConsentKind.entries[kind]).titleRes) {
        override val arg: Int get() = kind

        private val spec = Grants.of(ConsentKind.entries[kind])
        private var handedOver = false

        override fun createView(): View {
            val restriction = InstallSourceProbe.restriction(this@SettingsActivity)
            val actions =
                buildList {
                    add(getString(R.string.grant_continue) to ::openSettings)
                    add(getString(R.string.grant_not_now) to { pop() })
                    if (restriction != Restriction.NO) {
                        add(getString(R.string.restricted_title) to { push(RestrictedHelpScreen()) })
                    }
                }
            return page(
                paragraphs = listOf(getString(spec.whatRes), getString(spec.whyRes), getString(spec.limitsRes)),
                actions = actions,
            )
        }

        private fun openSettings() {
            // Tapping Continue is the affirmative action the disclosure asked for; note when.
            settings.update {
                SettingsEdits.recordConsent(it, ConsentKind.entries[kind], System.currentTimeMillis())
            }
            for (intent in spec.intents(this@SettingsActivity)) {
                try {
                    startActivity(intent)
                    handedOver = true
                    return
                } catch (e: ActivityNotFoundException) {
                    Logs.w(TAG, "no activity for ${intent.action}", e)
                }
            }
            toast(getString(R.string.grant_no_settings))
        }

        /** Called when Settings hands the user back: check rather than assume. */
        fun onReturn() {
            if (!handedOver) return
            handedOver = false
            if (spec.isGranted(this@SettingsActivity)) {
                // They came here to turn badges on, so turn them on.
                settings.update { it.copy(notifications = it.notifications.copy(enabled = true)) }
                toast(getString(R.string.grant_granted))
                popTo(ScreenId.BADGES)
                return
            }
            toast(getString(R.string.grant_not_granted))
            if (InstallSourceProbe.restriction(this@SettingsActivity) != Restriction.NO) push(RestrictedHelpScreen())
        }
    }

    /** Android 13 and later: why the switch did nothing, and how to allow it. */
    private inner class RestrictedHelpScreen : Screen(ScreenId.RESTRICTED_HELP, R.string.restricted_title) {
        override fun createView(): View {
            val certain = InstallSourceProbe.restriction(this@SettingsActivity) == Restriction.LIKELY
            return page(
                paragraphs =
                    listOf(
                        getString(if (certain) R.string.restricted_why else R.string.restricted_why_maybe),
                        getString(R.string.restricted_steps),
                    ),
                actions =
                    listOf(
                        getString(R.string.restricted_open) to ::openAppInfo,
                        getString(R.string.restricted_try_again) to { pop() },
                    ),
            )
        }

        private fun openAppInfo() {
            val intent =
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Logs.w(TAG, "no app info screen", e)
                toast(getString(R.string.grant_no_settings))
            }
        }
    }

    /** One row per app rather than per launchable activity: a badge belongs to the whole app. */
    private fun badgeApps(): List<AppEntry> =
        atomicApp.appRepository.current.entries
            .distinctBy { it.key.packageName to it.key.userSerial }

    private fun refOf(entry: AppEntry): PackageRef = PackageRef(entry.key.packageName, entry.key.userSerial)

    /** Reads up to [max] bytes; a settings file is a few kilobytes, so anything larger is not one. */
    private fun readAtMost(
        input: InputStream,
        max: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < max) {
            val read = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "SettingsActivity"
        const val STATE_STACK = "stack"
        const val STATE_ARGS = "args"

        /** The haptics and edge rows sit above the list of surfaces. */
        const val GESTURE_HEADER_ROWS = 2
        const val MAX_IMPORT_BYTES = 1_000_000
    }
}
