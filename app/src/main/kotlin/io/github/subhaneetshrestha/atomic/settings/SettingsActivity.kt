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
import io.github.subhaneetshrestha.atomic.background.BackgroundController
import io.github.subhaneetshrestha.atomic.background.BackgroundEngine
import io.github.subhaneetshrestha.atomic.background.BackgroundStatus
import io.github.subhaneetshrestha.atomic.background.SkipReason
import io.github.subhaneetshrestha.atomic.core.collections.UrlRules
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.ActionGroup
import io.github.subhaneetshrestha.atomic.core.theme.Background
import io.github.subhaneetshrestha.atomic.core.theme.BackgroundMode
import io.github.subhaneetshrestha.atomic.core.theme.BadgePosition
import io.github.subhaneetshrestha.atomic.core.theme.BadgeStyle
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinId
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinThemes
import io.github.subhaneetshrestha.atomic.core.theme.ColorValue
import io.github.subhaneetshrestha.atomic.core.theme.ColorsOverride
import io.github.subhaneetshrestha.atomic.core.theme.ConsentKind
import io.github.subhaneetshrestha.atomic.core.theme.DecodeResult
import io.github.subhaneetshrestha.atomic.core.theme.EdgeExclusion
import io.github.subhaneetshrestha.atomic.core.theme.HAlign
import io.github.subhaneetshrestha.atomic.core.theme.HomeLimits
import io.github.subhaneetshrestha.atomic.core.theme.InfoLineId
import io.github.subhaneetshrestha.atomic.core.theme.InfoPosition
import io.github.subhaneetshrestha.atomic.core.theme.Labels
import io.github.subhaneetshrestha.atomic.core.theme.LinkRules
import io.github.subhaneetshrestha.atomic.core.theme.NightMode
import io.github.subhaneetshrestha.atomic.core.theme.PackageRef
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.SettingsCodec
import io.github.subhaneetshrestha.atomic.core.theme.SettingsEdits
import io.github.subhaneetshrestha.atomic.core.theme.Theme
import io.github.subhaneetshrestha.atomic.core.theme.ThemeEdits
import io.github.subhaneetshrestha.atomic.core.theme.ThemeLink
import io.github.subhaneetshrestha.atomic.core.theme.VAlign
import io.github.subhaneetshrestha.atomic.home.DefaultHomePrompt
import io.github.subhaneetshrestha.atomic.home.HomeListModel
import io.github.subhaneetshrestha.atomic.notifications.NotificationAccess
import io.github.subhaneetshrestha.atomic.settings.grant.Grants
import io.github.subhaneetshrestha.atomic.settings.grant.InstallSourceProbe
import io.github.subhaneetshrestha.atomic.settings.grant.Restriction
import io.github.subhaneetshrestha.atomic.share.ImportActivity
import io.github.subhaneetshrestha.atomic.share.ThemeFiles
import io.github.subhaneetshrestha.atomic.system.DeviceAdminLock
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import io.github.subhaneetshrestha.atomic.util.readAtMost
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
            AndroidActionEnvironment(
                this,
                atomicApp.appRepository,
                atomicApp.systemActions,
                BuiltinActions.BUILT_SURFACES,
            ) {
                DefaultHomePrompt(this).isDefaultHome()
            },
        )
    }
    private lateinit var colors: ResolvedColors
    private lateinit var title: TextView
    private lateinit var container: FrameLayout
    private val stack = ArrayDeque<Screen>()

    /**
     * Editing a theme changes the colours this screen is drawn in, so the activity is recreated on
     * every edit. Without this, changing the badge colour would send the list back to the top.
     */
    private var restoreScroll = 0

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> uri?.let(::exportTo) }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importFrom) }
    private val themeExportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri -> uri?.let(::writeThemeTo) }
    private val themeImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::openTheme) }
    private val documentListener: (Settings, Settings) -> Unit = { old, new ->
        // Everything about a theme but its background decides the colours this screen is drawn in.
        val looksDifferent = old.theme.copy(background = new.theme.background) != new.theme
        if (looksDifferent || old.appearance != new.appearance) recreate() else stack.lastOrNull()?.refresh()
    }
    private val backgroundListener = BackgroundController.Listener { stack.lastOrNull()?.refresh() }

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
        restoreScroll = savedInstanceState?.getInt(STATE_SCROLL) ?: 0
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
        atomicApp.background.addListener(backgroundListener)
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
        atomicApp.background.removeListener(backgroundListener)
        settings.flush()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(STATE_STACK, stack.map { it.id.ordinal }.toIntArray())
        outState.putIntArray(STATE_ARGS, stack.map { it.arg }.toIntArray())
        outState.putInt(STATE_SCROLL, (container.getChildAt(0) as? ListView)?.firstVisiblePosition ?: 0)
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
            ScreenId.SYSTEM -> SystemScreen()
            ScreenId.INFO_LINES -> InfoLinesScreen()
            ScreenId.BADGE_APPS -> BadgeAppsScreen()
            ScreenId.GRANT -> GrantScreen(if (arg < 0) ConsentKind.NOTIFICATION_ACCESS.ordinal else arg)
            ScreenId.RESTRICTED_HELP -> RestrictedHelpScreen()
            ScreenId.ACTION_PICKER -> ActionPickerScreen(arg)
            ScreenId.APP_PICKER -> AppPickerScreen(arg)
            ScreenId.THEME -> ThemeScreen()
            ScreenId.THEME_EDITOR -> ThemeEditorScreen()
            ScreenId.BACKGROUND -> BackgroundScreen()
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
            if (restoreScroll > 0) {
                setSelection(restoreScroll)
                restoreScroll = 0
            }
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

    /** A dialog with one field in it, the shape every "type this in" setting uses. */
    private fun prompt(
        titleRes: Int,
        message: String?,
        input: EditText,
        onOk: () -> Unit,
    ) {
        val pad = dp(20)
        val container = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0) }
        container.addView(input)
        // The value arrives selected, so typing replaces it: these are short things — a colour, a
        // size, an address — that are retyped rather than edited a character at a time. Tapping
        // anywhere in the field puts the cursor there instead.
        input.selectAll()
        AlertDialog
            .Builder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A colour typed as #RRGGBB, or the word that means "follow the text" where that is allowed. */
    private fun askColour(
        titleRes: Int,
        current: String,
        allowAuto: Boolean = false,
        apply: (String) -> Unit,
    ) {
        val input =
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setHint(R.string.background_colour_hint)
                setText(current)
            }
        prompt(titleRes, if (allowAuto) getString(R.string.editor_auto_hint) else null, input) {
            val value = ColorValue.normalize(input.text.toString(), allowAuto = allowAuto)
            if (value == null) toast(getString(R.string.background_colour_invalid)) else apply(value)
        }
    }

    /** A number, whole or not. Anything outside the allowed range is pulled into it when stored. */
    private fun askNumber(
        titleRes: Int,
        current: String,
        decimal: Boolean,
        apply: (Float) -> Unit,
    ) {
        val input =
            EditText(this).apply {
                inputType =
                    InputType.TYPE_CLASS_NUMBER or if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                setText(current)
            }
        prompt(titleRes, null, input) {
            val value =
                input.text
                    .toString()
                    .trim()
                    .toFloatOrNull()
            if (value == null) toast(getString(R.string.editor_number_invalid)) else apply(value)
        }
    }

    private fun askText(
        titleRes: Int,
        current: String,
        apply: (String) -> Unit,
    ) {
        val input =
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(current)
            }
        prompt(titleRes, null, input) { apply(input.text.toString().trim()) }
    }

    private fun choose(
        titleRes: Int,
        labels: List<String>,
        pick: (Int) -> Unit,
    ) {
        AlertDialog
            .Builder(this)
            .setTitle(titleRes)
            .setItems(labels.toTypedArray()) { _, index -> pick(index) }
            .show()
    }

    // ---- screens ------------------------------------------------------------------------------

    private enum class ScreenId {
        MENU,
        HOME_APPS,
        HIDDEN_APPS,
        GESTURES,
        BADGES,
        BADGE_APPS,
        SYSTEM,
        INFO_LINES,
        GRANT,
        RESTRICTED_HELP,
        ACTION_PICKER,
        APP_PICKER,
        THEME,
        THEME_EDITOR,
        BACKGROUND,
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
                    R.string.settings_info_lines to { push(InfoLinesScreen()) },
                    R.string.settings_badges to { push(BadgesScreen()) },
                    R.string.settings_system to { push(SystemScreen()) },
                    R.string.settings_theme to { push(ThemeScreen()) },
                    R.string.settings_background to { push(BackgroundScreen()) },
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

    /**
     * What is behind the names. The rows below the mode change with it, because a gradient and a
     * collection have nothing in common to configure.
     */
    private inner class BackgroundScreen : Screen(ScreenId.BACKGROUND, R.string.settings_background) {
        private lateinit var adapter: RowAdapter
        private val rows = mutableListOf<Row>()
        private val taps = mutableListOf<() -> Unit>()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = { position -> taps.getOrNull(position)?.invoke() })
        }

        override fun refresh() {
            rows.clear()
            taps.clear()
            val background = settings.settings.theme.background
            add(Row(getString(R.string.background_mode), isHeader = true))
            for ((mode, label, detail) in MODES) {
                add(
                    Row(getString(label), getString(detail), checked = background.mode == mode, singleChoice = true),
                ) { edit { it.copy(mode = mode) } }
            }
            when (background.mode) {
                BackgroundMode.GRADIENT -> gradientRows(background)
                BackgroundMode.COLLECTION -> collectionRows(background)
                else -> Unit
            }
            adapter.rows = rows.toList()
            adapter.notifyDataSetChanged()
        }

        private fun gradientRows(background: Background) {
            val gradient = background.gradient
            add(Row(getString(R.string.background_from), gradient.from)) {
                askColour(R.string.background_from, gradient.from) { value ->
                    edit { it.copy(gradient = it.gradient.copy(from = value)) }
                }
            }
            add(Row(getString(R.string.background_to), gradient.to)) {
                askColour(R.string.background_to, gradient.to) { value ->
                    edit { it.copy(gradient = it.gradient.copy(to = value)) }
                }
            }
            add(Row(getString(R.string.background_direction), getString(directionLabel(gradient.angle)))) {
                choose(R.string.background_direction, DIRECTIONS.map { getString(it.second) }) { index ->
                    edit { it.copy(gradient = it.gradient.copy(angle = DIRECTIONS[index].first)) }
                }
            }
        }

        private fun collectionRows(background: Background) {
            val collection = background.collection
            add(
                Row(
                    getString(R.string.background_address),
                    collection.url.ifEmpty { getString(R.string.background_address_none) },
                ),
            ) { askForCollection(collection.url) }
            add(Row(getString(R.string.background_interval), getString(intervalLabel(collection.intervalMinutes)))) {
                choose(R.string.background_interval, INTERVALS.map { getString(it.second) }) { index ->
                    edit { it.copy(collection = it.collection.copy(intervalMinutes = INTERVALS[index].first)) }
                }
            }
            add(
                Row(
                    getString(R.string.background_unmetered),
                    getString(R.string.background_unmetered_detail),
                    checked = collection.unmeteredOnly,
                ),
            ) { edit { it.copy(collection = it.collection.copy(unmeteredOnly = !collection.unmeteredOnly)) } }
            add(
                Row(
                    getString(R.string.background_shuffle),
                    getString(R.string.background_shuffle_detail),
                    checked = collection.shuffle,
                ),
            ) { edit { it.copy(collection = it.collection.copy(shuffle = !collection.shuffle)) } }
            add(
                Row(
                    getString(R.string.background_dim),
                    getString(R.string.background_dim_value, (background.dim * PERCENT).toInt()),
                ),
            ) {
                choose(R.string.background_dim, DIMS.map { getString(R.string.background_dim_value, it) }) { index ->
                    edit { it.copy(dim = DIMS[index] / PERCENT.toFloat()) }
                }
            }
            add(
                Row(
                    getString(R.string.background_auto_text),
                    getString(R.string.background_auto_text_detail),
                    checked = background.autoTextColor,
                ),
            ) { edit { it.copy(autoTextColor = !background.autoTextColor) } }
            add(
                Row(getString(R.string.background_change_now), statusLine(), enabled = collection.isConfigured),
            ) { if (!atomicApp.background.requestNext()) toast(getString(R.string.background_address_none)) }
        }

        /** What the engine did last, in the user's words rather than the log's. */
        private fun statusLine(): String {
            val status: BackgroundStatus = atomicApp.background.status
            val skip = status.lastSkip?.let { runCatching { SkipReason.valueOf(it) }.getOrNull() }
            return when {
                status.working -> {
                    getString(R.string.background_changing)
                }

                skip != null -> {
                    getString(
                        when (skip) {
                            SkipReason.NO_NETWORK -> R.string.background_status_no_network
                            SkipReason.METERED -> R.string.background_status_metered
                            SkipReason.DATA_SAVER -> R.string.background_status_data_saver
                            SkipReason.POWER_SAVE -> R.string.background_status_power_save
                        },
                    )
                }

                status.lastError != null -> {
                    getString(R.string.background_status_error, status.lastError)
                }

                status.currentUrl != null -> {
                    getString(R.string.background_status_showing, status.imageCount, ago(status.changedAt))
                }

                else -> {
                    getString(R.string.background_status_none)
                }
            }
        }

        private fun ago(moment: Long): String {
            val minutes = ((System.currentTimeMillis() - moment) / 60_000L).coerceAtLeast(0)
            return when {
                minutes < 1 -> getString(R.string.background_when_just_now)
                minutes < 60 -> getString(R.string.background_when_minutes, minutes.toInt())
                minutes < 60 * 24 -> getString(R.string.background_when_hours, (minutes / 60).toInt())
                else -> getString(R.string.background_when_days, (minutes / (60 * 24)).toInt())
            }
        }

        /** The address is checked, and then the host it would download from is named out loud. */
        private fun askForCollection(current: String) {
            val input =
                EditText(this@SettingsActivity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setHint(R.string.background_address_hint)
                    setText(current)
                }
            prompt(R.string.background_address, getString(R.string.background_address_help), input) {
                val url = input.text.toString().trim()
                if (url.isEmpty()) {
                    edit { it.copy(collection = it.collection.copy(url = "")) }
                    return@prompt
                }
                val problem = UrlRules.problemWith(url)
                if (problem != null) {
                    toast(getString(R.string.background_address_invalid, problem))
                    return@prompt
                }
                AlertDialog
                    .Builder(this@SettingsActivity)
                    .setTitle(R.string.background_address)
                    .setMessage(getString(R.string.background_address_host, UrlRules.hostOf(url).orEmpty()))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        edit { it.copy(collection = it.collection.copy(url = url)) }
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        private fun add(
            row: Row,
            tap: () -> Unit = {},
        ) {
            rows += row
            taps += tap
        }

        private fun edit(transform: (Background) -> Background) {
            settings.update { it.copy(theme = it.theme.copy(background = transform(it.theme.background))) }
        }
    }

    /** Which lines sit with the app list, and on which side of it. */
    private inner class InfoLinesScreen : Screen(ScreenId.INFO_LINES, R.string.settings_info_lines) {
        private lateinit var adapter: RowAdapter
        private val rows = mutableListOf<Row>()
        private val taps = mutableListOf<() -> Unit>()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = { position -> taps.getOrNull(position)?.invoke() })
        }

        override fun refresh() {
            rows.clear()
            taps.clear()
            val config = settings.settings.homeInfo
            for ((id, label) in INFO_LINES) {
                val line = config.line(id)
                val needsAccess = id == InfoLineId.SCREEN_TIME && !Grants.usageAccess.isGranted(this@SettingsActivity)
                add(
                    Row(
                        getString(label),
                        if (needsAccess) getString(R.string.screen_time_needs_access) else null,
                        checked = line.enabled,
                    ),
                ) {
                    if (needsAccess && !line.enabled) {
                        push(GrantScreen(ConsentKind.USAGE_ACCESS.ordinal))
                    } else {
                        settings.update {
                            it.copy(homeInfo = it.homeInfo.withLine(id, line.copy(enabled = !line.enabled)))
                        }
                    }
                }
            }
            val above = config.position == InfoPosition.ABOVE
            add(
                Row(
                    getString(R.string.info_position),
                    getString(if (above) R.string.info_above else R.string.info_below),
                ),
            ) {
                settings.update {
                    val next = if (above) InfoPosition.BELOW else InfoPosition.ABOVE
                    it.copy(homeInfo = it.homeInfo.copy(position = next))
                }
            }
            add(Row(getString(R.string.info_bind_hint), isHeader = true))
            adapter.rows = rows.toList()
            adapter.notifyDataSetChanged()
        }

        private fun add(
            row: Row,
            tap: () -> Unit = {},
        ) {
            rows += row
            taps += tap
        }
    }

    /**
     * The three special accesses that are not notification badges: the accessibility service six
     * gesture actions need, the device administrator that can lock the screen instead, and the
     * usage access behind screen time. Each one is off until its own disclosure has been read.
     */
    private inner class SystemScreen : Screen(ScreenId.SYSTEM, R.string.settings_system) {
        private lateinit var adapter: RowAdapter
        private val rows = mutableListOf<Row>()
        private val taps = mutableListOf<() -> Unit>()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = { position -> taps.getOrNull(position)?.invoke() })
        }

        override fun refresh() {
            rows.clear()
            taps.clear()
            val accessibility = Grants.accessibility.isGranted(this@SettingsActivity)
            add(
                Row(
                    getString(R.string.system_accessibility),
                    getString(
                        if (accessibility) R.string.system_accessibility_on else R.string.system_accessibility_off,
                    ),
                    checked = accessibility,
                ),
            ) { toggle(accessibility, Grants.accessibility) }

            val admin = Grants.deviceAdmin.isGranted(this@SettingsActivity)
            add(
                Row(
                    getString(R.string.system_admin),
                    getString(if (admin) R.string.system_admin_on else R.string.system_admin_off),
                    checked = admin,
                ),
            ) {
                if (admin) {
                    DeviceAdminLock.remove(this@SettingsActivity)
                    toast(getString(R.string.grant_admin_removed))
                    refresh()
                } else {
                    push(GrantScreen(ConsentKind.DEVICE_ADMIN.ordinal))
                }
            }

            val usage = Grants.usageAccess.isGranted(this@SettingsActivity)
            add(
                Row(
                    getString(R.string.system_usage),
                    getString(if (usage) R.string.system_usage_on else R.string.system_usage_off),
                    checked = usage,
                ),
            ) { toggle(usage, Grants.usageAccess) }

            if (atomicApp.systemActions.shadeWithoutAccessibility) {
                add(Row(getString(R.string.system_shade_free), isHeader = true))
            }
            adapter.rows = rows.toList()
            adapter.notifyDataSetChanged()
        }

        /** Granting is a disclosure then Settings; taking it away is Settings alone, and theirs to do. */
        private fun toggle(
            granted: Boolean,
            spec: io.github.subhaneetshrestha.atomic.settings.grant.GrantSpec,
        ) {
            if (!granted) {
                push(GrantScreen(spec.kind.ordinal))
                return
            }
            for (intent in spec.intents(this@SettingsActivity)) {
                try {
                    startActivity(intent)
                    return
                } catch (e: ActivityNotFoundException) {
                    Logs.w(TAG, "no activity for ${intent.action}", e)
                }
            }
            toast(getString(R.string.grant_no_settings))
        }

        private fun add(
            row: Row,
            tap: () -> Unit = {},
        ) {
            rows += row
            taps += tap
        }
    }

    /**
     * Which theme is showing, and what can be done with it: edited, shared as a file or a link,
     * written out, or replaced by one somebody else made.
     */
    private inner class ThemeScreen : Screen(ScreenId.THEME, R.string.settings_theme) {
        private lateinit var adapter: RowAdapter
        private val rows = mutableListOf<Row>()
        private val taps = mutableListOf<() -> Unit>()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = { position -> taps.getOrNull(position)?.invoke() })
        }

        override fun refresh() {
            rows.clear()
            taps.clear()
            val theme = settings.settings.theme
            val builtin = ThemeEdits.builtinBehind(theme)
            add(Row(getString(R.string.theme_builtins), isHeader = true))
            for (candidate in BuiltinThemes.all) {
                add(
                    Row(
                        candidate.meta.name,
                        candidate.meta.description,
                        checked = builtin?.meta?.id == candidate.meta.id,
                        singleChoice = true,
                    ),
                ) { choose(candidate) }
            }
            if (builtin == null) {
                add(Row(getString(R.string.theme_custom), isHeader = true))
                add(Row(theme.meta.name, theme.meta.author, checked = true, singleChoice = true))
            }
            add(Row(getString(R.string.theme_current), isHeader = true))
            add(Row(getString(R.string.theme_edit))) { push(ThemeEditorScreen()) }
            add(Row(getString(R.string.theme_share_file))) { shareThemeAsFile() }
            add(Row(getString(R.string.theme_share_link))) { shareThemeAsLink() }
            add(Row(getString(R.string.theme_save))) { themeExportLauncher.launch(ThemeFiles.fileName(theme)) }
            add(Row(getString(R.string.theme_import))) { themeImportLauncher.launch(arrayOf("*/*")) }
            adapter.rows = rows.toList()
            adapter.notifyDataSetChanged()
        }

        /** A built-in brings its colours, not a background: the one set up here stays. */
        private fun choose(chosen: Theme) {
            settings.update { current ->
                val background =
                    if (chosen.background == Background()) current.theme.background else chosen.background
                current.copy(theme = chosen.copy(background = background))
            }
        }

        private fun add(
            row: Row,
            tap: () -> Unit = {},
        ) {
            rows += row
            taps += tap
        }
    }

    /**
     * Every value a theme holds, in the order somebody changing one thinks about them. Each edit
     * goes through the same pass a theme read from a file does, so a size typed with one digit too
     * many is pulled into range as it is typed.
     */
    private inner class ThemeEditorScreen : Screen(ScreenId.THEME_EDITOR, R.string.editor_title) {
        private lateinit var adapter: RowAdapter
        private val rows = mutableListOf<Row>()
        private val taps = mutableListOf<() -> Unit>()

        override fun createView(): View {
            adapter = RowAdapter(this@SettingsActivity, colors, emptyList())
            refresh()
            return list(adapter, onClick = { position -> taps.getOrNull(position)?.invoke() })
        }

        override fun refresh() {
            rows.clear()
            taps.clear()
            val theme = settings.settings.theme
            meta(theme)
            colours(theme)
            typography(theme)
            layout(theme)
            badge(theme)
            adapter.rows = rows.toList()
            adapter.notifyDataSetChanged()
        }

        private fun meta(theme: Theme) {
            add(Row(getString(R.string.editor_name), theme.meta.name)) {
                askText(R.string.editor_name, theme.meta.name) { value ->
                    val name = Labels.clean(value) ?: return@askText
                    edit { it.copy(meta = it.meta.copy(name = name)) }
                }
            }
            add(Row(getString(R.string.editor_author), theme.meta.author.orEmpty())) {
                askText(R.string.editor_author, theme.meta.author.orEmpty()) { value ->
                    edit { it.copy(meta = it.meta.copy(author = value.ifBlank { null })) }
                }
            }
        }

        private fun colours(theme: Theme) {
            add(Row(getString(R.string.editor_colours), isHeader = true))
            val day = theme.colors
            colour(
                R.string.editor_background,
                day.background,
            ) { v -> edit { it.copy(colors = it.colors.copy(background = v)) } }
            colour(
                R.string.editor_text,
                day.text,
                allowAuto = true,
            ) { v -> edit { it.copy(colors = it.colors.copy(text = v)) } }
            colour(R.string.editor_text_secondary, day.textSecondary, allowAuto = true) { v ->
                edit { it.copy(colors = it.colors.copy(textSecondary = v)) }
            }
            colour(R.string.editor_accent, day.accent) { v -> edit { it.copy(colors = it.colors.copy(accent = v)) } }

            val night = theme.darkColors
            add(
                Row(
                    getString(R.string.editor_night_own),
                    getString(R.string.editor_night_own_detail),
                    checked = night != null,
                ),
            ) {
                edit { current ->
                    current.copy(
                        darkColors =
                            if (current.darkColors == null) {
                                ColorsOverride(
                                    current.colors.background,
                                    current.colors.text,
                                    current.colors.textSecondary,
                                    current.colors.accent,
                                )
                            } else {
                                null
                            },
                    )
                }
            }
            if (night == null) return
            add(Row(getString(R.string.editor_night), isHeader = true))
            colour(R.string.editor_background, night.background ?: day.background) { v ->
                edit { it.copy(darkColors = it.darkColors?.copy(background = v)) }
            }
            colour(R.string.editor_text, night.text ?: day.text, allowAuto = true) { v ->
                edit { it.copy(darkColors = it.darkColors?.copy(text = v)) }
            }
            colour(R.string.editor_text_secondary, night.textSecondary ?: day.textSecondary, allowAuto = true) { v ->
                edit { it.copy(darkColors = it.darkColors?.copy(textSecondary = v)) }
            }
            colour(R.string.editor_accent, night.accent ?: day.accent) { v ->
                edit { it.copy(darkColors = it.darkColors?.copy(accent = v)) }
            }
        }

        private fun typography(theme: Theme) {
            val typography = theme.typography
            add(Row(getString(R.string.editor_typography), isHeader = true))
            add(Row(getString(R.string.editor_family), typography.family)) {
                choose(R.string.editor_family, FAMILIES) { index ->
                    edit { it.copy(typography = it.typography.copy(family = FAMILIES[index])) }
                }
            }
            add(Row(getString(R.string.editor_weight), typography.weight.toString())) {
                choose(R.string.editor_weight, WEIGHTS.map { it.toString() }) { index ->
                    edit { it.copy(typography = it.typography.copy(weight = WEIGHTS[index])) }
                }
            }
            add(Row(getString(R.string.editor_italic), checked = typography.italic)) {
                edit { it.copy(typography = it.typography.copy(italic = !typography.italic)) }
            }
            val sizes = typography.sizes
            size(R.string.editor_size_home, sizes.homeSp) { v ->
                edit { it.copy(typography = it.typography.copy(sizes = it.typography.sizes.copy(homeSp = v))) }
            }
            size(R.string.editor_size_drawer, sizes.drawerSp) { v ->
                edit { it.copy(typography = it.typography.copy(sizes = it.typography.sizes.copy(drawerSp = v))) }
            }
            size(R.string.editor_size_clock, sizes.clockSp) { v ->
                edit { it.copy(typography = it.typography.copy(sizes = it.typography.sizes.copy(clockSp = v))) }
            }
            size(R.string.editor_size_info, sizes.infoSp) { v ->
                edit { it.copy(typography = it.typography.copy(sizes = it.typography.sizes.copy(infoSp = v))) }
            }
        }

        private fun layout(theme: Theme) {
            val layout = theme.layout
            add(Row(getString(R.string.editor_layout), isHeader = true))
            add(Row(getString(R.string.editor_halign), getString(H_ALIGNMENTS.getValue(layout.hAlign)))) {
                choose(R.string.editor_halign, H_ALIGNMENTS.values.map { getString(it) }) { index ->
                    edit { it.copy(layout = it.layout.copy(hAlign = H_ALIGNMENTS.keys.toList()[index])) }
                }
            }
            add(Row(getString(R.string.editor_valign), getString(V_ALIGNMENTS.getValue(layout.vAlign)))) {
                choose(R.string.editor_valign, V_ALIGNMENTS.values.map { getString(it) }) { index ->
                    edit { it.copy(layout = it.layout.copy(vAlign = V_ALIGNMENTS.keys.toList()[index])) }
                }
            }
            pixels(R.string.editor_padding_h, layout.paddingDp.h) { v ->
                edit { it.copy(layout = it.layout.copy(paddingDp = it.layout.paddingDp.copy(h = v))) }
            }
            pixels(R.string.editor_padding_v, layout.paddingDp.v) { v ->
                edit { it.copy(layout = it.layout.copy(paddingDp = it.layout.paddingDp.copy(v = v))) }
            }
            pixels(R.string.editor_row_gap, layout.rowGapDp) { v ->
                edit { it.copy(layout = it.layout.copy(rowGapDp = v)) }
            }
        }

        private fun badge(theme: Theme) {
            val badge = theme.badge
            add(Row(getString(R.string.editor_badge), isHeader = true))
            add(Row(getString(R.string.editor_badge_style), getString(BADGE_STYLES.getValue(badge.style)))) {
                choose(R.string.editor_badge_style, BADGE_STYLES.values.map { getString(it) }) { index ->
                    edit { it.copy(badge = it.badge.copy(style = BADGE_STYLES.keys.toList()[index])) }
                }
            }
            add(Row(getString(R.string.editor_badge_position), getString(BADGE_SIDES.getValue(badge.position)))) {
                choose(R.string.editor_badge_position, BADGE_SIDES.values.map { getString(it) }) { index ->
                    edit { it.copy(badge = it.badge.copy(position = BADGE_SIDES.keys.toList()[index])) }
                }
            }
            add(Row(getString(R.string.editor_badge_scale), badge.scale.toString())) {
                askNumber(R.string.editor_badge_scale, badge.scale.toString(), decimal = true) { v ->
                    edit { it.copy(badge = it.badge.copy(scale = v)) }
                }
            }
            colour(R.string.editor_badge_background, badge.background, allowAuto = true) { v ->
                edit { it.copy(badge = it.badge.copy(background = v)) }
            }
            colour(R.string.editor_badge_text, badge.text, allowAuto = true) { v ->
                edit { it.copy(badge = it.badge.copy(text = v)) }
            }
        }

        private fun colour(
            titleRes: Int,
            value: String,
            allowAuto: Boolean = false,
            apply: (String) -> Unit,
        ) {
            val shown = if (value == ColorValue.AUTO) getString(R.string.editor_auto) else value
            add(Row(getString(titleRes), shown)) { askColour(titleRes, value, allowAuto, apply) }
        }

        private fun size(
            titleRes: Int,
            value: Float,
            apply: (Float) -> Unit,
        ) {
            add(Row(getString(titleRes), getString(R.string.editor_sp, trim(value)))) {
                askNumber(titleRes, trim(value), decimal = true, apply)
            }
        }

        private fun pixels(
            titleRes: Int,
            value: Int,
            apply: (Int) -> Unit,
        ) {
            add(Row(getString(titleRes), getString(R.string.editor_dp, value))) {
                askNumber(titleRes, value.toString(), decimal = false) { apply(it.toInt()) }
            }
        }

        private fun trim(value: Float): String =
            if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

        private fun add(
            row: Row,
            tap: () -> Unit = {},
        ) {
            rows += row
            taps += tap
        }

        private fun edit(transform: (Theme) -> Theme) {
            settings.update { it.copy(theme = ThemeEdits.edit(it.theme, transform)) }
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
                        )?.use { it.readAtMost(MAX_IMPORT_BYTES).toString(Charsets.UTF_8) }
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
                    // Whoever notices the grant first acts on it: this screen may be gone by the
                    // time the user comes back, and then the home screen's onResume is all there is.
                    if (spec.kind == ConsentKind.NOTIFICATION_ACCESS) atomicApp.badges.awaitingGrant = true
                    return
                } catch (e: ActivityNotFoundException) {
                    Logs.w(TAG, "no activity for ${intent.action}", e)
                }
            }
            toast(getString(R.string.grant_no_settings))
        }

        /**
         * Called when Settings hands the user back: check rather than assume. Turning badges on
         * is the controller's job (it has already run in onResume, and it runs even when this
         * screen did not survive); this only says what happened and gets out of the way.
         */
        fun onReturn() {
            if (!handedOver) return
            handedOver = false
            if (spec.isGranted(this@SettingsActivity)) {
                toast(getString(spec.grantedRes))
                pop()
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

    /** Hands the theme to another app as a file it may read for as long as the share lasts. */
    private fun shareThemeAsFile() {
        val theme = settings.settings.theme
        Threads.io.post {
            val uri = ThemeFiles.share(this, theme)
            Threads.main.post {
                if (uri == null) {
                    toast(getString(R.string.theme_share_failed))
                } else {
                    start(
                        Intent.createChooser(
                            ThemeFiles.sendIntent(uri, theme.meta.name),
                            getString(R.string.theme_share_file),
                        ),
                    )
                }
            }
        }
    }

    /** The whole theme, packed into a link short enough to paste into a message. */
    private fun shareThemeAsLink() {
        val link = ThemeLink.write(settings.settings.theme)
        if (link == null) {
            toast(getString(R.string.theme_link_too_long))
            return
        }
        start(Intent.createChooser(ThemeFiles.sendLinkIntent(link), getString(R.string.theme_share_link)))
    }

    private fun writeThemeTo(uri: Uri) {
        val text = SettingsCodec.encodeTheme(settings.settings.theme)
        Threads.io.post {
            val written =
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                } catch (e: IOException) {
                    Logs.w(TAG, "could not write the theme", e)
                    false
                } catch (e: SecurityException) {
                    Logs.w(TAG, "not allowed to write the theme", e)
                    false
                }
            Threads.main.post {
                toast(getString(if (written) R.string.theme_saved else R.string.theme_share_failed))
            }
        }
    }

    /** A theme is read and shown by the screen that reads every theme, wherever it came from. */
    private fun openTheme(uri: Uri) {
        start(
            Intent(Intent.ACTION_VIEW, uri)
                .setClass(this, ImportActivity::class.java)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.theme_no_app))
        }
    }

    private companion object {
        /** The four ways to fill the screen behind the names, in the order they are offered. */
        val MODES =
            listOf(
                Triple(BackgroundMode.COLOR, R.string.background_color, R.string.background_color_detail),
                Triple(BackgroundMode.GRADIENT, R.string.background_gradient, R.string.background_gradient_detail),
                Triple(BackgroundMode.WALLPAPER, R.string.background_wallpaper, R.string.background_wallpaper_detail),
                Triple(
                    BackgroundMode.COLLECTION,
                    R.string.background_collection,
                    R.string.background_collection_detail,
                ),
            )

        val DIRECTIONS =
            listOf(
                0 to R.string.background_direction_down,
                90 to R.string.background_direction_right,
                180 to R.string.background_direction_up,
                270 to R.string.background_direction_left,
            )

        val INTERVALS =
            listOf(
                15 to R.string.background_interval_15,
                30 to R.string.background_interval_30,
                60 to R.string.background_interval_60,
                180 to R.string.background_interval_180,
                360 to R.string.background_interval_360,
                720 to R.string.background_interval_720,
                1440 to R.string.background_interval_1440,
            )

        val DIMS = listOf(0, 15, 25, 35, 50, 65, 80)

        val INFO_LINES =
            listOf(
                InfoLineId.CLOCK to R.string.info_clock,
                InfoLineId.DATE to R.string.info_date,
                InfoLineId.BATTERY to R.string.info_battery,
                InfoLineId.SCREEN_TIME to R.string.info_screen_time,
            )

        val FAMILIES = listOf("sans-serif", "serif", "monospace", "sans-serif-condensed", "cursive")

        val WEIGHTS = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

        val H_ALIGNMENTS =
            linkedMapOf(
                HAlign.START to R.string.editor_halign_start,
                HAlign.CENTER to R.string.editor_halign_center,
                HAlign.END to R.string.editor_halign_end,
            )

        val V_ALIGNMENTS =
            linkedMapOf(
                VAlign.TOP to R.string.editor_valign_top,
                VAlign.CENTER to R.string.editor_valign_center,
                VAlign.BOTTOM to R.string.editor_valign_bottom,
            )

        val BADGE_STYLES =
            linkedMapOf(
                BadgeStyle.CIRCLE to R.string.editor_badge_circle,
                BadgeStyle.DOT to R.string.editor_badge_dot,
                BadgeStyle.NUMBER to R.string.editor_badge_number,
            )

        val BADGE_SIDES =
            linkedMapOf(
                BadgePosition.START to R.string.editor_badge_start,
                BadgePosition.END to R.string.editor_badge_end,
            )

        const val PERCENT = 100

        fun directionLabel(angle: Int): Int =
            DIRECTIONS.minByOrNull { kotlin.math.abs(it.first - angle) }?.second
                ?: R.string.background_direction_down

        fun intervalLabel(minutes: Int): Int =
            INTERVALS.minByOrNull { kotlin.math.abs(it.first - minutes) }?.second
                ?: R.string.background_interval_360

        const val TAG = "SettingsActivity"
        const val STATE_STACK = "stack"
        const val STATE_ARGS = "args"
        const val STATE_SCROLL = "scroll"

        /** The haptics and edge rows sit above the list of surfaces. */
        const val GESTURE_HEADER_ROWS = 2
        const val MAX_IMPORT_BYTES = 1_000_000
    }
}
