package io.github.subhaneetshrestha.atomic.settings

import io.github.subhaneetshrestha.atomic.apps.AppKey
import io.github.subhaneetshrestha.atomic.core.theme.AppRef
import io.github.subhaneetshrestha.atomic.core.theme.HAlign
import io.github.subhaneetshrestha.atomic.core.theme.Settings
import io.github.subhaneetshrestha.atomic.core.theme.VAlign

/** The home screen's view of the settings document: keys parsed, labels merged, theme layout flattened. */
fun Settings.toHomeSettings(): HomeSettings {
    val overrides = LinkedHashMap<AppKey, String>()
    for (rename in renames) {
        AppKey.fromComponent(rename.component, rename.user)?.let { overrides[it] = rename.label }
    }
    val homeApps =
        home.entries.mapNotNull { entry ->
            AppKey.fromComponent(entry.component, entry.user)?.also { key ->
                entry.label?.let { overrides[key] = it }
            }
        }
    return HomeSettings(
        homeApps = homeApps,
        homeAppCount = home.fallbackCount,
        labelOverrides = overrides,
        hidden = hidden.mapNotNull { AppKey.fromComponent(it.component, it.user) }.toSet(),
        horizontalAlignment =
            when (theme.layout.hAlign) {
                HAlign.START -> HorizontalAlignment.START
                HAlign.CENTER -> HorizontalAlignment.CENTER
                HAlign.END -> HorizontalAlignment.END
            },
        verticalPosition =
            when (theme.layout.vAlign) {
                VAlign.TOP -> VerticalPosition.TOP
                VAlign.CENTER -> VerticalPosition.CENTER
                VAlign.BOTTOM -> VerticalPosition.BOTTOM
            },
        textSizeSp = theme.typography.sizes.homeSp,
        font =
            FontSpec(
                family = theme.typography.family,
                weight = theme.typography.weight,
                italic = theme.typography.italic,
            ),
        rowGapDp = theme.layout.rowGapDp,
        horizontalPaddingDp = theme.layout.paddingDp.h,
        verticalPaddingDp = theme.layout.paddingDp.v,
    )
}

/** The persisted form of an app identity: flattened component plus user serial. */
fun AppKey.toRef(): AppRef = AppRef(flattenedComponent, userSerial)
