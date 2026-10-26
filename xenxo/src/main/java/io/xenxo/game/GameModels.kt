package io.xenxo.game

/**
 * Modelos de datos del videojuego "Ruta xenxo" (xenxoRun).
 */
data class GameRecord(
    val id: Long,
    val uid: String,
    val timestamp: Long,
    val score: Int,
    val coins: Int,
    val distanceMeters: Int,
    val durationSec: Int,
    val synced: Boolean = false
)

data class GameStats(
    val highScore: Int,
    val totalCoins: Int,
    val totalDistanceMeters: Int,
    val gamesPlayed: Int,
    val level: Int
) {
    val totalDistanceKm: Float get() = Math.round((totalDistanceMeters / 1000f) * 10f) / 10f
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean,
    val unlockedAt: Long = 0L
)

enum class ObstacleType {
    BARRIER_LOW,   // Valla baja para saltar (rojo neón)
    BARRIER_HIGH,  // Valla alta para agacharse (ámbar neón)
    BLOCK          // Bloque sólido que requiere cambiar de carril (azul neón)
}

enum class ItemType {
    COIN,          // Moneda dorada neón
    BOOST          // Multiplicador temporal
}

data class GameEntity(
    var z: Float,           // 0.0f (horizonte) -> 1.0f (frente / jugador)
    var lane: Int,          // -1 (izq), 0 (centro), 1 (der)
    var isObstacle: Boolean,
    var obstacleType: ObstacleType = ObstacleType.BARRIER_LOW,
    var itemType: ItemType = ItemType.COIN,
    var collected: Boolean = false
)
