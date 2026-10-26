package io.xenxo.game

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.max

/**
 * Base de datos local SQLite para el videojuego "Ruta xenxo" (xenxoRun).
 * Almacena partidas, récords, monedas acumuladas, distancia y estado de sincronización.
 */
class GameDb(ctx: Context) : SQLiteOpenHelper(ctx, "xenxo_game.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE game_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid TEXT UNIQUE,
                timestamp INTEGER NOT NULL,
                score INTEGER NOT NULL,
                coins INTEGER NOT NULL,
                distance_meters INTEGER NOT NULL,
                duration_sec INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_game_synced ON game_runs(synced, timestamp)")
        db.execSQL("CREATE INDEX idx_game_score ON game_runs(score DESC)")

        db.execSQL(
            """
            CREATE TABLE achievements (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                icon TEXT NOT NULL,
                unlocked INTEGER NOT NULL DEFAULT 0,
                unlocked_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // Inicializar logros base
        initAchievements(db)
    }

    private fun initAchievements(db: SQLiteDatabase) {
        val baseAchievements = listOf(
            arrayOf("first_run", "Primeros Pasos", "Completa tu primera carrera en Ruta xenxo", "runner"),
            arrayOf("score_500", "En Carrera", "Consigue 500 puntos en una sola partida", "trophy"),
            arrayOf("score_1000", "Velocidad de Crucero", "Consigue 1000 puntos en una partida", "lightning"),
            arrayOf("coins_50", "Coleccionista", "Recoge 50 monedas en una carrera", "coin"),
            arrayOf("dist_1km", "Kilómetro Cero", "Alcanza 1 km de distancia acumulada", "map"),
            arrayOf("dist_5km", "Maratoniano", "Alcanza 5 km de distancia acumulada", "medal")
        )

        for (a in baseAchievements) {
            val cv = ContentValues().apply {
                put("id", a[0])
                put("title", a[1])
                put("description", a[2])
                put("icon", a[3])
                put("unlocked", 0)
                put("unlocked_at", 0L)
            }
            db.insertWithOnConflict("achievements", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun saveRun(score: Int, coins: Int, distanceMeters: Int, durationSec: Int): GameRecord {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("timestamp", now)
            put("score", score)
            put("coins", coins)
            put("distance_meters", distanceMeters)
            put("duration_sec", durationSec)
            put("synced", 0)
        }
        val id = db.insert("game_runs", null, cv)
        val uid = "g-$id-$now"
        val updateCv = ContentValues().apply { put("uid", uid) }
        db.update("game_runs", updateCv, "id = ?", arrayOf(id.toString()))

        val record = GameRecord(
            id = id,
            uid = uid,
            timestamp = now,
            score = score,
            coins = coins,
            distanceMeters = distanceMeters,
            durationSec = durationSec,
            synced = false
        )

        checkAndUnlockAchievements(record)
        return record
    }

    fun getStats(): GameStats {
        val db = readableDatabase
        var highScore = 0
        var totalCoins = 0
        var totalDistance = 0
        var gamesPlayed = 0

        db.rawQuery("SELECT MAX(score), SUM(coins), SUM(distance_meters), COUNT(*) FROM game_runs", null).use { c ->
            if (c.moveToFirst()) {
                highScore = if (!c.isNull(0)) c.getInt(0) else 0
                totalCoins = if (!c.isNull(1)) c.getInt(1) else 0
                totalDistance = if (!c.isNull(2)) c.getInt(2) else 0
                gamesPlayed = if (!c.isNull(3)) c.getInt(3) else 0
            }
        }

        // Nivel calculado: 1 nivel cada 500 m o 50 monedas
        val level = max(1, 1 + (totalDistance / 500) + (totalCoins / 50))

        return GameStats(
            highScore = highScore,
            totalCoins = totalCoins,
            totalDistanceMeters = totalDistance,
            gamesPlayed = gamesPlayed,
            level = level
        )
    }

    fun getRecentRuns(limit: Int = 10): List<GameRecord> {
        val list = mutableListOf<GameRecord>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, uid, timestamp, score, coins, distance_meters, duration_sec, synced FROM game_runs ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    GameRecord(
                        id = c.getLong(0),
                        uid = c.getString(1) ?: "g-${c.getLong(0)}-${c.getLong(2)}",
                        timestamp = c.getLong(2),
                        score = c.getInt(3),
                        coins = c.getInt(4),
                        distanceMeters = c.getInt(5),
                        durationSec = c.getInt(6),
                        synced = c.getInt(7) != 0
                    )
                )
            }
        }
        return list
    }

    fun unsyncedGames(limit: Int = 50): List<GameRecord> {
        val list = mutableListOf<GameRecord>()
        val db = readableDatabase
        db.rawQuery(
            "SELECT id, uid, timestamp, score, coins, distance_meters, duration_sec, synced FROM game_runs WHERE synced = 0 ORDER BY timestamp ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    GameRecord(
                        id = c.getLong(0),
                        uid = c.getString(1) ?: "g-${c.getLong(0)}-${c.getLong(2)}",
                        timestamp = c.getLong(2),
                        score = c.getInt(3),
                        coins = c.getInt(4),
                        distanceMeters = c.getInt(5),
                        durationSec = c.getInt(6),
                        synced = false
                    )
                )
            }
        }
        return list
    }

    fun markSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply { put("synced", 1) }
            for (id in ids) {
                db.update("game_runs", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAchievements(): List<Achievement> {
        val list = mutableListOf<Achievement>()
        readableDatabase.rawQuery(
            "SELECT id, title, description, icon, unlocked, unlocked_at FROM achievements ORDER BY id ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    Achievement(
                        id = c.getString(0),
                        title = c.getString(1),
                        description = c.getString(2),
                        icon = c.getString(3),
                        unlocked = c.getInt(4) == 1,
                        unlockedAt = c.getLong(5)
                    )
                )
            }
        }
        return list
    }

    private fun checkAndUnlockAchievements(lastRun: GameRecord) {
        val stats = getStats()
        val now = System.currentTimeMillis()

        if (stats.gamesPlayed >= 1) unlockAchievement("first_run", now)
        if (lastRun.score >= 500) unlockAchievement("score_500", now)
        if (lastRun.score >= 1000) unlockAchievement("score_1000", now)
        if (lastRun.coins >= 50) unlockAchievement("coins_50", now)
        if (stats.totalDistanceMeters >= 1000) unlockAchievement("dist_1km", now)
        if (stats.totalDistanceMeters >= 5000) unlockAchievement("dist_5km", now)
    }

    private fun unlockAchievement(id: String, timestamp: Long) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("unlocked", 1)
            put("unlocked_at", timestamp)
        }
        db.update("achievements", cv, "id = ? AND unlocked = 0", arrayOf(id))
    }
}
