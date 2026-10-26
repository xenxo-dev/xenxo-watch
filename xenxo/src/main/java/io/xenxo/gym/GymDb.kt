package io.xenxo.gym

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import io.xenxo.profile.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Base de datos SQLite para la sección :gym.
 * Almacena rutinas, series registradas, pesos y 1RM.
 */
class GymDb(private val ctx: Context) : SQLiteOpenHelper(ctx, "xenxo_gym.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day_of_week INTEGER NOT NULL,
                order_idx INTEGER NOT NULL,
                muscle_group TEXT NOT NULL,
                name TEXT NOT NULL,
                target_sets INTEGER NOT NULL,
                target_reps TEXT NOT NULL,
                default_weight REAL NOT NULL,
                rest_seconds INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE set_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                exercise_id INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                set_num INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                weight_kg REAL NOT NULL,
                epley_1rm REAL NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_set_ex ON set_logs(exercise_id, timestamp)")

        db.execSQL(
            """
            CREATE TABLE body_weight_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                weight_kg REAL NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routine_days (
                day_of_week INTEGER PRIMARY KEY,
                title TEXT NOT NULL
            )
            """.trimIndent()
        )

        seedExampleRoutine(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_set_synced ON set_logs(synced, timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_weight_synced ON body_weight_logs(synced, timestamp)")
        db.execSQL("CREATE TABLE IF NOT EXISTS routine_days (day_of_week INTEGER PRIMARY KEY, title TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE set_logs ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE body_weight_logs ADD COLUMN synced INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE exercises ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
            } catch (_: Exception) {}
            db.execSQL("CREATE TABLE IF NOT EXISTS routine_days (day_of_week INTEGER PRIMARY KEY, title TEXT NOT NULL)")
        }
    }

    // ---------- sincronización con el móvil ----------

    /** Series aún no confirmadas por el móvil, con el nombre y grupo del ejercicio. */
    fun unsyncedSets(limit: Int): List<Pair<SetLog, Exercise>> {
        val list = mutableListOf<Pair<SetLog, Exercise>>()
        readableDatabase.rawQuery(
            """
            SELECT s.id, s.exercise_id, s.timestamp, s.set_num, s.reps, s.weight_kg, s.epley_1rm,
                   e.day_of_week, e.order_idx, e.muscle_group, e.name, e.target_sets, e.target_reps, e.default_weight, e.rest_seconds
            FROM set_logs s JOIN exercises e ON e.id = s.exercise_id
            WHERE s.synced = 0 ORDER BY s.timestamp LIMIT ?
            """.trimIndent(), arrayOf(limit.toString())
        ).use {
            while (it.moveToNext()) {
                list += SetLog(it.getLong(0), it.getLong(1), it.getLong(2), it.getInt(3), it.getInt(4), it.getFloat(5), it.getFloat(6)) to
                    Exercise(it.getLong(1), it.getInt(7), it.getInt(8), it.getString(9), it.getString(10), it.getInt(11),
                        it.getString(12), it.getFloat(13), it.getInt(14))
            }
        }
        return list
    }

    fun unsyncedWeights(limit: Int): List<BodyWeightLog> {
        val list = mutableListOf<BodyWeightLog>()
        readableDatabase.rawQuery(
            "SELECT id, timestamp, weight_kg FROM body_weight_logs WHERE synced = 0 ORDER BY timestamp LIMIT ?",
            arrayOf(limit.toString())
        ).use { while (it.moveToNext()) list += BodyWeightLog(it.getLong(0), it.getLong(1), it.getFloat(2)) }
        return list
    }

    fun markSynced(table: String, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        require(table == "set_logs" || table == "body_weight_logs")
        writableDatabase.execSQL("UPDATE $table SET synced = 1 WHERE id IN (${ids.joinToString(",")})")
    }

    fun getExercisesForDay(dayOfWeek: Int): List<Exercise> {
        val list = mutableListOf<Exercise>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT id, day_of_week, order_idx, muscle_group, name, target_sets, target_reps, default_weight, rest_seconds 
            FROM exercises WHERE day_of_week = ? AND (active IS NULL OR active = 1) ORDER BY order_idx ASC
            """.trimIndent(),
            arrayOf(dayOfWeek.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    Exercise(
                        id = it.getLong(0),
                        dayOfWeek = it.getInt(1),
                        orderIdx = it.getInt(2),
                        group = it.getString(3),
                        name = it.getString(4),
                        targetSets = it.getInt(5),
                        targetReps = it.getString(6),
                        defaultWeightKg = it.getFloat(7),
                        restSeconds = it.getInt(8)
                    )
                )
            }
        }
        return list
    }

    fun hasExercises(): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM exercises WHERE (active IS NULL OR active = 1) LIMIT 1",
            null
        ).use { return it.moveToFirst() }
    }

    fun hasExercisesForDay(dayOfWeek: Int): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM exercises WHERE day_of_week = ? AND (active IS NULL OR active = 1) LIMIT 1",
            arrayOf(dayOfWeek.toString())
        ).use { return it.moveToFirst() }
    }

    fun getDayTitle(dayOfWeek: Int): String? {
        readableDatabase.rawQuery(
            "SELECT title FROM routine_days WHERE day_of_week = ?",
            arrayOf(dayOfWeek.toString())
        ).use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    fun setDayTitle(dayOfWeek: Int, title: String) {
        val cv = ContentValues().apply {
            put("day_of_week", dayOfWeek)
            put("title", title)
        }
        writableDatabase.insertWithOnConflict("routine_days", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getTodaySetsForExercise(exerciseId: Long): List<SetLog> {
        val list = mutableListOf<SetLog>()
        val startOfDay = getStartOfDayMillis()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT id, exercise_id, timestamp, set_num, reps, weight_kg, epley_1rm 
            FROM set_logs 
            WHERE exercise_id = ? AND timestamp >= ? 
            ORDER BY set_num ASC
            """.trimIndent(),
            arrayOf(exerciseId.toString(), startOfDay.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SetLog(
                        id = it.getLong(0),
                        exerciseId = it.getLong(1),
                        timestamp = it.getLong(2),
                        setNum = it.getInt(3),
                        reps = it.getInt(4),
                        weightKg = it.getFloat(5),
                        epley1rm = it.getFloat(6)
                    )
                )
            }
        }
        return list
    }

    fun logSet(exerciseId: Long, setNum: Int, reps: Int, weightKg: Float): SetLog {
        val epley = calculateEpley1RM(weightKg, reps)
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("exercise_id", exerciseId)
            put("timestamp", now)
            put("set_num", setNum)
            put("reps", reps)
            put("weight_kg", weightKg)
            put("epley_1rm", epley)
        }
        val id = writableDatabase.insert("set_logs", null, cv)
        return SetLog(id, exerciseId, now, setNum, reps, weightKg, epley)
    }

    fun getLastSetForExercise(exerciseId: Long): SetLog? {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT id, exercise_id, timestamp, set_num, reps, weight_kg, epley_1rm 
            FROM set_logs 
            WHERE exercise_id = ? 
            ORDER BY timestamp DESC LIMIT 1
            """.trimIndent(),
            arrayOf(exerciseId.toString())
        )
        return cursor.use {
            if (it.moveToNext()) {
                SetLog(
                    id = it.getLong(0),
                    exerciseId = it.getLong(1),
                    timestamp = it.getLong(2),
                    setNum = it.getInt(3),
                    reps = it.getInt(4),
                    weightKg = it.getFloat(5),
                    epley1rm = it.getFloat(6)
                )
            } else null
        }
    }

    fun deleteLastSetForExercise(exerciseId: Long): Boolean {
        val last = getLastSetForExercise(exerciseId) ?: return false
        val deleted = writableDatabase.delete("set_logs", "id = ?", arrayOf(last.id.toString()))
        return deleted > 0
    }

    fun updateExerciseRest(exerciseId: Long, restSeconds: Int) {
        val cv = ContentValues().apply { put("rest_seconds", restSeconds) }
        writableDatabase.update("exercises", cv, "id = ?", arrayOf(exerciseId.toString()))
    }

    fun updateAllExercisesRest(restSeconds: Int) {
        val cv = ContentValues().apply { put("rest_seconds", restSeconds) }
        writableDatabase.update("exercises", cv, null, null)
    }

    fun getPRForExercise(exerciseId: Long): Float {
        val cursor = readableDatabase.rawQuery(
            "SELECT MAX(epley_1rm) FROM set_logs WHERE exercise_id = ?",
            arrayOf(exerciseId.toString())
        )
        return cursor.use { if (it.moveToNext()) it.getFloat(0) else 0f }
    }

    fun getBest1RM(exerciseId: Long): Float = getPRForExercise(exerciseId)

    fun getAllPRs(): Map<String, Float> {
        val map = mutableMapOf<String, Float>()
        val cursor = readableDatabase.rawQuery(
            """
            SELECT e.name, MAX(s.epley_1rm) 
            FROM set_logs s 
            JOIN exercises e ON s.exercise_id = e.id 
            GROUP BY e.name
            """.trimIndent(),
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val pr = it.getFloat(1)
                map[name] = pr
            }
        }
        return map
    }

    fun getLastWorkoutSession(): WorkoutSessionSnapshot? {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT s.timestamp, COUNT(*), COUNT(DISTINCT s.exercise_id), e.name, s.reps, s.weight_kg
            FROM set_logs s 
            JOIN exercises e ON s.exercise_id = e.id 
            GROUP BY strftime('%Y-%m-%d', datetime(s.timestamp/1000, 'unixepoch', 'localtime')) 
            ORDER BY s.timestamp DESC LIMIT 1
            """.trimIndent(),
            null
        )
        return cursor.use {
            if (it.moveToNext()) {
                WorkoutSessionSnapshot(
                    dateTs = it.getLong(0),
                    totalSets = it.getInt(1),
                    exercisesCount = it.getInt(2),
                    topExercise = it.getString(3),
                    topReps = it.getInt(4),
                    topWeightKg = it.getFloat(5)
                )
            } else null
        }
    }

    fun logBodyWeight(weightKg: Float) {
        val cv = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("weight_kg", weightKg)
        }
        writableDatabase.insert("body_weight_logs", null, cv)
    }

    fun getLastBodyWeight(): Float {
        val cursor = readableDatabase.rawQuery(
            "SELECT weight_kg FROM body_weight_logs ORDER BY timestamp DESC LIMIT 1",
            null
        )
        return cursor.use { if (it.moveToNext()) it.getFloat(0) else UserProfile.get(ctx).weightKg }
    }

    fun getWeightHistory(limit: Int = 7): List<Pair<Long, Float>> {
        val list = mutableListOf<Pair<Long, Float>>()
        val cursor = readableDatabase.rawQuery(
            "SELECT timestamp, weight_kg FROM body_weight_logs ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(Pair(it.getLong(0), it.getFloat(1)))
            }
        }
        return list
    }

    private fun getStartOfDayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Sincroniza el plan recibido desde xenxoHub (móvil).
     * Preserva las filas de ejercicios que ya tienen series (no rompe exercise_id en set_logs).
     */
    fun syncPlanFromPhone(planJson: JSONObject) {
        if (planJson.has("profile")) {
            val prof = planJson.optJSONObject("profile")
            if (prof != null) {
                UserProfile.updateFromPhone(ctx, prof)
            }
        }

        val daysArr = planJson.optJSONArray("days") ?: return
        if (daysArr.length() == 0) return

        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until daysArr.length()) {
                val dObj = daysArr.getJSONObject(i)
                val dayOfWeek = dObj.getInt("dayOfWeek")
                val title = dObj.optString("title", "")
                if (title.isNotEmpty()) {
                    setDayTitle(dayOfWeek, title)
                }

                // Desactivamos los ejercicios actuales de ese día
                db.execSQL("UPDATE exercises SET active = 0 WHERE day_of_week = ?", arrayOf(dayOfWeek.toString()))

                val exArr = dObj.optJSONArray("exercises") ?: JSONArray()
                for (j in 0 until exArr.length()) {
                    val eObj = exArr.getJSONObject(j)
                    val name = eObj.getString("name")
                    val group = eObj.optString("group", "Fuerza")
                    val sets = eObj.optInt("sets", 3)
                    val reps = eObj.optString("reps", "10")
                    val weight = eObj.optDouble("weight", 0.0).toFloat()
                    val rest = eObj.optInt("rest", 90)
                    val order = eObj.optInt("order", j + 1)

                    var existingId: Long = -1
                    db.rawQuery(
                        "SELECT id FROM exercises WHERE day_of_week = ? AND name = ? LIMIT 1",
                        arrayOf(dayOfWeek.toString(), name)
                    ).use { c ->
                        if (c.moveToFirst()) existingId = c.getLong(0)
                    }

                    if (existingId > 0) {
                        val cv = ContentValues().apply {
                            put("order_idx", order)
                            put("muscle_group", group)
                            put("target_sets", sets)
                            put("target_reps", reps)
                            put("default_weight", weight)
                            put("rest_seconds", rest)
                            put("active", 1)
                        }
                        db.update("exercises", cv, "id = ?", arrayOf(existingId.toString()))
                    } else {
                        val cv = ContentValues().apply {
                            put("day_of_week", dayOfWeek)
                            put("order_idx", order)
                            put("muscle_group", group)
                            put("name", name)
                            put("target_sets", sets)
                            put("target_reps", reps)
                            put("default_weight", weight)
                            put("rest_seconds", rest)
                            put("active", 1)
                        }
                        db.insert("exercises", null, cv)
                    }
                }
            }
            db.setTransactionSuccessful()
            Log.i("xenxo-gym", "plan sincronizado desde xenxoHub con éxito")
        } catch (e: Exception) {
            Log.w("xenxo-gym", "error al sincronizar plan desde el móvil", e)
        } finally {
            db.endTransaction()
        }
        ctx.sendBroadcast(Intent("io.xenxo.PLAN_UPDATED"))
    }

    /** Desactiva todas las rutinas sin borrar el historial de series. */
    fun clearAllExercises() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE exercises SET active = 0")
            db.execSQL("DELETE FROM routine_days")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        ctx.sendBroadcast(Intent("io.xenxo.PLAN_UPDATED"))
    }

    /**
     * Carga la rutina de 4 días de ejemplo (Pecho/Tríceps, Espalda/Bíceps, Hombro/Brazos, Pierna/Abdomen).
     * Se usa como plantilla inicial para cualquier usuario.
     */
    fun seedExampleRoutine(targetDb: SQLiteDatabase? = null) {
        val db = targetDb ?: writableDatabase
        val needTransaction = !db.inTransaction()
        if (needTransaction) db.beginTransaction()
        try {
            // Títulos de los días
            val titles = mapOf(
                Calendar.MONDAY to "Pecho y Tríceps",
                Calendar.TUESDAY to "Espalda y Bíceps",
                Calendar.THURSDAY to "Hombro y Brazos",
                Calendar.FRIDAY to "Pierna y Abdomen"
            )
            for ((d, t) in titles) {
                val cv = ContentValues().apply {
                    put("day_of_week", d)
                    put("title", t)
                }
                db.insertWithOnConflict("routine_days", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            // Lunes: Pecho + Tríceps + Abdomen (Calendar.MONDAY = 2)
            insertEx(db, Calendar.MONDAY, 1, "Pecho", "Press banca / máquina", 3, "6-10", 60.0f, 120)
            insertEx(db, Calendar.MONDAY, 2, "Pecho", "Press inclinado manc.", 3, "8-12", 40.0f, 90)
            insertEx(db, Calendar.MONDAY, 3, "Pecho", "Aperturas en polea", 3, "10-15", 25.0f, 60)
            insertEx(db, Calendar.MONDAY, 4, "Pecho", "Fondos / press declinado", 2, "8-12", 0.0f, 90)
            insertEx(db, Calendar.MONDAY, 5, "Tríceps", "Extensión polea", 3, "10-15", 20.0f, 60)
            insertEx(db, Calendar.MONDAY, 6, "Tríceps", "Extensión sobre cabeza", 2, "10-15", 15.0f, 60)
            insertEx(db, Calendar.MONDAY, 7, "Abdomen", "Crunch polea", 3, "8-15", 40.0f, 60)
            insertEx(db, Calendar.MONDAY, 8, "Abdomen", "Elevaciones piernas", 3, "8-15", 0.0f, 60)

            // Martes: Espalda + Bíceps (Calendar.TUESDAY = 3)
            insertEx(db, Calendar.TUESDAY, 1, "Espalda", "Dominadas", 3, "6-10", 0.0f, 120)
            insertEx(db, Calendar.TUESDAY, 2, "Espalda", "Remo pecho apoyado", 3, "8-12", 50.0f, 90)
            insertEx(db, Calendar.TUESDAY, 3, "Espalda", "Jalón agarre neutro", 2, "10-15", 50.0f, 90)
            insertEx(db, Calendar.TUESDAY, 4, "Espalda", "Remo unilateral polea", 3, "8-12", 25.0f, 60)
            insertEx(db, Calendar.TUESDAY, 5, "Espalda", "Face pulls / reverse fly", 2, "12-20", 15.0f, 60)
            insertEx(db, Calendar.TUESDAY, 6, "Bíceps", "Curl barra", 3, "8-12", 30.0f, 90)
            insertEx(db, Calendar.TUESDAY, 7, "Bíceps", "Curl inclinado", 2, "10-15", 12.0f, 60)
            insertEx(db, Calendar.TUESDAY, 8, "Bíceps", "Curl martillo", 2, "10-15", 10.0f, 60)

            // Jueves: Hombros + Brazos + Abdomen (Calendar.THURSDAY = 5)
            insertEx(db, Calendar.THURSDAY, 1, "Hombros", "Press militar / máquina", 3, "6-10", 35.0f, 90)
            insertEx(db, Calendar.THURSDAY, 2, "Hombros", "Elevaciones laterales", 4, "10-20", 10.0f, 60)
            insertEx(db, Calendar.THURSDAY, 3, "Hombros", "Reverse pec deck", 3, "12-20", 40.0f, 60)
            insertEx(db, Calendar.THURSDAY, 4, "Hombros", "Elevaciones lat. polea", 2, "12-20", 10.0f, 60)
            insertEx(db, Calendar.THURSDAY, 5, "Brazos", "Curl bíceps polea", 2, "10-15", 20.0f, 60)
            insertEx(db, Calendar.THURSDAY, 6, "Brazos", "Extensión tríceps polea", 2, "10-15", 20.0f, 60)
            insertEx(db, Calendar.THURSDAY, 7, "Pierna", "Curl femoral", 2, "10-15", 30.0f, 60)
            insertEx(db, Calendar.THURSDAY, 8, "Pierna", "Extensión cuádriceps", 2, "10-15", 35.0f, 60)
            insertEx(db, Calendar.THURSDAY, 9, "Abdomen", "Crunch polea", 3, "8-15", 40.0f, 60)
            insertEx(db, Calendar.THURSDAY, 10, "Abdomen", "Elevaciones piernas", 3, "8-15", 0.0f, 60)

            // Viernes: Pierna + Abdomen (Calendar.FRIDAY = 6)
            insertEx(db, Calendar.FRIDAY, 1, "Pierna", "Hack squat / sentadilla", 3, "6-10", 50.0f, 120)
            insertEx(db, Calendar.FRIDAY, 2, "Pierna", "Prensa", 3, "8-12", 70.0f, 120)
            insertEx(db, Calendar.FRIDAY, 3, "Pierna", "Peso muerto rumano", 3, "6-10", 50.0f, 120)
            insertEx(db, Calendar.FRIDAY, 4, "Pierna", "Curl femoral", 3, "10-15", 30.0f, 60)
            insertEx(db, Calendar.FRIDAY, 5, "Pierna", "Extensión cuádriceps", 2, "10-15", 35.0f, 60)
            insertEx(db, Calendar.FRIDAY, 6, "Pierna", "Abductores/aductores", 2, "10-15", 35.0f, 60)
            insertEx(db, Calendar.FRIDAY, 7, "Abdomen", "Crunch polea", 3, "8-15", 40.0f, 60)

            if (needTransaction) db.setTransactionSuccessful()
        } finally {
            if (needTransaction) db.endTransaction()
        }
        ctx.sendBroadcast(Intent("io.xenxo.PLAN_UPDATED"))
    }

    private fun insertEx(db: SQLiteDatabase, day: Int, order: Int, grp: String, name: String, sets: Int, reps: String, wt: Float, rest: Int) {
        val cv = ContentValues().apply {
            put("day_of_week", day)
            put("order_idx", order)
            put("muscle_group", grp)
            put("name", name)
            put("target_sets", sets)
            put("target_reps", reps)
            put("default_weight", wt)
            put("rest_seconds", rest)
            put("active", 1)
        }
        db.insert("exercises", null, cv)
    }

    companion object {
        /** Epley formula: 1RM = weight * (1 + reps / 30) */
        fun calculateEpley1RM(weightKg: Float, reps: Int): Float {
            if (reps <= 1) return weightKg
            return weightKg * (1f + reps / 30f)
        }
    }
}
