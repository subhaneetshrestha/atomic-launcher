package io.github.subhaneetshrestha.atomic.apps

/**
 * Identity of a launchable activity: its component plus the serial of the user profile it lives
 * in. Pure Kotlin so it can be persisted and unit-tested; the UserHandle behind the serial is
 * resolved by [AppRepository]. v1 only ever sees the current user, but keying by user now means
 * work profiles and private space can be added later without a schema break.
 */
data class AppKey(val packageName: String, val activityName: String, val userSerial: Long) {
    val flattenedComponent: String
        get() = "$packageName/$activityName"
}
