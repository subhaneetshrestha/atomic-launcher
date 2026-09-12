package io.github.subhaneetshrestha.atomic.home.info

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.core.theme.Action
import io.github.subhaneetshrestha.atomic.core.theme.BindingSurface
import io.github.subhaneetshrestha.atomic.core.theme.HomeInfoConfig
import io.github.subhaneetshrestha.atomic.core.theme.InfoLineId
import io.github.subhaneetshrestha.atomic.core.theme.ResolvedColors
import io.github.subhaneetshrestha.atomic.core.theme.Theme
import io.github.subhaneetshrestha.atomic.home.ThemeApplier
import io.github.subhaneetshrestha.atomic.settings.HomeSettings
import io.github.subhaneetshrestha.atomic.util.Threads
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Clock, date and battery above or below the app list. The clock is a platform TextClock (follows
 * the system 12/24-hour setting); the date rolls over at local midnight; the battery follows the
 * sticky ACTION_BATTERY_CHANGED broadcast while the screen is started.
 */
class InfoLinesView(
    context: Context,
    private val applier: ThemeApplier,
) : LinearLayout(context) {
    /** A line was tapped or held; what that does is the dispatcher's decision, not this view's. */
    var onSurface: ((BindingSurface) -> Unit)? = null
    private val clock = TextClock(context)
    private val date = TextView(context)
    private val battery = TextView(context)
    private val screenTime = TextView(context)

    private var config = HomeInfoConfig()
    private var batteryLevel = -1
    private var charging = false
    private var started = false

    private val batteryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = onBattery(intent)
        }
    private val screenTimeTick = Runnable { refreshScreenTime() }
    private val midnightTick =
        Runnable {
            refreshDate()
            scheduleMidnight()
        }

    init {
        orientation = VERTICAL
        for (line in listOf(
            clock,
            date,
            battery,
            screenTime,
        )) {
            addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    fun bind(
        config: HomeInfoConfig,
        theme: Theme,
        view: HomeSettings,
        colors: ResolvedColors,
    ) {
        this.config = config
        val gravity = applier.horizontalGravity(view.horizontalAlignment)
        val sizes = theme.typography.sizes
        applier.applyText(clock, view.font, sizes.clockSp, colors.text, gravity, maxFontScale = CLOCK_MAX_FONT_SCALE)
        applier.applyText(date, view.font, sizes.infoSp, colors.textSecondary, gravity)
        applier.applyText(battery, view.font, sizes.infoSp, colors.textSecondary, gravity)
        applier.applyText(screenTime, view.font, sizes.infoSp, colors.textSecondary, gravity)
        clock.format12Hour = config.clock.format
        clock.format24Hour = config.clock.format
        clock.visibility = if (config.clock.enabled) VISIBLE else GONE
        date.visibility = if (config.date.enabled) VISIBLE else GONE
        battery.visibility = if (config.battery.enabled) VISIBLE else GONE
        screenTime.visibility = if (config.screenTime.enabled) VISIBLE else GONE
        wire(clock, InfoLineId.CLOCK, config)
        wire(date, InfoLineId.DATE, config)
        wire(battery, InfoLineId.BATTERY, config)
        wire(screenTime, InfoLineId.SCREEN_TIME, config)
        visibility =
            if (config.clock.enabled || config.date.enabled || config.battery.enabled ||
                config.screenTime.enabled
            ) {
                VISIBLE
            } else {
                GONE
            }
        refreshDate()
        refreshBattery()
        refreshScreenTime()
    }

    /**
     * A line answers touches only when something is bound to it, so a double tap over an idle
     * clock still reaches the home screen behind it.
     */
    private fun wire(
        view: TextView,
        id: InfoLineId,
        config: HomeInfoConfig,
    ) {
        val tappable = config.tap(id) != Action.None
        val holdable = config.longPress(id) != Action.None
        view.setOnClickListener(
            if (tappable) OnClickListener { onSurface?.invoke(BindingSurface.InfoTap(id)) } else null,
        )
        view.setOnLongClickListener(
            if (holdable) {
                OnLongClickListener {
                    onSurface?.invoke(BindingSurface.InfoLongPress(id))
                    true
                }
            } else {
                null
            },
        )
        view.isClickable = tappable
        view.isLongClickable = holdable
    }

    fun onStart() {
        if (started) return
        started = true
        val sticky =
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        sticky?.let(::onBattery)
        refreshDate()
        scheduleMidnight()
        refreshScreenTime()
    }

    fun onStop() {
        if (!started) return
        started = false
        context.unregisterReceiver(batteryReceiver)
        removeCallbacks(midnightTick)
        removeCallbacks(screenTimeTick)
    }

    /**
     * Screen time is worked out from Android's record of every app that came to the front today,
     * which is a question worth asking off the main thread, and worth asking again while the
     * launcher is on screen because it keeps growing.
     */
    private fun refreshScreenTime() {
        removeCallbacks(screenTimeTick)
        if (!config.screenTime.enabled) return
        val appContext = context.applicationContext
        Threads.io.post {
            val total = UsageAccess.todayMillis(appContext)
            Threads.main.post {
                if (!config.screenTime.enabled) return@post
                screenTime.text =
                    when {
                        total == null -> {
                            context.getString(R.string.screen_time_needs_access)
                        }

                        total >= HOUR_MS -> {
                            val minutes = (total / MINUTE_MS).toInt()
                            context.getString(R.string.screen_time_hours, minutes / 60, minutes % 60)
                        }

                        else -> {
                            context.getString(R.string.screen_time_minutes, (total / MINUTE_MS).toInt())
                        }
                    }
                if (started) postDelayed(screenTimeTick, MINUTE_MS)
            }
        }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        batteryLevel = if (level < 0 || scale <= 0) -1 else level * 100 / scale
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        refreshBattery()
    }

    private fun refreshBattery() {
        battery.text =
            if (batteryLevel < 0) "" else BatteryLineFormat.format(batteryLevel, charging, config.battery.format)
    }

    private fun refreshDate() {
        val locale: Locale = resources.configuration.locales[0]
        date.text =
            DateLineFormat.format(LocalDate.now(), locale, config.date.format) {
                DateFormat.getBestDateTimePattern(it, "EEEEMMMMd")
            }
    }

    private fun scheduleMidnight() {
        removeCallbacks(midnightTick)
        val now = ZonedDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        postDelayed(midnightTick, Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_SLACK_MS)
    }

    private companion object {
        const val MIDNIGHT_SLACK_MS = 500L

        const val MINUTE_MS = 60_000L

        const val HOUR_MS = 60 * MINUTE_MS

        /** The clock is decoration: it follows the font size setting only up to this factor. */
        const val CLOCK_MAX_FONT_SCALE = 1.3f
    }
}
