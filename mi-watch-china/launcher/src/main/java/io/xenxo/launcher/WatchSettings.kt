package io.xenxo.launcher

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.Settings

/**
 * Ajustes reales del reloj que puede cambiar una app sin root. Permisos (una vez, por ADB):
 *   adb shell appops set io.xenxo.launcher WRITE_SETTINGS allow                                  (brillo, pantalla)
 *   adb shell pm grant io.xenxo.launcher android.permission.WRITE_SECURE_SETTINGS                (muñeca, búsquedas, Wi-Fi)
 * Solo se tocan claves públicas de Settings.System y una de Settings.Secure: las internas de Wear OS
 * (modo Wi-Fi, pantalla en reposo) las bloquea el sistema para apps con targetSdk > 22.
 */
class WatchSettings(private val ctx: Context) {

    private val cr: ContentResolver get() = ctx.contentResolver

    val canWrite get() = Settings.System.canWrite(ctx)
    val canWriteSecure get() =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Brillo en % (1-100) sobre la escala 0-255 del sistema. */
    var brightnessPercent: Int
        get() = (Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 + 127) / 255
        set(p) {
            val v = (p.coerceIn(1, 100) * 255 + 50) / 100
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(1, 255))
        }

    var autoBrightness: Boolean
        get() = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        set(on) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        }

    /** Segundos hasta apagar la pantalla. Wear OS no admite menos de 5 s. */
    var screenTimeoutSec: Int
        get() = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 10_000) / 1000
        set(s) { Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, s.coerceAtLeast(5) * 1000) }

    /** Encender la pantalla al levantar la muñeca. */
    var wakeGesture: Boolean
        get() = Settings.Secure.getInt(cr, WAKE_GESTURE, 1) == 1
        set(on) { Settings.Secure.putInt(cr, WAKE_GESTURE, if (on) 1 else 0) }

    /** Búsquedas de Bluetooth y Wi-Fi en segundo plano para ubicación (gastan aunque la radio esté "apagada"). */
    var backgroundScans: Boolean
        get() = Settings.Global.getInt(cr, BLE_SCAN, 0) == 1 || Settings.Global.getInt(cr, WIFI_SCAN, 0) == 1
        set(on) {
            Settings.Global.putInt(cr, BLE_SCAN, if (on) 1 else 0)
            Settings.Global.putInt(cr, WIFI_SCAN, if (on) 1 else 0)
        }

    /** Encender el Wi-Fi solo al acercarse a redes guardadas. No apaga el Wi-Fi actual. */
    var wifiWakeup: Boolean
        get() = Settings.Global.getInt(cr, WIFI_WAKEUP, 1) == 1
        set(on) { Settings.Global.putInt(cr, WIFI_WAKEUP, if (on) 1 else 0) }

    /** Puerto del ADB por Wi-Fi, o -1 si está apagado. Solo se muestra: apagarlo cortaría la sesión de desarrollo. */
    val adbWifiPort: Int
        get() = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "service.adb.tcp.port") as String
        }.getOrNull()?.toIntOrNull()?.takeIf { it > 0 } ?: -1

    val batteryPercent: Int
        get() = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    companion object {
        val TIMEOUTS = intArrayOf(5, 10, 15, 30)
        // Claves ocultas de Settings.Secure / Settings.Global (no están en el SDK público)
        private const val WAKE_GESTURE = "wake_gesture_enabled"
        private const val BLE_SCAN = "ble_scan_always_enabled"
        private const val WIFI_SCAN = "wifi_scan_always_enabled"
        private const val WIFI_WAKEUP = "wifi_wakeup_enabled"
    }
}
