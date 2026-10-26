package io.xenxo.gym

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * xenxo Gym Circular para Xiaomi Watch 2 (466×466).
 * Registro rápido de series con botones táctiles grandes optimizados para dedos con sudor / guantes.
 */
class GymActivity : Activity() {

    private var currentWeight = 70.0f
    private var currentReps = 10
    private var setCounter = 1

    private lateinit var tvWeight: TextView
    private lateinit var tvReps: TextView
    private lateinit var tvRest: TextView
    private var restTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            setPadding(32, 60, 32, 60)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "🏋️ Serie $setCounter"
            textSize = 17f
            setTextColor(Color.parseColor("#30D158"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
        root.addView(title)

        // Stepper de Peso
        tvWeight = TextView(this).apply {
            text = "$currentWeight kg"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(createStepper("PESO", tvWeight, -2.5f, 2.5f) { delta ->
            currentWeight = (currentWeight + delta).coerceAtLeast(0f)
            tvWeight.text = String.format("%.1f kg", currentWeight)
        })

        // Stepper de Reps
        tvReps = TextView(this).apply {
            text = "$currentReps reps"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(createStepper("REPS", tvReps, -1f, 1f) { delta ->
            currentReps = (currentReps + delta.toInt()).coerceIn(1, 100)
            tvReps.text = "$currentReps reps"
        })

        // Botón Registrar Serie
        val btnLog = Button(this).apply {
            text = "＋ GUARDAR SERIE"
            textSize = 14f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#30D158"))
                cornerRadius = 24f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(24, 20, 24, 10) }
            layoutParams = lp

            setOnClickListener {
                setCounter++
                title.text = "🏋️ Serie $setCounter"
                startRestTimer(90)
            }
        }
        root.addView(btnLog)

        // Temporizador de descanso
        tvRest = TextView(this).apply {
            text = "⏱ Descanso: Listo"
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        }
        root.addView(tvRest)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun createStepper(
        label: String,
        valueView: TextView,
        stepDown: Float,
        stepUp: Float,
        onDelta: (Float) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 10, 16, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 24f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(20, 8, 20, 8) }
            layoutParams = lp

            val btnMinus = Button(this@GymActivity).apply {
                text = "−"
                textSize = 22f
                setTextColor(Color.WHITE)
                background = null
                setOnClickListener { onDelta(stepDown) }
            }
            addView(btnMinus, LinearLayout.LayoutParams(90, 80))

            addView(valueView, LinearLayout.LayoutParams(0, -2, 1f))

            val btnPlus = Button(this@GymActivity).apply {
                text = "＋"
                textSize = 20f
                setTextColor(Color.parseColor("#30D158"))
                background = null
                setOnClickListener { onDelta(stepUp) }
            }
            addView(btnPlus, LinearLayout.LayoutParams(90, 80))
        }
    }

    private fun startRestTimer(seconds: Int) {
        restTimer?.cancel()
        restTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvRest.text = String.format("⏱ Descanso: %02d:%02d", sec / 60, sec % 60)
                tvRest.setTextColor(Color.parseColor("#FFD60A"))
            }

            override fun onFinish() {
                tvRest.text = "⏱ ¡A por la siguiente serie!"
                tvRest.setTextColor(Color.parseColor("#30D158"))
                val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        restTimer?.cancel()
    }
}
