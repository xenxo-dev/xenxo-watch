package io.xenxo.intervals

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator

/**
 * Alarma exacta del sistema para cambios de fase en entrenamientos por intervalos
 * cuando la pantalla está apagada.
 * Se programa una única alarma para el final de la fase actual (trabajo o descanso)
 * y se cancela al pausar, saltar o terminar.
 */
class IntervalAlarm : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val isWork = intent.getBooleanExtra(EXTRA_IS_WORK, true)
        @Suppress("DEPRECATION")
        if (v != null && v.hasVibrator()) {
            if (isWork) {
                // Doble pulso para entrar en trabajo
                v.vibrate(longArrayOf(0, 120, 60, 150), -1)
            } else {
                // Pulso profundo para entrar en descanso
                v.vibrate(longArrayOf(0, 250), -1)
            }
        }
        listener?.invoke(isWork)
    }

    companion object {
        const val EXTRA_IS_WORK = "is_work"
        private const val REQ_CODE = 4002

        var listener: ((isWork: Boolean) -> Unit)? = null

        fun schedule(ctx: Context, endAtMillis: Long, isWork: Boolean) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, IntervalAlarm::class.java).apply {
                putExtra(EXTRA_IS_WORK, isWork)
            }
            val pending = PendingIntent.getBroadcast(
                ctx, REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAtMillis, pending)
        }

        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(ctx, IntervalAlarm::class.java)
            val pending = PendingIntent.getBroadcast(
                ctx, REQ_CODE, intent, PendingIntent.FLAG_NO_CREATE
            )
            if (pending != null) {
                am.cancel(pending)
                pending.cancel()
            }
        }
    }
}
