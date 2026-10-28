package io.xenxo.health

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class HealthManager(private val ctx: Context) : SensorEventListener {

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val hrSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val prefs: SharedPreferences = ctx.getSharedPreferences("health_steps", Context.MODE_PRIVATE)
    private val db = HealthDb(ctx)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    private val mainHandler = Handler(Looper.getMainLooper())

    var currentSteps: Int = 0; private set
    var currentKcal: Int = 0; private set
    var currentKm: Float = 0f; private set

    var onStepsChanged: ((steps: Int, kcal: Int, km: Float) -> Unit)? = null

    // Medición activa de pulso
    var isMeasuringHr = false; private set
    private var hrTimeoutRunnable: Runnable? = null
    var onHeartRateUpdate: ((bpm: Int, status: String) -> Unit)? = null

    init {
        checkDayRollover()
        val today = todayStr()
        val cached = prefs.getInt("cached_steps", 0)
        if (prefs.getString("cached_day", "") == today && cached > 0) {
            currentSteps = cached
            val record = db.getDaily(today)
            currentKcal = if (record != null && record.kcal > 0) record.kcal else estimateKcal(cached)
            currentKm = if (record != null && record.km > 0f) record.km else estimateKm(cached)
        } else {
            val record = db.getDaily(today)
            if (record != null) {
                currentSteps = record.steps
                currentKcal = record.kcal
                currentKm = record.km
            } else {
                currentSteps = 0
                currentKcal = 0
                currentKm = 0f
            }
        }
    }

    /** Comprueba cambio de día a medianoche y resetea métricas a 0 */
    fun checkDayRollover(): Boolean {
        val today = todayStr()
        val cachedDay = prefs.getString("cached_day", "") ?: ""
        if (cachedDay.isNotEmpty() && cachedDay != today) {
            val last = prefs.getLong("last", 0L)
            prefs.edit()
                .putString("cached_day", today)
                .putLong("base", last)
                .putLong("carry", 0L)
                .putInt("cached_steps", 0)
                .apply()
            currentSteps = 0
            currentKcal = 0
            currentKm = 0f
            db.upsertSteps(today, 0, 0, 0f, io.xenxo.profile.UserProfile.get(ctx).stepGoal)
            ctx.sendBroadcast(android.content.Intent("io.xenxo.HEALTH_UPDATED"))
            return true
        }
        return false
    }

    /**
     * Sincroniza pasos de forma autoritativa (p. ej. desde complicaciones Xiaomi o teléfono).
     * Recalcula la base del podómetro hardware para que los conteos posteriores
     * continúen sumando en directo a partir de este valor exacto sin desfasarse.
     */
    fun syncSteps(steps: Int, kcal: Int = -1, km: Float = -1f, rawHardware: Long = -1L) {
        val today = todayStr()
        val raw = if (rawHardware >= 0L) rawHardware else prefs.getLong("last", 0L)
        val base = if (raw >= steps) raw - steps else 0L
        val profile = io.xenxo.profile.UserProfile.get(ctx)
        val finalKcal = if (kcal >= 0) kcal else estimateKcal(steps, profile.weightKg)
        val finalKm = if (km >= 0f) km else estimateKm(steps, profile.heightCm)

        currentSteps = steps
        currentKcal = finalKcal
        currentKm = finalKm

        val editor = prefs.edit()
            .putString("cached_day", today)
            .putLong("base", base)
            .putLong("carry", 0L)
            .putInt("cached_steps", steps)
        if (rawHardware >= 0L) {
            editor.putLong("last", rawHardware)
        }
        editor.apply()

        db.upsertSteps(today, steps, finalKcal, finalKm, profile.stepGoal)
        ctx.sendBroadcast(android.content.Intent("io.xenxo.HEALTH_UPDATED"))
        mainHandler.post { onStepsChanged?.invoke(steps, finalKcal, finalKm) }
    }

    fun startListeningSteps() {
        checkDayRollover()
        stepSensor?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 60_000_000)
        }
    }

    fun stopListeningSteps() {
        if (!isMeasuringHr) {
            sm.unregisterListener(this)
        } else {
            // Si está midiendo pulso, solo desregistramos el sensor de pasos
            stepSensor?.let { sm.unregisterListener(this, it) }
        }
    }

    fun startHeartRateMeasurement() {
        if (hrSensor == null) {
            onHeartRateUpdate?.invoke(0, "Sensor no disponible")
            return
        }
        if (isMeasuringHr) return
        isMeasuringHr = true
        onHeartRateUpdate?.invoke(0, "Midiendo...")

        sm.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_NORMAL)

        hrTimeoutRunnable = Runnable {
            stopHeartRateMeasurement("Medición completada")
        }
        mainHandler.postDelayed(hrTimeoutRunnable!!, 20_000) // 20s máx para no gastar batería
    }

    fun stopHeartRateMeasurement(statusMessage: String = "Detenido") {
        if (!isMeasuringHr) return
        isMeasuringHr = false
        hrTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        hrSensor?.let { sm.unregisterListener(this, it) }
        onHeartRateUpdate?.invoke(0, statusMessage)
        ctx.sendBroadcast(android.content.Intent("io.xenxo.HEALTH_UPDATED"))
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val raw = event.values[0].toLong()
                val today = todayStr()
                val cachedDay = prefs.getString("cached_day", "") ?: ""
                var base = prefs.getLong("base", raw)
                var carry = prefs.getLong("carry", 0L)
                var last = prefs.getLong("last", raw)

                if (cachedDay != today) {
                    if (raw < last) {
                        base = 0L
                    } else {
                        base = last
                    }
                    carry = 0L
                    last = raw
                } else if (raw < last) {
                    // Reloj reiniciado
                    carry += (last - base).coerceAtLeast(0L)
                    base = 0L
                }

                val profile = io.xenxo.profile.UserProfile.get(ctx)
                val steps = (carry + raw - base).coerceAtLeast(0L).toInt()
                val kcal = estimateKcal(steps, profile.weightKg)
                val km = estimateKm(steps, profile.heightCm)

                currentSteps = steps
                currentKcal = kcal
                currentKm = km

                prefs.edit()
                    .putString("cached_day", today)
                    .putLong("base", base)
                    .putLong("carry", carry)
                    .putLong("last", raw)
                    .putInt("cached_steps", steps)
                    .apply()

                db.upsertSteps(today, steps, kcal, km, profile.stepGoal)
                ctx.sendBroadcast(android.content.Intent("io.xenxo.HEALTH_UPDATED"))
                mainHandler.post { onStepsChanged?.invoke(steps, kcal, km) }
            }
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0].toInt()
                if (bpm > 30 && bpm < 240) {
                    val today = todayStr()
                    db.recordHeartRate(bpm = bpm, date = today)
                    ctx.sendBroadcast(android.content.Intent("io.xenxo.HEALTH_UPDATED"))
                    mainHandler.post {
                        onHeartRateUpdate?.invoke(bpm, "Detectado")
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun todayStr(): String = dayFmt.format(Date())

    fun estimateKcal(steps: Int): Int = estimateKcal(steps, io.xenxo.profile.UserProfile.get(ctx).weightKg)
    fun estimateKm(steps: Int): Float = estimateKm(steps, io.xenxo.profile.UserProfile.get(ctx).heightCm)

    companion object {
        const val DEFAULT_WEIGHT_KG = 70.0f
        const val DEFAULT_HEIGHT_CM = 175.0f
        const val KCAL_PER_STEP_KG = 0.000601f
        const val STRIDE_PER_CM = 0.415f

        fun estimateKcal(steps: Int, weightKg: Float = DEFAULT_WEIGHT_KG): Int =
            Math.round(steps * weightKg * KCAL_PER_STEP_KG)

        fun estimateKm(steps: Int, heightCm: Float = DEFAULT_HEIGHT_CM): Float =
            (steps * heightCm * STRIDE_PER_CM / 100_000f * 10f).roundToInt() / 10f
    }
}
