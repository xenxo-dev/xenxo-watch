package io.xenxo.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Menú negro a pantalla completa, con filas grandes para el dedo y desplazamiento con la corona.
 * Sustituye a los AlertDialog del sistema, que en el reloj quedan pequeños y en el tema por defecto.
 */
class MenuView(context: Context) : FrameLayout(context) {

    class Item(val label: String, val action: () -> Unit)

    private val d = resources.displayMetrics.density
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = object : ScrollView(context) {
        override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                smoothScrollBy(0, (-ev.getAxisValue(MotionEvent.AXIS_SCROLL) * 60 * d).toInt())
                return true
            }
            return super.onGenericMotionEvent(ev)
        }
    }

    val isShowing get() = visibility == View.VISIBLE

    init {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        isClickable = true // que los toques no lleguen al panal de debajo
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)
        addView(scroll, LayoutParams(-1, -1))
    }

    /**
     * Muestra el menú. [message] es un texto informativo opcional (gris) bajo el título.
     * [keepScroll] conserva la posición al volver a pintar el mismo menú (p. ej. tras cambiar un interruptor).
     */
    fun show(title: String, items: List<Item>, message: String? = null, keepScroll: Boolean = false) {
        val y = if (keepScroll) scroll.scrollY else 0
        list.removeAllViews()
        list.setPadding((10 * d).toInt(), (24 * d).toInt(), (10 * d).toInt(), (40 * d).toInt())
        list.addView(TextView(context).apply {
            text = title.uppercase()
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 13f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * d).toInt())
        })
        if (message != null) list.addView(TextView(context).apply {
            text = message
            setTextColor(0xFFBDBDBD.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (14 * d).toInt())
        })
        for (item in items) list.addView(row(item))
        scroll.post { scroll.scrollTo(0, y) }
        visibility = View.VISIBLE
        scroll.requestFocus()
    }

    fun hide() {
        visibility = View.GONE
        list.removeAllViews()
    }

    private fun row(item: Item) = TextView(context).apply {
        text = item.label
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER_VERTICAL
        minHeight = (50 * d).toInt()
        setPadding((14 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(0xFF262626.toInt()))
            addState(intArrayOf(), ColorDrawable(0xFF141414.toInt()))
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (6 * d).toInt() }
        setOnClickListener { hide(); item.action() }
    }
}
