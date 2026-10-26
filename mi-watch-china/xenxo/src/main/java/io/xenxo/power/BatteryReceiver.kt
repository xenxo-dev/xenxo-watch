package io.xenxo.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Receiver ligero que registra cambios en la batería sin wakelocks ni servicios de fondo.
 */
class BatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED) {
            
            // Leer estado pasivo del sticky intent de batería
            val bIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (bIntent != null) {
                val level = bIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = bIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (level * 100) / scale else level
                val temp = bIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
                val status = bIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

                BatteryLog(context).record(pct, temp, isCharging)
            }
        }
    }
}
