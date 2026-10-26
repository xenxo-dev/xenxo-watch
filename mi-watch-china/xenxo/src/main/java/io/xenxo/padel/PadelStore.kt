package io.xenxo.padel

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.xenxo.padel.PadelMatch.Config
import io.xenxo.padel.PadelMatch.Team
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un partido terminado del historial. */
data class PadelRecord(val start: Long, val end: Long, val result: String, val weWon: Boolean?, val golden: Boolean)

/**
 * Persistencia del pádel:
 *  - Partido en curso en SharedPreferences (config + lista de puntos "UTTU…"), guardado tras cada punto:
 *    si Android cierra la app por falta de RAM, el marcador vuelve exactamente igual.
 *  - Historial de partidos terminados en SQLite (xenxo_padel.db) y exportación a CSV.
 */
class PadelStore(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "xenxo_padel.db", null, 2) {

    private val prefs = ctx.getSharedPreferences("padel", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE matches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_ts INTEGER NOT NULL,
                end_ts INTEGER NOT NULL,
                result TEXT NOT NULL,
                winner TEXT,
                golden_point INTEGER NOT NULL,
                super_tiebreak INTEGER NOT NULL,
                points TEXT NOT NULL
            )
            """.trimIndent()
        )
        onUpgrade(db, 1, 2)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matches_synced ON matches(synced, start_ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v2: marca de "ya enviado al móvil" (io.xenxo.sync)
        if (oldVersion < 2) db.execSQL("ALTER TABLE matches ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
    }

    /** Partido del historial con su id de fila, para enviarlo al móvil. */
    data class Row(val id: Long, val record: PadelRecord, val superTieBreak: Boolean, val points: String)

    fun unsynced(limit: Int): List<Row> {
        val list = mutableListOf<Row>()
        readableDatabase.rawQuery(
            "SELECT id, start_ts, end_ts, result, winner, golden_point, super_tiebreak, points FROM matches WHERE synced = 0 ORDER BY start_ts LIMIT ?",
            arrayOf(limit.toString())
        ).use {
            while (it.moveToNext()) {
                val w = it.getString(4)
                list += Row(it.getLong(0),
                    PadelRecord(it.getLong(1), it.getLong(2), it.getString(3), if (w == null) null else w == "US", it.getInt(5) == 1),
                    it.getInt(6) == 1, it.getString(7))
            }
        }
        return list
    }

    fun markSynced(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.execSQL("UPDATE matches SET synced = 1 WHERE id IN (${ids.joinToString(",")})")
    }

    // ---------- partido en curso ----------

    val hasCurrent get() = prefs.contains("start")

    fun loadCurrent(): PadelMatch? {
        if (!hasCurrent) return null
        val cfg = Config(
            goldenPoint = prefs.getBoolean("golden", false),
            superTieBreak = prefs.getBoolean("stb", false),
            firstServer = if (prefs.getString("first", "U") == "U") Team.US else Team.THEM,
        )
        val pts = prefs.getString("points", "")!!.map { if (it == 'U') Team.US else Team.THEM }
        return PadelMatch(cfg, pts)
    }

    val currentStart get() = prefs.getLong("start", System.currentTimeMillis())

    fun startNew(cfg: Config) {
        prefs.edit()
            .putLong("start", System.currentTimeMillis())
            .putBoolean("golden", cfg.goldenPoint).putBoolean("stb", cfg.superTieBreak)
            .putString("first", if (cfg.firstServer == Team.US) "U" else "T")
            .putString("points", "")
            .apply()
    }

    fun saveCurrent(m: PadelMatch) {
        prefs.edit().putString("points", encode(m.points)).apply()
    }

    fun clearCurrent() {
        prefs.edit().clear().apply()
    }

    /** Guarda el partido en el historial (terminado o abandonado) y borra el partido en curso. */
    fun finish(m: PadelMatch) {
        if (m.points.isNotEmpty()) {
            writableDatabase.insert("matches", null, ContentValues().apply {
                put("start_ts", currentStart)
                put("end_ts", System.currentTimeMillis())
                put("result", m.resultText(withCurrent = true))
                put("winner", m.winner?.let { if (it == Team.US) "US" else "THEM" })
                put("golden_point", if (m.config.goldenPoint) 1 else 0)
                put("super_tiebreak", if (m.config.superTieBreak) 1 else 0)
                put("points", encode(m.points))
            })
            io.xenxo.sync.XenxoSync.sync(ctx)
        }
        clearCurrent()
    }

    // ---------- historial ----------

    fun history(limit: Int = 20): List<PadelRecord> {
        val list = mutableListOf<PadelRecord>()
        readableDatabase.rawQuery(
            "SELECT start_ts, end_ts, result, winner, golden_point FROM matches ORDER BY start_ts DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use {
            while (it.moveToNext()) {
                val w = it.getString(3)
                list += PadelRecord(it.getLong(0), it.getLong(1), it.getString(2),
                    if (w == null) null else w == "US", it.getInt(4) == 1)
            }
        }
        return list
    }

    /** Borra un partido del historial (se identifica por su hora de inicio). */
    fun delete(start: Long) {
        writableDatabase.delete("matches", "start_ts = ?", arrayOf(start.toString()))
    }

    /** CSV en el almacenamiento de la app: adb pull /sdcard/Android/data/io.xenxo/files/xenxo-padel.csv */
    fun exportCsv(): File? = runCatching {
        val file = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "xenxo-padel.csv")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es", "ES"))
        file.bufferedWriter().use { w ->
            w.write("fecha;duracion_min;resultado;ganador;punto_de_oro\n")
            history(Int.MAX_VALUE).forEach { r ->
                val ganador = when (r.weWon) { true -> "nosotros"; false -> "ellos"; null -> "sin terminar" }
                w.write("${fmt.format(Date(r.start))};${(r.end - r.start) / 60_000};${r.result};$ganador;${if (r.golden) "si" else "no"}\n")
            }
        }
        file
    }.getOrNull()

    private fun encode(points: List<Team>) = points.joinToString("") { if (it == Team.US) "U" else "T" }
}
