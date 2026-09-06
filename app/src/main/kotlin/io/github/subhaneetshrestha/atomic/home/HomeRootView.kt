package io.github.subhaneetshrestha.atomic.home

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Root of the home window. The frame itself is never padded so a later background view can draw
 * edge to edge behind everything; [content] takes the system-bar and display-cutout insets.
 * Insets are returned unconsumed so a later search overlay can react to the IME itself.
 */
class HomeRootView(
    context: Context,
) : FrameLayout(context) {
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val blockHost = FrameLayout(context)

    init {
        content.addView(blockHost, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    /** The block of info lines plus app list, placed by the theme's vertical alignment. */
    fun setBlock(
        block: View,
        verticalGravity: Int,
    ) {
        blockHost.removeAllViews()
        blockHost.addView(block, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, verticalGravity))
    }

    fun positionBlock(verticalGravity: Int) {
        val params = blockHost.getChildAt(0)?.layoutParams as? LayoutParams ?: return
        if (params.gravity != verticalGravity) {
            params.gravity = verticalGravity
            blockHost.requestLayout()
        }
    }

    /** A full-width row below the block, e.g. the default-home banner. */
    fun addFooter(view: View) {
        content.addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
