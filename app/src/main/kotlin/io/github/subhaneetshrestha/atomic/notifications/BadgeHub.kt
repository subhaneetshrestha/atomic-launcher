package io.github.subhaneetshrestha.atomic.notifications

/**
 * Holds what is currently showing in the shade, as facts rather than notifications, and keeps
 * [BadgeStore] in step with it. Separate from the listener service for two reasons: a setting can
 * change with no notification in sight and the counts must follow, and the awkward moments
 * (a channel silenced while its notifications are still there) are then testable off-device.
 *
 * Nothing here is persisted or written down. The facts live as long as the binding does.
 */
class BadgeHub(
    private val store: BadgeStore,
) {
    private val facts = LinkedHashMap<String, NotificationFacts>()
    private var counter = BadgeCounter(includeOngoing = false)

    /** Count what merely sits in the shade. Changing it recounts what is already held. */
    var includeOngoing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            counter = BadgeCounter(includeOngoing = value)
            publishAll()
        }

    /** Everything outstanding, as the listener reports it on connecting. */
    fun replaceAll(all: List<NotificationFacts>) {
        facts.clear()
        for (notification in all) facts[notification.key] = notification
        publishAll()
    }

    fun posted(notification: NotificationFacts) {
        facts[notification.key] = notification
        publish(notification.packageName, notification.userSerial)
    }

    fun removed(key: String) {
        val gone = facts.remove(key) ?: return
        publish(gone.packageName, gone.userSerial)
    }

    /**
     * The ranking changed: whether a notification may show a badge can differ now, for instance
     * because the user has just silenced its channel. [canShowBadge] returns null for a key the
     * ranking no longer mentions, which is left as it was.
     */
    fun reranked(canShowBadge: (String) -> Boolean?) {
        var changed = false
        for ((key, notification) in facts) {
            val allowed = canShowBadge(key) ?: continue
            if (allowed == notification.canShowBadge) continue
            facts[key] = notification.copy(canShowBadge = allowed)
            changed = true
        }
        if (changed) publishAll()
    }

    /** The binding is gone: forget the notifications and show no badges. */
    fun clear() {
        facts.clear()
        store.clear()
    }

    private fun publishAll() {
        store.replaceAll(counter.counts(facts.values.toList()))
    }

    private fun publish(
        packageName: String,
        userSerial: Long,
    ) {
        val key = BadgeKey(packageName, userSerial)
        val mine = facts.values.filter { it.packageName == packageName && it.userSerial == userSerial }
        store.update(key, counter.counts(mine)[key] ?: 0)
    }
}
