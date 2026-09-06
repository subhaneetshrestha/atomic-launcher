package io.github.subhaneetshrestha.atomic.home

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.apps.AppActions
import io.github.subhaneetshrestha.atomic.apps.AppEntry
import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.theme.Labels
import io.github.subhaneetshrestha.atomic.core.theme.SettingsEdits
import io.github.subhaneetshrestha.atomic.settings.SettingsRepository
import io.github.subhaneetshrestha.atomic.settings.toRef

/**
 * Long-press menu for an app row: home membership, rename, hide, app info, uninstall.
 * Shortcuts join in a later phase. Plain platform dialogs, themed by the activity.
 */
class AppMenu(
    private val activity: Activity,
    private val settings: SettingsRepository,
    private val actions: AppActions,
) {
    /** [visibleRows] are the apps currently on the home screen, used to pin the alphabetical fallback before editing. */
    fun show(
        entry: AppEntry,
        anchor: View,
        visibleRows: List<AppKey>,
    ) {
        val ref = entry.key.toRef()
        val onHome = entry.key in visibleRows
        val hidden = entry.key in settings.current.hidden
        val visibleRefs = visibleRows.map { it.toRef() }

        val items = mutableListOf<Pair<Int, () -> Unit>>()
        items +=
            (if (onHome) R.string.menu_remove_from_home else R.string.menu_add_to_home) to {
                settings.update { doc ->
                    val pinned = SettingsEdits.materializeHome(doc, visibleRefs)
                    if (onHome) SettingsEdits.removeFromHome(pinned, ref) else SettingsEdits.addToHome(pinned, ref)
                }
            }
        items += R.string.menu_rename to { showRename(entry) }
        items +=
            (if (hidden) R.string.menu_unhide else R.string.menu_hide) to
            {
                settings.update {
                    SettingsEdits.setHidden(
                        SettingsEdits.materializeHome(it, visibleRefs),
                        ref,
                        !hidden,
                    )
                }
            }
        items += R.string.menu_app_info to { actions.showAppInfo(entry, anchor) }
        if (!entry.isSystem) items += R.string.menu_uninstall to { actions.uninstall(entry) }

        AlertDialog
            .Builder(activity)
            .setTitle(entry.label)
            .setItems(items.map { activity.getString(it.first) }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun showRename(entry: AppEntry) {
        val ref = entry.key.toRef()
        val input =
            EditText(activity).apply {
                setText(SettingsEdits.labelOverride(settings.settings, ref) ?: entry.label)
                setSelectAllOnFocus(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                filters = arrayOf(InputFilter.LengthFilter(Labels.MAX_LENGTH))
            }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val container = FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0) }
        container.addView(input)
        AlertDialog
            .Builder(activity)
            .setTitle(R.string.menu_rename)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                settings.update {
                    SettingsEdits.rename(it, ref, input.text.toString())
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(
                R.string.menu_rename_reset,
            ) { _, _ -> settings.update { SettingsEdits.rename(it, ref, null) } }
            .show()
    }
}
