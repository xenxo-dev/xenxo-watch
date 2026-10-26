package io.xenxo.profile

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * Perfil de usuario para la app del reloj (:xenxo).
 * Centraliza altura, peso, objetivo de pasos y unidades.
 * Se sincroniza bidireccionalmente con xenxoHub (móvil) o se edita en el reloj.
 */
data class UserProfile(
    val heightCm: Float = 175f,
    val weightKg: Float = 70.0f,
    val stepGoal: Int = 10_000,
    val unit: String = "kg",
    val sex: String = "m"
) {
    companion object {
        private const val TAG = "xenxo-profile"
        private const val PREFS_NAME = "xenxo_user_profile"
        private const val KEY_HEIGHT = "height_cm"
        private const val KEY_WEIGHT = "weight_kg"
        private const val KEY_STEP_GOAL = "step_goal"
        private const val KEY_UNIT = "unit"
        private const val KEY_SEX = "sex"

        fun get(ctx: Context): UserProfile {
            val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return UserProfile(
                heightCm = p.getFloat(KEY_HEIGHT, 175f),
                weightKg = p.getFloat(KEY_WEIGHT, 70.0f),
                stepGoal = p.getInt(KEY_STEP_GOAL, 10_000),
                unit = p.getString(KEY_UNIT, "kg") ?: "kg",
                sex = p.getString(KEY_SEX, "m") ?: "m"
            )
        }

        fun save(ctx: Context, profile: UserProfile) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_HEIGHT, profile.heightCm.coerceIn(100f, 250f))
                .putFloat(KEY_WEIGHT, profile.weightKg.coerceIn(30f, 250f))
                .putInt(KEY_STEP_GOAL, profile.stepGoal.coerceIn(1_000, 50_000))
                .putString(KEY_UNIT, if (profile.unit == "lb") "lb" else "kg")
                .putString(KEY_SEX, profile.sex)
                .apply()
            Log.i(TAG, "perfil guardado: $profile")
            ctx.sendBroadcast(Intent("io.xenxo.PROFILE_UPDATED"))
        }

        fun updateFromPhone(ctx: Context, json: JSONObject): Boolean {
            return try {
                val current = get(ctx)
                val height = if (json.has("heightCm")) json.optDouble("heightCm", current.heightCm.toDouble()).toFloat() else current.heightCm
                val weight = if (json.has("weightKg")) json.optDouble("weightKg", current.weightKg.toDouble()).toFloat() else current.weightKg
                val goal = if (json.has("stepGoal")) json.optInt("stepGoal", current.stepGoal) else current.stepGoal
                val unit = json.optString("unit", current.unit)
                val sex = json.optString("sex", current.sex)
                val updated = UserProfile(heightCm = height, weightKg = weight, stepGoal = goal, unit = unit, sex = sex)
                save(ctx, updated)
                Log.i(TAG, "perfil sincronizado desde el móvil: $updated")
                true
            } catch (e: Exception) {
                Log.w(TAG, "error aplicando perfil desde el móvil", e)
                false
            }
        }
    }
}
