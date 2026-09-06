package io.github.subhaneetshrestha.atomic.search

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.search.SearchIndex
import io.github.subhaneetshrestha.atomic.core.search.SearchResult
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.home.ThemeApplier
import io.github.subhaneetshrestha.atomic.settings.HomeSettings

/**
 * The search field and its results, and with nothing typed, the list of every app. The field sits
 * at the bottom by the thumb and the results run upwards from it, so the best match is the nearest
 * thing to reach.
 *
 * With one match left it opens straight away, which is the whole point of a two-letter search, but
 * never while an input method is still composing a word: a Japanese or Chinese keyboard builds one
 * character from several keystrokes, and each of those is a text change like any other.
 */
class SearchOverlay(
    context: Context,
    private val applier: ThemeApplier,
) : LinearLayout(context) {
    var onLaunch: ((AppKey) -> Unit)? = null
    var onWebSearch: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val list = ListView(context)
    private val field = EditText(context)
    private val adapter = Results()
    private var index: SearchIndex<AppKey>? = null
    private var settings = HomeSettings()
    private var colors: ResolvedColors? = null
    private var showWebSearch = false
    private var rows: List<Row> = emptyList()

    val isOpen: Boolean get() = visibility == VISIBLE

    private sealed class Row {
        data class App(
            val result: SearchResult<AppKey>,
        ) : Row()

        data class Web(
            val query: String,
        ) : Row()

        data object Empty : Row()
    }

    init {
        orientation = VERTICAL
        visibility = GONE
        isClickable = true
        setOnClickListener { close() }

        list.adapter = adapter
        list.divider = null
        list.dividerHeight = 0
        // The list grows upwards from the field, so the best match is closest to it.
        list.isStackFromBottom = true
        list.isVerticalScrollBarEnabled = false
        list.setOnItemClickListener { _, _, position, _ -> choose(rows[position]) }

        field.setHint(R.string.search_hint)
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        field.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_FULLSCREEN
        field.setSingleLine(true)
        field.setBackgroundColor(0)
        field.setOnEditorActionListener { _, _, _ ->
            rows.firstOrNull()?.let(::choose)
            true
        }
        field.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(s: Editable?) = search()
            },
        )

        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val room =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime(),
                )
            view.setPadding(room.left + applier.dp(20), room.top, room.right + applier.dp(20), room.bottom)
            insets
        }
    }

    fun open(
        index: SearchIndex<AppKey>,
        settings: HomeSettings,
        colors: ResolvedColors,
        withKeyboard: Boolean,
    ) {
        this.index = index
        this.settings = settings
        this.colors = colors
        showWebSearch = settings.webSearchFallback
        setBackgroundColor(colors.background)
        applier.applyText(field, settings.font, settings.drawerTextSizeSp, colors.text, Gravity.START)
        field.setHintTextColor(colors.textSecondary)
        field.setText("")
        visibility = VISIBLE
        search()
        if (withKeyboard && settings.autoShowKeyboard) showKeyboard() else field.clearFocus()
    }

    fun close() {
        if (!isOpen) return
        visibility = GONE
        field.setText("")
        hideKeyboard()
        onClose?.invoke()
    }

    /** After a rotation the keyboard has to be asked for again. */
    fun refreshKeyboard() {
        if (isOpen && settings.autoShowKeyboard) showKeyboard()
    }

    private fun showKeyboard() {
        field.requestFocus()
        ViewCompat.getWindowInsetsController(field)?.show(WindowInsetsCompat.Type.ime())
    }

    private fun hideKeyboard() {
        ViewCompat.getWindowInsetsController(field)?.hide(WindowInsetsCompat.Type.ime())
        field.clearFocus()
    }

    private fun search() {
        val index = index ?: return
        val query = field.text.toString()
        val found = index.query(query)
        rows =
            when {
                found.isNotEmpty() -> found.map(Row::App)
                query.isBlank() -> emptyList()
                showWebSearch -> listOf(Row.Web(query))
                else -> listOf(Row.Empty)
            }
        adapter.notifyDataSetChanged()
        if (settings.autoLaunchSingle && found.size == 1 && query.isNotBlank() && !isComposing()) {
            choose(rows.first())
        }
    }

    private fun isComposing(): Boolean = BaseInputConnection.getComposingSpanStart(field.editableText) != -1

    private fun choose(row: Row) {
        when (row) {
            is Row.App -> {
                val key = row.result.key
                close()
                onLaunch?.invoke(key)
            }

            is Row.Web -> {
                val query = row.query
                close()
                onWebSearch?.invoke(query)
            }

            Row.Empty -> {
                Unit
            }
        }
    }

    /** The rows, nearest the field first, which is how the list is stacked. */
    private inner class Results : BaseAdapter() {
        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): Row = rows[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun isEnabled(position: Int): Boolean = rows[position] !is Row.Empty

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val view = convertView as? TextView ?: newRow()
            val shade = colors ?: return view
            when (val row = rows[position]) {
                is Row.App -> {
                    applier.applyText(view, settings.font, settings.drawerTextSizeSp, shade.text, Gravity.START)
                    view.text = row.result.label
                }

                is Row.Web -> {
                    applier.applyText(
                        view,
                        settings.font,
                        settings.drawerTextSizeSp,
                        shade.textSecondary,
                        Gravity.START,
                    )
                    view.text = context.getString(R.string.search_web, row.query)
                }

                Row.Empty -> {
                    applier.applyText(
                        view,
                        settings.font,
                        settings.drawerTextSizeSp,
                        shade.textSecondary,
                        Gravity.START,
                    )
                    view.setText(R.string.search_no_results)
                }
            }
            return view
        }

        private fun newRow(): TextView =
            TextView(context).apply {
                layoutParams =
                    AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT,
                    )
                setSingleLine(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            }
    }
}
