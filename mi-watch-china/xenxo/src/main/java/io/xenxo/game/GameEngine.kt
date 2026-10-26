package io.xenxo.game

import kotlin.math.max
import kotlin.math.min

/**
 * Motor de juego desacoplado de vistas para "Ruta xenxo" (xenxoRun).
 *
 * Simula un corredor infinito de 3 carriles con perspectiva z (0 = horizonte, 1 = jugador).
 * Diseñado con cero asignaciones en el ciclo de actualización (reutiliza entidades preasignadas).
 */
class GameEngine(
    var stepBoostActive: Boolean = false,
    private val listener: OnGameEventListener? = null
) {
    interface OnGameEventListener {
        fun onCoinCollected(coins: Int) {}
        fun onJump() {}
        fun onDuck() {}
        fun onLaneChanged(lane: Int) {}
        fun onCrash(score: Int, distanceMeters: Int, coins: Int) {}
    }

    enum class State {
        READY,
        RUNNING,
        GAME_OVER,
        PAUSED
    }

    companion object {
        const val LANE_LEFT = -1
        const val LANE_CENTER = 0
        const val LANE_RIGHT = 1

        const val MAX_ENTITIES = 20
        const val JUMP_DURATION = 0.65f       // segundos
        const val JUMP_HEIGHT = 1.0f          // altura máxima relativa
        const val DUCK_DURATION = 0.60f       // segundos

        const val INITIAL_SPEED = 0.35f      // avance en z por segundo
        const val MAX_SPEED = 0.90f
        const val SPEED_ACCELERATION = 0.005f // por segundo

        const val HIT_Z_MIN = 0.82f          // ventana de colisión
        const val HIT_Z_MAX = 0.98f
    }

    var state: State = State.READY
        private set

    // Estado del jugador
    var targetLane: Int = LANE_CENTER
        private set
    var laneX: Float = 0f                    // Interpolación visual (-1.0f .. +1.0f)
        private set

    var isJumping: Boolean = false
        private set
    var jumpProgress: Float = 0f             // 0.0f .. 1.0f
        private set
    var jumpY: Float = 0f                    // 0.0f .. 1.0f
        private set

    var isDucking: Boolean = false
        private set
    var duckProgress: Float = 0f             // 0.0f .. 1.0f
        private set

    // Puntuación y progreso
    var distanceMeters: Float = 0f
        private set
    var coins: Int = 0
        private set
    var score: Int = 0
        private set
    var speed: Float = INITIAL_SPEED
        private set
    var durationSec: Float = 0f
        private set

    // Multiplicador por pasos reales del día (si > 5000 pasos -> 1.5x puntos)
    val scoreMultiplier: Float get() = if (stepBoostActive) 1.5f else 1.0f

    // Pool de entidades preasignadas para evitar reservas en el bucle
    val entities = Array(MAX_ENTITIES) {
        GameEntity(z = -1f, lane = 0, isObstacle = false)
    }

    private var spawnTimer: Float = 0f
    private var nextSpawnInterval: Float = 1.2f
    private var randomSeed: Long = System.currentTimeMillis()

    init {
        reset()
    }

    fun reset() {
        state = State.READY
        targetLane = LANE_CENTER
        laneX = 0f
        isJumping = false
        jumpProgress = 0f
        jumpY = 0f
        isDucking = false
        duckProgress = 0f
        distanceMeters = 0f
        coins = 0
        score = 0
        speed = INITIAL_SPEED
        durationSec = 0f
        spawnTimer = 0.5f // primer spawn pronto
        nextSpawnInterval = 1.2f

        for (e in entities) {
            e.z = -1f
            e.collected = false
        }
    }

    fun start() {
        if (state == State.READY || state == State.GAME_OVER) {
            reset()
            state = State.RUNNING
        } else if (state == State.PAUSED) {
            state = State.RUNNING
        }
    }

    fun pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED
        }
    }

    fun resume() {
        if (state == State.PAUSED) {
            state = State.RUNNING
        }
    }

    // --- Controles ---

    fun moveLeft() {
        if (state != State.RUNNING) return
        if (targetLane > LANE_LEFT) {
            targetLane--
            listener?.onLaneChanged(targetLane)
        }
    }

    fun moveRight() {
        if (state != State.RUNNING) return
        if (targetLane < LANE_RIGHT) {
            targetLane++
            listener?.onLaneChanged(targetLane)
        }
    }

    fun jump() {
        if (state != State.RUNNING) return
        if (!isJumping) {
            isJumping = true
            jumpProgress = 0f
            isDucking = false
            listener?.onJump()
        }
    }

    fun duck() {
        if (state != State.RUNNING) return
        if (isJumping) {
            // Cancelar salto inmediatamente para caída rápida
            isJumping = false
            jumpProgress = 0f
            jumpY = 0f
        }
        isDucking = true
        duckProgress = 0f
        listener?.onDuck()
    }

    // --- Ciclo principal de actualización ---

    fun update(dt: Float) {
        if (state != State.RUNNING) return

        durationSec += dt

        // Aceleración suave
        if (speed < MAX_SPEED) {
            speed = min(MAX_SPEED, speed + SPEED_ACCELERATION * dt)
        }

        // Distancia recorrida (escala visual)
        distanceMeters += speed * dt * 25f
        score = ((distanceMeters + (coins * 15f)) * scoreMultiplier).toInt()

        // Interpolación del carril
        val laneDiff = targetLane.toFloat() - laneX
        val step = laneDiff * min(1f, 16f * dt)
        laneX += step

        // Salto
        if (isJumping) {
            jumpProgress += dt / JUMP_DURATION
            if (jumpProgress >= 1f) {
                isJumping = false
                jumpProgress = 0f
                jumpY = 0f
            } else {
                // Parábola de salto: 4 * h * p * (1 - p)
                jumpY = 4f * JUMP_HEIGHT * jumpProgress * (1f - jumpProgress)
            }
        }

        // Agachado
        if (isDucking) {
            duckProgress += dt / DUCK_DURATION
            if (duckProgress >= 1f) {
                isDucking = false
                duckProgress = 0f
            }
        }

        // Actualizar entidades
        for (i in 0 until MAX_ENTITIES) {
            val e = entities[i]
            if (e.z >= 0f) {
                e.z += speed * dt

                // Comprobar colisión en la ventana z
                if (e.z in HIT_Z_MIN..HIT_Z_MAX) {
                    val laneMatch = Math.abs(laneX - e.lane.toFloat()) < 0.55f

                    if (laneMatch) {
                        if (e.isObstacle) {
                            var hit = false
                            when (e.obstacleType) {
                                ObstacleType.BARRIER_LOW -> {
                                    // Requiere salto (altura > 0.45)
                                    if (jumpY < 0.45f) {
                                        hit = true
                                    }
                                }
                                ObstacleType.BARRIER_HIGH -> {
                                    // Requiere agacharse
                                    if (!isDucking) {
                                        hit = true
                                    }
                                }
                                ObstacleType.BLOCK -> {
                                    // Bloque completo, no se puede saltar ni agachar
                                    hit = true
                                }
                            }

                            if (hit) {
                                triggerCrash()
                                return
                            }
                        } else if (!e.collected) {
                            // Es un ítem (moneda / boost)
                            e.collected = true
                            if (e.itemType == ItemType.COIN) {
                                coins++
                                score = ((distanceMeters + (coins * 15f)) * scoreMultiplier).toInt()
                                listener?.onCoinCollected(coins)
                            } else if (e.itemType == ItemType.BOOST) {
                                coins += 5
                                listener?.onCoinCollected(coins)
                            }
                        }
                    }
                }

                // Fuera de pantalla (detrás del jugador)
                if (e.z > 1.15f) {
                    e.z = -1f // Libera entidad para reusar
                }
            }
        }

        // Generar nuevos obstáculos / monedas
        spawnTimer += dt
        if (spawnTimer >= nextSpawnInterval) {
            spawnTimer = 0f
            // Intervalo ajustado a la velocidad actual
            nextSpawnInterval = max(0.6f, 1.3f - (speed - INITIAL_SPEED) * 0.8f)
            spawnWave()
        }
    }

    private fun triggerCrash() {
        state = State.GAME_OVER
        listener?.onCrash(score, distanceMeters.toInt(), coins)
    }

    private fun pseudoRandom(max: Int): Int {
        randomSeed = (randomSeed * 1103515245L + 12345L) and 0x7fffffffL
        return ((randomSeed ushr 16) % max).toInt()
    }

    private fun spawnWave() {
        val pattern = pseudoRandom(10)

        // Selección de carriles para asegurar que al menos un carril siempre es transitable
        when {
            pattern < 4 -> {
                // Obstáculo simple (valla baja o alta) en un carril y moneda en otro
                val obsLane = pseudoRandom(3) - 1
                val obsType = if (pseudoRandom(2) == 0) ObstacleType.BARRIER_LOW else ObstacleType.BARRIER_HIGH
                spawnObstacle(obsLane, obsType)

                val coinLane = if (obsLane == LANE_CENTER) (if (pseudoRandom(2) == 0) LANE_LEFT else LANE_RIGHT) else LANE_CENTER
                spawnItem(coinLane, ItemType.COIN)
            }
            pattern < 7 -> {
                // Bloque en un carril, valla baja en otro, tercer carril libre
                val freeLane = pseudoRandom(3) - 1
                for (lane in -1..1) {
                    if (lane != freeLane) {
                        if (lane == LANE_CENTER || pseudoRandom(2) == 0) {
                            spawnObstacle(lane, ObstacleType.BLOCK)
                        } else {
                            spawnObstacle(lane, ObstacleType.BARRIER_LOW)
                        }
                    }
                }
                spawnItem(freeLane, ItemType.COIN)
            }
            pattern < 9 -> {
                // Hilera de monedas en un carril
                val coinLane = pseudoRandom(3) - 1
                spawnItem(coinLane, ItemType.COIN, zOffset = 0.0f)
                spawnItem(coinLane, ItemType.COIN, zOffset = -0.08f)
                spawnItem(coinLane, ItemType.COIN, zOffset = -0.16f)
            }
            else -> {
                // Valla alta en un carril con gema/moneda
                val lane = pseudoRandom(3) - 1
                spawnObstacle(lane, ObstacleType.BARRIER_HIGH)
                spawnItem(lane, ItemType.COIN)
            }
        }
    }

    private fun spawnObstacle(lane: Int, type: ObstacleType, zOffset: Float = 0f) {
        val e = findFreeEntity() ?: return
        e.z = zOffset
        e.lane = lane
        e.isObstacle = true
        e.obstacleType = type
        e.collected = false
    }

    private fun spawnItem(lane: Int, type: ItemType, zOffset: Float = 0f) {
        val e = findFreeEntity() ?: return
        e.z = zOffset
        e.lane = lane
        e.isObstacle = false
        e.itemType = type
        e.collected = false
    }

    private fun findFreeEntity(): GameEntity? {
        for (e in entities) {
            if (e.z < 0f) return e
        }
        return null
    }
}
