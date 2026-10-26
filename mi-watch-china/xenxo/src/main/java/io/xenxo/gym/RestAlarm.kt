package io.xenxo.gym

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Vibrator

/**
 * Fin del descanso aunque la pantalla esté apagada. Con la pantalla apagada (10 s) el procesador se duerme
 * y un CountDownTimer se congela: la vibración llegaba al volver a mirar el reloj. Por eso, al empezar cada
 * descanso se programa UNA alarma exacta del sistema que despierta lo justo para vibrar.
 * Es la única alarma exacta del proyecto: la pide el usuario al guardar una serie, se cancela con
 * "Saltar"/atrás y se reprograma con "+30 s".
 */
class RestAlarm : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        @Suppress("DEPRECATION")
        if (v.hasVibrator()) v.vibrate(longArrayOf(0, 300, 150, 400), -1)
    }

    companion object {
        /** [endAt] en SystemClock.elapsedRealtime(). */
        fun schedule(ctx: Context, endAt: Long) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAt, pending(ctx))
        }

        fun cancel(ctx: Context) {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        }

        private fun pending(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, RestAlarm::class.java), PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
