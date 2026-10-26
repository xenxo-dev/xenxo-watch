// La librería wearable support está obsoleta para Wear OS 3+, pero es la que corresponde a Wear OS 2 (Android 9).
@file:Suppress("DEPRECATION")

package io.xenxo.launcher.watchface

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.support.wearable.complications.ComplicationData
import android.support.wearable.watchface.WatchFaceService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Datos que pintan las esferas: pasos, kcal, km, pulso y batería.
 *
 * Podómetro continuo de hardware: escucha directa a Sensor.TYPE_STEP_COUNTER con hardware batching
 * en el sensor hub Snapdragon Wear 3100 (consumo ~0 mA).
 * Sincronización bidireccional con el HealthProvider de xenxo (:xenxo).
 */
class FaceData(private val ctx: Context, private val onChange: () -> Unit) : SensorEventListener {

    var steps = -1; private set
    var kcal = -1; private set
    var km = -1f; private set
    var bpm = -1; private set

    var stepGoal: Int = STEP_GOAL; private set
    var userWeight: Float = DEFAULT_WEIGHT_KG; private set
    var userHeight: Float = DEFAULT_HEIGHT_CM; private set

    private var stepsFromXiaomi = false
    private var kcalFromXiaomi = false
    private var kmFromXiaomi = false

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepSensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs = ctx.getSharedPreferences("face_steps", Context.MODE_PRIVATE)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    private var isListeningSteps = false

    private var healthReceiverRegistered = false
    private val healthReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            readFromXenxoHealth()
        }
    }

    /** Consulta autoritativa al HealthProvider nativo del reloj */
    fun readFromXenxoHealth(): Boolean {
        return try {
            val res = ctx.contentResolver.call(
                android.net.Uri.parse("content://io.xenxo.health.provider"),
                "getDaily",
                null,
                null
            ) ?: return false

            val w = res.getFloat("weightKg", userWeight)
            val h = res.getFloat("heightCm", userHeight)
            val g = res.getInt("goalSteps", stepGoal)
            if (w > 0f) userWeight = w
            if (h > 0f) userHeight = h
            if (g > 0) stepGoal = g

            val xSteps = res.getInt("steps", -1)
            val xKcal = res.getInt("kcal", -1)
            val xKm = res.getFloat("km", -1f)
            val xBpm = res.getInt("lastBpm", -1)
            android.util.Log.i("FaceData", "readFromXenxoHealth: xSteps=$xSteps, xKcal=$xKcal, xKm=$xKm, xBpm=$xBpm")

            var changed = false
            // Pulso cardíaco
            if (xBpm > 0 && bpm != xBpm) {
                bpm = xBpm
                changed = true
            }

            // Pasos, calorías y distancia: inicializar si aún no tenemos lectura o si el proveedor trae un dato mayor
            if (xSteps >= 0 && (steps < 0 || xSteps > steps)) {
                steps = xSteps
                if (!kcalFromXiaomi || kcal < 0) {
                    kcal = if (xKcal >= 0) xKcal else Math.round(steps * userWeight * KCAL_PER_STEP_KG)
                }
                if (!kmFromXiaomi || km < 0f) {
                    km = if (xKm >= 0f) xKm else (steps * userHeight * STRIDE_PER_CM / 100_000f * 10f).roundToInt() / 10f
                }
                changed = true
            }

            if (changed) {
                onChange()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun syncStepsToProvider(s: Int, k: Int = -1, m: Float = -1f, raw: Long = -1L) {
        try {
            val b = android.os.Bundle().apply {
                putInt("steps", s)
                if (k >= 0) putInt("kcal", k)
                if (m >= 0f) putFloat("km", m)
                if (raw >= 0L) putLong("raw", raw)
            }
            ctx.contentResolver.call(
                android.net.Uri.parse("content://io.xenxo.health.provider"),
                "syncSteps",
                null,
                b
            )
        } catch (_: Exception) {}
    }

    fun syncHeartRateToProvider(b: Int) {
        try {
            val bundle = android.os.Bundle().apply { putInt("bpm", b) }
            ctx.contentResolver.call(
                android.net.Uri.parse("content://io.xenxo.health.provider"),
                "syncHeartRate",
                null,
                bundle
            )
        } catch (_: Exception) {}
    }

    fun refresh() {
        readFromXenxoHealth()
    }

    /** Declara las complicaciones de Mi Fitness como fuente por defecto. Llamar en Engine.onCreate. */
    fun attach(engine: WatchFaceService.Engine) {
        // En Wear OS 2, setActiveComplications DEBE llamarse antes de setDefaultComplicationProvider
        engine.setActiveComplications(STEPS, KCAL, KM, HR)
        engine.setDefaultComplicationProvider(STEPS, provider("sportrecord.complication.StepComplicationDataProvider"),
            ComplicationData.TYPE_RANGED_VALUE)
        engine.setDefaultComplicationProvider(KCAL, provider("sportrecord.complication.CalorieComplicationDataProvider"),
            ComplicationData.TYPE_SHORT_TEXT)
        engine.setDefaultComplicationProvider(KM, provider("sportrecord.complication.DistanceComplicationProvider"),
            ComplicationData.TYPE_SHORT_TEXT)
        engine.setDefaultComplicationProvider(HR, provider("heartrate.complication.HeartRateProviderService"),
            ComplicationData.TYPE_SHORT_TEXT)
    }

    /** Mientras la esfera es visible (también en modo ambiente). */
    fun start() {
        readFromXenxoHealth()
        if (!healthReceiverRegistered) {
            try {
                ctx.registerReceiver(healthReceiver, IntentFilter("io.xenxo.HEALTH_UPDATED"))
                healthReceiverRegistered = true
            } catch (_: Exception) {}
        }
        startListeningSteps(realtime = true)
    }

    fun stop() {
        if (healthReceiverRegistered) {
            try {
                ctx.unregisterReceiver(healthReceiver)
                healthReceiverRegistered = false
            } catch (_: Exception) {}
        }
        // Cuando no es visible (otra app encima o pantalla reposo prolongado),
        // mantenemos la escucha en segundo plano con latencia de 60s en el DSP hardware
        startListeningSteps(realtime = false)
    }

    fun destroy() {
        stop()
        try {
            sm?.unregisterListener(this)
            isListeningSteps = false
        } catch (_: Exception) {}
    }

    private fun startListeningSteps(realtime: Boolean) {
        if (stepSensor == null || sm == null) return
        try {
            sm.unregisterListener(this)
            val latencyUs = if (realtime) 0 else 60_000_000
            val delay = if (realtime) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_NORMAL
            sm.registerListener(this, stepSensor, delay, latencyUs)
            isListeningSteps = true
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val raw = event.values[0].toLong()
            val today = dayFmt.format(Date())
            var base = prefs.getLong("base", raw)
            var carry = prefs.getLong("carry", 0L)
            val last = prefs.getLong("last", raw)
            val cachedDay = prefs.getString("cached_day", "")

            if (cachedDay != today) {
                // Nuevo día
                base = last
                carry = 0L
            }
            if (raw < last) {
                // Reloj reiniciado a mitad del día
                carry += (last - base)
                base = 0L
            }

            // Si es primer arranque en este día y ya había pasos en DB, alinear la base
            if (cachedDay != today && steps > 0 && (raw - base) <= 0) {
                base = if (raw >= steps) raw - steps else 0L
            }

            val s = (carry + raw - base).coerceAtLeast(0L).toInt()
            val k = if (!kcalFromXiaomi || kcal < 0) Math.round(s * userWeight * KCAL_PER_STEP_KG) else kcal
            val m = if (!kmFromXiaomi || km < 0f) (s * userHeight * STRIDE_PER_CM / 100_000f * 10f).roundToInt() / 10f else km

            val changed = (s != steps || k != kcal || m != km)
            steps = s
            kcal = k
            km = m

            prefs.edit()
                .putString("cached_day", today)
                .putLong("base", base)
                .putLong("carry", carry)
                .putLong("last", raw)
                .putInt("cached_steps", s)
                .apply()

            if (changed) {
                onChange()
                syncStepsToProvider(s, k, m, raw)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun onComplication(id: Int, d: ComplicationData) {
        val now = System.currentTimeMillis()
        val number: Float? = when (d.type) {
            ComplicationData.TYPE_RANGED_VALUE -> d.value
            ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_LONG_TEXT ->
                (d.shortText ?: d.longText)?.getText(ctx, now)?.let { parseNumber(it.toString()) }
            else -> null
        }
        val textStr = (d.shortText ?: d.longText)?.getText(ctx, now)?.toString()
        android.util.Log.i("FaceData", "onComplication id=$id text='$textStr' number=$number")
        when (id) {
            STEPS -> {
                if (number != null && number.toInt() >= 0) {
                    val s = number.toInt()
                    steps = s
                    stepsFromXiaomi = true
                    if (!kcalFromXiaomi) kcal = Math.round(s * userWeight * KCAL_PER_STEP_KG)
                    if (!kmFromXiaomi) km = (s * userHeight * STRIDE_PER_CM / 100_000f * 10f).roundToInt() / 10f
                    syncStepsToProvider(s, kcal, km)
                }
            }
            KCAL -> {
                if (number != null && number > 0) {
                    val k = number.toInt()
                    kcal = k
                    kcalFromXiaomi = true
                    if (steps >= 0) syncStepsToProvider(steps, kcal, km)
                }
            }
            KM -> {
                if (number != null && number > 0f) {
                    val rawStr = (d.shortText ?: d.longText)?.getText(ctx, now)?.toString()?.lowercase(Locale.ROOT) ?: ""
                    var parsed = number
                    if (rawStr.contains("m") && !rawStr.contains("km")) {
                        parsed /= 1000f
                    } else if (parsed > 50f || (steps > 0 && parsed > steps * 0.002f)) {
                        parsed /= 1000f
                    }
                    km = (parsed * 10f).roundToInt() / 10f
                    kmFromXiaomi = true
                    if (steps >= 0) syncStepsToProvider(steps, kcal, km)
                }
            }
            HR -> {
                if (number != null && number > 30) {
                    val b = number.toInt()
                    bpm = b
                    syncHeartRateToProvider(b)
                }
            }
        }
        onChange()
    }

    /** Pasos y, si Xiaomi no da kcal/km, su estimación con el perfil de usuario. */
    fun setStepsEstimated(n: Int) {
        steps = n
        if (!kcalFromXiaomi) kcal = Math.round(n * userWeight * KCAL_PER_STEP_KG)
        if (!kmFromXiaomi) km = (n * userHeight * STRIDE_PER_CM / 100_000f * 10f).roundToInt() / 10f
    }

    /** Batería del aviso "sticky" del sistema: sin receptor registrado ni despertar nada. */
    fun batteryPercent(): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0) -1 else level * 100 / scale
    }

    private fun provider(cls: String) = ComponentName(FITNESS, "com.xiaomi.wear.health.$cls")

    companion object {
        const val STEPS = 1
        const val KCAL = 2
        const val KM = 3
        const val HR = 4
        const val FITNESS = "com.xiaomi.wear.fitness"

        const val STEP_GOAL = 6_000
        private const val DEFAULT_WEIGHT_KG = 70.0f
        private const val DEFAULT_HEIGHT_CM = 175.0f
        private const val KCAL_PER_STEP_KG = 0.000601f
        private const val STRIDE_PER_CM = 0.415f   // zancada ≈ 41,5 % de la altura

        private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
        private fun parseNumber(s: String) = NUMBER.find(s)?.value?.replace(',', '.')?.toFloatOrNull()
    }
}

/** Formatos en castellano de las esferas (fijos: el diseño está en castellano). */
object FaceFormat {
    private val DAYS = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
    private val MONTHS = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** "Mié 21 oct" */
    fun shortDate(c: java.util.Calendar) =
        "${DAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]} ${c.get(java.util.Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(java.util.Calendar.MONTH)]}"

    /** "MIÉ 21" */
    fun dayAndNumber(c: java.util.Calendar) =
        "${DAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1].uppercase(Locale("es", "ES"))} ${c.get(java.util.Calendar.DAY_OF_MONTH)}"

    /**
     * "7.750": punto de miles siempre, agrupado a mano. Ni el formato de "es" ni el de Locale.ROOT de este
     * Android agrupan los números de 4 cifras (en el reloj salía "1673").
     */
    fun thousands(n: Int): String {
        val digits = kotlin.math.abs(n.toLong()).toString()
        val sb = StringBuilder(if (n < 0) "-" else "")
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return sb.toString()
    }

    /** "4,9" */
    fun oneDecimal(f: Float) = String.format(Locale.ROOT, "%.1f", f).replace('.', ',')

    fun hhmm(c: java.util.Calendar) =
        String.format(Locale.ROOT, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}
