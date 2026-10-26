package io.xenxo.intervals

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Entrenador de Intervalos HIIT / Tabata / EMOM para Xiaomi Mi Watch (368×448 AMOLED).
 *
 * Características:
 * 1. Monitorización óptica del pulso cardíaco en vivo (Sensor.TYPE_HEART_RATE) con zonas de color.
 * 2. Retroalimentación háptica distintiva en cambios de fase (Trabajo / Descanso) y cuenta atrás 3-2-1.
 * 3. Selector de rutinas preconfiguradas (Tabata, HIIT 30/30, HIIT 45/15, EMOM) y corona rotatoria.
 * 4. Pantalla siempre encendida durante el entreno y fondo negro puro AMOLED (#000000) para mínimo gasto.
 */
class IntervalsActivity : Activity(), SensorEventListener {

    private var vibrator: Vibrator? = null
    private var sensorManager: SensorManager? = null
    private var heartRateSensor: Sensor? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentPreset = IntervalPreset.TABATA
    private var phase = IntervalPhase.IDLE
    private var currentRound = 1
    private var secondsLeft = 0
    private var phaseEndAt = 0L
    private var isRunning = false
    private var currentBpm = 0

    private lateinit var root: FrameLayout
    private lateinit var setupLayout: LinearLayout
    private lateinit var workoutLayout: LinearLayout

    private lateinit var txtPresetHeader: TextView
    private lateinit var txtRound: TextView
    private lateinit var txtPhaseBanner: TextView
    private lateinit var txtSeconds: TextView
    private lateinit var txtHeartRate: TextView
    private lateinit var btnControl: TextView
    private lateinit var btnSkip: TextView

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val now = android.os.SystemClock.elapsedRealtime()
            val remainingMs = phaseEndAt - now
            secondsLeft = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)

            if (secondsLeft in 1..3) {
                // Toque háptico de aviso 3-2-1
                vibrator?.vibrate(20)
            }

            if (remainingMs <= 0) {
                advancePhase()
            } else {
                updateWorkoutUi()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val d = resources.displayMetrics.density

        IntervalAlarm.listener = { _ ->
            handler.post {
                if (isRunning) {
                    advancePhase()
                }
            }
        }

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        buildSetupUi(d)
        buildWorkoutUi(d)

        setContentView(root)
        showSetup()
    }

    override fun onResume() {
        super.onResume()
        if (isRunning && heartRateSensor != null) {
            sensorManager?.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        IntervalAlarm.listener = null
        IntervalAlarm.cancel(this)
        stopTimer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun buildSetupUi(d: Float) {
        setupLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt(), (16 * d).toInt())
        }

        val lblTitle = TextView(this).apply {
            text = "⚡ Intervalos xenxo"
            textSize = 15f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * d).toInt())
        }
        setupLayout.addView(lblTitle)

        val desc = TextView(this).apply {
            text = "Elige tu rutina con la corona o toca para empezar:"
            textSize = 10f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (10 * d).toInt())
        }
        setupLayout.addView(desc)

        // Botones de presets
        for (preset in IntervalPreset.ALL) {
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(0xFF1C1C1E.toInt())
                    cornerRadius = 8 * d
                    setStroke((1 * d).toInt(), 0xFF333333.toInt())
                }
                setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
                val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (6 * d).toInt()
                }
                layoutParams = p

                val t = TextView(this@IntervalsActivity).apply {
                    text = preset.name
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    paint.isFakeBoldText = true
                }
                addView(t)

                val sub = TextView(this@IntervalsActivity).apply {
                    text = "${preset.totalRounds} rondas · ${preset.workSeconds}s trabajo / ${preset.restSeconds}s descanso"
                    textSize = 9.5f
                    setTextColor(0xFF30D158.toInt())
                }
                addView(sub)

                setOnClickListener {
                    vibrator?.vibrate(30)
                    startWorkoutWithPreset(preset)
                }
            }
            setupLayout.addView(btn)
        }

        root.addView(setupLayout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    private fun buildWorkoutUi(d: Float) {
        workoutLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            visibility = View.GONE
        }

        txtPresetHeader = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
        }
        workoutLayout.addView(txtPresetHeader)

        txtRound = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, (2 * d).toInt(), 0, (4 * d).toInt())
        }
        workoutLayout.addView(txtRound)

        // Banner de Fase (TRABAJO / DESCANSO / PREPARAR)
        txtPhaseBanner = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF0A84FF.toInt())
                cornerRadius = 6 * d
            }
            setPadding((16 * d).toInt(), (4 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
        }
        workoutLayout.addView(txtPhaseBanner)

        // Número gigante de segundos
        txtSeconds = TextView(this).apply {
            text = "20"
            textSize = 58f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, (4 * d).toInt(), 0, (2 * d).toInt())
        }
        workoutLayout.addView(txtSeconds)

        // Pulso óptico en vivo
        txtHeartRate = TextView(this).apply {
            text = "❤️ Leyendo pulso..."
            textSize = 11f
            setTextColor(0xFFFF375F.toInt())
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * d).toInt())
        }
        workoutLayout.addView(txtHeartRate)

        // Botonera de control
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnControl = TextView(this).apply {
            text = "Pausar"
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF333333.toInt())
                cornerRadius = 8 * d
            }
            setPadding((18 * d).toInt(), (8 * d).toInt(), (18 * d).toInt(), (8 * d).toInt())
            setOnClickListener {
                togglePause()
            }
        }
        val p1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = (4 * d).toInt()
        }
        btnRow.addView(btnControl, p1)

        btnSkip = TextView(this).apply {
            text = "Saltar ⏭"
            textSize = 12f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF222222.toInt())
                cornerRadius = 8 * d
            }
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            setOnClickListener {
                advancePhase()
            }
        }
        val p2 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = (4 * d).toInt()
        }
        btnRow.addView(btnSkip, p2)

        workoutLayout.addView(btnRow)
        root.addView(workoutLayout, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    private fun startWorkoutWithPreset(preset: IntervalPreset) {
        currentPreset = preset
        currentRound = 1
        phase = IntervalPhase.PREPARE
        secondsLeft = 5
        phaseEndAt = android.os.SystemClock.elapsedRealtime() + 5000L
        isRunning = true

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (heartRateSensor != null) {
            sensorManager?.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI)
        }

        setupLayout.visibility = View.GONE
        workoutLayout.visibility = View.VISIBLE

        vibrator?.vibrate(longArrayOf(0, 100, 50, 100), -1)
        IntervalAlarm.schedule(this, phaseEndAt, isWork = true)
        updateWorkoutUi()
        handler.postDelayed(timerRunnable, 1000)
    }

    private fun advancePhase() {
        IntervalAlarm.cancel(this)
        when (phase) {
            IntervalPhase.IDLE -> {}
            IntervalPhase.PREPARE -> {
                phase = IntervalPhase.WORK
                secondsLeft = currentPreset.workSeconds
                phaseEndAt = android.os.SystemClock.elapsedRealtime() + secondsLeft * 1000L
                // Doble pulso enérgico para empezar a sprintar
                vibrator?.vibrate(longArrayOf(0, 120, 60, 150), -1)
                val nextIsWork = (currentPreset.restSeconds <= 0)
                IntervalAlarm.schedule(this, phaseEndAt, isWork = nextIsWork)
            }
            IntervalPhase.WORK -> {
                if (currentPreset.restSeconds > 0) {
                    phase = IntervalPhase.REST
                    secondsLeft = currentPreset.restSeconds
                    phaseEndAt = android.os.SystemClock.elapsedRealtime() + secondsLeft * 1000L
                    // Vibración profunda para empezar descanso
                    vibrator?.vibrate(longArrayOf(0, 250), -1)
                    IntervalAlarm.schedule(this, phaseEndAt, isWork = true)
                } else {
                    nextRoundOrFinish()
                }
            }
            IntervalPhase.REST -> {
                nextRoundOrFinish()
            }
            IntervalPhase.FINISHED -> {
                showSetup()
                return
            }
        }
        updateWorkoutUi()
    }

    private fun nextRoundOrFinish() {
        if (currentRound < currentPreset.totalRounds) {
            currentRound++
            phase = IntervalPhase.WORK
            secondsLeft = currentPreset.workSeconds
            phaseEndAt = android.os.SystemClock.elapsedRealtime() + secondsLeft * 1000L
            vibrator?.vibrate(longArrayOf(0, 120, 60, 150), -1)
            val nextIsWork = (currentPreset.restSeconds <= 0)
            IntervalAlarm.schedule(this, phaseEndAt, isWork = nextIsWork)
        } else {
            finishWorkout()
        }
    }

    private fun finishWorkout() {
        phase = IntervalPhase.FINISHED
        isRunning = false
        IntervalAlarm.cancel(this)
        stopTimer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Patrón festivo de entreno completo
        vibrator?.vibrate(longArrayOf(0, 150, 80, 150, 80, 300), -1)
        updateWorkoutUi()
    }

    private fun togglePause() {
        isRunning = !isRunning
        if (isRunning) {
            btnControl.text = "Pausar"
            phaseEndAt = android.os.SystemClock.elapsedRealtime() + secondsLeft * 1000L
            IntervalAlarm.schedule(this, phaseEndAt, isWork = (phase != IntervalPhase.WORK))
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            handler.postDelayed(timerRunnable, 1000)
        } else {
            btnControl.text = "Reanudar"
            IntervalAlarm.cancel(this)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            stopTimer()
        }
    }

    private fun stopTimer() {
        handler.removeCallbacks(timerRunnable)
    }

    private fun showSetup() {
        IntervalAlarm.cancel(this)
        stopTimer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        workoutLayout.visibility = View.GONE
        setupLayout.visibility = View.VISIBLE
    }

    private fun updateWorkoutUi() {
        txtPresetHeader.text = currentPreset.name
        txtRound.text = "Ronda $currentRound de ${currentPreset.totalRounds}"
        txtSeconds.text = "%02d".format(secondsLeft)

        val bg = txtPhaseBanner.background as? GradientDrawable

        when (phase) {
            IntervalPhase.PREPARE -> {
                txtPhaseBanner.text = "PREPÁRATE"
                bg?.setColor(0xFF0A84FF.toInt()) // Azul
                txtSeconds.setTextColor(0xFF0A84FF.toInt())
            }
            IntervalPhase.WORK -> {
                txtPhaseBanner.text = "¡TRABAJO!"
                bg?.setColor(0xFF30D158.toInt()) // Verde lima
                txtSeconds.setTextColor(0xFF30D158.toInt())
            }
            IntervalPhase.REST -> {
                txtPhaseBanner.text = "DESCANSO"
                bg?.setColor(0xFFFF9F0A.toInt()) // Ámbar
                txtSeconds.setTextColor(0xFFFF9F0A.toInt())
            }
            IntervalPhase.FINISHED -> {
                txtPhaseBanner.text = "¡COMPLETO!"
                bg?.setColor(0xFFFF375F.toInt()) // Rosa/Rojo
                txtSeconds.text = "✓"
                txtSeconds.setTextColor(0xFF30D158.toInt())
                btnControl.text = "Volver"
                btnControl.setOnClickListener { showSetup() }
            }
            IntervalPhase.IDLE -> {}
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE && event.values.isNotEmpty()) {
            val bpm = event.values[0].toInt()
            if (bpm > 40) {
                currentBpm = bpm
                val zoneColor = when {
                    bpm < 120 -> 0xFF0A84FF.toInt() // Azul Z1
                    bpm in 120..145 -> 0xFF30D158.toInt() // Verde Z2
                    bpm in 146..165 -> 0xFFFF9F0A.toInt() // Naranja Z3
                    else -> 0xFFFF453A.toInt() // Rojo Z4-Z5
                }
                txtHeartRate.text = "❤️ $bpm bpm"
                txtHeartRate.setTextColor(zoneColor)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Corona rotatoria: cambia presets en pantalla inicial */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            (event.source and InputDevice.SOURCE_ROTARY_ENCODER) != 0
        ) {
            if (setupLayout.visibility == View.VISIBLE) {
                vibrator?.vibrate(15)
                val axis = event.getAxisValue(MotionEvent.AXIS_SCROLL)
                val currentIndex = IntervalPreset.ALL.indexOf(currentPreset)
                val nextIndex = if (axis > 0) {
                    (currentIndex + 1) % IntervalPreset.ALL.size
                } else {
                    (currentIndex - 1 + IntervalPreset.ALL.size) % IntervalPreset.ALL.size
                }
                currentPreset = IntervalPreset.ALL[nextIndex]
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (workoutLayout.visibility == View.VISIBLE) {
            finishWorkout()
            showSetup()
        } else {
            super.onBackPressed()
        }
    }
}
