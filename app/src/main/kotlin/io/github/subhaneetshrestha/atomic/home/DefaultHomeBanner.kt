package io.github.subhaneetshrestha.atomic.home

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors

/** One quiet line at the bottom of the screen, shown only while we are not the default home app. */
class DefaultHomeBanner(
    context: Context,
    applier: ThemeApplier,
) : TextView(context) {
    init {
        setText(R.string.default_home_banner)
        gravity = Gravity.CENTER
        minHeight = applier.dp(48)
        setPadding(applier.dp(16), applier.dp(8), applier.dp(16), applier.dp(8))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        isClickable = true
        isFocusable = true
        visibility = GONE
    }

    fun applyColors(colors: ResolvedColors) {
        setTextColor(colors.textSecondary)
    }
}
