package io.github.subhaneetshrestha.atomic.notifications

import android.app.Notification
import android.os.Build
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.subhaneetshrestha.atomic.AtomicApp
import io.github.subhaneetshrestha.atomic.util.Logs

/**
 * The one place notifications enter the app. It reads what the counting rules need and nothing
 * else: whether a notification has any title or text is recorded, never what that text says.
 * Everything is handed straight to [BadgeHub]; nothing is stored, logged or sent anywhere.
 *
 * While the badge setting is off the service asks the system to unbind it, so a launcher whose
 * badges are turned off is not receiving notifications at all.
 */
class BadgeNotificationListener : NotificationListenerService() {
    private val badges: BadgeController? get() = (application as? AtomicApp)?.badges

    private val users: UserManager? by lazy { getSystemService(UserManager::class.java) }

    override fun onListenerConnected() {
        val badges = badges ?: return
        if (!badges.wanted) {
            Logs.d(TAG) { "connected but badges are off; unbinding" }
            badges.hub.clear()
            requestUnbind()
            return
        }
        badges.attach(this)
        val active = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        val ranking = currentRanking
        badges.hub.replaceAll(active.map { facts(it, ranking) })
        Logs.d(TAG) { "connected with ${active.size} notifications" }
    }

    override fun onListenerDisconnected() {
        val badges = badges ?: return
        badges.detach(this)
        badges.hub.clear()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
    ) {
        val notification = sbn ?: return
        badges?.hub?.posted(facts(notification, rankingMap))
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        badges?.hub?.removed(sbn?.key ?: return)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        val map = rankingMap ?: return
        val ranking = Ranking()
        badges?.hub?.reranked { key -> if (map.getRanking(key, ranking)) ranking.canShowBadge() else null }
    }

    private fun facts(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
    ): NotificationFacts {
        val notification = sbn.notification
        val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
        return NotificationFacts(
            key = sbn.key,
            packageName = sbn.packageName,
            userSerial = serialOf(sbn),
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            // No ranking (it has just arrived and the map is stale) is treated as allowed: the
            // next ranking update corrects it, and a missing badge is worse than an extra one.
            canShowBadge = ranking?.canShowBadge() ?: true,
            channelId = notification.channelId ?: "",
            isOngoing = sbn.isOngoing,
            isForegroundService = notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            category = notification.category,
            isSuspended = if (Build.VERSION.SDK_INT >= SUSPENDED_SDK) ranking?.isSuspended == true else false,
            hasTitleOrText = hasTitleOrText(notification),
            number = notification.number,
        )
    }

    /**
     * Whether there is anything to read, without reading it. The extras come from another app's
     * process, so unparcelling them can fail on a class this process does not have; a failure
     * means we cannot tell, and cannot tell is treated as "there is something".
     */
    private fun hasTitleOrText(notification: Notification): Boolean =
        runCatching {
            val extras = notification.extras
            TEXT_KEYS.any { key -> !extras.getCharSequence(key).isNullOrBlank() } ||
                !notification.tickerText.isNullOrBlank()
        }.getOrDefault(true)

    private fun serialOf(sbn: StatusBarNotification): Long =
        runCatching { users?.getSerialNumberForUser(sbn.user) ?: 0L }.getOrDefault(0L)

    private companion object {
        const val TAG = "BadgeListener"

        /** Ranking.isSuspended arrived in Android 9. */
        const val SUSPENDED_SDK = 28

        val TEXT_KEYS =
            listOf(
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
            )
    }
}
