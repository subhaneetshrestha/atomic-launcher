package io.github.subhaneetshrestha.atomic.ui

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/**
 * Every animation in atomic and every number behind one. Nothing here decorates: each entry
 * answers "what just happened" or "which way is back", none lasts longer than a quarter second,
 * and all of them move alpha and transform only, so none of them can drop a frame on a slow phone.
 *
 * A phone with animations turned off gets none of it. [ValueAnimator] and [android.view.ViewPropertyAnimator]
 * already honour the system animator scale, so durations collapse to zero on their own; what they
 * do not do is run the end-of-animation work, which is why every caller here either puts the final
 * state in place first or checks [enabled] before starting.
 */
object Motion {
    const val PUSH_MS = 180L
    const val POP_MS = 140L
    const val COLOUR_MS = 220L
    const val REVEAL_MS = 160L
    const val DISMISS_MS = 120L
    const val PRESS_MS = 120L
    const val BADGE_MS = 140L

    /** How far a screen travels as it arrives or leaves. Far enough to read as a direction, no further. */
    const val SHIFT_DP = 12

    /** The search field's rise as it opens. */
    const val RISE_DP = 8

    /** Arriving: slow at the end, so the eye lands on the new thing rather than chasing it. */
    val enter: Interpolator = PathInterpolator(0f, 0f, 0.2f, 1f)

    /** Leaving: quick from the start; nothing should wait on something that is going away. */
    val exit: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)

    /** Changing in place, where both ends matter equally. */
    val ease: Interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** False when the device has animations off (developer options, battery saver, accessibility). */
    val enabled: Boolean get() = ValueAnimator.areAnimatorsEnabled()

    fun dp(
        context: Context,
        value: Int,
    ): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        )

    /**
     * Replaces the one child of [this] with [next], moving both sideways so that going deeper and
     * coming back look different. The new view is added and laid out whatever happens, so a device
     * with animations off simply gets the swap.
     */
    fun FrameLayout.swapChild(
        next: View,
        forward: Boolean,
    ) {
        val previous = getChildAt(0)
        addView(
            next,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        if (previous == null || !enabled) {
            if (previous != null) removeView(previous)
            return
        }
        val shift = dp(context, SHIFT_DP) * if (forward) 1f else -1f
        next.alpha = 0f
        next.translationX = shift
        next
            .animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(PUSH_MS)
            .setInterpolator(enter)
        previous
            .animate()
            .alpha(0f)
            .translationX(-shift / 2f)
            .setDuration(POP_MS)
            .setInterpolator(exit)
            .withEndAction {
                removeView(previous)
                // The view is pooled by nothing, but a cancelled animation leaves these behind and
                // the next screen to reuse the instance would start half-transparent and off-centre.
                previous.alpha = 1f
                previous.translationX = 0f
            }
    }

    /**
     * A row shrinks by 3% under the finger. The haptic says the tap was felt; this says which row
     * felt it. A state list animator rather than a touch listener, so the framework handles press,
     * release and the cancel that a swipe gesture turns a press into — and so it never touches
     * alpha, which a suspended app is already using to say something else.
     */
    fun pressScale(): StateListAnimator =
        StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), scaleTo(0.97f))
            addState(IntArray(0), scaleTo(1f))
        }

    private fun scaleTo(scale: Float): AnimatorSet =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(null, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(null, View.SCALE_Y, scale),
            )
            duration = PRESS_MS
            interpolator = ease
        }

    /** Fades [this] in from [risePx] below its resting place. */
    fun View.reveal(risePx: Float) {
        // A reopen during the closing fade would otherwise be switched off by that fade's end.
        animate().cancel()
        visibility = View.VISIBLE
        if (!enabled) {
            alpha = 1f
            translationY = 0f
            return
        }
        alpha = 0f
        translationY = risePx
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(REVEAL_MS)
            .setInterpolator(enter)
    }

    /** Fades [this] out and hands back when it is gone. [onGone] runs either way. */
    fun View.dismiss(onGone: () -> Unit) {
        if (!enabled) {
            visibility = View.GONE
            onGone()
            return
        }
        animate()
            .alpha(0f)
            .setDuration(DISMISS_MS)
            .setInterpolator(exit)
            .withEndAction {
                visibility = View.GONE
                alpha = 1f
                onGone()
            }
    }

    private val argb = ArgbEvaluator()

    /** A colour [fraction] of the way from [from] to [to]. */
    fun blend(
        from: Int,
        to: Int,
        fraction: Float,
    ): Int = argb.evaluate(fraction, from, to) as Int

    /**
     * Runs [onFrame] from 0 to 1 and always ends on exactly 1, so the final state is never left to
     * the animator. One animator drives however many colours the caller blends, and nothing is
     * returned to cancel: colour edits arrive a tap apart and the last one wins by arriving last.
     */
    fun tween(onFrame: (Float) -> Unit) {
        if (!enabled) {
            onFrame(1f)
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOUR_MS
            interpolator = ease
            addUpdateListener { onFrame(it.animatedValue as Float) }
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = onFrame(1f)
                },
            )
            start()
        }
    }
}
