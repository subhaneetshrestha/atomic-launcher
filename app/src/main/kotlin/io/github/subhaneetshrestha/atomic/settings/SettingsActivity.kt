package io.github.subhaneetshrestha.atomic.settings

import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.ThemedActivity

/** Launcher settings. Phase 2 fills this with the screens; this is the entry point the home screen opens. */
class SettingsActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val colors = atomicApp.resolvedColors(this)
        window.decorView.setBackgroundColor(colors.background)
        setContentView(
            TextView(this).apply {
                setText(R.string.settings_title)
                setTextColor(colors.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                val pad = (48 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            },
        )
    }
}
