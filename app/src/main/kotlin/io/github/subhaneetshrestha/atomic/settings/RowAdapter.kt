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

    override fun getViewTypeCount(): Int = 3

    override fun getItemViewType(position: Int): Int =
        when {
            rows[position].checked == null -> 0
            rows[position].singleChoice -> 2
            else -> 1
        }

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
                else -> android.R.layout.simple_list_item_multiple_choice
            }
        val view = convertView ?: LayoutInflater.from(context).inflate(layout, parent, false)
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
        return view
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
