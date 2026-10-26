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
 * Diálogo para apuntar el peso corporal en ayunas cada mañana.
 * Estética openGym con acento verde #30D158 y botón píldora.
 * La corona giratoria ajusta el peso en pasos finos de 0.1 kg.
 */
class BodyWeightDialog(
    context: Context,
    initialWeight: Float,
    private val onSaved: (weightKg: Float) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val d = resources.displayMetrics.density
    private var currentWeight = initialWeight.coerceIn(30f, 250f)

    private val tvWeight = TextView(context).apply {
        setTextColor(XTheme.GREEN)
        textSize = 30f
        gravity = Gravity.CENTER
        setPadding(0, (2 * d).toInt(), 0, (4 * d).toInt())
    }

    init {
        setBackgroundColor(XTheme.BG_BLACK)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
        }

        root.addView(TextView(context).apply {
            text = "PESO EN AYUNAS"
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 12f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
        })

        // Fila [ - ] 70.0 kg [ + ]
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (6 * d).toInt()
                bottomMargin = (6 * d).toInt()
            }
        }

        row.addView(roundBtn("−") {
            currentWeight -= 0.1f
            updateUI()
        })

        row.addView(tvWeight, LinearLayout.LayoutParams(0, -2, 1f))

        row.addView(roundBtn("+") {
            currentWeight += 0.1f
            updateUI()
        })

        root.addView(row)

        root.addView(TextView(context).apply {
            text = "Corona: pasos de 0,1 kg"
            setTextColor(XTheme.TEXT_TERTIARY)
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * d).toInt())
        })

        // Botón Guardar
        root.addView(TextView(context).apply {
            text = "GUARDAR PESO"
            setTextColor(Color.BLACK)
            textSize = 13f
            gravity = Gravity.CENTER
            minimumHeight = (38 * d).toInt()
            background = XTheme.pillButtonDrawable(d, primary = true)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (4 * d).toInt() }
            setOnClickListener { onSaved(currentWeight) }
        })

        // Botón Cancelar
        root.addView(TextView(context).apply {
            text = "Cancelar"
            setTextColor(XTheme.TEXT_SECONDARY)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (2 * d).toInt(), 0, 0)
            setOnClickListener { onCancel() }
        })

        addView(root, LayoutParams(-1, -1, Gravity.CENTER))
        updateUI()
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val delta = -ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            val step = if (delta > 0) 0.1f else -0.1f
            currentWeight = (currentWeight + step).coerceIn(30f, 250f)
            updateUI()
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    private fun updateUI() {
        tvWeight.text = String.format("%.1f kg", currentWeight)
    }

    private fun roundBtn(title: String, onClick: () -> Unit) = TextView(context).apply {
        text = title
        setTextColor(XTheme.TEXT_PRIMARY)
        textSize = 20f
        gravity = Gravity.CENTER
        val sz = (36 * d).toInt()
        layoutParams = LinearLayout.LayoutParams(sz, sz)
        background = XTheme.circleButtonDrawable(d, normalColor = XTheme.SURFACE_BTN, pressedColor = XTheme.SURFACE_BTN_PRESS)
        setOnClickListener { onClick() }
    }
}
