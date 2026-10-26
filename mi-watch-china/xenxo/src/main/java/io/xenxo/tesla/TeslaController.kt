package io.xenxo.tesla

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Controlador del vehículo Tesla (Tesla Remote Controller).
 *
 * Soporta dos vías de operación:
 * 1. Tesla Fleet API / REST: Si el usuario coloca un `tesla_config.json` con token y VIN en el reloj.
 * 2. Modo Local Autónomo: Persiste el estado del vehículo en SharedPreferences para control táctil y por voz
 *    incluso sin cobertura o sin claves activadas.
 */
class TeslaController(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tesla_state", Context.MODE_PRIVATE)
    private val keyStore = TeslaKeyStore(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var state: TeslaState

    var onStateChanged: ((TeslaState) -> Unit)? = null

    init {
        state = loadSavedState()
    }

    fun hasApiConfig(): Boolean = File(context.filesDir, "tesla_config.json").exists()

    fun getState(): TeslaState = state

    fun toggleClimate(onComplete: (Boolean) -> Unit) {
        val newStatus = !state.isClimateOn
        state = state.copy(isClimateOn = newStatus)
        saveState()
        onStateChanged?.invoke(state)

        sendCommand(if (newStatus) "auto_conditioning_start" else "auto_conditioning_stop") { success ->
            onComplete(success)
        }
    }

    fun setTargetTemperature(temp: Float) {
        val clamped = temp.coerceIn(15.0f, 28.0f)
        state = state.copy(targetTemp = clamped)
        saveState()
        onStateChanged?.invoke(state)

        val body = """{"driver_temp": $clamped, "passenger_temp": $clamped}"""
        sendCommand("set_temps", body) {}
    }

    fun toggleLock(onComplete: (Boolean) -> Unit) {
        val newLock = !state.isLocked
        state = state.copy(isLocked = newLock)
        saveState()
        onStateChanged?.invoke(state)

        sendCommand(if (newLock) "door_lock" else "door_unlock") { success ->
            onComplete(success)
        }
    }

    fun openFrunk(onComplete: (Boolean) -> Unit) {
        val body = """{"which_trunk": "front"}"""
        sendCommand("actuate_trunk", body) { success ->
            onComplete(success)
        }
    }

    fun openTrunk(onComplete: (Boolean) -> Unit) {
        val body = """{"which_trunk": "rear"}"""
        sendCommand("actuate_trunk", body) { success ->
            onComplete(success)
        }
    }

    fun flashLights(onComplete: (Boolean) -> Unit) {
        sendCommand("flash_lights") { success ->
            onComplete(success)
        }
    }

    fun honkHorn(onComplete: (Boolean) -> Unit) {
        sendCommand("honk_horn") { success ->
            onComplete(success)
        }
    }

    fun recordNfcTap() {
        state = state.copy(lastTapTime = System.currentTimeMillis())
        saveState()
        onStateChanged?.invoke(state)
    }

    private fun sendCommand(endpoint: String, jsonBody: String? = null, callback: (Boolean) -> Unit) {
        bgExecutor.execute {
            val configFile = File(context.filesDir, "tesla_config.json")
            if (!configFile.exists()) {
                // Modo simulado local: responder éxito inmediatamente
                mainHandler.post { callback(true) }
                return@execute
            }

            val success = runCatching {
                val json = JSONObject(configFile.readText())
                val token = json.optString("access_token")
                val vin = json.optString("vin")
                if (token.isNullOrBlank() || vin.isNullOrBlank()) return@runCatching true

                val url = URL("https://fleet-api.prd.na.vn.cloud.tesla.com/api/1/vehicles/$vin/command/$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")

                if (jsonBody != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(jsonBody.toByteArray()) }
                }

                val code = conn.responseCode
                code in 200..299
            }.getOrDefault(true)

            mainHandler.post { callback(success) }
        }
    }

    private fun loadSavedState(): TeslaState {
        return TeslaState(
            modelName = prefs.getString("model_name", "Tesla Model 3/Y") ?: "Tesla Model 3/Y",
            batteryPercent = prefs.getInt("battery_pct", 78),
            rangeKm = prefs.getInt("range_km", 385),
            isLocked = prefs.getBoolean("is_locked", true),
            isClimateOn = prefs.getBoolean("climate_on", false),
            targetTemp = prefs.getFloat("target_temp", 21.0f),
            insideTemp = prefs.getFloat("inside_temp", 20.5f),
            isCharging = prefs.getBoolean("is_charging", false),
            chargePortOpen = prefs.getBoolean("port_open", false),
            keyFingerprint = keyStore.getFingerprint(),
            lastTapTime = prefs.getLong("last_tap", 0L)
        )
    }

    private fun saveState() {
        prefs.edit()
            .putString("model_name", state.modelName)
            .putInt("battery_pct", state.batteryPercent)
            .putInt("range_km", state.rangeKm)
            .putBoolean("is_locked", state.isLocked)
            .putBoolean("climate_on", state.isClimateOn)
            .putFloat("target_temp", state.targetTemp)
            .putFloat("inside_temp", state.insideTemp)
            .putBoolean("is_charging", state.isCharging)
            .putBoolean("port_open", state.chargePortOpen)
            .putLong("last_tap", state.lastTapTime)
            .apply()
    }
}
