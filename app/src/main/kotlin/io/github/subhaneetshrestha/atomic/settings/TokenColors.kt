package io.github.subhaneetshrestha.atomic.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build

/** Resolves `system_*` Material You colour names to the device's values on Android 12+, null elsewhere. */
class TokenColors(
    private val context: Context,
) : (String) -> Int? {
    @SuppressLint("DiscouragedApi")
    override fun invoke(name: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val id = context.resources.getIdentifier(name, "color", "android")
        return if (id == 0) null else context.getColor(id)
    }
}
