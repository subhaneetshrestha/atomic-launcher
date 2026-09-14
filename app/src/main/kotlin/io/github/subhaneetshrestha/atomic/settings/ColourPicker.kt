package io.github.subhaneetshrestha.atomic.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.subhaneetshrestha.atomic.R
import io.github.subhaneetshrestha.atomic.core.theme.ColorValue
import kotlin.math.roundToInt

/**
 * A colour is a picture, not a hex string typed into a box: a live preview, quick swatches drawn
 * from the theme itself, three sliders whose tracks show what they do, and a hex field for anyone
 * who already has one. Built from platform views only — `SeekBar`, `GradientDrawable`, and
 * `android.graphics.Color`'s own HSV conversion — so this costs no dependency.
 *
 * ponytail: the preview swatch shows alpha by blending against a flat grey ground rather than a
 * true chequerboard. A chequerboard is a `BitmapShader` over a small tiled bitmap; add it if a
 * flat ground is ever reported as reading as "washed out" rather than "transparent".
 */
object ColourPicker {
    fun show(
        context: Context,
        titleRes: Int,
        current: String,
        allowAuto: Boolean,
        swatches: List<Int>,
        apply: (String) -> Unit,
    ) {
        val dp = { v: Int -> (v * context.resources.displayMetrics.density).toInt() }
        var auto = current == ColorValue.AUTO
        val initial =
            when {
                auto -> Color.WHITE

                ColorValue.isHex(current) -> runCatching { Color.parseColor(current) }.getOrDefault(Color.WHITE)

                // A Material You token cannot be slid apart into HSV; starting neutral is honest —
                // touching any slider or swatch is what commits a real, storable colour.
                else -> Color.WHITE
            }
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var alpha = Color.alpha(initial)

        fun argb(): Int = Color.HSVToColor(alpha, hsv)

        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dp(20)
                setPadding(pad, dp(12), pad, 0)
            }

        val preview =
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
            }
        val previewFrame =
            FrameLayout(context).apply {
                setBackgroundColor(Color.rgb(NEUTRAL_GRAY, NEUTRAL_GRAY, NEUTRAL_GRAY))
                addView(preview)
            }
        root.addView(previewFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        val hexInput =
            EditText(context).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setHint(R.string.background_colour_hint)
            }

        var updating = false

        fun swatch(
            color: Int,
            size: Int = 32,
        ): View =
            View(context).apply {
                val d = dp(size)
                layoutParams = LinearLayout.LayoutParams(d, d).apply { marginEnd = dp(10) }
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                isClickable = true
                isFocusable = true
            }

        lateinit var hueBar: SeekBar
        lateinit var satBar: SeekBar
        lateinit var valBar: SeekBar
        lateinit var alphaBar: SeekBar
        lateinit var dialog: AlertDialog

        fun refresh(fromHex: Boolean) {
            preview.setBackgroundColor(argb())
            hueBar.progressDrawable = trackFor(HUE_STOPS)
            satBar.progressDrawable =
                twoStop(Color.HSVToColor(floatArrayOf(hsv[0], 0f, 1f)), Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
            valBar.progressDrawable =
                twoStop(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
            alphaBar.progressDrawable =
                twoStop(Color.HSVToColor(0, hsv), Color.HSVToColor(MAX_ALPHA, hsv))
            if (!fromHex) {
                updating = true
                hexInput.setText(ColorValue.normalize("#%08X".format(argb()), allowAuto = false))
                updating = false
            }
        }

        fun slider(
            labelRes: Int,
            max: Int,
            progress: Int,
            onChange: (Int) -> Unit,
        ): SeekBar {
            root.addView(
                TextView(context).apply {
                    setText(labelRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, dp(10), 0, dp(2))
                },
            )
            val bar =
                SeekBar(context).apply {
                    this.max = max
                    this.progress = progress
                    setOnSeekBarChangeListener(
                        object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(
                                seekBar: SeekBar?,
                                value: Int,
                                fromUser: Boolean,
                            ) {
                                if (!fromUser) return
                                auto = false
                                onChange(value)
                                refresh(fromHex = false)
                            }

                            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                        },
                    )
                }
            root.addView(bar)
            return bar
        }

        if (allowAuto || swatches.isNotEmpty()) {
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(4))
                }
            if (allowAuto) {
                row.addView(
                    TextView(context).apply {
                        text = context.getString(R.string.editor_auto)
                        setPadding(dp(14), 0, dp(14), 0)
                        gravity = Gravity.CENTER
                        minHeight = dp(32)
                        minWidth = dp(32)
                        background =
                            GradientDrawable().apply {
                                cornerRadius = dp(16).toFloat()
                                setStroke(dp(1), Color.GRAY)
                            }
                        setOnClickListener {
                            apply(ColorValue.AUTO)
                            dialog.dismiss()
                        }
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                        marginEnd = dp(10)
                    },
                )
            }
            for (color in swatches) {
                row.addView(
                    swatch(color).apply {
                        setOnClickListener {
                            auto = false
                            Color.colorToHSV(color, hsv)
                            alpha = MAX_ALPHA
                            refresh(fromHex = false)
                        }
                    },
                )
            }
            root.addView(HorizontalScrollView(context).apply { addView(row) })
        }

        hueBar = slider(R.string.editor_hue, HUE_MAX, (hsv[0]).roundToInt()) { hsv[0] = it.toFloat() }
        satBar =
            slider(R.string.editor_saturation, PERCENT_MAX, (hsv[1] * PERCENT_MAX).roundToInt()) {
                hsv[1] = it / PERCENT_MAX.toFloat()
            }
        valBar =
            slider(R.string.editor_brightness, PERCENT_MAX, (hsv[2] * PERCENT_MAX).roundToInt()) {
                hsv[2] = it / PERCENT_MAX.toFloat()
            }
        alphaBar =
            slider(R.string.editor_opacity, PERCENT_MAX, (alpha * PERCENT_MAX / MAX_ALPHA)) {
                alpha = (it * MAX_ALPHA / PERCENT_MAX)
            }

        root.addView(
            TextView(context).apply {
                setText(R.string.background_colour_hex)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(14), 0, dp(2))
            },
        )
        root.addView(hexInput)
        hexInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (updating) return
                    val parsed = ColorValue.normalize(s.toString(), allowAuto = false) ?: return
                    val parsedArgb = runCatching { Color.parseColor(parsed) }.getOrNull() ?: return
                    auto = false
                    Color.colorToHSV(parsedArgb, hsv)
                    alpha = Color.alpha(parsedArgb)
                    refresh(fromHex = true)
                }
            },
        )

        refresh(fromHex = false)

        dialog =
            AlertDialog
                .Builder(context)
                .setTitle(titleRes)
                .setView(root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (auto) {
                        apply(ColorValue.AUTO)
                    } else {
                        ColorValue.normalize("#%08X".format(argb()), allowAuto = false)?.let(apply)
                    }
                }.setNegativeButton(android.R.string.cancel, null)
                .create()
        // The dialog now holds several focusable views (a swatch, a slider), where the old plain
        // prompt held exactly one; without forcing it, the platform focuses whichever comes first
        // in the layout, not the field someone opening this dialog to type a known hex value
        // expects. Requested only once the window is actually up: a request made any earlier can
        // be dropped before the view is attached.
        dialog.setOnShowListener {
            hexInput.requestFocus()
            hexInput.selectAll()
        }
        dialog.show()
    }

    private fun trackFor(stops: IntArray): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, stops)

    private fun twoStop(
        from: Int,
        to: Int,
    ): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(from, to))

    private const val NEUTRAL_GRAY = 0x80
    private const val MAX_ALPHA = 255
    private const val HUE_MAX = 359
    private const val PERCENT_MAX = 100

    /** Full-saturation, full-value swatches every 60 degrees: the only stop set hue itself needs. */
    private val HUE_STOPS =
        floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
            .map { Color.HSVToColor(floatArrayOf(it, 1f, 1f)) }
            .toIntArray()
}
