package io.xenxo.padel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.xenxo.R
import io.xenxo.ui.XTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pestaña "Pádel" de xenxo: abrir el marcador (o seguir el partido en curso), últimos partidos y CSV.
 * Estética openGym con fondo negro puro AMOLED, tarjetas #1C1C1E y botones píldora.
 * El marcador va en su propia actividad (PadelActivity) para que solo él use el modo ambiente.
 */
class PadelView(context: Context) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private val store = PadelStore(context)
    private val dateFmt = SimpleDateFormat("d MMM", Locale("es", "ES"))

    private val scroll = object : ScrollView(context) {
        override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                smoothScrollBy(0, (-ev.getAxisValue(MotionEvent.AXIS_SCROLL) * 60 * d).toInt())
                return true
            }
            return super.onGenericMotionEvent(ev)
        }
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt(), (40 * d).toInt())
    }

    init {
        setBackgroundColor(XTheme.BG_BLACK)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content)
        addView(scroll, LayoutParams(-1, -1))
        refresh()
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val scrollDelta = (-delta * 40 * d).toInt()
            scroll.scrollBy(0, scrollDelta)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    fun refresh() {
        content.removeAllViews()
        val current = store.loadCurrent()
        val open = if (current != null && current.points.isNotEmpty())
            context.getString(R.string.padel_resume, current.resultText(withCurrent = true))
        else context.getString(R.string.padel_open)

        content.addView(XTheme.pillButton(context, open, primary = true, minHeightDp = 44f) {
            context.startActivity(Intent(context, PadelActivity::class.java))
        })

        content.addView(XTheme.sectionHeader(context, context.getString(R.string.padel_history)))
        val history = store.history()
        if (history.isEmpty()) {
            content.addView(TextView(context).apply {
                text = context.getString(R.string.padel_no_history)
                setTextColor(XTheme.TEXT_TERTIARY)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            })
        }
        history.forEach { content.addView(historyRow(it)) }

        if (history.isNotEmpty()) {
            content.addView(XTheme.pillButton(context, context.getString(R.string.padel_export), primary = false, minHeightDp = 40f) {
                val f = store.exportCsv()
                Toast.makeText(context, if (f != null) context.getString(R.string.padel_exported) else "Error", Toast.LENGTH_SHORT).show()
            })
        }
    }

    /** Partido pendiente de confirmar el borrado (mantener pulsado → tocar para borrar). */
    private var deleting: Long? = null

    private fun historyRow(r: PadelRecord) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
        val confirm = deleting == r.start

        val bg = if (confirm) XTheme.RED_SUBTLE else XTheme.CARD_BG
        val stroke = if (confirm) XTheme.RED else XTheme.CARD_STROKE
        background = XTheme.cardStateList(d, radiusDp = 14f, normalColor = bg, strokeColor = stroke)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (6 * d).toInt() }

        setOnLongClickListener { deleting = r.start; refresh(); true }
        setOnClickListener {
            if (confirm) store.delete(r.start)
            deleting = null
            refresh()
        }

        if (confirm) {
            addView(TextView(context).apply {
                text = context.getString(R.string.padel_delete_q)
                textSize = 14f
                setTextColor(XTheme.RED)
            })
            return@apply
        }

        addView(TextView(context).apply {
            text = r.result
            textSize = 16f
            setTextColor(XTheme.TEXT_PRIMARY)
        })

        val (label, color) = when (r.weWon) {
            true -> R.string.padel_won to XTheme.GREEN
            false -> R.string.padel_lost to XTheme.RED
            null -> R.string.padel_unfinished to XTheme.TEXT_SECONDARY
        }
        val minutes = (r.end - r.start) / 60_000
        addView(TextView(context).apply {
            text = "${dateFmt.format(Date(r.start))} · ${context.getString(label)} · ${minutes / 60} h ${"%02d".format(minutes % 60)}"
            textSize = 12f
            setTextColor(color)
            setPadding(0, (2 * d).toInt(), 0, 0)
        })
    }
}
