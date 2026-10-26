package io.xenxo.power

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings

/**
 * Gestor de perfiles de energía para el Xiaomi Mi Watch.
 * Requiere conceder por ADB:
 *   adb shell pm grant io.xenxo android.permission.WRITE_SECURE_SETTINGS
 *   adb shell appops set io.xenxo WRITE_SETTINGS allow
 */
class PowerProfiles(private val ctx: Context) {

    private val cr: ContentResolver get() = ctx.contentResolver
    private val prefs: SharedPreferences = ctx.getSharedPreferences("power_profiles", Context.MODE_PRIVATE)
    private val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    enum class Profile(val id: String) {
        DAY("day"),
        NIGHT("night"),
        WORKOUT("workout"),
        SAVING("saving"),
        CUSTOM("custom")
    }

    val hasPermissions: Boolean
        get() = Settings.System.canWrite(ctx) &&
            ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    var currentProfile: Profile
        get() = Profile.values().firstOrNull { it.id == prefs.getString("active_profile", Profile.DAY.id) } ?: Profile.DAY
        private set(p) = prefs.edit().putString("active_profile", p.id).apply()

    /** Puerto activo de ADB por Wi-Fi (> 0 si está escuchando, -1 si apagado). */
    val adbWifiPort: Int
        get() = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "service.adb.tcp.port") as String
        }.getOrNull()?.toIntOrNull()?.takeIf { it > 0 } ?: -1

    val isWifiEnabled: Boolean
        get() = wifiManager.isWifiEnabled

    /**
     * Pantalla siempre activa (AOD) de Wear OS, o null si no se puede leer. Vive en el proveedor de ajustes
     * de Wear (`ambient_config`, clave `ambient_enabled`): se puede LEER, pero escribirlo exige un permiso
     * de firma de Google (com.google.android.wearable.WRITE_SETTINGS). Se cambia en Ajustes → Pantalla.
     */
    val isAodEnabled: Boolean?
        get() = runCatching {
            cr.query(AMBIENT_CONFIG, null, null, null, null)?.use { c ->
                val k = c.getColumnIndex("key"); val v = c.getColumnIndex("value")
                while (c.moveToNext()) if (c.getString(k) == "ambient_enabled") return@use c.getString(v) == "1"
                null
            }
        }.getOrNull()

    fun setWifiEnabled(enabled: Boolean) {
        runCatching { wifiManager.isWifiEnabled = enabled }
    }

    /** Guarda los ajustes actuales como punto de restauración si no existen. */
    fun saveOriginalsIfNeeded() {
        if (!prefs.contains("orig_brightness")) {
            val brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val timeout = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 10000)
            // De fábrica es 0 (diagnóstico del 2026-10-21); una versión anterior lo dejó a 1, así que no se lee
            val doze = FACTORY_DOZE
            val wake = Settings.Secure.getInt(cr, WAKE_GESTURE, 1)
            prefs.edit()
                .putInt("orig_brightness", brightness)
                .putInt("orig_timeout", timeout)
                .putInt("orig_doze", doze)
                .putInt("orig_wake", wake)
                .apply()
        }
    }

    fun applyProfile(p: Profile): Boolean {
        if (!hasPermissions) return false
        saveOriginalsIfNeeded()

        // doze_enabled es el "ambient display" de los móviles: en Wear OS no es el AOD. Versiones anteriores lo
        // cambiaban por error; se deja siempre en su valor original. El AOD real solo se cambia en Ajustes.
        setSecure(DOZE_ENABLED, prefs.getInt("orig_doze", FACTORY_DOZE))

        when (p) {
            Profile.DAY -> {
                // Modo día: brillo bajo fijo (70/255), timeout 10s, levantar muñeca on
                setSystem(Settings.System.SCREEN_BRIGHTNESS, 70)
                setSystem(Settings.System.SCREEN_OFF_TIMEOUT, 10_000)
                setSecure(WAKE_GESTURE, 1)
                setGlobal(BLE_SCAN, 0)
                setGlobal(WIFI_SCAN, 0)
            }
            Profile.NIGHT -> {
                // Modo noche: levantar muñeca off, brillo mínimo (10/255), timeout 5s
                setSecure(WAKE_GESTURE, 0)
                setSystem(Settings.System.SCREEN_BRIGHTNESS, 10)
                setSystem(Settings.System.SCREEN_OFF_TIMEOUT, 5_000)
                setGlobal(BLE_SCAN, 0)
                setGlobal(WIFI_SCAN, 0)
            }
            Profile.WORKOUT -> {
                // Modo entreno: brillo alto (150/255), pantalla dura 30s encendida, levantar muñeca on
                setSecure(WAKE_GESTURE, 1)
                setSystem(Settings.System.SCREEN_BRIGHTNESS, 150)
                setSystem(Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            }
            Profile.SAVING -> {
                // Ahorro extremo: levantar muñeca fuera, brillo mínimo, timeout mínimo (5s)
                setSecure(WAKE_GESTURE, 0)
                setSystem(Settings.System.SCREEN_BRIGHTNESS, 10)
                setSystem(Settings.System.SCREEN_OFF_TIMEOUT, 5_000)
                setGlobal(BLE_SCAN, 0)
                setGlobal(WIFI_SCAN, 0)
            }
            Profile.CUSTOM -> {}
        }
        currentProfile = p
        return true
    }

    fun restoreOriginals(): Boolean {
        if (!hasPermissions || !prefs.contains("orig_brightness")) return false
        val brightness = prefs.getInt("orig_brightness", 70)
        val timeout = prefs.getInt("orig_timeout", 10000)
        val doze = prefs.getInt("orig_doze", FACTORY_DOZE)
        val wake = prefs.getInt("orig_wake", 1)

        setSystem(Settings.System.SCREEN_BRIGHTNESS, brightness)
        setSystem(Settings.System.SCREEN_OFF_TIMEOUT, timeout)
        setSecure(DOZE_ENABLED, doze)
        setSecure(WAKE_GESTURE, wake)

        currentProfile = Profile.CUSTOM
        return true
    }

    private fun setSystem(name: String, value: Int) {
        runCatching { Settings.System.putInt(cr, name, value) }
    }

    private fun setSecure(name: String, value: Int) {
        runCatching { Settings.Secure.putInt(cr, name, value) }
    }

    private fun setGlobal(name: String, value: Int) {
        runCatching { Settings.Global.putInt(cr, name, value) }
    }

    companion object {
        private val AMBIENT_CONFIG = android.net.Uri.parse("content://com.google.android.wearable.settings/ambient_config")
        const val DOZE_ENABLED = "doze_enabled"
        private const val FACTORY_DOZE = 0
        const val WAKE_GESTURE = "wake_gesture_enabled"
        const val BLE_SCAN = "ble_scan_always_enabled"
        const val WIFI_SCAN = "wifi_scan_always_enabled"
    }
}
