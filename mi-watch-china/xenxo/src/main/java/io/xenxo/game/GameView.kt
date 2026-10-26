package io.xenxo.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Vista de alto rendimiento para "Ruta xenxo" (xenxoRun).
 *
 * Implementa renderizado en Canvas sincronizado con VSYNC mediante Choreographer a 60 FPS estables.
 * CERO reservas de memoria en onDraw (Paints, Paths y RectFs preasignados).
 * Detención estricta del ciclo al pausar o entrar en modo reposo.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), GameEngine.OnGameEventListener, Choreographer.FrameCallback {

    var onExitRequested: (() -> Unit)? = null
    var onRunFinished: ((record: GameRecord) -> Unit)? = null

    val engine = GameEngine(stepBoostActive = false, listener = this)
    private val db = GameDb(context)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private var isRunning = false
    private var isPaused = false
    private var lastFrameTimeNanos = 0L

    // Pre-allocated Paints (Cero GC en onDraw)
    private val density = resources.displayMetrics.density
    private val paintBg = Paint().apply { color = 0xFF000000.toInt() }
    private val paintTrackBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF30D158.toInt()
    }
    private val paintTrackLane = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0xFF2C2C2E.toInt()
    }
    private val paintDashes = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x8830D158.toInt()
    }
    private val paintHorizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0xFF1C1C1E.toInt()
    }

    // Player
    private val paintPlayer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.8f * density
        color = 0xFF30D158.toInt()
    }
    private val paintPlayerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x3330D158.toInt()
    }
    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x55000000.toInt()
    }

    // Obstacles
    private val paintObstacleLow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xFFFF453A.toInt() // Rojo neón
    }
    private val paintObstacleLowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x44FF453A.toInt()
    }
    private val paintObstacleHigh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = 0xFFFF9F0A.toInt() // Ámbar neón
    }
    private val paintObstacleHighBeam = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33FF9F0A.toInt()
    }
    private val paintObstacleBlock = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF0A84FF.toInt() // Azul neón
    }
    private val paintObstacleBlockFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x330A84FF.toInt()
    }

    // Collectibles
    private val paintCoin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFFFFD60A.toInt() // Oro neón
    }
    private val paintCoinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x55FFD60A.toInt()
    }
    private val paintBoost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF32ADE6.toInt() // Cyan neón
    }

    // HUD / Text
    private val paintScore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 20f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val paintHudSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8E8E93.toInt()
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val paintHudCoin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD60A.toInt()
        textSize = 13f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    private val paintBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF30D158.toInt()
        textSize = 10f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Overlay Buttons
    private val paintButtonFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF30D158.toInt()
    }
    private val paintButtonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        textSize = 14f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val paintSecondaryBtnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1C1C1E.toInt()
    }
    private val paintSecondaryBtnText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 13f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    // Reusable geometry scratch objects (Pre-allocated)
    private val pathTmp = Path()
    private val rectTmp = RectF()
    private val rectRetryBtn = RectF()
    private val rectExitBtn = RectF()

    private var dashOffset = 0f
    private var animTick = 0f
    private var highScore = 0

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val x = e.x
            val y = e.y

            if (engine.state == GameEngine.State.READY) {
                engine.start()
                vibrate(15)
                return true
            }

            if (engine.state == GameEngine.State.GAME_OVER) {
                if (rectRetryBtn.contains(x, y)) {
                    vibrate(20)
                    engine.start()
                    return true
                } else if (rectExitBtn.contains(x, y)) {
                    vibrate(15)
                    onExitRequested?.invoke()
                    return true
                }
            }

            if (engine.state == GameEngine.State.RUNNING) {
                engine.jump()
                return true
            }

            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (engine.state != GameEngine.State.RUNNING) return false

            val dx = (e2.x - (e1?.x ?: e2.x))
            val dy = (e2.y - (e1?.y ?: e2.y))

            if (Math.abs(dx) > Math.abs(dy)) {
                if (dx < -30) {
                    engine.moveLeft()
                } else if (dx > 30) {
                    engine.moveRight()
                }
            } else {
                if (dy < -30) {
                    engine.jump()
                } else if (dy > 30) {
                    engine.duck()
                }
            }
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        loadHighScore()
    }

    private fun loadHighScore() {
        try {
            val stats = db.getStats()
            highScore = stats.highScore
        } catch (_: Exception) {}
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            startLoop()
        } else {
            stopLoop()
        }
    }

    fun startLoop() {
        if (!isRunning) {
            isRunning = true
            isPaused = false
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stopLoop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    fun onPauseGame() {
        isPaused = true
        engine.pause()
        stopLoop()
    }

    fun onResumeGame() {
        isPaused = false
        engine.resume()
        startLoop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning || isPaused) return

        val dt = if (lastFrameTimeNanos == 0L) {
            0.016f
        } else {
            ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        }
        lastFrameTimeNanos = frameTimeNanos

        engine.update(dt)
        dashOffset = (dashOffset + engine.speed * dt * 2.5f) % 1.0f
        animTick += dt

        invalidate()

        if (isRunning && !isPaused) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val btnW = w * 0.70f
        val btnH = 40f * density
        val btnLeft = (w - btnW) / 2f

        val retryTop = h * 0.62f
        rectRetryBtn.set(btnLeft, retryTop, btnLeft + btnW, retryTop + btnH)

        val exitTop = retryTop + btnH + 10f * density
        rectExitBtn.set(btnLeft, exitTop, btnLeft + btnW, exitTop + btnH * 0.85f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    // --- OnGameEventListener ---

    override fun onCoinCollected(coins: Int) {
        vibrate(18)
    }

    override fun onJump() {
        vibrate(12)
    }

    override fun onDuck() {
        vibrate(10)
    }

    override fun onLaneChanged(lane: Int) {
        vibrate(8)
    }

    override fun onCrash(score: Int, distanceMeters: Int, coins: Int) {
        vibrateLongPattern(longArrayOf(0, 60, 40, 80))

        try {
            val record = db.saveRun(
                score = score,
                coins = coins,
                distanceMeters = distanceMeters,
                durationSec = engine.durationSec.toInt()
            )
            if (score > highScore) {
                highScore = score
            }
            onRunFinished?.invoke(record)
        } catch (_: Exception) {}
    }

    private fun vibrate(ms: Long) {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    private fun vibrateLongPattern(pattern: LongArray) {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }

    // --- Renderizado Canvas 2D en perspectiva ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Fondo negro AMOLED puro
        canvas.drawRect(0f, 0f, w, h, paintBg)

        val cx = w / 2f
        val horizonY = h * 0.22f
        val horizonW = w * 0.18f
        val bottomY = h * 0.94f
        val bottomW = w * 0.84f

        // Línea del horizonte tenue
        canvas.drawLine(0f, horizonY, w, horizonY, paintHorizon)

        // 2. Líneas de los 3 carriles en perspectiva
        for (i in 0..3) {
            val factor = (i - 1.5f) / 1.5f
            val topX = cx + (horizonW / 2f) * factor
            val botX = cx + (bottomW / 2f) * factor

            if (i == 0 || i == 3) {
                canvas.drawLine(topX, horizonY, botX, bottomY, paintTrackBorder)
            } else {
                canvas.drawLine(topX, horizonY, botX, bottomY, paintTrackLane)
            }
        }

        // Trazos de velocidad punteados
        for (i in 1..2) {
            val factor = (i - 1.5f) / 1.5f
            for (step in 0..4) {
                val zStep = ((step / 5f) + dashOffset) % 1.0f
                val y1 = computeY(horizonY, bottomY, zStep)
                val y2 = computeY(horizonY, bottomY, min(1.0f, zStep + 0.08f))
                val x1 = computeX(cx, horizonW, bottomW, factor, zStep)
                val x2 = computeX(cx, horizonW, bottomW, factor, min(1.0f, zStep + 0.08f))
                canvas.drawLine(x1, y1, x2, y2, paintDashes)
            }
        }

        // 3. Entidades ordenadas por z
        for (i in 0 until GameEngine.MAX_ENTITIES) {
            val e = engine.entities[i]
            if (e.z in 0f..1.1f && !e.collected) {
                drawEntity(canvas, e, cx, horizonY, bottomY, horizonW, bottomW)
            }
        }

        // 4. Jugador
        if (engine.state == GameEngine.State.RUNNING || engine.state == GameEngine.State.READY) {
            drawPlayer(canvas, cx, horizonY, bottomY, horizonW, bottomW)
        }

        // 5. HUD
        drawHud(canvas, w, h)

        // 6. Overlays de estado
        if (engine.state == GameEngine.State.READY) {
            drawReadyOverlay(canvas, w, h)
        } else if (engine.state == GameEngine.State.GAME_OVER) {
            drawGameOverOverlay(canvas, w, h)
        }
    }

    private fun computeY(horizonY: Float, bottomY: Float, z: Float): Float {
        val t = z * z
        return horizonY + (bottomY - horizonY) * t
    }

    private fun computeTrackWidth(horizonW: Float, bottomW: Float, z: Float): Float {
        val t = z * z
        return horizonW + (bottomW - horizonW) * t
    }

    private fun computeX(cx: Float, horizonW: Float, bottomW: Float, laneFactor: Float, z: Float): Float {
        val tw = computeTrackWidth(horizonW, bottomW, z)
        return cx + (tw / 2f) * laneFactor
    }

    private fun drawEntity(
        canvas: Canvas,
        e: GameEntity,
        cx: Float,
        horizonY: Float,
        bottomY: Float,
        horizonW: Float,
        bottomW: Float
    ) {
        val z = e.z
        val y = computeY(horizonY, bottomY, z)
        val tw = computeTrackWidth(horizonW, bottomW, z)
        val laneW = tw / 3f
        val x = cx + e.lane * laneW
        val scale = 0.25f + 0.85f * (z * z)

        if (e.isObstacle) {
            when (e.obstacleType) {
                ObstacleType.BARRIER_LOW -> {
                    val wHalf = (laneW * 0.44f) * scale
                    val hHalf = (14f * density) * scale
                    rectTmp.set(x - wHalf, y - hHalf, x + wHalf, y + hHalf)

                    canvas.drawRoundRect(rectTmp, 3f * density, 3f * density, paintObstacleLowFill)
                    canvas.drawRoundRect(rectTmp, 3f * density, 3f * density, paintObstacleLow)
                    canvas.drawLine(x - wHalf * 0.5f, y + hHalf, x, y - hHalf, paintObstacleLow)
                    canvas.drawLine(x, y + hHalf, x + wHalf * 0.5f, y - hHalf, paintObstacleLow)
                }
                ObstacleType.BARRIER_HIGH -> {
                    val wHalf = (laneW * 0.48f) * scale
                    val archH = (32f * density) * scale
                    val barH = (12f * density) * scale

                    canvas.drawLine(x - wHalf, y, x - wHalf, y - archH, paintObstacleHigh)
                    canvas.drawLine(x + wHalf, y, x + wHalf, y - archH, paintObstacleHigh)

                    rectTmp.set(x - wHalf, y - archH, x + wHalf, y - archH + barH)
                    canvas.drawRect(rectTmp, paintObstacleHighBeam)
                    canvas.drawRect(rectTmp, paintObstacleHigh)
                }
                ObstacleType.BLOCK -> {
                    val size = (laneW * 0.46f) * scale
                    rectTmp.set(x - size, y - size * 1.5f, x + size, y)

                    canvas.drawRoundRect(rectTmp, 4f * density, 4f * density, paintObstacleBlockFill)
                    canvas.drawRoundRect(rectTmp, 4f * density, 4f * density, paintObstacleBlock)
                    canvas.drawLine(rectTmp.left, rectTmp.top, rectTmp.right, rectTmp.bottom, paintObstacleBlock)
                    canvas.drawLine(rectTmp.right, rectTmp.top, rectTmp.left, rectTmp.bottom, paintObstacleBlock)
                }
            }
        } else {
            if (e.itemType == ItemType.COIN) {
                val radius = (10f * density) * scale
                val wobble = sin((animTick * 6f + e.lane).toDouble()).toFloat() * 0.35f
                val rx = max(2f, radius * (1f - Math.abs(wobble)))

                rectTmp.set(x - rx, y - radius * 1.6f - radius, x + rx, y - radius * 1.6f + radius)
                canvas.drawOval(rectTmp, paintCoinFill)
                canvas.drawOval(rectTmp, paintCoin)
                canvas.drawCircle(x, y - radius * 1.6f, radius * 0.3f, paintCoin)
            } else {
                val radius = (12f * density) * scale
                val cyBoost = y - radius * 1.8f
                pathTmp.reset()
                pathTmp.moveTo(x, cyBoost - radius)
                pathTmp.lineTo(x + radius, cyBoost)
                pathTmp.lineTo(x, cyBoost + radius)
                pathTmp.lineTo(x - radius, cyBoost)
                pathTmp.close()
                canvas.drawPath(pathTmp, paintBoost)
            }
        }
    }

    private fun drawPlayer(
        canvas: Canvas,
        cx: Float,
        horizonY: Float,
        bottomY: Float,
        horizonW: Float,
        bottomW: Float
    ) {
        val playerZ = 0.88f
        val baseY = computeY(horizonY, bottomY, playerZ)
        val tw = computeTrackWidth(horizonW, bottomW, playerZ)
        val laneW = tw / 3f

        val playerX = cx + engine.laneX * laneW
        val jumpOffset = engine.jumpY * (60f * density)
        val playerY = baseY - jumpOffset

        val shadowWidth = (laneW * 0.38f) * (1f - engine.jumpY * 0.4f)
        val shadowHeight = 8f * density * (1f - engine.jumpY * 0.4f)
        rectTmp.set(playerX - shadowWidth, baseY - shadowHeight, playerX + shadowWidth, baseY + shadowHeight)
        canvas.drawOval(rectTmp, paintShadow)

        if (engine.isDucking) {
            val wHalf = 18f * density
            val hHalf = 9f * density
            rectTmp.set(playerX - wHalf, playerY - hHalf * 2, playerX + wHalf, playerY)
            canvas.drawRoundRect(rectTmp, 5f * density, 5f * density, paintPlayerGlow)
            canvas.drawRoundRect(rectTmp, 5f * density, 5f * density, paintPlayer)

            canvas.drawLine(playerX - wHalf - 8f * density, playerY - hHalf, playerX - wHalf, playerY - hHalf, paintPlayer)
            canvas.drawLine(playerX + wHalf, playerY - hHalf, playerX + wHalf + 8f * density, playerY - hHalf, paintPlayer)
        } else {
            val runnerH = 34f * density
            val headRadius = 5.5f * density
            val headY = playerY - runnerH

            canvas.drawCircle(playerX, headY, headRadius, paintPlayer)
            canvas.drawCircle(playerX, headY, headRadius, paintPlayerGlow)

            val torsoTop = headY + headRadius + 2f * density
            val torsoBottom = playerY - 12f * density
            canvas.drawLine(playerX, torsoTop, playerX, torsoBottom, paintPlayer)

            val armSwing = sin(animTick * 16.0).toFloat() * 10f * density
            canvas.drawLine(playerX, torsoTop + 4f * density, playerX - 10f * density, torsoTop + 10f * density + armSwing, paintPlayer)
            canvas.drawLine(playerX, torsoTop + 4f * density, playerX + 10f * density, torsoTop + 10f * density - armSwing, paintPlayer)

            if (engine.isJumping) {
                pathTmp.reset()
                pathTmp.moveTo(playerX, torsoBottom)
                pathTmp.lineTo(playerX - 8f * density, torsoBottom + 7f * density)
                pathTmp.lineTo(playerX - 4f * density, playerY)
                canvas.drawPath(pathTmp, paintPlayer)

                pathTmp.reset()
                pathTmp.moveTo(playerX, torsoBottom)
                pathTmp.lineTo(playerX + 8f * density, torsoBottom + 7f * density)
                pathTmp.lineTo(playerX + 4f * density, playerY)
                canvas.drawPath(pathTmp, paintPlayer)
            } else {
                val legSwing = sin(animTick * 18.0).toFloat() * 8f * density
                canvas.drawLine(playerX, torsoBottom, playerX - 7f * density, playerY + legSwing, paintPlayer)
                canvas.drawLine(playerX, torsoBottom, playerX + 7f * density, playerY - legSwing, paintPlayer)
            }
        }
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val pad = 16f * density

        val distText = "${engine.distanceMeters.toInt()}m"
        canvas.drawText(distText, pad, pad + 16f * density, paintHudSmall)

        canvas.drawText("${engine.score}", w / 2f, pad + 18f * density, paintScore)

        val coinText = "★ ${engine.coins}"
        canvas.drawText(coinText, w - pad, pad + 16f * density, paintHudCoin)

        if (engine.stepBoostActive) {
            canvas.drawText("⚡ BOOST 1.5x", w / 2f, pad + 32f * density, paintBadge)
        }
    }

    private fun drawReadyOverlay(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f

        paintScore.textSize = 22f * density
        paintScore.color = 0xFF30D158.toInt()
        canvas.drawText("RUTA XENXO", cx, h * 0.38f, paintScore)
        paintScore.color = 0xFFFFFFFF.toInt()

        paintHudSmall.textAlign = Paint.Align.CENTER
        paintHudSmall.color = 0xFF8E8E93.toInt()
        canvas.drawText("Desliza o corona: cambiar carril", cx, h * 0.46f, paintHudSmall)
        canvas.drawText("Arriba: saltar • Abajo: agachar", cx, h * 0.52f, paintHudSmall)

        val alpha = ((sin(animTick * 5.0) + 1.0) * 0.5 * 180 + 75).toInt().coerceIn(0, 255)
        paintButtonText.color = 0xFFFFFFFF.toInt()
        paintButtonText.alpha = alpha
        canvas.drawText("TOCA PARA EMPEZAR", cx, h * 0.68f, paintButtonText)
        paintButtonText.alpha = 255
        paintButtonText.color = 0xFF000000.toInt()
        paintHudSmall.textAlign = Paint.Align.LEFT
    }

    private fun drawGameOverOverlay(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f

        canvas.drawColor(0xBB000000.toInt())

        paintScore.textSize = 20f * density
        paintScore.color = 0xFFFF453A.toInt()
        canvas.drawText("FIN DE RUTA", cx, h * 0.28f, paintScore)
        paintScore.color = 0xFFFFFFFF.toInt()

        paintScore.textSize = 28f * density
        canvas.drawText("${engine.score}", cx, h * 0.38f, paintScore)

        paintHudSmall.textAlign = Paint.Align.CENTER
        paintHudSmall.color = 0xFF8E8E93.toInt()
        canvas.drawText("Récord: $highScore • Monedas: ${engine.coins}", cx, h * 0.46f, paintHudSmall)
        canvas.drawText("Distancia: ${engine.distanceMeters.toInt()}m", cx, h * 0.52f, paintHudSmall)
        paintHudSmall.textAlign = Paint.Align.LEFT

        canvas.drawRoundRect(rectRetryBtn, 20f * density, 20f * density, paintButtonFill)
        val textY = rectRetryBtn.centerY() + (paintButtonText.textSize / 3f)
        canvas.drawText("REINTENTAR", rectRetryBtn.centerX(), textY, paintButtonText)

        canvas.drawRoundRect(rectExitBtn, 18f * density, 18f * density, paintSecondaryBtnFill)
        val exitTextY = rectExitBtn.centerY() + (paintSecondaryBtnText.textSize / 3f)
        canvas.drawText("SALIR", rectExitBtn.centerX(), exitTextY, paintSecondaryBtnText)
    }
}
