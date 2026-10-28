package io.xenxo.health

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import io.xenxo.gym.GymDb
import io.xenxo.padel.PadelStore
import io.xenxo.power.BatteryLog
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Proveedor local para compartir métricas de salud, gym, pádel y batería
 * con el launcher, las esferas xenxo y el asistente Wrist AI.
 */
class HealthProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        when (method) {
            "getDaily" -> return getDailyBundle(ctx)
            "getHealthContext" -> return getHealthContextBundle(ctx)
            "syncSteps" -> {
                val s = extras?.getInt("steps", -1) ?: (arg?.toIntOrNull() ?: -1)
                val kcal = extras?.getInt("kcal", -1) ?: -1
                val km = extras?.getFloat("km", -1f) ?: -1f
                val raw = extras?.getLong("raw", -1L) ?: -1L
                if (s >= 0) {
                    val mgr = HealthManager(ctx)
                    mgr.syncSteps(s, kcal, km, raw)
                    return Bundle().apply { putBoolean("success", true) }
                }
                return Bundle().apply { putBoolean("success", false) }
            }
            "syncHeartRate" -> {
                val bpm = extras?.getInt("bpm", -1) ?: (arg?.toIntOrNull() ?: -1)
                if (bpm in 31..239) {
                    val db = HealthDb(ctx)
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    db.recordHeartRate(bpm, System.currentTimeMillis(), today)
                    ctx.sendBroadcast(Intent("io.xenxo.HEALTH_UPDATED"))
                    return Bundle().apply { putBoolean("success", true) }
                }
                return Bundle().apply { putBoolean("success", false) }
            }
            "logWeight" -> {
                val fromExtra = extras?.getFloat("weight", 0f) ?: 0f
                val wt = if (fromExtra > 0f) fromExtra else (arg?.toFloatOrNull() ?: 0f)
                if (wt in 30.0f..250.0f) {
                    val gymDb = GymDb(ctx)
                    gymDb.logBodyWeight(wt)
                    io.xenxo.sync.XenxoSync.sync(ctx)
                    val b = Bundle()
                    b.putBoolean("success", true)
                    b.putFloat("weight", wt)
                    return b
                }
                return Bundle().apply { putBoolean("success", false) }
            }
            "getPRs" -> {
                val prs = GymDb(ctx).getAllPRs()
                val b = Bundle()
                val json = JSONObject()
                prs.forEach { (k, v) -> 
                    b.putFloat(k, v)
                    json.put(k, v.toDouble())
                }
                b.putString("json", json.toString())
                return b
            }
        }
        return null
    }

    private fun getDailyBundle(ctx: Context): Bundle {
        val db = HealthDb(ctx)
        val manager = HealthManager(ctx)
        manager.checkDayRollover()
        val today = manager.todayStr()
        val record = db.getDaily(today)
        val b = Bundle()
        val steps = if (manager.currentSteps > 0) manager.currentSteps else (record?.steps ?: 0)
        val kcal = if (manager.currentKcal > 0) manager.currentKcal else (record?.kcal ?: 0)
        val km = if (manager.currentKm > 0f) manager.currentKm else (record?.km ?: 0f)
        var bpm = if (record != null && record.lastBpm > 0) record.lastBpm else 0
        if (bpm <= 0) {
            bpm = db.getLatestBpm()
        }

        val profile = io.xenxo.profile.UserProfile.get(ctx)
        b.putInt("steps", steps)
        b.putInt("kcal", kcal)
        b.putFloat("km", km)
        b.putInt("lastBpm", bpm)
        b.putString("date", today)
        b.putInt("goalSteps", profile.stepGoal)
        b.putFloat("weightKg", profile.weightKg)
        b.putFloat("heightCm", profile.heightCm)
        b.putString("unit", profile.unit)
        return b
    }

    private fun getHealthContextBundle(ctx: Context): Bundle {
        val b = Bundle()
        val fmtDay = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val now = Date()
        val todayStr = fmtDay.format(now)

        // 1. Salud hoy
        val healthDb = HealthDb(ctx)
        val healthMgr = HealthManager(ctx)
        healthMgr.checkDayRollover()
        val healthRec = healthDb.getDaily(todayStr)
        val steps = if (healthMgr.currentSteps > 0) healthMgr.currentSteps else (healthRec?.steps ?: 0)
        val kcal = if (healthMgr.currentKcal > 0) healthMgr.currentKcal else (healthRec?.kcal ?: 0)
        val km = if (healthMgr.currentKm > 0f) healthMgr.currentKm else (healthRec?.km ?: 0f)
        val bpm = if (healthRec != null && healthRec.lastBpm > 0) healthRec.lastBpm else 0

        b.putInt("steps", steps)
        b.putInt("kcal", kcal)
        b.putFloat("km", km)
        b.putInt("lastBpm", bpm)

        // 2. Peso y Gym
        val gymDb = GymDb(ctx)
        val weights = gymDb.getWeightHistory(7)
        val currentWeight = if (weights.isNotEmpty()) weights.first().second else gymDb.getLastBodyWeight()
        val avgWeight = if (weights.isNotEmpty()) weights.map { it.second }.average().toFloat() else currentWeight
        val weightTrend = if (weights.size >= 2) {
            val oldest = weights.last().second
            (currentWeight - oldest)
        } else 0f

        b.putFloat("currentWeight", currentWeight)
        b.putFloat("avgWeight7d", avgWeight)
        b.putFloat("weightTrend", weightTrend)

        val lastSession = gymDb.getLastWorkoutSession()
        val sessionJson = JSONObject()
        if (lastSession != null) {
            val sDate = fmtDay.format(Date(lastSession.dateTs))
            sessionJson.put("fecha", sDate)
            sessionJson.put("total_series", lastSession.totalSets)
            sessionJson.put("ejercicios", lastSession.exercisesCount)
            sessionJson.put("destacado", "${lastSession.topExercise} ${lastSession.topReps}x${lastSession.topWeightKg}kg")
            b.putString("lastWorkoutDate", sDate)
            b.putInt("lastWorkoutSets", lastSession.totalSets)
            b.putString("lastWorkoutTop", "${lastSession.topExercise} ${lastSession.topReps}x${lastSession.topWeightKg}kg")
        }

        // 3. Pádel
        val padelStore = PadelStore(ctx)
        val padelHistory = padelStore.history(5)
        val padelJson = JSONObject()
        if (padelHistory.isNotEmpty()) {
            val lastPadel = padelHistory.first()
            val pDate = fmtDay.format(Date(lastPadel.start))
            val winStr = when (lastPadel.weWon) { true -> "Victoria"; false -> "Derrota"; null -> "Empate/Abandonado" }
            padelJson.put("ultima_fecha", pDate)
            padelJson.put("resultado", lastPadel.result)
            padelJson.put("estado", winStr)
            b.putString("lastPadelDate", pDate)
            b.putString("lastPadelResult", "${lastPadel.result} ($winStr)")
        }

        // 4. Batería
        val batteryLog = BatteryLog(ctx)
        val drainRate = batteryLog.calculateDrainRatePerHour(3) ?: 2.5f
        val battFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battIntent = ctx.registerReceiver(null, battFilter)
        val level = battIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 50
        b.putInt("batteryLevel", batteryPct)
        b.putFloat("drainRate", drainRate)

        // 5. Construir JSON compacto (~250 bytes) para el prompt de la IA
        val rootJson = JSONObject().apply {
            put("fecha", todayStr)
            put("peso", JSONObject().apply {
                put("hoy", (currentWeight * 10).roundToInt() / 10.0)
                put("media_7d", (avgWeight * 10).roundToInt() / 10.0)
                put("tendencia_sem", (weightTrend * 10).roundToInt() / 10.0)
            })
            put("hoy", JSONObject().apply {
                put("pasos", steps)
                put("kcal", kcal)
                put("ppm", bpm)
            })
            if (sessionJson.length() > 0) put("ultimo_gym", sessionJson)
            if (padelJson.length() > 0) put("ultimo_padel", padelJson)
            put("bateria", JSONObject().apply {
                put("nivel", batteryPct)
                put("gasto_pct_h", (drainRate * 10).roundToInt() / 10.0)
            })
        }

        val jsonStr = rootJson.toString()
        b.putString("json", jsonStr)
        return b
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.io.xenxo.health"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
