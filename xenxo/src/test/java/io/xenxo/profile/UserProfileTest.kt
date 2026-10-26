package io.xenxo.profile

import io.xenxo.gym.GymDb
import io.xenxo.health.HealthManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {

    @Test
    fun testDefaultProfileValues() {
        val p = UserProfile()
        assertEquals(175f, p.heightCm, 0.01f)
        assertEquals(70.0f, p.weightKg, 0.01f)
        assertEquals(10_000, p.stepGoal)
        assertEquals("kg", p.unit)
        assertEquals("m", p.sex)
    }

    @Test
    fun testKcalAndKmEstimation() {
        val steps = 10_000
        val weight = 70.0f
        val height = 175.0f

        val kcal = HealthManager.estimateKcal(steps, weight)
        // 10000 * 70 * 0.000601 = 420.7 -> 421 kcal
        assertEquals(421, kcal)

        val km = HealthManager.estimateKm(steps, height)
        // 10000 * 175 * 0.415 / 100000 = 7.2625 -> redondeado a 1 decimal: 7.3 km
        assertEquals(7.3f, km, 0.01f)
    }

    @Test
    fun testEpley1RMCalculation() {
        assertEquals(100f, GymDb.calculateEpley1RM(100f, 1), 0.01f)
        // 100 * (1 + 10 / 30) = 133.33
        assertEquals(133.33f, GymDb.calculateEpley1RM(100f, 10), 0.05f)
        // 80 * (1 + 6 / 30) = 96
        assertEquals(96f, GymDb.calculateEpley1RM(80f, 6), 0.01f)
    }

    @Test
    fun testProfileJsonParsing() {
        val json = JSONObject("""
            {
                "heightCm": 182,
                "weightKg": 77.4,
                "stepGoal": 12500,
                "unit": "kg",
                "sex": "m"
            }
        """.trimIndent())

        val height = json.optDouble("heightCm", 175.0).toFloat()
        val weight = json.optDouble("weightKg", 70.0).toFloat()
        val goal = json.optInt("stepGoal", 10_000)

        assertEquals(182f, height, 0.01f)
        assertEquals(77.4f, weight, 0.01f)
        assertEquals(12500, goal)
    }
}
