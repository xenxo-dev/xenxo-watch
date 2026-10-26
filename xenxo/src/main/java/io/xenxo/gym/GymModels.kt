package io.xenxo.gym

import java.util.Locale

/** Peso para mostrar: "75", "12,5" (coma española; sin ".0" ni redondeos que pierdan el medio kilo). */
fun kg(w: Float): String =
    if (w % 1f == 0f) w.toInt().toString() else String.format(Locale("es", "ES"), "%.1f", w)

data class Exercise(
    val id: Long,
    val dayOfWeek: Int, // Calendar.MONDAY (2), TUESDAY (3), etc.
    val orderIdx: Int,
    val group: String,
    val name: String,
    val targetSets: Int,
    val targetReps: String,
    val defaultWeightKg: Float,
    val restSeconds: Int = 90
)

data class SetLog(
    val id: Long = 0,
    val exerciseId: Long,
    val timestamp: Long,
    val setNum: Int,
    val reps: Int,
    val weightKg: Float,
    val epley1rm: Float
)

data class BodyWeightLog(
    val id: Long = 0,
    val timestamp: Long,
    val weightKg: Float
)

data class WorkoutSessionSnapshot(
    val dateTs: Long,
    val totalSets: Int,
    val exercisesCount: Int,
    val topExercise: String,
    val topReps: Int,
    val topWeightKg: Float
)
