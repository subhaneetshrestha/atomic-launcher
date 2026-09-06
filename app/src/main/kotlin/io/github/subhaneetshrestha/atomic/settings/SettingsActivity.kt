package io.github.subhaneetshrestha.atomic.settings

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.ThemedActivity
import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.theme.BuiltinThemes
import io.github.subhaneetshrestha.atomic.core.theme.DecodeResult
import io.github.subhaneetshrestha.atomic.core.theme.HomeLimits
import io.github.subhaneetshrestha.atomic.core.theme.NightMode
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.SettingsCodec
import io.github.subhaneetshrestha.atomic.core.theme.SettingsEdits
import io.github.subhaneetshrestha.atomic.home.HomeListModel
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
        push(MenuScreen())
    }

    override fun onStart() {
        super.onStart()
        settings.addDocumentListener(documentListener)
    }

    override fun onStop() {
        settings.removeDocumentListener(documentListener)
        settings.flush()
        super.onStop()
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

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    // ---- screens ------------------------------------------------------------------------------

    private abstract inner class Screen(
        val titleRes: Int,
    ) {
        abstract fun createView(): View

        open fun refresh() = Unit
    }

    private inner class MenuScreen : Screen(R.string.settings_title) {
        override fun createView(): View {
            val items =
                listOf(
                    R.string.settings_home_apps to { push(HomeAppsScreen()) },
                    R.string.settings_hidden_apps to { push(HiddenAppsScreen()) },
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
    private inner class HomeAppsScreen : Screen(R.string.settings_home_apps) {
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

    private inner class HiddenAppsScreen : Screen(R.string.settings_hidden_apps) {
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

    private inner class ThemeScreen : Screen(R.string.settings_theme) {
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

    private inner class AppearanceScreen : Screen(R.string.settings_appearance) {
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

    private inner class BackupScreen : Screen(R.string.settings_backup) {
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

    private inner class AboutScreen : Screen(R.string.settings_about) {
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
        const val MAX_IMPORT_BYTES = 1_000_000
    }
}
