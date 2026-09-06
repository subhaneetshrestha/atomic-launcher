package io.github.subhaneetshrestha.atomic.home

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.notifications.BadgeDrawable
import io.github.subhaneetshrestha.atomic.settings.HomeSettings

/**
 * The home list: a vertical LinearLayout of TextView rows. For 1 to 16 rows this beats ListView
 * and RecyclerView: no adapter, no nested scrolling to fight swipe gestures, zero dependencies,
 * and every row is a plain clickable, focusable view for TalkBack.
 */
class HomeListView(
    context: Context,
    private val applier: ThemeApplier,
) : LinearLayout(context) {
    var onRowClick: ((AppEntry, View) -> Unit)? = null
    var onRowLongClick: ((AppEntry, View) -> Boolean)? = null

    /** How many notifications are waiting for an app; nought means no badge. */
    var badgeCount: ((AppEntry) -> Int)? = null

    private val badges = ArrayList<BadgeDrawable>()

    init {
        orientation = VERTICAL
    }

    fun render(
        rows: List<HomeRow>,
        settings: HomeSettings,
        colors: ResolvedColors,
    ) {
        gravity = applier.horizontalGravity(settings.horizontalAlignment)
        while (childCount > rows.size) removeViewAt(childCount - 1)
        while (childCount < rows.size) addView(newRow())

        val gap = applier.dp(settings.rowGapDp)
        while (badges.size < rows.size) badges += BadgeDrawable()
        rows.forEachIndexed { index, row ->
            val view = getChildAt(index) as TextView
            view.tag = row.entry
            view.text = row.label
            view.alpha = if (row.entry.isSuspended) SUSPENDED_ALPHA else 1f
            applier.applyRow(view, settings, colors)
            applier.applyBadge(view, badges[index], badgeCount?.invoke(row.entry) ?: 0, settings, colors)
            (view.layoutParams as LayoutParams).topMargin = if (index == 0) 0 else gap
        }
        requestLayout()
    }

    private fun newRow(): TextView =
        TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            setOnClickListener { view ->
                val entry = view.tag as? AppEntry ?: return@setOnClickListener
                onRowClick?.invoke(entry, view)
            }
            setOnLongClickListener { view ->
                val entry = view.tag as? AppEntry ?: return@setOnLongClickListener false
                onRowLongClick?.invoke(entry, view) ?: false
            }
        }

    private companion object {
        const val SUSPENDED_ALPHA = 0.5f
    }
}
