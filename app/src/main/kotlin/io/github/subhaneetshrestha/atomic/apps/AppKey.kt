package io.github.subhaneetshrestha.atomic.apps

/**
 * Identity of a launchable activity: its component plus the serial of the user profile it lives
 * in. Pure Kotlin so it can be persisted and unit-tested; the UserHandle behind the serial is
 * resolved by [AppRepository]. v1 only ever sees the current user, but keying by user now means
 * work profiles and private space can be added later without a schema break.
 */
data class AppKey(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
) {
    val flattenedComponent: String
        get() = "$packageName/$activityName"

    companion object {
        /** Parses `package/class`; a class starting with `.` is relative to the package. Null when malformed. */
        fun fromComponent(
            component: String,
            userSerial: Long,
        ): AppKey? {
            val slash = component.indexOf('/')
            if (slash <= 0 || slash == component.lastIndex) return null
            val packageName = component.substring(0, slash)
            val rawClass = component.substring(slash + 1)
            if (packageName.any { it.isWhitespace() } || rawClass.any { it.isWhitespace() }) return null
            val activityName = if (rawClass.startsWith(".")) packageName + rawClass else rawClass
            return AppKey(packageName, activityName, userSerial)
        }
    }
}
