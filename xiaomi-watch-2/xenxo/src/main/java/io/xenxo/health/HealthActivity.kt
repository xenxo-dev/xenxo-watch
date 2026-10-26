package io.xenxo.health

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * xenxo Health Circular para Xiaomi Watch 2 (466×466).
 * Integra lectura PPG de 12 canales para pulso en vivo y podómetro continuo.
 */
class HealthActivity : Activity(), SensorEventListener {

    private var sm: SensorManager? = null
    private var heartRateSensor: Sensor? = null
    private var stepSensor: Sensor? = null

    private lateinit var tvHeartRate: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvKcal: TextView
    private lateinit var tvKm: TextView

    private var currentSteps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        heartRateSensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepSensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            setPadding(32, 70, 32, 70)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "❤️ Salud en vivo"
            textSize = 17f
            setTextColor(Color.parseColor("#FF453A"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        root.addView(title)

        // Tarjeta Pulso
        tvHeartRate = TextView(this).apply {
            text = "-- PPM"
            textSize = 34f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(createMetricCard("FRECUENCIA CARDÍACA", tvHeartRate, "#FF453A"))

        // Tarjeta Pasos
        tvSteps = TextView(this).apply {
            text = "0 pasos"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(createMetricCard("PASOS DE HOY", tvSteps, "#30D158"))

        // Fila Kcal + Km
        val subRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 8, 16, 8) }
            layoutParams = lp
        }

        tvKcal = TextView(this).apply {
            text = "0 kcal"
            textSize = 18f
            setTextColor(Color.parseColor("#FF9F0A"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        subRow.addView(createMiniCard("CALORÍAS", tvKcal), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 0, 4, 0) })

        tvKm = TextView(this).apply {
            text = "0.0 km"
            textSize = 18f
            setTextColor(Color.parseColor("#0A84FF"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        subRow.addView(createMiniCard("DISTANCIA", tvKm), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(4, 0, 4, 0) })

        root.addView(subRow)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun createMetricCard(label: String, valueView: View, accentHex: String): View {
        return LinearLayout(this).apply {
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

            val l = TextView(this@HealthActivity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor(accentHex))
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(l)
            addView(valueView)
        }
    }

    private fun createMiniCard(label: String, valueView: View): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 14, 12, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 24f
            }

            val l = TextView(this@HealthActivity).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#8E8E93"))
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(l)
            addView(valueView)
        }
    }

    override fun onResume() {
        super.onResume()
        heartRateSensor?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        stepSensor?.let { sm?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sm?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values[0].toInt()
                if (hr > 0) tvHeartRate.text = "$hr PPM"
            }
            Sensor.TYPE_STEP_COUNTER -> {
                currentSteps = event.values[0].toInt()
                tvSteps.text = "$currentSteps pasos"
                val kcal = (currentSteps * 70.0f * 0.000601f).toInt()
                tvKcal.text = "$kcal kcal"
                val km = (currentSteps * 175.0f * 0.415f / 100000f)
                tvKm.text = String.format("%.1f km", km)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
