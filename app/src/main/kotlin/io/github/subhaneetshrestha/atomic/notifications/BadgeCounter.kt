package io.github.subhaneetshrestha.atomic.notifications

/** Which app a badge belongs to. The profile is part of it, so work and personal count apart. */
data class BadgeKey(
    val packageName: String,
    val userSerial: Long,
)

/**
 * Everything the counting rules need from one notification, and nothing else. In particular the
 * text itself is never carried: only whether there is any, so that a launcher which never reads
 * notification content cannot begin to.
 */
data class NotificationFacts(
    val key: String,
    val packageName: String,
    val userSerial: Long,
    val isGroupSummary: Boolean,
    /** What the system says about the channel: the user can turn badges off per channel. */
    val canShowBadge: Boolean,
    val channelId: String,
    val isOngoing: Boolean,
    val isForegroundService: Boolean,
    val category: String?,
    val isSuspended: Boolean,
    val hasTitleOrText: Boolean,
    /** What the app itself says the count is; nought means it did not say. */
    val number: Int,
)

/**
 * Counts what is worth showing beside an app's name, following the rules the stock launcher uses
 * so a badge here means what it would there: the summary standing for a group is skipped in favour
 * of the notifications under it, a channel the user has silenced does not count, and things that
 * sit in the shade rather than arriving are left out unless the user asks for them.
 */
class BadgeCounter(
    private val includeOngoing: Boolean,
) {
    fun counts(notifications: List<NotificationFacts>): Map<BadgeKey, Int> {
        val counts = LinkedHashMap<BadgeKey, Int>()
        for (notification in notifications) {
            if (!countable(notification)) continue
            val key = BadgeKey(notification.packageName, notification.userSerial)
            val running = (counts[key] ?: 0) + notification.number.coerceAtLeast(1)
            counts[key] = running.coerceAtMost(MAX)
        }
        return counts
    }

    fun countable(notification: NotificationFacts): Boolean =
        when {
            notification.isGroupSummary -> false

            !notification.canShowBadge -> false

            notification.isSuspended -> false

            !notification.hasTitleOrText -> false

            // Before channels existed everything landed here, so an ongoing notification on it is
            // as likely to be a stopwatch as anything worth a badge.
            notification.channelId == LEGACY_CHANNEL && notification.isOngoing -> false

            includeOngoing -> true

            notification.isOngoing || notification.isForegroundService -> false

            notification.category == CATEGORY_TRANSPORT -> false

            else -> true
        }

    private companion object {
        /** Where a badge stops fitting; the stock launcher stops here too. */
        const val MAX = 999

        /** NotificationChannel.DEFAULT_CHANNEL_ID: the catch-all an app without channels gets. */
        const val LEGACY_CHANNEL = "miscellaneous"

        /** Notification.CATEGORY_TRANSPORT: whatever is playing. */
        const val CATEGORY_TRANSPORT = "transport"
    }
}
