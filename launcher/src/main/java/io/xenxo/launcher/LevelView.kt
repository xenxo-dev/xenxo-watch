package io.xenxo.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.sign

/**
 * Selector de un valor numérico a pantalla completa (p. ej. el brillo): número grande en el centro,
 * corona o botones − / + para cambiarlo. Cada cambio se aplica al momento con [onChange].
 */
class LevelView(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val title = TextView(context).apply {
        setTextColor(0xFF9E9E9E.toInt())
        textSize = 13f
        letterSpacing = 0.12f
        gravity = Gravity.CENTER
    }
    private val value = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 56f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        gravity = Gravity.CENTER
    }
    private val hint = TextView(context).apply {
        setTextColor(0xFF757575.toInt())
        textSize = 13f
        gravity = Gravity.CENTER
    }

    private var current = 0
    private var min = 0
    private var max = 100
    private var step = 5
    private var format: (Int) -> String = { it.toString() }
    private var onChange: (Int) -> Unit = {}

    val isShowing get() = visibility == View.VISIBLE

    init {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        // De arriba abajo: título, número, ayuda y botones; nada se solapa en 368×448
        addView(title, LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = (26 * d).toInt() })
        addView(value, LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = (52 * d).toInt() })
        addView(hint, LayoutParams(-1, -2, Gravity.TOP).apply {
            topMargin = (120 * d).toInt(); leftMargin = (20 * d).toInt(); rightMargin = (20 * d).toInt()
        })
        addView(button("−") { nudge(-1) }, LayoutParams(size(), size(), Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = (30 * d).toInt(); bottomMargin = (20 * d).toInt()
        })
        addView(button("+") { nudge(+1) }, LayoutParams(size(), size(), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = (30 * d).toInt(); bottomMargin = (20 * d).toInt()
        })
    }

    fun show(label: String, start: Int, min: Int, max: Int, step: Int, note: String,
             format: (Int) -> String, onChange: (Int) -> Unit) {
        this.min = min; this.max = max; this.step = step
        this.format = format; this.onChange = onChange
        title.text = label.uppercase()
        hint.text = note
        current = start.coerceIn(min, max)
        value.text = format(current)
        visibility = View.VISIBLE
        requestFocus()
    }

    fun hide() { visibility = View.GONE }

    private fun nudge(dir: Int) {
        val next = (current + dir * step).coerceIn(min, max)
        if (next == current) return
        current = next
        value.text = format(current)
        onChange(current)
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            // Mismo sentido que el desplazamiento de las listas de Wear OS (sin comprobar aún en el reloj)
            nudge(-sign(ev.getAxisValue(MotionEvent.AXIS_SCROLL)).toInt())
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun size() = (52 * d).toInt()

    private fun button(text: String, action: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 30f
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF262626.toInt()) }
        setOnClickListener { action() }
    }
}
