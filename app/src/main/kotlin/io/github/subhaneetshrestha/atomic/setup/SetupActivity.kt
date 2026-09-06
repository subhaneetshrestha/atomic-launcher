package io.github.subhaneetshrestha.atomic.setup

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
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
import io.github.subhaneetshrestha.atomic.core.theme.HomeEntry
import io.github.subhaneetshrestha.atomic.core.theme.HomeLimits
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.home.DefaultHomePrompt
import io.github.subhaneetshrestha.atomic.settings.Row
import io.github.subhaneetshrestha.atomic.settings.RowAdapter
import io.github.subhaneetshrestha.atomic.settings.SettingsRepository

/**
 * First run: pick home apps, pick a look, become the default home. Every step can be skipped and
 * every choice is saved as it is made; finishing (or skipping) marks setup done so it never
 * returns on its own. Permission disclosures are not here: they appear when a feature is enabled.
 */
class SetupActivity : ThemedActivity() {
    private val settings: SettingsRepository get() = atomicApp.settingsRepository
    private lateinit var colors: ResolvedColors
    private lateinit var heading: TextView
    private lateinit var summary: TextView
    private lateinit var content: FrameLayout
    private lateinit var next: Button
    private lateinit var defaultHome: DefaultHomePrompt
    private var step = 0
    private val chosen = LinkedHashSet<AppKey>()

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { showStep() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        colors = atomicApp.resolvedColors(this)
        window.decorView.setBackgroundColor(colors.background)
        defaultHome = DefaultHomePrompt(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        heading = text(22f, colors.text).apply { setPadding(dp(20), dp(24), dp(20), dp(4)) }
        summary = text(14f, colors.textSecondary).apply { setPadding(dp(20), 0, dp(20), dp(12)) }
        content = FrameLayout(this)
        val skip =
            Button(this).apply {
                setText(R.string.setup_skip)
                setOnClickListener { finishSetup() }
            }
        next = Button(this).apply { setOnClickListener { advance() } }
        val bar =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                addView(skip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(next, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        root.addView(heading, LinearLayout.LayoutParams(match, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(summary, LinearLayout.LayoutParams(match, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(match, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() =
                    if (step > 0) {
                        step--
                        showStep()
                    } else {
                        finishSetup()
                    }
            },
        )
        showStep()
    }

    private fun advance() {
        if (step < LAST_STEP) {
            step++
            showStep()
        } else {
            finishSetup()
        }
    }

    private fun finishSetup() {
        settings.update { it.copy(app = it.app.copy(setupDone = true)) }
        settings.flush()
        finish()
    }

    private fun showStep() {
        content.removeAllViews()
        next.setText(if (step == LAST_STEP) R.string.setup_done else R.string.setup_next)
        val view =
            when (step) {
                0 -> appsStep()
                1 -> themeStep()
                else -> defaultStep()
            }
        content.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    private fun appsStep(): View {
        heading.setText(R.string.setup_apps_title)
        summary.setText(R.string.setup_apps_summary)
        val entries: List<AppEntry> = atomicApp.appRepository.current.entries
        val adapter = RowAdapter(this, colors, entries.map { Row(it.label, checked = it.key in chosen) })
        return ListView(this).apply {
            this.adapter = adapter
            divider = null
            dividerHeight = 0
            setOnItemClickListener { _, _, position, _ ->
                val key = entries[position].key
                if (!chosen.remove(key) && chosen.size < HomeLimits.MAX_ROWS) chosen += key
                adapter.rows = entries.map { Row(it.label, checked = it.key in chosen) }
                adapter.notifyDataSetChanged()
                settings.update { doc ->
                    doc.copy(
                        home = doc.home.copy(entries = chosen.map { HomeEntry(it.flattenedComponent, it.userSerial) }),
                    )
                }
            }
        }
    }

    private fun themeStep(): View {
        heading.setText(R.string.setup_theme_title)
        summary.text = ""
        val themes = BuiltinThemes.all
        val currentId = settings.settings.theme.meta.id
        val adapter =
            RowAdapter(
                this,
                colors,
                themes.map {
                    Row(
                        it.meta.name,
                        it.meta.description,
                        checked =
                            it.meta.id == currentId,
                        singleChoice = true,
                    )
                },
            )
        return ListView(this).apply {
            this.adapter = adapter
            divider = null
            dividerHeight = 0
            setOnItemClickListener { _, _, position, _ ->
                settings.update { it.copy(theme = themes[position]) }
                recreate()
            }
        }
    }

    private fun defaultStep(): View {
        heading.setText(R.string.setup_default_title)
        summary.setText(R.string.setup_default_summary)
        val isDefault = defaultHome.isDefaultHome()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            if (isDefault) {
                addView(
                    text(18f, colors.text).apply {
                        setText(R.string.setup_is_default)
                        gravity = Gravity.CENTER
                    },
                )
            } else {
                addView(
                    Button(context).apply {
                        setText(R.string.setup_set_default)
                        setOnClickListener { defaultHome.request(roleRequest) }
                    },
                )
            }
        }
    }

    private fun text(
        sizeSp: Float,
        color: Int,
    ): TextView =
        TextView(this).apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val LAST_STEP = 2
    }
}
