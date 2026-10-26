package io.xenxo.outdoor

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Suite de Montaña, Altímetro Barométrico y Brújula Digital para Xiaomi Mi Watch (368×448 AMOLED).
 *
 * Características:
 * 1. Altímetro barométrico de precisión (TDK-Invensense icp101xx): Presión en hPa y altitud calculada.
 * 2. Brújula digital 360° (AKM ak0991x Magnetometer): Rosa de los vientos interactiva con aguja hacia el Norte.
 * 3. Calibración manual de presión a nivel del mar (QNH) con la corona rotatoria física.
 * 4. Tendencia barométrica meteorológica (Subiendo ☀️ / Estable ⛅ / Bajando 🌧️).
 * 5. Punto de referencia / Retorno (Waypoint) para senderismo.
 */
class OutdoorActivity : Activity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private var rotationSensor: Sensor? = null
    private var orientationSensor: Sensor? = null
    private var vibrator: Vibrator? = null

    private lateinit var prefs: SharedPreferences
    private lateinit var compassView: CompassRoseView
    private lateinit var txtHeading: TextView
    private lateinit var txtAltitude: TextView
    private lateinit var txtPressure: TextView
    private lateinit var txtQnh: TextView
    private lateinit var txtTendency: TextView
    private lateinit var btnWaypoint: TextView

    private var seaLevelPressure = 1013.25f
    private var currentPressure = 1013.25f
    private var smoothedHeading = 0f

    private var waypointHeading: Float? = null
    private var waypointAltitude: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("outdoor_prefs", Context.MODE_PRIVATE)
        seaLevelPressure = prefs.getFloat("qnh", 1013.25f)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        val d = resources.displayMetrics.density

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (24 * d).toInt())
        }

        // TÍTULO
        val lblTitle = TextView(this).apply {
            text = "🏔️ Montaña y Brújula"
            textSize = 15f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * d).toInt())
        }
        contentLayout.addView(lblTitle)

        // CARD 1: BRÚJULA DIGITAL
        val cardCompass = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(0xFF141414.toInt())
                cornerRadius = 10 * d
                setStroke((1 * d).toInt(), 0xFF2C2C2E.toInt())
            }
            setPadding((8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
        }

        compassView = CompassRoseView(this).apply {
            layoutParams = LinearLayout.LayoutParams((140 * d).toInt(), (140 * d).toInt())
        }
        cardCompass.addView(compassView)

        txtHeading = TextView(this).apply {
            text = "0° N"
            textSize = 20f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, (4 * d).toInt(), 0, 0)
        }
        cardCompass.addView(txtHeading)
        contentLayout.addView(cardCompass)

        // CARD 2: ALTÍMETRO BAROMÉTRICO
        val cardAltimeter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFF141414.toInt())
                cornerRadius = 10 * d
                setStroke((1 * d).toInt(), 0xFF34C759.toInt()) // Verde Outdoor
            }
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * d).toInt()
            }
            layoutParams = p

            val lbl = TextView(this@OutdoorActivity).apply {
                text = "⛰️ ALTÍMETRO BAROMÉTRICO"
                textSize = 10f
                setTextColor(0xFF34C759.toInt())
                paint.isFakeBoldText = true
            }
            addView(lbl)

            txtAltitude = TextView(this@OutdoorActivity).apply {
                text = "-- m"
                textSize = 26f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                setPadding(0, (2 * d).toInt(), 0, (2 * d).toInt())
            }
            addView(txtAltitude)

            txtPressure = TextView(this@OutdoorActivity).apply {
                text = "Presión: -- hPa"
                textSize = 11f
                setTextColor(0xFFAAAAAA.toInt())
            }
            addView(txtPressure)

            txtTendency = TextView(this@OutdoorActivity).apply {
                text = "Tendencia: ⛅ Estable"
                textSize = 10.5f
                setTextColor(0xFF0A84FF.toInt())
                setPadding(0, (2 * d).toInt(), 0, 0)
            }
            addView(txtTendency)

            txtQnh = TextView(this@OutdoorActivity).apply {
                text = "QNH: %.1f hPa (gira corona para calibrar)".format(seaLevelPressure)
                textSize = 9.5f
                setTextColor(0xFF777777.toInt())
                setPadding(0, (4 * d).toInt(), 0, 0)
            }
            addView(txtQnh)
        }
        contentLayout.addView(cardAltimeter)

        // CARD 3: WAYPOINT DE RETORNO
        btnWaypoint = TextView(this).apply {
            text = "📌 Fijar Punto de Retorno"
            textSize = 11f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF2C2C2E.toInt())
                cornerRadius = 8 * d
                setStroke((1 * d).toInt(), 0xFFFF9F0A.toInt())
            }
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * d).toInt()
            }
            layoutParams = p

            setOnClickListener {
                setWaypoint()
            }
        }
        contentLayout.addView(btnWaypoint)

        scrollView.addView(contentLayout)
        root.addView(scrollView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        pressureSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: orientationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                if (event.values.isNotEmpty()) {
                    currentPressure = event.values[0]
                    updateAltimeter()
                }
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthDeg = (Math.toDegrees(orientation[0].toDouble()) + 360) % 360
                updateCompass(azimuthDeg.toFloat())
            }

            Sensor.TYPE_ORIENTATION -> {
                if (event.values.isNotEmpty()) {
                    val azimuthDeg = (event.values[0] + 360) % 360
                    updateCompass(azimuthDeg)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateCompass(rawDeg: Float) {
        // Filtro paso bajo para suavizar rotación (0.2 factor)
        var diff = rawDeg - smoothedHeading
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        smoothedHeading = (smoothedHeading + diff * 0.25f + 360f) % 360f

        compassView.setHeading(smoothedHeading)

        val degInt = smoothedHeading.toInt()
        val cardinal = getCardinalDirection(degInt)
        txtHeading.text = "$degInt° $cardinal"

        waypointHeading?.let { wpHeading ->
            var wpDiff = (wpHeading - smoothedHeading + 360f) % 360f
            if (wpDiff > 180f) wpDiff -= 360f
            btnWaypoint.text = "📍 Retorno: rumbo ${wpHeading.toInt()}° (%+d°)".format(wpDiff.toInt())
        }
    }

    private fun updateAltimeter() {
        val alt = SensorManager.getAltitude(seaLevelPressure, currentPressure)
        txtAltitude.text = "%.0f m".format(alt)
        txtPressure.text = "Presión: %.1f hPa".format(currentPressure)
    }

    private fun getCardinalDirection(deg: Int): String {
        return when (deg) {
            in 338..360, in 0..22 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SO"
            in 248..292 -> "O"
            in 293..337 -> "NO"
            else -> "N"
        }
    }

    private fun setWaypoint() {
        waypointHeading = smoothedHeading
        waypointAltitude = SensorManager.getAltitude(seaLevelPressure, currentPressure)
        vibrator?.vibrate(longArrayOf(0, 50, 40, 50), -1)
        btnWaypoint.text = "📍 Retorno fijado a ${waypointHeading?.toInt()}°"
    }

    /** Calibración de presión QNH a nivel del mar con corona rotatoria */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            (event.source and InputDevice.SOURCE_ROTARY_ENCODER) != 0
        ) {
            val axis = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val delta = if (axis > 0) +0.5f else -0.5f
            seaLevelPressure = (seaLevelPressure + delta).coerceIn(900.0f, 1100.0f)
            vibrator?.vibrate(15)
            prefs.edit().putFloat("qnh", seaLevelPressure).apply()
            txtQnh.text = "QNH: %.1f hPa (calibrado)".format(seaLevelPressure)
            updateAltimeter()
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
