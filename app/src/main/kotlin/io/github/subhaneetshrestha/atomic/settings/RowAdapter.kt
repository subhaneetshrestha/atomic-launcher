package io.github.subhaneetshrestha.atomic.settings

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.TextView
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors

/** A settings row: a label, an optional detail line, and an optional check state (null = plain row). */
data class Row(
    val label: String,
    val detail: String? = null,
    val checked: Boolean? = null,
    val singleChoice: Boolean = false,
    /** A heading between groups of rows, not something to choose. */
    val isHeader: Boolean = false,
    /** Shown, but dimmed: tapping it explains why it cannot be chosen. */
    val enabled: Boolean = true,
)

/** Themed rows over the platform list layouts; check state is owned here, not by ListView's choice mode. */
class RowAdapter(
    private val context: Context,
    private val colors: ResolvedColors,
    var rows: List<Row>,
) : BaseAdapter() {
    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): Row = rows[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 4

    override fun getItemViewType(position: Int): Int =
        when {
            rows[position].isHeader -> 3
            rows[position].checked == null -> 0
            rows[position].singleChoice -> 2
            else -> 1
        }

    override fun areAllItemsEnabled(): Boolean = true

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val row = rows[position]
        val layout =
            when (getItemViewType(position)) {
                0 -> android.R.layout.simple_list_item_2
                2 -> android.R.layout.simple_list_item_single_choice
                3 -> android.R.layout.simple_list_item_1
                else -> android.R.layout.simple_list_item_multiple_choice
            }
        val view = convertView ?: LayoutInflater.from(context).inflate(layout, parent, false)
        if (row.isHeader) {
            val title = view.findViewById<TextView>(android.R.id.text1)
            style(title, colors.textSecondary, 13f)
            title.text = row.label.uppercase()
            view.alpha = 1f
            view.minimumHeight = (40 * context.resources.displayMetrics.density).toInt()
            return view
        }
        if (row.checked == null) {
            val title = view.findViewById<TextView>(android.R.id.text1)
            val detail = view.findViewById<TextView>(android.R.id.text2)
            style(title, colors.text, 18f)
            style(detail, colors.textSecondary, 14f)
            title.text = row.label
            detail.text = row.detail
            detail.visibility = if (row.detail == null) View.GONE else View.VISIBLE
        } else {
            val checkable = view as CheckedTextView
            style(checkable, colors.text, 18f)
            checkable.text = if (row.detail == null) row.label else "${row.label}\n${row.detail}"
            checkable.isChecked = row.checked
        }
        view.minimumHeight = (56 * context.resources.displayMetrics.density).toInt()
        view.alpha = if (row.enabled) 1f else DIMMED
        return view
    }

    private companion object {
        const val DIMMED = 0.45f
    }

    private fun style(
        view: TextView,
        color: Int,
        sizeSp: Float,
    ) {
        view.setTextColor(color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
}
