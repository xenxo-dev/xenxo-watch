package io.xenxo.gym

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.xenxo.ui.XTheme

/**
 * Pantalla de registro de una serie.
 * Estética inspirada en openGym con acento verde #30D158 y botón píldora.
 * Permite ajustar reps (+/-) y peso (corona giratoria o +/-).
 * Calcula el 1RM estimado (Epley) al vuelo.
 */
class SetLoggerView(
    context: Context,
    private val exercise: Exercise,
    private val setNum: Int,
    initialReps: Int,
    initialWeight: Float,
    private val best1RM: Float,
    private val onSaved: (reps: Int, weight: Float, restSec: Int) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private var reps = initialReps.coerceIn(1, 99)
    private var weight = initialWeight.coerceAtLeast(0f)
    private var restSec = exercise.restSeconds.coerceIn(15, 600)

    private val tvReps = TextView(context).apply {
        setTextColor(XTheme.TEXT_PRIMARY)
        textSize = 24f
        gravity = Gravity.CENTER
    }

    private val tvWeight = TextView(context).apply {
        setTextColor(XTheme.GREEN)
        textSize = 24f
        gravity = Gravity.CENTER
    }

    private val tvRest = TextView(context).apply {
        setTextColor(XTheme.BLUE)
        textSize = 20f
        gravity = Gravity.CENTER
    }

    private val tv1RM = TextView(context).apply {
        setTextColor(XTheme.TEXT_SECONDARY)
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(0, (2 * d).toInt(), 0, (6 * d).toInt())
    }

    init {
        setBackgroundColor(XTheme.BG_BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        val scroll = object : android.widget.ScrollView(context) {
            override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                    val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL)
                    val step = if (delta > 0) 0.5f else -0.5f
                    weight = (weight + step).coerceAtLeast(0f)
                    updateUI()
                    return true
                }
                return super.onGenericMotionEvent(ev)
            }
        }
        scroll.isVerticalScrollBarEnabled = false

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }

        // Título: Nombre y Serie
        root.addView(TextView(context).apply {
            text = exercise.name
            setTextColor(XTheme.TEXT_PRIMARY)
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
        })

        root.addView(TextView(context).apply {
            text = "Serie $setNum de ${exercise.targetSets}"
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (4 * d).toInt())
        })

        // Fila Repeticiones: [ - ]  8 reps  [ + ]
        root.addView(stepperRow("REPETICIONES", tvReps,
            onMinus = { if (reps > 1) { reps--; updateUI() } },
            onPlus = { reps++; updateUI() }
        ))

        // Fila Peso: [ - ]  75.0 kg  [ + ]
        root.addView(stepperRow("PESO (KG)", tvWeight,
            onMinus = { if (weight >= 0.5f) { weight -= 0.5f; updateUI() } },
            onPlus = { weight += 0.5f; updateUI() }
        ))

        // Fila Descanso: [ - ]  90 s  [ + ]
        root.addView(stepperRow("DESCANSO", tvRest,
            onMinus = { if (restSec > 15) { restSec -= 15; updateUI() } },
            onPlus = { if (restSec < 600) { restSec += 15; updateUI() } }
        ))

        // 1RM
        root.addView(tv1RM)

        // Botón Guardar Serie (Píldora verde openGym)
        root.addView(TextView(context).apply {
            text = "GUARDAR SERIE"
            setTextColor(Color.BLACK)
            textSize = 13f
            gravity = Gravity.CENTER
            minimumHeight = (36 * d).toInt()
            background = XTheme.pillButtonDrawable(d, primary = true)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (4 * d).toInt() }
            setOnClickListener { onSaved(reps, weight, restSec) }
        })

        // Botón Cancelar
        root.addView(TextView(context).apply {
            text = "Cancelar"
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, (2 * d).toInt(), 0, 0)
            setOnClickListener { onCancel() }
        })

        scroll.addView(root)
        addView(scroll, LayoutParams(-1, -1))
        updateUI()
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        // Corona giratoria ajusta el peso en pasos de 0.5 kg
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val step = if (delta > 0) 0.5f else -0.5f
            weight = (weight + step).coerceAtLeast(0f)
            updateUI()
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun updateUI() {
        tvReps.text = "$reps"
        tvWeight.text = String.format("%.1f kg", weight)
        tvRest.text = "${restSec} s"

        val epley = GymDb.calculateEpley1RM(weight, reps)
        val isRecord = epley > best1RM && best1RM > 0
        tv1RM.text = buildString {
            append(String.format("1RM estimado: %.1f kg", epley))
            if (isRecord) append("  ⭐ ¡Récord!")
        }
        tv1RM.setTextColor(if (isRecord) XTheme.AMBER else XTheme.TEXT_SECONDARY)
    }

    private fun stepperRow(label: String, centerTv: TextView, onMinus: () -> Unit, onPlus: () -> Unit) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (4 * d).toInt() }

            val btnMinus = roundBtn("−") { onMinus() }
            val btnPlus = roundBtn("+") { onPlus() }

            addView(btnMinus)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(context).apply {
                    text = label
                    setTextColor(XTheme.TEXT_SECONDARY)
                    textSize = 9f
                    letterSpacing = 0.1f
                    gravity = Gravity.CENTER
                })
                addView(centerTv)
            })
            addView(btnPlus)
        }

    private fun roundBtn(title: String, onClick: () -> Unit) = TextView(context).apply {
        text = title
        setTextColor(XTheme.TEXT_PRIMARY)
        textSize = 18f
        gravity = Gravity.CENTER
        val sz = (30 * d).toInt()
        layoutParams = LinearLayout.LayoutParams(sz, sz)
        background = XTheme.circleButtonDrawable(d, normalColor = XTheme.SURFACE_BTN, pressedColor = XTheme.SURFACE_BTN_PRESS)
        setOnClickListener { onClick() }
    }
}
