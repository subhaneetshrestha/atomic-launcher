package io.github.subhaneetshrestha.atomic.share

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.ThemedActivity
import io.github.subhaneetshrestha.atomic.background.ImageFetcher
import io.github.subhaneetshrestha.atomic.background.launcherUserAgent
import io.github.subhaneetshrestha.atomic.core.collections.UrlRules
import io.github.subhaneetshrestha.atomic.core.theme.LinkContent
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.Theme
import io.github.subhaneetshrestha.atomic.core.theme.ThemeImport
import io.github.subhaneetshrestha.atomic.core.theme.ThemeLink
import io.github.subhaneetshrestha.atomic.home.ThemeApplier
import io.github.subhaneetshrestha.atomic.settings.FontSpec
import io.github.subhaneetshrestha.atomic.settings.NightModes
import io.github.subhaneetshrestha.atomic.settings.toAlignment
import io.github.subhaneetshrestha.atomic.util.Logs
import io.github.subhaneetshrestha.atomic.util.Threads
import io.github.subhaneetshrestha.atomic.util.readAtMost
import java.io.IOException

/**
 * A theme that arrived from somewhere else: a file, a share, or a link. It is never applied on
 * arrival — a theme changes every colour on the screen and may bring an address the launcher would
 * fetch images from, so this screen shows what it is, what it would do, and anything that had to
 * be corrected in it, and waits to be told.
 */
class ImportActivity : ThemedActivity() {
    private lateinit var colors: ResolvedColors
    private lateinit var applier: ThemeApplier
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        colors = atomicApp.resolvedColors(this)
        applier = ThemeApplier(this)
        window.setBackgroundDrawable(ColorDrawable(colors.background))
        column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(24))
            }
        val scroll = ScrollView(this).apply { addView(column) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        waiting()
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        waiting()
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val data = intent.data
        when {
            data != null && data.scheme.equals(ThemeLink.SCHEME, ignoreCase = true) -> {
                fromLink(data.toString())
            }

            intent.action == Intent.ACTION_SEND -> {
                fromUri(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            }

            else -> {
                fromUri(data)
            }
        }
    }

    private fun fromLink(link: String) {
        when (val content = ThemeLink.read(link)) {
            is LinkContent.Document -> read(content.json)
            is LinkContent.Address -> askBeforeFetching(content.url)
            is LinkContent.Problem -> refuse(content.reason)
            null -> refuse(getString(R.string.import_not_a_theme))
        }
    }

    private fun fromUri(uri: Uri?) {
        if (uri == null) {
            refuse(getString(R.string.import_not_a_theme))
            return
        }
        Threads.io.post {
            val text =
                try {
                    contentResolver.openInputStream(uri)?.use {
                        it.readAtMost(ThemeLink.MAX_JSON_BYTES).toString(Charsets.UTF_8)
                    }
                } catch (e: IOException) {
                    Logs.w(TAG, "could not read the theme", e)
                    null
                } catch (e: SecurityException) {
                    Logs.w(TAG, "not allowed to read the theme", e)
                    null
                }
            Threads.main.post { if (text == null) refuse(getString(R.string.import_unreadable)) else read(text) }
        }
    }

    /** A link that names an address downloads nothing until the user has seen where from. */
    private fun askBeforeFetching(url: String) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.import_title)
            .setMessage(getString(R.string.import_fetch_from, UrlRules.hostOf(url).orEmpty()))
            .setPositiveButton(R.string.import_fetch) { _, _ -> fetch(url) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun fetch(url: String) {
        waiting()
        Threads.io.post {
            val outcome =
                ImageFetcher(launcherUserAgent(this)).fetchIndex(url, maxBytes = ThemeLink.MAX_FETCH_BYTES)
            Threads.main.post {
                when (outcome) {
                    is ImageFetcher.Outcome.Index -> read(outcome.body)
                    is ImageFetcher.Outcome.Failed -> refuse(getString(R.string.import_fetch_failed, outcome.reason))
                    else -> refuse(getString(R.string.import_unreadable))
                }
            }
        }
    }

    private fun read(json: String) {
        when (val outcome = ThemeImport.read(json, atomicApp.crashEnvironment.versionCode)) {
            is ThemeImport.Outcome.Ready -> offer(outcome)
            is ThemeImport.Outcome.Refused -> refuse(outcome.reason)
        }
    }

    private fun offer(outcome: ThemeImport.Outcome.Ready) {
        val preview = outcome.preview
        column.removeAllViews()
        column.addView(sample(outcome.theme))
        heading(preview.name)
        listOfNotNull(preview.author?.let { getString(R.string.import_by, it) }, preview.license)
            .takeIf { it.isNotEmpty() }
            ?.let { paragraph(it.joinToString(" · "), colors.textSecondary) }
        preview.description?.let { paragraph(it, colors.text) }
        preview.imageHost?.let { paragraph(getString(R.string.import_images_from, it), colors.text) }
        if (preview.warnings.isNotEmpty()) {
            paragraph(getString(R.string.import_corrected, preview.warnings.size), colors.textSecondary)
            for (warning in preview.warnings.take(MAX_WARNINGS_SHOWN)) {
                paragraph("${warning.path}: ${warning.message}", colors.textSecondary, small = true)
            }
        }
        action(getString(R.string.import_apply)) { apply(outcome.theme) }
        action(getString(R.string.import_not_now)) { finish() }
    }

    private fun apply(theme: Theme) {
        atomicApp.settingsRepository.update { it.copy(theme = theme) }
        atomicApp.settingsRepository.flush()
        Toast.makeText(this, getString(R.string.import_applied, theme.meta.name), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun refuse(reason: String) {
        column.removeAllViews()
        heading(getString(R.string.import_title))
        paragraph(reason, colors.text)
        action(getString(R.string.import_close)) { finish() }
    }

    private fun waiting() {
        column.removeAllViews()
        heading(getString(R.string.import_title))
        paragraph(getString(R.string.import_reading), colors.textSecondary)
    }

    /** A look at the theme itself, which is the only part of this that is not words. */
    private fun sample(theme: Theme): View {
        val night = NightModes.isNight(this)
        val sampled = atomicApp.themeResolver.resolve(theme, night)
        val font =
            FontSpec(theme.typography.family, theme.typography.weight, theme.typography.italic)
        val block =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(sampled.background)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(SAMPLE_HEIGHT_DP)
            }
        val gravity = applier.horizontalGravity(theme.layout.hAlign.toAlignment())
        block.addView(
            TextView(this).apply {
                text = getString(R.string.import_sample_clock)
                applier.applyText(this, font, theme.typography.sizes.clockSp / 2f, sampled.text, gravity, 0)
            },
        )
        for (name in listOf(R.string.import_sample_one, R.string.import_sample_two)) {
            block.addView(
                TextView(this).apply {
                    text = getString(name)
                    applier.applyText(this, font, theme.typography.sizes.homeSp, sampled.text, gravity, 0)
                },
            )
        }
        block.addView(
            TextView(this).apply {
                text = getString(R.string.import_sample_info)
                applier.applyText(this, font, theme.typography.sizes.infoSp, sampled.textSecondary, gravity, 0)
            },
        )
        return block
    }

    private fun heading(text: String) {
        column.addView(
            TextView(this).apply {
                this.text = text
                setTextColor(colors.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(0, dp(16), 0, dp(4))
            },
        )
    }

    private fun paragraph(
        text: String,
        color: Int,
        small: Boolean = false,
    ) {
        column.addView(
            TextView(this).apply {
                this.text = text
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 13f else 16f)
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(4), 0, dp(4))
            },
        )
    }

    private fun action(
        label: String,
        run: () -> Unit,
    ) {
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SAMPLE_HEIGHT_DP = 200
        const val MAX_WARNINGS_SHOWN = 6
        const val TAG = "ImportActivity"
    }
}
