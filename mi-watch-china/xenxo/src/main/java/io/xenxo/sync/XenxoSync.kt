package io.xenxo.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.Wearable
import io.xenxo.gym.GymDb
import io.xenxo.padel.PadelStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sincronización reloj → móvil por el Data Layer de Wear OS (ver xenxo-phone/XENXO.md).
 *
 * Con Wear OS China SIEMPRE inicia el reloj: el móvil solo puede contestar a un mensaje nuestro.
 *  1. sync() manda "/xenxo/v1/push" con todo lo pendiente (series, peso en ayunas, partidos de pádel).
 *  2. El móvil lo guarda y contesta "/xenxo/v1/ack" con los uid guardados (+ rutinas en el futuro).
 *  3. WatchSyncListener marca como enviado solo lo confirmado. Sin conexión no se pierde nada:
 *     se reintenta al abrir la app, al guardar algo o con "Sincronizar".
 * Sin servicios ni alarmas: un mensaje cuando hay motivo, y el servicio de Google ya está en marcha.
 */
object XenxoSync {

    const val PATH_PUSH = "/xenxo/v1/push"
    const val PATH_ACK = "/xenxo/v1/ack"
    private const val TAG = "xenxo-sync"
    /** Registros por mensaje (los mensajes del Data Layer admiten ~100 KB; 150 registros ≈ 25 KB). */
    private const val BATCH = 150

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Estado para mostrarlo en la app ("Sincronizado hace 5 min", "Sin conexión con el móvil"…). */
    fun status(ctx: Context) = ctx.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /**
     * Envía lo pendiente. [force] = mandar aunque no haya nada nuevo (para recibir rutinas y comprobar
     * la conexión). Sin nada pendiente y sin forzar, no hace nada: no se gasta radio.
     */
    fun sync(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        executor.execute {
            runCatching { pushNow(app, force) }.onFailure { setStatus(app, "error", it.message) }
        }
    }

    private fun pushNow(ctx: Context, force: Boolean) {
        val gym = GymDb(ctx)
        val padel = PadelStore(ctx)
        val health = io.xenxo.health.HealthDb(ctx)
        val gameDb = io.xenxo.game.GameDb(ctx)
        val sets = gym.unsyncedSets(BATCH)
        val weights = gym.unsyncedWeights(BATCH)
        val matches = padel.unsynced(BATCH)
        val healthDaily = health.unsyncedDaily(BATCH)
        val games = gameDb.unsyncedGames(BATCH)
        if (sets.isEmpty() && weights.isEmpty() && matches.isEmpty() && healthDaily.isEmpty() && games.isEmpty() && !force) return

        val payload = JSONObject().apply {
            put("v", 1)
            put("device", Build.MODEL)
            put("sentAt", System.currentTimeMillis())
            put("sets", JSONArray().apply {
                sets.forEach { (s, e) ->
                    put(JSONObject().apply {
                        put("uid", "s-${s.id}-${s.timestamp}")
                        put("ts", s.timestamp)
                        put("exercise", e.name)
                        put("group", e.group)
                        put("day", e.dayOfWeek)
                        put("setNum", s.setNum)
                        put("reps", s.reps)
                        put("kg", s.weightKg.toDouble())
                    })
                }
            })
            put("weights", JSONArray().apply {
                weights.forEach { w ->
                    put(JSONObject().put("uid", "w-${w.id}-${w.timestamp}").put("ts", w.timestamp).put("kg", w.weightKg.toDouble()))
                }
            })
            put("padel", JSONArray().apply {
                matches.forEach { r ->
                    put(JSONObject().apply {
                        put("uid", "p-${r.id}-${r.record.start}")
                        put("start", r.record.start)
                        put("end", r.record.end)
                        put("result", r.record.result)
                        put("winner", when (r.record.weWon) { true -> "us"; false -> "them"; null -> JSONObject.NULL })
                        put("goldenPoint", r.record.golden)
                        put("superTieBreak", r.superTieBreak)
                        put("points", r.points)
                    })
                }
            })
            put("health", JSONArray().apply {
                healthDaily.forEach { h ->
                    put(JSONObject().apply {
                        put("uid", "h-${h.date}")
                        put("date", h.date)
                        put("steps", h.steps)
                        put("goalSteps", h.goalSteps)
                        put("kcal", h.kcal)
                        put("km", h.km.toDouble())
                        put("avgBpm", h.avgBpm)
                        put("maxBpm", h.maxBpm)
                        put("lastBpm", h.lastBpm)
                        put("ts", h.lastUpdated)
                    })
                }
            })
            put("game", JSONArray().apply {
                games.forEach { g ->
                    put(JSONObject().apply {
                        put("uid", g.uid)
                        put("ts", g.timestamp)
                        put("score", g.score)
                        put("coins", g.coins)
                        put("distanceMeters", g.distanceMeters)
                        put("durationSec", g.durationSec)
                    })
                }
            })
        }

        val nodes = com.google.android.gms.tasks.Tasks.await(Wearable.getNodeClient(ctx).connectedNodes)
        val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        if (phone == null) { setStatus(ctx, "offline", null); return }
        com.google.android.gms.tasks.Tasks.await(
            Wearable.getMessageClient(ctx).sendMessage(phone.id, PATH_PUSH, payload.toString().toByteArray())
        )
        val n = sets.size + weights.size + matches.size + healthDaily.size + games.size
        Log.i(TAG, "push: $n registros a ${phone.displayName}")
        status(ctx).edit().putLong("lastPush", System.currentTimeMillis()).putInt("lastPushCount", n).apply()
    }

    /** Al abrir la app: lo pendiente siempre; y un aviso forzado si hace más de 15 min que el móvil no confirma. */
    fun syncOnOpen(ctx: Context) {
        val lastAck = status(ctx).getLong("lastAck", 0)
        sync(ctx, force = System.currentTimeMillis() - lastAck > 15 * 60_000)
    }

    /** Respuesta del móvil: marca como enviados los uid confirmados y procesa planes o perfil. Devuelve cuántos. */
    fun onAck(ctx: Context, json: String): Int {
        val o = JSONObject(json)
        val plan = o.optJSONObject("plan")
        if (plan != null) {
            Log.i(TAG, "recibido plan de entrenamiento o perfil en ack")
            GymDb(ctx).syncPlanFromPhone(plan)
            val prof = plan.optJSONObject("profile")
            if (prof != null) {
                io.xenxo.profile.UserProfile.updateFromPhone(ctx, prof)
            }
        }
        val acked = o.optJSONArray("acked") ?: JSONArray()
        val sets = mutableListOf<Long>(); val weights = mutableListOf<Long>(); val matches = mutableListOf<Long>()
        val gameIds = mutableListOf<Long>()
        val healthDates = mutableListOf<String>()
        for (i in 0 until acked.length()) {
            val uid = acked.getString(i)
            if (uid.startsWith("h-")) {
                healthDates += uid.removePrefix("h-")
                continue
            }
            val parts = uid.split('-')
            val id = parts.getOrNull(1)?.toLongOrNull() ?: continue
            when (parts[0]) {
                "s" -> sets += id
                "w" -> weights += id
                "p" -> matches += id
                "g" -> gameIds += id
            }
        }
        val gym = GymDb(ctx)
        gym.markSynced("set_logs", sets)
        gym.markSynced("body_weight_logs", weights)
        PadelStore(ctx).markSynced(matches)
        io.xenxo.health.HealthDb(ctx).markDailySynced(healthDates)
        io.xenxo.game.GameDb(ctx).markSynced(gameIds)
        val n = sets.size + weights.size + matches.size + healthDates.size + gameIds.size
        setStatus(ctx, "ok", null)
        status(ctx).edit().putLong("lastAck", System.currentTimeMillis()).putInt("lastAckCount", n).apply()
        Log.i(TAG, "ack: $n registros confirmados")
        // Si quedaba más de un lote, seguimos
        if (n >= BATCH) sync(ctx)
        return n
    }

    private fun setStatus(ctx: Context, state: String, detail: String?) {
        status(ctx).edit().putString("state", state).putString("detail", detail).putLong("stateAt", System.currentTimeMillis()).apply()
        Log.i(TAG, "estado: $state ${detail ?: ""}")
    }
}
