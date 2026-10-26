package io.xenxo.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameEngineTest {

    private lateinit var engine: GameEngine
    private var crashCalled = false
    private var coinsCollected = 0
    private var jumpCalled = false
    private var duckCalled = false

    @Before
    fun setUp() {
        crashCalled = false
        coinsCollected = 0
        jumpCalled = false
        duckCalled = false

        engine = GameEngine(
            stepBoostActive = false,
            listener = object : GameEngine.OnGameEventListener {
                override fun onCoinCollected(coins: Int) {
                    coinsCollected = coins
                }

                override fun onJump() {
                    jumpCalled = true
                }

                override fun onDuck() {
                    duckCalled = true
                }

                override fun onCrash(score: Int, distanceMeters: Int, coins: Int) {
                    crashCalled = true
                }
            }
        )
    }

    @Test
    fun testInitialState() {
        assertEquals(GameEngine.State.READY, engine.state)
        assertEquals(GameEngine.LANE_CENTER, engine.targetLane)
        assertEquals(0f, engine.laneX, 0.001f)
        assertFalse(engine.isJumping)
        assertFalse(engine.isDucking)
        assertEquals(0, engine.coins)
        assertEquals(0, engine.score)
    }

    @Test
    fun testStartAndMovement() {
        engine.start()
        assertEquals(GameEngine.State.RUNNING, engine.state)

        engine.moveLeft()
        assertEquals(GameEngine.LANE_LEFT, engine.targetLane)

        // Cannot move past left border
        engine.moveLeft()
        assertEquals(GameEngine.LANE_LEFT, engine.targetLane)

        engine.moveRight()
        assertEquals(GameEngine.LANE_CENTER, engine.targetLane)

        engine.moveRight()
        assertEquals(GameEngine.LANE_RIGHT, engine.targetLane)

        // Cannot move past right border
        engine.moveRight()
        assertEquals(GameEngine.LANE_RIGHT, engine.targetLane)
    }

    @Test
    fun testJumpAndDuck() {
        engine.start()

        engine.jump()
        assertTrue(engine.isJumping)
        assertTrue(jumpCalled)

        // Ducking while jumping immediately interrupts jump
        engine.duck()
        assertFalse(engine.isJumping)
        assertTrue(engine.isDucking)
        assertTrue(duckCalled)
    }

    @Test
    fun testUpdateProgressAndScore() {
        engine.start()
        engine.update(1.0f) // 1 second elapsed

        assertTrue(engine.distanceMeters > 0f)
        assertTrue(engine.score > 0)
        assertEquals(1.0f, engine.durationSec, 0.001f)
    }

    @Test
    fun testCollisionLowBarrierRequiresJump() {
        engine.start()
        // Force an obstacle in front of player
        val e = engine.entities[0]
        e.z = 0.85f // In hit zone
        e.lane = GameEngine.LANE_CENTER
        e.isObstacle = true
        e.obstacleType = ObstacleType.BARRIER_LOW

        // Without jumping: crash
        engine.update(0.016f)
        assertTrue(crashCalled)
        assertEquals(GameEngine.State.GAME_OVER, engine.state)
    }

    @Test
    fun testJumpAvoidsLowBarrier() {
        engine.start()
        engine.jump()
        // Advance jump to peak (jumpProgress ~ 0.5, jumpY ~ 1.0)
        engine.update(GameEngine.JUMP_DURATION * 0.45f)
        assertTrue("jumpY should be high enough", engine.jumpY > 0.5f)

        val e = engine.entities[0]
        e.z = 0.85f // In hit zone
        e.lane = GameEngine.LANE_CENTER
        e.isObstacle = true
        e.obstacleType = ObstacleType.BARRIER_LOW

        engine.update(0.016f)
        assertFalse("Player jumped over barrier, should not crash", crashCalled)
        assertEquals(GameEngine.State.RUNNING, engine.state)
    }

    @Test
    fun testDuckAvoidsHighBarrier() {
        engine.start()
        engine.duck()
        assertTrue(engine.isDucking)

        val e = engine.entities[0]
        e.z = 0.85f // In hit zone
        e.lane = GameEngine.LANE_CENTER
        e.isObstacle = true
        e.obstacleType = ObstacleType.BARRIER_HIGH

        engine.update(0.016f)
        assertFalse("Player ducked under barrier, should not crash", crashCalled)
        assertEquals(GameEngine.State.RUNNING, engine.state)
    }

    @Test
    fun testBlockCannotBeJumped() {
        engine.start()
        engine.jump()
        engine.update(GameEngine.JUMP_DURATION * 0.5f)

        val e = engine.entities[0]
        e.z = 0.85f // In hit zone
        e.lane = GameEngine.LANE_CENTER
        e.isObstacle = true
        e.obstacleType = ObstacleType.BLOCK

        engine.update(0.016f)
        assertTrue("Block obstacle cannot be avoided by jumping, must crash", crashCalled)
        assertEquals(GameEngine.State.GAME_OVER, engine.state)
    }

    @Test
    fun testCollectCoin() {
        engine.start()
        val e = engine.entities[0]
        e.z = 0.85f
        e.lane = GameEngine.LANE_CENTER
        e.isObstacle = false
        e.itemType = ItemType.COIN
        e.collected = false

        engine.update(0.016f)
        assertTrue(e.collected)
        assertEquals(1, engine.coins)
        assertEquals(1, coinsCollected)
    }

    @Test
    fun testStepBoostMultiplier() {
        val normalEngine = GameEngine(stepBoostActive = false)
        normalEngine.start()
        normalEngine.update(2.0f)
        val normalScore = normalEngine.score

        val boostEngine = GameEngine(stepBoostActive = true)
        boostEngine.start()
        boostEngine.update(2.0f)
        val boostScore = boostEngine.score

        assertTrue("Boosted score ($boostScore) should be higher than normal score ($normalScore)", boostScore > normalScore)
    }
}
