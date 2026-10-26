package io.xenxo.health

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DailyHealth(
    val date: String,
    val steps: Int,
    val goalSteps: Int,
    val kcal: Int,
    val km: Float,
    val avgBpm: Int,
    val maxBpm: Int,
    val lastBpm: Int,
    val lastUpdated: Long,
    val synced: Boolean
)

data class HeartRateLog(
    val id: Long,
    val timestamp: Long,
    val bpm: Int,
    val synced: Boolean
)

class HealthDb(ctx: Context) : SQLiteOpenHelper(ctx, "xenxo_health.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE daily_health (
                date TEXT PRIMARY KEY,
                steps INTEGER NOT NULL DEFAULT 0,
                goal_steps INTEGER NOT NULL DEFAULT 10000,
                kcal INTEGER NOT NULL DEFAULT 0,
                km REAL NOT NULL DEFAULT 0.0,
                avg_bpm INTEGER NOT NULL DEFAULT 0,
                max_bpm INTEGER NOT NULL DEFAULT 0,
                last_bpm INTEGER NOT NULL DEFAULT 0,
                last_updated INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE heart_rate_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                bpm INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_hr_time ON heart_rate_logs(timestamp)")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dh_synced ON daily_health(synced, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hr_synced ON heart_rate_logs(synced, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun getDaily(date: String): DailyHealth? {
        readableDatabase.rawQuery(
            "SELECT date, steps, goal_steps, kcal, km, avg_bpm, max_bpm, last_bpm, last_updated, synced FROM daily_health WHERE date = ?",
            arrayOf(date)
        ).use { c ->
            if (c.moveToFirst()) {
                return DailyHealth(
                    date = c.getString(0),
                    steps = c.getInt(1),
                    goalSteps = c.getInt(2),
                    kcal = c.getInt(3),
                    km = c.getFloat(4),
                    avgBpm = c.getInt(5),
                    maxBpm = c.getInt(6),
                    lastBpm = c.getInt(7),
                    lastUpdated = c.getLong(8),
                    synced = c.getInt(9) == 1
                )
            }
        }
        return null
    }

    /**
     * Devuelve la última medición de pulso registrada en el historial (de hoy o días previos),
     * útil para que la esfera nunca muestre "--" si ya se tomó una lectura.
     */
    fun getLatestBpm(): Int {
        return try {
            readableDatabase.rawQuery(
                "SELECT bpm FROM heart_rate_logs WHERE bpm > 30 ORDER BY timestamp DESC LIMIT 1",
                null
            ).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun upsertSteps(date: String, steps: Int, kcal: Int, km: Float, goalSteps: Int = 10000) {
        val now = System.currentTimeMillis()
        val existing = getDaily(date)
        val cv = ContentValues().apply {
            put("date", date)
            put("steps", steps)
            put("kcal", kcal)
            put("km", km)
            put("last_updated", now)
            put("synced", 0)
            put("goal_steps", goalSteps)
            if (existing != null) {
                put("avg_bpm", existing.avgBpm)
                put("max_bpm", existing.maxBpm)
                put("last_bpm", existing.lastBpm)
            }
        }
        writableDatabase.insertWithOnConflict("daily_health", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun recordHeartRate(bpm: Int, timestamp: Long = System.currentTimeMillis(), date: String) {
        if (bpm <= 30 || bpm > 240) return
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("timestamp", timestamp)
                put("bpm", bpm)
                put("synced", 0)
            }
            writableDatabase.insert("heart_rate_logs", null, cv)

            val existing = getDaily(date)
            if (existing == null) {
                val cvDaily = ContentValues().apply {
                    put("date", date)
                    put("steps", 0)
                    put("goal_steps", 10000)
                    put("kcal", 0)
                    put("km", 0f)
                    put("avg_bpm", bpm)
                    put("max_bpm", bpm)
                    put("last_bpm", bpm)
                    put("last_updated", timestamp)
                    put("synced", 0)
                }
                writableDatabase.insert("daily_health", null, cvDaily)
            } else {
                val newMax = maxOf(existing.maxBpm, bpm)
                val newAvg = if (existing.avgBpm > 0) (existing.avgBpm + bpm) / 2 else bpm
                writableDatabase.execSQL(
                    """
                    UPDATE daily_health SET
                        avg_bpm = ?,
                        max_bpm = ?,
                        last_bpm = ?,
                        last_updated = ?,
                        synced = 0
                    WHERE date = ?
                    """.trimIndent(),
                    arrayOf(newAvg, newMax, bpm, timestamp, date)
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getLast7Days(): List<DailyHealth> {
        val list = mutableListOf<DailyHealth>()
        readableDatabase.rawQuery(
            "SELECT date, steps, goal_steps, kcal, km, avg_bpm, max_bpm, last_bpm, last_updated, synced FROM daily_health ORDER BY date DESC LIMIT 7",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    DailyHealth(
                        date = c.getString(0),
                        steps = c.getInt(1),
                        goalSteps = c.getInt(2),
                        kcal = c.getInt(3),
                        km = c.getFloat(4),
                        avgBpm = c.getInt(5),
                        maxBpm = c.getInt(6),
                        lastBpm = c.getInt(7),
                        lastUpdated = c.getLong(8),
                        synced = c.getInt(9) == 1
                    )
                )
            }
        }
        return list.reversed()
    }

    fun unsyncedDaily(limit: Int = 30): List<DailyHealth> {
        val list = mutableListOf<DailyHealth>()
        readableDatabase.rawQuery(
            "SELECT date, steps, goal_steps, kcal, km, avg_bpm, max_bpm, last_bpm, last_updated, synced FROM daily_health WHERE synced = 0 ORDER BY date DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    DailyHealth(
                        date = c.getString(0),
                        steps = c.getInt(1),
                        goalSteps = c.getInt(2),
                        kcal = c.getInt(3),
                        km = c.getFloat(4),
                        avgBpm = c.getInt(5),
                        maxBpm = c.getInt(6),
                        lastBpm = c.getInt(7),
                        lastUpdated = c.getLong(8),
                        synced = false
                    )
                )
            }
        }
        return list
    }

    fun markDailySynced(dates: Collection<String>) {
        if (dates.isEmpty()) return
        val placeholders = dates.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "UPDATE daily_health SET synced = 1 WHERE date IN ($placeholders)",
            dates.toTypedArray()
        )
    }
}
