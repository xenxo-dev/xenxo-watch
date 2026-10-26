package io.xenxo.tesla

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * xenxo Tesla Circular para Xiaomi Watch 2 (466×466).
 * Control de climatización con ajuste táctil, apertura de maleteros (Frunk/Trunk) y bloqueo.
 */
class TeslaActivity : Activity() {

    private var targetTemp = 21.0f
    private var isClimateOn = false
    private var isLocked = true

    private lateinit var tvTemp: TextView
    private lateinit var btnClimate: Button
    private lateinit var btnLock: Button

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
            text = "🚗 Tesla Model 3/Y"
            textSize = 17f
            setTextColor(Color.parseColor("#0A84FF"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 16)
        }
        root.addView(title)

        // Tarjeta Clima Circular
        val climateCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 16, 20, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 28f
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(20, 8, 20, 8) }
            layoutParams = lp

            val l = TextView(this@TeslaActivity).apply {
                text = "CLIMATIZACIÓN"
                textSize = 11f
                setTextColor(Color.parseColor("#8E8E93"))
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(l)

            tvTemp = TextView(this@TeslaActivity).apply {
                text = String.format("%.1f°C", targetTemp)
                textSize = 34f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 4, 0, 10)
            }
            addView(tvTemp)

            val stepper = LinearLayout(this@TeslaActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val btnMinus = Button(this@TeslaActivity).apply {
                text = "−0.5°"
                textSize = 13f
                setTextColor(Color.WHITE)
                background = null
                setOnClickListener {
                    targetTemp = (targetTemp - 0.5f).coerceAtLeast(16f)
                    tvTemp.text = String.format("%.1f°C", targetTemp)
                }
            }
            stepper.addView(btnMinus)

            val btnPlus = Button(this@TeslaActivity).apply {
                text = "＋0.5°"
                textSize = 13f
                setTextColor(Color.WHITE)
                background = null
                setOnClickListener {
                    targetTemp = (targetTemp + 0.5f).coerceAtMost(28f)
                    tvTemp.text = String.format("%.1f°C", targetTemp)
                }
            }
            stepper.addView(btnPlus)
            addView(stepper)

            btnClimate = Button(this@TeslaActivity).apply {
                text = "ENCENDER CLIMA"
                textSize = 12f
                setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#30D158"))
                    cornerRadius = 20f
                }
                setOnClickListener {
                    isClimateOn = !isClimateOn
                    text = if (isClimateOn) "APAGAR CLIMA" else "ENCENDER CLIMA"
                    Toast.makeText(this@TeslaActivity, if (isClimateOn) "Clima encendido a ${String.format("%.1f°C", targetTemp)}" else "Clima apagado", Toast.LENGTH_SHORT).show()
                }
            }
            addView(btnClimate, LinearLayout.LayoutParams(-1, -2).apply { setMargins(16, 8, 16, 0) })
        }
        root.addView(climateCard)

        // Botones de acción rápida: Bloqueo y Maleteros
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(20, 8, 20, 8) }
            layoutParams = lp
        }

        btnLock = Button(this).apply {
            text = "🔒 Bloquear"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 22f
            }
            setOnClickListener {
                isLocked = !isLocked
                text = if (isLocked) "🔒 Bloquear" else "🔓 Abrir"
                Toast.makeText(this@TeslaActivity, if (isLocked) "Vehículo bloqueado" else "Vehículo desbloqueado", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(btnLock, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 0, 4, 0) })

        val btnFrunk = Button(this).apply {
            text = "⚡ Frunk"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 22f
            }
            setOnClickListener {
                Toast.makeText(this@TeslaActivity, "Maletero delantero abierto", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(btnFrunk, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 0, 4, 0) })

        root.addView(actionsRow)
        scroll.addView(root)
        setContentView(scroll)
    }
}
