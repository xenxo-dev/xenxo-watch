package io.xenxo.padel

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.xenxo.ui.XTheme

/**
 * Pantalla negra a pantalla completa con título, texto opcional y botones píldora grandes para el dedo.
 * Estética openGym con acento verde #30D158 para acciones principales.
 * Se usa para configurar el partido, el menú de pausa y el resumen final. La corona desplaza la lista.
 */
class PadelMenu(context: Context) : FrameLayout(context) {

    class Item(val label: String, val primary: Boolean = false, val action: () -> Unit)

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
        setBackgroundColor(XTheme.BG_BLACK)
        visibility = View.GONE
        isClickable = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)
        addView(scroll, LayoutParams(-1, -1))
    }

    /** [big] es un texto grande opcional (p. ej. el resultado), [message] uno gris más pequeño. */
    fun show(title: String, items: List<Item>, big: String? = null, message: String? = null, keepScroll: Boolean = false) {
        val y = if (keepScroll) scroll.scrollY else 0
        list.removeAllViews()
        list.setPadding((14 * d).toInt(), (20 * d).toInt(), (14 * d).toInt(), (36 * d).toInt())
        list.addView(text(title.uppercase(), 12f, XTheme.TEXT_SECONDARY).apply { letterSpacing = 0.15f })
        if (big != null) list.addView(text(big, 28f, XTheme.TEXT_PRIMARY).apply { setPadding(0, (6 * d).toInt(), 0, 0) })
        if (message != null) list.addView(text(message, 13f, XTheme.TEXT_SECONDARY).apply { setPadding(0, (4 * d).toInt(), 0, 0) })
        list.addView(View(context), LinearLayout.LayoutParams(1, (10 * d).toInt()))
        items.forEach { list.addView(row(it)) }
        scroll.post { scroll.scrollTo(0, y) }
        visibility = View.VISIBLE
        scroll.requestFocus()
    }

    fun hide() { visibility = View.GONE }

    private fun text(s: String, size: Float, color: Int) = TextView(context).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
    }

    private fun row(item: Item) = TextView(context).apply {
        text = item.label
        textSize = 15f
        setTextColor(if (item.primary) Color.BLACK else XTheme.TEXT_PRIMARY)
        gravity = Gravity.CENTER
        minimumHeight = (44 * d).toInt()
        setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        background = XTheme.pillButtonDrawable(d, primary = item.primary)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (6 * d).toInt() }
        setOnClickListener { item.action() }
    }
}
