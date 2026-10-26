package io.xenxo.gym

import android.content.Context
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.xenxo.ui.XTheme

/**
 * Temporizador de descanso de alta visibilidad para la pantalla 368×448 AMOLED.
 * Estética openGym con cronómetro en verde #30D158 y botones píldora.
 * Al terminar, doble vibración háptica con RestAlarm (alarma del sistema).
 */
class RestTimerView(
    context: Context,
    private val totalSeconds: Int,
    private val onFinished: () -> Unit
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private var timer: CountDownTimer? = null
    private var secondsLeft = totalSeconds

    private val tvTimer = TextView(context).apply {
        setTextColor(XTheme.GREEN)
        textSize = 48f
        gravity = Gravity.CENTER
    }

    private val tvTitle = TextView(context).apply {
        text = "DESCANSO"
        setTextColor(XTheme.TEXT_SECONDARY)
        textSize = 13f
        letterSpacing = 0.15f
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, (6 * d).toInt())
    }

    init {
        setBackgroundColor(XTheme.BG_BLACK)
        isClickable = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt())
        }

        root.addView(tvTitle)
        root.addView(tvTimer)

        // Botones rápidos: -15s, +30s y Saltar
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((4 * d).toInt(), (14 * d).toInt(), (4 * d).toInt(), 0)
        }

        btnRow.addView(button("−15 s", fg = XTheme.TEXT_SECONDARY) {
            addSeconds(-15)
        })

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((5 * d).toInt(), 1)
        })

        btnRow.addView(button("+30 s", fg = XTheme.GREEN) {
            addSeconds(30)
        })

        btnRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((5 * d).toInt(), 1)
        })

        btnRow.addView(button("Saltar", fg = XTheme.TEXT_PRIMARY) {
            cancelTimer()
            onFinished()
        })

        root.addView(btnRow)
        addView(root, LayoutParams(-1, -1, Gravity.CENTER))

        startTimer(totalSeconds)
    }

    private fun startTimer(secs: Int) {
        cancelTimer()
        secondsLeft = secs
        updateText()
        RestAlarm.schedule(context, SystemClock.elapsedRealtime() + secs * 1000L)

        // Solo para la pantalla: CountDownTimer calcula lo que falta con elapsedRealtime, que sigue
        // contando con el reloj dormido; la vibración la da la alarma.
        timer = object : CountDownTimer(secs * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = ((millisUntilFinished + 999) / 1000).toInt()
                updateText()
            }

            override fun onFinish() {
                secondsLeft = 0
                updateText()
                postDelayed({ onFinished() }, 600)
            }
        }.start()
    }

    private fun addSeconds(extra: Int) {
        val newTotal = (secondsLeft + extra).coerceIn(5, 600)
        startTimer(newTotal)
    }

    override fun onGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_SCROLL &&
            ev.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -ev.getAxisValue(android.view.MotionEvent.AXIS_SCROLL)
            val step = if (delta > 0) 5 else -5
            addSeconds(step)
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun updateText() {
        val m = secondsLeft / 60
        val s = secondsLeft % 60
        tvTimer.text = String.format("%02d:%02d", m, s)
    }

    /** Para la cuenta atrás y la alarma de fin ("Saltar", atrás o reprogramar con +30 s). */
    fun cancelTimer() {
        timer?.cancel()
        timer = null
        RestAlarm.cancel(context)
    }

    private fun button(title: String, fg: Int, onClick: () -> Unit) = TextView(context).apply {
        text = title
        setTextColor(fg)
        textSize = 11f
        maxLines = 1
        gravity = Gravity.CENTER
        minimumHeight = (36 * d).toInt()
        setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
        background = XTheme.pillButtonDrawable(d, primary = false)
        setOnClickListener { onClick() }
    }
}
