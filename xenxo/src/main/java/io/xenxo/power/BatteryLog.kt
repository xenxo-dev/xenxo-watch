package io.xenxo.power

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Registro local pasivo de batería y estimación de autonomía.
 * Almacena en SQLite nativo sin dependencias ni wakelocks.
 */
class BatteryLog(private val ctx: Context) : SQLiteOpenHelper(ctx, "xenxo_battery.db", null, 1) {

    data class Point(val time: Long, val level: Int, val temp: Float, val charging: Boolean)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS battery_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                level INTEGER NOT NULL,
                temp REAL NOT NULL,
                charging INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_time ON battery_log(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun record(level: Int, tempCelsius: Float, isCharging: Boolean) {
        val cv = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("level", level)
            put("temp", tempCelsius)
            put("charging", if (isCharging) 1 else 0)
        }
        writableDatabase.insert("battery_log", null, cv)

        // Limpiar registros antiguos (> 7 días) para no ocupar almacenamiento
        val limit = System.currentTimeMillis() - 7 * 86400 * 1000L
        writableDatabase.delete("battery_log", "timestamp < ?", arrayOf(limit.toString()))
    }

    /**
     * Calcula la tasa media de descarga en %/h durante las últimas horas sin estar cargando.
     * Devuelve null si no hay suficientes datos o está cargando.
     */
    fun calculateDrainRatePerHour(hoursWindow: Int = 3): Float? {
        val since = System.currentTimeMillis() - hoursWindow * 3600 * 1000L
        val cursor = readableDatabase.rawQuery(
            """
            SELECT timestamp, level, charging FROM battery_log 
            WHERE timestamp >= ? ORDER BY timestamp ASC
            """.trimIndent(),
            arrayOf(since.toString())
        )

        val points = mutableListOf<Point>()
        cursor.use {
            while (it.moveToNext()) {
                val t = it.getLong(0)
                val lvl = it.getInt(1)
                val chg = it.getInt(2) == 1
                if (!chg) points.add(Point(t, lvl, 0f, false))
            }
        }

        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        val deltaHours = (last.time - first.time) / (1000.0 * 3600.0)
        if (deltaHours < 0.25) return null // Menos de 15 minutos no es representativo

        val deltaLevel = first.level - last.level
        if (deltaLevel <= 0) return 0f // No ha bajado o ha subido

        return (deltaLevel / deltaHours).toFloat()
    }

    /**
     * Estima la hora de fin de batería ("HH:mm") a partir de la tasa de descarga actual.
     */
    fun estimateEndTime(currentLevel: Int, drainRate: Float): String? {
        if (drainRate <= 0.1f || currentLevel <= 0) return null
        val hoursRemaining = currentLevel / drainRate
        val endCal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, (hoursRemaining * 60).toInt())
        }
        return String.format(Locale("es", "ES"), "%02d:%02d",
            endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE))
    }

    /**
     * Exporta el historial de batería a un archivo CSV en el almacenamiento externo/privado.
     */
    fun exportCsv(): File? {
        return runCatching {
            val file = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "xenxo-bateria.csv")
            val cursor = readableDatabase.rawQuery(
                "SELECT timestamp, level, temp, charging FROM battery_log ORDER BY timestamp ASC",
                null
            )
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            file.bufferedWriter().use { out ->
                out.write("Fecha,Nivel_Pct,Temp_C,Cargando\n")
                cursor.use {
                    while (it.moveToNext()) {
                        val dt = sdf.format(Date(it.getLong(0)))
                        val lvl = it.getInt(1)
                        val tmp = it.getFloat(2)
                        val chg = it.getInt(3)
                        out.write("$dt,$lvl,$tmp,$chg\n")
                    }
                }
            }
            file
        }.getOrNull()
    }
}
