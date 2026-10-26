package io.xenxo.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pasos de hoy a partir del sensor hardware TYPE_STEP_COUNTER (bajo consumo, lo cuenta el chip).
 * El sensor da pasos desde el último arranque; aquí se convierte a "pasos de hoy".
 * Solo se escucha mientras la esfera está visible.
 *
 * Limitación conocida: los pasos entre la última lectura de ayer y medianoche cuentan para hoy.
 * TODO (Claude Code): mover a un servicio/alarma ligera que muestree cerca de medianoche, o al módulo :health.
 */
class StepCounter(ctx: Context, private val onSteps: (Int) -> Unit) : SensorEventListener {

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.ROOT)

    val available get() = sensor != null

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 60_000_000) }
        val cached = prefs.getInt("today_cache", -1)
        if (cached >= 0 && prefs.getString("day", "") == today()) onSteps(cached)
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        val raw = e.values[0].toLong()
        val day = today()
        var base = prefs.getLong("base", raw)
        var carry = prefs.getLong("carry", 0)   // pasos de hoy acumulados antes de un reinicio
        val last = prefs.getLong("last", raw)

        if (prefs.getString("day", "") != day) { base = last; carry = 0 }   // día nuevo
        if (raw < last) { carry += last - base; base = 0 }                    // el reloj se ha reiniciado

        val steps = (carry + raw - base).coerceAtLeast(0).toInt()
        prefs.edit()
            .putString("day", day).putLong("base", base).putLong("carry", carry)
            .putLong("last", raw).putInt("today_cache", steps)
            .apply()
        onSteps(steps)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun today() = dayFmt.format(Date())
}
