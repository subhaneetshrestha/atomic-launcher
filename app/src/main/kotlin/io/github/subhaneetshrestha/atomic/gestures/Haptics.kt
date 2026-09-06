package io.github.subhaneetshrestha.atomic.gestures

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The small taps under the finger that tell a gesture apart from nothing happening. Needs no
 * permission. Newer Android has feedback made for exactly these moments; older versions fall back
 * to the nearest thing they have.
 */
class Haptics(
    private val view: View,
) {
    var enabled: Boolean = true

    fun onGesture(event: GestureEvent) {
        if (!enabled) return
        val feedback =
            when (event) {
                is GestureEvent.LongPress -> {
                    HapticFeedbackConstants.LONG_PRESS
                }

                is GestureEvent.DoubleTap -> {
                    if (Build.VERSION.SDK_INT >=
                        30
                    ) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.VIRTUAL_KEY
                    }
                }

                is GestureEvent.LongSwipeArmed -> {
                    if (Build.VERSION.SDK_INT >= 34) {
                        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                }

                is GestureEvent.LongSwipeDisarmed -> {
                    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE else return
                }

                // A swipe is already answered by whatever it opens.
                is GestureEvent.Swipe -> {
                    return
                }
            }
        view.performHapticFeedback(feedback)
    }

    /** Nothing came of it: a different, shorter buzz so the difference is felt, not guessed. */
    fun rejected() {
        if (enabled && Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}
