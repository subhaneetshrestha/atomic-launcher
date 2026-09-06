package io.github.subhaneetshrestha.atomic.apps

import android.os.Process
import android.os.UserHandle

/**
 * Which user profiles the launcher enumerates. v1 lists the current user only; work profile and
 * private space support plugs in here (LauncherApps.getProfiles) without touching the rest.
 */
fun interface ProfileSource {
    fun profiles(): List<UserHandle>

    object CurrentUserOnly : ProfileSource {
        override fun profiles(): List<UserHandle> = listOf(Process.myUserHandle())
    }
}
